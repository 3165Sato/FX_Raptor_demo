package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketOrderResponseDto(
        Long orderId,
        OrderStatus orderStatus,
        Long tradeId,
        String currencyPair,
        OrderSide side,
        BigDecimal executionPrice,
        BigDecimal quantity,
        Instant executedAt
) {
}
