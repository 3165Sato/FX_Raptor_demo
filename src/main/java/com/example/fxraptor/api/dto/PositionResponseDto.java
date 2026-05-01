package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponseDto(
        Long positionId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal unrealizedPnl,
        Instant updatedAt
) {
}
