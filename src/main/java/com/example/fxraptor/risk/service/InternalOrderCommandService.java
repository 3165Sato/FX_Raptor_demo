package com.example.fxraptor.risk.service;

import com.example.fxraptor.order.engine.OrderEngine;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内部注文コマンドをOrderEngineへ橋渡しするサービス。
 */
@Service
public class InternalOrderCommandService {

    private final OrderEngine orderEngine;

    public InternalOrderCommandService(OrderEngine orderEngine) {
        this.orderEngine = orderEngine;
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
}
