package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketOrderResponseDto(
        Long orderId,
        String status,
        String message,
        Instant acceptedAt,
        Long tradeId,
        String currencyPair,
        OrderSide side,
        BigDecimal executionPrice,
        BigDecimal quantity,
        Instant executedAt
) {
}
