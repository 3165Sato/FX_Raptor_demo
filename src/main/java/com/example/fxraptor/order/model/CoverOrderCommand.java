package com.example.fxraptor.order.model;

import com.example.fxraptor.domain.CoverMode;
import com.example.fxraptor.domain.OrderSide;

import java.math.BigDecimal;

public record CoverOrderCommand(
        Long tradeId,
        Long accountId,
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal requestedPrice,
        CoverMode coverMode
) {
}
