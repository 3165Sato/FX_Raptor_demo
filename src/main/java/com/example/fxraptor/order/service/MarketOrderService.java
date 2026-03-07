package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 成行注文固有の責務だけを持つサービス。
 */
@Service
public class MarketOrderService {

    private static final String USD_JPY = "USD/JPY";
    private static final BigDecimal USD_JPY_BID = new BigDecimal("149.98");
    private static final BigDecimal USD_JPY_ASK = new BigDecimal("150.00");

    private final OrderRepository orderRepository;

    public MarketOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void validate(MarketOrderRequest request) {
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

    public BigDecimal resolveMarketPrice(String currencyPair, OrderSide side) {
        if (!USD_JPY.equals(currencyPair)) {
            throw new IllegalArgumentException("Only USD/JPY is supported");
        }
        return side == OrderSide.BUY ? USD_JPY_ASK : USD_JPY_BID;
    }

    public Order createOrder(MarketOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());
        order.setCurrencyPair(request.currencyPair());
        order.setSide(request.side());
        order.setType(OrderType.MARKET);
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.NEW);
        return orderRepository.save(order);
    }
}
