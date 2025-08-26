package main.core.poetry;

import java.util.*;

/**
 * Computes per-line syllable counts, rhyme labels, stanza separators, and tooltips.
 * Keeps logic UI-agnostic and returns a {@link MeterAnalysis} DTO.
 */
public class MeterScanner {

    public MeterAnalysis analyze(String text, boolean perStanza) {
        List<String> lines = PoetryUtils.splitLines(text);
        MeterAnalysis.Builder b = MeterAnalysis.builder();

        Map<String, Character> keyToLabel = new LinkedHashMap<>();
        int stanzaNo = 1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isBlank = (line == null || line.trim().isEmpty());
            if (isBlank) {
                String sep = String.format(Locale.ROOT, "— stanza %d —", stanzaNo);
                b.addStanzaSeparator(sep);
                b.mapTextLineToCurrentRow(i);
                stanzaNo++;
                if (perStanza) keyToLabel.clear();
                continue;
            }

            int syl = PoetryUtils.countSyllablesInLine(line);
            String end = PoetryUtils.endWord(line);
            String key = end != null ? PoetryUtils.rhymeKey(end) : null;
            Character label = null;
            if (key != null && !key.isBlank()){
                label = keyToLabel.get(key);
                if (label == null) {
                    int idx = keyToLabel.size();
                    label = (char) ('A' + Math.min(idx, 25));
                    keyToLabel.put(key, label);
                }
            }
            String lbl = String.format(Locale.ROOT, "%2d: %2d syl%s%s",
                    (i+1), syl,
                    label!=null?" • ":"",
                    label!=null?label.toString():"");
            String tip;
            if (end != null && key != null) tip = String.format(Locale.ROOT, "End: %s  |  Rhyme key: %s", end, key);
            else if (end != null) tip = String.format(Locale.ROOT, "End: %s", end);
            else tip = " ";

            b.addLineRow(lbl, syl, tip);
            b.mapTextLineToCurrentRow(i);
        }

        return b.build();
    }
}
