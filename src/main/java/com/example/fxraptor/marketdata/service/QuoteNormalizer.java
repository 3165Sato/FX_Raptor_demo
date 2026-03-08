package com.example.fxraptor.marketdata.service;

import com.example.fxraptor.marketdata.model.NormalizedQuote;
import com.example.fxraptor.marketdata.model.RawQuote;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;

/**
 * 外部ティックの入力値を最小限に正規化する。
 */
@Service
public class QuoteNormalizer {

    private static final int PRICE_SCALE = 8;

    public NormalizedQuote normalize(RawQuote rawQuote) {
        if (rawQuote == null) {
            throw new IllegalArgumentException("rawQuote must not be null");
        }
        if (rawQuote.currencyPair() == null || rawQuote.currencyPair().isBlank()) {
            throw new IllegalArgumentException("currencyPair must not be blank");
        }
        if (rawQuote.bid() == null || rawQuote.ask() == null) {
            throw new IllegalArgumentException("bid/ask must not be null");
        }
        if (rawQuote.ask().compareTo(rawQuote.bid()) < 0) {
            throw new IllegalArgumentException("ask must not be lower than bid");
        }

        Instant timestamp = rawQuote.timestamp() == null ? Instant.now() : rawQuote.timestamp();
        return new NormalizedQuote(
                rawQuote.currencyPair(),
                rawQuote.bid().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                rawQuote.ask().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                timestamp
        );
    }
}
