package com.example.fxraptor.infra.event;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ロスカット要求イベント。
 */
public record LiquidationRequestedEvent(
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        String reason,
        Instant requestedAt
) {
}
