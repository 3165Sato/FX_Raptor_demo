package com.example.fxraptor.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.order.service.MarketOrderService;
import com.example.fxraptor.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void validatesRequest() {
        MarketOrderService service = new MarketOrderService(orderRepository);
        MarketOrderRequest request = new MarketOrderRequest("user-1", "USD/JPY", OrderSide.BUY, new BigDecimal("1.00000000"));

        service.validate(request);

        assertThatThrownBy(() -> service.validate(new MarketOrderRequest("", "USD/JPY", OrderSide.BUY, new BigDecimal("1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
    }

    @Test
    void resolvesMarketPriceByBidAskRule() {
        MarketOrderService service = new MarketOrderService(orderRepository);

        assertThat(service.resolveMarketPrice("USD/JPY", OrderSide.BUY)).isEqualByComparingTo("150.00");
        assertThat(service.resolveMarketPrice("USD/JPY", OrderSide.SELL)).isEqualByComparingTo("149.98");
    }

    @Test
    void createsOrderWithMarketDefaults() {
        MarketOrderService service = new MarketOrderService(orderRepository);
        MarketOrderRequest request = new MarketOrderRequest("user-1", "USD/JPY", OrderSide.BUY, new BigDecimal("2.00000000"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });

        Order created = service.createOrder(request);

        assertThat(created.getId()).isEqualTo(100L);
        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getCurrencyPair()).isEqualTo("USD/JPY");
        assertThat(created.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(created.getQuantity()).isEqualByComparingTo("2.00000000");
    }
}
