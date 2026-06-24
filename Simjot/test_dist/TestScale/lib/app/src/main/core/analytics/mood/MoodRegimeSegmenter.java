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
import java.util.List;

/**
 * Segments mood timeline into directional regimes.
 */
public final class MoodRegimeSegmenter {

    public enum Regime {
        RECOVERY,
        PLATEAU,
        DIP
    }

    public static final class RegimeSegment {
        public final LocalDate start;
        public final LocalDate end;
        public final Regime regime;
        public final double averageMood;
        public final double slope;

        private RegimeSegment(LocalDate start,
                              LocalDate end,
                              Regime regime,
                              double averageMood,
                              double slope) {
            this.start = start;
            this.end = end;
            this.regime = regime;
            this.averageMood = averageMood;
            this.slope = slope;
        }
    }

    public List<RegimeSegment> segment(List<LocalDate> days, List<Double> values, int minSegmentLength) {
        if (days == null || values == null || days.isEmpty() || values.isEmpty() || days.size() != values.size()) {
            return List.of();
        }

        int minLen = Math.max(2, minSegmentLength);

        List<Regime> classifications = new ArrayList<>(days.size());
        for (int i = 0; i < values.size(); i++) {
            classifications.add(classifyPoint(values, i));
        }

        List<RawSegment> raw = buildSegments(days, values, classifications);
        if (raw.isEmpty()) {
            return List.of();
        }

        List<RawSegment> merged = mergeShortSegments(raw, minLen);

        List<RegimeSegment> result = new ArrayList<>(merged.size());
        for (RawSegment segment : merged) {
            result.add(new RegimeSegment(
                    segment.start,
                    segment.end,
                    segment.regime,
                    segment.averageMood,
                    segment.slope
            ));
        }
        return Collections.unmodifiableList(result);
    }

    private Regime classifyPoint(List<Double> values, int index) {
        Double current = values.get(index);
        if (current == null) {
            return Regime.PLATEAU;
        }

        int lookBack = Math.max(0, index - 3);
        int lookForward = Math.min(values.size() - 1, index + 3);

        Double prev = nearest(values, lookBack, index, true);
        Double next = nearest(values, index, lookForward, false);

        if (prev == null || next == null) {
            return Regime.PLATEAU;
        }

        double slope = (next - prev) / Math.max(1d, lookForward - lookBack);
        if (slope > 0.35d) return Regime.RECOVERY;
        if (slope < -0.35d) return Regime.DIP;
        return Regime.PLATEAU;
    }

    private Double nearest(List<Double> values, int from, int to, boolean reverse) {
        if (reverse) {
            for (int i = to; i >= from; i--) {
                Double value = values.get(i);
                if (value != null) return value;
            }
            return null;
        }

        for (int i = from; i <= to; i++) {
            Double value = values.get(i);
            if (value != null) return value;
        }
        return null;
    }

    private List<RawSegment> buildSegments(List<LocalDate> days, List<Double> values, List<Regime> classifications) {
        List<RawSegment> out = new ArrayList<>();
        if (classifications.isEmpty()) return out;

        int start = 0;
        Regime active = classifications.get(0);

        for (int i = 1; i < classifications.size(); i++) {
            Regime now = classifications.get(i);
            if (now != active) {
                out.add(buildRaw(days, values, start, i - 1, active));
                start = i;
                active = now;
            }
        }
        out.add(buildRaw(days, values, start, classifications.size() - 1, active));
        return out;
    }

    private RawSegment buildRaw(List<LocalDate> days,
                                List<Double> values,
                                int from,
                                int to,
                                Regime regime) {
        double sum = 0d;
        int count = 0;
        Double first = null;
        Double last = null;
        for (int i = from; i <= to; i++) {
            Double value = values.get(i);
            if (value == null) continue;
            if (first == null) first = value;
            last = value;
            sum += value;
            count++;
        }

        double avg = count > 0 ? sum / count : 0d;
        double slope = (first != null && last != null && to > from)
                ? (last - first) / (to - from)
                : 0d;

        return new RawSegment(days.get(from), days.get(to), regime, avg, slope, to - from + 1);
    }

    private List<RawSegment> mergeShortSegments(List<RawSegment> segments, int minSegmentLength) {
        if (segments.size() <= 1) {
            return segments;
        }

        List<RawSegment> out = new ArrayList<>();
        for (RawSegment segment : segments) {
            if (!out.isEmpty() && segment.length < minSegmentLength) {
                RawSegment prev = out.remove(out.size() - 1);
                Regime mergedRegime = segment.length <= 2 ? prev.regime : segment.regime;
                out.add(prev.merge(segment, mergedRegime));
            } else {
                out.add(segment);
            }
        }

        return out;
    }

    private static final class RawSegment {
        private final LocalDate start;
        private final LocalDate end;
        private final Regime regime;
        private final double averageMood;
        private final double slope;
        private final int length;

        private RawSegment(LocalDate start,
                           LocalDate end,
                           Regime regime,
                           double averageMood,
                           double slope,
                           int length) {
            this.start = start;
            this.end = end;
            this.regime = regime;
            this.averageMood = averageMood;
            this.slope = slope;
            this.length = length;
        }

        private RawSegment merge(RawSegment other, Regime mergedRegime) {
            int totalLength = Math.max(1, this.length + other.length);
            double avg = ((this.averageMood * this.length) + (other.averageMood * other.length)) / totalLength;
            double mergedSlope = (this.slope + other.slope) / 2d;
            return new RawSegment(this.start, other.end, mergedRegime, avg, mergedSlope, totalLength);
        }
    }
}
