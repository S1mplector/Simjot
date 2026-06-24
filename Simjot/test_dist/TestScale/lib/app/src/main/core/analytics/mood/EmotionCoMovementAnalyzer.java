/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.analytics.mood;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Computes pairwise co-movement between emotion day-to-day changes.
 */
public final class EmotionCoMovementAnalyzer {

    public static final class Pair {
        public final int left;
        public final int right;
        public final double correlation;
        public final int samples;

        private Pair(int left, int right, double correlation, int samples) {
            this.left = left;
            this.right = right;
            this.correlation = correlation;
            this.samples = samples;
        }
    }

    public static final class Result {
        public final double[][] correlations;
        public final int[][] sampleCounts;
        public final Pair strongestPositive;
        public final Pair strongestNegative;

        private Result(double[][] correlations,
                       int[][] sampleCounts,
                       Pair strongestPositive,
                       Pair strongestNegative) {
            this.correlations = correlations;
            this.sampleCounts = sampleCounts;
            this.strongestPositive = strongestPositive;
            this.strongestNegative = strongestNegative;
        }

        public static Result empty() {
            int n = MoodEmotionCatalog.count();
            return new Result(new double[n][n], new int[n][n], null, null);
        }

        public boolean hasData() {
            return strongestPositive != null || strongestNegative != null;
        }
    }

    public Result analyze(List<LocalDate> orderedDays, Map<LocalDate, double[]> emotionValuesByDay) {
        if (orderedDays == null || orderedDays.size() < 3 || emotionValuesByDay == null || emotionValuesByDay.isEmpty()) {
            return Result.empty();
        }

        int n = MoodEmotionCatalog.count();
        double[][] corr = new double[n][n];
        int[][] counts = new int[n][n];
        Pair strongestPositive = null;
        Pair strongestNegative = null;

        for (int i = 0; i < n; i++) {
            corr[i][i] = 1d;
            counts[i][i] = orderedDays.size();
        }

        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                CorrelationStats stats = correlateDeltas(orderedDays, emotionValuesByDay, a, b);
                if (stats.samples < 3 || !Double.isFinite(stats.correlation)) {
                    corr[a][b] = corr[b][a] = 0d;
                    counts[a][b] = counts[b][a] = stats.samples;
                    continue;
                }

                corr[a][b] = corr[b][a] = stats.correlation;
                counts[a][b] = counts[b][a] = stats.samples;

                if (stats.correlation > 0d) {
                    if (strongestPositive == null || stats.correlation > strongestPositive.correlation) {
                        strongestPositive = new Pair(a, b, stats.correlation, stats.samples);
                    }
                } else if (stats.correlation < 0d) {
                    if (strongestNegative == null || stats.correlation < strongestNegative.correlation) {
                        strongestNegative = new Pair(a, b, stats.correlation, stats.samples);
                    }
                }
            }
        }

        return new Result(corr, counts, strongestPositive, strongestNegative);
    }

    public String describePair(Pair pair) {
        if (pair == null) return "No strong relationship";
        String rel = pair.correlation >= 0 ? "move together" : "move opposite";
        return MoodEmotionCatalog.emotionName(pair.left)
                + " & "
                + MoodEmotionCatalog.emotionName(pair.right)
                + " "
                + rel
                + " (r="
                + String.format(java.util.Locale.ROOT, "%.2f", pair.correlation)
                + ")";
    }

    private CorrelationStats correlateDeltas(List<LocalDate> orderedDays,
                                             Map<LocalDate, double[]> emotionValuesByDay,
                                             int leftEmotion,
                                             int rightEmotion) {
        double sumX = 0d;
        double sumY = 0d;
        double sumXY = 0d;
        double sumXX = 0d;
        double sumYY = 0d;
        int samples = 0;

        for (int i = 1; i < orderedDays.size(); i++) {
            LocalDate prevDay = orderedDays.get(i - 1);
            LocalDate day = orderedDays.get(i);
            double[] prev = emotionValuesByDay.get(prevDay);
            double[] now = emotionValuesByDay.get(day);
            if (prev == null || now == null) continue;

            double prevLeft = at(prev, leftEmotion);
            double nowLeft = at(now, leftEmotion);
            double prevRight = at(prev, rightEmotion);
            double nowRight = at(now, rightEmotion);
            if (prevLeft < 0 || nowLeft < 0 || prevRight < 0 || nowRight < 0) continue;

            double dx = nowLeft - prevLeft;
            double dy = nowRight - prevRight;

            sumX += dx;
            sumY += dy;
            sumXY += dx * dy;
            sumXX += dx * dx;
            sumYY += dy * dy;
            samples++;
        }

        if (samples < 3) {
            return new CorrelationStats(0d, samples);
        }

        double cov = (sumXY - (sumX * sumY) / samples);
        double varX = (sumXX - (sumX * sumX) / samples);
        double varY = (sumYY - (sumY * sumY) / samples);
        if (varX <= 1e-8d || varY <= 1e-8d) {
            return new CorrelationStats(0d, samples);
        }

        double corr = cov / Math.sqrt(varX * varY);
        corr = Math.max(-1d, Math.min(1d, corr));
        return new CorrelationStats(corr, samples);
    }

    private double at(double[] values, int idx) {
        if (values == null || idx < 0 || idx >= values.length) return -1d;
        return values[idx];
    }

    private static final class CorrelationStats {
        private final double correlation;
        private final int samples;

        private CorrelationStats(double correlation, int samples) {
            this.correlation = correlation;
            this.samples = samples;
        }
    }
}
