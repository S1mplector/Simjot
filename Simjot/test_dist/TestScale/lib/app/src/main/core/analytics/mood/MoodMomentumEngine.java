/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.analytics.mood;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates short-term trend direction and acceleration.
 */
public final class MoodMomentumEngine {

    public enum Direction {
        RISING,
        FALLING,
        FLAT
    }

    public static final class MomentumResult {
        public final double slope;
        public final double acceleration;
        public final Direction direction;

        private MomentumResult(double slope, double acceleration, Direction direction) {
            this.slope = slope;
            this.acceleration = acceleration;
            this.direction = direction;
        }

        public static MomentumResult empty() {
            return new MomentumResult(0d, 0d, Direction.FLAT);
        }

        public String arrow() {
            return switch (direction) {
                case RISING -> "↑";
                case FALLING -> "↓";
                case FLAT -> "→";
            };
        }

        public String label() {
            return switch (direction) {
                case RISING -> "Rising";
                case FALLING -> "Falling";
                case FLAT -> "Flat";
            };
        }
    }

    public MomentumResult analyze(List<Double> values, int window) {
        if (values == null || values.isEmpty()) {
            return MomentumResult.empty();
        }
        int w = Math.max(3, window);

        List<Double> cleaned = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value != null) {
                cleaned.add(value);
            }
        }
        if (cleaned.size() < 3) {
            return MomentumResult.empty();
        }

        List<Double> recent = sliceTail(cleaned, w);
        double currentSlope = slope(recent);

        List<Double> previous = slicePrevious(cleaned, w);
        double previousSlope = previous.size() >= 3 ? slope(previous) : currentSlope;

        double acceleration = currentSlope - previousSlope;
        Direction direction = classify(currentSlope);
        return new MomentumResult(currentSlope, acceleration, direction);
    }

    public int compareDirection(double currentValue, double previousValue, double neutralTolerance) {
        double delta = currentValue - previousValue;
        if (Math.abs(delta) <= Math.max(0.001d, neutralTolerance)) return 0;
        return delta > 0 ? 1 : -1;
    }

    private Direction classify(double slope) {
        if (slope > 0.28d) return Direction.RISING;
        if (slope < -0.28d) return Direction.FALLING;
        return Direction.FLAT;
    }

    private double slope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0d;

        double sumX = 0d;
        double sumY = 0d;
        double sumXY = 0d;
        double sumXX = 0d;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double denom = (n * sumXX) - (sumX * sumX);
        if (Math.abs(denom) < 1e-9d) return 0d;
        return ((n * sumXY) - (sumX * sumY)) / denom;
    }

    private List<Double> sliceTail(List<Double> values, int window) {
        int from = Math.max(0, values.size() - window);
        return values.subList(from, values.size());
    }

    private List<Double> slicePrevious(List<Double> values, int window) {
        int end = Math.max(0, values.size() - window);
        int start = Math.max(0, end - window);
        if (start >= end) return List.of();
        return values.subList(start, end);
    }
}
