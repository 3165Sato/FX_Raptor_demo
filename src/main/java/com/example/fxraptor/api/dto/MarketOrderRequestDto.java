package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;

public record MarketOrderRequestDto(
        String accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity
) {
}
