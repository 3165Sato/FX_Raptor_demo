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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerEngineTest {

    @Mock
    private TriggerOrderRepository triggerOrderRepository;

    @Mock
    private QuoteService quoteService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MarketOrderService marketOrderService;

    @Test
    void firesStopBuyWhenAskCrossesTriggerPrice() {
        TriggerEngine engine = new TriggerEngine(triggerOrderRepository, quoteService, accountRepository, marketOrderService);
        TriggerOrder order = triggerOrder(1L, 10L, "USD/JPY", OrderSide.BUY, TriggerType.STOP, "150.00");
        Quote quote = quote("USD/JPY", "149.99", "150.01");
        Account account = account(10L, "user-1");

        when(triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE)).thenReturn(List.of(order));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(triggerOrderRepository.updateStatusIfCurrent(any(), any(), any(), any())).thenReturn(1);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        engine.evaluateActiveTriggersNow();

        ArgumentCaptor<MarketOrderRequest> captor = ArgumentCaptor.forClass(MarketOrderRequest.class);
        verify(marketOrderService).execute(captor.capture());
        assertThat(captor.getValue().side()).isEqualTo(OrderSide.BUY);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("1.00000000");
    }

    @Test
    void firesStopSellWhenBidCrossesTriggerPrice() {
        TriggerEngine engine = new TriggerEngine(triggerOrderRepository, quoteService, accountRepository, marketOrderService);
        TriggerOrder order = triggerOrder(2L, 10L, "USD/JPY", OrderSide.SELL, TriggerType.STOP, "149.90");
        Quote quote = quote("USD/JPY", "149.90", "149.95");
        Account account = account(10L, "user-1");

        when(triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE)).thenReturn(List.of(order));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(triggerOrderRepository.updateStatusIfCurrent(any(), any(), any(), any())).thenReturn(1);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        engine.evaluateActiveTriggersNow();

        ArgumentCaptor<MarketOrderRequest> captor = ArgumentCaptor.forClass(MarketOrderRequest.class);
        verify(marketOrderService).execute(captor.capture());
        assertThat(captor.getValue().side()).isEqualTo(OrderSide.SELL);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("1.00000000");
    }

    @Test
    void usesBidAskRulesForAllTriggerTypes() {
        TriggerEngine engine = new TriggerEngine(triggerOrderRepository, quoteService, accountRepository, marketOrderService);

        TriggerOrder stopSell = triggerOrder(1L, 10L, "USD/JPY", OrderSide.SELL, TriggerType.STOP, "149.90");
        TriggerOrder takeProfitBuy = triggerOrder(2L, 10L, "USD/JPY", OrderSide.BUY, TriggerType.TAKE_PROFIT, "150.10");
        TriggerOrder takeProfitSell = triggerOrder(3L, 10L, "USD/JPY", OrderSide.SELL, TriggerType.TAKE_PROFIT, "149.95");
        Quote quote = quote("USD/JPY", "149.90", "149.95");
        Account account = account(10L, "user-1");

        when(triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE))
                .thenReturn(List.of(stopSell, takeProfitBuy, takeProfitSell));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(triggerOrderRepository.updateStatusIfCurrent(any(), any(), any(), any())).thenReturn(1);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        engine.evaluateActiveTriggersNow();

        verify(marketOrderService, times(2)).execute(any(MarketOrderRequest.class));
    }

    @Test
    void doesNotDoubleFireWhenStatusUpdateLostRace() {
        TriggerEngine engine = new TriggerEngine(triggerOrderRepository, quoteService, accountRepository, marketOrderService);
        TriggerOrder order = triggerOrder(1L, 10L, "USD/JPY", OrderSide.BUY, TriggerType.STOP, "150.00");
        Quote quote = quote("USD/JPY", "149.99", "150.01");

        when(triggerOrderRepository.findAllByStatus(TriggerStatus.ACTIVE)).thenReturn(List.of(order));
        when(quoteService.getQuote("USD/JPY")).thenReturn(quote);
        when(triggerOrderRepository.updateStatusIfCurrent(any(), any(), any(), any())).thenReturn(0);

        engine.evaluateActiveTriggersNow();

        verify(marketOrderService, never()).execute(any(MarketOrderRequest.class));
    }

    private static TriggerOrder triggerOrder(Long id,
                                             Long accountId,
                                             String pair,
                                             OrderSide side,
                                             TriggerType type,
                                             String triggerPrice) {
        TriggerOrder order = new TriggerOrder();
        order.setId(id);
        order.setAccountId(accountId);
        order.setCurrencyPair(pair);
        order.setSide(side);
        order.setTriggerType(type);
        order.setTriggerPrice(new BigDecimal(triggerPrice));
        order.setQuantity(new BigDecimal("1.00000000"));
        order.setStatus(TriggerStatus.ACTIVE);
        order.setCreatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        return order;
    }

    private static Quote quote(String pair, String bid, String ask) {
        Quote quote = new Quote();
        quote.setCurrencyPair(pair);
        quote.setBid(new BigDecimal(bid));
        quote.setAsk(new BigDecimal(ask));
        quote.setTimestamp(Instant.parse("2026-03-05T00:00:00Z"));
        return quote;
    }

    private static Account account(Long id, String userId) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setCurrency("JPY");
        account.setBalance(new BigDecimal("10000.0000"));
        return account;
    }
}
