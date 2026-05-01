package com.example.fxraptor.order.model;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderSourceType;

import java.math.BigDecimal;

public record MarketOrderRequest(
        String userId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        OrderSourceType sourceType
) {
}
