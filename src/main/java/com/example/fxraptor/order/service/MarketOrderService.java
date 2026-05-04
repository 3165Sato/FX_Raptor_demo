package com.example.fxraptor.order.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSourceType;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MarketOrderService {

    private static final Logger log = LoggerFactory.getLogger(MarketOrderService.class);

    private final OrderRepository orderRepository;
    private final QuoteService quoteService;

    public MarketOrderService(OrderRepository orderRepository,
                              QuoteService quoteService) {
        this.orderRepository = orderRepository;
        this.quoteService = quoteService;
    }

    public void validate(MarketOrderRequest request) {
        log.debug("Validating market order request. userId={}, currencyPair={}, side={}, quantity={}",
                request == null ? null : request.userId(),
                request == null ? null : request.currencyPair(),
                request == null ? null : request.side(),
                request == null ? null : request.quantity());
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (request.currencyPair() == null || request.currencyPair().isBlank()) {
            throw new IllegalArgumentException("currencyPair must not be blank");
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public BigDecimal resolveMarketPrice(String currencyPair, OrderSide side) {
        Quote quote = quoteService.getQuote(currencyPair);
        if (quote.getBid() == null || quote.getAsk() == null) {
            throw new IllegalArgumentException("quote bid/ask must not be null for " + currencyPair);
        }
        log.debug("Resolved market price. currencyPair={}, side={}, bid={}, ask={}",
                currencyPair, side, quote.getBid(), quote.getAsk());
        return side == OrderSide.BUY ? quote.getAsk() : quote.getBid();
    }

    public Order createOrder(MarketOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());
        order.setCurrencyPair(request.currencyPair());
        order.setSide(request.side());
        order.setType(OrderType.MARKET);
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.NEW);
        order.setSourceType(request.sourceType() == null ? OrderSourceType.USER : request.sourceType());
        Order saved = orderRepository.save(order);
        log.info("Created order. orderId={}, userId={}, currencyPair={}, side={}, quantity={}, sourceType={}",
                saved.getId(), saved.getUserId(), saved.getCurrencyPair(), saved.getSide(), saved.getQuantity(), saved.getSourceType());
        return saved;
    }
}
