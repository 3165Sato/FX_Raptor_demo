package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.service.dto.MarginCalculationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValuationSchedulerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarginRuleRepository marginRuleRepository;

    @Mock
    private QuoteService quoteService;

    @Mock
    private MarginService marginService;

    @Test
    void triggersLiquidationWhenRatioIsBelowThreshold() {
        ValuationScheduler scheduler = new ValuationScheduler(
                accountRepository,
                positionRepository,
                marginRuleRepository,
                quoteService,
                marginService
        );

        Account account = account("user-1");
        Position position = position("user-1", "USD/JPY");
        Quote quote = quote("USD/JPY");
        MarginRule rule = marginRule("USD/JPY");
        MarginCalculationResult result = new MarginCalculationResult(
                new BigDecimal("1000.00000000"),
                new BigDecimal("100.00000000"),
                new BigDecimal("10.0000")
        );

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(positionRepository.findAllByUserId("user-1")).thenReturn(List.of(position));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(marginRuleRepository.findById("USD/JPY")).thenReturn(Optional.of(rule));
        when(marginService.calculateResult(account, List.of(position), List.of(quote), List.of(rule))).thenReturn(result);
        when(marginService.shouldLiquidate(List.of(position), List.of(rule), result)).thenReturn(true);

        scheduler.runValuationCycle();

        verify(marginService).liquidate(account);
    }

    @Test
    void skipsAccountWithoutPositions() {
        ValuationScheduler scheduler = new ValuationScheduler(
                accountRepository,
                positionRepository,
                marginRuleRepository,
                quoteService,
                marginService
        );

        Account account = account("user-2");
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(positionRepository.findAllByUserId("user-2")).thenReturn(List.of());

        scheduler.runValuationCycle();

        verify(marginService, never()).liquidate(account);
    }

    private static Account account(String userId) {
        Account account = new Account();
        account.setUserId(userId);
        account.setBalance(new BigDecimal("1000.0000"));
        account.setCurrency("JPY");
        return account;
    }

    private static Position position(String userId, String pair) {
        Position position = new Position();
        position.setUserId(userId);
        position.setCurrencyPair(pair);
        position.setSide(OrderSide.BUY);
        position.setQuantity(new BigDecimal("1.00000000"));
        position.setAvgPrice(new BigDecimal("149.00000000"));
        return position;
    }

    private static Quote quote(String pair) {
        Quote quote = new Quote();
        quote.setCurrencyPair(pair);
        quote.setBid(new BigDecimal("150.00000000"));
        quote.setAsk(new BigDecimal("150.02000000"));
        quote.setTimestamp(Instant.parse("2026-03-05T00:00:00Z"));
        return quote;
    }

    private static MarginRule marginRule(String pair) {
        MarginRule rule = new MarginRule();
        rule.setCurrencyPair(pair);
        rule.setLeverage(new BigDecimal("25.00"));
        rule.setMaintenanceRate(new BigDecimal("0.5000"));
        rule.setLiquidationRate(new BigDecimal("0.3000"));
        return rule;
    }
}
