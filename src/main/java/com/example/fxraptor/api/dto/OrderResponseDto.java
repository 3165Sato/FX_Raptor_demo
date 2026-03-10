package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponseDto(
        Long id,
        String userId,
        String currencyPair,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        OrderStatus status,
        Instant createdAt
) {
}
