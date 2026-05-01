package com.example.fxraptor.quote;

import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.repository.QuoteRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class QuoteStore {

    private final QuoteRepository quoteRepository;

    public QuoteStore(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    @PostConstruct
    public void seedDefaults() {
        seedIfMissing("USD/JPY", new BigDecimal("149.98"), new BigDecimal("150.00"));
        seedIfMissing("EUR/JPY", new BigDecimal("161.48"), new BigDecimal("161.50"));
        seedIfMissing("GBP/JPY", new BigDecimal("192.10"), new BigDecimal("192.12"));
    }

    public Quote getQuote(String currencyPair) {
        return quoteRepository.findById(currencyPair)
                .map(this::copyOf)
                .orElseThrow(() -> new IllegalArgumentException("quote not found for " + currencyPair));
    }

    public Map<String, Quote> getAllQuotes() {
        return quoteRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(Quote::getCurrencyPair, this::copyOf));
    }

    public void updateQuote(String currencyPair, BigDecimal bid, BigDecimal ask, Instant timestamp) {
        if (currencyPair == null || currencyPair.isBlank()) {
            throw new IllegalArgumentException("currencyPair must not be blank");
        }
        if (bid == null || ask == null || timestamp == null) {
            throw new IllegalArgumentException("bid/ask/timestamp must not be null");
        }
        if (ask.compareTo(bid) <= 0) {
            throw new IllegalArgumentException("ask must be greater than bid");
        }
        Quote quote = new Quote();
        quote.setCurrencyPair(currencyPair);
        quote.setBid(bid);
        quote.setAsk(ask);
        quote.setTimestamp(timestamp);
        quoteRepository.save(quote);
    }

    private void seedIfMissing(String currencyPair, BigDecimal bid, BigDecimal ask) {
        if (quoteRepository.existsById(currencyPair)) {
            return;
        }
        updateQuote(currencyPair, bid, ask, Instant.now());
    }

    private Quote copyOf(Quote source) {
        Quote quote = new Quote();
        quote.setCurrencyPair(source.getCurrencyPair());
        quote.setBid(source.getBid());
        quote.setAsk(source.getAsk());
        quote.setTimestamp(source.getTimestamp());
        return quote;
    }
}
