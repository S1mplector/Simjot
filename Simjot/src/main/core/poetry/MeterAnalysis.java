package main.core.poetry;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable-ish result for meter/rhyme sidebar rendering.
 */
public class MeterAnalysis {
    public final List<String> displayRows;
    public final List<Integer> syllablesByRow;
    public final List<Boolean> stanzaBreakByRow;
    public final List<String> tooltipsByRow;
    public final List<Integer> modelIndexByTextLine; // text line -> row index

    public final int minSyllables;
    public final int maxSyllables;
    public final int countedLines;
    public final double avgSyllables;

    public MeterAnalysis(List<String> displayRows,
                         List<Integer> syllablesByRow,
                         List<Boolean> stanzaBreakByRow,
                         List<String> tooltipsByRow,
                         List<Integer> modelIndexByTextLine,
                         int minSyllables,
                         int maxSyllables,
                         int countedLines,
                         double avgSyllables) {
        this.displayRows = List.copyOf(displayRows);
        this.syllablesByRow = List.copyOf(syllablesByRow);
        this.stanzaBreakByRow = List.copyOf(stanzaBreakByRow);
        this.tooltipsByRow = List.copyOf(tooltipsByRow);
        this.modelIndexByTextLine = List.copyOf(modelIndexByTextLine);
        this.minSyllables = minSyllables;
        this.maxSyllables = maxSyllables;
        this.countedLines = countedLines;
        this.avgSyllables = avgSyllables;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final List<String> displayRows = new ArrayList<>();
        private final List<Integer> syllablesByRow = new ArrayList<>();
        private final List<Boolean> stanzaBreakByRow = new ArrayList<>();
        private final List<String> tooltipsByRow = new ArrayList<>();
        private final List<Integer> modelIndexByTextLine = new ArrayList<>();
        private int minSyl = Integer.MAX_VALUE, maxSyl = 0, counted = 0, total = 0;

        public int size() { return displayRows.size(); }
        public int nextRowIndex() { return displayRows.size(); }

        public void addStanzaSeparator(String text) {
            displayRows.add(text);
            syllablesByRow.add(0);
            stanzaBreakByRow.add(true);
            tooltipsByRow.add(" ");
        }

        public void addLineRow(String display, int syllables, String tooltip) {
            displayRows.add(display);
            syllablesByRow.add(Math.max(0, syllables));
            stanzaBreakByRow.add(false);
            tooltipsByRow.add(tooltip == null ? " " : tooltip);
            if (syllables >= 0) {
                minSyl = Math.min(minSyl, syllables);
                maxSyl = Math.max(maxSyl, syllables);
                total += syllables; counted++;
            }
        }

        public void mapTextLineToCurrentRow(int textLineIndex) {
            // ensure mapping list grows in order
            modelIndexByTextLine.add(displayRows.size() - 1);
        }

        public MeterAnalysis build() {
            double avg = counted > 0 ? (total / (double) counted) : 0.0;
            if (counted == 0) { minSyl = 0; maxSyl = 0; }
            return new MeterAnalysis(displayRows, syllablesByRow, stanzaBreakByRow, tooltipsByRow, modelIndexByTextLine,
                    minSyl, maxSyl, counted, avg);
        }
    }
}
