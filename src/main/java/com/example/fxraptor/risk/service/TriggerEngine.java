package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.domain.TriggerStatus;
import com.example.fxraptor.domain.TriggerType;
import com.example.fxraptor.infra.event.QuoteUpdatedEvent;
import com.example.fxraptor.infra.event.TriggerFiredEvent;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.TriggerOrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ACTIVEなトリガー注文を監視し、成立時に内部成行注文を発行するエンジン。
 */
@Component
public class TriggerEngine {

    private final TriggerOrderRepository triggerOrderRepository;
    private final QuoteService quoteService;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TriggerEngine(TriggerOrderRepository triggerOrderRepository,
                         QuoteService quoteService,
                         AccountRepository accountRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.triggerOrderRepository = triggerOrderRepository;
        this.quoteService = quoteService;
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = 1000)
    public void evaluateActiveTriggers() {
        evaluateActiveTriggersNow();
    }

    @Transactional
    public void evaluateActiveTriggersNow() {
        List<TriggerOrder> activeOrders = triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE);
        evaluate(activeOrders);
    }

    @Transactional
    public void evaluateActiveTriggersByCurrencyPair(String currencyPair) {
        Quote quote = quoteService.getQuote(currencyPair);
        evaluateActiveTriggersByCurrencyPair(currencyPair, quote);
    }

    @EventListener
    @Transactional
    public void onQuoteUpdated(QuoteUpdatedEvent event) {
        Quote quote = new Quote();
        quote.setCurrencyPair(event.currencyPair());
        quote.setBid(event.bid());
        quote.setAsk(event.ask());
        quote.setTimestamp(event.timestamp());
        evaluateActiveTriggersByCurrencyPair(event.currencyPair(), quote);
    }

    private void evaluateActiveTriggersByCurrencyPair(String currencyPair, Quote quote) {
        List<TriggerOrder> filtered = triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE).stream()
                .filter(order -> order.getCurrencyPair().equals(currencyPair))
                .collect(Collectors.toList());
        evaluate(filtered, quote);
    }

    private void evaluate(List<TriggerOrder> activeOrders) {
        for (TriggerOrder order : activeOrders) {
            Quote quote = quoteService.getQuote(order.getCurrencyPair());
            evaluateSingle(order, quote);
        }
    }

    private void evaluate(List<TriggerOrder> activeOrders, Quote quote) {
        for (TriggerOrder order : activeOrders) {
            evaluateSingle(order, quote);
        }
    }

    private void evaluateSingle(TriggerOrder order, Quote quote) {
        if (!isTriggered(order, quote)) {
            return;
        }

        int updated = triggerOrderRepository.updateStatusIfCurrent(
                order.getId(),
                TriggerStatus.ACTIVE,
                TriggerStatus.TRIGGERED,
                Instant.now()
        );
        if (updated != 1) {
            return;
        }

        Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("account not found for id: " + order.getAccountId()));
        eventPublisher.publishEvent(new TriggerFiredEvent(
                order.getId(),
                account.getId(),
                order.getCurrencyPair(),
                order.getSide(),
                order.getQuantity(),
                order.getTriggerType(),
                order.getTriggerPrice(),
                Instant.now()
        ));
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
