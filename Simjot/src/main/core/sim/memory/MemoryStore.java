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

package main.core.sim.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Minimal local memory store.
 * - facts: user preferences or recurring statements (manual/API-fed later)
 * - episodic: recent snippets for lightweight session summary
 *
 * This is intentionally simple and in-memory to start. Swap with SQLite/ANN later.
 */
public final class MemoryStore {
    private final List<String> facts = new ArrayList<>();
    private final Deque<String> episodic = new ArrayDeque<>(); // bounded queue of recent lines
    private final int episodicLimit = 20; // keep last N snippets

    public synchronized void addFact(String fact) {
        if (fact == null) return;
        String f = sanitize(fact);
        if (f.isEmpty()) return;
        if (!facts.contains(f)) facts.add(f);
    }

    public synchronized List<String> getTopFacts(int k) {
        if (k <= 0) return List.of();
        int n = Math.min(k, facts.size());
        return new ArrayList<>(facts.subList(0, n));
    }

    public synchronized void addEpisodic(String snippet) {
        String s = sanitize(snippet);
        if (s.isEmpty()) return;
        episodic.addLast(s);
        while (episodic.size() > episodicLimit) episodic.removeFirst();
    }

    public synchronized String getConversationSummary() {
        if (episodic.isEmpty()) return "";
        // Very lightweight summary: join last up to 5 snippets into one paragraph
        return episodic.stream().skip(Math.max(0, episodic.size() - 5))
                .collect(Collectors.joining(" | "));
    }

    public synchronized String getFactsSummary(int k) {
        List<String> top = getTopFacts(k);
        if (top.isEmpty()) return "";
        return String.join("; ", top);
    }

    private static String sanitize(String s) {
        String out = s.replace('\n', ' ').replace('\r', ' ');
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > 240) out = out.substring(0, 239) + "…";
        return out;
    }
}
