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
 * Computes restorative/challenging balance from detailed emotion data.
 */
public final class EmotionBalanceEngine {

    public static final class BalanceResult {
        public final boolean hasData;
        public final int restorativePercent;
        public final int challengingPercent;
        public final String tilt;

        private BalanceResult(boolean hasData,
                              int restorativePercent,
                              int challengingPercent,
                              String tilt) {
            this.hasData = hasData;
            this.restorativePercent = restorativePercent;
            this.challengingPercent = challengingPercent;
            this.tilt = tilt;
        }

        public static BalanceResult empty() {
            return new BalanceResult(false, 0, 0, "unknown");
        }
    }

    public BalanceResult analyze(List<EmotionStackAggregator.EmotionStack> stacks, LocalDate start, LocalDate end) {
        if (stacks == null || stacks.isEmpty() || start == null || end == null || start.isAfter(end)) {
            return BalanceResult.empty();
        }

        double restorative = 0d;
        double challenging = 0d;
        int restorativeCount = 0;
        int challengingCount = 0;

        for (EmotionStackAggregator.EmotionStack stack : stacks) {
            if (stack == null || stack.day == null || stack.day.isBefore(start) || stack.day.isAfter(end) || !stack.hasData) {
                continue;
            }

            for (int i = 0; i < MoodEmotionCatalog.count(); i++) {
                double value = valueAt(stack.values, i);
                if (value < 0) continue;
                if (MoodEmotionCatalog.isRestorative(i)) {
                    restorative += value;
                    restorativeCount++;
                } else {
                    challenging += value;
                    challengingCount++;
                }
            }
        }

        if (restorativeCount <= 0 && challengingCount <= 0) {
            return BalanceResult.empty();
        }

        double restorativeAvg = restorativeCount > 0 ? restorative / restorativeCount : 0d;
        double challengingAvg = challengingCount > 0 ? challenging / challengingCount : 0d;
        double total = restorativeAvg + challengingAvg;
        if (total <= 0.01d) {
            return new BalanceResult(true, 50, 50, "balanced");
        }

        int restorativePct = (int) Math.round((restorativeAvg * 100d) / total);
        restorativePct = Math.max(0, Math.min(100, restorativePct));
        int challengingPct = Math.max(0, 100 - restorativePct);

        String tilt;
        if (restorativePct >= 56) {
            tilt = "restorative-leaning";
        } else if (challengingPct >= 56) {
            tilt = "challenging-leaning";
        } else {
            tilt = "balanced";
        }

        return new BalanceResult(true, restorativePct, challengingPct, tilt);
    }

    private double valueAt(double[] values, int idx) {
        if (values == null || idx < 0 || idx >= values.length) return -1d;
        return values[idx];
    }
}
