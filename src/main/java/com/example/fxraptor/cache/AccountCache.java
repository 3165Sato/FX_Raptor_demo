package com.example.fxraptor.cache;

import com.example.fxraptor.domain.Account;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学習用途の簡易キャッシュ。口座スナップショットを短期保持する。
 */
@Component
public class AccountCache {

    private final Map<String, Account> cache = new ConcurrentHashMap<>();

    public void put(String userId, Account account) {
        cache.put(userId, account);
    }

    public Account get(String userId) {
        return cache.get(userId);
    }

    public void evict(String userId) {
        cache.remove(userId);
    }
}
