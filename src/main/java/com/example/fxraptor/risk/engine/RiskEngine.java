package com.example.fxraptor.risk.engine;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.marketdata.model.AggregatedQuote;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import com.example.fxraptor.risk.model.MarginCalculationResult;
import com.example.fxraptor.risk.service.InternalOrderCommandService;
import com.example.fxraptor.risk.service.LiquidationService;
import com.example.fxraptor.risk.service.MarginService;
import com.example.fxraptor.risk.service.TriggerEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 価格イベント処理の司令塔。
 * Trigger判定、証拠金判定、ロスカット指示生成、内部注文連携を順にオーケストレーションする。
 */
@Component
public class RiskEngine {

    private final TriggerEngine triggerEngine;
    private final MarginService marginService;
    private final LiquidationService liquidationService;
    private final InternalOrderCommandService internalOrderCommandService;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final QuoteService quoteService;

    public RiskEngine(TriggerEngine triggerEngine,
                      MarginService marginService,
                      LiquidationService liquidationService,
                      InternalOrderCommandService internalOrderCommandService,
                      AccountRepository accountRepository,
                      PositionRepository positionRepository,
                      MarginRuleRepository marginRuleRepository,
                      QuoteService quoteService) {
        this.triggerEngine = triggerEngine;
        this.marginService = marginService;
        this.liquidationService = liquidationService;
        this.internalOrderCommandService = internalOrderCommandService;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.quoteService = quoteService;
    }

    public void onQuoteUpdated(String currencyPair) {
        triggerEngine.evaluateActiveTriggersByCurrencyPair(currencyPair);
        Quote quote = quoteService.getQuote(currencyPair);
        evaluateLiquidationByPair(currencyPair, quote);
    }

    /**
     * 1秒集約足で値洗い・ロスカット判定を実行する。
     * close値を現在価格として評価し、必要なら内部注文を発行する。
     */
    public void onSecondQuote(AggregatedQuote aggregatedQuote) {
        Quote quote = new Quote();
        quote.setCurrencyPair(aggregatedQuote.currencyPair());
        quote.setBid(aggregatedQuote.closeBid());
        quote.setAsk(aggregatedQuote.closeAsk());
        quote.setTimestamp(aggregatedQuote.second());
        evaluateLiquidationByPair(aggregatedQuote.currencyPair(), quote);
    }

    private void evaluateLiquidationByPair(String currencyPair, Quote quote) {
        Optional<MarginRule> maybeRule = marginRuleRepository.findById(currencyPair);
        if (maybeRule.isEmpty()) {
            return;
        }
        MarginRule rule = maybeRule.get();

        List<InternalOrderCommand> commands = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            List<Position> positions = positionRepository.findAllByUserId(account.getUserId()).stream()
                    .filter(position -> currencyPair.equals(position.getCurrencyPair()))
                    .toList();
            if (positions.isEmpty()) {
                continue;
            }

            MarginCalculationResult result = marginService.calculateResult(
                    account,
                    positions,
                    List.of(quote),
                    List.of(rule)
            );
            if (marginService.shouldLiquidate(positions, List.of(rule), result)) {
                commands.addAll(liquidationService.createInternalCommands(account, positions, quote));
            }
        }
        if (!commands.isEmpty()) {
            internalOrderCommandService.dispatch(commands);
        }
    }
}
