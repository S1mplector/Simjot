/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.spelling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IntelligentAutocorrect - Context-aware autocorrection engine.
 * 
 * Features:
 * - Real-time typo correction as user types
 * - Context-aware suggestions based on surrounding words
 * - Learning from user corrections
 * - Keyboard layout awareness (adjacent key typos)
 * - Common phonetic mistakes
 * - Capitalization fixing
 */
public class IntelligentAutocorrect {
    
    private static IntelligentAutocorrect instance;
    
    private final Map<String, String> corrections = new ConcurrentHashMap<>();
    private final Map<String, String> userCorrections = new ConcurrentHashMap<>();
    private final Map<String, Integer> userWordFrequency = new ConcurrentHashMap<>();
    private final Set<String> ignoredWords = ConcurrentHashMap.newKeySet();
    private final SpellCheckEngine spellChecker;
    
    // Keyboard layout for adjacent key detection
    private static final Map<Character, String> QWERTY_ADJACENT = new HashMap<>();
    static {
        QWERTY_ADJACENT.put('q', "wa");
        QWERTY_ADJACENT.put('w', "qeas");
        QWERTY_ADJACENT.put('e', "wrsd");
        QWERTY_ADJACENT.put('r', "etdf");
        QWERTY_ADJACENT.put('t', "ryfg");
        QWERTY_ADJACENT.put('y', "tugh");
        QWERTY_ADJACENT.put('u', "yihj");
        QWERTY_ADJACENT.put('i', "uojk");
        QWERTY_ADJACENT.put('o', "ipkl");
        QWERTY_ADJACENT.put('p', "ol");
        QWERTY_ADJACENT.put('a', "qwsz");
        QWERTY_ADJACENT.put('s', "awedxz");
        QWERTY_ADJACENT.put('d', "serfcx");
        QWERTY_ADJACENT.put('f', "drtgvc");
        QWERTY_ADJACENT.put('g', "ftyhbv");
        QWERTY_ADJACENT.put('h', "gyujnb");
        QWERTY_ADJACENT.put('j', "huikmn");
        QWERTY_ADJACENT.put('k', "jiolm");
        QWERTY_ADJACENT.put('l', "kop");
        QWERTY_ADJACENT.put('z', "asx");
        QWERTY_ADJACENT.put('x', "zsdc");
        QWERTY_ADJACENT.put('c', "xdfv");
        QWERTY_ADJACENT.put('v', "cfgb");
        QWERTY_ADJACENT.put('b', "vghn");
        QWERTY_ADJACENT.put('n', "bhjm");
        QWERTY_ADJACENT.put('m', "njk");
    }
    
    // Common phonetic confusions
    private static final String[][] PHONETIC_PAIRS = {
        {"ei", "ie"}, {"ie", "ei"},
        {"tion", "sion"}, {"sion", "tion"},
        {"ible", "able"}, {"able", "ible"},
        {"ence", "ance"}, {"ance", "ence"},
        {"ant", "ent"}, {"ent", "ant"},
        {"er", "or"}, {"or", "er"},
        {"c", "k"}, {"k", "c"},
        {"ph", "f"}, {"f", "ph"},
        {"gh", "f"},
        {"ough", "uff"}, {"ough", "off"},
    };
    
    // Word patterns that should trigger specific corrections
    private static final Pattern DOUBLE_SPACE = Pattern.compile("  +");
    private static final Pattern STARTS_WITH_LOWERCASE_I = Pattern.compile("\\bi\\b");
    
    private IntelligentAutocorrect() {
        spellChecker = SpellCheckEngine.get();
        loadCorrections();
    }
    
    public static synchronized IntelligentAutocorrect get() {
        if (instance == null) instance = new IntelligentAutocorrect();
        return instance;
    }
    
    private void loadCorrections() {
        // Load from AutocorrectDatabase
        for (String[] pair : AutocorrectDatabase.getMappings()) {
            if (pair.length == 2) {
                corrections.put(pair[0].toLowerCase(Locale.ROOT), pair[1]);
            }
        }
        
        // Add common double-letter typos
        addDoubleLetterCorrections();
        
        // Add common transposition errors
        addTranspositionCorrections();
    }
    
    private void addDoubleLetterCorrections() {
        // Words where double letters are commonly missed or added incorrectly
        String[][] doubles = {
            {"comming", "coming"}, {"runing", "running"}, {"begining", "beginning"},
            {"ocurring", "occurring"}, {"refering", "referring"}, {"prefered", "preferred"},
            {"transfered", "transferred"}, {"excellant", "excellent"}, {"succesfull", "successful"},
            {"dissapear", "disappear"}, {"dissapoint", "disappoint"}, {"recomend", "recommend"},
            {"accomodate", "accommodate"}, {"occurrance", "occurrence"}, {"embarass", "embarrass"},
            {"harrass", "harass"}, {"posession", "possession"}, {"proffessional", "professional"},
            {"tommorrow", "tomorrow"}, {"neccessary", "necessary"}, {"occassion", "occasion"},
            {"agressive", "aggressive"}, {"comittee", "committee"}, {"millenium", "millennium"}
        };
        for (String[] pair : doubles) {
            corrections.putIfAbsent(pair[0], pair[1]);
        }
    }
    
    private void addTranspositionCorrections() {
        // Common transposition errors
        String[][] transpositions = {
            {"teh", "the"}, {"hte", "the"}, {"taht", "that"}, {"thta", "that"},
            {"yuor", "your"}, {"yoru", "your"}, {"wiht", "with"}, {"whit", "with"},
            {"form", "from"}, {"fomr", "from"}, {"jsut", "just"}, {"jstu", "just"},
            {"konw", "know"}, {"knwo", "know"}, {"liek", "like"}, {"lkie", "like"},
            {"wnat", "want"}, {"watn", "want"}, {"tihs", "this"}, {"htis", "this"},
            {"sicne", "since"}, {"snce", "since"}, {"htat", "that"}, {"thier", "their"},
            {"beucase", "because"}, {"becuase", "because"}, {"beacuse", "because"}
        };
        for (String[] pair : transpositions) {
            corrections.putIfAbsent(pair[0], pair[1]);
        }
    }
    
    /**
     * Check if a word should be autocorrected and return the correction.
     * Returns null if no correction is needed.
     */
    public String getCorrection(String word) {
        if (word == null || word.length() <= 1) return null;
        
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Check if word is in ignore list
        if (ignoredWords.contains(lower)) return null;
        
        // Check if word is already correct
        if (spellChecker.isCorrect(word)) return null;
        
        // Check user corrections first (learning)
        String userCorrection = userCorrections.get(lower);
        if (userCorrection != null) {
            return preserveCase(word, userCorrection);
        }
        
        // Check standard corrections
        String correction = corrections.get(lower);
        if (correction != null) {
            return preserveCase(word, correction);
        }
        
        // Try phonetic matching
        correction = findPhoneticCorrection(lower);
        if (correction != null) {
            return preserveCase(word, correction);
        }
        
        // Try keyboard adjacency correction
        correction = findAdjacentKeyCorrection(lower);
        if (correction != null) {
            return preserveCase(word, correction);
        }
        
        // Fall back to spell checker suggestions
        List<String> suggestions = spellChecker.getSuggestions(word);
        if (!suggestions.isEmpty()) {
            String best = suggestions.get(0);
            // Only auto-correct if it's a very close match
            if (levenshteinDistance(lower, best.toLowerCase()) <= 2) {
                return preserveCase(word, best);
            }
        }
        
        return null;
    }
    
    /**
     * Context-aware correction considering surrounding words.
     */
    public String getContextualCorrection(String word, String prevWord, String nextWord) {
        String basic = getCorrection(word);
        if (basic != null) return basic;
        
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Context-specific corrections
        if (prevWord != null) {
            String prevLower = prevWord.toLowerCase(Locale.ROOT);
            
            // "a" vs "an" correction
            if (lower.equals("a") && nextWord != null && startsWithVowelSound(nextWord)) {
                return "an";
            }
            if (lower.equals("an") && nextWord != null && !startsWithVowelSound(nextWord)) {
                return "a";
            }
            
            // Common phrase corrections
            String phrase = prevLower + " " + lower;
            String phraseCorrection = getPhraseCorrection(phrase);
            if (phraseCorrection != null) {
                return phraseCorrection.split(" ")[1]; // Return corrected second word
            }
        }
        
        return null;
    }
    
    private String getPhraseCorrection(String phrase) {
        // Common phrase mistakes
        Map<String, String> phrases = Map.of(
            "should of", "should have",
            "could of", "could have",
            "would of", "would have",
            "must of", "must have",
            "might of", "might have",
            "alot of", "a lot of",
            "incase of", "in case of",
            "apart of", "a part of"
        );
        return phrases.get(phrase);
    }
    
    private boolean startsWithVowelSound(String word) {
        if (word == null || word.isEmpty()) return false;
        String lower = word.toLowerCase(Locale.ROOT);
        
        // Words starting with silent h
        Set<String> silentH = Set.of("hour", "honor", "honest", "heir", "herb");
        for (String s : silentH) {
            if (lower.startsWith(s)) return true;
        }
        
        // Words starting with vowel sound but consonant letter (like "university")
        Set<String> consonantSound = Set.of("uni", "use", "user", "usual", "utility", "european", "one", "once");
        for (String s : consonantSound) {
            if (lower.startsWith(s)) return false;
        }
        
        char first = lower.charAt(0);
        return "aeiou".indexOf(first) >= 0;
    }
    
    private String findPhoneticCorrection(String word) {
        for (String[] pair : PHONETIC_PAIRS) {
            if (word.contains(pair[0])) {
                String candidate = word.replace(pair[0], pair[1]);
                if (spellChecker.isCorrect(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
    
    private String findAdjacentKeyCorrection(String word) {
        // Try replacing each character with adjacent keys
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            String adjacent = QWERTY_ADJACENT.get(Character.toLowerCase(c));
            if (adjacent != null) {
                for (char adj : adjacent.toCharArray()) {
                    String candidate = word.substring(0, i) + adj + word.substring(i + 1);
                    if (spellChecker.isCorrect(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Preserve the original case pattern when applying correction.
     */
    private String preserveCase(String original, String correction) {
        if (original == null || correction == null) return correction;
        if (original.isEmpty()) return correction;
        
        // All uppercase
        if (original.equals(original.toUpperCase(Locale.ROOT))) {
            return correction.toUpperCase(Locale.ROOT);
        }
        
        // Title case (first letter uppercase)
        if (Character.isUpperCase(original.charAt(0))) {
            if (correction.isEmpty()) return correction;
            return Character.toUpperCase(correction.charAt(0)) + 
                   (correction.length() > 1 ? correction.substring(1).toLowerCase(Locale.ROOT) : "");
        }
        
        // All lowercase
        return correction.toLowerCase(Locale.ROOT);
    }
    
    /**
     * Learn from user correction.
     */
    public void learn(String typo, String correction) {
        if (typo == null || correction == null) return;
        String lowerTypo = typo.toLowerCase(Locale.ROOT);
        userCorrections.put(lowerTypo, correction.toLowerCase(Locale.ROOT));
        
        // Track word frequency
        String lowerCorrection = correction.toLowerCase(Locale.ROOT);
        userWordFrequency.merge(lowerCorrection, 1, Integer::sum);
    }
    
    /**
     * Add word to ignore list (don't autocorrect).
     */
    public void ignore(String word) {
        if (word != null) {
            ignoredWords.add(word.toLowerCase(Locale.ROOT));
        }
    }
    
    /**
     * Remove word from ignore list.
     */
    public void unignore(String word) {
        if (word != null) {
            ignoredWords.remove(word.toLowerCase(Locale.ROOT));
        }
    }
    
    /**
     * Fix common capitalization issues.
     */
    public String fixCapitalization(String text) {
        if (text == null || text.isEmpty()) return text;
        
        // Fix standalone "i" -> "I"
        text = STARTS_WITH_LOWERCASE_I.matcher(text).replaceAll("I");
        
        // Fix double spaces
        text = DOUBLE_SPACE.matcher(text).replaceAll(" ");
        
        return text;
    }
    
    /**
     * Process entire text and return corrected version.
     */
    public CorrectionResult processText(String text) {
        if (text == null || text.isEmpty()) {
            return new CorrectionResult(text, Collections.emptyList());
        }
        
        List<Correction> correctionList = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        
        int pos = 0;
        while (pos < text.length()) {
            // Find word boundaries
            int wordStart = pos;
            while (wordStart < text.length() && !Character.isLetter(text.charAt(wordStart))) {
                result.append(text.charAt(wordStart));
                wordStart++;
            }
            
            if (wordStart >= text.length()) break;
            
            int wordEnd = wordStart;
            while (wordEnd < text.length() && (Character.isLetter(text.charAt(wordEnd)) || text.charAt(wordEnd) == '\'')) {
                wordEnd++;
            }
            
            String word = text.substring(wordStart, wordEnd);
            String correction = getCorrection(word);
            
            if (correction != null && !correction.equals(word)) {
                result.append(correction);
                correctionList.add(new Correction(wordStart, wordEnd, word, correction));
            } else {
                result.append(word);
            }
            
            pos = wordEnd;
        }
        
        // Apply capitalization fixes
        String finalText = fixCapitalization(result.toString());
        
        return new CorrectionResult(finalText, correctionList);
    }
    
    private int levenshteinDistance(String a, String b) {
        // Try native implementation first
        Integer nativeResult = NativeAccess.textLevenshtein(a, b);
        if (nativeResult != null) {
            return nativeResult;
        }
        // Java fallback
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
    
    /**
     * Result of processing text with corrections.
     */
    public static class CorrectionResult {
        public final String correctedText;
        public final List<Correction> corrections;
        
        public CorrectionResult(String correctedText, List<Correction> corrections) {
            this.correctedText = correctedText;
            this.corrections = corrections;
        }
        
        public boolean hasCorrections() {
            return !corrections.isEmpty();
        }
    }
    
    /**
     * Individual correction made.
     */
    public static class Correction {
        public final int start;
        public final int end;
        public final String original;
        public final String replacement;
        
        public Correction(int start, int end, String original, String replacement) {
            this.start = start;
            this.end = end;
            this.original = original;
            this.replacement = replacement;
        }
    }
}
