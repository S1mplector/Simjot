/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

import java.time.LocalDate;
import java.util.List;

/**
 * Finds dominant emotions inside a time window.
 */
public final class EmotionDominanceEngine {

    public static final class Result {
        public final boolean hasData;
        public final int dominantEmotion;
        public final double averageValue;
        public final double averageIntensity;
        public final double confidence;

        private Result(boolean hasData,
                       int dominantEmotion,
                       double averageValue,
                       double averageIntensity,
                       double confidence) {
            this.hasData = hasData;
            this.dominantEmotion = dominantEmotion;
            this.averageValue = averageValue;
            this.averageIntensity = averageIntensity;
            this.confidence = confidence;
        }

        public static Result empty() {
            return new Result(false, -1, 0d, 0d, 0d);
        }
    }

    public Result analyze(List<EmotionStackAggregator.EmotionStack> stacks, LocalDate start, LocalDate end) {
        if (stacks == null || stacks.isEmpty() || start == null || end == null || start.isAfter(end)) {
            return Result.empty();
        }

        int count = MoodEmotionCatalog.count();
        double[] scoreSums = new double[count];
        double[] valueSums = new double[count];
        double[] intensitySums = new double[count];
        int[] observations = new int[count];

        for (EmotionStackAggregator.EmotionStack stack : stacks) {
            if (stack == null || stack.day == null || stack.day.isBefore(start) || stack.day.isAfter(end) || !stack.hasData) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                double value = valueAt(stack.values, i);
                double intensity = valueAt(stack.intensities, i);
                double percent = valueAt(stack.percentages, i);
                if (value < 0 || intensity < 0) {
                    continue;
                }
                scoreSums[i] += (intensity * 0.65d) + (percent * 0.35d);
                valueSums[i] += value;
                intensitySums[i] += intensity;
                observations[i]++;
            }
        }

        int bestIdx = -1;
        double bestScore = -1d;
        double secondScore = -1d;

        for (int i = 0; i < count; i++) {
            if (observations[i] <= 0) continue;
            double avgScore = scoreSums[i] / observations[i];
            if (avgScore > bestScore) {
                secondScore = bestScore;
                bestScore = avgScore;
                bestIdx = i;
            } else if (avgScore > secondScore) {
                secondScore = avgScore;
            }
        }

        if (bestIdx < 0) {
            return Result.empty();
        }

        double avgValue = valueSums[bestIdx] / observations[bestIdx];
        double avgIntensity = intensitySums[bestIdx] / observations[bestIdx];
        double margin = bestScore - Math.max(0d, secondScore);
        double confidence = clamp01((margin / 30d) * 0.7d + (avgIntensity / 100d) * 0.3d);

        return new Result(true, bestIdx, avgValue, avgIntensity, confidence);
    }

    private double valueAt(double[] values, int idx) {
        if (values == null || idx < 0 || idx >= values.length) return -1d;
        return values[idx];
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
