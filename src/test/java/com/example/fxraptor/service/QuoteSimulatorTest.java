package com.example.fxraptor.service;

import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.quote.QuoteSimulator;
import com.example.fxraptor.quote.QuoteStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteSimulatorTest {

    @Test
    void alternatesBidByFixedPatternAndKeepsSpread() {
        QuoteStore store = new QuoteStore();
        QuoteSimulator simulator = new QuoteSimulator(store);

        Quote initial = store.getQuote("USD/JPY");
        BigDecimal initialSpread = initial.getAsk().subtract(initial.getBid());

        simulator.simulateOneTick();
        Quote afterFirstTick = store.getQuote("USD/JPY");
        assertThat(afterFirstTick.getBid()).isEqualByComparingTo(initial.getBid().add(new BigDecimal("0.01")));
        assertThat(afterFirstTick.getAsk().subtract(afterFirstTick.getBid())).isEqualByComparingTo(initialSpread);

        simulator.simulateOneTick();
        Quote afterSecondTick = store.getQuote("USD/JPY");
        assertThat(afterSecondTick.getBid()).isEqualByComparingTo(initial.getBid());
        assertThat(afterSecondTick.getAsk().subtract(afterSecondTick.getBid())).isEqualByComparingTo(initialSpread);
    }
}
