package main.core.spelling;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SpellCheckEngine - A comprehensive spell checking and autocorrect engine.
 */
public class SpellCheckEngine {
    
    private static SpellCheckEngine instance;
    private final Set<String> dictionary = ConcurrentHashMap.newKeySet();
    private final Set<String> userDictionary = ConcurrentHashMap.newKeySet();
    private final Map<String, String> autocorrectMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> wordFrequency = new ConcurrentHashMap<>();
    private final Map<String, Boolean> spellCheckCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> suggestionCache = new ConcurrentHashMap<>();
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z']+");
    
    private SpellCheckEngine() {
        loadDefaultDictionary();
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
        // High frequency words for ranking
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
        
        boolean correct = dictionary.contains(lower) || userDictionary.contains(lower);
        
        // Check word forms (possessives, plurals, verb forms)
        if (!correct) correct = checkWordForms(lower);
        
        spellCheckCache.put(lower, correct);
        return correct;
    }
    
    private boolean checkWordForms(String lower) {
        // Possessives
        if (lower.endsWith("'s") && lower.length() > 2) {
            String base = lower.substring(0, lower.length() - 2);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
        }
        // Plurals
        if (lower.endsWith("s") && lower.length() > 2) {
            String base = lower.substring(0, lower.length() - 1);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
        }
        if (lower.endsWith("es") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
        }
        // Past tense
        if (lower.endsWith("ed") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
            base = lower.substring(0, lower.length() - 1);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
        }
        // Present participle
        if (lower.endsWith("ing") && lower.length() > 4) {
            String base = lower.substring(0, lower.length() - 3);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
            if (dictionary.contains(base + "e") || userDictionary.contains(base + "e")) return true;
        }
        // Adverbs
        if (lower.endsWith("ly") && lower.length() > 3) {
            String base = lower.substring(0, lower.length() - 2);
            if (dictionary.contains(base) || userDictionary.contains(base)) return true;
        }
        return false;
    }
    
    public List<String> getSuggestions(String word) {
        if (word == null || word.isEmpty()) return Collections.emptyList();
        String lower = word.toLowerCase(Locale.ROOT);
        
        List<String> cached = suggestionCache.get(lower);
        if (cached != null) return cached;
        
        String autocorrect = autocorrectMap.get(lower);
        if (autocorrect != null) {
            List<String> result = Collections.singletonList(autocorrect);
            suggestionCache.put(lower, result);
            return result;
        }
        
        Set<String> suggestions = new LinkedHashSet<>();
        addEditDistance1Candidates(lower, suggestions);
        if (suggestions.size() < 3) addEditDistance2Candidates(lower, suggestions);
        
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
        return word == null ? null : autocorrectMap.get(word.toLowerCase(Locale.ROOT));
    }
    
    public void addToUserDictionary(String word) {
        if (word != null && !word.isEmpty()) {
            String lower = word.toLowerCase(Locale.ROOT);
            userDictionary.add(lower);
            spellCheckCache.remove(lower);
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
            if (dictionary.contains(c)) suggestions.add(c);
        }
        // Transpositions
        for (int i = 0; i < word.length() - 1; i++) {
            String c = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2);
            if (dictionary.contains(c)) suggestions.add(c);
        }
        // Replacements
        for (int i = 0; i < word.length(); i++) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                if (ch != word.charAt(i)) {
                    String c = word.substring(0, i) + ch + word.substring(i + 1);
                    if (dictionary.contains(c)) suggestions.add(c);
                }
            }
        }
        // Insertions
        for (int i = 0; i <= word.length(); i++) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                String c = word.substring(0, i) + ch + word.substring(i);
                if (dictionary.contains(c)) suggestions.add(c);
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
                if (dictionary.contains(c)) suggestions.add(c);
            }
        }
    }
    
    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
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
