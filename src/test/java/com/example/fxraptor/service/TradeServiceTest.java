package com.example.fxraptor.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.service.TradeService;
import com.example.fxraptor.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Test
    void savesTradeForExecutedMarketOrder() {
        TradeService service = new TradeService(tradeRepository);
        Order order = new Order();
        order.setId(10L);
        order.setUserId("user-1");
        order.setCurrencyPair("USD/JPY");
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("10000.00000000"));

        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setId(20L);
            return trade;
        });

        Trade trade = service.createTrade(order, new BigDecimal("150.00000000"));

        assertThat(trade.getId()).isEqualTo(20L);
        assertThat(trade.getOrderId()).isEqualTo(10L);
        assertThat(trade.getUserId()).isEqualTo("user-1");
        assertThat(trade.getPrice()).isEqualByComparingTo("150.00000000");
        assertThat(trade.getQuantity()).isEqualByComparingTo("10000.00000000");
    }
}
