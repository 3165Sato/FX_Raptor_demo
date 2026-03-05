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
import com.example.fxraptor.service.dto.MarketOrderRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 証拠金の計算とロスカット実行を担当するサービス。
 * calculateResult は純計算のみ、liquidate は通常の成行注文フローを呼んで決済する。
 */
@Service
public class MarginService {

    private static final int MONEY_SCALE = 8;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MarketOrderService marketOrderService;
    private final QuoteService quoteService;
    private final PositionRepository positionRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final AccountRepository accountRepository;

    public MarginService(MarketOrderService marketOrderService,
                         QuoteService quoteService,
                         PositionRepository positionRepository,
                         MarginRuleRepository marginRuleRepository,
                         AccountRepository accountRepository) {
        this.marketOrderService = marketOrderService;
        this.quoteService = quoteService;
        this.positionRepository = positionRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * 互換用メソッド。副作用は持たず、純粋に計算だけを返す。
     */
    public MarginCalculationResult calculate(Account account,
                                             List<Position> positions,
                                             List<Quote> quotes,
                                             List<MarginRule> marginRules) {
        return calculateResult(account, positions, quotes, marginRules);
    }

    public MarginCalculationResult calculateResult(Account account,
                                                   List<Position> positions,
                                                   List<Quote> quotes,
                                                   List<MarginRule> marginRules) {
        validateAccount(account);

        Map<String, Quote> quoteByPair = indexByCurrencyPair(quotes, Quote::getCurrencyPair);
        Map<String, MarginRule> ruleByPair = indexByCurrencyPair(marginRules, MarginRule::getCurrencyPair);

        BigDecimal requiredMargin = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;

        for (Position position : positions) {
            validatePosition(position);

            Quote quote = requireQuote(quoteByPair, position.getCurrencyPair());
            MarginRule marginRule = requireMarginRule(ruleByPair, position.getCurrencyPair());
            validateAccountCurrency(account, position.getCurrencyPair());

            BigDecimal markPrice = resolveMarkPrice(position.getSide(), quote);
            BigDecimal notional = markPrice.multiply(position.getQuantity());
            BigDecimal positionRequiredMargin = notional.divide(marginRule.getLeverage(), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal positionPnl = calculateUnrealizedPnl(position, quote);

            requiredMargin = requiredMargin.add(positionRequiredMargin);
            unrealizedPnl = unrealizedPnl.add(positionPnl);
        }

        BigDecimal normalizedRequiredMargin = requiredMargin.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal equity = account.getBalance().add(unrealizedPnl).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal marginRatio = calculateMarginMaintenanceRatio(equity, normalizedRequiredMargin);

        return new MarginCalculationResult(normalizedRequiredMargin, equity, marginRatio);
    }

    public boolean shouldLiquidate(List<Position> positions,
                                   List<MarginRule> marginRules,
                                   MarginCalculationResult result) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        if (result.marginMaintenanceRatio() == null) {
            return false;
        }

        Map<String, MarginRule> ruleByPair = indexByCurrencyPair(marginRules, MarginRule::getCurrencyPair);
        BigDecimal threshold = BigDecimal.ZERO;
        for (Position position : positions) {
            MarginRule rule = requireMarginRule(ruleByPair, position.getCurrencyPair());
            BigDecimal pairThreshold = rule.getLiquidationRate().multiply(HUNDRED);
            if (pairThreshold.compareTo(threshold) > 0) {
                threshold = pairThreshold;
            }
        }
        return result.marginMaintenanceRatio().compareTo(threshold.setScale(RATIO_SCALE, RoundingMode.HALF_UP)) < 0;
    }

    /**
     * 対象口座の建玉を notional 降順に決済し、1回ごとに再計算して閾値回復なら停止する。
     */
    public void liquidate(Account account) {
        if (account == null) {
            return;
        }

        while (true) {
            Account latestAccount = accountRepository.findByUserId(account.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("account not found for userId: " + account.getUserId()));
            List<Position> positions = positionRepository.findAllByUserId(latestAccount.getUserId());
            if (positions.isEmpty()) {
                return;
            }

            List<Quote> quotes = loadQuotes(positions);
            List<MarginRule> rules = loadMarginRules(positions);
            MarginCalculationResult result = calculateResult(latestAccount, positions, quotes, rules);
            if (!shouldLiquidate(positions, rules, result)) {
                return;
            }

            Map<String, Quote> quoteByPair = indexByCurrencyPair(quotes, Quote::getCurrencyPair);
            List<Position> sortedByNotional = new ArrayList<>(positions);
            sortedByNotional.sort(Comparator.comparing(
                    (Position position) -> positionNotional(position, quoteByPair.get(position.getCurrencyPair()))
            ).reversed());

            Position target = sortedByNotional.get(0);
            marketOrderService.execute(new MarketOrderRequest(
                    latestAccount.getUserId(),
                    target.getCurrencyPair(),
                    oppositeSide(target.getSide()),
                    target.getQuantity()
            ));
        }
    }

    private BigDecimal positionNotional(Position position, Quote quote) {
        BigDecimal markPrice = resolveMarkPrice(position.getSide(), quote);
        return markPrice.multiply(position.getQuantity());
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

    private BigDecimal calculateUnrealizedPnl(Position position, Quote quote) {
        BigDecimal closingPrice = resolveMarkPrice(position.getSide(), quote);
        BigDecimal priceDiff = position.getSide() == OrderSide.BUY
                ? closingPrice.subtract(position.getAvgPrice())
                : position.getAvgPrice().subtract(closingPrice);
        return priceDiff.multiply(position.getQuantity());
    }

    private BigDecimal resolveMarkPrice(OrderSide side, Quote quote) {
        return side == OrderSide.BUY ? quote.getBid() : quote.getAsk();
    }

    private BigDecimal calculateMarginMaintenanceRatio(BigDecimal equity, BigDecimal requiredMargin) {
        // 建玉なし(requiredMargin=0)は維持率N/A扱いにする。
        if (requiredMargin.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return equity.multiply(HUNDRED).divide(requiredMargin, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private void validateAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        if (account.getBalance() == null) {
            throw new IllegalArgumentException("account balance must not be null");
        }
        if (account.getCurrency() == null || account.getCurrency().isBlank()) {
            throw new IllegalArgumentException("account currency must not be blank");
        }
    }

    private void validatePosition(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (position.getCurrencyPair() == null || position.getCurrencyPair().isBlank()) {
            throw new IllegalArgumentException("position currencyPair must not be blank");
        }
        if (position.getSide() == null) {
            throw new IllegalArgumentException("position side must not be null");
        }
        if (position.getQuantity() == null || position.getAvgPrice() == null) {
            throw new IllegalArgumentException("position quantity and avgPrice must not be null");
        }
    }

    private void validateAccountCurrency(Account account, String currencyPair) {
        String[] currencies = currencyPair.split("/");
        if (currencies.length != 2) {
            throw new IllegalArgumentException("currencyPair must be in BASE/QUOTE format");
        }
        String quoteCurrency = currencies[1];
        if (!quoteCurrency.equals(account.getCurrency())) {
            throw new IllegalArgumentException("account currency must match quote currency");
        }
    }

    private Quote requireQuote(Map<String, Quote> quoteByPair, String currencyPair) {
        Quote quote = quoteByPair.get(currencyPair);
        if (quote == null) {
            throw new IllegalArgumentException("quote not found for " + currencyPair);
        }
        if (quote.getBid() == null || quote.getAsk() == null) {
            throw new IllegalArgumentException("quote bid and ask must not be null");
        }
        return quote;
    }

    private MarginRule requireMarginRule(Map<String, MarginRule> ruleByPair, String currencyPair) {
        MarginRule marginRule = ruleByPair.get(currencyPair);
        if (marginRule == null) {
            throw new IllegalArgumentException("margin rule not found for " + currencyPair);
        }
        if (marginRule.getLeverage() == null || marginRule.getLeverage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("margin rule leverage must be positive");
        }
        if (marginRule.getLiquidationRate() == null || marginRule.getLiquidationRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("margin rule liquidationRate must not be negative");
        }
        return marginRule;
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private <T> Map<String, T> indexByCurrencyPair(List<T> values, Function<T, String> keyExtractor) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return values.stream().collect(Collectors.toMap(keyExtractor, Function.identity()));
    }
}
