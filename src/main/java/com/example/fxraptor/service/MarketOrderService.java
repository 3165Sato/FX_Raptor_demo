package com.example.fxraptor.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.repository.TradeRepository;
import com.example.fxraptor.service.dto.MarketOrderExecutionResult;
import com.example.fxraptor.service.dto.MarketOrderRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    private Position upsertPosition(MarketOrderRequest request, BigDecimal executionPrice) {
        Position sameSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), request.side())
                .orElse(null);
        Position oppositeSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(request.userId(), request.currencyPair(), oppositeSide(request.side()))
                .orElse(null);

        BigDecimal remainingQuantity = request.quantity();
        Position updatedPosition = sameSidePosition;

        if (oppositeSidePosition != null) {
            BigDecimal offsetQuantity = remainingQuantity.min(oppositeSidePosition.getQuantity());
            BigDecimal oppositeRemaining = oppositeSidePosition.getQuantity().subtract(offsetQuantity);
            remainingQuantity = remainingQuantity.subtract(offsetQuantity);
            realizePnl(request.userId(), oppositeSidePosition, executionPrice, offsetQuantity);

            if (oppositeRemaining.compareTo(BigDecimal.ZERO) == 0) {
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
