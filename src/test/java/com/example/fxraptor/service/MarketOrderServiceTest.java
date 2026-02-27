package com.example.fxraptor.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.OrderRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.repository.TradeRepository;
import com.example.fxraptor.service.dto.MarketOrderExecutionResult;
import com.example.fxraptor.service.dto.MarketOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderServiceTest {

    @org.mockito.Mock
    private OrderRepository orderRepository;

    @org.mockito.Mock
    private TradeRepository tradeRepository;

    @org.mockito.Mock
    private PositionRepository positionRepository;

    private final PlatformTransactionManager transactionManager = new NoOpTransactionManager();

    @Test
    void retriesPositionUpdateWhenOptimisticLockOccurs() {
        MarketOrderService service = new MarketOrderService(
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
        MarketOrderService service = new MarketOrderService(
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
