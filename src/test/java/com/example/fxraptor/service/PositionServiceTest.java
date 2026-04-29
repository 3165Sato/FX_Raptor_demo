package com.example.fxraptor.service;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.model.NettingResult;
import com.example.fxraptor.order.service.PositionService;
import com.example.fxraptor.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Test
    void createsNewPositionForInitialBuy() {
        PositionService service = new PositionService(positionRepository);
        Trade trade = trade("user-1", "USD/JPY", OrderSide.BUY, "10000.00000000", "150.00000000");
        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            position.setId(1L);
            return position;
        });

        Position saved = service.applyNettingResult(new NettingResult(
                trade,
                null,
                null,
                BigDecimal.ZERO,
                trade.getQuantity(),
                trade.getPrice()
        ));

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(saved.getQuantity()).isEqualByComparingTo("10000.00000000");
        assertThat(saved.getAvgPrice()).isEqualByComparingTo("150.00000000");
    }

    @Test
    void updatesWeightedAverageWhenAddingSameSidePosition() {
        PositionService service = new PositionService(positionRepository);
        Position sameSide = position("user-1", "USD/JPY", OrderSide.BUY, "10000.00000000", "150.00000000");
        Trade trade = trade("user-1", "USD/JPY", OrderSide.BUY, "5000.00000000", "151.00000000");
        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Position saved = service.applyNettingResult(new NettingResult(
                trade,
                sameSide,
                null,
                BigDecimal.ZERO,
                trade.getQuantity(),
                trade.getPrice()
        ));

        assertThat(saved.getQuantity()).isEqualByComparingTo("15000.00000000");
        assertThat(saved.getAvgPrice()).isEqualByComparingTo("150.33333333");
    }

    @Test
    void keepsAveragePriceWhenPartiallyClosingOppositePosition() {
        PositionService service = new PositionService(positionRepository);
        Position opposite = position("user-1", "USD/JPY", OrderSide.BUY, "10000.00000000", "150.12000000");
        Trade trade = trade("user-1", "USD/JPY", OrderSide.SELL, "4000.00000000", "149.98000000");
        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Position saved = service.applyNettingResult(new NettingResult(
                trade,
                null,
                opposite,
                new BigDecimal("4000.00000000"),
                BigDecimal.ZERO,
                trade.getPrice()
        ));

        assertThat(saved.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(saved.getQuantity()).isEqualByComparingTo("6000.00000000");
        assertThat(saved.getAvgPrice()).isEqualByComparingTo("150.12000000");
    }

    @Test
    void createsOppositeSidePositionWhenTradeFlipsPosition() {
        PositionService service = new PositionService(positionRepository);
        Position opposite = position("user-1", "USD/JPY", OrderSide.BUY, "10000.00000000", "150.12000000");
        Trade trade = trade("user-1", "USD/JPY", OrderSide.SELL, "15000.00000000", "149.98000000");

        when(positionRepository.saveAndFlush(any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            if (position.getId() == null) {
                position.setId(2L);
            }
            return position;
        });

        Position saved = service.applyNettingResult(new NettingResult(
                trade,
                null,
                opposite,
                new BigDecimal("10000.00000000"),
                new BigDecimal("5000.00000000"),
                trade.getPrice()
        ));

        verify(positionRepository).delete(opposite);
        ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).saveAndFlush(positionCaptor.capture());
        Position persisted = positionCaptor.getValue();
        assertThat(persisted.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(persisted.getQuantity()).isEqualByComparingTo("5000.00000000");
        assertThat(persisted.getAvgPrice()).isEqualByComparingTo("149.98000000");
        assertThat(saved.getSide()).isEqualTo(OrderSide.SELL);
    }

    private static Position position(String userId,
                                     String currencyPair,
                                     OrderSide side,
                                     String quantity,
                                     String avgPrice) {
        Position position = new Position();
        position.setUserId(userId);
        position.setCurrencyPair(currencyPair);
        position.setSide(side);
        position.setQuantity(new BigDecimal(quantity));
        position.setAvgPrice(new BigDecimal(avgPrice));
        return position;
    }

    private static Trade trade(String userId,
                               String currencyPair,
                               OrderSide side,
                               String quantity,
                               String price) {
        Trade trade = new Trade();
        trade.setUserId(userId);
        trade.setCurrencyPair(currencyPair);
        trade.setSide(side);
        trade.setQuantity(new BigDecimal(quantity));
        trade.setPrice(new BigDecimal(price));
        return trade;
    }
}
