package com.example.fxraptor.service;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.order.model.CoverOrderCommand;
import com.example.fxraptor.order.service.CoverDecisionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CoverDecisionServiceTest {

    @Test
    void decidesFullCoverWithOppositeSide() {
        CoverDecisionService service = new CoverDecisionService();
        Trade trade = new Trade();
        trade.setId(1L);
        trade.setCurrencyPair("USD/JPY");
        trade.setSide(OrderSide.BUY);
        trade.setQuantity(new BigDecimal("2.00000000"));
        trade.setPrice(new BigDecimal("150.10000000"));

        Optional<CoverOrderCommand> result = service.decide(trade, 10L);

        assertThat(result).isPresent();
        CoverOrderCommand command = result.orElseThrow();
        assertThat(command.tradeId()).isEqualTo(1L);
        assertThat(command.accountId()).isEqualTo(10L);
        assertThat(command.side()).isEqualTo(OrderSide.SELL);
        assertThat(command.requestedPrice()).isEqualByComparingTo("150.10000000");
        assertThat(command.quantity()).isEqualByComparingTo("2.00000000");
    }
}
