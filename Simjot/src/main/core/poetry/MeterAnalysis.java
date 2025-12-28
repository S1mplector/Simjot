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
    public final List<Integer> syllablesByTextLine; // non-blank lines only (order preserved)
    public final List<String> rhymeLabelsByTextLine; // rhyme scheme labels aligned to syllablesByTextLine
    public final String detectedForm;
    public final java.util.List<int[]> stressByTextLine; // 0/1 patterns per line (best-effort)

    public final int minSyllables;
    public final int maxSyllables;
    public final int countedLines;
    public final double avgSyllables;

    public MeterAnalysis(List<String> displayRows,
                         List<Integer> syllablesByRow,
                         List<Boolean> stanzaBreakByRow,
                         List<String> tooltipsByRow,
                         List<Integer> modelIndexByTextLine,
                         List<Integer> syllablesByTextLine,
                         List<String> rhymeLabelsByTextLine,
                         String detectedForm,
                         java.util.List<int[]> stressByTextLine,
                         int minSyllables,
                         int maxSyllables,
                         int countedLines,
                         double avgSyllables) {
        this.displayRows = List.copyOf(displayRows);
        this.syllablesByRow = List.copyOf(syllablesByRow);
        this.stanzaBreakByRow = List.copyOf(stanzaBreakByRow);
        this.tooltipsByRow = List.copyOf(tooltipsByRow);
        this.modelIndexByTextLine = List.copyOf(modelIndexByTextLine);
        this.syllablesByTextLine = List.copyOf(syllablesByTextLine);
        this.rhymeLabelsByTextLine = List.copyOf(rhymeLabelsByTextLine);
        this.detectedForm = detectedForm == null ? "" : detectedForm;
        this.stressByTextLine = stressByTextLine == null ? List.of() : List.copyOf(stressByTextLine);
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
        private final List<Integer> syllablesByTextLine = new ArrayList<>();
        private final List<String> rhymeLabelsByTextLine = new ArrayList<>();
        private final java.util.List<int[]> stressByTextLine = new ArrayList<>();
        private int minSyl = Integer.MAX_VALUE, maxSyl = 0, counted = 0, total = 0;
        private String detectedForm = "";

        public int size() { return displayRows.size(); }
        public int nextRowIndex() { return displayRows.size(); }

        public void addStanzaSeparator(String text) {
            displayRows.add(text);
            syllablesByRow.add(0);
            stanzaBreakByRow.add(true);
            tooltipsByRow.add(" ");
        }

        public void addLineRow(String display, int syllables, String tooltip, String rhymeLabel) {
            displayRows.add(display);
            syllablesByRow.add(Math.max(0, syllables));
            stanzaBreakByRow.add(false);
            tooltipsByRow.add(tooltip == null ? " " : tooltip);
            syllablesByTextLine.add(Math.max(0, syllables));
            rhymeLabelsByTextLine.add(rhymeLabel == null ? "" : rhymeLabel);
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

        public void setDetectedForm(String form) {
            this.detectedForm = form == null ? "" : form;
        }

        public void addStressPattern(int[] pattern) {
            if (pattern == null) {
                stressByTextLine.add(new int[0]);
            } else {
                stressByTextLine.add(java.util.Arrays.copyOf(pattern, pattern.length));
            }
        }

        public MeterAnalysis build() {
            double avg = counted > 0 ? (total / (double) counted) : 0.0;
            if (counted == 0) { minSyl = 0; maxSyl = 0; }
            return new MeterAnalysis(displayRows, syllablesByRow, stanzaBreakByRow, tooltipsByRow, modelIndexByTextLine,
                    syllablesByTextLine, rhymeLabelsByTextLine, detectedForm, stressByTextLine,
                    minSyl, maxSyl, counted, avg);
        }
    }
}
