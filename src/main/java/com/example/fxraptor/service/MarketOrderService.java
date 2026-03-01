package com.example.fxraptor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.repository.TradeRepository;
import com.example.fxraptor.service.dto.MarketOrderExecutionResult;
import com.example.fxraptor.service.dto.MarketOrderRequest;

/**
 * 成行注文を受け付け、Order -> Trade -> Position -> Account 更新までを一貫して行うサービス。
 * デモ実装では固定レートで即時約定させるが、更新順序とトランザクション境界は
 * 実運用で重要になる整合性を意識している。
 */
@Service
public class MarketOrderService {

    private static final String USD_JPY = "USD/JPY";
    private static final BigDecimal USD_JPY_BID = new BigDecimal("149.98");
    private static final BigDecimal USD_JPY_ASK = new BigDecimal("150.00");
    private static final int PRICE_SCALE = 8;
    private static final int POSITION_UPDATE_MAX_RETRIES = 3;

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final PositionRepository positionRepository;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    public MarketOrderService(AccountRepository accountRepository,
                              OrderRepository orderRepository,
                              TradeRepository tradeRepository,
                              PositionRepository positionRepository,
                              PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MarketOrderExecutionResult execute(MarketOrderRequest request) {
        validate(request);

        /*
         * Position は (userId, currencyPair, side) で 1 行に集約しているため、
         * 同時更新では数量と平均価格の read-modify-write 競合が起きる。
         * 楽観ロック例外を拾って再試行することで、壊れた集計値の上書きを防ぐ。
         */
        for (int attempt = 1; attempt <= POSITION_UPDATE_MAX_RETRIES; attempt++) {
            try {
                return executeInTransaction(request);
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                if (attempt == POSITION_UPDATE_MAX_RETRIES) {
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("Failed to update position after retries");
    }

    private MarketOrderExecutionResult executeInTransaction(MarketOrderRequest request) {
        // 注文受付から約定、ポジション、口座残高反映までを 1 トランザクションで確定させる。
        MarketOrderExecutionResult result = transactionTemplate.execute(status -> {
            Order order = new Order();
            order.setUserId(request.userId());
            order.setCurrencyPair(request.currencyPair());
            order.setSide(request.side());
            order.setType(OrderType.MARKET);
            order.setQuantity(request.quantity());
            order.setStatus(OrderStatus.NEW);
            Order savedOrder = orderRepository.save(order);

            BigDecimal executionPrice = resolveMarketPrice(request.currencyPair(), request.side());

            Trade trade = new Trade();
            trade.setOrderId(savedOrder.getId());
            trade.setUserId(request.userId());
            trade.setCurrencyPair(request.currencyPair());
            trade.setSide(request.side());
            trade.setPrice(executionPrice);
            trade.setQuantity(request.quantity());
            Trade savedTrade = tradeRepository.save(trade);

            Position savedPosition = upsertPosition(request, executionPrice);

            savedOrder.setStatus(OrderStatus.FILLED);
            Order filledOrder = orderRepository.save(savedOrder);

            return new MarketOrderExecutionResult(filledOrder, savedTrade, savedPosition);
        });

        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }

        return result;
    }

    /**
     * 約定結果を Position に反映する。
     * 反対売買がある場合は先に相殺して realized PnL を口座へ反映し、
     * 残数量がある場合だけ同方向の建玉を新規作成または加算する。
     */
    private Position upsertPosition(MarketOrderRequest request, BigDecimal executionPrice) {
        Position sameSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), request.side())
                .orElse(null);
        Position oppositeSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), oppositeSide(request.side()))
                .orElse(null);

        BigDecimal remainingQuantity = request.quantity();
        Position updatedPosition = sameSidePosition;

        /*
         * FX のネットポジションでは反対売買を両建てとして残さず、まず既存建玉を減らす。
         * 減った数量分だけが realized PnL になり、ここで Account.balance に確定反映される。
         */
        if (oppositeSidePosition != null) {
            BigDecimal offsetQuantity = remainingQuantity.min(oppositeSidePosition.getQuantity());
            BigDecimal oppositeRemaining = oppositeSidePosition.getQuantity().subtract(offsetQuantity);
            remainingQuantity = remainingQuantity.subtract(offsetQuantity);
            realizePnl(request.userId(), oppositeSidePosition, executionPrice, offsetQuantity);

            if (oppositeRemaining.compareTo(BigDecimal.ZERO) == 0) {
                // quantity=0 の行を残さず削除しておくと、一意制約と後続集計が素直になる。
                positionRepository.delete(oppositeSidePosition);
                updatedPosition = null;
            } else {
                oppositeSidePosition.setQuantity(oppositeRemaining);
                updatedPosition = positionRepository.saveAndFlush(oppositeSidePosition);
            }
        }

        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return updatedPosition;
        }

        if (sameSidePosition == null) {
            Position newPosition = createNewPosition(request, executionPrice, remainingQuantity);
            return positionRepository.saveAndFlush(newPosition);
        }

        // 同方向への積み増しは平均取得単価を加重平均で持ち直す。
        BigDecimal newQuantity = sameSidePosition.getQuantity().add(remainingQuantity);
        BigDecimal newAvgPrice = weightedAverage(
                sameSidePosition.getAvgPrice(),
                sameSidePosition.getQuantity(),
                executionPrice,
                remainingQuantity
        );
        sameSidePosition.setQuantity(newQuantity);
        sameSidePosition.setAvgPrice(newAvgPrice);
        return positionRepository.saveAndFlush(sameSidePosition);
    }

    private void validate(MarketOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (request.currencyPair() == null || request.currencyPair().isBlank()) {
            throw new IllegalArgumentException("currencyPair must not be blank");
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private BigDecimal resolveMarketPrice(String currencyPair, OrderSide side) {
        // 成行約定価格は BUY=Ask、SELL=Bid を使う。
        if (!USD_JPY.equals(currencyPair)) {
            throw new IllegalArgumentException("Only USD/JPY is supported");
        }
        return side == OrderSide.BUY ? USD_JPY_ASK : USD_JPY_BID;
    }

    private Position createNewPosition(MarketOrderRequest request, BigDecimal executionPrice, BigDecimal quantity) {
        Position position = new Position();
        position.setUserId(request.userId());
        position.setCurrencyPair(request.currencyPair());
        position.setSide(request.side());
        position.setQuantity(quantity);
        position.setAvgPrice(executionPrice);
        return position;
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private void realizePnl(String userId,
                            Position oppositeSidePosition,
                            BigDecimal executionPrice,
                            BigDecimal offsetQuantity) {
        // 新規建て部分は未実現損益のまま残し、相殺された数量分だけを口座残高へ確定させる。
        if (offsetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("account not found for userId: " + userId));

        BigDecimal realizedPnl = calculateRealizedPnl(oppositeSidePosition, executionPrice, offsetQuantity);
        account.setBalance(account.getBalance().add(realizedPnl));
        accountRepository.save(account);
    }

    private BigDecimal calculateRealizedPnl(Position oppositeSidePosition,
                                            BigDecimal executionPrice,
                                            BigDecimal offsetQuantity) {
        // 取得原価(avgPrice) と決済単価(executionPrice) の差を、閉じた数量分だけ確定損益にする。
        if (oppositeSidePosition.getSide() == OrderSide.BUY) {
            return executionPrice.subtract(oppositeSidePosition.getAvgPrice()).multiply(offsetQuantity);
        }
        return oppositeSidePosition.getAvgPrice().subtract(executionPrice).multiply(offsetQuantity);
    }

    private BigDecimal weightedAverage(BigDecimal currentAvg,
                                       BigDecimal currentQty,
                                       BigDecimal newPrice,
                                       BigDecimal newQty) {
        BigDecimal totalAmount = currentAvg.multiply(currentQty).add(newPrice.multiply(newQty));
        BigDecimal totalQty = currentQty.add(newQty);
        return totalAmount.divide(totalQty, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
