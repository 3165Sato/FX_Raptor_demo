package com.example.fxraptor.marketdata.engine;

import com.example.fxraptor.marketdata.model.NormalizedQuote;
import com.example.fxraptor.marketdata.model.RawQuote;
import com.example.fxraptor.marketdata.service.OneSecondAggregator;
import com.example.fxraptor.marketdata.service.QuoteNormalizer;
import com.example.fxraptor.infra.event.QuoteUpdatedEvent;
import com.example.fxraptor.quote.QuoteStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Price Feed入力の司令塔。Tick判定と1秒集約の接続点。
 */
@Component
public class MarketDataEngine {

    private final QuoteNormalizer quoteNormalizer;
    private final QuoteStore quoteStore;
    private final OneSecondAggregator oneSecondAggregator;
    private final ApplicationEventPublisher eventPublisher;

    public MarketDataEngine(QuoteNormalizer quoteNormalizer,
                            QuoteStore quoteStore,
                            OneSecondAggregator oneSecondAggregator,
                            ApplicationEventPublisher eventPublisher) {
        this.quoteNormalizer = quoteNormalizer;
        this.quoteStore = quoteStore;
        this.oneSecondAggregator = oneSecondAggregator;
        this.eventPublisher = eventPublisher;
    }

    public void onTick(RawQuote rawQuote) {
        NormalizedQuote normalized = quoteNormalizer.normalize(rawQuote);
        quoteStore.updateQuote(
                normalized.currencyPair(),
                normalized.bid(),
                normalized.ask(),
                normalized.timestamp()
        );
        eventPublisher.publishEvent(new QuoteUpdatedEvent(
                normalized.currencyPair(),
                normalized.bid(),
                normalized.ask(),
                normalized.timestamp()
        ));
        oneSecondAggregator.add(normalized);
    }
}
