/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.retrieval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import main.infrastructure.ffi.NativeAccess;

/**
 * Thread-safe buffer of recent snippets with timestamps for lightweight recency-aware context.
 */
public final class RecencyBuffer {
    private static final class Item { final long t; final String s; Item(long t, String s){ this.t=t; this.s=s; } }
    private final Deque<Item> q = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxItems;
    private final long ttlMs;

    public RecencyBuffer(int maxItems, long ttlMs) {
        this.maxItems = Math.max(1, maxItems);
        this.ttlMs = Math.max(1000L, ttlMs);
    }

    public void add(String snippet){
        if (snippet == null) return;
        String s = sanitize(snippet);
        if (s.isBlank()) return;
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            q.addLast(new Item(now, s));
            while (q.size() > maxItems) q.removeFirst();
            prune(now);
        } finally { lock.unlock(); }
    }

    public String summary(int maxJoin) {
        lock.lock();
        try {
            prune(System.currentTimeMillis());
            if (q.isEmpty()) return "";
            int n = Math.max(1, Math.min(maxJoin, q.size()));
            return q.stream().skip(Math.max(0, q.size() - n))
                    .map(it -> it.s)
                    .collect(Collectors.joining(" | "));
        } finally { lock.unlock(); }
    }

    private void prune(long now){
        Iterator<Item> it = q.iterator();
        while (it.hasNext()){
            Item i = it.next();
            if (now - i.t > ttlMs) it.remove();
        }
    }

    private static String sanitize(String s){
        if (s == null || s.isEmpty()) return "";
        
        // Try native sanitization first (faster)
        String nativeSanitized = NativeAccess.stringSanitize(s);
        if (nativeSanitized != null) {
            String out = nativeSanitized.trim();
            if (out.length() > 240) out = out.substring(0, 239) + "…";
            return out;
        }
        
        // Java fallback
        String out = s.replace('\n',' ').replace('\r',' ');
        out = out.replaceAll("\\s+"," ").trim();
        if (out.length() > 240) out = out.substring(0,239) + "…";
        return out;
    }
}
