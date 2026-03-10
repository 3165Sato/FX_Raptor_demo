package com.example.fxraptor.marketdata.service;

import com.example.fxraptor.marketdata.model.AggregatedQuote;
import com.example.fxraptor.marketdata.model.NormalizedQuote;
import com.example.fxraptor.infra.event.OneSecondAggregatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ティックを1秒足に集約し、秒が切り替わったらRiskEngineへ通知する。
 */
@Service
public class OneSecondAggregator {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public OneSecondAggregator(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void add(NormalizedQuote quote) {
        long epochSecond = quote.timestamp().getEpochSecond();
        Bucket currentBucket = buckets.get(quote.currencyPair());
        if (currentBucket == null) {
            buckets.put(quote.currencyPair(), Bucket.from(quote, epochSecond));
            return;
        }
        if (currentBucket.epochSecond != epochSecond) {
            publishOneSecondEvent(currentBucket.toAggregatedQuote(quote.currencyPair()));
            buckets.put(quote.currencyPair(), Bucket.from(quote, epochSecond));
            return;
        }
        currentBucket.accept(quote);
    }

    private void publishOneSecondEvent(AggregatedQuote quote) {
        Instant start = quote.second();
        eventPublisher.publishEvent(new OneSecondAggregatedEvent(
                quote.currencyPair(),
                quote.highBid(),
                quote.lowBid(),
                quote.closeBid(),
                quote.highAsk(),
                quote.lowAsk(),
                quote.closeAsk(),
                start,
                start.plusSeconds(1)
        ));
    }

    private static final class Bucket {
        private final long epochSecond;
        private BigDecimal highBid;
        private BigDecimal lowBid;
        private BigDecimal closeBid;
        private BigDecimal highAsk;
        private BigDecimal lowAsk;
        private BigDecimal closeAsk;

        private Bucket(long epochSecond,
                       BigDecimal highBid,
                       BigDecimal lowBid,
                       BigDecimal closeBid,
                       BigDecimal highAsk,
                       BigDecimal lowAsk,
                       BigDecimal closeAsk) {
            this.epochSecond = epochSecond;
            this.highBid = highBid;
            this.lowBid = lowBid;
            this.closeBid = closeBid;
            this.highAsk = highAsk;
            this.lowAsk = lowAsk;
            this.closeAsk = closeAsk;
        }

        private static Bucket from(NormalizedQuote quote, long epochSecond) {
            return new Bucket(
                    epochSecond,
                    quote.bid(),
                    quote.bid(),
                    quote.bid(),
                    quote.ask(),
                    quote.ask(),
                    quote.ask()
            );
        }

        private void accept(NormalizedQuote quote) {
            this.highBid = this.highBid.max(quote.bid());
            this.lowBid = this.lowBid.min(quote.bid());
            this.closeBid = quote.bid();
            this.highAsk = this.highAsk.max(quote.ask());
            this.lowAsk = this.lowAsk.min(quote.ask());
            this.closeAsk = quote.ask();
        }

        private AggregatedQuote toAggregatedQuote(String currencyPair) {
            return new AggregatedQuote(
                    currencyPair,
                    highBid,
                    lowBid,
                    closeBid,
                    highAsk,
                    lowAsk,
                    closeAsk,
                    Instant.ofEpochSecond(epochSecond)
            );
        }
    }
}
