package com.example.fxraptor.service;

import com.example.fxraptor.domain.Quote;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * アプリ内でQuoteを参照するための取得API。
 */
@Service
public class QuoteService {

    private final QuoteStore quoteStore;

    public QuoteService(QuoteStore quoteStore) {
        this.quoteStore = quoteStore;
    }

    public Quote getQuote(String currencyPair) {
        return quoteStore.getQuote(currencyPair);
    }

    public Map<String, Quote> getAllQuotes() {
        return quoteStore.getAllQuotes();
    }
}
