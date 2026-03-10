package com.example.fxraptor.infra.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 1秒集約価格イベント。
 */
public record OneSecondAggregatedEvent(
        String currencyPair,
        BigDecimal highBid,
        BigDecimal lowBid,
        BigDecimal closeBid,
        BigDecimal highAsk,
        BigDecimal lowAsk,
        BigDecimal closeAsk,
        Instant windowStart,
        Instant windowEnd
) {
}
