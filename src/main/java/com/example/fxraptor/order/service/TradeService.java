package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

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
        Trade saved = tradeRepository.save(trade);
        log.info("Created trade. tradeId={}, orderId={}, userId={}, currencyPair={}, side={}, quantity={}, price={}",
                saved.getId(), saved.getOrderId(), saved.getUserId(), saved.getCurrencyPair(),
                saved.getSide(), saved.getQuantity(), saved.getPrice());
        return saved;
    }
}
