package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponseDto(
        Long id,
        Long orderId,
        String userId,
        String currencyPair,
        OrderSide side,
        BigDecimal price,
        BigDecimal quantity,
        Instant executedAt
) {
}
