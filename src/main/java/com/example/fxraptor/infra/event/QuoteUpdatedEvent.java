package com.example.fxraptor.infra.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tick更新イベント。
 */
public record QuoteUpdatedEvent(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        Instant timestamp
) {
}
