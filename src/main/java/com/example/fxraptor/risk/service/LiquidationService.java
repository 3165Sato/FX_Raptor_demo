package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ロスカット対象ポジションから、内部成行コマンドを生成するサービス。
 */
@Service
public class LiquidationService {

    public List<InternalOrderCommand> createInternalCommands(Account account,
                                                             List<Position> positions,
                                                             Quote quote) {
        return positions.stream()
                .sorted(Comparator.comparing((Position position) -> positionNotional(position, quote)).reversed())
                .map(position -> new InternalOrderCommand(
                        account.getId(),
                        account.getUserId(),
                        position.getCurrencyPair(),
                        oppositeSide(position.getSide()),
                        position.getQuantity()
                ))
                .collect(Collectors.toList());
    }

    private BigDecimal positionNotional(Position position, Quote quote) {
        BigDecimal mark = position.getSide() == OrderSide.BUY ? quote.getBid() : quote.getAsk();
        return mark.multiply(position.getQuantity());
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
