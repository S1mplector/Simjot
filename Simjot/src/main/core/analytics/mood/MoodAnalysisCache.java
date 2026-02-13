/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lightweight LRU+TTL cache for derived mood analytics.
 */
public final class MoodAnalysisCache {

    private static final class CacheEntry {
        private final long fingerprint;
        private final long storedAtMillis;
        private final Object value;

        private CacheEntry(long fingerprint, long storedAtMillis, Object value) {
            this.fingerprint = fingerprint;
            this.storedAtMillis = storedAtMillis;
            this.value = value;
        }
    }

    private final int maxEntries;
    private final Map<String, CacheEntry> cache;

    public MoodAnalysisCache(int maxEntries) {
        this.maxEntries = Math.max(8, maxEntries);
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > MoodAnalysisCache.this.maxEntries;
            }
        };
    }

    public synchronized <T> T getOrCompute(String key,
                                           long fingerprint,
                                           long ttlMillis,
                                           Supplier<T> supplier) {
        long now = System.currentTimeMillis();
        CacheEntry existing = cache.get(key);
        if (existing != null) {
            long age = now - existing.storedAtMillis;
            if (existing.fingerprint == fingerprint && age >= 0 && age <= Math.max(0L, ttlMillis)) {
                @SuppressWarnings("unchecked")
                T cached = (T) existing.value;
                return cached;
            }
        }

        T computed = supplier.get();
        cache.put(key, new CacheEntry(fingerprint, now, computed));
        return computed;
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }
}
