package com.example.fxraptor.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSourceType;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.order.service.MarketOrderService;
import com.example.fxraptor.quote.QuoteService;
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

    @Mock
    private QuoteService quoteService;

    @Test
    void validatesRequest() {
        MarketOrderService service = new MarketOrderService(orderRepository, quoteService);
        MarketOrderRequest request = new MarketOrderRequest(
                "user-1",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1.00000000"),
                OrderSourceType.USER
        );

        service.validate(request);

        assertThatThrownBy(() -> service.validate(new MarketOrderRequest(
                "",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1"),
                OrderSourceType.USER
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
    }

    @Test
    void resolvesMarketPriceByBidAskRule() {
        MarketOrderService service = new MarketOrderService(orderRepository, quoteService);
        Quote quote = new Quote();
        quote.setCurrencyPair("USD/JPY");
        quote.setBid(new BigDecimal("149.98"));
        quote.setAsk(new BigDecimal("150.00"));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);

        assertThat(service.resolveMarketPrice("USD/JPY", OrderSide.BUY)).isEqualByComparingTo("150.00");
        assertThat(service.resolveMarketPrice("USD/JPY", OrderSide.SELL)).isEqualByComparingTo("149.98");
    }

    @Test
    void createsOrderWithMarketDefaults() {
        MarketOrderService service = new MarketOrderService(orderRepository, quoteService);
        MarketOrderRequest request = new MarketOrderRequest(
                "user-1",
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("2.00000000"),
                OrderSourceType.TRIGGER
        );
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
        assertThat(created.getSourceType()).isEqualTo(OrderSourceType.TRIGGER);
        assertThat(created.getQuantity()).isEqualByComparingTo("2.00000000");
    }
}
