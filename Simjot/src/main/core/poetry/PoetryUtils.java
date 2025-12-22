package main.core.poetry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PoetryUtils - Overhauled poetry utilities with improved accuracy.
 * 
 * Features:
 * - Enhanced syllable counting with exception handling
 * - Multiple rhyme types (exact, near, slant)
 * - Phonetic similarity detection
 * - Stress pattern estimation
 */
public final class PoetryUtils {
    private PoetryUtils() {}

    private static final Pattern VOWEL_GROUPS = Pattern.compile("[aeiouy]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z']+");
    private static final Set<String> SILENT_E_EXCEPTIONS = new HashSet<>(Arrays.asList(
        "be", "he", "me", "we", "she", "the", "cafe", "forte", "finale", "recipe",
        "adobe", "coyote", "karate", "maybe", "sesame", "simile", "apostrophe"
    ));
    
    // Known syllable counts for common irregular words
    private static final Map<String, Integer> SYLLABLE_OVERRIDES = new HashMap<>();
    static {
        SYLLABLE_OVERRIDES.put("every", 3); SYLLABLE_OVERRIDES.put("evening", 3);
        SYLLABLE_OVERRIDES.put("different", 3); SYLLABLE_OVERRIDES.put("interesting", 4);
        SYLLABLE_OVERRIDES.put("beautiful", 3); SYLLABLE_OVERRIDES.put("favorite", 3);
        SYLLABLE_OVERRIDES.put("family", 3); SYLLABLE_OVERRIDES.put("chocolate", 3);
        SYLLABLE_OVERRIDES.put("comfortable", 4); SYLLABLE_OVERRIDES.put("vegetable", 4);
        SYLLABLE_OVERRIDES.put("camera", 3); SYLLABLE_OVERRIDES.put("actually", 4);
        SYLLABLE_OVERRIDES.put("generally", 4); SYLLABLE_OVERRIDES.put("naturally", 4);
        SYLLABLE_OVERRIDES.put("practically", 4); SYLLABLE_OVERRIDES.put("literally", 4);
        SYLLABLE_OVERRIDES.put("probably", 3); SYLLABLE_OVERRIDES.put("definitely", 4);
        SYLLABLE_OVERRIDES.put("especially", 5); SYLLABLE_OVERRIDES.put("unfortunately", 5);
        SYLLABLE_OVERRIDES.put("immediately", 5); SYLLABLE_OVERRIDES.put("particularly", 5);
        SYLLABLE_OVERRIDES.put("area", 3); SYLLABLE_OVERRIDES.put("idea", 3);
        SYLLABLE_OVERRIDES.put("real", 1); SYLLABLE_OVERRIDES.put("really", 2);
        SYLLABLE_OVERRIDES.put("being", 2); SYLLABLE_OVERRIDES.put("doing", 2);
        SYLLABLE_OVERRIDES.put("going", 2); SYLLABLE_OVERRIDES.put("saying", 2);
        SYLLABLE_OVERRIDES.put("having", 2); SYLLABLE_OVERRIDES.put("making", 2);
        SYLLABLE_OVERRIDES.put("taking", 2); SYLLABLE_OVERRIDES.put("coming", 2);
        SYLLABLE_OVERRIDES.put("getting", 2); SYLLABLE_OVERRIDES.put("looking", 2);
        SYLLABLE_OVERRIDES.put("nothing", 2); SYLLABLE_OVERRIDES.put("something", 2);
        SYLLABLE_OVERRIDES.put("everything", 4); SYLLABLE_OVERRIDES.put("everyone", 3);
        SYLLABLE_OVERRIDES.put("someone", 2); SYLLABLE_OVERRIDES.put("anyone", 3);
        SYLLABLE_OVERRIDES.put("ourselves", 2); SYLLABLE_OVERRIDES.put("themselves", 2);
        SYLLABLE_OVERRIDES.put("fire", 1); SYLLABLE_OVERRIDES.put("desire", 2);
        SYLLABLE_OVERRIDES.put("hour", 1); SYLLABLE_OVERRIDES.put("flower", 2);
        SYLLABLE_OVERRIDES.put("power", 2); SYLLABLE_OVERRIDES.put("tower", 2);
        SYLLABLE_OVERRIDES.put("poem", 2); SYLLABLE_OVERRIDES.put("poet", 2);
        SYLLABLE_OVERRIDES.put("poetry", 3); SYLLABLE_OVERRIDES.put("quiet", 2);
        SYLLABLE_OVERRIDES.put("science", 2); SYLLABLE_OVERRIDES.put("patient", 2);
        SYLLABLE_OVERRIDES.put("ancient", 2); SYLLABLE_OVERRIDES.put("ocean", 2);
        SYLLABLE_OVERRIDES.put("heaven", 2); SYLLABLE_OVERRIDES.put("seven", 2);
        SYLLABLE_OVERRIDES.put("even", 2); SYLLABLE_OVERRIDES.put("given", 2);
        SYLLABLE_OVERRIDES.put("driven", 2); SYLLABLE_OVERRIDES.put("written", 2);
        SYLLABLE_OVERRIDES.put("rhythm", 2); SYLLABLE_OVERRIDES.put("prism", 2);
        SYLLABLE_OVERRIDES.put("naive", 2); SYLLABLE_OVERRIDES.put("cafe", 2);
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String l : text.split("\r?\n")) lines.add(l);
        return lines;
    }

    public static List<String> wordsInLine(String line) {
        List<String> words = new ArrayList<>();
        if (line == null) return words;
        Matcher m = WORD_PATTERN.matcher(line);
        while (m.find()) words.add(m.group());
        return words;
    }

    public static String endWord(String line) {
        List<String> words = wordsInLine(line);
        return words.isEmpty() ? null : words.get(words.size()-1);
    }

    /**
     * Enhanced syllable counting with multiple heuristics.
     */
    public static int countSyllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
        if (w.isEmpty()) return 0;
        
        // Check overrides first
        Integer override = SYLLABLE_OVERRIDES.get(w);
        if (override != null) return override;
        
        int count = 0;
        boolean prevVowel = false;
        int len = w.length();
        
        for (int i = 0; i < len; i++) {
            char c = w.charAt(i);
            boolean isVowel = isVowel(c);
            
            if (isVowel && !prevVowel) {
                count++;
            }
            prevVowel = isVowel;
        }
        
        // Silent 'e' at end (but not words like "the", "be", etc.)
        if (w.endsWith("e") && len > 2 && !SILENT_E_EXCEPTIONS.contains(w)) {
            char prev = w.charAt(len - 2);
            if (!isVowel(prev) && prev != 'l') {
                count--;
            }
        }
        
        // Words ending in "le" preceded by consonant add syllable
        if (len > 2 && w.endsWith("le") && !isVowel(w.charAt(len - 3))) {
            count++;
        }
        
        // "-ed" endings: silent if preceded by t or d
        if (w.endsWith("ed") && len > 3) {
            char prev = w.charAt(len - 3);
            if (prev != 't' && prev != 'd') {
                count--;
            }
        }
        
        // Diphthongs and special combinations that reduce count
        if (w.contains("ia") || w.contains("io") || w.contains("iu")) {
            // Usually separate syllables - already counted
        }
        if (w.contains("ious") || w.contains("eous")) count--;
        if (w.contains("tion") || w.contains("sion")) count = Math.max(1, count);
        
        // Common suffixes that add syllables
        if (w.endsWith("ism")) count = Math.max(count, 2);
        if (w.endsWith("ity")) count = Math.max(count, 2);
        
        return Math.max(1, count);
    }
    
    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    }

    public static int countSyllablesInLine(String line) {
        int total = 0;
        for (String w : wordsInLine(line)) total += countSyllables(w);
        return total;
    }

    /**
     * Get the primary rhyme key (last stressed vowel + following sounds).
     */
    public static String rhymeKey(String word) {
        if (word == null) return null;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return null;
        
        // Find last vowel group
        Matcher m = VOWEL_GROUPS.matcher(w);
        int start = -1;
        while (m.find()) { start = m.start(); }
        if (start == -1) return w.length() >= 2 ? w.substring(Math.max(0, w.length()-2)) : w;
        return w.substring(start);
    }
    
    /**
     * Get a near-rhyme key (more lenient matching).
     */
    public static String nearRhymeKey(String word) {
        if (word == null) return null;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return null;
        
        // Just the last 2-3 characters for near rhymes
        return w.length() >= 3 ? w.substring(w.length() - 3) : w;
    }
    
    /**
     * Check if two words rhyme (exact match).
     */
    public static boolean rhymes(String word1, String word2) {
        if (word1 == null || word2 == null) return false;
        String key1 = rhymeKey(word1);
        String key2 = rhymeKey(word2);
        return key1 != null && key1.equals(key2) && 
               !word1.equalsIgnoreCase(word2);
    }
    
    /**
     * Check if two words are near-rhymes (slant rhymes).
     */
    public static boolean nearRhymes(String word1, String word2) {
        if (word1 == null || word2 == null) return false;
        String key1 = nearRhymeKey(word1);
        String key2 = nearRhymeKey(word2);
        return key1 != null && key2 != null && 
               (key1.equals(key2) || shareEnding(key1, key2, 2)) &&
               !word1.equalsIgnoreCase(word2);
    }
    
    private static boolean shareEnding(String s1, String s2, int chars) {
        if (s1.length() < chars || s2.length() < chars) return false;
        return s1.substring(s1.length() - chars).equals(s2.substring(s2.length() - chars));
    }
    
    /**
     * Estimate the stress pattern of a word (0 = unstressed, 1 = stressed).
     * Returns a simplified pattern based on syllable count.
     */
    public static int[] estimateStressPattern(String word) {
        int syllables = countSyllables(word);
        if (syllables <= 0) return new int[0];
        if (syllables == 1) return new int[]{1};
        if (syllables == 2) return new int[]{1, 0}; // Most 2-syllable words stress first
        
        // Common patterns for longer words
        int[] pattern = new int[syllables];
        for (int i = 0; i < syllables; i++) {
            // Alternate with first syllable stressed
            pattern[i] = (i % 2 == 0) ? 1 : 0;
        }
        return pattern;
    }
    
    /**
     * Check if a line follows iambic pattern (unstressed-stressed).
     */
    public static boolean isIambic(String line) {
        List<String> words = wordsInLine(line);
        if (words.isEmpty()) return false;
        
        List<Integer> pattern = new ArrayList<>();
        for (String w : words) {
            for (int s : estimateStressPattern(w)) pattern.add(s);
        }
        
        // Check for iambic: 0-1-0-1-0-1...
        for (int i = 0; i < pattern.size(); i++) {
            int expected = (i % 2 == 0) ? 0 : 1;
            if (pattern.get(i) != expected) return false;
        }
        return true;
    }
    
    /**
     * Check if a line follows trochaic pattern (stressed-unstressed).
     */
    public static boolean isTrochaic(String line) {
        List<String> words = wordsInLine(line);
        if (words.isEmpty()) return false;
        
        List<Integer> pattern = new ArrayList<>();
        for (String w : words) {
            for (int s : estimateStressPattern(w)) pattern.add(s);
        }
        
        // Check for trochaic: 1-0-1-0-1-0...
        for (int i = 0; i < pattern.size(); i++) {
            int expected = (i % 2 == 0) ? 1 : 0;
            if (pattern.get(i) != expected) return false;
        }
        return true;
    }
}
