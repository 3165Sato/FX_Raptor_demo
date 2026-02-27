package com.example.fxraptor.service.dto;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;

public record MarketOrderExecutionResult(
        Order order,
        Trade trade,
        Position position
) {
}
