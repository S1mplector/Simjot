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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates per-day detailed emotion values into stackable chart segments.
 */
public final class EmotionStackAggregator {

    public enum WeightingMode {
        INTENSITY,
        VALUE,
        HYBRID
    }

    public static final class EmotionStack {
        public final LocalDate day;
        public final double[] values;
        public final double[] intensities;
        public final double[] percentages;
        public final int dominantEmotion;
        public final double averageIntensity;
        public final double confidence;
        public final boolean hasData;

        private EmotionStack(LocalDate day,
                             double[] values,
                             double[] intensities,
                             double[] percentages,
                             int dominantEmotion,
                             double averageIntensity,
                             double confidence,
                             boolean hasData) {
            this.day = day;
            this.values = values;
            this.intensities = intensities;
            this.percentages = percentages;
            this.dominantEmotion = dominantEmotion;
            this.averageIntensity = averageIntensity;
            this.confidence = confidence;
            this.hasData = hasData;
        }
    }

    public List<EmotionStack> aggregate(List<LocalDate> orderedDays,
                                        Map<LocalDate, double[]> averageValuesByDay,
                                        WeightingMode weightingMode) {
        if (orderedDays == null || orderedDays.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, double[]> source = averageValuesByDay == null
                ? Collections.emptyMap()
                : averageValuesByDay;

        List<EmotionStack> stacks = new ArrayList<>(orderedDays.size());
        for (LocalDate day : orderedDays) {
            double[] avg = source.get(day);
            stacks.add(buildStack(day, avg, weightingMode));
        }
        return Collections.unmodifiableList(stacks);
    }

    public Map<LocalDate, EmotionStack> indexByDay(List<EmotionStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<LocalDate, EmotionStack> out = new LinkedHashMap<>(stacks.size());
        for (EmotionStack stack : stacks) {
            if (stack != null && stack.day != null) {
                out.put(stack.day, stack);
            }
        }
        return out;
    }

    private EmotionStack buildStack(LocalDate day, double[] averageValues, WeightingMode weightingMode) {
        int count = MoodEmotionCatalog.count();
        double[] values = new double[count];
        double[] intensities = new double[count];
        double[] percentages = new double[count];

        int active = 0;
        double weightSum = 0d;
        double intensitySum = 0d;

        for (int i = 0; i < count; i++) {
            double value = valueAt(averageValues, i);
            values[i] = value;
            if (value < 0) {
                intensities[i] = -1d;
                percentages[i] = 0d;
                continue;
            }

            double intensity = clamp(Math.abs(value - MoodEmotionCatalog.NEUTRAL_VALUE) * 2d);
            intensities[i] = intensity;
            intensitySum += intensity;
            active++;

            double weight = switch (weightingMode == null ? WeightingMode.HYBRID : weightingMode) {
                case INTENSITY -> intensity;
                case VALUE -> clamp(value);
                case HYBRID -> (intensity * 0.7d) + (clamp(value) * 0.3d);
            };

            if (weight > 0d) {
                percentages[i] = weight;
                weightSum += weight;
            }
        }

        int dominant = -1;
        double best = 0d;
        if (weightSum > 0d) {
            for (int i = 0; i < count; i++) {
                if (percentages[i] <= 0d) continue;
                percentages[i] = (percentages[i] * 100d) / weightSum;
                if (percentages[i] > best) {
                    best = percentages[i];
                    dominant = i;
                }
            }
        }

        boolean hasData = active > 0;
        double averageIntensity = hasData ? intensitySum / active : 0d;
        double coverage = active / (double) count;
        double confidence = hasData ? clamp01((best / 100d) * 0.6d + coverage * 0.4d) : 0d;

        return new EmotionStack(day, values, intensities, percentages, dominant, averageIntensity, confidence, hasData);
    }

    private double valueAt(double[] averageValues, int idx) {
        if (averageValues == null || idx < 0 || idx >= averageValues.length) {
            return -1d;
        }
        return averageValues[idx];
    }

    private double clamp(double v) {
        return Math.max(0d, Math.min(100d, v));
    }

    private double clamp01(double v) {
        return Math.max(0d, Math.min(1d, v));
    }
}
