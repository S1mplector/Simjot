/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import main.core.analytics.MoodAnalyticsEngine;
import main.core.analytics.MoodAnalyticsEngine.AnalyticsResult;
import main.core.analytics.MoodAnalyticsEngine.DailyStats;
import main.core.analytics.MoodAnalyticsEngine.MoodSample;
import main.core.security.EncryptionManager;
import main.core.security.crypto.EncryptedMetadata;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.NativeJson;

final class MoodChartModel {
    private static final String META_PREFIX = "SJMETA:";
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private static final class EntryInfo {
        final LocalDateTime ts;
        final String title;
        EntryInfo(LocalDateTime ts, String title) {
            this.ts = ts;
            this.title = title;
        }
    }

    private static final class MetaHeader {
        final String title;
        final long savedAt;
        MetaHeader(String title, long savedAt) {
            this.title = title;
            this.savedAt = savedAt;
        }
    }

    static final class EntryRef {
        final File file;
        final LocalDateTime ts;
        final String title;
        EntryRef(File f, LocalDateTime t, String title){
            this.file = f;
            this.ts = t;
            this.title = title;
        }
    }
    static final class Details { final LocalDateTime ts; final int joy, calm, gratitude, energy, sadness, anger, anxiety, stress; Details(LocalDateTime ts,int joy,int calm,int gratitude,int energy,int sadness,int anger,int anxiety,int stress){ this.ts=ts; this.joy=joy; this.calm=calm; this.gratitude=gratitude; this.energy=energy; this.sadness=sadness; this.anger=anger; this.anxiety=anxiety; this.stress=stress; } }

    private final java.util.List<LocalDate> dayList = new ArrayList<>();
    private final java.util.List<Double> avgMoodList = new ArrayList<>();
    private final java.util.List<Double> smoothedMoodList = new ArrayList<>();
    private final java.util.Map<LocalDate, java.util.List<File>> entriesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<LocalDateTime>> moodTimesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<EntryRef>> entryTimesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<Details>> detailsByDate = new java.util.HashMap<>();

    // Enhanced analytics results
    private AnalyticsResult lastAnalytics;

    boolean load(int rangeIndex){
        dayList.clear(); avgMoodList.clear(); smoothedMoodList.clear();
        entriesByDate.clear(); moodTimesByDate.clear(); entryTimesByDate.clear(); detailsByDate.clear();
        lastAnalytics = null;

        int daysLimit = switch(rangeIndex){ case 0 -> 7; case 1 -> 30; case 2 -> 90; case 3 -> 365; default -> 0; };

        // Use enhanced analytics engine
        MoodAnalyticsEngine engine = MoodAnalyticsEngine.get();
        AnalyticsResult analytics = engine.analyze(daysLimit, 7); // 7-day smoothing window
        lastAnalytics = analytics;

        if (analytics.dates.isEmpty()) return false;

        // Populate lists from analytics
        dayList.addAll(analytics.dates);
        avgMoodList.addAll(analytics.dailyAverages);
        smoothedMoodList.addAll(analytics.smoothedAverages);

        // Convert detailed stats to legacy Details format for backward compatibility
        for (LocalDate d : analytics.dates) {
            DailyStats stats = analytics.dailyStats.get(d);
            if (stats == null) continue;
            if (!stats.samples.isEmpty()) {
                for (MoodSample sample : stats.samples) {
                    if (sample.timestamp != null) {
                        moodTimesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(sample.timestamp);
                    }
                    if (sample.hasDetails()) {
                        detailsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(
                            new Details(sample.timestamp,
                                sample.joy, sample.calm, sample.gratitude, sample.energy,
                                sample.sadness, sample.anger, sample.anxiety, sample.stress));
                    }
                }
            } else if (stats.sampleCount > 0 && hasAnyDetailAverages(stats)) {
                detailsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(
                    new Details(d.atStartOfDay(),
                        (int) Math.round(stats.avgJoy),
                        (int) Math.round(stats.avgCalm),
                        (int) Math.round(stats.avgGratitude),
                        (int) Math.round(stats.avgEnergy),
                        (int) Math.round(stats.avgSadness),
                        (int) Math.round(stats.avgAnger),
                        (int) Math.round(stats.avgAnxiety),
                        (int) Math.round(stats.avgStress))
                );
            }
        }

        // Load journal entries for date mapping
        loadJournalEntries();

        return true;
    }

    private void loadJournalEntries() {
        try {
            Set<LocalDate> relevantDates = new HashSet<>(dayList);
            if (relevantDates.isEmpty()) return;
            NotebookStore store = new NotebookStore();
            java.util.List<NotebookInfo> nbs = store.list();
            for (NotebookInfo nb : nbs) {
                if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) continue;
                File folder = nb.getFolder();
                if (folder == null) continue;
                File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
                if (files == null) continue;
                for (File f : files) {
                    LocalDate d; LocalDateTime ts;
                    try {
                        String name = f.getName();
                        if (name.matches("\\d{8}_\\d{6}.*\\.txt")) {
                            String ymd = name.substring(0, 8);
                            String hms = name.substring(9, 15);
                            ts = LocalDateTime.parse(ymd+"_"+hms, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            d = ts.toLocalDate();
                        } else {
                            ts = java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                            d = ts.toLocalDate();
                        }
                    } catch (Throwable ignored) { continue; }
                    EntryInfo info = readEntryInfo(f, ts);
                    LocalDate entryDate = info.ts.toLocalDate();
                    if (!relevantDates.contains(entryDate)) continue;
                    entriesByDate.computeIfAbsent(entryDate, k -> new ArrayList<>()).add(f);
                    entryTimesByDate.computeIfAbsent(entryDate, k -> new ArrayList<>()).add(new EntryRef(f, info.ts, info.title));
                }
            }
        } catch (Throwable ignored) {}
    }

    private EntryInfo readEntryInfo(File f, LocalDateTime fallbackTs) {
        if (fallbackTs == null) {
            fallbackTs = Instant.ofEpochMilli(f.lastModified()).atZone(SYSTEM_ZONE).toLocalDateTime();
        }
        String title = "";
        LocalDateTime ts = fallbackTs;
        try {
            if (EncryptionManager.isEncrypted(f)) {
                EncryptedMetadata.Meta meta = EncryptionManager.readMetadata(f);
                if (meta != null && meta.savedAt > 0) {
                    ts = Instant.ofEpochMilli(meta.savedAt).atZone(SYSTEM_ZONE).toLocalDateTime();
                }
                title = meta != null && meta.title != null ? meta.title.trim() : "";
                return new EntryInfo(ts, title.isBlank() ? fallbackTitle(f) : title);
            }
            try (BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                String first = br.readLine();
                if (first != null) {
                    MetaHeader meta = parseMetaHeader(first);
                    if (meta != null) {
                        if (meta.savedAt > 0) {
                            ts = Instant.ofEpochMilli(meta.savedAt).atZone(SYSTEM_ZONE).toLocalDateTime();
                        }
                        title = meta.title;
                        if (title.isBlank()) {
                            String next = br.readLine();
                            if (next != null && next.isBlank()) next = br.readLine();
                            if (next != null && !next.isBlank()) title = next.trim();
                        }
                        return new EntryInfo(ts, title.isBlank() ? fallbackTitle(f) : title);
                    }
                    if (!first.isBlank()) {
                        title = first.trim();
                        return new EntryInfo(ts, title);
                    }
                }
            }
            // Native fallback keeps behavior consistent with entry listing panels.
            String nativeTitle = NativeAccess.extractTitle(f.getAbsolutePath());
            if (nativeTitle != null && !nativeTitle.isBlank()) {
                MetaHeader meta = parseMetaHeader(nativeTitle);
                if (meta != null) {
                    if (meta.savedAt > 0) {
                        ts = Instant.ofEpochMilli(meta.savedAt).atZone(SYSTEM_ZONE).toLocalDateTime();
                    }
                    title = meta.title;
                    return new EntryInfo(ts, title.isBlank() ? fallbackTitle(f) : title);
                }
                title = nativeTitle.trim();
                return new EntryInfo(ts, title);
            }
        } catch (Throwable ignored) {
            // Fall through to filename-based fallback.
        }
        return new EntryInfo(ts, fallbackTitle(f));
    }

    private boolean hasAnyDetailAverages(DailyStats stats) {
        if (stats == null) return false;
        return stats.avgJoy >= 0 || stats.avgCalm >= 0 || stats.avgGratitude >= 0 || stats.avgEnergy >= 0
                || stats.avgSadness >= 0 || stats.avgAnger >= 0 || stats.avgAnxiety >= 0 || stats.avgStress >= 0;
    }

    private MetaHeader parseMetaHeader(String line) {
        if (line == null || !line.startsWith(META_PREFIX)) return null;
        String json = line.substring(META_PREFIX.length()).trim();
        String title = NativeJson.getString(json, "title");
        Long savedAt = NativeJson.getLong(json, "savedAt");
        return new MetaHeader(title == null ? "" : title.trim(), savedAt == null ? 0L : savedAt);
    }

    private String fallbackTitle(File f) {
        String nm = f.getName();
        int dot = nm.lastIndexOf('.');
        return dot > 0 ? nm.substring(0, dot) : nm;
    }

    java.util.List<LocalDate> getDays(){ return dayList; }
    java.util.List<Double> getValues(){ return avgMoodList; }
    java.util.List<Double> getSmoothedValues(){ return smoothedMoodList; }
    java.util.Map<LocalDate, java.util.List<File>> getEntriesByDate(){ return entriesByDate; }
    java.util.Map<LocalDate, java.util.List<LocalDateTime>> getMoodTimesByDate(){ return moodTimesByDate; }
    java.util.Map<LocalDate, java.util.List<EntryRef>> getEntryTimesByDate(){ return entryTimesByDate; }
    java.util.Map<LocalDate, java.util.List<Details>> getDetailsByDate(){ return detailsByDate; }

    // Enhanced analytics accessors
    AnalyticsResult getAnalytics(){ return lastAnalytics; }
    double getOverallAverage(){ return lastAnalytics != null ? lastAnalytics.overallAverage : 0; }
    double getVolatility(){ return lastAnalytics != null ? lastAnalytics.volatility : 0; }
    int getCurrentStreak(){ return lastAnalytics != null ? lastAnalytics.currentStreak : 0; }
    int getLongestGoodStreak(){ return lastAnalytics != null ? lastAnalytics.longestGoodStreak : 0; }
    int getLongestBadStreak(){ return lastAnalytics != null ? lastAnalytics.longestBadStreak : 0; }
    int getTotalSamples(){ return lastAnalytics != null ? lastAnalytics.totalSamples : 0; }

    Details getLatestDetailsFor(LocalDate d){
        java.util.List<Details> l = detailsByDate.get(d);
        if (l == null || l.isEmpty()) return null;
        Details best = null;
        for (Details it : l) {
            if (best == null) best = it;
            else if (it.ts != null && best.ts != null && it.ts.isAfter(best.ts)) best = it;
        }
        return best != null ? best : l.get(l.size()-1);
    }
}
