package com.example.fxraptor.order.model;

import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;

import java.math.BigDecimal;

public record NettingResult(
        Trade trade,
        Position sameSidePosition,
        Position oppositeSidePosition,
        BigDecimal closedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal executionPrice
) {
}
