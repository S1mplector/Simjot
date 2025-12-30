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

package main.core.sim.proactive;

import main.infrastructure.io.AppDirectories;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight persistent store for proactive trigger statistics.
 * Persists per-trigger counts and last-fired timestamp to a UTF-8 file.
 */
public final class TriggerStatsStore {
    private static final String FILE_NAME = "sim_trigger_stats.tsv";

    private final Map<String, Stat> stats = Collections.synchronizedMap(new LinkedHashMap<>());

    public static final class Stat {
        public long count;
        public long lastFiredAtMs;

        public Stat(long count, long lastFiredAtMs) {
            this.count = count;
            this.lastFiredAtMs = lastFiredAtMs;
        }
    }

    public TriggerStatsStore() {
        try { load(); } catch (Throwable ignored) {}
    }

    public void record(String triggerKey) {
        if (triggerKey == null || triggerKey.isBlank()) return;
        long now = System.currentTimeMillis();
        synchronized (stats) {
            Stat s = stats.get(triggerKey);
            if (s == null) s = new Stat(0, 0);
            s.count += 1;
            s.lastFiredAtMs = now;
            stats.put(triggerKey, s);
        }
        try { save(); } catch (Throwable ignored) {}
    }

    public Stat get(String triggerKey) {
        synchronized (stats) { return stats.get(triggerKey); }
    }

    private void load() throws IOException {
        File dir = AppDirectories.folder(AppDirectories.Type.SETTINGS);
        File f = new File(dir, FILE_NAME);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    String key = parts[0];
                    long count = parseLong(parts[1]);
                    long last = parseLong(parts[2]);
                    stats.put(key, new Stat(count, last));
                }
            }
        }
    }

    private void save() throws IOException {
        File dir = AppDirectories.folder(AppDirectories.Type.SETTINGS);
        File f = new File(dir, FILE_NAME);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8))) {
            synchronized (stats) {
                for (Map.Entry<String, Stat> e : stats.entrySet()) {
                    Stat s = e.getValue();
                    pw.println(e.getKey() + "\t" + s.count + "\t" + s.lastFiredAtMs);
                }
            }
        }
    }

    private static long parseLong(String s){
        try { return Long.parseLong(s.trim()); } catch (Throwable ignored) { return 0L; }
    }
}
