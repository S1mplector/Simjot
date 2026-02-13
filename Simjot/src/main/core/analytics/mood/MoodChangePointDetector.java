/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects regime shifts in mood series using a PELT-style segmentation objective.
 */
public final class MoodChangePointDetector {

    public enum ShiftType {
        UPWARD,
        DOWNWARD
    }

    public static final class ChangePoint {
        public final int index;
        public final LocalDate day;
        public final double previousMean;
        public final double nextMean;
        public final double delta;
        public final double score;
        public final ShiftType type;

        private ChangePoint(int index,
                            LocalDate day,
                            double previousMean,
                            double nextMean,
                            double delta,
                            double score,
                            ShiftType type) {
            this.index = index;
            this.day = day;
            this.previousMean = previousMean;
            this.nextMean = nextMean;
            this.delta = delta;
            this.score = score;
            this.type = type;
        }
    }

    public List<ChangePoint> detect(List<LocalDate> days, List<Double> values) {
        return detect(days, values, 4, 14.0d, 4.5d);
    }

    public List<ChangePoint> detect(List<LocalDate> days,
                                    List<Double> values,
                                    int minSegmentLength,
                                    double penalty,
                                    double minDelta) {
        if (days == null || values == null || days.isEmpty() || values.isEmpty() || days.size() != values.size()) {
            return List.of();
        }

        int minSeg = Math.max(2, minSegmentLength);
        List<Sample> dense = toDense(days, values);
        if (dense.size() < minSeg * 2) {
            return List.of();
        }

        double[] prefix = new double[dense.size() + 1];
        double[] prefixSq = new double[dense.size() + 1];
        for (int i = 0; i < dense.size(); i++) {
            double v = dense.get(i).value;
            prefix[i + 1] = prefix[i] + v;
            prefixSq[i + 1] = prefixSq[i] + v * v;
        }

        int n = dense.size();
        double[] best = new double[n + 1];
        int[] prev = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            best[i] = Double.POSITIVE_INFINITY;
            prev[i] = -1;
        }
        best[0] = -Math.max(0d, penalty);

        for (int t = minSeg; t <= n; t++) {
            double bestCost = Double.POSITIVE_INFINITY;
            int bestStart = -1;

            for (int s = 0; s <= t - minSeg; s++) {
                if (!Double.isFinite(best[s])) continue;
                double cost = best[s] + segmentCost(prefix, prefixSq, s, t) + Math.max(0d, penalty);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestStart = s;
                }
            }

            best[t] = bestCost;
            prev[t] = bestStart;
        }

        if (!Double.isFinite(best[n])) {
            return List.of();
        }

        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(n);
        int cursor = n;
        while (cursor > 0) {
            int p = prev[cursor];
            if (p < 0) break;
            boundaries.add(p);
            if (p == 0) break;
            cursor = p;
        }
        Collections.reverse(boundaries);
        if (boundaries.isEmpty() || boundaries.get(0) != 0) {
            boundaries.add(0, 0);
        }
        if (boundaries.get(boundaries.size() - 1) != n) {
            boundaries.add(n);
        }

        List<ChangePoint> out = new ArrayList<>();
        for (int i = 1; i < boundaries.size() - 1; i++) {
            int cp = boundaries.get(i);
            int leftStart = boundaries.get(i - 1);
            int rightEnd = boundaries.get(i + 1);
            if (cp - leftStart < minSeg || rightEnd - cp < minSeg) continue;

            double leftMean = segmentMean(prefix, leftStart, cp);
            double rightMean = segmentMean(prefix, cp, rightEnd);
            double delta = rightMean - leftMean;
            if (Math.abs(delta) < Math.max(0.5d, minDelta)) {
                continue;
            }

            double leftVar = segmentVariance(prefix, prefixSq, leftStart, cp);
            double rightVar = segmentVariance(prefix, prefixSq, cp, rightEnd);
            int support = Math.min(cp - leftStart, rightEnd - cp);
            double denom = Math.sqrt(Math.max(1e-6d, leftVar + rightVar));
            double score = (Math.abs(delta) / denom) * Math.sqrt(Math.max(1d, support));

            Sample pivot = dense.get(cp);
            out.add(new ChangePoint(
                    pivot.originalIndex,
                    pivot.day,
                    leftMean,
                    rightMean,
                    delta,
                    score,
                    delta >= 0 ? ShiftType.UPWARD : ShiftType.DOWNWARD
            ));
        }

        return Collections.unmodifiableList(out);
    }

    private List<Sample> toDense(List<LocalDate> days, List<Double> values) {
        List<Sample> dense = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Double v = values.get(i);
            if (v == null || !Double.isFinite(v)) continue;
            dense.add(new Sample(i, days.get(i), v));
        }
        return dense;
    }

    private double segmentCost(double[] prefix, double[] prefixSq, int start, int endExclusive) {
        int n = endExclusive - start;
        if (n <= 1) return 0d;
        double sum = prefix[endExclusive] - prefix[start];
        double sq = prefixSq[endExclusive] - prefixSq[start];
        return Math.max(0d, sq - ((sum * sum) / n));
    }

    private double segmentMean(double[] prefix, int start, int endExclusive) {
        int n = endExclusive - start;
        if (n <= 0) return 0d;
        return (prefix[endExclusive] - prefix[start]) / n;
    }

    private double segmentVariance(double[] prefix, double[] prefixSq, int start, int endExclusive) {
        int n = endExclusive - start;
        if (n <= 1) return 0d;
        double mean = segmentMean(prefix, start, endExclusive);
        double sq = prefixSq[endExclusive] - prefixSq[start];
        return Math.max(0d, (sq / n) - (mean * mean));
    }

    private static final class Sample {
        private final int originalIndex;
        private final LocalDate day;
        private final double value;

        private Sample(int originalIndex, LocalDate day, double value) {
            this.originalIndex = originalIndex;
            this.day = day;
            this.value = value;
        }
    }
}
