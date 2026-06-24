/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.analytics.mood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes volatility-oriented statistics for mood series.
 */
public final class MoodVolatilityEngine {

    public enum Band {
        STABLE,
        VARIABLE,
        TURBULENT
    }

    public static final class VolatilityResult {
        public final double standardDeviation;
        public final double averageSwing;
        public final List<Double> rollingStdDev;
        public final Band band;

        private VolatilityResult(double standardDeviation,
                                 double averageSwing,
                                 List<Double> rollingStdDev,
                                 Band band) {
            this.standardDeviation = standardDeviation;
            this.averageSwing = averageSwing;
            this.rollingStdDev = Collections.unmodifiableList(rollingStdDev);
            this.band = band;
        }

        public static VolatilityResult empty() {
            return new VolatilityResult(0d, 0d, List.of(), Band.STABLE);
        }
    }

    public VolatilityResult analyze(List<Double> values, int window) {
        if (values == null || values.isEmpty()) {
            return VolatilityResult.empty();
        }

        List<Double> valid = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value != null) {
                valid.add(value);
            }
        }

        if (valid.isEmpty()) {
            return VolatilityResult.empty();
        }

        double std = standardDeviation(valid);
        double swing = averageSwing(valid);
        List<Double> rolling = rollingStd(values, Math.max(2, window));
        Band band = classify(std, swing);
        return new VolatilityResult(std, swing, rolling, band);
    }

    public Band classify(double standardDeviation, double averageSwing) {
        double blended = (standardDeviation * 0.7d) + (averageSwing * 0.3d);
        if (blended < 7.5d) return Band.STABLE;
        if (blended < 14d) return Band.VARIABLE;
        return Band.TURBULENT;
    }

    public String bandLabel(Band band) {
        if (band == null) return "Stable";
        return switch (band) {
            case STABLE -> "Stable";
            case VARIABLE -> "Moderate swings";
            case TURBULENT -> "High swings";
        };
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

    private double averageSwing(List<Double> values) {
        if (values.size() <= 1) return 0d;
        double sum = 0d;
        int count = 0;
        double prev = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            double current = values.get(i);
            sum += Math.abs(current - prev);
            prev = current;
            count++;
        }
        return count > 0 ? sum / count : 0d;
    }

    private List<Double> rollingStd(List<Double> rawValues, int window) {
        List<Double> out = new ArrayList<>(rawValues.size());
        for (int i = 0; i < rawValues.size(); i++) {
            int start = Math.max(0, i - window + 1);
            List<Double> segment = new ArrayList<>(window);
            for (int j = start; j <= i; j++) {
                Double v = rawValues.get(j);
                if (v != null) {
                    segment.add(v);
                }
            }
            if (segment.size() < 2) {
                out.add(null);
            } else {
                out.add(standardDeviation(segment));
            }
        }
        return out;
    }
}
