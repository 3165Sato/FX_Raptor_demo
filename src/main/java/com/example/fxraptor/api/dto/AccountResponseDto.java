package com.example.fxraptor.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponseDto(
        Long id,
        String userId,
        BigDecimal balance,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
