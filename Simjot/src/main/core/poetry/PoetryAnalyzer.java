/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.poetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import main.infrastructure.ffi.NativeAccess;
/**
 * <h1>Simjot Poetry Analysis Engine v1.0.0</h1>
 * 
 * <p>A poetry analysis engine providing professional-grade analysis
 * for prosody, phonetics, structure, rhetorical devices, lexical patterns, and sentiment.</p>
 * 
 * <h2>Analysis Categories</h2>
 * <ul>
 *   <li><b>Prosodic Analysis</b> - Meter identification, foot types, scansion, rhythm patterns</li>
 *   <li><b>Phonetic Analysis</b> - Rhyme schemes, alliteration, assonance, consonance</li>
 *   <li><b>Structural Analysis</b> - Form detection, stanza patterns, enjambment</li>
 *   <li><b>Rhetorical Analysis</b> - Anaphora, epistrophe, repetition patterns</li>
 *   <li><b>Lexical Analysis</b> - Vocabulary richness, word frequency, POS distribution</li>
 *   <li><b>Sentiment Analysis</b> - Emotional tone, imagery density, affect scoring</li>
 * </ul>
 * 
 * <h2>Supported Poetic Forms</h2>
 * <p>Sonnet (Shakespearean/Petrarchan), Haiku, Tanka, Limerick, Villanelle,
 * Ballad, Ode, Elegy, Blank Verse, Free Verse, Quatrains, Tercets, Couplets</p>
 * 
 * <h2>Academic Standards</h2>
 * <p>Based on prosodic conventions from:</p>
 * <ul>
 *   <li>The Princeton Encyclopedia of Poetry and Poetics</li>
 *   <li>Paul Fussell's "Poetic Meter and Poetic Form"</li>
 *   <li>Derek Attridge's "Poetic Rhythm: An Introduction"</li>
 * </ul>
 * 
 * @author S1mplector
 * @version 1.0.0
 */
public final class PoetryAnalyzer {
    
    private PoetryAnalyzer() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Metrical foot types in classical and English prosody.
     * Each foot has a name, scansion symbol, pattern notation, and example.
     */
    public enum FootType {
        /** Unstressed-Stressed (da-DUM) - "a-LONE", "be-FORE" */
        IAMB("Iamb", "˘ ´", "u S", "alone"),
        /** Stressed-Unstressed (DUM-da) - "GAR-den", "PO-et" */
        TROCHEE("Trochee", "´ ˘", "S u", "garden"),
        /** Unstressed-Unstressed-Stressed (da-da-DUM) - "in-ter-VENE" */
        ANAPEST("Anapest", "˘ ˘ ´", "u u S", "intervene"),
        /** Stressed-Unstressed-Unstressed (DUM-da-da) - "MER-ri-ly" */
        DACTYL("Dactyl", "´ ˘ ˘", "S u u", "merrily"),
        /** Stressed-Stressed (DUM-DUM) - "HEART-BREAK" */
        SPONDEE("Spondee", "´ ´", "S S", "heartbreak"),
        /** Unstressed-Unstressed (da-da) - "of the" */
        PYRRHIC("Pyrrhic", "˘ ˘", "u u", "of the"),
        /** Unstressed-Stressed-Unstressed (da-DUM-da) */
        AMPHIBRACH("Amphibrach", "˘ ´ ˘", "u S u", "ro-MAN-tic"),
        /** Stressed-Unstressed-Stressed (DUM-da-DUM) */
        CRETIC("Cretic", "´ ˘ ´", "S u S", "NIGHT-in-GALE");
        
        public final String name;
        public final String symbol;
        public final String pattern;
        public final String example;
        
        FootType(String name, String symbol, String pattern, String example) {
            this.name = name;
            this.symbol = symbol;
            this.pattern = pattern;
            this.example = example;
        }
    }
    
    /**
     * Meter types based on number of feet per line.
     */
    public enum MeterLength {
        MONOMETER(1, "Monometer", "One foot per line"),
        DIMETER(2, "Dimeter", "Two feet per line"),
        TRIMETER(3, "Trimeter", "Three feet per line"),
        TETRAMETER(4, "Tetrameter", "Four feet per line"),
        PENTAMETER(5, "Pentameter", "Five feet per line"),
        HEXAMETER(6, "Hexameter", "Six feet per line (Alexandrine)"),
        HEPTAMETER(7, "Heptameter", "Seven feet per line (Fourteener)"),
        OCTAMETER(8, "Octameter", "Eight feet per line");
        
        public final int feet;
        public final String name;
        public final String description;
        
        MeterLength(int feet, String name, String description) {
            this.feet = feet;
            this.name = name;
            this.description = description;
        }
        
        public static MeterLength fromFeet(int count) {
            for (MeterLength m : values()) if (m.feet == count) return m;
            return null;
        }
    }
    
    /**
     * Recognized poetic forms with their characteristics.
     */
    public enum PoeticForm {
        SHAKESPEAREAN_SONNET("Shakespearean Sonnet", 14, "ABABCDCDEFEFGG"),
        PETRARCHAN_SONNET("Petrarchan Sonnet", 14, "ABBAABBACDECDE"),
        SPENSERIAN_SONNET("Spenserian Sonnet", 14, "ABABBCBCCDCDEE"),
        HAIKU("Haiku", 3, null),
        TANKA("Tanka", 5, null),
        LIMERICK("Limerick", 5, "AABBA"),
        VILLANELLE("Villanelle", 19, null),
        SESTINA("Sestina", 39, null),
        BALLAD("Ballad", -1, "ABAB or ABCB"),
        GHAZAL("Ghazal", -1, "AA BA CA DA..."),
        BLANK_VERSE("Blank Verse", -1, null),
        FREE_VERSE("Free Verse", -1, null),
        QUATRAINS("Quatrains", -1, null),
        TERCETS("Tercets", -1, null),
        COUPLETS("Couplets", -1, null),
        ODE("Ode", -1, null),
        ELEGY("Elegy", -1, null);
        
        public final String displayName;
        public final int lineCount; // -1 means variable
        public final String typicalRhyme;
        
        PoeticForm(String displayName, int lineCount, String typicalRhyme) {
            this.displayName = displayName;
            this.lineCount = lineCount;
            this.typicalRhyme = typicalRhyme;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static class PoemAnalysis {
        public final String title, text;
        public final ProsodicAnalysis prosody;
        public final PhoneticAnalysis phonetics;
        public final StructuralAnalysis structure;
        public final LexicalAnalysis lexical;
        public final SentimentAnalysis sentiment;
        
        public PoemAnalysis(String title, String text, ProsodicAnalysis prosody,
                          PhoneticAnalysis phonetics, StructuralAnalysis structure,
                          LexicalAnalysis lexical, SentimentAnalysis sentiment) {
            this.title = title; this.text = text; this.prosody = prosody;
            this.phonetics = phonetics; this.structure = structure;
            this.lexical = lexical; this.sentiment = sentiment;
        }
    }
    
    public static class ProsodicAnalysis {
        public final String meterName;
        public final FootType dominantFoot;
        public final MeterLength meterLength;
        public final double regularity;
        public final double avgSyllables;
        public final List<LineScansion> scansions;
        public final Map<FootType, Integer> footDistribution;
        
        public ProsodicAnalysis(String meterName, FootType dominantFoot, MeterLength meterLength,
                               double regularity, double avgSyllables, List<LineScansion> scansions,
                               Map<FootType, Integer> footDistribution) {
            this.meterName = meterName; this.dominantFoot = dominantFoot;
            this.meterLength = meterLength; this.regularity = regularity;
            this.avgSyllables = avgSyllables; this.scansions = scansions;
            this.footDistribution = footDistribution;
        }
    }
    
    public static class LineScansion {
        public final String line;
        public final int lineNum;
        public final String stressPattern;
        public final String scansionMarks;
        public final List<FootType> feet;
        public final int syllables;
        
        public LineScansion(String line, int lineNum, String stressPattern,
                          String scansionMarks, List<FootType> feet, int syllables) {
            this.line = line; this.lineNum = lineNum; this.stressPattern = stressPattern;
            this.scansionMarks = scansionMarks; this.feet = feet; this.syllables = syllables;
        }
    }
    
    public static class PhoneticAnalysis {
        public final String rhymeScheme;
        public final Map<String, List<Integer>> rhymeGroups;
        public final Map<String, List<Integer>> nearRhymeGroups;
        public final List<SoundDevice> alliterations;
        public final List<SoundDevice> assonances;
        public final List<SoundDevice> consonances;
        public final List<SoundDevice> internalRhymes;
        public final double density;
        
        public PhoneticAnalysis(String rhymeScheme, Map<String, List<Integer>> rhymeGroups,
                               Map<String, List<Integer>> nearRhymeGroups,
                               List<SoundDevice> alliterations, List<SoundDevice> assonances,
                               List<SoundDevice> consonances, List<SoundDevice> internalRhymes,
                               double density) {
            this.rhymeScheme = rhymeScheme; this.rhymeGroups = rhymeGroups;
            this.nearRhymeGroups = nearRhymeGroups;
            this.alliterations = alliterations; this.assonances = assonances;
            this.consonances = consonances; this.internalRhymes = internalRhymes;
            this.density = density;
        }
    }
    
    public static class SoundDevice {
        public final int line;
        public final String sound;
        public final List<String> words;
        
        public SoundDevice(int line, String sound, List<String> words) {
            this.line = line; this.sound = sound; this.words = words;
        }
    }
    
    public static class StructuralAnalysis {
        public final String form;
        public final int lineCount, stanzaCount;
        public final List<Integer> stanzaLengths;
        public final List<Integer> syllableCounts;
        public final boolean hasRefrain;
        public final String refrain;
        public final int enjambment, endStopped;
        
        public StructuralAnalysis(String form, int lineCount, int stanzaCount,
                                 List<Integer> stanzaLengths, List<Integer> syllableCounts,
                                 boolean hasRefrain, String refrain, int enjambment, int endStopped) {
            this.form = form; this.lineCount = lineCount; this.stanzaCount = stanzaCount;
            this.stanzaLengths = stanzaLengths; this.syllableCounts = syllableCounts;
            this.hasRefrain = hasRefrain; this.refrain = refrain;
            this.enjambment = enjambment; this.endStopped = endStopped;
        }
    }
    
    public static class LexicalAnalysis {
        public final int totalWords, uniqueWords;
        public final double typeTokenRatio, avgWordLength;
        public final Map<String, Integer> wordFrequency;
        public final Map<String, Integer> posDistribution;
        public final List<String> topWords;
        public final int polysyllabic;
        public final double readability;
        public final double lexicalDensity;
        public final double avgSyllablesPerWord;
        public final double polysyllabicRatio;
        public final double mattr;
        public final double mtld;
        public final double yulesK;
        public final double simpsonsD;
        public final double gunningFog;
        public final double smogIndex;
        public final double colemanLiauIndex;
        public final double automatedReadabilityIndex;
        public final double lexicalSophistication;
        
        public LexicalAnalysis(int totalWords, int uniqueWords, double typeTokenRatio,
                              double avgWordLength, Map<String, Integer> wordFrequency,
                              Map<String, Integer> posDistribution, List<String> topWords,
                              int polysyllabic, double readability, double lexicalDensity,
                              double avgSyllablesPerWord, double polysyllabicRatio,
                              double mattr, double mtld, double yulesK, double simpsonsD,
                              double gunningFog, double smogIndex, double colemanLiauIndex,
                              double automatedReadabilityIndex, double lexicalSophistication) {
            this.totalWords = totalWords; this.uniqueWords = uniqueWords;
            this.typeTokenRatio = typeTokenRatio; this.avgWordLength = avgWordLength;
            this.wordFrequency = wordFrequency; this.posDistribution = posDistribution;
            this.topWords = topWords; this.polysyllabic = polysyllabic; this.readability = readability;
            this.lexicalDensity = lexicalDensity;
            this.avgSyllablesPerWord = avgSyllablesPerWord;
            this.polysyllabicRatio = polysyllabicRatio;
            this.mattr = mattr;
            this.mtld = mtld;
            this.yulesK = yulesK;
            this.simpsonsD = simpsonsD;
            this.gunningFog = gunningFog;
            this.smogIndex = smogIndex;
            this.colemanLiauIndex = colemanLiauIndex;
            this.automatedReadabilityIndex = automatedReadabilityIndex;
            this.lexicalSophistication = lexicalSophistication;
        }
    }
    
    public static class SentimentAnalysis {
        public final String tone;
        public final double positive, negative, neutral, intensity;
        public final List<String> emotions;
        public final List<String> imagery;
        public final Map<String, Double> emotionScores;
        
        public SentimentAnalysis(String tone, double positive, double negative,
                                double neutral, double intensity, List<String> emotions,
                                List<String> imagery, Map<String, Double> emotionScores) {
            this.tone = tone; this.positive = positive; this.negative = negative;
            this.neutral = neutral; this.intensity = intensity;
            this.emotions = emotions; this.imagery = imagery;
            this.emotionScores = emotionScores;
        }
    }
    
    /**
     * Rhetorical device analysis results.
     */
    public static class RhetoricalAnalysis {
        public final List<RepetitionDevice> anaphoras;
        public final List<RepetitionDevice> epistrophes;
        public final List<RepetitionDevice> anadiploses;
        public final int repetitionScore;
        public final List<Integer> caesuraLines;
        public final boolean hasParallelism;
        
        public RhetoricalAnalysis(List<RepetitionDevice> anaphoras, List<RepetitionDevice> epistrophes,
                                 List<RepetitionDevice> anadiploses, int repetitionScore,
                                 List<Integer> caesuraLines, boolean hasParallelism) {
            this.anaphoras = anaphoras; this.epistrophes = epistrophes;
            this.anadiploses = anadiploses; this.repetitionScore = repetitionScore;
            this.caesuraLines = caesuraLines; this.hasParallelism = hasParallelism;
        }
    }
    
    /**
     * A repetition-based rhetorical device instance.
     */
    public static class RepetitionDevice {
        public final String repeatedPhrase;
        public final List<Integer> lineNumbers;
        public final String deviceType;
        
        public RepetitionDevice(String repeatedPhrase, List<Integer> lineNumbers, String deviceType) {
            this.repeatedPhrase = repeatedPhrase;
            this.lineNumbers = lineNumbers;
            this.deviceType = deviceType;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static PoemAnalysis analyze(String title, String text) {
        if (text == null || text.isBlank()) return createEmpty(title, text);
        return new PoemAnalysis(title, text, analyzeProsody(text), analyzePhonetics(text),
            analyzeStructure(text), analyzeLexical(text), analyzeSentiment(text));
    }
    
    public static ProsodicAnalysis analyzeProsody(String text) {
        List<String> lines = getContentLines(text);
        if (lines.isEmpty()) return emptyProsody();
        
        List<LineScansion> scansions = new ArrayList<>();
        Map<FootType, Integer> footCounts = new EnumMap<>(FootType.class);
        int totalSyllables = 0;
        
        for (int i = 0; i < lines.size(); i++) {
            LineScansion s = scanLine(lines.get(i), i + 1);
            scansions.add(s);
            totalSyllables += s.syllables;
            for (FootType f : s.feet) footCounts.merge(f, 1, Integer::sum);
        }
        
        FootType dominant = footCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(FootType.IAMB);
        
        double avgFeet = scansions.stream().mapToInt(s -> s.feet.size()).average().orElse(0);
        MeterLength length = MeterLength.fromFeet((int) Math.round(avgFeet));
        String name = getMeterName(dominant, length);
        double regularity = calcRegularity(scansions, dominant);
        if (NativeAccess.hasHaskellPoetrySupport()) {
            FootType hsDominant = mapHaskellFootType(NativeAccess.hsAnalyzeMeter(text));
            if (hsDominant != null) {
                dominant = hsDominant;
                name = getMeterName(dominant, length);
            }
            String hsName = NativeAccess.hsGetMeterName(text);
            if (hsName != null && !hsName.isBlank()) {
                name = hsName;
            }
            int hsRegularity = NativeAccess.hsGetMeterRegularity(text);
            if (hsRegularity > 0) {
                regularity = Math.min(1.0, hsRegularity / 100.0);
            }
        }
        double avgSyl = lines.isEmpty() ? 0 : (double) totalSyllables / lines.size();
        
        return new ProsodicAnalysis(name, dominant, length, regularity, avgSyl, scansions, footCounts);
    }
    
    public static LineScansion scanLine(String line, int num) {
        List<String> words = PoetryUtils.wordsInLine(line);
        if (words.isEmpty()) return new LineScansion(line, num, "", "", Collections.emptyList(), 0);
        
        StringBuilder stress = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        int syllables = 0;
        
        for (String w : words) {
            int[] pat = PoetryDictionary.getStressPattern(w);
            for (int s : pat) {
                stress.append(s);
                marks.append(s == 1 ? "´" : "˘");
                syllables++;
            }
            if (!w.equals(words.get(words.size() - 1))) marks.append(" ");
        }
        
        return new LineScansion(line, num, stress.toString(), marks.toString(),
            identifyFeet(stress.toString()), syllables);
    }
    
    private static class FootProfile {
        final FootType dominantFoot;
        final int footSize;
        final int startOffset;
        final boolean feminineEnding;
        
        FootProfile(FootType dominantFoot, int footSize, int startOffset, boolean feminineEnding) {
            this.dominantFoot = dominantFoot;
            this.footSize = footSize;
            this.startOffset = startOffset;
            this.feminineEnding = feminineEnding;
        }
    }
    
    private static List<FootType> identifyFeet(String pattern) {
        List<FootType> feet = new ArrayList<>();
        int i = 0;
        if (pattern == null || pattern.isEmpty()) return feet;
        
        FootProfile profile = determineFootProfile(pattern);
        int footSize = profile.footSize;
        i = profile.startOffset;
        int end = pattern.length();
        if (profile.feminineEnding && end > i) end -= 1;
        
        while (i + footSize <= end) {
            String chunk = pattern.substring(i, i + footSize);
            FootType type = classifyFootPattern(chunk);
            if (type == null) type = closestFootPattern(chunk);
            if (type != null) feet.add(type);
            i += footSize;
        }
        return feet;
    }
    
    private static FootProfile determineFootProfile(String pattern) {
        FootProfile best = new FootProfile(FootType.IAMB, 2, 0, false);
        double bestScore = Double.NEGATIVE_INFINITY;
        
        Map<FootType, String> candidates = Map.of(
            FootType.IAMB, "01",
            FootType.TROCHEE, "10",
            FootType.SPONDEE, "11",
            FootType.PYRRHIC, "00",
            FootType.ANAPEST, "001",
            FootType.DACTYL, "100",
            FootType.AMPHIBRACH, "010",
            FootType.CRETIC, "101"
        );
        
        for (var entry : candidates.entrySet()) {
            String footPattern = entry.getValue();
            int footSize = footPattern.length();
            FootProfile scored = scoreFootProfile(pattern, entry.getKey(), footPattern, footSize);
            double score = 1.0 - calculateMismatchRatio(pattern, footPattern, scored.startOffset, scored.feminineEnding);
            if (score > bestScore) {
                bestScore = score;
                best = scored;
            }
        }
        
        return best;
    }
    
    private static FootProfile scoreFootProfile(String pattern, FootType footType, String footPattern, int footSize) {
        int bestOffset = 0;
        boolean bestFeminine = false;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int offset = 0; offset <= 1; offset++) {
            if (offset == 1 && (pattern.length() < 2 || pattern.charAt(0) != '0')) continue;
            boolean feminine = false;
            int available = pattern.length() - offset;
            if (available > 1 && available % footSize == 1 && pattern.charAt(pattern.length() - 1) == '0') {
                feminine = true;
            }
            double mismatch = calculateMismatchRatio(pattern, footPattern, offset, feminine);
            double score = 1.0 - mismatch - (offset == 1 ? 0.05 : 0.0) - (feminine ? 0.03 : 0.0);
            if (score > bestScore) {
                bestScore = score;
                bestOffset = offset;
                bestFeminine = feminine;
            }
        }
        
        return new FootProfile(footType, footSize, bestOffset, bestFeminine);
    }
    
    private static double calculateMismatchRatio(String pattern, String footPattern, int offset, boolean feminine) {
        int available = pattern.length() - offset;
        if (feminine && available > 1) available -= 1;
        int footCount = available / footPattern.length();
        if (footCount <= 0) return 1.0;
        int expectedLen = footCount * footPattern.length();
        int mismatches = 0;
        for (int i = 0; i < expectedLen; i++) {
            char expected = footPattern.charAt(i % footPattern.length());
            char actual = pattern.charAt(offset + i);
            if (expected != actual) mismatches++;
        }
        return expectedLen > 0 ? (double) mismatches / expectedLen : 1.0;
    }
    
    private static FootType classifyFootPattern(String pattern) {
        return switch (pattern) {
            case "01" -> FootType.IAMB;
            case "10" -> FootType.TROCHEE;
            case "11" -> FootType.SPONDEE;
            case "00" -> FootType.PYRRHIC;
            case "001" -> FootType.ANAPEST;
            case "100" -> FootType.DACTYL;
            case "010" -> FootType.AMPHIBRACH;
            case "101" -> FootType.CRETIC;
            default -> null;
        };
    }
    
    private static FootType closestFootPattern(String pattern) {
        Map<FootType, String> candidates = Map.of(
            FootType.IAMB, "01",
            FootType.TROCHEE, "10",
            FootType.SPONDEE, "11",
            FootType.PYRRHIC, "00",
            FootType.ANAPEST, "001",
            FootType.DACTYL, "100",
            FootType.AMPHIBRACH, "010",
            FootType.CRETIC, "101"
        );
        
        int bestDistance = Integer.MAX_VALUE;
        FootType best = null;
        boolean tie = false;
        
        for (var entry : candidates.entrySet()) {
            if (entry.getValue().length() != pattern.length()) continue;
            int distance = 0;
            for (int i = 0; i < pattern.length(); i++) {
                if (pattern.charAt(i) != entry.getValue().charAt(i)) distance++;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
                tie = false;
            } else if (distance == bestDistance) {
                tie = true;
            }
        }
        
        return tie ? null : best;
    }
    
    public static PhoneticAnalysis analyzePhonetics(String text) {
        List<String> lines = getContentLines(text);
        String scheme = detectRhymeScheme(lines);
        Map<String, List<Integer>> groups = groupRhymes(lines);
        Map<String, List<Integer>> nearGroups = groupNearRhymes(lines);
        List<SoundDevice> allit = detectAlliteration(lines);
        List<SoundDevice> asson = detectAssonance(lines);
        List<SoundDevice> conson = detectConsonance(lines);
        List<SoundDevice> internal = detectInternalRhymes(lines);
        double density = lines.isEmpty() ? 0 : (double)(allit.size() + asson.size() + conson.size() + internal.size()) / lines.size();
        return new PhoneticAnalysis(scheme, groups, nearGroups, allit, asson, conson, internal, density);
    }
    
    public static String detectRhymeScheme(List<String> lines) {
        if (lines.isEmpty()) return "";
        String text = String.join("\n", lines);
        if (NativeAccess.hasHaskellPoetrySupport()) {
            String hsScheme = NativeAccess.hsAnalyzeRhymeScheme(text);
            if (hsScheme != null && !hsScheme.isBlank()) return hsScheme;
        }
        String nativeScheme = NativeAccess.rhymeDetectScheme(text);
        if (nativeScheme != null && !nativeScheme.isBlank()) return nativeScheme;
        Map<String, Character> seen = new HashMap<>();
        char cur = 'A';
        StringBuilder sb = new StringBuilder();
        
        for (String line : lines) {
            String end = PoetryUtils.endWord(line);
            if (end == null) { sb.append("X"); continue; }
            String key = PoetryUtils.rhymeKey(end);
            boolean found = false;
            for (var e : seen.entrySet()) {
                if (e.getKey().equals(key)) { sb.append(e.getValue()); found = true; break; }
            }
            if (!found) { seen.put(key, cur); sb.append(cur); if (cur < 'Z') cur++; }
        }
        return sb.toString();
    }
    
    public static StructuralAnalysis analyzeStructure(String text) {
        List<String> all = PoetryUtils.splitLines(text);
        List<String> content = all.stream().filter(l -> !l.isBlank()).collect(Collectors.toList());
        
        List<Integer> stanzas = new ArrayList<>();
        int cur = 0;
        for (String l : all) {
            if (l.isBlank()) { if (cur > 0) { stanzas.add(cur); cur = 0; } }
            else cur++;
        }
        if (cur > 0) stanzas.add(cur);
        
        List<Integer> sylCounts = new ArrayList<>();
        int nativeLineCount = NativeAccess.poetryAnalyzeMeter(text);
        if (nativeLineCount > 0 && !all.isEmpty()) {
            int nativeIndex = 0;
            for (String line : all) {
                if (line.isEmpty()) continue; // native analyzer skips empty lines
                int syllables = NativeAccess.poetryGetLineSyllables(nativeIndex++);
                if (!line.isBlank()) {
                    sylCounts.add(syllables);
                }
            }
            if (nativeIndex != nativeLineCount || sylCounts.size() != content.size()) {
                sylCounts.clear();
            }
        }
        if (sylCounts.isEmpty()) {
            sylCounts = content.stream().map(PoetryUtils::countSyllablesInLine).collect(Collectors.toList());
        }
        String refrain = detectRefrain(content);
        
        int enj = 0, end = 0;
        Pattern endPunc = Pattern.compile("[.!?;,:]\\s*$");
        for (String l : content) { if (endPunc.matcher(l).find()) end++; else enj++; }
        
        String form = detectForm(content, stanzas, sylCounts, detectRhymeScheme(content));
        return new StructuralAnalysis(form, content.size(), stanzas.size(), stanzas, sylCounts,
            refrain != null, refrain, enj, end);
    }
    
    public static LexicalAnalysis analyzeLexical(String text) {
        VocabularyAnalyzer analyzer = new VocabularyAnalyzer();
        VocabularyAnalyzer.VocabularyAnalysis analysis = analyzer.analyze(text);
        if (analysis.totalWords == 0) return emptyLexical();
        
        Map<String, Integer> freq = analysis.wordFrequencies;
        Map<String, Integer> pos = analysis.posDistribution;
        List<String> top = freq.entrySet().stream()
            .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        return new LexicalAnalysis(
            analysis.totalWords,
            analysis.uniqueWords,
            analysis.typeTokenRatio,
            analysis.avgWordLength,
            freq,
            pos,
            top,
            analysis.polysyllabicWords,
            analysis.fleschReadingEase,
            analysis.lexicalDensity,
            analysis.avgSyllablesPerWord,
            analysis.polysyllabicRatio,
            analysis.mattr,
            analysis.mtld,
            analysis.yulesK,
            analysis.simpsonsD,
            analysis.gunningFog,
            analysis.smogIndex,
            analysis.colemanLiauIndex,
            analysis.automatedReadabilityIndex,
            analysis.lexicalSophistication
        );
    }
    
    public static SentimentAnalysis analyzeSentiment(String text) {
        List<String> words = NativeAccess.textExtractWords(text);
        if (words == null || words.isEmpty()) {
            words = new ArrayList<>();
            for (String l : PoetryUtils.splitLines(text)) words.addAll(PoetryUtils.wordsInLine(l));
        }
        
        int posCount = 0, negCount = 0;
        List<String> imagery = new ArrayList<>();
        Map<String, Double> emotionScores = new HashMap<>();
        
        // Initialize emotion categories
        for (String emotion : EMOTIONS.keySet()) {
            emotionScores.put(emotion, 0.0);
        }
        
        for (String w : words) {
            String lower = w.toLowerCase(Locale.ROOT);
            if (POSITIVE.contains(lower)) posCount++;
            if (NEGATIVE.contains(lower)) negCount++;
            
            // Check imagery categories
            for (var e : IMAGERY.entrySet()) {
                if (e.getValue().contains(lower) && !imagery.contains(e.getKey())) {
                    imagery.add(e.getKey());
                }
            }
            
            // Check emotion categories
            for (var e : EMOTIONS.entrySet()) {
                if (e.getValue().contains(lower)) {
                    emotionScores.merge(e.getKey(), 1.0, Double::sum);
                }
            }
        }
        
        int total = words.size();
        double pos = total > 0 ? (double) posCount / total : 0;
        double neg = total > 0 ? (double) negCount / total : 0;
        double neut = Math.max(0, 1.0 - pos - neg);
        
        // Normalize emotion scores
        double emotionTotal = emotionScores.values().stream().mapToDouble(d -> d).sum();
        if (emotionTotal > 0) {
            emotionScores.replaceAll((k, v) -> v / emotionTotal);
        }
        
        // Determine tone based on multiple factors
        String tone = determineTone(pos, neg, emotionScores);
        double intensity = Math.min(1.0, (pos + neg) * 2.5);
        
        // Get dominant emotions
        List<String> emotions = emotionScores.entrySet().stream()
            .filter(e -> e.getValue() > 0.1)
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(4)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (emotions.isEmpty()) {
            if (pos > neg) emotions.add("Serenity");
            else if (neg > pos) emotions.add("Melancholy");
            else emotions.add("Contemplation");
        }
        
        return new SentimentAnalysis(tone, pos, neg, neut, intensity, emotions, imagery, emotionScores);
    }
    
    private static String determineTone(double pos, double neg, Map<String, Double> emotions) {
        if (pos > neg * 2) return "Joyful/Optimistic";
        if (neg > pos * 2) return "Melancholic/Somber";
        if (pos > 0.1 && neg > 0.1) return "Complex/Ambivalent";
        
        // Check specific emotion dominance
        String dominant = emotions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        if (dominant != null) {
            return switch (dominant) {
                case "Love" -> "Romantic/Tender";
                case "Grief" -> "Elegiac/Mournful";
                case "Wonder" -> "Wondrous/Sublime";
                case "Fear" -> "Gothic/Ominous";
                case "Anger" -> "Fierce/Passionate";
                case "Nostalgia" -> "Nostalgic/Wistful";
                case "Hope" -> "Hopeful/Aspirational";
                case "Despair" -> "Despairing/Bleak";
                default -> "Contemplative/Reflective";
            };
        }
        return "Contemplative/Reflective";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static List<String> getContentLines(String text) {
        return PoetryUtils.splitLines(text).stream().filter(l -> !l.isBlank()).collect(Collectors.toList());
    }
    
    private static String getMeterName(FootType foot, MeterLength len) {
        if (foot == null) return "Free Verse";
        String f = foot.name.substring(0, 1).toUpperCase() + foot.name.substring(1).toLowerCase();
        return len != null ? f + " " + len.name().substring(0, 1) + len.name().substring(1).toLowerCase() : f;
    }

    private static FootType mapHaskellFootType(int footCode) {
        return switch (footCode) {
            case 0 -> FootType.IAMB;
            case 1 -> FootType.TROCHEE;
            case 2 -> FootType.SPONDEE;
            case 3 -> FootType.PYRRHIC;
            case 4 -> FootType.ANAPEST;
            case 5 -> FootType.DACTYL;
            case 6 -> FootType.AMPHIBRACH;
            default -> null;
        };
    }
    
    private static double calcRegularity(List<LineScansion> scans, FootType exp) {
        int total = 0, match = 0;
        for (var s : scans) for (var f : s.feet) { total++; if (f == exp) match++; }
        return total > 0 ? (double) match / total : 0;
    }
    
    private static Map<String, List<Integer>> groupRhymes(List<String> lines) {
        Map<String, List<Integer>> g = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String end = PoetryUtils.endWord(lines.get(i));
            if (end != null) {
                String key = PoetryUtils.rhymeKey(end);
                if (key != null) g.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 1);
            }
        }
        g.entrySet().removeIf(e -> e.getValue().size() < 2);
        return g;
    }
    
    private static Map<String, List<Integer>> groupNearRhymes(List<String> lines) {
        Map<String, List<Integer>> g = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String end = PoetryUtils.endWord(lines.get(i));
            if (end != null) {
                String key = PoetryUtils.nearRhymeKey(end);
                if (key != null) g.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 1);
            }
        }
        g.entrySet().removeIf(e -> e.getValue().size() < 2);
        return g;
    }
    
    private static List<SoundDevice> detectAlliteration(List<String> lines) {
        List<SoundDevice> r = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<Character, List<String>> m = new HashMap<>();
            for (String w : PoetryUtils.wordsInLine(lines.get(i))) {
                if (!w.isEmpty()) {
                    char c = Character.toLowerCase(w.charAt(0));
                    if (Character.isLetter(c) && !"aeiou".contains(String.valueOf(c)))
                        m.computeIfAbsent(c, k -> new ArrayList<>()).add(w);
                }
            }
            for (var e : m.entrySet()) if (e.getValue().size() >= 2)
                r.add(new SoundDevice(i + 1, String.valueOf(e.getKey()), e.getValue()));
        }
        return r;
    }
    
    private static List<SoundDevice> detectAssonance(List<String> lines) {
        List<SoundDevice> r = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, List<String>> m = new HashMap<>();
            for (String w : PoetryUtils.wordsInLine(lines.get(i))) {
                String v = w.toLowerCase().replaceAll("[^aeiou]", "");
                if (v.length() >= 1) m.computeIfAbsent(v, k -> new ArrayList<>()).add(w);
            }
            for (var e : m.entrySet()) if (e.getValue().size() >= 2)
                r.add(new SoundDevice(i + 1, e.getKey(), e.getValue()));
        }
        return r;
    }
    
    private static List<SoundDevice> detectConsonance(List<String> lines) {
        List<SoundDevice> r = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, List<String>> m = new HashMap<>();
            for (String w : PoetryUtils.wordsInLine(lines.get(i))) {
                String end = getEndConsonants(w.toLowerCase());
                if (!end.isEmpty()) m.computeIfAbsent(end, k -> new ArrayList<>()).add(w);
            }
            for (var e : m.entrySet()) if (e.getValue().size() >= 2)
                r.add(new SoundDevice(i + 1, e.getKey(), e.getValue()));
        }
        return r;
    }
    
    private static List<SoundDevice> detectInternalRhymes(List<String> lines) {
        List<SoundDevice> r = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, List<String>> m = new HashMap<>();
            for (String w : PoetryUtils.wordsInLine(lines.get(i))) {
                String lower = w.toLowerCase(Locale.ROOT);
                if (PoetryDictionary.isFunctionWord(lower)) continue;
                String key = PoetryUtils.rhymeKey(w);
                if (key != null && key.length() >= 2) {
                    m.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
                }
            }
            for (var e : m.entrySet()) if (e.getValue().size() >= 2)
                r.add(new SoundDevice(i + 1, e.getKey(), e.getValue()));
        }
        return r;
    }
    
    private static String getEndConsonants(String w) {
        StringBuilder sb = new StringBuilder();
        for (int i = w.length() - 1; i >= 0; i--) {
            char c = w.charAt(i);
            if (!"aeiou".contains(String.valueOf(c)) && Character.isLetter(c)) sb.insert(0, c);
            else break;
        }
        return sb.toString();
    }
    
    private static String detectRefrain(List<String> lines) {
        Map<String, Integer> freq = new HashMap<>();
        for (String l : lines) freq.merge(l.trim().toLowerCase(), 1, Integer::sum);
        return freq.entrySet().stream().filter(e -> e.getValue() >= 2)
            .max(Map.Entry.comparingByValue())
            .map(e -> lines.stream().filter(l -> l.trim().toLowerCase().equals(e.getKey())).findFirst().orElse(null))
            .orElse(null);
    }
    
    private static String detectForm(List<String> lines, List<Integer> stanzas, List<Integer> syl, String rhyme) {
        int n = lines.size();
        
        // Sonnet variants (14 lines)
        if (n == 14) {
            if (rhyme.matches("ABAB.?CDCD.?EFEF.?GG")) return "Shakespearean Sonnet";
            if (rhyme.matches("ABBA.?ABBA.?CDE.?CDE")) return "Petrarchan Sonnet";
            if (rhyme.matches("ABAB.?BCBC.?CDCD.?EE")) return "Spenserian Sonnet";
            return "Sonnet";
        }
        
        // Japanese forms
        if (n == 3 && syl.size() == 3 && syl.get(0) == 5 && syl.get(1) == 7 && syl.get(2) == 5) {
            return "Haiku";
        }
        if (n == 5 && syl.size() == 5 && syl.get(0) == 5 && syl.get(1) == 7 && 
            syl.get(2) == 5 && syl.get(3) == 7 && syl.get(4) == 7) {
            return "Tanka";
        }
        
        // Limerick (5 lines AABBA)
        if (n == 5 && rhyme.equals("AABBA")) return "Limerick";
        
        // Villanelle (19 lines: 5 tercets + 1 quatrain)
        if (n == 19 && stanzas.size() == 6) {
            boolean valid = true;
            for (int i = 0; i < 5; i++) if (stanzas.get(i) != 3) valid = false;
            if (stanzas.get(5) != 4) valid = false;
            if (valid) return "Villanelle";
        }
        
        // Ballad (quatrains with ABAB or ABCB rhyme)
        if (stanzas.stream().allMatch(l -> l == 4)) {
            if (rhyme.matches("(ABAB|ABCB)+")) return "Ballad";
            return "Quatrains";
        }
        
        // Terza Rima (interlocking tercets ABA BCB CDC...)
        if (stanzas.stream().allMatch(l -> l == 3)) {
            if (rhyme.matches("ABA.?BCB.?CDC.*")) return "Terza Rima";
            return "Tercets";
        }
        
        // Heroic Couplets (rhyming pairs, often iambic pentameter)
        if (stanzas.stream().allMatch(l -> l == 2)) {
            double avgSyl = syl.stream().mapToInt(i -> i).average().orElse(0);
            if (avgSyl >= 9 && avgSyl <= 11) return "Heroic Couplets";
            return "Couplets";
        }
        
        // Blank Verse (unrhymed iambic pentameter)
        double avgSyl = syl.stream().mapToInt(i -> i).average().orElse(0);
        long uniqueEndings = rhyme.chars().distinct().count();
        if (avgSyl >= 9 && avgSyl <= 11 && uniqueEndings > n * 0.7) {
            return "Blank Verse";
        }
        
        // Ode (irregular stanzas, elevated subject)
        if (stanzas.size() >= 3 && !stanzas.stream().allMatch(l -> l == stanzas.get(0))) {
            double variance = calcVariance(syl);
            if (variance < 5) return "Ode";
        }
        
        // Free Verse (default)
        return "Free Verse";
    }
    
    private static double calcVariance(List<Integer> vals) {
        if (vals.isEmpty()) return 0;
        double mean = vals.stream().mapToInt(i -> i).average().orElse(0);
        return vals.stream().mapToDouble(i -> Math.pow(i - mean, 2)).average().orElse(0);
    }
    
    private static PoemAnalysis createEmpty(String t, String txt) {
        return new PoemAnalysis(t, txt, emptyProsody(), emptyPhonetics(), emptyStructure(), emptyLexical(), emptySentiment());
    }
    
    private static ProsodicAnalysis emptyProsody() {
        return new ProsodicAnalysis("N/A", null, null, 0, 0, Collections.emptyList(), Collections.emptyMap());
    }
    private static PhoneticAnalysis emptyPhonetics() {
        return new PhoneticAnalysis("", Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0);
    }
    private static StructuralAnalysis emptyStructure() {
        return new StructuralAnalysis("Empty", 0, 0, Collections.emptyList(), Collections.emptyList(), false, null, 0, 0);
    }
    private static LexicalAnalysis emptyLexical() {
        return new LexicalAnalysis(0, 0, 0, 0, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    private static SentimentAnalysis emptySentiment() {
        return new SentimentAnalysis("N/A", 0, 0, 0, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LEXICONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final Set<String> POSITIVE = Set.of(
        "love", "joy", "happy", "beautiful", "bright", "light", "hope", "peace", "gentle",
        "warm", "sweet", "dream", "heaven", "angel", "bliss", "delight", "wonder", "grace",
        "golden", "glory", "bloom", "shine", "smile", "tender", "pure", "serene", "radiant",
        "cherish", "treasure", "embrace", "adore", "enchant", "bless", "harmony", "paradise",
        "eternal", "divine", "sacred", "kind", "faith", "trust", "comfort", "heal"
    );
    
    private static final Set<String> NEGATIVE = Set.of(
        "death", "dark", "pain", "sorrow", "grief", "cold", "alone", "lost", "fear", "weep",
        "tear", "break", "fall", "fade", "shadow", "grave", "doom", "despair", "bitter", "bleak",
        "mourn", "wither", "decay", "ruin", "waste", "agony", "torment", "anguish", "woe",
        "dread", "terror", "horror", "gloom", "misery", "suffer", "wound", "hurt", "abandon"
    );
    
    /** Emotion categories for nuanced sentiment analysis */
    private static final Map<String, Set<String>> EMOTIONS = Map.ofEntries(
        Map.entry("Love", Set.of("love", "heart", "beloved", "cherish", "adore", "passion", "desire", 
            "embrace", "tender", "devotion", "affection", "romance", "kiss", "yearn", "longing")),
        Map.entry("Joy", Set.of("joy", "happy", "delight", "bliss", "mirth", "glee", "elation",
            "jubilant", "ecstasy", "rapture", "exult", "rejoice", "celebrate", "cheer", "merry")),
        Map.entry("Grief", Set.of("grief", "mourn", "weep", "sorrow", "lament", "bereave", "loss",
            "tears", "sob", "cry", "wail", "funeral", "death", "departed", "gone")),
        Map.entry("Fear", Set.of("fear", "dread", "terror", "horror", "fright", "panic", "trembling",
            "nightmare", "haunt", "ghost", "specter", "shadow", "lurk", "creep", "shudder")),
        Map.entry("Anger", Set.of("anger", "rage", "fury", "wrath", "hate", "scorn", "bitter",
            "revenge", "vengeance", "fierce", "burn", "fire", "storm", "thunder", "violent")),
        Map.entry("Wonder", Set.of("wonder", "awe", "marvel", "miracle", "magic", "mystery", "infinite",
            "sublime", "transcend", "ethereal", "celestial", "divine", "heavenly", "sacred", "eternal")),
        Map.entry("Nostalgia", Set.of("remember", "memory", "past", "yesterday", "once", "ago", "youth",
            "childhood", "return", "again", "lost", "forgotten", "echo", "fade", "ghost")),
        Map.entry("Hope", Set.of("hope", "dream", "wish", "believe", "faith", "promise", "tomorrow",
            "future", "dawn", "sunrise", "spring", "bloom", "grow", "rise", "new")),
        Map.entry("Despair", Set.of("despair", "hopeless", "void", "abyss", "nothing", "empty", "hollow",
            "desolate", "forsaken", "abandon", "doom", "end", "fall", "sink", "drown"))
    );
    
    /** Imagery categories for sensory analysis */
    private static final Map<String, Set<String>> IMAGERY = Map.ofEntries(
        Map.entry("Visual", Set.of("light", "dark", "shadow", "color", "red", "blue", "green", "gold", 
            "silver", "shine", "glow", "gleam", "bright", "dim", "pale", "vivid", "crimson", "azure",
            "emerald", "scarlet", "white", "black", "grey", "purple", "flame", "spark", "ray", "beam")),
        Map.entry("Auditory", Set.of("sound", "silence", "whisper", "cry", "song", "music", "thunder", 
            "bell", "echo", "voice", "sing", "chime", "ring", "hum", "murmur", "roar", "crash",
            "rustle", "sigh", "scream", "wail", "chant", "melody", "harmony", "discord")),
        Map.entry("Tactile", Set.of("cold", "warm", "soft", "hard", "rough", "smooth", "touch", "feel",
            "velvet", "silk", "stone", "ice", "fire", "heat", "chill", "freeze", "burn", "gentle",
            "sharp", "prick", "caress", "stroke", "embrace", "grasp", "grip")),
        Map.entry("Olfactory", Set.of("smell", "scent", "fragrance", "perfume", "rose", "flower",
            "incense", "smoke", "earth", "rain", "sea", "pine", "jasmine", "lavender", "musk")),
        Map.entry("Gustatory", Set.of("sweet", "bitter", "sour", "salt", "taste", "honey", "wine",
            "nectar", "fruit", "poison", "blood", "tears", "kiss", "lip", "tongue")),
        Map.entry("Nature", Set.of("sun", "moon", "star", "sky", "sea", "river", "mountain", "tree", 
            "wind", "rain", "snow", "storm", "cloud", "earth", "flower", "leaf", "bird", "forest",
            "ocean", "wave", "shore", "field", "meadow", "garden", "spring", "winter", "autumn", "summer")),
        Map.entry("Temporal", Set.of("dawn", "dusk", "night", "day", "morning", "evening", "midnight",
            "noon", "hour", "moment", "eternal", "forever", "never", "always", "time", "age", "century"))
    );
}
