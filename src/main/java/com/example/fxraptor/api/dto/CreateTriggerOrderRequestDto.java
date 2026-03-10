package com.example.fxraptor.api.dto;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.TriggerType;

import java.math.BigDecimal;

public record CreateTriggerOrderRequestDto(
        Long accountId,
        String currencyPair,
        OrderSide side,
        TriggerType triggerType,
        BigDecimal triggerPrice,
        BigDecimal quantity
) {
}
