/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.sim.retrieval;

import main.core.sim.memory.MemoryStore;
import main.core.sim.memory.PersistentMemoryStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes a concise retrieval context using persistent facts, session facts, and recent snippets.
 * Heuristics:
 * - Prefer persistent facts first (higher salience), then session facts from MemoryStore
 * - Include a short recency summary from RecencyBuffer
 * - De-duplicate and clamp total tokens by item count
 */
public final class RetrievalRanker {

    public static String buildContext(PersistentMemoryStore persistent,
                                      MemoryStore session,
                                      RecencyBuffer recency,
                                      int maxFacts,
                                      int maxRecentJoin) {
        List<String> facts = new ArrayList<>();
        try {
            if (persistent != null) facts.addAll(persistent.getTopFacts(Math.max(0, maxFacts)));
        } catch (Throwable ignored) {}
        try {
            if (session != null) facts.addAll(session.getTopFacts(Math.max(0, Math.max(0, maxFacts - facts.size()))));
        } catch (Throwable ignored) {}

        // De-duplicate while preserving order
        Set<String> uniq = new LinkedHashSet<>(facts);
        List<String> orderedFacts = new ArrayList<>(uniq);
        String factsStr = orderedFacts.isEmpty() ? "" : orderedFacts.stream().limit(maxFacts).collect(Collectors.joining("; "));

        String recentStr = "";
        try { if (recency != null) recentStr = recency.summary(Math.max(1, maxRecentJoin)); } catch (Throwable ignored) {}

        if (factsStr.isBlank() && recentStr.isBlank()) return "";
        if (factsStr.isBlank()) return "Recent: " + recentStr;
        if (recentStr.isBlank()) return "Facts: " + factsStr;
        return "Facts: " + factsStr + " | Recent: " + recentStr;
    }
}
