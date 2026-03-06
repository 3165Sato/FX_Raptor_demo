package com.example.fxraptor.risk.model;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;

public record InternalOrderCommand(
        Long accountId,
        String userId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity
) {
}
