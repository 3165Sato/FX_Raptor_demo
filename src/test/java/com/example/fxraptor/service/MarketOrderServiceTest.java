package com.example.fxraptor.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.repository.TradeRepository;
import com.example.fxraptor.service.dto.MarketOrderExecutionResult;
import com.example.fxraptor.service.dto.MarketOrderRequest;

@ExtendWith(MockitoExtension.class)
class MarketOrderServiceTest {

    @org.mockito.Mock
    private AccountRepository accountRepository;

    @org.mockito.Mock
    private OrderRepository orderRepository;

    @org.mockito.Mock
    private TradeRepository tradeRepository;

    @org.mockito.Mock
    private PositionRepository positionRepository;

    private final PlatformTransactionManager transactionManager = new NoOpTransactionManager();

    @Test
    void retriesPositionUpdateWhenOptimisticLockOccurs() {
        // 楽観ロック競合時に最新建玉を読み直し、数量と平均価格を壊さず更新できることを保証する。
        MarketOrderService service = new MarketOrderService(
                accountRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                transactionManager
        );

        MarketOrderRequest request = new MarketOrderRequest(
                "user-1",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1.00000000")
        );

        AtomicLong orderIdSequence = new AtomicLong(100);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(orderIdSequence.incrementAndGet());
            }
            return order;
        });

        AtomicLong tradeIdSequence = new AtomicLong(200);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            if (trade.getId() == null) {
                trade.setId(tradeIdSequence.incrementAndGet());
            }
            return trade;
        });

        Position stalePosition = position(1L, "1.00000000", "149.00000000", 0L);
        Position refreshedPosition = position(1L, "2.00000000", "149.50000000", 1L);
        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-1", "USD/JPY", OrderSide.BUY))
                .thenReturn(Optional.of(stalePosition), Optional.of(refreshedPosition));
        when(positionRepository.saveAndFlush(any(Position.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Position.class, 1L))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketOrderExecutionResult result = service.execute(request);

        ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository, times(2)).saveAndFlush(positionCaptor.capture());
        Position finalSavedPosition = positionCaptor.getAllValues().get(1);

        assertThat(finalSavedPosition.getQuantity()).isEqualByComparingTo("3.00000000");
        assertThat(finalSavedPosition.getAvgPrice()).isEqualByComparingTo("149.66666667");
        assertThat(result.position().getQuantity()).isEqualByComparingTo("3.00000000");
        assertThat(result.position().getAvgPrice()).isEqualByComparingTo("149.66666667");
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void retriesPositionInsertWhenConcurrentCreateHitsUniqueConstraint() {
        // 初回建玉作成が競合しても、既存行へ合流して結果が壊れないことを確認する。
        MarketOrderService service = new MarketOrderService(
                accountRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                transactionManager
        );

        MarketOrderRequest request = new MarketOrderRequest(
                "user-2",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1.00000000")
        );

        AtomicLong orderIdSequence = new AtomicLong(300);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(orderIdSequence.incrementAndGet());
            }
            return order;
        });

        AtomicLong tradeIdSequence = new AtomicLong(400);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            if (trade.getId() == null) {
                trade.setId(tradeIdSequence.incrementAndGet());
            }
            return trade;
        });

        Position existingPosition = position(10L, "1.00000000", "150.00000000", 0L);
        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-2", "USD/JPY", OrderSide.BUY))
                .thenReturn(Optional.empty(), Optional.of(existingPosition));
        when(positionRepository.saveAndFlush(any(Position.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketOrderExecutionResult result = service.execute(request);

        ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository, times(2)).saveAndFlush(positionCaptor.capture());
        Position retriedPosition = positionCaptor.getAllValues().get(1);

        assertThat(retriedPosition.getId()).isEqualTo(10L);
        assertThat(retriedPosition.getQuantity()).isEqualByComparingTo("2.00000000");
        assertThat(retriedPosition.getAvgPrice()).isEqualByComparingTo("150.00000000");
        assertThat(result.position().getQuantity()).isEqualByComparingTo("2.00000000");
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void offsetsOppositeSidePositionBeforeOpeningNewPosition() {
        // 反対売買は両建てにせず既存建玉を減らし、その差額だけ realized PnL を口座へ反映する。
        MarketOrderService service = new MarketOrderService(
                accountRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                transactionManager
        );

        MarketOrderRequest request = new MarketOrderRequest(
                "user-3",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1.00000000")
        );

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(500L);
            }
            return order;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Position shortPosition = new Position();
        shortPosition.setId(50L);
        shortPosition.setUserId("user-3");
        shortPosition.setCurrencyPair("USD/JPY");
        shortPosition.setSide(OrderSide.SELL);
        shortPosition.setQuantity(new BigDecimal("2.00000000"));
        shortPosition.setAvgPrice(new BigDecimal("151.00000000"));
        shortPosition.setVersion(0L);

        Account account = account("user-3", "10000.0000");
        when(accountRepository.findByUserId("user-3")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-3", "USD/JPY", OrderSide.BUY))
                .thenReturn(Optional.empty());
        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-3", "USD/JPY", OrderSide.SELL))
                .thenReturn(Optional.of(shortPosition));
        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketOrderExecutionResult result = service.execute(request);

        ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).saveAndFlush(positionCaptor.capture());
        Position updatedShort = positionCaptor.getValue();

        assertThat(updatedShort.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(updatedShort.getQuantity()).isEqualByComparingTo("1.00000000");
        assertThat(updatedShort.getAvgPrice()).isEqualByComparingTo("151.00000000");
        assertThat(result.position()).isNotNull();
        assertThat(result.position().getSide()).isEqualTo(OrderSide.SELL);
        assertThat(result.position().getQuantity()).isEqualByComparingTo("1.00000000");
        assertThat(account.getBalance()).isEqualByComparingTo("10001.0000");
        verify(positionRepository, never()).delete(any(Position.class));
    }

    @Test
    void offsetsOppositeSidePositionBeforeOpeningNewPosition1() {
        // 反対売買は両建てにせず既存建玉を減らし、その差額だけ realized PnL を口座へ反映する。
        MarketOrderService service = new MarketOrderService(
                accountRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                transactionManager
        );

        MarketOrderRequest request = new MarketOrderRequest(
                "user-10",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("10.00000000")
        );

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(500L);
            }
            return order;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Position shortPosition = new Position();
        shortPosition.setId(50L);
        shortPosition.setUserId("user-10");
        shortPosition.setCurrencyPair("USD/JPY");
        shortPosition.setSide(OrderSide.SELL);
        shortPosition.setQuantity(new BigDecimal("11.00000000"));
        shortPosition.setAvgPrice(new BigDecimal("151.00000000"));
        shortPosition.setVersion(0L);

        Account account = account("user-10", "10000.0000");
        when(accountRepository.findByUserId("user-10")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-10", "USD/JPY", OrderSide.BUY))
                .thenReturn(Optional.empty());
        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-10", "USD/JPY", OrderSide.SELL))
                .thenReturn(Optional.of(shortPosition));
        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketOrderExecutionResult result = service.execute(request);

        ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).saveAndFlush(positionCaptor.capture());
        Position updatedShort = positionCaptor.getValue();

        assertThat(updatedShort.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(updatedShort.getQuantity()).isEqualByComparingTo("1.00000000");
        assertThat(updatedShort.getAvgPrice()).isEqualByComparingTo("151.00000000");
        assertThat(result.position()).isNotNull();
        assertThat(result.position().getSide()).isEqualTo(OrderSide.SELL);
        assertThat(result.position().getQuantity()).isEqualByComparingTo("1.00000000");
        assertThat(account.getBalance()).isEqualByComparingTo("10010.0000");
        verify(positionRepository, never()).delete(any(Position.class));
    }

    @Test
    void deletesPositionAndUpdatesAccountWhenOppositePositionIsFullyClosed() {
        // 全量決済時は quantity=0 の Position を残さず削除し、損益だけ残高へ反映する。
        MarketOrderService service = new MarketOrderService(
                accountRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                transactionManager
        );

        MarketOrderRequest request = new MarketOrderRequest(
                "user-4",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("2.00000000")
        );

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(600L);
            }
            return order;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Position shortPosition = new Position();
        shortPosition.setId(60L);
        shortPosition.setUserId("user-4");
        shortPosition.setCurrencyPair("USD/JPY");
        shortPosition.setSide(OrderSide.SELL);
        shortPosition.setQuantity(new BigDecimal("2.00000000"));
        shortPosition.setAvgPrice(new BigDecimal("151.00000000"));
        shortPosition.setVersion(0L);

        Account account = account("user-4", "10000.0000");
        when(accountRepository.findByUserId("user-4")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-4", "USD/JPY", OrderSide.BUY))
                .thenReturn(Optional.empty());
        when(positionRepository.findByUserIdAndCurrencyPairAndSide("user-4", "USD/JPY", OrderSide.SELL))
                .thenReturn(Optional.of(shortPosition));

        MarketOrderExecutionResult result = service.execute(request);

        assertThat(result.position()).isNull();
        assertThat(account.getBalance()).isEqualByComparingTo("10002.0000");
        verify(positionRepository).delete(shortPosition);
        verify(positionRepository, never()).saveAndFlush(any(Position.class));
    }

    private static Position position(Long id, String quantity, String avgPrice, Long version) {
        Position position = new Position();
        position.setId(id);
        position.setUserId("user");
        position.setCurrencyPair("USD/JPY");
        position.setSide(OrderSide.BUY);
        position.setQuantity(new BigDecimal(quantity));
        position.setAvgPrice(new BigDecimal(avgPrice));
        position.setVersion(version);
        return position;
    }

    private static Account account(String userId, String balance) {
        Account account = new Account();
        account.setUserId(userId);
        account.setCurrency("JPY");
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
