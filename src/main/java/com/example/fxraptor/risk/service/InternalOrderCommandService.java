package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.LiquidationLog;
import com.example.fxraptor.domain.OrderSourceType;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.infra.event.LiquidationRequestedEvent;
import com.example.fxraptor.infra.event.TriggerFiredEvent;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.LiquidationLogRepository;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InternalOrderCommandService {

    private final OrderEngine orderEngine;
    private final AccountRepository accountRepository;
    private final LiquidationLogRepository liquidationLogRepository;

    public InternalOrderCommandService(OrderEngine orderEngine,
                                       AccountRepository accountRepository,
                                       LiquidationLogRepository liquidationLogRepository) {
        this.orderEngine = orderEngine;
        this.accountRepository = accountRepository;
        this.liquidationLogRepository = liquidationLogRepository;
    }

    public void dispatch(List<InternalOrderCommand> commands) {
        for (InternalOrderCommand command : commands) {
            orderEngine.executeMarketOrder(new MarketOrderCommand(
                    command.userId(),
                    command.currencyPair(),
                    command.side(),
                    command.quantity(),
                    OrderSourceType.LIQUIDATION
            ));
        }
    }

    @EventListener
    public void onTriggerFired(TriggerFiredEvent event) {
        dispatchByAccountId(
                event.accountId(),
                event.currencyPair(),
                event.side(),
                event.quantity(),
                OrderSourceType.TRIGGER
        );
    }

    @EventListener
    public void onLiquidationRequested(LiquidationRequestedEvent event) {
        MarketOrderExecutionResult result = dispatchByAccountId(
                event.accountId(),
                event.currencyPair(),
                event.side(),
                event.quantity(),
                OrderSourceType.LIQUIDATION
        );

        LiquidationLog liquidationLog = new LiquidationLog();
        liquidationLog.setAccountId(event.accountId());
        liquidationLog.setOrderId(result.order().getId());
        liquidationLog.setTradeId(result.trade().getId());
        liquidationLog.setCurrencyPair(event.currencyPair());
        liquidationLog.setSide(event.side());
        liquidationLog.setQuantity(event.quantity());
        liquidationLog.setLiquidationReason(event.reason());
        liquidationLogRepository.save(liquidationLog);
    }

    private MarketOrderExecutionResult dispatchByAccountId(Long accountId,
                                                           String currencyPair,
                                                           OrderSide side,
                                                           BigDecimal quantity,
                                                           OrderSourceType sourceType) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("account not found for id: " + accountId));
        return orderEngine.executeMarketOrder(new MarketOrderCommand(
                account.getUserId(),
                currencyPair,
                side,
                quantity,
                sourceType
        ));
    }
}
