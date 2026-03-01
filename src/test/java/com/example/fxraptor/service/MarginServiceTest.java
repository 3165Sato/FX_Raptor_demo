package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.service.dto.MarketOrderRequest;
import com.example.fxraptor.service.dto.MarginCalculationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {

    @Mock
    private MarketOrderService marketOrderService;

    @Test
    void calculatesRequiredEffectiveAndMaintenanceMargins() {
        // 証拠金の3指標が、必要証拠金と未実現損益の集計ルールどおりに計算されることを確認する。
        MarginService marginService = new MarginService(marketOrderService);
        Account account = account("10000.0000", "JPY");
        Position buyPosition = position("USD/JPY", OrderSide.BUY, "1000.00000000", "149.00000000");
        Position sellPosition = position("USD/JPY", OrderSide.SELL, "500.00000000", "151.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00");

        MarginCalculationResult result = marginService.calculate(
                account,
                List.of(buyPosition, sellPosition),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(result.requiredMargin()).isEqualByComparingTo("9000.00000000");
        assertThat(result.effectiveMargin()).isEqualByComparingTo("11500.00000000");
        assertThat(result.marginMaintenanceRatio()).isEqualByComparingTo("127.7778");
        verify(marketOrderService, never()).execute(org.mockito.ArgumentMatchers.any(MarketOrderRequest.class));
    }

    @Test
    void usesBidForBuyAndAskForSellInUnrealizedPnl() {
        // 評価損益では BUY=Bid、SELL=Ask を使うという FX 固有ルールを固定する。
        MarginService marginService = new MarginService(marketOrderService);
        Account account = account("0.0000", "JPY");
        Position buyPosition = position("USD/JPY", OrderSide.BUY, "1.00000000", "149.00000000");
        Position sellPosition = position("USD/JPY", OrderSide.SELL, "1.00000000", "151.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.50000000");
        MarginRule marginRule = marginRule("USD/JPY", "100.00");

        MarginCalculationResult result = marginService.calculate(
                account,
                List.of(buyPosition, sellPosition),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(result.effectiveMargin()).isEqualByComparingTo("1.50000000");
    }

    @Test
    void rejectsAccountCurrencyThatDoesNotMatchQuoteCurrency() {
        // このデモは通貨換算を持たないため、口座通貨と quote 通貨が不一致なら明示的に失敗させる。
        MarginService marginService = new MarginService(marketOrderService);
        Account account = account("10000.0000", "USD");
        Position position = position("USD/JPY", OrderSide.BUY, "1.00000000", "149.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.02000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00");

        assertThatThrownBy(() -> marginService.calculate(
                account,
                List.of(position),
                List.of(quote),
                List.of(marginRule)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("account currency must match quote currency");
    }

    @Test
    void issuesForcedMarketOrdersWhenMaintenanceRatioFallsBelowLiquidationRate() {
        // ロスカットも特別処理ではなく、通常の成行注文フローへ委譲することを確認する。
        MarginService marginService = new MarginService(marketOrderService);
        Account account = account("1000.0000", "JPY");
        Position buyPosition = position("USD/JPY", OrderSide.BUY, "1000.00000000", "149.00000000");
        Position sellPosition = position("USD/JPY", OrderSide.SELL, "500.00000000", "151.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00");
        marginRule.setLiquidationRate(new BigDecimal("0.3000"));

        MarginCalculationResult result = marginService.calculate(
                account,
                List.of(buyPosition, sellPosition),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(result.marginMaintenanceRatio()).isEqualByComparingTo("27.7778");

        ArgumentCaptor<MarketOrderRequest> requestCaptor = ArgumentCaptor.forClass(MarketOrderRequest.class);
        verify(marketOrderService, org.mockito.Mockito.times(2)).execute(requestCaptor.capture());

        List<MarketOrderRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).extracting(MarketOrderRequest::side).containsExactly(OrderSide.SELL, OrderSide.BUY);
        assertThat(requests).extracting(MarketOrderRequest::quantity)
                .containsExactly(new BigDecimal("1000.00000000"), new BigDecimal("500.00000000"));
    }

    private static Account account(String balance, String currency) {
        Account account = new Account();
        account.setUserId("user-1");
        account.setBalance(new BigDecimal(balance));
        account.setCurrency(currency);
        return account;
    }

    private static Position position(String currencyPair, OrderSide side, String quantity, String avgPrice) {
        Position position = new Position();
        position.setUserId("user-1");
        position.setCurrencyPair(currencyPair);
        position.setSide(side);
        position.setQuantity(new BigDecimal(quantity));
        position.setAvgPrice(new BigDecimal(avgPrice));
        return position;
    }

    private static Quote quote(String currencyPair, String bid, String ask) {
        Quote quote = new Quote();
        quote.setCurrencyPair(currencyPair);
        quote.setBid(new BigDecimal(bid));
        quote.setAsk(new BigDecimal(ask));
        return quote;
    }

    private static MarginRule marginRule(String currencyPair, String leverage) {
        MarginRule marginRule = new MarginRule();
        marginRule.setCurrencyPair(currencyPair);
        marginRule.setLeverage(new BigDecimal(leverage));
        marginRule.setMaintenanceRate(new BigDecimal("0.5000"));
        marginRule.setLiquidationRate(new BigDecimal("0.3000"));
        return marginRule;
    }
}
