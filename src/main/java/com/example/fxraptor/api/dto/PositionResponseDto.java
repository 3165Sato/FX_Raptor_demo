package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponseDto(
        Long id,
        String userId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal avgPrice,
        Long version,
        Instant updatedAt
) {
}
