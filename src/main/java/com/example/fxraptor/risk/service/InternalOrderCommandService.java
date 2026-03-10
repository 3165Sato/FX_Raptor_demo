package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.infra.event.LiquidationRequestedEvent;
import com.example.fxraptor.infra.event.TriggerFiredEvent;
import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 内部注文コマンドをOrderEngineへ橋渡しするサービス。
 */
@Service
public class InternalOrderCommandService {

    private final OrderEngine orderEngine;
    private final AccountRepository accountRepository;

    public InternalOrderCommandService(OrderEngine orderEngine,
                                       AccountRepository accountRepository) {
        this.orderEngine = orderEngine;
        this.accountRepository = accountRepository;
    }

    public void dispatch(List<InternalOrderCommand> commands) {
        for (InternalOrderCommand command : commands) {
            orderEngine.executeMarketOrder(new MarketOrderCommand(
                    command.userId(),
                    command.currencyPair(),
                    command.side(),
                    command.quantity()
            ));
        }
    }

    @EventListener
    public void onTriggerFired(TriggerFiredEvent event) {
        dispatchByAccountId(event.accountId(), event.currencyPair(), event.side(), event.quantity());
    }

    @EventListener
    public void onLiquidationRequested(LiquidationRequestedEvent event) {
        dispatchByAccountId(event.accountId(), event.currencyPair(), event.side(), event.quantity());
    }

    private void dispatchByAccountId(Long accountId, String currencyPair, OrderSide side, BigDecimal quantity) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("account not found for id: " + accountId));
        orderEngine.executeMarketOrder(new MarketOrderCommand(
                account.getUserId(),
                currencyPair,
                side,
                quantity
        ));
    }
}
