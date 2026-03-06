package com.example.fxraptor.quote;

import com.example.fxraptor.domain.Quote;
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
    private final AtomicInteger tick = new AtomicInteger(0);

    public QuoteSimulator(QuoteStore quoteStore) {
        this.quoteStore = quoteStore;
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
            quoteStore.updateQuote(current.getCurrencyPair(), nextBid, nextAsk, now);
        }
    }
}
