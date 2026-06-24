/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.analytics.mood;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Detects unusual mood spikes/dips using robust z-score.
 */
public final class MoodAnomalyDetector {

    public enum Type {
        SPIKE,
        DIP
    }

    public static final class AnomalyPoint {
        public final int index;
        public final LocalDate day;
        public final double value;
        public final double score;
        public final Type type;

        private AnomalyPoint(int index, LocalDate day, double value, double score, Type type) {
            this.index = index;
            this.day = day;
            this.value = value;
            this.score = score;
            this.type = type;
        }
    }

    public List<AnomalyPoint> detect(List<LocalDate> days, List<Double> values, double threshold) {
        if (days == null || values == null || days.isEmpty() || values.isEmpty() || days.size() != values.size()) {
            return List.of();
        }

        List<Double> valid = new ArrayList<>();
        for (Double value : values) {
            if (value != null) valid.add(value);
        }
        if (valid.size() < 5) {
            return List.of();
        }

        double median = median(valid);
        double mad = mad(valid, median);
        double std = standardDeviation(valid);

        List<AnomalyPoint> out = new ArrayList<>();
        double zThreshold = Math.max(1.0d, threshold);

        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null) continue;

            double score;
            if (mad > 1e-6d) {
                score = 0.6745d * (value - median) / mad;
            } else if (std > 1e-6d) {
                score = (value - median) / std;
            } else {
                score = 0d;
            }

            if (Math.abs(score) >= zThreshold) {
                Type type = score >= 0d ? Type.SPIKE : Type.DIP;
                out.add(new AnomalyPoint(i, days.get(i), value, score, type));
            }
        }

        out.sort(Comparator.comparingInt(a -> a.index));
        return Collections.unmodifiableList(out);
    }

    private double median(List<Double> values) {
        List<Double> copy = new ArrayList<>(values);
        copy.sort(Double::compareTo);
        int n = copy.size();
        if ((n & 1) == 1) {
            return copy.get(n / 2);
        }
        return (copy.get((n / 2) - 1) + copy.get(n / 2)) / 2d;
    }

    private double mad(List<Double> values, double median) {
        List<Double> deviations = new ArrayList<>(values.size());
        for (double value : values) {
            deviations.add(Math.abs(value - median));
        }
        return median(deviations);
    }

    private double standardDeviation(List<Double> values) {
        if (values.size() <= 1) return 0d;
        double mean = 0d;
        for (double v : values) mean += v;
        mean /= values.size();

        double sumSq = 0d;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.size());
    }
}
