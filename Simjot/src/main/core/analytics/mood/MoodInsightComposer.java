/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Composes compact narrative insights from mood analytics outputs.
 */
public final class MoodInsightComposer {

    public String composeDominantLine(EmotionDominanceEngine.Result dominant,
                                      MoodSemanticLabeler semanticLabeler) {
        if (dominant == null || !dominant.hasData || dominant.dominantEmotion < 0) {
            return "Dominant this week: no detailed emotion data yet.";
        }

        int value = (int) Math.round(dominant.averageValue);
        int intensity = (int) Math.round(dominant.averageIntensity);
        String semantic = semanticLabeler.labelEmotion(dominant.dominantEmotion, value);

        return "Dominant this week: "
                + MoodEmotionCatalog.emotionName(dominant.dominantEmotion)
                + " • " + semantic
                + " (" + intensity + "% intensity)";
    }

    public String composeBalanceLine(EmotionBalanceEngine.BalanceResult balance) {
        if (balance == null || !balance.hasData) {
            return "Balance: no detailed emotion data yet.";
        }

        return "Balance: " + balance.restorativePercent + "% restorative • "
                + balance.challengingPercent + "% challenging (" + balance.tilt + ")";
    }

    public String composeShiftLine(EmotionDominanceEngine.Result current,
                                   EmotionDominanceEngine.Result previous,
                                   MoodSemanticLabeler semanticLabeler) {
        if (current == null || previous == null || !current.hasData || !previous.hasData
                || current.dominantEmotion < 0 || previous.dominantEmotion < 0) {
            return "Shift: need two weeks of detail to compare.";
        }

        int delta = (int) Math.round(current.averageValue - previous.averageValue);
        String direction = delta >= 0 ? "+" : "-";
        String semantic = semanticLabeler.labelEmotion(current.dominantEmotion, (int) Math.round(current.averageValue));

        if (current.dominantEmotion != previous.dominantEmotion) {
            return "Shift: "
                    + MoodEmotionCatalog.emotionName(previous.dominantEmotion)
                    + " → "
                    + MoodEmotionCatalog.emotionName(current.dominantEmotion)
                    + " (" + semantic + ")";
        }

        return "Shift: "
                + MoodEmotionCatalog.emotionName(current.dominantEmotion)
                + " " + direction + Math.abs(delta)
                + " vs previous week • " + semantic;
    }

    public String composeCoverageLine(MoodCoverageAnalyzer.CoverageResult coverage) {
        if (coverage == null || coverage.daysInRange <= 0) {
            return "Coverage: no data";
        }

        return "Coverage: "
                + coverage.moodCoveragePercent + "% mood days • "
                + coverage.detailCoveragePercent + "% detailed ("
                + coverage.reliabilityLabel + ")";
    }

    public String composeMomentumLine(MoodMomentumEngine.MomentumResult momentum) {
        if (momentum == null) {
            return "Trend: unavailable";
        }

        String accel;
        if (momentum.acceleration > 0.18d) {
            accel = "accelerating";
        } else if (momentum.acceleration < -0.18d) {
            accel = "cooling";
        } else {
            accel = "steady";
        }

        return "Trend: " + momentum.arrow() + " " + momentum.label() + " (" + accel + ")";
    }

    public String composeRegimeLine(List<MoodRegimeSegmenter.RegimeSegment> regimes) {
        if (regimes == null || regimes.isEmpty()) {
            return "Regime: not enough data";
        }

        MoodRegimeSegmenter.RegimeSegment current = regimes.get(regimes.size() - 1);
        String label = switch (current.regime) {
            case RECOVERY -> "Recovery";
            case DIP -> "Dip";
            case PLATEAU -> "Plateau";
        };

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        return "Regime: " + label + " since " + fmt.format(current.start);
    }

    public String composeAnomalyLine(List<MoodAnomalyDetector.AnomalyPoint> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return "Anomalies: none";
        }

        int spikes = 0;
        int dips = 0;
        for (MoodAnomalyDetector.AnomalyPoint anomaly : anomalies) {
            if (anomaly.type == MoodAnomalyDetector.Type.SPIKE) spikes++;
            else dips++;
        }

        return "Anomalies: " + anomalies.size() + " (" + spikes + " spikes, " + dips + " dips)";
    }
}
