package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.CoverMode;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.model.CoverOrderCommand;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 顧客Tradeに対してカバー注文を出すべきかを判定する。
 * 現在は学習用としてFULL固定。
 */
@Service
public class CoverDecisionService {

    public Optional<CoverOrderCommand> decide(Trade trade, Long accountId) {
        CoverMode mode = CoverMode.FULL;
        if (mode == CoverMode.DISABLED || accountId == null) {
            return Optional.empty();
        }

        return Optional.of(new CoverOrderCommand(
                trade.getId(),
                accountId,
                trade.getCurrencyPair(),
                oppositeSide(trade.getSide()),
                trade.getQuantity(),
                trade.getPrice(),
                mode
        ));
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
