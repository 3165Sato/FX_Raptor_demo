package com.example.fxraptor.service;

import com.example.fxraptor.cache.AccountCache;
import com.example.fxraptor.cache.PositionCache;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEngineTest {

    @Mock
    private MarketOrderService marketOrderService;

    @Mock
    private TradeService tradeService;

    @Mock
    private CoverDecisionService coverDecisionService;

    @Mock
    private CoverExecutionService coverExecutionService;

    @Mock
    private NettingService nettingService;

    @Mock
    private AccountService accountService;

    @Mock
    private PositionService positionService;

    @Mock
    private PositionCache positionCache;

    @Mock
    private AccountCache accountCache;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void marketBuyPersistsOrderTradeAndPositionThroughOrderFlow() {
        stubTransactionManager();

        OrderEngine engine = new OrderEngine(
                marketOrderService,
                tradeService,
                coverDecisionService,
                coverExecutionService,
                nettingService,
                accountService,
                positionService,
                positionCache,
                accountCache,
                accountRepository,
                orderRepository,
                transactionManager
        );

        Order order = new Order();
        order.setId(1L);
        order.setUserId("user-1");
        order.setCurrencyPair("USD/JPY");
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("10000.00000000"));
        order.setStatus(OrderStatus.NEW);

        Trade trade = new Trade();
        trade.setId(10L);
        trade.setOrderId(1L);
        trade.setUserId("user-1");
        trade.setCurrencyPair("USD/JPY");
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(new BigDecimal("10000.00000000"));
        trade.setPrice(new BigDecimal("150.00000000"));

        Position position = new Position();
        position.setId(100L);
        position.setUserId("user-1");
        position.setCurrencyPair("USD/JPY");
        position.setSide(OrderSide.BUY);
        position.setQuantity(new BigDecimal("10000.00000000"));
        position.setAvgPrice(new BigDecimal("150.00000000"));

        NettingResult nettingResult = new NettingResult(
                trade,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("10000.00000000"),
                new BigDecimal("150.00000000")
        );

        Account account = new Account();
        account.setId(11L);
        account.setUserId("user-1");
        account.setBalance(new BigDecimal("1000000.0000"));

        when(marketOrderService.createOrder(any())).thenReturn(order);
        when(marketOrderService.resolveMarketPrice("USD/JPY", OrderSide.BUY)).thenReturn(new BigDecimal("150.00000000"));
        when(tradeService.createTrade(order, new BigDecimal("150.00000000"))).thenReturn(trade);
        when(coverDecisionService.decide(trade, 11L)).thenReturn(Optional.empty());
        when(nettingService.net(trade)).thenReturn(nettingResult);
        when(positionService.applyNettingResult(nettingResult)).thenReturn(position);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findByUserId("user-1")).thenReturn(Optional.of(account));

        MarketOrderExecutionResult result = engine.executeMarketOrder(new MarketOrderCommand(
                "user-1",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("10000.00000000")
        ));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(accountService).applyRealizedPnl("user-1", null, new BigDecimal("150.00000000"), BigDecimal.ZERO);
        verify(positionCache).put("user-1|USD/JPY|BUY", position);
        verify(accountCache).put("user-1", account);
        verify(positionCache, never()).evict(any());
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trade().getId()).isEqualTo(10L);
        assertThat(result.position().getId()).isEqualTo(100L);
    }

    private void stubTransactionManager() {
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    }
}
