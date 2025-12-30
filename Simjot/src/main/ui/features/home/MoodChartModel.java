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

package main.ui.features.home;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import main.core.analytics.MoodAnalyticsEngine;
import main.core.analytics.MoodAnalyticsEngine.AnalyticsResult;
import main.core.analytics.MoodAnalyticsEngine.DailyStats;
import main.core.analytics.MoodAnalyticsEngine.MoodSample;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;

final class MoodChartModel {
    static final class EntryRef { final File file; final LocalDateTime ts; EntryRef(File f, LocalDateTime t){ this.file=f; this.ts=t; } }
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
            if (stats != null && !stats.samples.isEmpty()) {
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
            }
        }

        // Load journal entries for date mapping
        loadJournalEntries();

        return true;
    }

    private void loadJournalEntries() {
        try {
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
                    entriesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
                    entryTimesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(new EntryRef(f, ts));
                }
            }
        } catch (Throwable ignored) {}
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
