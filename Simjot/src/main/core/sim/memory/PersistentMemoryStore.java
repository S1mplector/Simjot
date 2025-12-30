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

import main.core.sim.data.SimDataGateway;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Persistent memory store for Sim that derives simple, durable facts from the user's entries.
 *
 * Implementation is deliberately lightweight:
 * - Facts are stored as unique sanitized strings in a UTF-8 text file (one per line).
 * - Facts are derived using simple pattern heuristics from journal entries.
 * - Provides summaries for prompts and proactive check-ins.
 */
public final class PersistentMemoryStore {
    private static final String FILE_NAME = "sim_memories.txt";

    private final LinkedHashSet<String> facts = new LinkedHashSet<>();

    // Simple patterns to extract stable preferences or self-descriptions
    private static final Pattern[] FACT_PATTERNS = new Pattern[]{
            Pattern.compile("\\bI like ([^.!?\\n]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bI love ([^.!?\\n]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bMy favorite (?:thing|food|place|music|song|book|hobby|movie|game|workout|tea|coffee) is ([^.!?\\n]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bI am (?:learning|working on|trying to|planning to) ([^.!?\\n]{2,80})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bI (?:struggle with|have trouble with) ([^.!?\\n]{2,80})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bI feel (?:most )?([a-zA-Z ]{3,20}) when ([^.!?\\n]{2,80})", Pattern.CASE_INSENSITIVE)
    };

    public synchronized void load() {
        File dir = AppDirectories.folder(AppDirectories.Type.SETTINGS);
        File f = new File(dir, FILE_NAME);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = sanitize(line);
                if (!s.isEmpty()) facts.add(s);
            }
        } catch (IOException ignored) {}
    }

    public synchronized void save() {
        File dir = AppDirectories.folder(AppDirectories.Type.SETTINGS);
        File f = new File(dir, FILE_NAME);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8))) {
            for (String fact : facts) pw.println(fact);
        } catch (IOException ignored) {}
    }

    public synchronized void addFact(String fact) {
        String s = sanitize(fact);
        if (!s.isEmpty()) {
            if (facts.add(s)) save();
        }
    }

    public synchronized List<String> getTopFacts(int k) {
        if (k <= 0 || facts.isEmpty()) return List.of();
        return facts.stream().limit(k).collect(Collectors.toList());
    }

    public synchronized String getFactsSummary(int k) {
        List<String> top = getTopFacts(k);
        if (top.isEmpty()) return "";
        return String.join("; ", top);
    }

    /**
     * Ingest a single piece of free-form text (e.g., current typing or a new entry)
     * and derive any durable facts from it. Returns the number of new facts added.
     */
    public synchronized int ingestText(String text) {
        int added = extractFactsFrom(text);
        if (added > 0) save();
        return added;
    }

    /**
     * @return true if the store currently has at least one fact.
     */
    public synchronized boolean hasAnyFacts() {
        return !facts.isEmpty();
    }

    /**
     * Scan all notebooks and entries to derive facts. Intended to run on startup or occasionally.
     * This is a synchronous method; run it off the EDT.
     */
    public synchronized int ingestAllNotebooks(SimDataGateway data) {
        if (data == null) return 0;
        int added = 0;
        try {
            for (NotebookInfo nb : data.listNotebooks()) {
                if (nb == null) continue;
                for (File entry : data.listEntriesByModifiedDesc(nb, 200)) { // cap for performance
                    String text = data.readEntry(entry, 5000); // read last 5k chars for recency
                    added += extractFactsFrom(text);
                }
            }
        } catch (Throwable ignored) {}
        if (added > 0) save();
        return added;
    }

    private int extractFactsFrom(String text) {
        if (text == null || text.isBlank()) return 0;
        int before = facts.size();
        String t = text.replace('\r', ' ').replace('\n', ' ');
        for (Pattern p : FACT_PATTERNS) {
            Matcher m = p.matcher(t);
            while (m.find()) {
                String phrase;
                if (m.groupCount() >= 2 && m.group(2) != null) {
                    // For patterns with two groups, join succinctly
                    phrase = m.group(1).trim() + " when " + m.group(2).trim();
                } else {
                    phrase = m.group(1).trim();
                }
                String candidate = collapse("" + phrase);
                if (!candidate.isEmpty()) facts.add(candidate);
                if (facts.size() - before > 24) break; // avoid too many from one entry
            }
        }
        return Math.max(0, facts.size() - before);
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        String out = s.replace('\n', ' ').replace('\r', ' ');
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > 240) out = out.substring(0, 239) + "…";
        return out;
    }

    private static String collapse(String s) {
        String out = sanitize(s);
        // Remove leading determiners/punctuation for more natural fact phrasing
        out = out.replaceAll("^(?:the |a |an )", "");
        return out;
    }
}
