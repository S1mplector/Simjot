package main.core.poetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight poetry utilities: syllable estimation, rhyme keys, tokenization.
 * Heuristics are simple and dependency-free to keep UI responsive.
 */
public final class PoetryUtils {
    private PoetryUtils() {}

    private static final Pattern VOWEL_GROUPS = Pattern.compile("[aeiouy]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z']+");

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

    public static int countSyllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
        if (w.isEmpty()) return 0;
        // Remove silent trailing 'e'
        if (w.endsWith("e") && w.length() > 2 && !w.endsWith("le")) {
            w = w.substring(0, w.length()-1);
        }
        int count = 0;
        Matcher m = VOWEL_GROUPS.matcher(w);
        while (m.find()) count++;
        // Ensure at least 1 syllable for any alphabetic word
        return Math.max(1, count);
    }

    public static int countSyllablesInLine(String line) {
        int total = 0;
        for (String w : wordsInLine(line)) total += countSyllables(w);
        return total;
    }

    /**
     * Rhyme key: last vowel-group plus trailing consonants, e.g., "flow"->"ow", "falling"->"ing".
     */
    public static String rhymeKey(String word) {
        if (word == null) return null;
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return null;
        // find last vowel group
        Matcher m = VOWEL_GROUPS.matcher(w);
        int start = -1, end = -1;
        while (m.find()) { start = m.start(); end = m.end(); }
        if (start == -1) return w.length() >= 2 ? w.substring(Math.max(0, w.length()-2)) : w; // fallback
        return w.substring(start);
    }
}
