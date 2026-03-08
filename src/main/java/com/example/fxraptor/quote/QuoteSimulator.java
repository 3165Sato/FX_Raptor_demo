package com.example.fxraptor.quote;

import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.marketdata.engine.MarketDataEngine;
import com.example.fxraptor.marketdata.model.RawQuote;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 疑似的にレートを更新するシミュレータ。
 * テストを決定的にするため、増減は +0.01, -0.01 を交互に適用する。
 */
@Component
public class QuoteSimulator {

    private static final BigDecimal STEP = new BigDecimal("0.01");
    private final QuoteStore quoteStore;
    private final MarketDataEngine marketDataEngine;
    private final AtomicInteger tick = new AtomicInteger(0);

    public QuoteSimulator(QuoteStore quoteStore, MarketDataEngine marketDataEngine) {
        this.quoteStore = quoteStore;
        this.marketDataEngine = marketDataEngine;
    }

    // ユニットテスト向け互換コンストラクタ
    public QuoteSimulator(QuoteStore quoteStore) {
        this.quoteStore = quoteStore;
        this.marketDataEngine = null;
    }

    @Scheduled(fixedRate = 1000)
    public void simulate() {
        simulateOneTick();
    }

    public void simulateOneTick() {
        BigDecimal delta = (tick.getAndIncrement() % 2 == 0) ? STEP : STEP.negate();
        Instant now = Instant.now();
        for (Quote current : quoteStore.getAllQuotes().values()) {
            BigDecimal spread = current.getAsk().subtract(current.getBid());
            BigDecimal nextBid = current.getBid().add(delta);
            BigDecimal nextAsk = nextBid.add(spread);
            if (marketDataEngine != null) {
                marketDataEngine.onTick(new RawQuote(current.getCurrencyPair(), nextBid, nextAsk, now));
            } else {
                quoteStore.updateQuote(current.getCurrencyPair(), nextBid, nextAsk, now);
            }
        }
    }
}
