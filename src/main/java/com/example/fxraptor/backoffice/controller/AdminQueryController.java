package com.example.fxraptor.backoffice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.fxraptor.backoffice.dto.AdminOrderResponse;
import com.example.fxraptor.backoffice.dto.AdminPositionResponse;
import com.example.fxraptor.backoffice.dto.AdminTradeResponse;
import com.example.fxraptor.backoffice.dto.ListResponse;
import com.example.fxraptor.backoffice.service.AccountQueryService;
import com.example.fxraptor.backoffice.service.CoverOrderQueryService;
import com.example.fxraptor.backoffice.service.LiquidationQueryService;
import com.example.fxraptor.backoffice.service.OrderQueryService;
import com.example.fxraptor.backoffice.service.PositionQueryService;
import com.example.fxraptor.backoffice.service.TradeQueryService;
import com.example.fxraptor.backoffice.service.TriggerOrderQueryService;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.CoverOrder;
import com.example.fxraptor.domain.LiquidationLog;
import com.example.fxraptor.domain.TriggerOrder;

/**
 * 更新系と分離した管理者向け照会API。
 */
@RestController
@RequestMapping("/admin")
public class AdminQueryController {

    private final AccountQueryService accountQueryService;
    private final OrderQueryService orderQueryService;
    private final TradeQueryService tradeQueryService;
    private final PositionQueryService positionQueryService;
    private final TriggerOrderQueryService triggerOrderQueryService;
    private final CoverOrderQueryService coverOrderQueryService;
    private final LiquidationQueryService liquidationQueryService;

    public AdminQueryController(AccountQueryService accountQueryService,
                                OrderQueryService orderQueryService,
                                TradeQueryService tradeQueryService,
                                PositionQueryService positionQueryService,
                                TriggerOrderQueryService triggerOrderQueryService,
                                CoverOrderQueryService coverOrderQueryService,
                                LiquidationQueryService liquidationQueryService) {
        this.accountQueryService = accountQueryService;
        this.orderQueryService = orderQueryService;
        this.tradeQueryService = tradeQueryService;
        this.positionQueryService = positionQueryService;
        this.triggerOrderQueryService = triggerOrderQueryService;
        this.coverOrderQueryService = coverOrderQueryService;
        this.liquidationQueryService = liquidationQueryService;
    }

    @GetMapping("/accounts/{accountId}")
    public Account getAccount(@PathVariable Long accountId) {
        return accountQueryService.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
    }

    @GetMapping("/orders")
    public ListResponse<AdminOrderResponse> getOrders(
        @RequestParam(required = false) Long accountId
    ) {
        List<AdminOrderResponse> items = orderQueryService.findAllAdminOrders(accountId);
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/trades")
    public ListResponse<AdminTradeResponse> getTrades(
        @RequestParam(required = false) Long accountId
    ) {
        List<AdminTradeResponse> items = tradeQueryService.findAllAdminTrades(accountId);
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/positions")
    public ListResponse<AdminPositionResponse> getPositions(
        @RequestParam(required = false) Long accountId
    ) {
        List<AdminPositionResponse> items = positionQueryService.findAllAdminPositions(accountId);
        return new ListResponse<>(items, items.size());
    }

    @GetMapping("/triggers")
    public List<TriggerOrder> getTriggers() {
        return triggerOrderQueryService.findAll();
    }

    @GetMapping("/covers")
    public List<CoverOrder> getCovers() {
        return coverOrderQueryService.findAll();
    }

    @GetMapping("/liquidations")
    public List<LiquidationLog> getLiquidations() {
        return liquidationQueryService.findAll();
    }
}
