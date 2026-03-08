package com.example.fxraptor.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 整合性チェックとスケール統一後のティック。
 */
public record NormalizedQuote(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        Instant timestamp
) {
}
