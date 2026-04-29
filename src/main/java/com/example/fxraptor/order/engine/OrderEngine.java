package com.example.fxraptor.order.engine;

import com.example.fxraptor.cache.AccountCache;
import com.example.fxraptor.cache.PositionCache;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.CoverOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.order.model.NettingResult;
import com.example.fxraptor.order.service.AccountService;
import com.example.fxraptor.order.service.CoverDecisionService;
import com.example.fxraptor.order.service.CoverExecutionService;
import com.example.fxraptor.order.service.MarketOrderService;
import com.example.fxraptor.order.service.NettingService;
import com.example.fxraptor.order.service.PositionService;
import com.example.fxraptor.order.service.TradeService;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * 注文処理全体の司令塔。
 * トランザクション境界を持ち、各サービスを順に呼び出して整合性を保つ。
 */
@Component
public class OrderEngine {

    private static final int POSITION_UPDATE_MAX_RETRIES = 3;

    private final MarketOrderService marketOrderService;
    private final TradeService tradeService;
    private final CoverDecisionService coverDecisionService;
    private final CoverExecutionService coverExecutionService;
    private final NettingService nettingService;
    private final AccountService accountService;
    private final PositionService positionService;
    private final PositionCache positionCache;
    private final AccountCache accountCache;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public OrderEngine(MarketOrderService marketOrderService,
                       TradeService tradeService,
                       CoverDecisionService coverDecisionService,
                       CoverExecutionService coverExecutionService,
                       NettingService nettingService,
                       AccountService accountService,
                       PositionService positionService,
                       PositionCache positionCache,
                       AccountCache accountCache,
                       AccountRepository accountRepository,
                       OrderRepository orderRepository,
                       PlatformTransactionManager transactionManager) {
        this.marketOrderService = marketOrderService;
        this.tradeService = tradeService;
        this.coverDecisionService = coverDecisionService;
        this.coverExecutionService = coverExecutionService;
        this.nettingService = nettingService;
        this.accountService = accountService;
        this.positionService = positionService;
        this.positionCache = positionCache;
        this.accountCache = accountCache;
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MarketOrderExecutionResult executeMarketOrder(MarketOrderCommand command) {
        MarketOrderRequest request = new MarketOrderRequest(
                command.userId(),
                command.currencyPair(),
                command.side(),
                command.quantity()
        );
        marketOrderService.validate(request);

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
            Order order = marketOrderService.createOrder(request);
            BigDecimal executionPrice = marketOrderService.resolveMarketPrice(request.currencyPair(), request.side());

            Trade trade = tradeService.createTrade(order, executionPrice);
            maybeExecuteCoverTrade(trade, request.userId());
            NettingResult nettingResult = nettingService.net(trade);

            accountService.applyRealizedPnl(
                    request.userId(),
                    nettingResult.oppositeSidePosition(),
                    executionPrice,
                    nettingResult.closedQuantity()
            );

            Position savedPosition = positionService.applyNettingResult(nettingResult);

            order.setStatus(OrderStatus.FILLED);
            Order filledOrder = orderRepository.save(order);

            updateCaches(request.userId(), nettingResult, savedPosition);
            return new MarketOrderExecutionResult(filledOrder, trade, savedPosition);
        });

        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private void maybeExecuteCoverTrade(Trade trade, String userId) {
        Long accountId = accountRepository.findByUserId(userId)
                .map(Account::getId)
                .orElse(null);
        coverDecisionService.decide(trade, accountId)
                .ifPresent(coverExecutionService::execute);
    }

    private void updateCaches(String userId, NettingResult nettingResult, Position position) {
        evictClosedOppositePosition(nettingResult);
        if (position != null) {
            positionCache.put(positionKey(position), position);
        }
        Account account = accountRepository.findByUserId(userId).orElse(null);
        if (account != null) {
            accountCache.put(userId, account);
        }
    }

    private void evictClosedOppositePosition(NettingResult nettingResult) {
        Position oppositePosition = nettingResult.oppositeSidePosition();
        if (oppositePosition == null) {
            return;
        }
        if (nettingResult.closedQuantity().compareTo(oppositePosition.getQuantity()) < 0) {
            return;
        }
        positionCache.evict(positionKey(oppositePosition));
    }

    private String positionKey(Position position) {
        return position.getUserId() + "|" + position.getCurrencyPair() + "|" + position.getSide();
    }
}
