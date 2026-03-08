package com.example.fxraptor.marketdata.service;

import com.example.fxraptor.marketdata.model.NormalizedQuote;
import com.example.fxraptor.risk.service.TriggerEngine;
import org.springframework.stereotype.Service;

/**
 * ティック到着のたびにTrigger判定を呼び出す。
 */
@Service
public class TickDispatcher {

    private final TriggerEngine triggerEngine;

    public TickDispatcher(TriggerEngine triggerEngine) {
        this.triggerEngine = triggerEngine;
    }

    public void dispatch(NormalizedQuote quote) {
        triggerEngine.evaluateActiveTriggersByCurrencyPair(quote.currencyPair());
    }
}
