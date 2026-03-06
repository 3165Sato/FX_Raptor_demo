package com.example.fxraptor.order.service;

import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import org.springframework.stereotype.Service;

/**
 * ネッティング後処理の拡張ポイント。
 * 既存の建玉相殺ロジックはMarketOrderService側で維持する。
 */
@Service
public class NettingService {

    public void onMarketOrderExecuted(MarketOrderExecutionResult executionResult) {
        // no-op
    }
}
