package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.service.dto.MarginCalculationResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 毎秒の値洗い処理。全口座の証拠金状態を再計算し、必要ならロスカットを発火する。
 */
@Component
public class ValuationScheduler {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final QuoteService quoteService;
    private final MarginService marginService;

    public ValuationScheduler(AccountRepository accountRepository,
                              PositionRepository positionRepository,
                              MarginRuleRepository marginRuleRepository,
                              QuoteService quoteService,
                              MarginService marginService) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.quoteService = quoteService;
        this.marginService = marginService;
    }

    @Scheduled(fixedRate = 1000)
    public void valuateAllAccounts() {
        runValuationCycle();
    }

    public void runValuationCycle() {
        for (Account account : accountRepository.findAll()) {
            List<Position> positions = positionRepository.findAllByUserId(account.getUserId());
            if (positions.isEmpty()) {
                continue;
            }

            List<Quote> quotes = loadQuotes(positions);
            List<MarginRule> rules = loadMarginRules(positions);
            MarginCalculationResult result = marginService.calculateResult(account, positions, quotes, rules);

            if (marginService.shouldLiquidate(positions, rules, result)) {
                marginService.liquidate(account);
            }
        }
    }

    private List<Quote> loadQuotes(List<Position> positions) {
        Map<String, Quote> byPair = new LinkedHashMap<>();
        for (Position position : positions) {
            byPair.putIfAbsent(position.getCurrencyPair(), quoteService.getQuote(position.getCurrencyPair()));
        }
        return new ArrayList<>(byPair.values());
    }

    private List<MarginRule> loadMarginRules(List<Position> positions) {
        Map<String, MarginRule> byPair = new LinkedHashMap<>();
        for (Position position : positions) {
            String pair = position.getCurrencyPair();
            MarginRule rule = marginRuleRepository.findById(pair)
                    .orElseThrow(() -> new IllegalArgumentException("margin rule not found for " + pair));
            byPair.putIfAbsent(pair, rule);
        }
        return new ArrayList<>(byPair.values());
    }
}
