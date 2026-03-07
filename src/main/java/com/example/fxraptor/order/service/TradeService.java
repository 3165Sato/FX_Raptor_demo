package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public Trade createTrade(Order order, BigDecimal executionPrice) {
        Trade trade = new Trade();
        trade.setOrderId(order.getId());
        trade.setUserId(order.getUserId());
        trade.setCurrencyPair(order.getCurrencyPair());
        trade.setSide(order.getSide());
        trade.setPrice(executionPrice);
        trade.setQuantity(order.getQuantity());
        return tradeRepository.save(trade);
    }
}
