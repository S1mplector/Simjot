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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.ResourceLoader;

/**
 * PoetryDictionary utility class
 * 
 * Serves as a centralized dictionary service for poetry analysis.
 * 
 * Loads from the simple-english-dictionary resource and provides:
 * - Part of speech lookup
 * - Syllable count estimation
 * - Stress pattern estimation based on POS and word structure
 * - Synonym lookup
 * - Antonym lookup
 */
public final class PoetryDictionary {
    private PoetryDictionary() {}
    
    private static volatile boolean loaded = false;
    private static final Map<String, WordEntry> dictionary = new ConcurrentHashMap<>();
    
    // Cache for computed stress patterns
    private static final Map<String, int[]> stressCache = new ConcurrentHashMap<>();
    
    /**
     * Word entry from dictionary.
     */
    public static class WordEntry {
        public final String word;
        public final List<String> partsOfSpeech;
        public final List<String> synonyms;
        public final List<String> antonyms;
        public final int syllableCount;
        
        public WordEntry(String word, List<String> partsOfSpeech, List<String> synonyms, 
                        List<String> antonyms, int syllableCount) {
            this.word = word;
            this.partsOfSpeech = Collections.unmodifiableList(partsOfSpeech);
            this.synonyms = Collections.unmodifiableList(synonyms);
            this.antonyms = Collections.unmodifiableList(antonyms);
            this.syllableCount = syllableCount;
        }
        
        public boolean isNoun() {
            return partsOfSpeech.stream().anyMatch(p -> p.equalsIgnoreCase("Noun"));
        }
        
        public boolean isVerb() {
            return partsOfSpeech.stream().anyMatch(p -> p.equalsIgnoreCase("Verb"));
        }
        
        public boolean isAdjective() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Adjective") || p.equalsIgnoreCase("Adj"));
        }
        
        public boolean isAdverb() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Adverb") || p.equalsIgnoreCase("Adv"));
        }
        
        public boolean isPreposition() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Preposition") || p.equalsIgnoreCase("Prep"));
        }
        
        public boolean isConjunction() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Conjunction") || p.equalsIgnoreCase("Conj"));
        }
        
        public boolean isPronoun() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Pronoun") || p.equalsIgnoreCase("Pron"));
        }
        
        public boolean isDeterminer() {
            return partsOfSpeech.stream().anyMatch(p -> 
                p.equalsIgnoreCase("Determiner") || p.equalsIgnoreCase("Det") ||
                p.equalsIgnoreCase("Article"));
        }
        
        public String getPrimaryPOS() {
            return partsOfSpeech.isEmpty() ? "Unknown" : partsOfSpeech.get(0);
        }
    }
    
    /**
     * Ensure dictionary is loaded.
     */
    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (PoetryDictionary.class) {
            if (loaded) return;
            loadDictionary();
            loaded = true;
        }
    }
    
    /**
     * Load dictionary from resources.
     */
    private static void loadDictionary() {
        for (char c = 'a'; c <= 'z'; c++) {
            String path = "simple-english-dictionary/data/" + c + ".json";
            try (InputStream in = ResourceLoader.getResourceAsStream(path)) {
                if (in == null) continue;
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                parseJsonChunk(json);
            } catch (Throwable ignored) {
                // Fail-safe: skip malformed file
            }
        }
    }
    
    /**
     * Parse a JSON chunk and extract word entries.
     */
    private static void parseJsonChunk(String json) {
        if (json == null || json.isEmpty()) return;
        
        int idx = 0;
        final int len = json.length();
        
        while (idx < len) {
            // Find word key
            int keyStart = json.indexOf('"', idx);
            if (keyStart < 0 || keyStart + 1 >= len) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            
            String word = json.substring(keyStart + 1, keyEnd);
            idx = keyEnd + 1;
            
            // Find object start
            int objStart = json.indexOf('{', idx);
            if (objStart < 0) break;
            
            // Find matching close brace
            int depth = 1;
            int cursor = objStart + 1;
            while (cursor < len && depth > 0) {
                char ch = json.charAt(cursor);
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
                else if (ch == '"') {
                    // Skip string content
                    cursor++;
                    while (cursor < len && json.charAt(cursor) != '"') {
                        if (json.charAt(cursor) == '\\') cursor++;
                        cursor++;
                    }
                }
                cursor++;
            }
            
            int objEnd = cursor;
            if (depth != 0 || objEnd <= objStart) {
                idx = objStart + 1;
                continue;
            }
            
            String objSection = json.substring(objStart + 1, objEnd - 1);
            idx = objEnd;
            
            // Parse entry
            WordEntry entry = parseWordEntry(word, objSection);
            if (entry != null) {
                dictionary.put(word.toLowerCase(Locale.ROOT), entry);
            }
        }
    }
    
    /**
     * Parse a single word entry from its JSON object section.
     */
    private static WordEntry parseWordEntry(String word, String objSection) {
        List<String> partsOfSpeech = new ArrayList<>();
        List<String> synonyms = new ArrayList<>();
        List<String> antonyms = new ArrayList<>();
        
        // Extract POS from MEANINGS
        int meaningsPos = objSection.indexOf("\"MEANINGS\"");
        if (meaningsPos >= 0) {
            int braceStart = objSection.indexOf('{', meaningsPos);
            if (braceStart >= 0) {
                int braceEnd = findMatchingBrace(objSection, braceStart);
                if (braceEnd > braceStart) {
                    String meanings = objSection.substring(braceStart, braceEnd + 1);
                    // Extract POS from meaning entries like ["Noun", ...]
                    Pattern posPattern = Pattern.compile("\\[\"([A-Za-z]+)\"");
                    Matcher m = posPattern.matcher(meanings);
                    Set<String> posSet = new LinkedHashSet<>();
                    while (m.find()) {
                        String pos = m.group(1);
                        if (isValidPOS(pos)) {
                            posSet.add(pos);
                        }
                    }
                    partsOfSpeech.addAll(posSet);
                }
            }
        }
        
        // Extract synonyms
        synonyms.addAll(extractStringArray(objSection, "\"SYNONYMS\""));
        
        // Extract antonyms
        antonyms.addAll(extractStringArray(objSection, "\"ANTONYMS\""));
        
        int syllables = PoetryUtils.countSyllables(word);
        
        return new WordEntry(word, partsOfSpeech, synonyms, antonyms, syllables);
    }
    
    /**
     * Check if a string is a valid part of speech.
     */
    private static boolean isValidPOS(String pos) {
        return Set.of("Noun", "Verb", "Adjective", "Adverb", "Preposition", 
                     "Conjunction", "Pronoun", "Determiner", "Article",
                     "Interjection", "Numeral").contains(pos);
    }
    
    /**
     * Find matching closing brace.
     */
    private static int findMatchingBrace(String s, int start) {
        if (start < 0 || start >= s.length() || s.charAt(start) != '{') return -1;
        int depth = 1;
        int i = start + 1;
        while (i < s.length() && depth > 0) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '"') {
                i++;
                while (i < s.length() && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\') i++;
                    i++;
                }
            }
            i++;
        }
        return depth == 0 ? i - 1 : -1;
    }
    
    /**
     * Extract a string array from JSON.
     */
    private static List<String> extractStringArray(String section, String key) {
        List<String> result = new ArrayList<>();
        int pos = section.indexOf(key);
        if (pos < 0) return result;
        
        int bracketStart = section.indexOf('[', pos);
        int bracketEnd = (bracketStart >= 0) ? section.indexOf(']', bracketStart) : -1;
        if (bracketStart < 0 || bracketEnd < 0 || bracketEnd <= bracketStart) return result;
        
        String array = section.substring(bracketStart + 1, bracketEnd);
        int idx = 0;
        while (idx < array.length()) {
            int q1 = array.indexOf('"', idx);
            if (q1 < 0 || q1 + 1 >= array.length()) break;
            int q2 = array.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String s = array.substring(q1 + 1, q2).trim();
            if (!s.isEmpty()) result.add(s);
            idx = q2 + 1;
        }
        return result;
    }
    
    /**
     * Look up a word in the dictionary.
     */
    public static WordEntry lookup(String word) {
        if (word == null) return null;
        String key = word.toLowerCase(Locale.ROOT);
        WordEntry cached = dictionary.get(key);
        if (cached != null) return cached;
        if (NativeAccess.dictionaryReady()) {
            NativeAccess.NativeDictionaryEntry nativeEntry = NativeAccess.dictionaryLookup(word);
            if (nativeEntry == null) return null;
            WordEntry entry = new WordEntry(word, nativeEntry.partsOfSpeech, nativeEntry.synonyms,
                    nativeEntry.antonyms, PoetryUtils.countSyllables(word));
            dictionary.put(key, entry);
            return entry;
        }
        ensureLoaded();
        return dictionary.get(key);
    }
    
    /**
     * Check if a word exists in the dictionary.
     */
    public static boolean contains(String word) {
        if (word == null) return false;
        String key = word.toLowerCase(Locale.ROOT);
        if (dictionary.containsKey(key)) return true;
        if (NativeAccess.dictionaryReady()) {
            return NativeAccess.dictionaryContains(word);
        }
        ensureLoaded();
        return dictionary.containsKey(key);
    }
    
    /**
     * Get the stress pattern for a word.
     * Returns array of 0 (unstressed) and 1 (stressed).
     */
    public static int[] getStressPattern(String word) {
        if (word == null || word.isEmpty()) return new int[0];
        
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Check cache first
        int[] cached = stressCache.get(lower);
        if (cached != null) return cached;

        WordEntry entry = lookup(word);
        int syllables = entry != null ? entry.syllableCount : PoetryUtils.countSyllables(word);
        if (syllables <= 0) return new int[0];
        if (syllables == 1) {
            int[] pattern = new int[]{1}; // Monosyllables are stressed by default
            // But function words are typically unstressed
            if (isFunctionWord(lower)) {
                pattern = new int[]{0};
            }
            stressCache.put(lower, pattern);
            return pattern;
        }

        int[] pattern = computeStressPattern(lower, syllables, entry);
        stressCache.put(lower, pattern);
        return pattern;
    }
    
    /**
     * Compute stress pattern based on word structure and POS.
     */
    private static int[] computeStressPattern(String word, int syllables, WordEntry entry) {
        int[] pattern = new int[syllables];
        
        // Use POS-based rules if available
        if (entry != null) {
            if (entry.isVerb() && syllables == 2) {
                // Two-syllable verbs often stress the second syllable
                pattern[1] = 1;
                return pattern;
            }
            if (entry.isNoun() && syllables == 2) {
                // Two-syllable nouns often stress the first syllable
                pattern[0] = 1;
                return pattern;
            }
            if (entry.isAdjective() && syllables == 2) {
                // Two-syllable adjectives typically stress the first
                pattern[0] = 1;
                return pattern;
            }
        }
        
        // Suffix-based stress rules
        if (word.endsWith("tion") || word.endsWith("sion")) {
            // Stress on syllable before -tion/-sion
            if (syllables >= 2) pattern[syllables - 2] = 1;
            return pattern;
        }
        if (word.endsWith("ic") || word.endsWith("ical")) {
            // Stress on syllable before -ic
            if (syllables >= 2) pattern[syllables - 2] = 1;
            return pattern;
        }
        if (word.endsWith("ity") || word.endsWith("ety") || word.endsWith("ogy") || word.endsWith("graphy")) {
            // Stress on ante-penultimate
            if (syllables >= 3) pattern[syllables - 3] = 1;
            else if (syllables >= 2) pattern[syllables - 2] = 1;
            return pattern;
        }
        if (word.endsWith("ious") || word.endsWith("eous") || word.endsWith("uous")) {
            if (syllables >= 2) pattern[syllables - 2] = 1;
            return pattern;
        }
        if (word.endsWith("ive") || word.endsWith("ous") || word.endsWith("al")) {
            // Usually stress penultimate or ante-penultimate
            if (syllables >= 2) pattern[syllables - 2] = 1;
            return pattern;
        }
        if (word.endsWith("ly") && syllables > 2) {
            // -ly doesn't change stress; stress the root
            pattern[0] = 1;
            return pattern;
        }
        if (word.endsWith("ness") || word.endsWith("less") || word.endsWith("ful") || 
            word.endsWith("ment") || word.endsWith("er") || word.endsWith("or")) {
            // Stress on root (usually first syllable)
            pattern[0] = 1;
            return pattern;
        }
        if (word.endsWith("ing") || word.endsWith("ed") || word.endsWith("es") || word.endsWith("s")) {
            // Inflectional endings don't change stress
            pattern[0] = 1;
            return pattern;
        }
        
        // Prefix-based rules
        if (word.startsWith("un") || word.startsWith("re") || word.startsWith("de") ||
            word.startsWith("pre") || word.startsWith("mis") || word.startsWith("dis")) {
            // Prefixes are usually unstressed; stress the root
            if (syllables >= 2) pattern[1] = 1;
            else pattern[0] = 1;
            return pattern;
        }
        if (word.startsWith("be") || word.startsWith("a") && word.length() > 2 && 
            !isVowel(word.charAt(1))) {
            // be-, a- prefixes unstressed
            if (syllables >= 2) pattern[1] = 1;
            return pattern;
        }
        
        // Default: stress first syllable (most common in English)
        pattern[0] = 1;
        
        // Add secondary stress for longer words
        if (syllables >= 4) {
            pattern[2] = 1;
        }
        
        return pattern;
    }
    
    private static boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }
    
    /**
     * Check if a word is a function word (typically unstressed).
     */
    public static boolean isFunctionWord(String word) {
        if (word == null) return false;
        String lower = word.toLowerCase(Locale.ROOT);
        return FUNCTION_WORDS.contains(lower);
    }
    
    // Common function words (determiners, prepositions, conjunctions, auxiliaries, pronouns)
    private static final Set<String> FUNCTION_WORDS = Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(
        // Determiners/Articles
        "a", "an", "the", "this", "that", "these", "those", "my", "your", "his", "her", 
        "its", "our", "their", "some", "any", "no", "every", "each", "all", "both",
        "few", "many", "much", "most", "other", "another", "such", "what", "which",
        // Prepositions
        "at", "by", "for", "from", "in", "of", "on", "to", "with", "as", "into",
        "through", "during", "before", "after", "above", "below", "between", "under",
        "over", "out", "up", "down", "off", "about", "against", "among", "around",
        "behind", "beside", "besides", "beyond", "despite", "except", "near", "since",
        "than", "toward", "towards", "upon", "within", "without", "along", "across",
        // Conjunctions
        "and", "or", "but", "nor", "so", "yet", "if", "when", "while", "although",
        "because", "unless", "until", "whether", "though", "whereas", "wherever",
        // Pronouns
        "i", "me", "we", "us", "you", "he", "him", "she", "her", "it", "they", "them",
        "who", "whom", "whose", "which", "that", "what", "whoever", "whatever",
        "myself", "yourself", "himself", "herself", "itself", "ourselves", "themselves",
        // Auxiliaries
        "am", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "having", "do", "does", "did", "doing",
        "will", "would", "shall", "should", "may", "might", "must", "can", "could",
        // Other function words
        "not", "very", "too", "also", "just", "only", "even", "still", "already",
        "now", "then", "here", "there", "where", "how", "why"
    )));
    
    /**
     * Get synonyms for a word.
     */
    public static List<String> getSynonyms(String word) {
        WordEntry entry = lookup(word);
        return entry != null ? entry.synonyms : Collections.emptyList();
    }
    
    /**
     * Get antonyms for a word.
     */
    public static List<String> getAntonyms(String word) {
        WordEntry entry = lookup(word);
        return entry != null ? entry.antonyms : Collections.emptyList();
    }
    
    /**
     * Get primary part of speech for a word.
     */
    public static String getPOS(String word) {
        WordEntry entry = lookup(word);
        return entry != null ? entry.getPrimaryPOS() : "Unknown";
    }
    
    /**
     * Get all words in the dictionary.
     */
    public static Set<String> getAllWords() {
        ensureLoaded();
        return Collections.unmodifiableSet(dictionary.keySet());
    }
    
    /**
     * Get dictionary size.
     */
    public static int size() {
        Integer nativeSize = NativeAccess.dictionarySize();
        if (nativeSize != null && nativeSize > 0) return nativeSize;
        ensureLoaded();
        return dictionary.size();
    }
}
