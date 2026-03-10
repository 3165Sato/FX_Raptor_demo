package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.TriggerStatus;
import com.example.fxraptor.domain.TriggerType;

import java.math.BigDecimal;
import java.time.Instant;

public record TriggerOrderResponseDto(
        Long id,
        Long accountId,
        String currencyPair,
        OrderSide side,
        TriggerType triggerType,
        BigDecimal triggerPrice,
        BigDecimal quantity,
        TriggerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
