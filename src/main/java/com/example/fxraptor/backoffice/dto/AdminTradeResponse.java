package com.example.fxraptor.backoffice.dto;

import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTradeResponse(
        Long tradeId,
        Long orderId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal executionPrice,
        BigDecimal executionQuantity,
        Instant executedAt
) {
}
