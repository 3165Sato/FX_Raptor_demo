package com.example.fxraptor.cache;

import com.example.fxraptor.domain.Position;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学習用途の簡易キャッシュ。必要時にポジションのスナップショットを一時保持する。
 */
@Component
public class PositionCache {

    private final Map<String, Position> cache = new ConcurrentHashMap<>();

    public void put(String key, Position position) {
        cache.put(key, position);
    }

    public Position get(String key) {
        return cache.get(key);
    }

    public void evict(String key) {
        cache.remove(key);
    }
}
