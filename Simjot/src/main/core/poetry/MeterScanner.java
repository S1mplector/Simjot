/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.poetry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MeterScanner utility 
 * 
 * Computes per-line syllable counts, rhyme labels, stanza separators, tooltips,
 * and detects common poetic forms and meter patterns.
 * 
 * @author S1mplector
 * @version 1.0.0
 */
public class MeterScanner {

    public MeterAnalysis analyze(String text, boolean perStanza) {
        List<String> lines = PoetryUtils.splitLines(text);
        MeterAnalysis.Builder b = MeterAnalysis.builder();

        Map<String, Character> keyToLabel = new LinkedHashMap<>();
        int stanzaNo = 1;
        List<Integer> syllableCounts = new ArrayList<>();
        List<String> rhymeLabels = new ArrayList<>();

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
            syllableCounts.add(syl);
            
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
            rhymeLabels.add(label != null ? label.toString() : "");
            
            // Enhanced tooltip with meter info
            String meterHint = detectLineMeter(line);
            String lbl = String.format(Locale.ROOT, "%2d: %2d syl%s%s",
                    (i+1), syl,
                    label!=null?" • ":"",
                    label!=null?label.toString():"");
            String tip;
            if (end != null && key != null) {
                tip = String.format(Locale.ROOT, "End: %s  |  Rhyme: %s%s", 
                    end, key, meterHint.isEmpty() ? "" : "  |  " + meterHint);
            } else if (end != null) {
                tip = String.format(Locale.ROOT, "End: %s%s", 
                    end, meterHint.isEmpty() ? "" : "  |  " + meterHint);
            } else {
                tip = meterHint.isEmpty() ? " " : meterHint;
            }

            int[] stressPattern = PoetryUtils.estimateLineStressPattern(line);
            b.addLineRow(lbl, syl, tip, label != null ? label.toString() : "");
            b.addStressPattern(stressPattern);
            b.mapTextLineToCurrentRow(i);
        }

        String form = detectPoeticForm(syllableCounts, rhymeLabels);
        b.setDetectedForm(form);
        return b.build();
    }
    
    /**
     * Detect the meter pattern of a single line.
     */
    private String detectLineMeter(String line) {
        if (line == null || line.trim().isEmpty()) return "";
        
        int syllables = PoetryUtils.countSyllablesInLine(line);
        
        // Check common meters based on syllable count
        if (syllables == 10) {
            if (PoetryUtils.isIambic(line)) return "Iambic pentameter";
            if (PoetryUtils.isTrochaic(line)) return "Trochaic pentameter";
            return "Pentameter (10 syl)";
        } else if (syllables == 8) {
            if (PoetryUtils.isIambic(line)) return "Iambic tetrameter";
            if (PoetryUtils.isTrochaic(line)) return "Trochaic tetrameter";
            return "Tetrameter (8 syl)";
        } else if (syllables == 6) {
            return "Trimeter (6 syl)";
        } else if (syllables == 12) {
            return "Hexameter (12 syl)";
        } else if (syllables == 14) {
            return "Heptameter (14 syl)";
        }
        
        return "";
    }
    
    /**
     * Detect the poetic form based on line counts and rhyme scheme.
     */
    public static String detectPoeticForm(List<Integer> syllables, List<String> rhymes) {
        int lineCount = syllables.size();
        String rhymeScheme = String.join("", rhymes);
        
        // Sonnet detection (14 lines)
        if (lineCount == 14) {
            // Check for iambic pentameter (most lines ~10 syllables)
            long pentameterLines = syllables.stream().filter(s -> s >= 9 && s <= 11).count();
            if (pentameterLines >= 10) {
                // Shakespearean sonnet: ABAB CDCD EFEF GG
                if (rhymeScheme.matches("ABAB.?CDCD.?EFEF.?GG")) {
                    return "Shakespearean Sonnet";
                }
                // Petrarchan sonnet: ABBAABBA + various sestets
                if (rhymeScheme.startsWith("ABBAABBA")) {
                    return "Petrarchan Sonnet";
                }
                return "Sonnet (14 lines)";
            }
        }
        
        // Haiku detection (3 lines, 5-7-5 syllables)
        if (lineCount == 3) {
            if (syllables.get(0) == 5 && syllables.get(1) == 7 && syllables.get(2) == 5) {
                return "Haiku (5-7-5)";
            }
            // Close to haiku
            int total = syllables.stream().mapToInt(Integer::intValue).sum();
            if (total >= 15 && total <= 19) {
                return "Haiku-like (3 lines)";
            }
        }
        
        // Limerick detection (5 lines, AABBA)
        if (lineCount == 5 && rhymeScheme.equals("AABBA")) {
            return "Limerick";
        }
        
        // Quatrain detection (4 lines)
        if (lineCount == 4) {
            if (rhymeScheme.equals("ABAB")) return "Quatrain (ABAB)";
            if (rhymeScheme.equals("ABBA")) return "Quatrain (ABBA)";
            if (rhymeScheme.equals("AABB")) return "Couplet Quatrain";
        }
        
        // Couplet detection (2 lines, AA)
        if (lineCount == 2 && rhymeScheme.equals("AA")) {
            return "Couplet";
        }
        
        // Tercet detection (3 lines)
        if (lineCount == 3) {
            if (rhymeScheme.equals("ABA")) return "Tercet (ABA)";
            if (rhymeScheme.equals("AAA")) return "Triplet";
        }
        
        // Free verse (no consistent pattern)
        if (lineCount >= 4) {
            Set<Integer> uniqueSyllables = new HashSet<>(syllables);
            if (uniqueSyllables.size() > lineCount / 2) {
                return "Free Verse";
            }
        }
        
        return "";
    }
    
    /**
     * Get statistics about the poem.
     */
    public static Map<String, Object> getStatistics(String text) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<String> lines = PoetryUtils.splitLines(text);
        
        int totalLines = 0;
        int totalWords = 0;
        int totalSyllables = 0;
        int stanzaCount = 1;
        
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                stanzaCount++;
                continue;
            }
            totalLines++;
            List<String> words = PoetryUtils.wordsInLine(line);
            totalWords += words.size();
            totalSyllables += PoetryUtils.countSyllablesInLine(line);
        }
        
        stats.put("lines", totalLines);
        stats.put("words", totalWords);
        stats.put("syllables", totalSyllables);
        stats.put("stanzas", stanzaCount);
        stats.put("avgSyllablesPerLine", totalLines > 0 ? (double) totalSyllables / totalLines : 0);
        stats.put("avgWordsPerLine", totalLines > 0 ? (double) totalWords / totalLines : 0);
        
        return stats;
    }
}
