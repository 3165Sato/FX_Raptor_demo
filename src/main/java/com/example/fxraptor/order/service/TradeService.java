package com.example.fxraptor.order.service;

import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import org.springframework.stereotype.Service;

/**
 * 約定後処理の拡張ポイント。
 * 現時点では既存ロジックを保持するため、処理は持たない。
 */
@Service
public class TradeService {

    public void onMarketOrderExecuted(MarketOrderExecutionResult executionResult) {
        // no-op
    }
}
