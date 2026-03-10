package com.example.fxraptor.infra.event;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.TriggerType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trigger成立イベント。
 */
public record TriggerFiredEvent(
        Long triggerOrderId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        TriggerType triggerType,
        BigDecimal triggerPrice,
        Instant firedAt
) {
}
