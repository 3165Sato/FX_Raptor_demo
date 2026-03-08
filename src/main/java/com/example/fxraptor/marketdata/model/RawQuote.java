package com.example.fxraptor.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 外部Price Feedから受け取る生ティック。
 */
public record RawQuote(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        Instant timestamp
) {
}
