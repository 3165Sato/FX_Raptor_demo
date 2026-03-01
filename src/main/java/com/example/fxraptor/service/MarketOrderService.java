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
     * 成行注文の約定結果をポジションに反映する（Upsert）。
     *
     * 仕様イメージ：
     * - まず「反対サイドのポジション」があれば、注文数量と相殺（決済）する
     *   - 相殺された数量分は損益を確定（realizePnl）
     *   - 反対サイドが0になれば削除、残れば数量を減らして更新
     * - 相殺しても注文数量が残った場合のみ「同サイドのポジション」を作成/更新する
     *   - 同サイドが無ければ新規作成
     *   - 同サイドがあれば数量を加算し、加重平均で平均価格を更新
     *
     * @param request         成行注文リクエスト（userId, currencyPair, side, quantity など）
     * @param executionPrice  約定価格
     * @return 処理後に更新されたポジション
     *         - 注文が反対サイドの決済だけで終わった場合：反対サイドの更新後ポジション（または削除ならnull）
     *         - 注文が残って同サイドを建てた場合：同サイドの更新後ポジション
     */
    private Position upsertPosition(MarketOrderRequest request, BigDecimal executionPrice) {

        // 1) 同サイド（同方向）の既存ポジションを取得（例：買い注文なら買いポジション）
        Position sameSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), request.side())
                .orElse(null);

        // 2) 反対サイド（逆方向）の既存ポジションを取得（例：買い注文なら売りポジション）
        Position oppositeSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), oppositeSide(request.side()))
                .orElse(null);

        // 注文数量のうち、相殺後にまだ「建てる必要がある」残数量
        BigDecimal remainingQuantity = request.quantity();

        // 返却用（途中で反対サイドを更新した場合に返すため）
        // ※このメソッドは状況により返すポジションが「反対サイド」or「同サイド」になり得る点に注意
        Position updatedPosition = sameSidePosition;

        // 3) 反対サイドのポジションがある場合、まずは相殺（決済）を優先する
        if (oppositeSidePosition != null) {

            // 相殺できる数量 = min(注文残数量, 反対サイド数量)
            BigDecimal offsetQuantity = remainingQuantity.min(oppositeSidePosition.getQuantity());
            
            // 反対サイドの残数量 = 反対サイド数量 - 相殺数量
            BigDecimal oppositeRemaining = oppositeSidePosition.getQuantity().subtract(offsetQuantity);
            
            // 注文側の残数量 = 注文残数量 - 相殺数量
            remainingQuantity = remainingQuantity.subtract(offsetQuantity);
            
            // 相殺（決済）された数量分の損益を確定
            realizePnl(request.userId(), oppositeSidePosition, executionPrice, offsetQuantity);

            // 反対サイドがゼロになったら削除、残っていれば数量を減らして更新
            if (oppositeRemaining.compareTo(BigDecimal.ZERO) == 0) {
                positionRepository.delete(oppositeSidePosition);
                updatedPosition = null;
            } else {
                oppositeSidePosition.setQuantity(oppositeRemaining);
                updatedPosition = positionRepository.saveAndFlush(oppositeSidePosition);
            }
        }

        // 4) 相殺だけで注文数量が全部消化された場合はここで終了（＝新規建て無し）
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return updatedPosition;
        }

        // 5) ここまで来たら「注文数量が残っている」＝同サイドに建てる処理が必要

        // 5-1) 同サイドポジションが無ければ新規作成して保存
        if (sameSidePosition == null) {
            Position newPosition = createNewPosition(request, executionPrice, remainingQuantity);
            return positionRepository.saveAndFlush(newPosition);
        }

        // 5-2) 同サイドが既にある場合は、数量を加算し平均価格を加重平均で更新
        BigDecimal newQuantity = sameSidePosition.getQuantity().add(remainingQuantity);
        BigDecimal newAvgPrice = weightedAverage(
                sameSidePosition.getAvgPrice(), // 既存平均価格
                sameSidePosition.getQuantity(), // 既存数量
                executionPrice,                 // 今回の約定価格
                remainingQuantity               // 今回の建て増し数量
        );
        sameSidePosition.setQuantity(newQuantity);
        sameSidePosition.setAvgPrice(newAvgPrice);

        // 更新して返却
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
