package com.example.fxraptor.api.controller;

import com.example.fxraptor.api.dto.AccountResponseDto;
import com.example.fxraptor.api.dto.CreateTriggerOrderRequestDto;
import com.example.fxraptor.api.dto.MarketOrderRequestDto;
import com.example.fxraptor.api.dto.MarketOrderResponseDto;
import com.example.fxraptor.api.dto.OrderResponseDto;
import com.example.fxraptor.api.dto.PositionResponseDto;
import com.example.fxraptor.api.dto.TradeResponseDto;
import com.example.fxraptor.api.dto.TriggerOrderResponseDto;
import com.example.fxraptor.backoffice.service.AccountQueryService;
import com.example.fxraptor.backoffice.service.OrderQueryService;
import com.example.fxraptor.backoffice.service.PositionQueryService;
import com.example.fxraptor.backoffice.service.TradeQueryService;
import com.example.fxraptor.backoffice.service.TriggerOrderQueryService;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.risk.service.TriggerOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 投資家向け公開API相当の最小チャネル。
 * 管理系の /admin と分離して /api で提供する。
 */
@RestController
@RequestMapping("/api")
public class InvestorApiController {

    private final OrderEngine orderEngine;
    private final TriggerOrderService triggerOrderService;
    private final AccountRepository accountRepository;
    private final AccountQueryService accountQueryService;
    private final PositionQueryService positionQueryService;
    private final OrderQueryService orderQueryService;
    private final TradeQueryService tradeQueryService;
    private final TriggerOrderQueryService triggerOrderQueryService;

    public InvestorApiController(OrderEngine orderEngine,
                                 TriggerOrderService triggerOrderService,
                                 AccountRepository accountRepository,
                                 AccountQueryService accountQueryService,
                                 PositionQueryService positionQueryService,
                                 OrderQueryService orderQueryService,
                                 TradeQueryService tradeQueryService,
                                 TriggerOrderQueryService triggerOrderQueryService) {
        this.orderEngine = orderEngine;
        this.triggerOrderService = triggerOrderService;
        this.accountRepository = accountRepository;
        this.accountQueryService = accountQueryService;
        this.positionQueryService = positionQueryService;
        this.orderQueryService = orderQueryService;
        this.tradeQueryService = tradeQueryService;
        this.triggerOrderQueryService = triggerOrderQueryService;
    }

    @PostMapping("/orders/market")
    public MarketOrderResponseDto placeMarketOrder(@RequestBody MarketOrderRequestDto request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        MarketOrderExecutionResult result = orderEngine.executeMarketOrder(new MarketOrderCommand(
                account.getUserId(),
                request.currencyPair(),
                request.side(),
                request.quantity()
        ));
        return new MarketOrderResponseDto(
                result.order().getId(),
                result.order().getStatus(),
                result.trade().getId(),
                result.trade().getCurrencyPair(),
                result.trade().getSide(),
                result.trade().getPrice(),
                result.trade().getQuantity(),
                result.trade().getExecutedAt()
        );
    }

    @PostMapping("/triggers")
    public TriggerOrderResponseDto createTrigger(@RequestBody CreateTriggerOrderRequestDto request) {
        TriggerOrder order = new TriggerOrder();
        order.setAccountId(request.accountId());
        order.setCurrencyPair(request.currencyPair());
        order.setSide(request.side());
        order.setTriggerType(request.triggerType());
        order.setTriggerPrice(request.triggerPrice());
        order.setQuantity(request.quantity());
        TriggerOrder created = triggerOrderService.create(order);
        return toTriggerResponse(created);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponseDto getAccount(@PathVariable Long accountId) {
        Account account = accountQueryService.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        return new AccountResponseDto(
                account.getId(),
                account.getUserId(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    @GetMapping("/positions")
    public List<PositionResponseDto> getPositions() {
        return positionQueryService.findAll().stream()
                .map(this::toPositionResponse)
                .toList();
    }

    @GetMapping("/orders")
    public List<OrderResponseDto> getOrders() {
        return orderQueryService.findAll().stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @GetMapping("/trades")
    public List<TradeResponseDto> getTrades() {
        return tradeQueryService.findAll().stream()
                .map(this::toTradeResponse)
                .toList();
    }

    @GetMapping("/triggers")
    public List<TriggerOrderResponseDto> getTriggers() {
        return triggerOrderQueryService.findAll().stream()
                .map(this::toTriggerResponse)
                .toList();
    }

    private PositionResponseDto toPositionResponse(Position position) {
        return new PositionResponseDto(
                position.getId(),
                position.getUserId(),
                position.getCurrencyPair(),
                position.getSide(),
                position.getQuantity(),
                position.getAvgPrice(),
                position.getVersion(),
                position.getUpdatedAt()
        );
    }

    private OrderResponseDto toOrderResponse(Order order) {
        return new OrderResponseDto(
                order.getId(),
                order.getUserId(),
                order.getCurrencyPair(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    private TradeResponseDto toTradeResponse(Trade trade) {
        return new TradeResponseDto(
                trade.getId(),
                trade.getOrderId(),
                trade.getUserId(),
                trade.getCurrencyPair(),
                trade.getSide(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getExecutedAt()
        );
    }

    private TriggerOrderResponseDto toTriggerResponse(TriggerOrder triggerOrder) {
        return new TriggerOrderResponseDto(
                triggerOrder.getId(),
                triggerOrder.getAccountId(),
                triggerOrder.getCurrencyPair(),
                triggerOrder.getSide(),
                triggerOrder.getTriggerType(),
                triggerOrder.getTriggerPrice(),
                triggerOrder.getQuantity(),
                triggerOrder.getStatus(),
                triggerOrder.getCreatedAt(),
                triggerOrder.getUpdatedAt()
        );
    }
}
