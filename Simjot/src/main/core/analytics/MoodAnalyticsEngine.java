/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.analytics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.MoodFile;

/**
 * Mood analytics engine providing robust parsing, daily aggregation,
 * smoothing, volatility metrics, streak detection, and caching.
 */
public final class MoodAnalyticsEngine {

    private static MoodAnalyticsEngine instance;

    // Cache key -> computed data
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private long lastFileModified = 0L;
    private long lastFileSize = 0L;

    // Supported timestamp formats for robust parsing
    private static final DateTimeFormatter[] TIMESTAMP_FORMATS = {
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE
    };

    public static synchronized MoodAnalyticsEngine get() {
        if (instance == null) {
            instance = new MoodAnalyticsEngine();
        }
        return instance;
    }

    private MoodAnalyticsEngine() {}

    // Data structures 

    /** A single mood sample as parsed from the log */
    public static final class MoodSample {
        public final LocalDateTime timestamp;
        public final int composite;       // 0-100 overall mood
        public final int joy, calm, gratitude, energy;
        public final int sadness, anger, anxiety, stress;

        public MoodSample(LocalDateTime ts, int composite,
                          int joy, int calm, int gratitude, int energy,
                          int sadness, int anger, int anxiety, int stress) {
            this.timestamp = ts;
            this.composite = composite;
            this.joy = joy;
            this.calm = calm;
            this.gratitude = gratitude;
            this.energy = energy;
            this.sadness = sadness;
            this.anger = anger;
            this.anxiety = anxiety;
            this.stress = stress;
        }

        public MoodSample(LocalDateTime ts, int composite) {
            this(ts, composite, -1, -1, -1, -1, -1, -1, -1, -1);
        }

        public boolean hasDetails() {
            return joy >= 0 || calm >= 0 || gratitude >= 0 || energy >= 0
                    || sadness >= 0 || anger >= 0 || anxiety >= 0 || stress >= 0;
        }
    }

    /** Aggregated daily statistics */
    public static final class DailyStats {
        public final LocalDate date;
        public final double average;
        public final int min, max;
        public final int sampleCount;
        public final List<MoodSample> samples;

        // Detailed emotion averages (if available)
        public final double avgJoy, avgCalm, avgGratitude, avgEnergy;
        public final double avgSadness, avgAnger, avgAnxiety, avgStress;

        public DailyStats(LocalDate date, List<MoodSample> samples) {
            this.date = date;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
            this.sampleCount = samples.size();

            if (samples.isEmpty()) {
                average = 0; min = 0; max = 0;
                avgJoy = avgCalm = avgGratitude = avgEnergy = 0;
                avgSadness = avgAnger = avgAnxiety = avgStress = 0;
                return;
            }

            int sum = 0, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
            double jSum = 0, cSum = 0, gSum = 0, eSum = 0;
            double sSum = 0, aSum = 0, axSum = 0, stSum = 0;
            int jCount = 0, cCount = 0, gCount = 0, eCount = 0;
            int sCount = 0, aCount = 0, axCount = 0, stCount = 0;

            for (MoodSample s : samples) {
                sum += s.composite;
                if (s.composite < minV) minV = s.composite;
                if (s.composite > maxV) maxV = s.composite;
                if (s.joy >= 0) { jSum += s.joy; jCount++; }
                if (s.calm >= 0) { cSum += s.calm; cCount++; }
                if (s.gratitude >= 0) { gSum += s.gratitude; gCount++; }
                if (s.energy >= 0) { eSum += s.energy; eCount++; }
                if (s.sadness >= 0) { sSum += s.sadness; sCount++; }
                if (s.anger >= 0) { aSum += s.anger; aCount++; }
                if (s.anxiety >= 0) { axSum += s.anxiety; axCount++; }
                if (s.stress >= 0) { stSum += s.stress; stCount++; }
            }

            this.average = (double) sum / sampleCount;
            this.min = minV;
            this.max = maxV;

            avgJoy = jCount > 0 ? jSum / jCount : -1;
            avgCalm = cCount > 0 ? cSum / cCount : -1;
            avgGratitude = gCount > 0 ? gSum / gCount : -1;
            avgEnergy = eCount > 0 ? eSum / eCount : -1;
            avgSadness = sCount > 0 ? sSum / sCount : -1;
            avgAnger = aCount > 0 ? aSum / aCount : -1;
            avgAnxiety = axCount > 0 ? axSum / axCount : -1;
            avgStress = stCount > 0 ? stSum / stCount : -1;
        }

        public DailyStats(LocalDate date,
                          int sampleCount,
                          double average,
                          int min,
                          int max,
                          double avgJoy,
                          double avgCalm,
                          double avgGratitude,
                          double avgEnergy,
                          double avgSadness,
                          double avgAnger,
                          double avgAnxiety,
                          double avgStress) {
            this.date = date;
            this.samples = Collections.emptyList();
            this.sampleCount = Math.max(0, sampleCount);
            if (this.sampleCount <= 0) {
                this.average = 0;
                this.min = 0;
                this.max = 0;
                this.avgJoy = this.avgCalm = this.avgGratitude = this.avgEnergy = 0;
                this.avgSadness = this.avgAnger = this.avgAnxiety = this.avgStress = 0;
            } else {
                this.average = average;
                this.min = min;
                this.max = max;
                this.avgJoy = avgJoy;
                this.avgCalm = avgCalm;
                this.avgGratitude = avgGratitude;
                this.avgEnergy = avgEnergy;
                this.avgSadness = avgSadness;
                this.avgAnger = avgAnger;
                this.avgAnxiety = avgAnxiety;
                this.avgStress = avgStress;
            }
        }
    }

    /** Overall analytics result */
    public static final class AnalyticsResult {
        public final List<LocalDate> dates;
        public final Map<LocalDate, DailyStats> dailyStats;
        public final List<Double> dailyAverages;      // parallel to dates, null if no data
        public final List<Double> smoothedAverages;   // rolling average
        public final double overallAverage;
        public final double volatility;               // std deviation
        public final int currentStreak;               // positive = good streak, negative = bad streak
        public final int longestGoodStreak;
        public final int longestBadStreak;
        public final int totalSamples;

        public AnalyticsResult(List<LocalDate> dates,
                               Map<LocalDate, DailyStats> dailyStats,
                               List<Double> dailyAverages,
                               List<Double> smoothedAverages,
                               double overallAverage,
                               double volatility,
                               int currentStreak,
                               int longestGoodStreak,
                               int longestBadStreak,
                               int totalSamples) {
            this.dates = Collections.unmodifiableList(dates);
            this.dailyStats = Collections.unmodifiableMap(dailyStats);
            this.dailyAverages = Collections.unmodifiableList(dailyAverages);
            this.smoothedAverages = Collections.unmodifiableList(smoothedAverages);
            this.overallAverage = overallAverage;
            this.volatility = volatility;
            this.currentStreak = currentStreak;
            this.longestGoodStreak = longestGoodStreak;
            this.longestBadStreak = longestBadStreak;
            this.totalSamples = totalSamples;
        }
    }

    private static final class CachedResult {
        final AnalyticsResult result;
        final long timestamp;
        CachedResult(AnalyticsResult r) { this.result = r; this.timestamp = System.currentTimeMillis(); }
    }

    // ========== Public API ==========

    /**
     * Load and analyze mood data for the given range.
     * @param daysBack number of days to analyze (0 = all time)
     * @param smoothingWindow rolling average window size (typically 7)
     * @return analytics result with all computed metrics
     */
    public AnalyticsResult analyze(int daysBack, int smoothingWindow) {
        String cacheKey = daysBack + ":" + smoothingWindow;

        // Check file modification for cache invalidation
        File moodFile = getMoodFile();
        long mod = moodFile.exists() ? moodFile.lastModified() : 0L;
        long size = moodFile.exists() ? moodFile.length() : 0L;

        if (mod != lastFileModified || size != lastFileSize) {
            cache.clear();
            lastFileModified = mod;
            lastFileSize = size;
        }

        CachedResult cached = cache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 30_000) {
            return cached.result;
        }

        AnalyticsResult nativeResult = tryNativeAnalyze(moodFile, daysBack, smoothingWindow);
        if (nativeResult != null) {
            cache.put(cacheKey, new CachedResult(nativeResult));
            return nativeResult;
        }

        // Parse all samples (Java fallback)
        List<MoodSample> allSamples = parseAllSamples();

        // Filter by date range
        LocalDate today = LocalDate.now();
        LocalDate startDate = daysBack > 0 ? today.minusDays(daysBack - 1) : findEarliestDate(allSamples, today);

        // Group by date
        Map<LocalDate, List<MoodSample>> byDate = new LinkedHashMap<>();
        for (MoodSample s : allSamples) {
            if (s.timestamp == null) continue;
            LocalDate d = s.timestamp.toLocalDate();
            if (!d.isBefore(startDate) && !d.isAfter(today)) {
                byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(s);
            }
        }

        // Build date list (all days in range, even those without data)
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            dates.add(d);
        }

        // Compute daily stats
        Map<LocalDate, DailyStats> dailyStats = new LinkedHashMap<>();
        List<Double> dailyAverages = new ArrayList<>();
        int totalSamples = 0;
        double sumAll = 0;
        int countAll = 0;

        for (LocalDate d : dates) {
            List<MoodSample> samples = byDate.getOrDefault(d, Collections.emptyList());
            DailyStats stats = new DailyStats(d, samples);
            dailyStats.put(d, stats);

            if (samples.isEmpty()) {
                dailyAverages.add(null);
            } else {
                dailyAverages.add(stats.average);
                sumAll += stats.average;
                countAll++;
                totalSamples += samples.size();
            }
        }

        double overallAverage = countAll > 0 ? sumAll / countAll : 0;
        double[] nativeValues = toNativeValues(dailyAverages);

        // Compute smoothed (rolling) averages
        List<Double> smoothed = computeSmoothed(dailyAverages, nativeValues, smoothingWindow);

        // Compute volatility (standard deviation of daily averages)
        double volatility = computeVolatility(dailyAverages, nativeValues, overallAverage);

        // Compute streaks
        int[] streaks = computeStreaks(dailyAverages, nativeValues, 50.0); // threshold at 50

        AnalyticsResult result = new AnalyticsResult(
            dates, dailyStats, dailyAverages, smoothed,
            overallAverage, volatility,
            streaks[0], streaks[1], streaks[2], totalSamples
        );

        cache.put(cacheKey, new CachedResult(result));
        return result;
    }

    private AnalyticsResult tryNativeAnalyze(File moodFile, int daysBack, int smoothingWindow) {
        if (moodFile == null || !moodFile.exists()) return null;
        if (!moodFile.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) return null;
        int parsed = NativeAccess.moodLoad(moodFile.getAbsolutePath());
        if (parsed < 0) return null;
        int daily = NativeAccess.moodComputeDaily(daysBack);
        if (daily < 0) return null;

        int dailyCount = NativeAccess.moodDailyCount();
        Map<LocalDate, NativeAccess.MoodDailyStats> nativeStats = new java.util.HashMap<>();
        int minDateDays = Integer.MAX_VALUE;
        for (int i = 0; i < dailyCount; i++) {
            NativeAccess.MoodDailyStats stats = NativeAccess.moodGetDaily(i);
            if (stats == null) continue;
            LocalDate date = LocalDate.ofEpochDay(stats.dateDays());
            nativeStats.put(date, stats);
            if (stats.dateDays() < minDateDays) minDateDays = stats.dateDays();
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = daysBack > 0
                ? today.minusDays(daysBack - 1)
                : (minDateDays != Integer.MAX_VALUE ? LocalDate.ofEpochDay(minDateDays) : today);

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            dates.add(d);
        }

        Map<LocalDate, DailyStats> dailyStats = new LinkedHashMap<>();
        List<Double> dailyAverages = new ArrayList<>();
        int totalSamples = 0;
        double sumAll = 0;
        int countAll = 0;

        for (LocalDate d : dates) {
            NativeAccess.MoodDailyStats nativeDaily = nativeStats.get(d);
            DailyStats stats;
            if (nativeDaily != null && nativeDaily.sampleCount() > 0) {
                stats = new DailyStats(d,
                    nativeDaily.sampleCount(),
                    nativeDaily.average(),
                    nativeDaily.min(),
                    nativeDaily.max(),
                    nativeDaily.avgJoy(),
                    nativeDaily.avgCalm(),
                    nativeDaily.avgGratitude(),
                    nativeDaily.avgEnergy(),
                    nativeDaily.avgSadness(),
                    nativeDaily.avgAnger(),
                    nativeDaily.avgAnxiety(),
                    nativeDaily.avgStress());
                dailyAverages.add(nativeDaily.average());
                sumAll += nativeDaily.average();
                countAll++;
                totalSamples += nativeDaily.sampleCount();
            } else {
                stats = new DailyStats(d, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                dailyAverages.add(null);
            }
            dailyStats.put(d, stats);
        }

        double overallAverage = countAll > 0 ? sumAll / countAll : 0;
        double[] nativeValues = toNativeValues(dailyAverages);
        List<Double> smoothed = computeSmoothed(dailyAverages, nativeValues, smoothingWindow);
        double volatility = computeVolatility(dailyAverages, nativeValues, overallAverage);
        int[] streaks = computeStreaks(dailyAverages, nativeValues, 50.0);

        return new AnalyticsResult(
            dates, dailyStats, dailyAverages, smoothed,
            overallAverage, volatility,
            streaks[0], streaks[1], streaks[2], totalSamples
        );
    }

    /** Invalidate all cached results (e.g., after logging new mood) */
    public void invalidateCache() {
        cache.clear();
        lastFileModified = 0L;
        lastFileSize = 0L;
    }

    // ========== Parsing ==========

    private File getMoodFile() {
        File moods = MoodFile.getMoodsFile();
        if (moods.exists()) return moods;
        return MoodFile.getLegacyFile();
    }

    private List<MoodSample> parseAllSamples() {
        List<MoodSample> samples = new ArrayList<>();
        File moodFile = getMoodFile();
        if (!moodFile.exists()) return samples;

        List<MoodFile.MoodRecord> records = MoodFile.readAllRecords();
        if (!records.isEmpty()) {
            for (MoodFile.MoodRecord rec : records) {
                if (rec == null || rec.timestamp == null) continue;
                if (rec.details != null && rec.details.length >= 8) {
                    MoodSample s = new MoodSample(
                            rec.timestamp,
                            rec.composite,
                            rec.details[0], rec.details[1], rec.details[2], rec.details[3],
                            rec.details[4], rec.details[5], rec.details[6], rec.details[7]
                    );
                    samples.add(s);
                } else {
                    samples.add(new MoodSample(rec.timestamp, rec.composite));
                }
            }
            return samples;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                MoodSample s = parseLine(line);
                if (s != null) samples.add(s);
            }
        } catch (IOException e) {
            // Log but don't crash
            System.err.println("[MoodAnalyticsEngine] Error reading mood log: " + e.getMessage());
        }

        return samples;
    }

    private MoodSample parseLine(String line) {
        if (line == null || line.isBlank()) return null;

        String[] parts = line.split(",");
        if (parts.length < 2) return null;

        // Parse timestamp
        LocalDateTime ts = parseTimestamp(parts[0].trim());
        if (ts == null) return null;

        // Parse composite mood
        int composite = parseMoodValue(parts[1].trim());

        // Parse detailed emotions if present
        if (parts.length >= 10) {
            try {
                int joy = Integer.parseInt(parts[2].trim());
                int calm = Integer.parseInt(parts[3].trim());
                int gratitude = Integer.parseInt(parts[4].trim());
                int energy = Integer.parseInt(parts[5].trim());
                int sadness = Integer.parseInt(parts[6].trim());
                int anger = Integer.parseInt(parts[7].trim());
                int anxiety = Integer.parseInt(parts[8].trim());
                int stress = Integer.parseInt(parts[9].trim());
                return new MoodSample(ts, composite, joy, calm, gratitude, energy, sadness, anger, anxiety, stress);
            } catch (NumberFormatException ignored) {}
        }

        return new MoodSample(ts, composite);
    }

    private LocalDateTime parseTimestamp(String s) {
        // Try ISO date first (just date, no time)
        try {
            LocalDate d = LocalDate.parse(s);
            return d.atStartOfDay();
        } catch (Exception ignored) {}

        // Try each format
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private int parseMoodValue(String s) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            // Handle emoji-style values
            if (":)".equals(s) || "😊".equals(s) || "😀".equals(s)) return 100;
            if (":/".equals(s) || "😐".equals(s)) return 50;
            if (":(".equals(s) || "😢".equals(s) || "😞".equals(s)) return 0;
            return 50; // default neutral
        }
    }

    private LocalDate findEarliestDate(List<MoodSample> samples, LocalDate fallback) {
        LocalDate earliest = fallback;
        for (MoodSample s : samples) {
            if (s.timestamp != null) {
                LocalDate d = s.timestamp.toLocalDate();
                if (d.isBefore(earliest)) earliest = d;
            }
        }
        return earliest;
    }

    // ========== Analytics Computations ==========

    private static double[] toNativeValues(List<Double> values) {
        if (values == null || values.isEmpty()) return new double[0];
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double v = values.get(i);
            out[i] = v == null ? -1.0 : v;
        }
        return out;
    }

    private static List<Double> toNullableList(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double v : values) {
            out.add(v < 0 ? null : v);
        }
        return out;
    }

    private List<Double> computeSmoothed(List<Double> values, double[] nativeValues, int window) {
        if (nativeValues != null && nativeValues.length > 0) {
            double[] smoothed = NativeAccess.moodSmooth(nativeValues, window);
            if (smoothed != null) {
                return toNullableList(smoothed);
            }
        }

        List<Double> result = new ArrayList<>();
        int n = values.size();
        int safeWindow = Math.max(1, window);

        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - safeWindow + 1);
            double sum = 0;
            int count = 0;

            for (int j = start; j <= i; j++) {
                Double v = values.get(j);
                if (v != null) {
                    sum += v;
                    count++;
                }
            }

            if (count > 0) {
                result.add(sum / count);
            } else {
                result.add(null);
            }
        }

        return result;
    }

    private double computeVolatility(List<Double> values, double[] nativeValues, double mean) {
        if (nativeValues != null && nativeValues.length >= 2) {
            double nativeStddev = NativeAccess.moodVolatility(nativeValues);
            if (!Double.isNaN(nativeStddev) && nativeStddev >= 0) {
                return nativeStddev;
            }
        }

        // Try native stddev computation (SIMD-accelerated, faster for large datasets)
        double[] nonNull = values.stream()
            .filter(v -> v != null)
            .mapToDouble(Double::doubleValue)
            .toArray();
        if (nonNull.length >= 2) {
            double nativeStddev = NativeAccess.mathStddev(nonNull);
            if (!Double.isNaN(nativeStddev) && nativeStddev >= 0) {
                return nativeStddev;
            }
        }
        
        // Java fallback
        double sumSq = 0;
        int count = 0;

        for (Double v : values) {
            if (v != null) {
                double diff = v - mean;
                sumSq += diff * diff;
                count++;
            }
        }

        if (count < 2) return 0;
        return Math.sqrt(sumSq / (count - 1));
    }

    /**
     * Compute streaks.
     * @return [currentStreak, longestGoodStreak, longestBadStreak]
     *         currentStreak is positive for good, negative for bad, 0 if no data today
     */
    private int[] computeStreaks(List<Double> values, double[] nativeValues, double threshold) {
        if (nativeValues != null && nativeValues.length > 0) {
            int[] nativeStreaks = NativeAccess.moodStreaks(nativeValues, threshold);
            if (nativeStreaks != null) {
                return nativeStreaks;
            }
        }

        int currentStreak = 0;
        int longestGood = 0;
        int longestBad = 0;

        int runningGood = 0;
        int runningBad = 0;

        for (int i = 0; i < values.size(); i++) {
            Double v = values.get(i);
            if (v == null) {
                // Gap breaks streak
                if (runningGood > longestGood) longestGood = runningGood;
                if (runningBad > longestBad) longestBad = runningBad;
                runningGood = 0;
                runningBad = 0;
            } else if (v >= threshold) {
                runningGood++;
                if (runningBad > longestBad) longestBad = runningBad;
                runningBad = 0;
            } else {
                runningBad++;
                if (runningGood > longestGood) longestGood = runningGood;
                runningGood = 0;
            }
        }

        // Final check
        if (runningGood > longestGood) longestGood = runningGood;
        if (runningBad > longestBad) longestBad = runningBad;

        // Current streak (from most recent data point)
        if (!values.isEmpty()) {
            Double last = values.get(values.size() - 1);
            if (last != null) {
                currentStreak = last >= threshold ? runningGood : -runningBad;
            }
        }

        return new int[]{currentStreak, longestGood, longestBad};
    }

    // ========== Utility Methods ==========

    /** Get a human-readable summary of the analytics */
    public String getSummary(AnalyticsResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Overall: %.1f/100 | ", r.overallAverage));
        sb.append(String.format("Volatility: %.1f | ", r.volatility));

        if (r.currentStreak > 0) {
            sb.append(String.format("Current streak: %d good days", r.currentStreak));
        } else if (r.currentStreak < 0) {
            sb.append(String.format("Current streak: %d challenging days", -r.currentStreak));
        } else {
            sb.append("No current streak");
        }

        return sb.toString();
    }

    /** Categorize a mood value */
    public static String categorize(double value) {
        if (value >= 80) return "Excellent";
        if (value >= 60) return "Good";
        if (value >= 40) return "Neutral";
        if (value >= 20) return "Low";
        return "Very Low";
    }

    /** Get a color for a mood value (for UI rendering) */
    public static java.awt.Color getColor(double value) {
        if (value >= 66) return new java.awt.Color(40, 160, 90);   // Green
        if (value >= 33) return new java.awt.Color(230, 160, 50);  // Orange
        return new java.awt.Color(200, 60, 60);                     // Red
    }
}
