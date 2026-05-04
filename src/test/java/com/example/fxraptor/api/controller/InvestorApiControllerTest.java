package com.example.fxraptor.api.controller;

import com.example.fxraptor.api.dto.CreateTriggerOrderRequestDto;
import com.example.fxraptor.api.dto.MarketOrderRequestDto;
import com.example.fxraptor.api.handler.GlobalExceptionHandler;
import com.example.fxraptor.backoffice.service.AccountQueryService;
import com.example.fxraptor.backoffice.service.OrderQueryService;
import com.example.fxraptor.backoffice.service.PositionQueryService;
import com.example.fxraptor.backoffice.service.TradeQueryService;
import com.example.fxraptor.backoffice.service.TriggerOrderQueryService;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderSourceType;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.domain.TriggerStatus;
import com.example.fxraptor.domain.TriggerType;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.risk.service.TriggerOrderService;
import com.example.fxraptor.infra.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvestorApiControllerTest {

    @Mock
    private OrderEngine orderEngine;

    @Mock
    private TriggerOrderService triggerOrderService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private PositionQueryService positionQueryService;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private TradeQueryService tradeQueryService;

    @Mock
    private TriggerOrderQueryService triggerOrderQueryService;

    @Mock
    private QuoteService quoteService;

    @Test
    void marketOrderUsesNumericAccountId() {
        InvestorApiController controller = controller();
        Account account = account(1L, "account-1");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(orderEngine.executeMarketOrder(any(MarketOrderCommand.class))).thenReturn(executionResult(account));

        controller.placeMarketOrder(new MarketOrderRequestDto(
                1L,
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("10000.00000000")
        ));

        ArgumentCaptor<MarketOrderCommand> captor = ArgumentCaptor.forClass(MarketOrderCommand.class);
        verify(orderEngine).executeMarketOrder(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("account-1");
        assertThat(captor.getValue().sourceType()).isEqualTo(OrderSourceType.USER);
    }

    @Test
    void triggerOrderUsesNumericAccountId() {
        InvestorApiController controller = controller();
        Account account = account(1L, "account-1");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(triggerOrderService.create(any(TriggerOrder.class))).thenAnswer(invocation -> {
            TriggerOrder triggerOrder = invocation.getArgument(0);
            triggerOrder.setId(99L);
            triggerOrder.setStatus(TriggerStatus.ACTIVE);
            return triggerOrder;
        });

        controller.createTrigger(new CreateTriggerOrderRequestDto(
                1L,
                "USD/JPY",
                OrderSide.BUY,
                TriggerType.STOP,
                new BigDecimal("151.00000000"),
                new BigDecimal("5000.00000000")
        ));

        ArgumentCaptor<TriggerOrder> captor = ArgumentCaptor.forClass(TriggerOrder.class);
        verify(triggerOrderService).create(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(1L);
    }

    @Test
    void marketOrderRejectsStringAccountIdInJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();

        mockMvc.perform(post("/api/orders/market")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "test-trace-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "A-100",
                                  "currencyPair": "USD/JPY",
                                  "side": "BUY",
                                  "quantity": 10000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "test-trace-id"))
                .andExpect(jsonPath("$.traceId").value("test-trace-id"))
                .andExpect(jsonPath("$.path").value("/api/orders/market"))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    private InvestorApiController controller() {
        return new InvestorApiController(
                orderEngine,
                triggerOrderService,
                accountRepository,
                accountQueryService,
                positionQueryService,
                orderQueryService,
                tradeQueryService,
                triggerOrderQueryService,
                quoteService
        );
    }

    private static Account account(Long id, String userId) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setBalance(new BigDecimal("1000000.0000"));
        account.setCurrency("JPY");
        return account;
    }

    private static MarketOrderExecutionResult executionResult(Account account) {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.FILLED);
        Trade trade = new Trade();
        trade.setId(20L);
        trade.setUserId(account.getUserId());
        trade.setCurrencyPair("USD/JPY");
        trade.setSide(OrderSide.BUY);
        trade.setPrice(new BigDecimal("150.00000000"));
        trade.setQuantity(new BigDecimal("10000.00000000"));
        trade.setExecutedAt(Instant.parse("2026-04-30T00:00:00Z"));
        return new MarketOrderExecutionResult(order, trade, null);
    }
}
