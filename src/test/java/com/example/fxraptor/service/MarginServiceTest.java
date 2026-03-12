package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.risk.model.MarginCalculationResult;
import com.example.fxraptor.risk.service.MarginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {

    @Mock
    private OrderEngine orderEngine;

    @Mock
    private QuoteService quoteService;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarginRuleRepository marginRuleRepository;

    @Mock
    private AccountRepository accountRepository;

    @Test
    void calculatesRequiredEffectiveAndMaintenanceMarginsWithoutSideEffects() {
        MarginService marginService = service();
        Account account = account("user-1", "10000.0000", "JPY");
        Position buyPosition = position("user-1", "USD/JPY", OrderSide.BUY, "1000.00000000", "149.00000000");
        Position sellPosition = position("user-1", "USD/JPY", OrderSide.SELL, "500.00000000", "151.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00", "0.3000");

        MarginCalculationResult result = marginService.calculateResult(
                account,
                List.of(buyPosition, sellPosition),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(result.requiredMargin()).isEqualByComparingTo("9000.00000000");
        assertThat(result.effectiveMargin()).isEqualByComparingTo("11500.00000000");
        assertThat(result.marginMaintenanceRatio()).isEqualByComparingTo("127.7778");
        verify(orderEngine, never()).executeMarketOrder(any(MarketOrderCommand.class));
    }

    @Test
    void usesBidForBuyAndAskForSellInUnrealizedPnl() {
        MarginService marginService = service();
        Account account = account("user-1", "0.0000", "JPY");
        Position buyPosition = position("user-1", "USD/JPY", OrderSide.BUY, "1.00000000", "149.00000000");
        Position sellPosition = position("user-1", "USD/JPY", OrderSide.SELL, "1.00000000", "151.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.50000000");
        MarginRule marginRule = marginRule("USD/JPY", "100.00", "0.3000");

        MarginCalculationResult result = marginService.calculateResult(
                account,
                List.of(buyPosition, sellPosition),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(result.effectiveMargin()).isEqualByComparingTo("1.50000000");
    }

    @Test
    void returnsNaMarginRatioWhenRequiredMarginIsZero() {
        MarginService marginService = service();
        Account account = account("user-1", "10000.0000", "JPY");

        MarginCalculationResult result = marginService.calculateResult(
                account,
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(result.requiredMargin()).isEqualByComparingTo("0.00000000");
        assertThat(result.marginMaintenanceRatio()).isNull();
    }

    @Test
    void rejectsAccountCurrencyThatDoesNotMatchQuoteCurrency() {
        MarginService marginService = service();
        Account account = account("user-1", "10000.0000", "USD");
        Position position = position("user-1", "USD/JPY", OrderSide.BUY, "1.00000000", "149.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.02000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00", "0.3000");

        assertThatThrownBy(() -> marginService.calculateResult(
                account,
                List.of(position),
                List.of(quote),
                List.of(marginRule)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("account currency must match quote currency");
    }

    @Test
    void liquidateClosesLargestNotionalFirstAndStopsAfterRecovery() {
        MarginService marginService = service();

        Account initialAccount = account("user-1", "500.0000", "JPY");
        Account recoveredAccount = account("user-1", "1480.0000", "JPY");

        Position large = position("user-1", "USD/JPY", OrderSide.BUY, "1000.00000000", "149.00000000");
        Position small = position("user-1", "USD/JPY", OrderSide.BUY, "100.00000000", "149.00000000");

        Quote quote = quote("USD/JPY", "150.00000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00", "0.3000");

        when(accountRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(initialAccount), Optional.of(recoveredAccount));
        when(positionRepository.findAllByUserId("user-1"))
                .thenReturn(List.of(large, small), List.of(small));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(marginRuleRepository.findById("USD/JPY")).thenReturn(Optional.of(marginRule));

        marginService.liquidate(initialAccount);

        ArgumentCaptor<MarketOrderCommand> requestCaptor = ArgumentCaptor.forClass(MarketOrderCommand.class);
        verify(orderEngine, times(1)).executeMarketOrder(requestCaptor.capture());
        MarketOrderCommand request = requestCaptor.getValue();
        assertThat(request.currencyPair()).isEqualTo("USD/JPY");
        assertThat(request.side()).isEqualTo(OrderSide.SELL);
        assertThat(request.quantity()).isEqualByComparingTo("1000.00000000");
    }

    @Test
    void liquidationFlowsThroughForcedMarketOrdersUntilPositionsDisappear() {
        MarginService marginService = service();

        Account account = account("user-1", "0.0000", "JPY");
        Position large = position("user-1", "USD/JPY", OrderSide.BUY, "1000.00000000", "149.00000000");
        Position small = position("user-1", "USD/JPY", OrderSide.BUY, "100.00000000", "149.00000000");
        Quote quote = quote("USD/JPY", "150.00000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00", "0.3000");

        when(accountRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(account), Optional.of(account), Optional.of(account));
        when(positionRepository.findAllByUserId("user-1"))
                .thenReturn(List.of(large, small), List.of(small), List.of());
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(marginRuleRepository.findById("USD/JPY")).thenReturn(Optional.of(marginRule));

        marginService.liquidate(account);

        ArgumentCaptor<MarketOrderCommand> requestCaptor = ArgumentCaptor.forClass(MarketOrderCommand.class);
        verify(orderEngine, times(2)).executeMarketOrder(requestCaptor.capture());
        List<MarketOrderCommand> requests = requestCaptor.getAllValues();
        assertThat(requests.get(0).quantity()).isEqualByComparingTo("1000.00000000");
        assertThat(requests.get(1).quantity()).isEqualByComparingTo("100.00000000");
    }

    @Test
    void acceptsWholeNumberLiquidationRateAsPercentage() {
        MarginService marginService = service();
        Account account = account("user-1", "1000000.0000", "JPY");
        Position position = position("user-1", "USD/JPY", OrderSide.BUY, "10000.00000000", "150.12000000");
        Quote quote = quote("USD/JPY", "149.98000000", "150.00000000");
        MarginRule marginRule = marginRule("USD/JPY", "25.00", "50");

        MarginCalculationResult result = marginService.calculateResult(
                account,
                List.of(position),
                List.of(quote),
                List.of(marginRule)
        );

        assertThat(marginService.shouldLiquidate(List.of(position), List.of(marginRule), result)).isFalse();
    }

    private MarginService service() {
        return new MarginService(
                orderEngine,
                quoteService,
                positionRepository,
                marginRuleRepository,
                accountRepository
        );
    }

    private static Account account(String userId, String balance, String currency) {
        Account account = new Account();
        account.setUserId(userId);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency(currency);
        return account;
    }

    private static Position position(String userId,
                                     String currencyPair,
                                     OrderSide side,
                                     String quantity,
                                     String avgPrice) {
        Position position = new Position();
        position.setUserId(userId);
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
        quote.setTimestamp(Instant.parse("2026-03-05T00:00:00Z"));
        return quote;
    }

    private static MarginRule marginRule(String currencyPair, String leverage, String liquidationRate) {
        MarginRule marginRule = new MarginRule();
        marginRule.setCurrencyPair(currencyPair);
        marginRule.setLeverage(new BigDecimal(leverage));
        marginRule.setMaintenanceRate(new BigDecimal("0.5000"));
        marginRule.setLiquidationRate(new BigDecimal(liquidationRate));
        return marginRule;
    }
}
