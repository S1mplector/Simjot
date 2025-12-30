package main.core.poetry;

import main.infrastructure.ffi.NativeAccess;

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
 * Utility class for poetry analysis and text processing.
 * 
 * <p>This class provides a wide range of poetry-related utilities including enhanced
 * syllable counting, rhyme detection, stress pattern estimation, and text analysis.
 * It uses multiple heuristics and exception handling to provide accurate results
 * for English poetry analysis.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Enhanced Syllable Counting</strong> - Multiple algorithms with exception handling</li>
 *   <li><strong>Rhyme Detection</strong> - Exact, near, and slant rhyme matching</li>
 *   <li><strong>Stress Pattern Analysis</strong> - Iambic and trochaic pattern detection</li>
 *   <li><strong>Text Processing</strong> - Line splitting, word extraction, and end-word detection</li>
 *   <li><strong>Phonetic Analysis</strong> - Rhyme key generation and similarity detection</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Count syllables in a line
 * String line = "The sun sets slowly on the horizon";
 * int syllables = PoetryUtils.countSyllablesInLine(line);
 * 
 * // Check for rhymes
 * boolean rhymes = PoetryUtils.rhymes("day", "say");
 * 
 * // Detect meter pattern
 * boolean iambic = PoetryUtils.isIambic("To be or not to be");
 * }</pre>
 * 
 * @author Simjot Development Team
 * @since 1.0.0
 */
public final class PoetryUtils {
    
    /**
     * Regular expression pattern for matching vowel groups in words.
     * Used for syllable counting algorithms.
     */
    private static final Pattern VOWEL_GROUPS = Pattern.compile("[aeiouy]+", Pattern.CASE_INSENSITIVE);
    
    /**
     * Regular expression pattern for extracting words from text.
     * Matches alphabetic characters and apostrophes only.
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z']+");
    
    /**
     * Set of words that are exceptions to the silent 'e' rule.
     * These words keep the final 'e' sound even though it would normally be silent.
     */
    private static final Set<String> SILENT_E_EXCEPTIONS = new HashSet<>(Arrays.asList(
        "be", "he", "me", "we", "she", "the", "cafe", "forte", "finale", "recipe",
        "adobe", "coyote", "karate", "maybe", "sesame", "simile", "apostrophe"
    ));
    
    /**
     * Known syllable counts for common irregular words.
     * This map provides overrides for words that don't follow standard syllable rules.
     */
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

    /** Private constructor to prevent instantiation of utility class. */
    private PoetryUtils() {}

    /**
     * Splits text into individual lines, handling both Unix and Windows line endings.
     * 
     * @param text The text to split into lines. May be null or empty.
     * @return A list of lines. Empty if input is null or empty.
     */
    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String l : text.split("\r?\n")) lines.add(l);
        return lines;
    }

    /**
     * Extracts all words from a line of text.
     * 
     * <p>Words are defined as sequences of alphabetic characters and apostrophes.
     * Punctuation and other symbols are ignored.</p>
     * 
     * @param line The line to extract words from. May be null.
     * @return A list of words found in the line. Empty if input is null.
     */
    public static List<String> wordsInLine(String line) {
        List<String> words = new ArrayList<>();
        if (line == null) return words;
        Matcher m = WORD_PATTERN.matcher(line);
        while (m.find()) words.add(m.group());
        return words;
    }

    /**
     * Gets the last word in a line of text.
     * 
     * <p>Useful for rhyme analysis and end-word processing.</p>
     * 
     * @param line The line to analyze. May be null.
     * @return The last word in the line, or null if no words are found.
     */
    public static String endWord(String line) {
        List<String> words = wordsInLine(line);
        return words.isEmpty() ? null : words.get(words.size()-1);
    }

    /**
     * Counts syllables in a word using multiple heuristics and exception handling.
     * 
     * <p>This method uses a sophisticated algorithm that includes:
     * <ul>
     *   <li>Known word overrides for irregular words</li>
     *   <li>Vowel group counting with adjacency rules</li>
     *   <li>Silent 'e' detection with exceptions</li>
     *   <li>Special suffix handling (-le, -ed, -tion, etc.)</li>
     *   <li>Diphthong and special combination handling</li>
     * </ul></p>
     * 
     * @param word The word to analyze. May be null or empty.
     * @return The estimated syllable count (minimum 1 for non-empty words).
     */
    public static int countSyllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        Integer nativeCount = NativeAccess.countSyllables(word);
        if (nativeCount != null) return nativeCount;
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
    
    /**
     * Checks if a character is a vowel.
     * 
     * @param c The character to check.
     * @return true if the character is a vowel (a, e, i, o, u, y).
     */
    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    }

    /**
     * Counts total syllables in a line of text.
     * 
     * <p>Sums the syllable counts of all words in the line.</p>
     * 
     * @param line The line to analyze. May be null or empty.
     * @return Total syllable count for the line.
     */
    public static int countSyllablesInLine(String line) {
        int total = 0;
        for (String w : wordsInLine(line)) total += countSyllables(w);
        return total;
    }

    /**
     * Generates a rhyme key for a word based on its last stressed vowel and following sounds.
     * 
     * <p>The rhyme key represents the phonetic ending of the word that determines
     * rhyming patterns. It includes the last vowel group and all subsequent consonants.</p>
     * 
     * @param word The word to generate a rhyme key for. May be null.
     * @return The rhyme key, or null if input is null or empty.
     */
    public static String rhymeKey(String word) {
        if (word == null) return null;
        String nativeKey = NativeAccess.rhymeKey(word);
        if (nativeKey != null) return nativeKey;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return null;

        // Remove silent trailing 'e' for rhyme purposes (except exceptions)
        if (w.endsWith("e") && w.length() > 2 && !SILENT_E_EXCEPTIONS.contains(w)) {
            char prev = w.charAt(w.length() - 2);
            if (!isVowel(prev) && prev != 'l') {
                w = w.substring(0, w.length() - 1);
            }
        }
        
        // Find last vowel group
        Matcher m = VOWEL_GROUPS.matcher(w);
        int start = -1;
        while (m.find()) { start = m.start(); }
        if (start == -1) return w.length() >= 2 ? w.substring(Math.max(0, w.length()-2)) : w;
        String key = w.substring(start);

        // Heuristic normalizations for common rhyme variants
        if (key.startsWith("ea") && key.length() > 2 && key.charAt(2) == 'r') {
            key = key.substring(1);
        }
        if (key.startsWith("y")) {
            key = "i" + key.substring(1);
        }
        return key;
    }
    
    /**
     * Generates a near-rhyme key for more lenient rhyme matching.
     * 
     * <p>Near rhymes use the last 2-3 characters of the word, allowing for
     * slant rhymes and approximate sound matches.</p>
     * 
     * @param word The word to generate a near-rhyme key for. May be null.
     * @return The near-rhyme key, or null if input is null or empty.
     */
    public static String nearRhymeKey(String word) {
        if (word == null) return null;
        String nativeKey = NativeAccess.nearRhymeKey(word);
        if (nativeKey != null) return nativeKey;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return null;
        
        // Just the last 2-3 characters for near rhymes
        return w.length() >= 3 ? w.substring(w.length() - 3) : w;
    }
    
    /**
     * Checks if two words rhyme exactly.
     * 
     * <p>Words rhyme if they have the same rhyme key but are not identical words.</p>
     * 
     * @param word1 The first word to compare. May be null.
     * @param word2 The second word to compare. May be null.
     * @return true if the words rhyme exactly, false otherwise.
     */
    public static boolean rhymes(String word1, String word2) {
        if (word1 == null || word2 == null) return false;
        String key1 = rhymeKey(word1);
        String key2 = rhymeKey(word2);
        return key1 != null && key1.equals(key2) && 
               !word1.equalsIgnoreCase(word2);
    }
    
    /**
     * Checks if two words are near-rhymes (slant rhymes).
     * 
     * <p>Near rhymes allow for more flexible matching based on similar endings
     * or shared phonetic characteristics.</p>
     * 
     * @param word1 The first word to compare. May be null.
     * @param word2 The second word to compare. May be null.
     * @return true if the words are near-rhymes, false otherwise.
     */
    public static boolean nearRhymes(String word1, String word2) {
        if (word1 == null || word2 == null) return false;
        String key1 = nearRhymeKey(word1);
        String key2 = nearRhymeKey(word2);
        return key1 != null && key2 != null && 
               (key1.equals(key2) || shareEnding(key1, key2, 2)) &&
               !word1.equalsIgnoreCase(word2);
    }
    
    /**
     * Checks if two strings share the same ending characters.
     * 
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @param chars The number of ending characters to compare.
     * @return true if both strings share the specified number of ending characters.
     */
    private static boolean shareEnding(String s1, String s2, int chars) {
        if (s1.length() < chars || s2.length() < chars) return false;
        return s1.substring(s1.length() - chars).equals(s2.substring(s2.length() - chars));
    }
    
    /**
     * Estimates the stress pattern of a word.
     * 
     * <p>Returns a binary pattern where 1 represents stressed syllables and 0 represents
     * unstressed syllables. The estimation uses common English stress patterns.</p>
     * 
     * @param word The word to analyze. May be null or empty.
     * @return An array representing the stress pattern (1=stressed, 0=unstressed).
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
     * Estimates the stress pattern for an entire line of text.
     * 
     * <p>This method combines the stress patterns of individual words to create
     * a line-level stress pattern. The result is intentionally simplified for
     * practical meter analysis.</p>
     * 
     * @param line The line to analyze. May be null or empty.
     * @return An array representing the line's stress pattern.
     */
    public static int[] estimateLineStressPattern(String line) {
        if (line == null || line.isBlank()) return new int[0];
        java.util.List<Integer> bits = new java.util.ArrayList<>();
        for (String w : wordsInLine(line)) {
            int[] pat = estimateStressPattern(w);
            for (int b : pat) bits.add(b);
        }
        int[] out = new int[bits.size()];
        for (int i = 0; i < bits.size(); i++) out[i] = bits.get(i);
        return out;
    }
    
    /**
     * Checks if a line follows iambic meter pattern (unstressed-stressed).
     * 
     * <p>Iambic pattern: da-DUM da-DUM da-DUM (0-1-0-1-0-1)</p>
     * 
     * @param line The line to analyze. May be null or empty.
     * @return true if the line follows iambic pattern, false otherwise.
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
     * Checks if a line follows trochaic meter pattern (stressed-unstressed).
     * 
     * <p>Trochaic pattern: DUM-da DUM-da DUM-da (1-0-1-0-1-0)</p>
     * 
     * @param line The line to analyze. May be null or empty.
     * @return true if the line follows trochaic pattern, false otherwise.
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
