/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.spelling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import main.infrastructure.ffi.NativeAccess;

/**
 * SpellCheckEngine
 * A comprehensive spell checking and autocorrect engine.
 */
public class SpellCheckEngine {
    
    private static SpellCheckEngine instance;
    private final Set<String> dictionary = ConcurrentHashMap.newKeySet();
    private final Set<String> userDictionary = ConcurrentHashMap.newKeySet();
    private final Map<String, String> autocorrectMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> wordFrequency = new ConcurrentHashMap<>();
    
    // LRU-bounded caches to limit memory usage
    private static final int SPELL_CACHE_SIZE = 2048;
    private static final int SUGGESTION_CACHE_SIZE = 512;
    private static final int MAX_SUGGESTION_WORD_LENGTH = 32;
    private static final int MAX_EDIT2_WORD_LENGTH = 8;
    
    private final Map<String, Boolean> spellCheckCache = Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                return size() > SPELL_CACHE_SIZE;
            }
        });
    private final Map<String, List<String>> suggestionCache = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) {
                return size() > SUGGESTION_CACHE_SIZE;
            }
        });
    
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z']+");
    private final boolean useNativeSpell;
    private final boolean useNativeDictionary;
    
    private SpellCheckEngine() {
        useNativeSpell = NativeAccess.spellReady();
        useNativeDictionary = !useNativeSpell && NativeAccess.dictionaryReady();
        if (!useNativeSpell && !useNativeDictionary) {
            loadDefaultDictionary();
        }
        loadWordFrequencies();
        loadAutocorrectMappings();
    }
    
    public static synchronized SpellCheckEngine get() {
        if (instance == null) instance = new SpellCheckEngine();
        return instance;
    }
    
    private void loadDefaultDictionary() {
        // Load embedded dictionary
        for (String word : WordDatabase.getCommonWords()) {
            dictionary.add(word.toLowerCase(Locale.ROOT).trim());
        }
    }

    private void loadWordFrequencies() {
        String[] highFreq = {"the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with"};
        int freq = 10000;
        for (String w : highFreq) wordFrequency.put(w, freq--);
    }
    
    private void loadAutocorrectMappings() {
        for (String[] pair : AutocorrectDatabase.getMappings()) {
            if (pair.length == 2) autocorrectMap.put(pair[0], pair[1]);
        }
    }
    
    public boolean isCorrect(String word) {
        if (word == null || word.length() <= 1) return true;
        String lower = word.toLowerCase(Locale.ROOT);
        Boolean cached = spellCheckCache.get(lower);
        if (cached != null) return cached;
        
        boolean correct;
        if (useNativeSpell) {
            // Native spell check handles word forms internally
            Boolean nativeResult = NativeAccess.spellContains(lower);
            correct = nativeResult != null ? nativeResult : isInAnyDictionaryJava(lower);
        } else {
            correct = isInAnyDictionaryJava(lower);
            if (!correct) correct = checkWordForms(lower);
        }
        
        // Also check user dictionary (Java-side)
        if (!correct && userDictionary.contains(lower)) {
            correct = true;
        }
        
        spellCheckCache.put(lower, correct);
        return correct;
    }
    
    private boolean checkWordForms(String lower) {
        // Possessives
        if (lower.endsWith("'s") && lower.length() > 2) {
            String base = lower.substring(0, lower.length() - 2);
            if (isInAnyDictionaryJava(base)) return true;
        }
        // Plurals
        if (lower.endsWith("s") && lower.length() > 2) {
            String base = lower.substring(0, lower.length() - 1);
            if (isInAnyDictionaryJava(base)) return true;
        }
        if (lower.endsWith("es") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (isInAnyDictionaryJava(base)) return true;
        }
        // Past tense
        if (lower.endsWith("ed") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (isInAnyDictionaryJava(base)) return true;
            base = lower.substring(0, lower.length() - 1);
            if (isInAnyDictionaryJava(base)) return true;
        }
        // Present participle
        if (lower.endsWith("ing") && lower.length() > 4) {
            String base = lower.substring(0, lower.length() - 3);
            if (isInAnyDictionaryJava(base)) return true;
            if (isInAnyDictionaryJava(base + "e")) return true;
        }
        // Adverbs
        if (lower.endsWith("ly") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (isInAnyDictionaryJava(base)) return true;
        }
        return false;
    }
    
    public List<String> getSuggestions(String word) {
        if (word == null || word.isEmpty()) return Collections.emptyList();
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.length() > MAX_SUGGESTION_WORD_LENGTH || !isSuggestionCandidate(lower)) {
            suggestionCache.put(lower, Collections.emptyList());
            return Collections.emptyList();
        }

        List<String> cached = suggestionCache.get(lower);
        if (cached != null) return cached;
        
        // Try native suggestions first
        if (useNativeSpell) {
            List<String> nativeSuggestions = NativeAccess.spellSuggestions(lower, 5);
            if (nativeSuggestions != null && !nativeSuggestions.isEmpty()) {
                suggestionCache.put(lower, nativeSuggestions);
                return nativeSuggestions;
            }
        }
        
        // Fallback to Java autocorrect map
        String autocorrect = autocorrectMap.get(lower);
        if (autocorrect != null) {
            List<String> result = Collections.singletonList(autocorrect);
            suggestionCache.put(lower, result);
            return result;
        }
        
        // Fallback to Java edit-distance suggestions
        Set<String> suggestions = new LinkedHashSet<>();
        addEditDistance1Candidates(lower, suggestions);
        if (suggestions.size() < 3 && lower.length() <= MAX_EDIT2_WORD_LENGTH) addEditDistance2Candidates(lower, suggestions);
        
        List<String> result = suggestions.stream()
            .filter(s -> !s.equals(lower))
            .sorted((a, b) -> {
                int freqA = wordFrequency.getOrDefault(a, 0);
                int freqB = wordFrequency.getOrDefault(b, 0);
                if (freqA != freqB) return freqB - freqA;
                return levenshtein(lower, a) - levenshtein(lower, b);
            })
            .limit(5)
            .collect(Collectors.toList());
        
        suggestionCache.put(lower, result);
        return result;
    }
    
    public String getAutocorrect(String word) {
        if (word == null) return null;
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Try native autocorrect first
        if (useNativeSpell) {
            String nativeCorrection = NativeAccess.spellBestCorrection(lower);
            if (nativeCorrection != null && !nativeCorrection.isEmpty()) {
                return nativeCorrection;
            }
        }
        
        // Fallback to Java map
        return autocorrectMap.get(lower);
    }
    
    public void addToUserDictionary(String word) {
        if (word != null && !word.isEmpty()) {
            String lower = word.toLowerCase(Locale.ROOT);
            userDictionary.add(lower);
            spellCheckCache.remove(lower);
            // Also add to native user dictionary
            if (useNativeSpell) {
                NativeAccess.addUserDictionaryWord(lower);
            }
        }
    }
    
    public List<SpellError> checkText(String text) {
        List<SpellError> errors = new ArrayList<>();
        if (text == null || text.isEmpty()) return errors;
        
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (!isCorrect(word)) {
                errors.add(new SpellError(word, matcher.start(), matcher.end(), getSuggestions(word)));
            }
        }
        return errors;
    }
    
    public void clearCache() {
        spellCheckCache.clear();
        suggestionCache.clear();
    }
    
    private void addEditDistance1Candidates(String word, Set<String> suggestions) {
        // Deletions
        for (int i = 0; i < word.length(); i++) {
            String c = word.substring(0, i) + word.substring(i + 1);
            if (isInMainDictionary(c)) suggestions.add(c);
        }
        // Transpositions
        for (int i = 0; i < word.length() - 1; i++) {
            String c = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2);
            if (isInMainDictionary(c)) suggestions.add(c);
        }
        // Replacements
        for (int i = 0; i < word.length(); i++) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                if (ch != word.charAt(i)) {
                    String c = word.substring(0, i) + ch + word.substring(i + 1);
                    if (isInMainDictionary(c)) suggestions.add(c);
                }
            }
        }
        // Insertions
        for (int i = 0; i <= word.length(); i++) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                String c = word.substring(0, i) + ch + word.substring(i);
                if (isInMainDictionary(c)) suggestions.add(c);
            }
        }
    }
    
    private void addEditDistance2Candidates(String word, Set<String> suggestions) {
        if (word.length() < 4) return;
        Set<String> ed1 = new HashSet<>();
        for (int i = 0; i < word.length(); i++) ed1.add(word.substring(0, i) + word.substring(i + 1));
        for (int i = 0; i < word.length() - 1; i++) 
            ed1.add(word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2));
        
        for (String w : ed1) {
            for (int i = 0; i < w.length(); i++) {
                String c = w.substring(0, i) + w.substring(i + 1);
                if (isInMainDictionary(c)) suggestions.add(c);
            }
        }
    }

    private boolean isInMainDictionary(String word) {
        if (word == null || word.isEmpty()) return false;
        if (useNativeDictionary) {
            return NativeAccess.dictionaryContains(word);
        }
        return dictionary.contains(word);
    }

    private boolean isSuggestionCandidate(String lower) {
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '\'') {
                if (i == 0 || i == lower.length() - 1) return false;
                continue;
            }
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private boolean isInAnyDictionaryJava(String word) {
        if (word == null || word.isEmpty()) return false;
        if (userDictionary.contains(word)) return true;
        return isInMainDictionary(word);
    }
    
    private int levenshtein(String a, String b) {
        // Try native optimized Levenshtein first
        int nativeResult = NativeAccess.nativeLevenshtein(a, b);
        if (nativeResult >= 0) return nativeResult;
        
        // Try legacy text utils
        Integer legacyResult = NativeAccess.textLevenshtein(a, b);
        if (legacyResult != null && legacyResult >= 0) return legacyResult;
        
        // Java fallback with O(n) memory.
        int m = a.length();
        int n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }
    
    /**
     * Use native fuzzy matching for spell suggestions when available.
     */
    public boolean fuzzyMatch(String text, String query) {
        Boolean result = NativeAccess.textFuzzyMatch(text, query);
        if (result != null) return result;
        // Java fallback: simple contains check
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    
    /**
     * Get fuzzy match score using native implementation.
     */
    public int fuzzyScore(String text, String query) {
        Integer score = NativeAccess.textFuzzyScore(text, query);
        if (score != null) return score;
        // Java fallback: simple similarity
        return 100 - levenshtein(text.toLowerCase(Locale.ROOT), query.toLowerCase(Locale.ROOT)) * 10;
    }
    
    public static class SpellError {
        public final String word;
        public final int start;
        public final int end;
        public final List<String> suggestions;
        
        public SpellError(String word, int start, int end, List<String> suggestions) {
            this.word = word;
            this.start = start;
            this.end = end;
            this.suggestions = suggestions;
        }
    }
}
