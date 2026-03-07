package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.model.NettingResult;
import com.example.fxraptor.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class NettingService {

    private final PositionRepository positionRepository;

    public NettingService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public NettingResult net(Trade trade) {
        Position sameSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(trade.getUserId(), trade.getCurrencyPair(), trade.getSide())
                .orElse(null);
        Position oppositeSidePosition = positionRepository
                .findByUserIdAndCurrencyPairAndSide(trade.getUserId(), trade.getCurrencyPair(), oppositeSide(trade.getSide()))
                .orElse(null);

        BigDecimal closedQuantity = BigDecimal.ZERO;
        if (oppositeSidePosition != null) {
            closedQuantity = trade.getQuantity().min(oppositeSidePosition.getQuantity());
        }
        BigDecimal remainingQuantity = trade.getQuantity().subtract(closedQuantity);
        return new NettingResult(trade, sameSidePosition, oppositeSidePosition, closedQuantity, remainingQuantity, trade.getPrice());
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
