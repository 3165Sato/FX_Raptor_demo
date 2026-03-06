package com.example.fxraptor.order.model;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;

public record MarketOrderRequest(
        String userId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity
) {
}
