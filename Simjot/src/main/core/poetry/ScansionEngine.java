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
    
    private static final List<MeterPattern> METER_PATTERNS = List.of(
        new MeterPattern(FootType.IAMB, "01"),
        new MeterPattern(FootType.TROCHEE, "10"),
        new MeterPattern(FootType.SPONDEE, "11"),
        new MeterPattern(FootType.PYRRHIC, "00"),
        new MeterPattern(FootType.ANAPEST, "001"),
        new MeterPattern(FootType.DACTYL, "100"),
        new MeterPattern(FootType.AMPHIBRACH, "010")
    );
    
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
    
    private static class MeterPattern {
        final FootType footType;
        final String pattern;
        
        MeterPattern(FootType footType, String pattern) {
            this.footType = footType;
            this.pattern = pattern;
        }
    }
    
    private static class MeterProfile {
        final FootType dominantFoot;
        final int footSize;
        final int footCount;
        final int startOffset;
        final boolean anacrusis;
        final boolean feminineEnding;
        final double mismatchRatio;
        final String meterName;
        
        MeterProfile(FootType dominantFoot, int footSize, int footCount, int startOffset,
                     boolean anacrusis, boolean feminineEnding, double mismatchRatio, String meterName) {
            this.dominantFoot = dominantFoot;
            this.footSize = footSize;
            this.footCount = footCount;
            this.startOffset = startOffset;
            this.anacrusis = anacrusis;
            this.feminineEnding = feminineEnding;
            this.mismatchRatio = mismatchRatio;
            this.meterName = meterName;
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
        
        // Determine best-fit meter and parse into feet
        MeterProfile profile = determineBestMeter(syllables);
        List<FootInfo> feet = parseIntoFeet(syllables, profile);
        String meterName = profile != null ? profile.meterName : "Unknown";
        double confidence = calculateConfidence(syllables, feet, profile);
        List<String> substitutions = detectSubstitutions(feet, profile);
        
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
    private List<FootInfo> parseIntoFeet(List<SyllableInfo> syllables, MeterProfile profile) {
        List<FootInfo> feet = new ArrayList<>();
        if (syllables.isEmpty()) return feet;
        
        int footSize = profile != null ? profile.footSize : 2;
        int start = profile != null ? profile.startOffset : 0;
        int end = syllables.size();
        
        if (profile != null && profile.feminineEnding && end > start) {
            end -= 1;
        }
        
        if (start > 0) {
            List<SyllableInfo> extra = new ArrayList<>(syllables.subList(0, start));
            feet.add(new FootInfo(FootType.UNKNOWN, extra, 0, false));
        }
        
        int i = start;
        while (i + footSize <= end) {
            List<SyllableInfo> group = syllables.subList(i, i + footSize);
            FootType type = classifyFoot(group);
            if (type == FootType.UNKNOWN) {
                type = closestFoot(group);
            }
            boolean isSub = profile != null && profile.dominantFoot != FootType.UNKNOWN &&
                    type != FootType.UNKNOWN && type != profile.dominantFoot;
            feet.add(new FootInfo(type, new ArrayList<>(group), i, isSub));
            i += footSize;
        }
        
        if (i < end) {
            List<SyllableInfo> extra = new ArrayList<>(syllables.subList(i, end));
            feet.add(new FootInfo(FootType.UNKNOWN, extra, i, false));
        }
        
        if (profile != null && profile.feminineEnding && end < syllables.size()) {
            List<SyllableInfo> extra = new ArrayList<>(syllables.subList(end, syllables.size()));
            feet.add(new FootInfo(FootType.UNKNOWN, extra, end, false));
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
     * Find the closest matching foot type when there is no exact match.
     */
    private FootType closestFoot(List<SyllableInfo> syllables) {
        String pattern = buildStressPattern(syllables);
        int len = pattern.length();
        int bestDistance = Integer.MAX_VALUE;
        FootType bestType = FootType.UNKNOWN;
        boolean tie = false;
        
        for (MeterPattern candidate : METER_PATTERNS) {
            if (candidate.pattern.length() != len) continue;
            int distance = hammingDistance(pattern, candidate.pattern);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestType = candidate.footType;
                tie = false;
            } else if (distance == bestDistance) {
                tie = true;
            }
        }
        
        return tie ? FootType.UNKNOWN : bestType;
    }
    
    private int hammingDistance(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int distance = Math.abs(a.length() - b.length());
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) distance++;
        }
        return distance;
    }
    
    private String buildStressPattern(List<SyllableInfo> syllables) {
        StringBuilder sb = new StringBuilder();
        for (SyllableInfo syl : syllables) {
            sb.append(syl.stressed ? '1' : '0');
        }
        return sb.toString();
    }
    
    private MeterProfile determineBestMeter(List<SyllableInfo> syllables) {
        String stress = buildStressPattern(syllables);
        if (stress.isEmpty()) {
            return new MeterProfile(FootType.UNKNOWN, 0, 0, 0, false, false, 1.0, "Unknown");
        }
        
        MeterProfile best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (MeterPattern candidate : METER_PATTERNS) {
            MeterProfile profile = scoreCandidate(stress, candidate);
            if (profile == null) continue;
            double score = 1.0 - profile.mismatchRatio;
            if (profile.anacrusis) score -= 0.05;
            if (profile.feminineEnding) score -= 0.03;
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        
        if (best == null) {
            return new MeterProfile(FootType.UNKNOWN, 0, 0, 0, false, false, 1.0, "Unknown");
        }
        return best;
    }
    
    private MeterProfile scoreCandidate(String stress, MeterPattern candidate) {
        int n = stress.length();
        int footSize = candidate.pattern.length();
        MeterProfile best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int offset = 0; offset <= 1; offset++) {
            boolean anacrusis = offset == 1;
            if (anacrusis && (n < 2 || stress.charAt(0) != '0')) {
                continue;
            }
            
            int start = offset;
            int available = n - start;
            boolean feminine = false;
            if (available > 1 && available % footSize == 1 && stress.charAt(n - 1) == '0') {
                feminine = true;
                available -= 1;
            }
            
            int footCount = available / footSize;
            if (footCount == 0) continue;
            
            int expectedLen = footCount * footSize;
            int mismatches = 0;
            for (int i = 0; i < expectedLen; i++) {
                char expected = candidate.pattern.charAt(i % footSize);
                char actual = stress.charAt(start + i);
                if (expected != actual) mismatches++;
            }
            
            double mismatchRatio = expectedLen > 0 ? (double) mismatches / expectedLen : 1.0;
            int extra = n - (start + expectedLen);
            double penalty = mismatchRatio + (extra * 0.2);
            if (anacrusis) penalty += 0.05;
            if (feminine) penalty -= 0.1;
            double score = 1.0 - penalty;
            
            String meterName = formatMeterName(candidate.footType, footCount);
            MeterProfile profile = new MeterProfile(candidate.footType, footSize, footCount, start,
                    anacrusis, feminine, mismatchRatio, meterName);
            
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        
        return best;
    }
    
    private String formatMeterName(FootType foot, int footCount) {
        if (foot == FootType.UNKNOWN || footCount <= 0) return "Unknown";
        
        String length = switch (footCount) {
            case 1 -> "monometer";
            case 2 -> "dimeter";
            case 3 -> "trimeter";
            case 4 -> "tetrameter";
            case 5 -> "pentameter";
            case 6 -> "hexameter";
            case 7 -> "heptameter";
            case 8 -> "octameter";
            default -> footCount + "-foot";
        };
        
        String type = switch (foot) {
            case IAMB -> "Iambic";
            case TROCHEE -> "Trochaic";
            case SPONDEE -> "Spondaic";
            case ANAPEST -> "Anapestic";
            case DACTYL -> "Dactylic";
            case AMPHIBRACH -> "Amphibrachic";
            case PYRRHIC -> "Pyrrhic";
            default -> "Mixed";
        };
        
        return type + " " + length;
    }
    
    /**
     * Calculate confidence in the meter analysis.
     */
    private double calculateConfidence(List<SyllableInfo> syllables, List<FootInfo> feet, MeterProfile profile) {
        if (feet.isEmpty() || profile == null || profile.meterName.equals("Unknown")) return 0.0;
        
        double sylConfidence = syllables.stream()
                .mapToDouble(s -> s.stressConfidence)
                .average().orElse(0.5);
        
        int counted = 0;
        int regular = 0;
        for (FootInfo foot : feet) {
            if (foot.type == FootType.UNKNOWN) continue;
            counted++;
            if (!foot.isSubstitution) regular++;
        }
        double footConsistency = counted > 0 ? (double) regular / counted : 0.0;
        double patternScore = Math.max(0.0, 1.0 - profile.mismatchRatio);
        
        double confidence = (sylConfidence * 0.35) + (footConsistency * 0.45) + (patternScore * 0.2);
        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * Detect metrical substitutions and extrametrical features.
     */
    private List<String> detectSubstitutions(List<FootInfo> feet, MeterProfile profile) {
        List<String> substitutions = new ArrayList<>();
        
        if (profile != null) {
            if (profile.anacrusis) {
                substitutions.add("Anacrusis (extra unstressed opening)");
            }
            if (profile.feminineEnding) {
                substitutions.add("Feminine ending (extra unstressed closing)");
            }
        }
        
        for (int i = 0; i < feet.size(); i++) {
            FootInfo foot = feet.get(i);
            if (foot.isSubstitution && foot.type != FootType.UNKNOWN) {
                substitutions.add(String.format("Foot %d: %s substitution (%s)",
                    i + 1, foot.type.name().toLowerCase(Locale.ROOT), foot.type.description));
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
