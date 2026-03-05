package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.domain.TriggerStatus;
import com.example.fxraptor.domain.TriggerType;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.TriggerOrderRepository;
import com.example.fxraptor.service.dto.MarketOrderRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * ACTIVEなトリガー注文を監視し、成立時に内部成行注文を発行するエンジン。
 */
@Component
public class TriggerEngine {

    private final TriggerOrderRepository triggerOrderRepository;
    private final QuoteService quoteService;
    private final AccountRepository accountRepository;
    private final MarketOrderService marketOrderService;

    public TriggerEngine(TriggerOrderRepository triggerOrderRepository,
                         QuoteService quoteService,
                         AccountRepository accountRepository,
                         MarketOrderService marketOrderService) {
        this.triggerOrderRepository = triggerOrderRepository;
        this.quoteService = quoteService;
        this.accountRepository = accountRepository;
        this.marketOrderService = marketOrderService;
    }

    @Scheduled(fixedRate = 1000)
    public void evaluateActiveTriggers() {
        evaluateActiveTriggersNow();
    }

    @Transactional
    public void evaluateActiveTriggersNow() {
        List<TriggerOrder> activeOrders = triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE);
        for (TriggerOrder order : activeOrders) {
            Quote quote = quoteService.getQuote(order.getCurrencyPair());
            if (!isTriggered(order, quote)) {
                continue;
            }

            int updated = triggerOrderRepository.updateStatusIfCurrent(
                    order.getId(),
                    TriggerStatus.ACTIVE,
                    TriggerStatus.TRIGGERED,
                    Instant.now()
            );
            if (updated != 1) {
                continue;
            }

            Account account = accountRepository.findById(order.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("account not found for id: " + order.getAccountId()));
            marketOrderService.execute(new MarketOrderRequest(
                    account.getUserId(),
                    order.getCurrencyPair(),
                    order.getSide(),
                    order.getQuantity()
            ));
        }
    }

    private boolean isTriggered(TriggerOrder order, Quote quote) {
        BigDecimal bid = quote.getBid();
        BigDecimal ask = quote.getAsk();
        BigDecimal triggerPrice = order.getTriggerPrice();

        if (order.getTriggerType() == TriggerType.STOP) {
            if (order.getSide() == OrderSide.BUY) {
                return ask.compareTo(triggerPrice) >= 0;
            }
            return bid.compareTo(triggerPrice) <= 0;
        }

        if (order.getSide() == OrderSide.BUY) {
            return bid.compareTo(triggerPrice) >= 0;
        }
        return ask.compareTo(triggerPrice) <= 0;
    }
}
