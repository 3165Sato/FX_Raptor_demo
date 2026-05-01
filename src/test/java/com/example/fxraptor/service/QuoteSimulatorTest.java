package com.example.fxraptor.service;

import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.quote.QuoteSimulator;
import com.example.fxraptor.quote.QuoteStore;
import com.example.fxraptor.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSimulatorTest {

    @Test
    void alternatesBidByFixedPatternAndKeepsSpread() {
        QuoteRepository quoteRepository = mock(QuoteRepository.class);
        Map<String, Quote> store = new LinkedHashMap<>();

        when(quoteRepository.existsById(any())).thenAnswer(invocation -> store.containsKey(invocation.getArgument(0)));
        when(quoteRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(quoteRepository.findAll()).thenAnswer(invocation -> store.values().stream().toList());
        doAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            store.put(quote.getCurrencyPair(), copyOf(quote));
            return quote;
        }).when(quoteRepository).save(any(Quote.class));

        QuoteStore quoteStore = new QuoteStore(quoteRepository);
        quoteStore.seedDefaults();
        QuoteSimulator simulator = new QuoteSimulator(quoteStore);

        Quote initial = quoteStore.getQuote("USD/JPY");
        BigDecimal initialSpread = initial.getAsk().subtract(initial.getBid());

        simulator.simulateOneTick();
        Quote afterFirstTick = quoteStore.getQuote("USD/JPY");
        assertThat(afterFirstTick.getBid()).isEqualByComparingTo(initial.getBid().add(new BigDecimal("0.01")));
        assertThat(afterFirstTick.getAsk().subtract(afterFirstTick.getBid())).isEqualByComparingTo(initialSpread);

        simulator.simulateOneTick();
        Quote afterSecondTick = quoteStore.getQuote("USD/JPY");
        assertThat(afterSecondTick.getBid()).isEqualByComparingTo(initial.getBid());
        assertThat(afterSecondTick.getAsk().subtract(afterSecondTick.getBid())).isEqualByComparingTo(initialSpread);
    }

    private static Quote copyOf(Quote source) {
        Quote quote = new Quote();
        quote.setCurrencyPair(source.getCurrencyPair());
        quote.setBid(source.getBid());
        quote.setAsk(source.getAsk());
        quote.setTimestamp(source.getTimestamp());
        return quote;
    }
}
