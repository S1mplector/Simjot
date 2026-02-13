/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds consistently sampled mood series from sparse daily data.
 */
public final class MoodSeriesResampler {

    public enum GapPolicy {
        NONE,
        CARRY_FORWARD,
        INTERPOLATE
    }

    public static final class Point {
        public final LocalDate day;
        public final Double value;
        public final boolean synthetic;

        public Point(LocalDate day, Double value, boolean synthetic) {
            this.day = day;
            this.value = value;
            this.synthetic = synthetic;
        }
    }

    public static final class ResampledSeries {
        public final List<LocalDate> days;
        public final List<Double> values;
        public final List<Boolean> synthetic;

        public ResampledSeries(List<LocalDate> days, List<Double> values, List<Boolean> synthetic) {
            this.days = Collections.unmodifiableList(days);
            this.values = Collections.unmodifiableList(values);
            this.synthetic = Collections.unmodifiableList(synthetic);
        }

        public int size() {
            return days.size();
        }

        public boolean isEmpty() {
            return days.isEmpty();
        }
    }

    public ResampledSeries resampleDaily(Map<LocalDate, Double> valuesByDay,
                                         LocalDate start,
                                         LocalDate end,
                                         GapPolicy gapPolicy) {
        if (valuesByDay == null || valuesByDay.isEmpty() || start == null || end == null || start.isAfter(end)) {
            return new ResampledSeries(List.of(), List.of(), List.of());
        }

        int size = (int) ChronoUnit.DAYS.between(start, end) + 1;
        List<LocalDate> days = new ArrayList<>(size);
        List<Double> values = new ArrayList<>(size);
        List<Boolean> synthetic = new ArrayList<>(size);

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(d);
            values.add(valuesByDay.get(d));
            synthetic.add(Boolean.FALSE);
        }

        if (gapPolicy == null || gapPolicy == GapPolicy.NONE) {
            return new ResampledSeries(days, values, synthetic);
        }

        if (gapPolicy == GapPolicy.CARRY_FORWARD) {
            Double lastKnown = null;
            for (int i = 0; i < values.size(); i++) {
                Double current = values.get(i);
                if (current != null) {
                    lastKnown = current;
                } else if (lastKnown != null) {
                    values.set(i, lastKnown);
                    synthetic.set(i, Boolean.TRUE);
                }
            }
            return new ResampledSeries(days, values, synthetic);
        }

        int[] prevKnown = new int[size];
        int[] nextKnown = new int[size];
        int prev = -1;
        for (int i = 0; i < size; i++) {
            if (values.get(i) != null) prev = i;
            prevKnown[i] = prev;
        }

        int next = -1;
        for (int i = size - 1; i >= 0; i--) {
            if (values.get(i) != null) next = i;
            nextKnown[i] = next;
        }

        for (int i = 0; i < size; i++) {
            if (values.get(i) != null) continue;

            int prevIdx = prevKnown[i];
            int nextIdx = nextKnown[i];

            Double filled = null;
            if (prevIdx >= 0 && nextIdx >= 0 && prevIdx != nextIdx) {
                double prevValue = values.get(prevIdx);
                double nextValue = values.get(nextIdx);
                double t = (i - prevIdx) / (double) (nextIdx - prevIdx);
                filled = prevValue + ((nextValue - prevValue) * t);
            } else if (prevIdx >= 0) {
                filled = values.get(prevIdx);
            } else if (nextIdx >= 0) {
                filled = values.get(nextIdx);
            }

            if (filled != null) {
                values.set(i, filled);
                synthetic.set(i, Boolean.TRUE);
            }
        }

        return new ResampledSeries(days, values, synthetic);
    }

    public ResampledSeries resampleWeekly(ResampledSeries dailySeries) {
        if (dailySeries == null || dailySeries.isEmpty()) {
            return new ResampledSeries(List.of(), List.of(), List.of());
        }

        Map<LocalDate, double[]> weekly = new LinkedHashMap<>();
        for (int i = 0; i < dailySeries.days.size(); i++) {
            LocalDate day = dailySeries.days.get(i);
            Double value = dailySeries.values.get(i);
            if (value == null) {
                continue;
            }

            LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            double[] agg = weekly.computeIfAbsent(weekStart, k -> new double[2]);
            agg[0] += value;
            agg[1] += 1d;
        }

        List<LocalDate> outDays = new ArrayList<>(weekly.size());
        List<Double> outValues = new ArrayList<>(weekly.size());
        List<Boolean> outSynthetic = new ArrayList<>(weekly.size());

        for (Map.Entry<LocalDate, double[]> e : weekly.entrySet()) {
            outDays.add(e.getKey());
            double[] agg = e.getValue();
            outValues.add(agg[1] > 0d ? agg[0] / agg[1] : null);
            outSynthetic.add(Boolean.FALSE);
        }

        return new ResampledSeries(outDays, outValues, outSynthetic);
    }
}
