package com.example.fxraptor.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponseDto(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        Instant timestamp
) {
}
