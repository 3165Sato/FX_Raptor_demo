package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponseDto(
        Long orderId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        OrderStatus status,
        String sourceType,
        Instant createdAt
) {
}
