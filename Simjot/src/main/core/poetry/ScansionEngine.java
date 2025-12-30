/*
 * SIMJOT POETRY ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Poetry Engine Proprietary License.
 * You may inspect this code for educational and research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */
package main.core.poetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScansionEngine 
 * 
 * Scansion analysis for poetry.
 * 
 * Features:
 * - Syllable-by-syllable stress marking (stressed/unstressed)
 * - Multiple meter detection (iambic, trochaic, anapestic, dactylic, spondaic)
 * - Foot-by-foot breakdown with visual notation
 * - Metrical substitution detection
 * - Caesura detection
 * - Line-level and poem-level analysis
 * 
 * Uses PoetryDictionary for stress patterns based on the simple-english-dictionary resource.
 */
public class ScansionEngine {
    
    /**
     * Represents metrical foot types.
     */
    public enum FootType {
        IAMB("˘/", "unstressed-stressed"),
        TROCHEE("/˘", "stressed-unstressed"),
        SPONDEE("//", "stressed-stressed"),
        PYRRHIC("˘˘", "unstressed-unstressed"),
        ANAPEST("˘˘/", "unstressed-unstressed-stressed"),
        DACTYL("/˘˘", "stressed-unstressed-unstressed"),
        AMPHIBRACH("˘/˘", "unstressed-stressed-unstressed"),
        UNKNOWN("?", "unknown");
        
        public final String symbol;
        public final String description;
        
        FootType(String symbol, String description) {
            this.symbol = symbol;
            this.description = description;
        }
    }
    
    /**
     * Result of scansion analysis for a single line.
     */
    public static class LineScansion {
        public final String originalLine;
        public final List<SyllableInfo> syllables;
        public final List<FootInfo> feet;
        public final String meterName;
        public final String scansionNotation; // ˘ / pattern
        public final int caesuraPosition; // -1 if none
        public final double meterConfidence;
        public final List<String> substitutions;
        
        public LineScansion(String originalLine, List<SyllableInfo> syllables, List<FootInfo> feet,
                           String meterName, String scansionNotation, int caesuraPosition,
                           double meterConfidence, List<String> substitutions) {
            this.originalLine = originalLine;
            this.syllables = Collections.unmodifiableList(syllables);
            this.feet = Collections.unmodifiableList(feet);
            this.meterName = meterName;
            this.scansionNotation = scansionNotation;
            this.caesuraPosition = caesuraPosition;
            this.meterConfidence = meterConfidence;
            this.substitutions = Collections.unmodifiableList(substitutions);
        }
    }
    
    /**
     * Information about a single syllable.
     */
    public static class SyllableInfo {
        public final String text;
        public final String word;
        public final int wordIndex;
        public final int syllableInWord;
        public final boolean stressed;
        public final double stressConfidence;
        
        public SyllableInfo(String text, String word, int wordIndex, int syllableInWord, 
                          boolean stressed, double stressConfidence) {
            this.text = text;
            this.word = word;
            this.wordIndex = wordIndex;
            this.syllableInWord = syllableInWord;
            this.stressed = stressed;
            this.stressConfidence = stressConfidence;
        }
    }
    
    /**
     * Information about a metrical foot.
     */
    public static class FootInfo {
        public final FootType type;
        public final List<SyllableInfo> syllables;
        public final int startIndex;
        public final boolean isSubstitution;
        
        public FootInfo(FootType type, List<SyllableInfo> syllables, int startIndex, boolean isSubstitution) {
            this.type = type;
            this.syllables = Collections.unmodifiableList(syllables);
            this.startIndex = startIndex;
            this.isSubstitution = isSubstitution;
        }
    }
    
    /**
     * Full poem scansion analysis result.
     */
    public static class PoemScansion {
        public final List<LineScansion> lines;
        public final String dominantMeter;
        public final double overallConfidence;
        public final Map<FootType, Integer> footCounts;
        public final int totalSyllables;
        public final int totalStressed;
        public final int totalUnstressed;
        public final List<String> meterVariations;
        
        public PoemScansion(List<LineScansion> lines, String dominantMeter, double overallConfidence,
                           Map<FootType, Integer> footCounts, int totalSyllables, int totalStressed,
                           int totalUnstressed, List<String> meterVariations) {
            this.lines = Collections.unmodifiableList(lines);
            this.dominantMeter = dominantMeter;
            this.overallConfidence = overallConfidence;
            this.footCounts = Collections.unmodifiableMap(footCounts);
            this.totalSyllables = totalSyllables;
            this.totalStressed = totalStressed;
            this.totalUnstressed = totalUnstressed;
            this.meterVariations = Collections.unmodifiableList(meterVariations);
        }
    }
    
    /**
     * Analyze a complete poem.
     */
    public PoemScansion analyzePoem(String text) {
        if (text == null || text.isBlank()) {
            return new PoemScansion(Collections.emptyList(), "Unknown", 0.0,
                    Collections.emptyMap(), 0, 0, 0, Collections.emptyList());
        }
        
        List<String> rawLines = PoetryUtils.splitLines(text);
        List<LineScansion> lineScansions = new ArrayList<>();
        Map<FootType, Integer> footCounts = new EnumMap<>(FootType.class);
        Map<String, Integer> meterCounts = new HashMap<>();
        int totalSyllables = 0, totalStressed = 0, totalUnstressed = 0;
        List<String> meterVariations = new ArrayList<>();
        
        for (String line : rawLines) {
            if (line == null || line.isBlank()) continue;
            
            LineScansion ls = analyzeLine(line);
            lineScansions.add(ls);
            
            // Aggregate stats
            for (SyllableInfo syl : ls.syllables) {
                totalSyllables++;
                if (syl.stressed) totalStressed++;
                else totalUnstressed++;
            }
            
            for (FootInfo foot : ls.feet) {
                footCounts.merge(foot.type, 1, Integer::sum);
            }
            
            if (!ls.meterName.isEmpty() && !ls.meterName.equals("Unknown")) {
                meterCounts.merge(ls.meterName, 1, Integer::sum);
            }
        }
        
        // Determine dominant meter
        String dominantMeter = "Free Verse";
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : meterCounts.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                dominantMeter = e.getKey();
            }
        }
        
        // Detect meter variations
        for (int i = 1; i < lineScansions.size(); i++) {
            LineScansion prev = lineScansions.get(i - 1);
            LineScansion curr = lineScansions.get(i);
            if (!prev.meterName.equals(curr.meterName) && 
                !prev.meterName.equals("Unknown") && !curr.meterName.equals("Unknown")) {
                meterVariations.add(String.format("Line %d: %s → %s", i + 1, prev.meterName, curr.meterName));
            }
        }
        
        double overallConfidence = lineScansions.isEmpty() ? 0.0 :
                lineScansions.stream().mapToDouble(ls -> ls.meterConfidence).average().orElse(0.0);
        
        return new PoemScansion(lineScansions, dominantMeter, overallConfidence, footCounts,
                totalSyllables, totalStressed, totalUnstressed, meterVariations);
    }
    
    /**
     * Analyze a single line for scansion.
     */
    public LineScansion analyzeLine(String line) {
        if (line == null || line.isBlank()) {
            return new LineScansion(line, Collections.emptyList(), Collections.emptyList(),
                    "Unknown", "", -1, 0.0, Collections.emptyList());
        }
        
        // Extract syllables with stress
        List<SyllableInfo> syllables = extractSyllables(line);
        if (syllables.isEmpty()) {
            return new LineScansion(line, Collections.emptyList(), Collections.emptyList(),
                    "Unknown", "", -1, 0.0, Collections.emptyList());
        }
        
        // Build scansion notation
        StringBuilder notation = new StringBuilder();
        for (SyllableInfo syl : syllables) {
            notation.append(syl.stressed ? "/" : "˘");
        }
        
        // Detect caesura (natural pause, typically mid-line punctuation)
        int caesura = detectCaesura(line, syllables);
        
        // Parse into feet and determine meter
        List<FootInfo> feet = parseIntoFeet(syllables);
        String meterName = determineMeter(syllables, feet);
        double confidence = calculateConfidence(syllables, feet, meterName);
        List<String> substitutions = detectSubstitutions(feet, meterName);
        
        return new LineScansion(line, syllables, feet, meterName, notation.toString(),
                caesura, confidence, substitutions);
    }
    
    /**
     * Extract syllables from a line with stress information.
     */
    private List<SyllableInfo> extractSyllables(String line) {
        List<SyllableInfo> syllables = new ArrayList<>();
        List<String> words = PoetryUtils.wordsInLine(line);
        
        for (int wordIdx = 0; wordIdx < words.size(); wordIdx++) {
            String word = words.get(wordIdx);
            String lower = word.toLowerCase(Locale.ROOT);
            int[] stresses = getWordStress(lower);
            List<String> sylParts = splitIntoSyllables(word);
            
            for (int sylIdx = 0; sylIdx < sylParts.size(); sylIdx++) {
                boolean stressed = (sylIdx < stresses.length) ? stresses[sylIdx] == 1 : (sylIdx == 0);
                double confidence = PoetryDictionary.contains(lower) ? 0.9 : 0.6;
                syllables.add(new SyllableInfo(sylParts.get(sylIdx), word, wordIdx, sylIdx, stressed, confidence));
            }
        }
        
        return syllables;
    }
    
    /**
     * Get stress pattern for a word.
     */
    private int[] getWordStress(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Use PoetryDictionary for stress patterns
        return PoetryDictionary.getStressPattern(lower);
    }
    
    /**
     * Split a word into syllables (approximate).
     */
    private List<String> splitIntoSyllables(String word) {
        List<String> syllables = new ArrayList<>();
        if (word == null || word.isEmpty()) return syllables;
        
        String w = word.toLowerCase(Locale.ROOT);
        int count = PoetryUtils.countSyllables(w);
        if (count <= 1) {
            syllables.add(word);
            return syllables;
        }
        
        // Simple syllable splitting heuristic
        StringBuilder current = new StringBuilder();
        int sylCount = 0;
        boolean prevVowel = false;
        
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            current.append(word.charAt(i));
            
            boolean isVowel = "aeiouy".indexOf(c) >= 0;
            
            if (isVowel && !prevVowel) {
                sylCount++;
                // If we have more syllables to go and next char is consonant, consider splitting
                if (sylCount < count && i < word.length() - 2) {
                    char next = Character.toLowerCase(word.charAt(i + 1));
                    char nextNext = word.length() > i + 2 ? Character.toLowerCase(word.charAt(i + 2)) : ' ';
                    if ("aeiouy".indexOf(next) < 0 && "aeiouy".indexOf(nextNext) >= 0) {
                        syllables.add(current.toString());
                        current = new StringBuilder();
                    }
                }
            }
            prevVowel = isVowel;
        }
        
        if (current.length() > 0) {
            if (syllables.isEmpty()) {
                syllables.add(current.toString());
            } else {
                // Append remaining to last syllable if it doesn't form its own
                String last = syllables.get(syllables.size() - 1);
                syllables.set(syllables.size() - 1, last + current);
            }
        }
        
        // Ensure we have the right number of syllables
        while (syllables.size() < count && syllables.size() > 0) {
            // Split the longest syllable
            int maxLen = 0, maxIdx = 0;
            for (int i = 0; i < syllables.size(); i++) {
                if (syllables.get(i).length() > maxLen) {
                    maxLen = syllables.get(i).length();
                    maxIdx = i;
                }
            }
            if (maxLen > 2) {
                String s = syllables.get(maxIdx);
                int mid = s.length() / 2;
                syllables.set(maxIdx, s.substring(0, mid));
                syllables.add(maxIdx + 1, s.substring(mid));
            } else {
                break;
            }
        }
        
        return syllables;
    }
    
    /**
     * Parse syllables into metrical feet.
     */
    private List<FootInfo> parseIntoFeet(List<SyllableInfo> syllables) {
        List<FootInfo> feet = new ArrayList<>();
        if (syllables.isEmpty()) return feet;
        
        // Try to detect the dominant foot type first
        FootType dominant = detectDominantFoot(syllables);
        int footSize = (dominant == FootType.ANAPEST || dominant == FootType.DACTYL || 
                       dominant == FootType.AMPHIBRACH) ? 3 : 2;
        
        int i = 0;
        while (i < syllables.size()) {
            int remaining = syllables.size() - i;
            
            if (remaining >= 3 && footSize == 3) {
                // Try three-syllable foot
                List<SyllableInfo> group = syllables.subList(i, i + 3);
                FootType type = classifyFoot(group);
                boolean isSub = (type != dominant && dominant != FootType.UNKNOWN);
                feet.add(new FootInfo(type, new ArrayList<>(group), i, isSub));
                i += 3;
            } else if (remaining >= 2) {
                // Two-syllable foot
                List<SyllableInfo> group = syllables.subList(i, i + 2);
                FootType type = classifyFoot(group);
                boolean isSub = (type != dominant && dominant != FootType.UNKNOWN);
                feet.add(new FootInfo(type, new ArrayList<>(group), i, isSub));
                i += 2;
            } else {
                // Single remaining syllable (catalexis)
                List<SyllableInfo> group = syllables.subList(i, i + 1);
                feet.add(new FootInfo(FootType.UNKNOWN, new ArrayList<>(group), i, false));
                i++;
            }
        }
        
        return feet;
    }
    
    /**
     * Classify a group of syllables as a foot type.
     */
    private FootType classifyFoot(List<SyllableInfo> syllables) {
        if (syllables.isEmpty()) return FootType.UNKNOWN;
        
        StringBuilder pattern = new StringBuilder();
        for (SyllableInfo s : syllables) {
            pattern.append(s.stressed ? "1" : "0");
        }
        String p = pattern.toString();
        
        return switch (p) {
            case "01" -> FootType.IAMB;
            case "10" -> FootType.TROCHEE;
            case "11" -> FootType.SPONDEE;
            case "00" -> FootType.PYRRHIC;
            case "001" -> FootType.ANAPEST;
            case "100" -> FootType.DACTYL;
            case "010" -> FootType.AMPHIBRACH;
            default -> FootType.UNKNOWN;
        };
    }
    
    /**
     * Detect the dominant foot type in the line.
     */
    private FootType detectDominantFoot(List<SyllableInfo> syllables) {
        if (syllables.size() < 2) return FootType.UNKNOWN;
        
        // Count patterns
        int iambic = 0, trochaic = 0, anapestic = 0, dactylic = 0;
        
        for (int i = 0; i < syllables.size() - 1; i += 2) {
            boolean s1 = syllables.get(i).stressed;
            boolean s2 = syllables.get(i + 1).stressed;
            if (!s1 && s2) iambic++;
            else if (s1 && !s2) trochaic++;
        }
        
        for (int i = 0; i < syllables.size() - 2; i += 3) {
            boolean s1 = syllables.get(i).stressed;
            boolean s2 = syllables.get(i + 1).stressed;
            boolean s3 = syllables.get(i + 2).stressed;
            if (!s1 && !s2 && s3) anapestic++;
            else if (s1 && !s2 && !s3) dactylic++;
        }
        
        int max = Math.max(Math.max(iambic, trochaic), Math.max(anapestic, dactylic));
        if (max == 0) return FootType.UNKNOWN;
        
        if (max == iambic) return FootType.IAMB;
        if (max == trochaic) return FootType.TROCHEE;
        if (max == anapestic) return FootType.ANAPEST;
        if (max == dactylic) return FootType.DACTYL;
        
        return FootType.UNKNOWN;
    }
    
    /**
     * Determine the meter name based on analysis.
     */
    private String determineMeter(List<SyllableInfo> syllables, List<FootInfo> feet) {
        if (feet.isEmpty()) return "Unknown";
        
        // Count foot types
        Map<FootType, Integer> counts = new EnumMap<>(FootType.class);
        for (FootInfo f : feet) {
            counts.merge(f.type, 1, Integer::sum);
        }
        
        // Find dominant foot
        FootType dominant = FootType.UNKNOWN;
        int maxCount = 0;
        for (Map.Entry<FootType, Integer> e : counts.entrySet()) {
            if (e.getValue() > maxCount && e.getKey() != FootType.UNKNOWN) {
                maxCount = e.getValue();
                dominant = e.getKey();
            }
        }
        
        if (dominant == FootType.UNKNOWN) return "Unknown";
        
        // Determine meter length
        String length = switch (feet.size()) {
            case 1 -> "monometer";
            case 2 -> "dimeter";
            case 3 -> "trimeter";
            case 4 -> "tetrameter";
            case 5 -> "pentameter";
            case 6 -> "hexameter";
            case 7 -> "heptameter";
            default -> feet.size() + "-foot";
        };
        
        String type = switch (dominant) {
            case IAMB -> "Iambic";
            case TROCHEE -> "Trochaic";
            case SPONDEE -> "Spondaic";
            case ANAPEST -> "Anapestic";
            case DACTYL -> "Dactylic";
            case AMPHIBRACH -> "Amphibrachic";
            default -> "Mixed";
        };
        
        return type + " " + length;
    }
    
    /**
     * Calculate confidence in the meter analysis.
     */
    private double calculateConfidence(List<SyllableInfo> syllables, List<FootInfo> feet, String meterName) {
        if (feet.isEmpty() || meterName.equals("Unknown")) return 0.0;
        
        // Base confidence on syllable stress confidence and foot consistency
        double sylConfidence = syllables.stream()
                .mapToDouble(s -> s.stressConfidence)
                .average().orElse(0.5);
        
        // Count non-substitution feet
        long regular = feet.stream().filter(f -> !f.isSubstitution).count();
        double footConsistency = (double) regular / feet.size();
        
        return (sylConfidence * 0.4 + footConsistency * 0.6);
    }
    
    /**
     * Detect metrical substitutions.
     */
    private List<String> detectSubstitutions(List<FootInfo> feet, String meterName) {
        List<String> substitutions = new ArrayList<>();
        
        for (int i = 0; i < feet.size(); i++) {
            FootInfo foot = feet.get(i);
            if (foot.isSubstitution) {
                substitutions.add(String.format("Foot %d: %s substitution (%s)", 
                    i + 1, foot.type.name().toLowerCase(), foot.type.description));
            }
        }
        
        return substitutions;
    }
    
    /**
     * Detect caesura (mid-line pause).
     */
    private int detectCaesura(String line, List<SyllableInfo> syllables) {
        if (syllables.size() < 4) return -1;
        
        // Look for punctuation that suggests a pause
        Pattern pausePattern = Pattern.compile("[,;:\\-–—]");
        Matcher m = pausePattern.matcher(line);
        
        if (m.find()) {
            int pos = m.start();
            // Find which syllable this falls after
            int charCount = 0;
            for (int i = 0; i < syllables.size(); i++) {
                charCount += syllables.get(i).text.length() + 1; // +1 for space
                if (charCount >= pos) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Get a human-readable scansion display for a line.
     */
    public String getScansionDisplay(LineScansion scansion) {
        if (scansion == null || scansion.syllables.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder();
        
        // Line 1: Original text
        sb.append(scansion.originalLine).append("\n");
        
        // Line 2: Stress marks aligned to syllables
        StringBuilder marks = new StringBuilder();
        StringBuilder words = new StringBuilder();
        
        for (SyllableInfo syl : scansion.syllables) {
            String mark = syl.stressed ? "/" : "˘";
            int len = Math.max(syl.text.length(), 2);
            marks.append(String.format("%-" + len + "s", mark));
            words.append(String.format("%-" + len + "s", syl.text));
        }
        
        sb.append(marks.toString().trim()).append("\n");
        sb.append(words.toString().trim()).append("\n");
        
        // Line 3: Meter info
        sb.append(String.format("%s (%.0f%% confidence)", 
            scansion.meterName, scansion.meterConfidence * 100));
        
        if (scansion.caesuraPosition >= 0) {
            sb.append(" • Caesura at syllable ").append(scansion.caesuraPosition + 1);
        }
        
        if (!scansion.substitutions.isEmpty()) {
            sb.append("\nSubstitutions: ").append(String.join(", ", scansion.substitutions));
        }
        
        return sb.toString();
    }
}
