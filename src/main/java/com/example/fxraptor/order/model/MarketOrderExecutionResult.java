package com.example.fxraptor.order.model;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;

public record MarketOrderExecutionResult(
        Order order,
        Trade trade,
        Position position
) {
}
