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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ThematicAnalyzer 
 * 
 * Thematic analysis with keyword clustering.
 * 
 * Features:
 * - Theme detection based on semantic clustering
 * - Keyword extraction and grouping
 * - Mood/tone analysis
 * - Imagery classification (visual, auditory, tactile, etc.)
 * - Symbol detection
 * - Motif tracking across stanzas
 * - Emotional arc analysis
 */
public class ThematicAnalyzer {
    
    // Theme categories with associated keywords
    private static final Map<String, Set<String>> THEME_KEYWORDS = new LinkedHashMap<>();
    
    static {
        // Nature themes
        THEME_KEYWORDS.put("Nature", Set.of(
            "tree", "flower", "river", "mountain", "sky", "sun", "moon", "star", "rain", "wind",
            "forest", "ocean", "sea", "wave", "cloud", "storm", "snow", "leaf", "bird", "garden",
            "spring", "summer", "autumn", "winter", "dawn", "dusk", "sunset", "sunrise", "earth",
            "field", "meadow", "valley", "hill", "brook", "stream", "pond", "lake", "grass", "bloom"
        ));
        
        // Love themes
        THEME_KEYWORDS.put("Love", Set.of(
            "love", "heart", "kiss", "embrace", "beloved", "darling", "passion", "desire", "tender",
            "affection", "devotion", "romance", "sweetheart", "cherish", "adore", "longing", "yearn",
            "together", "forever", "soul", "mate", "partner", "wedding", "marriage", "vow", "promise"
        ));
        
        // Death/Mortality themes
        THEME_KEYWORDS.put("Mortality", Set.of(
            "death", "die", "dying", "dead", "grave", "tomb", "funeral", "mourn", "grief", "loss",
            "end", "eternal", "immortal", "mortal", "ghost", "spirit", "afterlife", "heaven", "hell",
            "fade", "wither", "decay", "ashes", "dust", "darkness", "shadow", "farewell", "goodbye"
        ));
        
        // Time themes
        THEME_KEYWORDS.put("Time", Set.of(
            "time", "moment", "hour", "day", "night", "year", "century", "age", "era", "past",
            "present", "future", "memory", "remember", "forget", "yesterday", "tomorrow", "today",
            "clock", "season", "eternal", "fleeting", "passing", "ancient", "old", "young", "new"
        ));
        
        // Identity/Self themes
        THEME_KEYWORDS.put("Identity", Set.of(
            "self", "soul", "identity", "who", "am", "being", "exist", "existence", "mind", "thought",
            "consciousness", "awareness", "inner", "outer", "reflect", "mirror", "face", "mask",
            "true", "false", "real", "dream", "illusion", "truth", "lie", "secret", "hidden"
        ));
        
        // Faith/Spirituality themes
        THEME_KEYWORDS.put("Spirituality", Set.of(
            "god", "divine", "sacred", "holy", "prayer", "faith", "believe", "soul", "spirit",
            "heaven", "angel", "blessing", "grace", "sin", "redemption", "salvation", "worship",
            "temple", "church", "eternal", "transcend", "enlighten", "mystic", "miracle"
        ));
        
        // War/Conflict themes
        THEME_KEYWORDS.put("Conflict", Set.of(
            "war", "battle", "fight", "soldier", "army", "enemy", "victory", "defeat", "blood",
            "wound", "sword", "gun", "violence", "peace", "struggle", "conflict", "rage", "anger",
            "revenge", "justice", "power", "conquer", "defend", "attack", "hero", "warrior"
        ));
        
        // Freedom themes
        THEME_KEYWORDS.put("Freedom", Set.of(
            "free", "freedom", "liberty", "escape", "cage", "prison", "chain", "bound", "fly",
            "soar", "wing", "bird", "open", "wild", "break", "release", "liberate", "independence"
        ));
        
        // Loneliness/Isolation themes
        THEME_KEYWORDS.put("Isolation", Set.of(
            "alone", "lonely", "solitude", "isolated", "abandoned", "forsaken", "empty", "void",
            "silence", "quiet", "distant", "apart", "separate", "stranger", "outcast", "exile"
        ));
        
        // Hope/Despair themes
        THEME_KEYWORDS.put("Hope", Set.of(
            "hope", "dream", "wish", "believe", "faith", "light", "dawn", "tomorrow", "future",
            "bright", "promise", "aspire", "inspire", "courage", "strength", "persevere"
        ));
        THEME_KEYWORDS.put("Despair", Set.of(
            "despair", "hopeless", "dark", "darkness", "shadow", "sorrow", "grief", "pain",
            "suffer", "anguish", "torment", "misery", "gloom", "bleak", "desolate", "lost"
        ));
        
        // Journey/Quest themes
        THEME_KEYWORDS.put("Journey", Set.of(
            "journey", "travel", "path", "road", "way", "walk", "wander", "roam", "seek", "search",
            "find", "discover", "explore", "adventure", "destination", "home", "return", "arrive"
        ));
    }
    
    // Imagery categories
    private static final Map<String, Set<String>> IMAGERY_KEYWORDS = new LinkedHashMap<>();
    
    static {
        IMAGERY_KEYWORDS.put("Visual", Set.of(
            "see", "sight", "look", "gaze", "watch", "view", "vision", "eye", "color", "light",
            "dark", "bright", "dim", "glow", "shine", "shadow", "shape", "form", "appear", "scene",
            "red", "blue", "green", "gold", "silver", "white", "black", "purple", "orange", "yellow"
        ));
        IMAGERY_KEYWORDS.put("Auditory", Set.of(
            "hear", "sound", "voice", "song", "music", "noise", "silence", "quiet", "loud", "whisper",
            "shout", "cry", "laugh", "echo", "ring", "chime", "thunder", "roar", "hum", "murmur"
        ));
        IMAGERY_KEYWORDS.put("Tactile", Set.of(
            "touch", "feel", "soft", "hard", "rough", "smooth", "cold", "warm", "hot", "cool",
            "wet", "dry", "sharp", "dull", "tender", "gentle", "harsh", "embrace", "caress", "stroke"
        ));
        IMAGERY_KEYWORDS.put("Olfactory", Set.of(
            "smell", "scent", "fragrance", "odor", "aroma", "perfume", "stench", "fresh", "sweet",
            "bitter", "flower", "rose", "smoke", "earth", "rain"
        ));
        IMAGERY_KEYWORDS.put("Gustatory", Set.of(
            "taste", "sweet", "bitter", "sour", "salty", "savory", "delicious", "flavor", "tongue",
            "eat", "drink", "hunger", "thirst", "feast", "wine", "honey", "fruit"
        ));
        IMAGERY_KEYWORDS.put("Kinesthetic", Set.of(
            "move", "motion", "run", "walk", "dance", "fly", "fall", "rise", "spin", "turn",
            "flow", "drift", "rush", "slow", "fast", "swift", "still", "rest", "leap", "jump"
        ));
    }
    
    // Mood/Tone categories
    private static final Map<String, Set<String>> MOOD_KEYWORDS = new LinkedHashMap<>();
    
    static {
        MOOD_KEYWORDS.put("Joyful", Set.of(
            "joy", "happy", "glad", "delight", "bliss", "merry", "cheerful", "bright", "laugh",
            "smile", "celebrate", "dance", "sing", "play", "fun", "pleasure", "elated"
        ));
        MOOD_KEYWORDS.put("Melancholic", Set.of(
            "sad", "sorrow", "grief", "mourn", "weep", "cry", "tears", "melancholy", "wistful",
            "longing", "nostalgia", "regret", "loss", "miss", "yearn", "ache", "heavy"
        ));
        MOOD_KEYWORDS.put("Peaceful", Set.of(
            "peace", "calm", "serene", "tranquil", "quiet", "still", "gentle", "soft", "rest",
            "ease", "comfort", "harmony", "balance", "soothe", "relax"
        ));
        MOOD_KEYWORDS.put("Anxious", Set.of(
            "fear", "afraid", "worry", "anxious", "dread", "terror", "panic", "nervous", "uneasy",
            "tense", "restless", "troubled", "disturbed", "alarmed"
        ));
        MOOD_KEYWORDS.put("Angry", Set.of(
            "anger", "rage", "fury", "wrath", "hate", "bitter", "resent", "hostile", "violent",
            "fierce", "burn", "storm", "explode", "scream"
        ));
        MOOD_KEYWORDS.put("Romantic", Set.of(
            "love", "passion", "desire", "romantic", "tender", "sweet", "gentle", "embrace",
            "kiss", "heart", "beloved", "devotion", "longing", "yearn"
        ));
        MOOD_KEYWORDS.put("Mysterious", Set.of(
            "mystery", "secret", "hidden", "shadow", "dark", "unknown", "strange", "enigma",
            "puzzle", "riddle", "veil", "obscure", "cryptic", "whisper"
        ));
        MOOD_KEYWORDS.put("Triumphant", Set.of(
            "triumph", "victory", "glory", "conquer", "win", "success", "achieve", "overcome",
            "rise", "soar", "proud", "strong", "power", "champion"
        ));
    }
    
    /**
     * Complete thematic analysis result.
     */
    public static class ThematicAnalysis {
        public final List<ThemeMatch> themes;
        public final List<ImageryMatch> imagery;
        public final List<MoodMatch> moods;
        public final List<KeywordCluster> clusters;
        public final List<SymbolMatch> symbols;
        public final List<StanzaAnalysis> stanzaAnalyses;
        public final EmotionalArc emotionalArc;
        public final String dominantTheme;
        public final String dominantMood;
        
        public ThematicAnalysis(List<ThemeMatch> themes, List<ImageryMatch> imagery,
                               List<MoodMatch> moods, List<KeywordCluster> clusters,
                               List<SymbolMatch> symbols, List<StanzaAnalysis> stanzaAnalyses,
                               EmotionalArc emotionalArc, String dominantTheme, String dominantMood) {
            this.themes = Collections.unmodifiableList(themes);
            this.imagery = Collections.unmodifiableList(imagery);
            this.moods = Collections.unmodifiableList(moods);
            this.clusters = Collections.unmodifiableList(clusters);
            this.symbols = Collections.unmodifiableList(symbols);
            this.stanzaAnalyses = Collections.unmodifiableList(stanzaAnalyses);
            this.emotionalArc = emotionalArc;
            this.dominantTheme = dominantTheme;
            this.dominantMood = dominantMood;
        }
    }
    
    public static class ThemeMatch {
        public final String theme;
        public final double strength;
        public final List<String> matchedWords;
        
        public ThemeMatch(String theme, double strength, List<String> matchedWords) {
            this.theme = theme;
            this.strength = strength;
            this.matchedWords = Collections.unmodifiableList(matchedWords);
        }
    }
    
    public static class ImageryMatch {
        public final String type;
        public final double strength;
        public final List<String> matchedWords;
        
        public ImageryMatch(String type, double strength, List<String> matchedWords) {
            this.type = type;
            this.strength = strength;
            this.matchedWords = Collections.unmodifiableList(matchedWords);
        }
    }
    
    public static class MoodMatch {
        public final String mood;
        public final double strength;
        public final List<String> matchedWords;
        
        public MoodMatch(String mood, double strength, List<String> matchedWords) {
            this.mood = mood;
            this.strength = strength;
            this.matchedWords = Collections.unmodifiableList(matchedWords);
        }
    }
    
    public static class KeywordCluster {
        public final String label;
        public final List<String> keywords;
        public final double coherence;
        
        public KeywordCluster(String label, List<String> keywords, double coherence) {
            this.label = label;
            this.keywords = Collections.unmodifiableList(keywords);
            this.coherence = coherence;
        }
    }
    
    public static class SymbolMatch {
        public final String symbol;
        public final String potentialMeaning;
        public final int occurrences;
        public final List<Integer> lineNumbers;
        
        public SymbolMatch(String symbol, String potentialMeaning, int occurrences, List<Integer> lineNumbers) {
            this.symbol = symbol;
            this.potentialMeaning = potentialMeaning;
            this.occurrences = occurrences;
            this.lineNumbers = Collections.unmodifiableList(lineNumbers);
        }
    }
    
    public static class StanzaAnalysis {
        public final int stanzaNumber;
        public final String dominantTheme;
        public final String dominantMood;
        public final List<String> keywords;
        
        public StanzaAnalysis(int stanzaNumber, String dominantTheme, String dominantMood, List<String> keywords) {
            this.stanzaNumber = stanzaNumber;
            this.dominantTheme = dominantTheme;
            this.dominantMood = dominantMood;
            this.keywords = Collections.unmodifiableList(keywords);
        }
    }
    
    public static class EmotionalArc {
        public final List<String> progression;
        public final String pattern; // e.g., "Rising", "Falling", "Arc", "Flat"
        public final String description;
        
        public EmotionalArc(List<String> progression, String pattern, String description) {
            this.progression = Collections.unmodifiableList(progression);
            this.pattern = pattern;
            this.description = description;
        }
    }
    
    // Common symbols and their meanings
    private static final Map<String, String> SYMBOL_MEANINGS = new LinkedHashMap<>();
    
    static {
        SYMBOL_MEANINGS.put("rose", "Love, beauty, passion");
        SYMBOL_MEANINGS.put("water", "Life, purification, change");
        SYMBOL_MEANINGS.put("fire", "Passion, destruction, transformation");
        SYMBOL_MEANINGS.put("night", "Mystery, death, unconscious");
        SYMBOL_MEANINGS.put("light", "Hope, knowledge, divinity");
        SYMBOL_MEANINGS.put("darkness", "Evil, ignorance, fear");
        SYMBOL_MEANINGS.put("moon", "Femininity, cycles, intuition");
        SYMBOL_MEANINGS.put("sun", "Life, power, masculinity");
        SYMBOL_MEANINGS.put("bird", "Freedom, transcendence, soul");
        SYMBOL_MEANINGS.put("tree", "Life, growth, connection");
        SYMBOL_MEANINGS.put("river", "Time, journey, life");
        SYMBOL_MEANINGS.put("mountain", "Achievement, obstacle, stability");
        SYMBOL_MEANINGS.put("mirror", "Self-reflection, truth, vanity");
        SYMBOL_MEANINGS.put("road", "Journey, choices, destiny");
        SYMBOL_MEANINGS.put("heart", "Love, emotion, courage");
        SYMBOL_MEANINGS.put("storm", "Turmoil, change, power");
        SYMBOL_MEANINGS.put("garden", "Paradise, cultivation, fertility");
        SYMBOL_MEANINGS.put("winter", "Death, dormancy, hardship");
        SYMBOL_MEANINGS.put("spring", "Rebirth, hope, youth");
        SYMBOL_MEANINGS.put("shadow", "Hidden self, death, unconscious");
        SYMBOL_MEANINGS.put("star", "Guidance, hope, destiny");
        SYMBOL_MEANINGS.put("blood", "Life, sacrifice, violence");
        SYMBOL_MEANINGS.put("crown", "Authority, achievement, burden");
        SYMBOL_MEANINGS.put("door", "Opportunity, transition, mystery");
        SYMBOL_MEANINGS.put("window", "Perception, opportunity, barrier");
    }
    
    /**
     * Analyze themes in a text.
     */
    public ThematicAnalysis analyze(String text) {
        if (text == null || text.isBlank()) {
            return emptyAnalysis();
        }
        
        // Extract all words
        List<String> allWords = extractWords(text);
        Set<String> uniqueWords = new HashSet<>(allWords);
        Map<String, Integer> wordCounts = countWords(allWords);
        
        // Analyze themes
        List<ThemeMatch> themes = analyzeThemes(uniqueWords, wordCounts, allWords.size());
        
        // Analyze imagery
        List<ImageryMatch> imagery = analyzeImagery(uniqueWords, wordCounts, allWords.size());
        
        // Analyze mood
        List<MoodMatch> moods = analyzeMoods(uniqueWords, wordCounts, allWords.size());
        
        // Create keyword clusters
        List<KeywordCluster> clusters = createClusters(wordCounts, allWords.size());
        
        // Detect symbols
        List<SymbolMatch> symbols = detectSymbols(text, wordCounts);
        
        // Analyze by stanza
        List<StanzaAnalysis> stanzaAnalyses = analyzeStanzas(text);
        
        // Compute emotional arc
        EmotionalArc arc = computeEmotionalArc(stanzaAnalyses);
        
        // Determine dominant theme and mood
        String dominantTheme = themes.isEmpty() ? "Unknown" : themes.get(0).theme;
        String dominantMood = moods.isEmpty() ? "Neutral" : moods.get(0).mood;
        
        return new ThematicAnalysis(themes, imagery, moods, clusters, symbols,
                stanzaAnalyses, arc, dominantTheme, dominantMood);
    }
    
    private List<String> extractWords(String text) {
        List<String> words = new ArrayList<>();
        for (String line : PoetryUtils.splitLines(text)) {
            for (String word : PoetryUtils.wordsInLine(line)) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }
    
    private Map<String, Integer> countWords(List<String> words) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String word : words) {
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }
    
    private List<ThemeMatch> analyzeThemes(Set<String> words, Map<String, Integer> counts, int total) {
        List<ThemeMatch> matches = new ArrayList<>();
        
        for (Map.Entry<String, Set<String>> theme : THEME_KEYWORDS.entrySet()) {
            List<String> matched = new ArrayList<>();
            int matchCount = 0;
            
            for (String keyword : theme.getValue()) {
                if (words.contains(keyword)) {
                    matched.add(keyword);
                    matchCount += counts.getOrDefault(keyword, 0);
                }
            }
            
            if (!matched.isEmpty()) {
                double strength = Math.min(1.0, (double) matchCount / Math.max(1, total / 10));
                matches.add(new ThemeMatch(theme.getKey(), strength, matched));
            }
        }
        
        matches.sort((a, b) -> Double.compare(b.strength, a.strength));
        return matches;
    }
    
    private List<ImageryMatch> analyzeImagery(Set<String> words, Map<String, Integer> counts, int total) {
        List<ImageryMatch> matches = new ArrayList<>();
        
        for (Map.Entry<String, Set<String>> type : IMAGERY_KEYWORDS.entrySet()) {
            List<String> matched = new ArrayList<>();
            int matchCount = 0;
            
            for (String keyword : type.getValue()) {
                if (words.contains(keyword)) {
                    matched.add(keyword);
                    matchCount += counts.getOrDefault(keyword, 0);
                }
            }
            
            if (!matched.isEmpty()) {
                double strength = Math.min(1.0, (double) matchCount / Math.max(1, total / 15));
                matches.add(new ImageryMatch(type.getKey(), strength, matched));
            }
        }
        
        matches.sort((a, b) -> Double.compare(b.strength, a.strength));
        return matches;
    }
    
    private List<MoodMatch> analyzeMoods(Set<String> words, Map<String, Integer> counts, int total) {
        List<MoodMatch> matches = new ArrayList<>();
        
        for (Map.Entry<String, Set<String>> mood : MOOD_KEYWORDS.entrySet()) {
            List<String> matched = new ArrayList<>();
            int matchCount = 0;
            
            for (String keyword : mood.getValue()) {
                if (words.contains(keyword)) {
                    matched.add(keyword);
                    matchCount += counts.getOrDefault(keyword, 0);
                }
            }
            
            if (!matched.isEmpty()) {
                double strength = Math.min(1.0, (double) matchCount / Math.max(1, total / 10));
                matches.add(new MoodMatch(mood.getKey(), strength, matched));
            }
        }
        
        matches.sort((a, b) -> Double.compare(b.strength, a.strength));
        return matches;
    }
    
    private List<KeywordCluster> createClusters(Map<String, Integer> wordCounts, int total) {
        List<KeywordCluster> clusters = new ArrayList<>();
        
        // Get content words (non-function words) sorted by frequency
        List<Map.Entry<String, Integer>> contentWords = wordCounts.entrySet().stream()
                .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
                .filter(e -> e.getKey().length() > 2)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(30)
                .collect(Collectors.toList());
        
        if (contentWords.isEmpty()) return clusters;
        
        // Simple clustering by semantic similarity (theme overlap)
        Map<String, List<String>> themeClusters = new LinkedHashMap<>();
        Set<String> assigned = new HashSet<>();
        
        for (Map.Entry<String, Integer> word : contentWords) {
            if (assigned.contains(word.getKey())) continue;
            
            String bestTheme = findBestTheme(word.getKey());
            if (bestTheme != null) {
                themeClusters.computeIfAbsent(bestTheme, k -> new ArrayList<>()).add(word.getKey());
                assigned.add(word.getKey());
            }
        }
        
        // Create clusters
        for (Map.Entry<String, List<String>> entry : themeClusters.entrySet()) {
            if (entry.getValue().size() >= 2) {
                double coherence = Math.min(1.0, entry.getValue().size() / 5.0);
                clusters.add(new KeywordCluster(entry.getKey(), entry.getValue(), coherence));
            }
        }
        
        // Add unclustered important words as "General" cluster
        List<String> unclustered = contentWords.stream()
                .map(Map.Entry::getKey)
                .filter(w -> !assigned.contains(w))
                .limit(10)
                .collect(Collectors.toList());
        
        if (!unclustered.isEmpty()) {
            clusters.add(new KeywordCluster("General", unclustered, 0.5));
        }
        
        return clusters;
    }
    
    private String findBestTheme(String word) {
        for (Map.Entry<String, Set<String>> theme : THEME_KEYWORDS.entrySet()) {
            if (theme.getValue().contains(word)) {
                return theme.getKey();
            }
        }
        return null;
    }
    
    private List<SymbolMatch> detectSymbols(String text, Map<String, Integer> wordCounts) {
        List<SymbolMatch> symbols = new ArrayList<>();
        List<String> lines = PoetryUtils.splitLines(text);
        
        for (Map.Entry<String, String> entry : SYMBOL_MEANINGS.entrySet()) {
            String symbol = entry.getKey();
            int count = wordCounts.getOrDefault(symbol, 0);
            
            if (count > 0) {
                List<Integer> lineNumbers = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).toLowerCase(Locale.ROOT).contains(symbol)) {
                        lineNumbers.add(i + 1);
                    }
                }
                symbols.add(new SymbolMatch(symbol, entry.getValue(), count, lineNumbers));
            }
        }
        
        symbols.sort((a, b) -> Integer.compare(b.occurrences, a.occurrences));
        return symbols;
    }
    
    private List<StanzaAnalysis> analyzeStanzas(String text) {
        List<StanzaAnalysis> analyses = new ArrayList<>();
        String[] stanzas = text.split("\n\n+");
        
        for (int i = 0; i < stanzas.length; i++) {
            String stanza = stanzas[i];
            if (stanza.isBlank()) continue;
            
            List<String> words = extractWords(stanza);
            Set<String> unique = new HashSet<>(words);
            Map<String, Integer> counts = countWords(words);
            
            List<ThemeMatch> themes = analyzeThemes(unique, counts, words.size());
            List<MoodMatch> moods = analyzeMoods(unique, counts, words.size());
            
            String theme = themes.isEmpty() ? "General" : themes.get(0).theme;
            String mood = moods.isEmpty() ? "Neutral" : moods.get(0).mood;
            
            List<String> keywords = counts.entrySet().stream()
                    .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
                    .filter(e -> e.getKey().length() > 2)
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            analyses.add(new StanzaAnalysis(i + 1, theme, mood, keywords));
        }
        
        return analyses;
    }
    
    private EmotionalArc computeEmotionalArc(List<StanzaAnalysis> stanzas) {
        if (stanzas.isEmpty()) {
            return new EmotionalArc(Collections.emptyList(), "Flat", "No discernible emotional progression");
        }
        
        List<String> progression = stanzas.stream()
                .map(s -> s.dominantMood)
                .collect(Collectors.toList());
        
        // Simple pattern detection
        String pattern = "Mixed";
        String description = "The emotional tone varies throughout the poem";
        
        if (progression.size() >= 2) {
            String first = progression.get(0);
            String last = progression.get(progression.size() - 1);
            
            // Check for common patterns
            boolean startsPositive = isPositiveMood(first);
            boolean endsPositive = isPositiveMood(last);
            
            if (startsPositive && !endsPositive) {
                pattern = "Falling";
                description = "The poem moves from positive to negative emotions";
            } else if (!startsPositive && endsPositive) {
                pattern = "Rising";
                description = "The poem moves from negative to positive emotions";
            } else if (startsPositive && endsPositive) {
                // Check for arc (positive-negative-positive)
                boolean hasDip = progression.stream().skip(1).limit(progression.size() - 2)
                        .anyMatch(m -> !isPositiveMood(m));
                if (hasDip) {
                    pattern = "Arc";
                    description = "The poem follows a traditional dramatic arc";
                } else {
                    pattern = "Sustained Positive";
                    description = "The poem maintains a positive emotional tone";
                }
            } else {
                // Check for inverted arc (negative-positive-negative)
                boolean hasPeak = progression.stream().skip(1).limit(progression.size() - 2)
                        .anyMatch(this::isPositiveMood);
                if (hasPeak) {
                    pattern = "Inverted Arc";
                    description = "The poem has a moment of hope within overall darkness";
                } else {
                    pattern = "Sustained Dark";
                    description = "The poem maintains a somber emotional tone";
                }
            }
        }
        
        return new EmotionalArc(progression, pattern, description);
    }
    
    private boolean isPositiveMood(String mood) {
        return Set.of("Joyful", "Peaceful", "Romantic", "Triumphant", "Hope").contains(mood);
    }
    
    private ThematicAnalysis emptyAnalysis() {
        return new ThematicAnalysis(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                new EmotionalArc(Collections.emptyList(), "Flat", "No content"),
                "Unknown", "Neutral"
        );
    }
    
    /**
     * Get summary of thematic analysis.
     */
    public String getSummary(ThematicAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Thematic Analysis ===\n\n");
        
        sb.append(String.format("Dominant Theme: %s\n", analysis.dominantTheme));
        sb.append(String.format("Dominant Mood: %s\n\n", analysis.dominantMood));
        
        if (!analysis.themes.isEmpty()) {
            sb.append("Themes Detected:\n");
            for (ThemeMatch theme : analysis.themes.stream().limit(5).collect(Collectors.toList())) {
                sb.append(String.format("  • %s (%.0f%%) - %s\n", 
                        theme.theme, theme.strength * 100, 
                        String.join(", ", theme.matchedWords.stream().limit(5).collect(Collectors.toList()))));
            }
            sb.append("\n");
        }
        
        if (!analysis.moods.isEmpty()) {
            sb.append("Mood/Tone:\n");
            for (MoodMatch mood : analysis.moods.stream().limit(3).collect(Collectors.toList())) {
                sb.append(String.format("  • %s (%.0f%%)\n", mood.mood, mood.strength * 100));
            }
            sb.append("\n");
        }
        
        if (!analysis.imagery.isEmpty()) {
            sb.append("Imagery Types:\n");
            for (ImageryMatch img : analysis.imagery.stream().limit(4).collect(Collectors.toList())) {
                sb.append(String.format("  • %s (%.0f%%)\n", img.type, img.strength * 100));
            }
            sb.append("\n");
        }
        
        if (!analysis.symbols.isEmpty()) {
            sb.append("Symbols Detected:\n");
            for (SymbolMatch sym : analysis.symbols.stream().limit(5).collect(Collectors.toList())) {
                sb.append(String.format("  • %s (%dx) - %s\n", 
                        sym.symbol, sym.occurrences, sym.potentialMeaning));
            }
            sb.append("\n");
        }
        
        sb.append("Emotional Arc: ").append(analysis.emotionalArc.pattern).append("\n");
        sb.append(analysis.emotionalArc.description).append("\n");
        
        return sb.toString();
    }
}
