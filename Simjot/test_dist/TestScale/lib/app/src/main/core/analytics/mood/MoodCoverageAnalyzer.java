/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.analytics.mood;

import java.util.List;

/**
 * Measures completeness and reliability of mood logging.
 */
public final class MoodCoverageAnalyzer {

    public static final class CoverageResult {
        public final int daysInRange;
        public final int daysWithMood;
        public final int daysWithDetail;
        public final int totalSamples;
        public final int detailedSamples;
        public final int detailCoveragePercent;
        public final int moodCoveragePercent;
        public final int longestMissingStreak;
        public final String reliabilityLabel;

        private CoverageResult(int daysInRange,
                               int daysWithMood,
                               int daysWithDetail,
                               int totalSamples,
                               int detailedSamples,
                               int detailCoveragePercent,
                               int moodCoveragePercent,
                               int longestMissingStreak,
                               String reliabilityLabel) {
            this.daysInRange = daysInRange;
            this.daysWithMood = daysWithMood;
            this.daysWithDetail = daysWithDetail;
            this.totalSamples = totalSamples;
            this.detailedSamples = detailedSamples;
            this.detailCoveragePercent = detailCoveragePercent;
            this.moodCoveragePercent = moodCoveragePercent;
            this.longestMissingStreak = longestMissingStreak;
            this.reliabilityLabel = reliabilityLabel;
        }

        public static CoverageResult empty() {
            return new CoverageResult(0, 0, 0, 0, 0, 0, 0, 0, "No data");
        }
    }

    public CoverageResult analyze(List<Double> moodValues,
                                  List<EmotionStackAggregator.EmotionStack> emotionStacks,
                                  int totalSamples,
                                  int detailedSamples) {
        if ((moodValues == null || moodValues.isEmpty()) && (emotionStacks == null || emotionStacks.isEmpty())) {
            return CoverageResult.empty();
        }

        int daysInRange = moodValues != null ? moodValues.size() : 0;
        int daysWithMood = 0;
        int longestMissing = 0;
        int currentMissing = 0;

        if (moodValues != null) {
            for (Double value : moodValues) {
                if (value != null) {
                    daysWithMood++;
                    currentMissing = 0;
                } else {
                    currentMissing++;
                    if (currentMissing > longestMissing) {
                        longestMissing = currentMissing;
                    }
                }
            }
        }

        int daysWithDetail = 0;
        if (emotionStacks != null) {
            for (EmotionStackAggregator.EmotionStack stack : emotionStacks) {
                if (stack != null && stack.hasData) {
                    daysWithDetail++;
                }
            }
        }

        int moodCoverage = daysInRange > 0 ? (int) Math.round((daysWithMood * 100d) / daysInRange) : 0;
        int detailCoverage;
        if (totalSamples > 0) {
            detailCoverage = (int) Math.round((Math.max(0, detailedSamples) * 100d) / totalSamples);
        } else {
            detailCoverage = daysWithMood > 0 ? (int) Math.round((daysWithDetail * 100d) / daysWithMood) : 0;
        }
        detailCoverage = Math.max(0, Math.min(100, detailCoverage));

        String reliability = reliabilityLabel(moodCoverage, detailCoverage, longestMissing);

        return new CoverageResult(
                daysInRange,
                daysWithMood,
                daysWithDetail,
                Math.max(0, totalSamples),
                Math.max(0, detailedSamples),
                detailCoverage,
                moodCoverage,
                longestMissing,
                reliability
        );
    }

    private String reliabilityLabel(int moodCoverage, int detailCoverage, int longestMissingStreak) {
        if (moodCoverage >= 85 && detailCoverage >= 60 && longestMissingStreak <= 3) return "Robust";
        if (moodCoverage >= 65 && detailCoverage >= 35 && longestMissingStreak <= 7) return "Usable";
        if (moodCoverage >= 45) return "Patchy";
        return "Sparse";
    }
}
