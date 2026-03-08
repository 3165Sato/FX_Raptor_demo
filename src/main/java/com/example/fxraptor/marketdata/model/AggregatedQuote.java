package com.example.fxraptor.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 1秒集約したレート。値洗いでは close を利用する。
 */
public record AggregatedQuote(
        String currencyPair,
        BigDecimal highBid,
        BigDecimal lowBid,
        BigDecimal closeBid,
        BigDecimal highAsk,
        BigDecimal lowAsk,
        BigDecimal closeAsk,
        Instant second
) {
}
