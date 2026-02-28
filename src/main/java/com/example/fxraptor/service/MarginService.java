package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.service.dto.MarketOrderRequest;
import com.example.fxraptor.service.dto.MarginCalculationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarginService {

    private static final int MONEY_SCALE = 8;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final MarketOrderService marketOrderService;

    public MarginService(MarketOrderService marketOrderService) {
        this.marketOrderService = marketOrderService;
    }

    public MarginCalculationResult calculate(Account account,
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

        requiredMargin = requiredMargin.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal effectiveMargin = account.getBalance().add(unrealizedPnl).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal marginMaintenanceRatio = calculateMarginMaintenanceRatio(effectiveMargin, requiredMargin);
        maybeForceLiquidate(account, positions, ruleByPair, marginMaintenanceRatio);

        return new MarginCalculationResult(requiredMargin, effectiveMargin, marginMaintenanceRatio);
    }

    private void maybeForceLiquidate(Account account,
                                     List<Position> positions,
                                     Map<String, MarginRule> ruleByPair,
                                     BigDecimal marginMaintenanceRatio) {
        if (!isLiquidationTriggered(positions, ruleByPair, marginMaintenanceRatio)) {
            return;
        }

        for (Position position : positions) {
            if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            marketOrderService.execute(new MarketOrderRequest(
                    account.getUserId(),
                    position.getCurrencyPair(),
                    oppositeSide(position.getSide()),
                    position.getQuantity()
            ));
        }
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

    private BigDecimal calculateMarginMaintenanceRatio(BigDecimal effectiveMargin, BigDecimal requiredMargin) {
        if (requiredMargin.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return effectiveMargin
                .multiply(HUNDRED)
                .divide(requiredMargin, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isLiquidationTriggered(List<Position> positions,
                                           Map<String, MarginRule> ruleByPair,
                                           BigDecimal marginMaintenanceRatio) {
        for (Position position : positions) {
            MarginRule marginRule = requireMarginRule(ruleByPair, position.getCurrencyPair());
            BigDecimal liquidationThreshold = marginRule.getLiquidationRate()
                    .multiply(HUNDRED)
                    .setScale(RATIO_SCALE, RoundingMode.HALF_UP);
            if (marginMaintenanceRatio.compareTo(liquidationThreshold) < 0) {
                return true;
            }
        }
        return false;
    }

    // アカウントのバリエーションチェック
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

    // ポジションのバリエーションチェック
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

    // 現在アカウントのバリエーションチェック   
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
