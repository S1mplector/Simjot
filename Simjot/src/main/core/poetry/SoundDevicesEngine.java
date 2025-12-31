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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SoundDevicesEngine 
 * 
 * Sound devices detection of alliteration, assonance, and consonance.
 * 
 * Features:
 * - Alliteration detection (repeated initial consonant sounds)
 * - Assonance detection (repeated vowel sounds)
 * - Consonance detection (repeated consonant sounds)
 * - Sibilance detection (s, sh, z sounds)
 * - Onomatopoeia detection
 * - Sound pattern clustering and scoring
 * - Visual highlighting data for UI
 * 
 * @author S1mplector
 */
public class SoundDevicesEngine {
    
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z']+");
    
    // Phoneme mapping for more accurate sound analysis
    private static final Map<String, String> INITIAL_SOUND_MAP = new HashMap<>();
    private static final Set<Character> VOWELS = Set.of('a', 'e', 'i', 'o', 'u');
    private static final Set<String> SIBILANTS = Set.of("s", "sh", "z", "zh", "ch", "j");
    
    // Common onomatopoeia
    private static final Set<String> ONOMATOPOEIA = new HashSet<>(Arrays.asList(
        "bang", "boom", "buzz", "click", "clang", "clap", "clatter", "crack", "crash",
        "creak", "drip", "fizz", "flutter", "gurgle", "hiss", "howl", "hum", "jingle",
        "knock", "murmur", "ping", "plop", "pop", "purr", "rattle", "ring", "roar",
        "rumble", "rush", "rustle", "screech", "sigh", "sizzle", "slam", "slap", "slurp",
        "smash", "snap", "splash", "splatter", "squeak", "squish", "swish", "swoosh",
        "thud", "thump", "tick", "tinkle", "whisper", "whir", "whistle", "whoosh",
        "zip", "zoom"
    ));
    
    static {
        // Map initial letter combinations to their phonetic sound
        INITIAL_SOUND_MAP.put("ph", "f");
        INITIAL_SOUND_MAP.put("gh", "g");
        INITIAL_SOUND_MAP.put("gn", "n");
        INITIAL_SOUND_MAP.put("kn", "n");
        INITIAL_SOUND_MAP.put("wr", "r");
        INITIAL_SOUND_MAP.put("wh", "w");
        INITIAL_SOUND_MAP.put("ps", "s");
        INITIAL_SOUND_MAP.put("pn", "n");
        INITIAL_SOUND_MAP.put("ch", "ch");
        INITIAL_SOUND_MAP.put("sh", "sh");
        INITIAL_SOUND_MAP.put("th", "th");
        INITIAL_SOUND_MAP.put("qu", "kw");
        INITIAL_SOUND_MAP.put("ck", "k");
        INITIAL_SOUND_MAP.put("sc", "sk");
        INITIAL_SOUND_MAP.put("ce", "s");
        INITIAL_SOUND_MAP.put("ci", "s");
        INITIAL_SOUND_MAP.put("cy", "s");
        INITIAL_SOUND_MAP.put("ge", "j");
        INITIAL_SOUND_MAP.put("gi", "j");
        INITIAL_SOUND_MAP.put("gy", "j");
    }
    
    /**
     * Represents a detected sound device instance.
     */
    public static class SoundDevice {
        public enum Type {
            ALLITERATION("Alliteration", "Repeated initial consonant sounds"),
            ASSONANCE("Assonance", "Repeated vowel sounds within words"),
            CONSONANCE("Consonance", "Repeated consonant sounds"),
            SIBILANCE("Sibilance", "Repeated s, sh, z sounds"),
            INTERNAL_RHYME("Internal Rhyme", "Repeated end sounds within a line"),
            ONOMATOPOEIA("Onomatopoeia", "Words that imitate sounds");
            
            public final String name;
            public final String description;
            
            Type(String name, String description) {
                this.name = name;
                this.description = description;
            }
        }
        
        public final Type type;
        public final List<WordMatch> matches;
        public final String sound;
        public final int lineNumber;
        public final double strength; // 0.0 to 1.0
        
        public SoundDevice(Type type, List<WordMatch> matches, String sound, int lineNumber, double strength) {
            this.type = type;
            this.matches = Collections.unmodifiableList(matches);
            this.sound = sound;
            this.lineNumber = lineNumber;
            this.strength = strength;
        }
    }
    
    /**
     * Represents a word that matches a sound pattern.
     */
    public static class WordMatch {
        public final String word;
        public final int startIndex; // position in original text
        public final int endIndex;
        public final String matchedSound;
        public final int wordIndex; // word position in line
        
        public WordMatch(String word, int startIndex, int endIndex, String matchedSound, int wordIndex) {
            this.word = word;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.matchedSound = matchedSound;
            this.wordIndex = wordIndex;
        }
    }
    
    /**
     * Full analysis result for a poem.
     */
    public static class SoundAnalysis {
        public final List<SoundDevice> devices;
        public final Map<SoundDevice.Type, Integer> deviceCounts;
        public final Map<SoundDevice.Type, Double> averageStrengths;
        public final List<String> dominantSounds;
        public final double overallSoundDensity;
        public final List<LineAnalysis> lineAnalyses;
        
        public SoundAnalysis(List<SoundDevice> devices, Map<SoundDevice.Type, Integer> deviceCounts,
                            Map<SoundDevice.Type, Double> averageStrengths, List<String> dominantSounds,
                            double overallSoundDensity, List<LineAnalysis> lineAnalyses) {
            this.devices = Collections.unmodifiableList(devices);
            this.deviceCounts = Collections.unmodifiableMap(deviceCounts);
            this.averageStrengths = Collections.unmodifiableMap(averageStrengths);
            this.dominantSounds = Collections.unmodifiableList(dominantSounds);
            this.overallSoundDensity = overallSoundDensity;
            this.lineAnalyses = Collections.unmodifiableList(lineAnalyses);
        }
    }
    
    /**
     * Analysis for a single line.
     */
    public static class LineAnalysis {
        public final int lineNumber;
        public final String line;
        public final List<SoundDevice> devices;
        public final Map<Integer, List<SoundDevice.Type>> wordHighlights; // word index -> device types
        
        public LineAnalysis(int lineNumber, String line, List<SoundDevice> devices,
                           Map<Integer, List<SoundDevice.Type>> wordHighlights) {
            this.lineNumber = lineNumber;
            this.line = line;
            this.devices = Collections.unmodifiableList(devices);
            this.wordHighlights = Collections.unmodifiableMap(wordHighlights);
        }
    }
    
    /**
     * Analyze a complete poem for sound devices.
     */
    public SoundAnalysis analyzePoem(String text) {
        if (text == null || text.isBlank()) {
            return new SoundAnalysis(Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList(), 0.0, Collections.emptyList());
        }
        
        List<String> lines = PoetryUtils.splitLines(text);
        List<SoundDevice> allDevices = new ArrayList<>();
        List<LineAnalysis> lineAnalyses = new ArrayList<>();
        Map<String, Integer> soundFrequency = new HashMap<>();
        
        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);
            if (line == null || line.isBlank()) continue;
            
            LineAnalysis la = analyzeLine(line, lineNum);
            lineAnalyses.add(la);
            allDevices.addAll(la.devices);
            
            // Track sound frequency
            for (SoundDevice device : la.devices) {
                soundFrequency.merge(device.sound, 1, Integer::sum);
            }
        }
        
        // Aggregate statistics
        Map<SoundDevice.Type, Integer> deviceCounts = new EnumMap<>(SoundDevice.Type.class);
        Map<SoundDevice.Type, List<Double>> strengthLists = new EnumMap<>(SoundDevice.Type.class);
        
        for (SoundDevice device : allDevices) {
            deviceCounts.merge(device.type, 1, Integer::sum);
            strengthLists.computeIfAbsent(device.type, k -> new ArrayList<>()).add(device.strength);
        }
        
        Map<SoundDevice.Type, Double> averageStrengths = new EnumMap<>(SoundDevice.Type.class);
        for (Map.Entry<SoundDevice.Type, List<Double>> e : strengthLists.entrySet()) {
            double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            averageStrengths.put(e.getKey(), avg);
        }
        
        // Find dominant sounds
        List<String> dominantSounds = soundFrequency.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
        
        // Calculate overall sound density
        int totalWords = lines.stream()
                .filter(l -> l != null && !l.isBlank())
                .mapToInt(l -> PoetryUtils.wordsInLine(l).size())
                .sum();
        int totalMatches = allDevices.stream().mapToInt(d -> d.matches.size()).sum();
        double density = totalWords > 0 ? (double) totalMatches / totalWords : 0.0;
        
        return new SoundAnalysis(allDevices, deviceCounts, averageStrengths, dominantSounds,
                density, lineAnalyses);
    }
    
    /**
     * Analyze a single line for sound devices.
     */
    public LineAnalysis analyzeLine(String line, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        Map<Integer, List<SoundDevice.Type>> wordHighlights = new HashMap<>();
        
        if (line == null || line.isBlank()) {
            return new LineAnalysis(lineNumber, line, devices, wordHighlights);
        }
        
        List<WordInfo> words = extractWords(line);
        if (words.size() < 2) {
            return new LineAnalysis(lineNumber, line, devices, wordHighlights);
        }
        
        // Detect alliteration
        devices.addAll(detectAlliteration(words, lineNumber));
        
        // Detect assonance
        devices.addAll(detectAssonance(words, lineNumber));
        
        // Detect consonance
        devices.addAll(detectConsonance(words, lineNumber));
        
        // Detect sibilance
        devices.addAll(detectSibilance(words, lineNumber));
        
        // Detect internal rhyme
        devices.addAll(detectInternalRhymes(words, lineNumber));
        
        // Detect onomatopoeia
        devices.addAll(detectOnomatopoeia(words, lineNumber));
        
        // Build word highlights map
        for (SoundDevice device : devices) {
            for (WordMatch match : device.matches) {
                wordHighlights.computeIfAbsent(match.wordIndex, k -> new ArrayList<>()).add(device.type);
            }
        }
        
        return new LineAnalysis(lineNumber, line, devices, wordHighlights);
    }
    
    /**
     * Extract words with position information.
     */
    private List<WordInfo> extractWords(String line) {
        List<WordInfo> words = new ArrayList<>();
        Matcher m = WORD_PATTERN.matcher(line);
        int idx = 0;
        while (m.find()) {
            words.add(new WordInfo(m.group(), m.start(), m.end(), idx++));
        }
        return words;
    }
    
    private static class WordInfo {
        final String word;
        final int start;
        final int end;
        final int index;
        
        WordInfo(String word, int start, int end, int index) {
            this.word = word;
            this.start = start;
            this.end = end;
            this.index = index;
        }
    }
    
    /**
     * Detect alliteration (repeated initial consonant sounds).
     */
    private List<SoundDevice> detectAlliteration(List<WordInfo> words, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        Map<String, List<WordInfo>> soundGroups = new HashMap<>();
        
        for (WordInfo word : words) {
            String sound = getInitialSound(word.word.toLowerCase(Locale.ROOT));
            if (sound != null && !isVowelSound(sound)) {
                soundGroups.computeIfAbsent(sound, k -> new ArrayList<>()).add(word);
            }
        }
        
        for (Map.Entry<String, List<WordInfo>> e : soundGroups.entrySet()) {
            List<WordInfo> group = e.getValue();
            if (group.size() >= 2) {
                // Check if words are close enough (within 4 words of each other)
                List<List<WordInfo>> clusters = clusterByProximity(group, 4);
                for (List<WordInfo> cluster : clusters) {
                    if (cluster.size() >= 2) {
                        List<WordMatch> matches = cluster.stream()
                                .map(w -> new WordMatch(w.word, w.start, w.end, e.getKey(), w.index))
                                .toList();
                        double strength = Math.min(1.0, cluster.size() / 4.0);
                        devices.add(new SoundDevice(SoundDevice.Type.ALLITERATION, matches, 
                                e.getKey(), lineNumber, strength));
                    }
                }
            }
        }
        
        return devices;
    }
    
    /**
     * Detect assonance (repeated vowel sounds).
     */
    private List<SoundDevice> detectAssonance(List<WordInfo> words, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        Map<String, List<WordInfo>> soundGroups = new HashMap<>();
        
        for (WordInfo word : words) {
            Set<String> vowelSounds = getVowelSounds(word.word.toLowerCase(Locale.ROOT));
            for (String sound : vowelSounds) {
                soundGroups.computeIfAbsent(sound, k -> new ArrayList<>()).add(word);
            }
        }
        
        for (Map.Entry<String, List<WordInfo>> e : soundGroups.entrySet()) {
            List<WordInfo> group = e.getValue();
            if (group.size() >= 3) { // Need at least 3 for assonance to be notable
                List<List<WordInfo>> clusters = clusterByProximity(group, 5);
                for (List<WordInfo> cluster : clusters) {
                    if (cluster.size() >= 3) {
                        List<WordMatch> matches = cluster.stream()
                                .map(w -> new WordMatch(w.word, w.start, w.end, e.getKey(), w.index))
                                .toList();
                        double strength = Math.min(1.0, cluster.size() / 5.0);
                        devices.add(new SoundDevice(SoundDevice.Type.ASSONANCE, matches,
                                e.getKey(), lineNumber, strength));
                    }
                }
            }
        }
        
        return devices;
    }
    
    /**
     * Detect consonance (repeated consonant sounds anywhere in words).
     */
    private List<SoundDevice> detectConsonance(List<WordInfo> words, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        Map<String, List<WordInfo>> soundGroups = new HashMap<>();
        
        for (WordInfo word : words) {
            Set<String> consonantSounds = getConsonantSounds(word.word.toLowerCase(Locale.ROOT));
            for (String sound : consonantSounds) {
                soundGroups.computeIfAbsent(sound, k -> new ArrayList<>()).add(word);
            }
        }
        
        for (Map.Entry<String, List<WordInfo>> e : soundGroups.entrySet()) {
            List<WordInfo> group = e.getValue();
            // Need at least 3, and shouldn't overlap too much with alliteration
            if (group.size() >= 3) {
                List<List<WordInfo>> clusters = clusterByProximity(group, 5);
                for (List<WordInfo> cluster : clusters) {
                    if (cluster.size() >= 3) {
                        // Check if it's primarily internal consonance (not initial)
                        long internalCount = cluster.stream()
                                .filter(w -> !getInitialSound(w.word.toLowerCase(Locale.ROOT)).equals(e.getKey()))
                                .count();
                        if (internalCount >= 2) {
                            List<WordMatch> matches = cluster.stream()
                                    .map(w -> new WordMatch(w.word, w.start, w.end, e.getKey(), w.index))
                                    .toList();
                            double strength = Math.min(1.0, cluster.size() / 5.0);
                            devices.add(new SoundDevice(SoundDevice.Type.CONSONANCE, matches,
                                    e.getKey(), lineNumber, strength));
                        }
                    }
                }
            }
        }
        
        return devices;
    }
    
    /**
     * Detect sibilance (s, sh, z sounds).
     */
    private List<SoundDevice> detectSibilance(List<WordInfo> words, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        List<WordInfo> sibilantWords = new ArrayList<>();
        
        for (WordInfo word : words) {
            String lower = word.word.toLowerCase(Locale.ROOT);
            if (containsSibilant(lower)) {
                sibilantWords.add(word);
            }
        }
        
        if (sibilantWords.size() >= 2) {
            List<List<WordInfo>> clusters = clusterByProximity(sibilantWords, 4);
            for (List<WordInfo> cluster : clusters) {
                if (cluster.size() >= 2) {
                    List<WordMatch> matches = cluster.stream()
                            .map(w -> new WordMatch(w.word, w.start, w.end, "s/sh/z", w.index))
                            .toList();
                    double strength = Math.min(1.0, cluster.size() / 4.0);
                    devices.add(new SoundDevice(SoundDevice.Type.SIBILANCE, matches,
                            "s/sh/z", lineNumber, strength));
                }
            }
        }
        
        return devices;
    }
    
    /**
     * Detect onomatopoeia.
     */
    private List<SoundDevice> detectOnomatopoeia(List<WordInfo> words, int lineNumber) {
        List<SoundDevice> devices = new ArrayList<>();
        
        for (WordInfo word : words) {
            String lower = word.word.toLowerCase(Locale.ROOT);
            if (ONOMATOPOEIA.contains(lower)) {
                List<WordMatch> matches = List.of(
                        new WordMatch(word.word, word.start, word.end, lower, word.index));
                devices.add(new SoundDevice(SoundDevice.Type.ONOMATOPOEIA, matches,
                        lower, lineNumber, 1.0));
            }
        }
        
        return devices;
    }
    
    /**
     * Get the initial sound of a word.
     */
    private String getInitialSound(String word) {
        if (word == null || word.isEmpty()) return "";
        
        // Check for digraphs first
        if (word.length() >= 2) {
            String digraph = word.substring(0, 2);
            if (INITIAL_SOUND_MAP.containsKey(digraph)) {
                return INITIAL_SOUND_MAP.get(digraph);
            }
        }
        
        return String.valueOf(word.charAt(0));
    }
    
    /**
     * Check if a sound is a vowel sound.
     */
    private boolean isVowelSound(String sound) {
        if (sound == null || sound.isEmpty()) return false;
        return VOWELS.contains(sound.charAt(0));
    }
    
    /**
     * Extract vowel sounds from a word.
     */
    private Set<String> getVowelSounds(String word) {
        Set<String> sounds = new HashSet<>();
        if (word == null || word.isEmpty()) return sounds;
        
        // Simplified vowel sound detection
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (VOWELS.contains(c)) {
                // Check for common vowel combinations
                if (i < word.length() - 1) {
                    char next = word.charAt(i + 1);
                    String combo = "" + c + next;
                    if (isVowelCombo(combo)) {
                        sounds.add(combo);
                        i++; // Skip next char
                        continue;
                    }
                }
                sounds.add(String.valueOf(c));
            }
        }
        
        return sounds;
    }
    
    /**
     * Check if a two-character string is a vowel combination.
     */
    private boolean isVowelCombo(String combo) {
        return Set.of("ea", "ee", "ie", "oo", "ou", "ow", "oi", "oy", "ai", "ay", "au", "aw",
                "ei", "ey", "eu", "ew", "oa", "oe", "ui", "ue").contains(combo);
    }
    
    /**
     * Extract consonant sounds from a word.
     */
    private Set<String> getConsonantSounds(String word) {
        Set<String> sounds = new HashSet<>();
        if (word == null || word.isEmpty()) return sounds;
        
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!VOWELS.contains(c) && Character.isLetter(c)) {
                // Check for consonant digraphs
                if (i < word.length() - 1) {
                    char next = word.charAt(i + 1);
                    String combo = "" + c + next;
                    if (isConsonantDigraph(combo)) {
                        sounds.add(combo);
                        i++;
                        continue;
                    }
                }
                sounds.add(String.valueOf(c));
            }
        }
        
        return sounds;
    }
    
    /**
     * Check if a two-character string is a consonant digraph.
     */
    private boolean isConsonantDigraph(String combo) {
        return Set.of("ch", "sh", "th", "ph", "wh", "gh", "ng", "ck", "ll", "ss", "ff", "zz",
                "tt", "pp", "bb", "dd", "gg", "mm", "nn", "rr").contains(combo);
    }
    
    /**
     * Check if a word contains sibilant sounds.
     */
    private boolean containsSibilant(String word) {
        if (word == null) return false;
        return word.contains("s") || word.contains("z") || word.contains("sh") ||
               word.contains("ch") || word.contains("x");
    }
    
    /**
     * Cluster words by proximity (word distance).
     */
    private List<List<WordInfo>> clusterByProximity(List<WordInfo> words, int maxGap) {
        List<List<WordInfo>> clusters = new ArrayList<>();
        if (words.isEmpty()) return clusters;
        
        // Sort by word index
        List<WordInfo> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingInt(w -> w.index));
        
        List<WordInfo> currentCluster = new ArrayList<>();
        currentCluster.add(sorted.get(0));
        
        for (int i = 1; i < sorted.size(); i++) {
            WordInfo prev = sorted.get(i - 1);
            WordInfo curr = sorted.get(i);
            
            if (curr.index - prev.index <= maxGap) {
                currentCluster.add(curr);
            } else {
                if (currentCluster.size() >= 2) {
                    clusters.add(new ArrayList<>(currentCluster));
                }
                currentCluster.clear();
                currentCluster.add(curr);
            }
        }
        
        if (currentCluster.size() >= 2) {
            clusters.add(currentCluster);
        }
        
        return clusters;
    }
    
    /**
     * Get a summary of sound devices in the poem.
     */
    public String getSummary(SoundAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Sound Devices Analysis ===\n\n");
        
        if (analysis.devices.isEmpty()) {
            sb.append("No significant sound devices detected.\n");
            return sb.toString();
        }
        
        sb.append("Device Counts:\n");
        for (SoundDevice.Type type : SoundDevice.Type.values()) {
            int count = analysis.deviceCounts.getOrDefault(type, 0);
            if (count > 0) {
                double avgStrength = analysis.averageStrengths.getOrDefault(type, 0.0);
                sb.append(String.format("  • %s: %d instances (avg strength: %.0f%%)\n",
                        type.name, count, avgStrength * 100));
            }
        }
        
        sb.append("\nDominant Sounds: ").append(String.join(", ", analysis.dominantSounds));
        sb.append(String.format("\nOverall Sound Density: %.1f%%\n", analysis.overallSoundDensity * 100));
        
        return sb.toString();
    }
}
