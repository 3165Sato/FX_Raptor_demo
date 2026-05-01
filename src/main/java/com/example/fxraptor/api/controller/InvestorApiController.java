package com.example.fxraptor.api.controller;

import com.example.fxraptor.api.dto.AccountResponseDto;
import com.example.fxraptor.api.dto.CreateTriggerOrderRequestDto;
import com.example.fxraptor.api.dto.MarketOrderRequestDto;
import com.example.fxraptor.api.dto.MarketOrderResponseDto;
import com.example.fxraptor.api.dto.OrderResponseDto;
import com.example.fxraptor.api.dto.PositionResponseDto;
import com.example.fxraptor.api.dto.QuoteResponseDto;
import com.example.fxraptor.api.dto.TradeResponseDto;
import com.example.fxraptor.api.dto.TriggerOrderResponseDto;
import com.example.fxraptor.backoffice.dto.ListResponse;
import com.example.fxraptor.backoffice.service.AccountQueryService;
import com.example.fxraptor.backoffice.service.OrderQueryService;
import com.example.fxraptor.backoffice.service.PositionQueryService;
import com.example.fxraptor.backoffice.service.TradeQueryService;
import com.example.fxraptor.backoffice.service.TriggerOrderQueryService;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderSourceType;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.quote.QuoteService;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.risk.service.TriggerOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

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
    private final QuoteService quoteService;

    public InvestorApiController(OrderEngine orderEngine,
                                 TriggerOrderService triggerOrderService,
                                 AccountRepository accountRepository,
                                 AccountQueryService accountQueryService,
                                 PositionQueryService positionQueryService,
                                 OrderQueryService orderQueryService,
                                 TradeQueryService tradeQueryService,
                                 TriggerOrderQueryService triggerOrderQueryService,
                                 QuoteService quoteService) {
        this.orderEngine = orderEngine;
        this.triggerOrderService = triggerOrderService;
        this.accountRepository = accountRepository;
        this.accountQueryService = accountQueryService;
        this.positionQueryService = positionQueryService;
        this.orderQueryService = orderQueryService;
        this.tradeQueryService = tradeQueryService;
        this.triggerOrderQueryService = triggerOrderQueryService;
        this.quoteService = quoteService;
    }

    @PostMapping("/orders/market")
    public MarketOrderResponseDto placeMarketOrder(@RequestBody MarketOrderRequestDto request) {
        Account account = resolveAccount(request.accountId());
        MarketOrderExecutionResult result = orderEngine.executeMarketOrder(new MarketOrderCommand(
                account.getUserId(),
                request.currencyPair(),
                request.side(),
                request.quantity(),
                OrderSourceType.USER
        ));
        return new MarketOrderResponseDto(
                result.order().getId(),
                result.order().getStatus().name(),
                "market order filled",
                result.trade().getExecutedAt(),
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
        Account account = resolveAccount(request.accountId());
        TriggerOrder order = new TriggerOrder();
        order.setAccountId(account.getId());
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
    public ListResponse<PositionResponseDto> getPositions(@RequestParam(required = false) Long accountId) {
        List<PositionResponseDto> items = positionQueryService.findAllByAccountId(accountId).stream()
                .map(this::toPositionResponse)
                .toList();
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/orders")
    public ListResponse<OrderResponseDto> getOrders(@RequestParam(required = false) Long accountId) {
        List<OrderResponseDto> items = orderQueryService.findAllByAccountId(accountId).stream()
                .map(this::toOrderResponse)
                .toList();
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/trades")
    public ListResponse<TradeResponseDto> getTrades(@RequestParam(required = false) Long accountId) {
        List<TradeResponseDto> items = tradeQueryService.findAllByAccountId(accountId).stream()
                .map(this::toTradeResponse)
                .toList();
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/triggers")
    public ListResponse<TriggerOrderResponseDto> getTriggers() {
        List<TriggerOrderResponseDto> items = triggerOrderQueryService.findAll().stream()
                .map(this::toTriggerResponse)
                .toList();
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/quotes")
    public QuoteResponseDto getQuote(@RequestParam String currencyPair) {
        Quote quote = quoteService.getQuote(currencyPair);
        return new QuoteResponseDto(
                quote.getCurrencyPair(),
                quote.getBid(),
                quote.getAsk(),
                quote.getTimestamp()
        );
    }

    private PositionResponseDto toPositionResponse(Position position) {
        Long accountId = resolveAccountId(position.getUserId());
        Quote quote = loadQuote(position.getCurrencyPair());
        BigDecimal currentPrice = resolveCurrentPrice(position.getSide(), quote);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, currentPrice);
        return new PositionResponseDto(
                position.getId(),
                accountId,
                position.getCurrencyPair(),
                position.getSide(),
                position.getQuantity(),
                position.getAvgPrice(),
                currentPrice,
                unrealizedPnl,
                position.getUpdatedAt()
        );
    }

    private OrderResponseDto toOrderResponse(Order order) {
        Long accountId = resolveAccountId(order.getUserId());
        return new OrderResponseDto(
                order.getId(),
                accountId,
                order.getCurrencyPair(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getStatus(),
                order.getSourceType() == null ? null : order.getSourceType().name(),
                order.getCreatedAt()
        );
    }

    private TradeResponseDto toTradeResponse(Trade trade) {
        Long accountId = resolveAccountId(trade.getUserId());
        return new TradeResponseDto(
                trade.getId(),
                trade.getOrderId(),
                accountId,
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

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId must not be null");
        }

        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
    }

    private Long resolveAccountId(String userId) {
        return accountRepository.findByUserId(userId)
                .map(Account::getId)
                .orElse(null);
    }

    private Quote loadQuote(String currencyPair) {
        try {
            return quoteService.getQuote(currencyPair);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal resolveCurrentPrice(OrderSide side, Quote quote) {
        if (quote == null) {
            return null;
        }
        return side == OrderSide.BUY ? quote.getBid() : quote.getAsk();
    }

    private BigDecimal calculateUnrealizedPnl(Position position, BigDecimal currentPrice) {
        if (currentPrice == null) {
            return null;
        }
        if (position.getSide() == OrderSide.BUY) {
            return currentPrice.subtract(position.getAvgPrice()).multiply(position.getQuantity());
        }
        return position.getAvgPrice().subtract(currentPrice).multiply(position.getQuantity());
    }
}
