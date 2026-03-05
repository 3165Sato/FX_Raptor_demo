package com.example.fxraptor.service;

import com.example.fxraptor.domain.Quote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 疑似レートのインメモリ保管庫。
 * DB永続化は行わず、アプリ起動中だけ有効な最新Quoteを保持する。
 */
@Component
public class QuoteStore {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    public QuoteStore() {
        Instant now = Instant.now();
        // 初期値は固定で開始する。
        updateQuote("USD/JPY", new BigDecimal("149.98"), new BigDecimal("150.00"), now);
        updateQuote("EUR/JPY", new BigDecimal("161.48"), new BigDecimal("161.50"), now);
    }

    public Quote getQuote(String currencyPair) {
        Quote quote = quotes.get(currencyPair);
        if (quote == null) {
            throw new IllegalArgumentException("quote not found for " + currencyPair);
        }
        return copyOf(quote);
    }

    public Map<String, Quote> getAllQuotes() {
        return quotes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> copyOf(entry.getValue())));
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
        quotes.put(currencyPair, quote);
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
