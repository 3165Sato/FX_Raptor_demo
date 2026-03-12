package com.example.fxraptor.backoffice.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminPositionResponse(
        Long positionId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal avgPrice,
        Instant updatedAt
) {
}
