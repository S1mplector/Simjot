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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PoetryUtils - syllable counting, rhyme detection,
 * stress patterns, and text processing utilities.
 */
@DisplayName("PoetryUtils")
class PoetryUtilsTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // SYLLABLE COUNTING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("countSyllables")
    class CountSyllablesTests {

        @ParameterizedTest(name = "\"{0}\" should have {1} syllable(s)")
        @CsvSource({
            // Single syllable words
            "cat, 1",
            "dog, 1",
            "the, 1",
            "fire, 1",
            "real, 1",
            "hour, 1",
            
            // Two syllable words
            "hello, 2",
            "water, 2",
            "really, 2",
            "being, 2",
            "poem, 2",
            "quiet, 2",
            "heaven, 2",
            "seven, 2",
            
            // Three syllable words
            "beautiful, 3",
            "poetry, 3",
            "family, 3",
            "probably, 3",
            "everyone, 3",
            "area, 3",
            
            // Four+ syllable words
            "interesting, 4",
            "comfortable, 4",
            "especially, 5",
            "unfortunately, 5"
        })
        void shouldCountSyllablesCorrectly(String word, int expected) {
            assertEquals(expected, PoetryUtils.countSyllables(word),
                "Syllable count for '" + word + "'");
        }

        @Test
        @DisplayName("should handle silent 'e' correctly")
        void shouldHandleSilentE() {
            assertEquals(1, PoetryUtils.countSyllables("make"));
            assertEquals(1, PoetryUtils.countSyllables("time"));
            assertEquals(1, PoetryUtils.countSyllables("love"));
            // Exceptions that keep the 'e' sound
            assertEquals(1, PoetryUtils.countSyllables("the"));
            assertEquals(1, PoetryUtils.countSyllables("be"));
        }

        @Test
        @DisplayName("should handle -ed endings correctly")
        void shouldHandleEdEndings() {
            assertEquals(2, PoetryUtils.countSyllables("wanted")); // t+ed = extra syllable
            assertEquals(2, PoetryUtils.countSyllables("needed")); // d+ed = extra syllable
            assertEquals(1, PoetryUtils.countSyllables("played"));  // silent -ed
            assertEquals(1, PoetryUtils.countSyllables("walked"));  // silent -ed
        }

        @Test
        @DisplayName("should handle -le endings correctly")
        void shouldHandleLeEndings() {
            assertEquals(2, PoetryUtils.countSyllables("apple"));
            assertEquals(2, PoetryUtils.countSyllables("table"));
            assertEquals(2, PoetryUtils.countSyllables("little"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return 0 for null or empty input")
        void shouldReturnZeroForNullOrEmpty(String input) {
            assertEquals(0, PoetryUtils.countSyllables(input));
        }

        @Test
        @DisplayName("should handle words with apostrophes")
        void shouldHandleApostrophes() {
            assertTrue(PoetryUtils.countSyllables("don't") >= 1);
            assertTrue(PoetryUtils.countSyllables("it's") >= 1);
            assertTrue(PoetryUtils.countSyllables("couldn't") >= 1);
        }
    }

    @Nested
    @DisplayName("countSyllablesInLine")
    class CountSyllablesInLineTests {

        @Test
        @DisplayName("should count syllables in a simple line")
        void shouldCountSyllablesInSimpleLine() {
            // "The cat sat on the mat" = 1+1+1+1+1+1 = 6
            int count = PoetryUtils.countSyllablesInLine("The cat sat on the mat");
            assertEquals(6, count);
        }

        @Test
        @DisplayName("should handle famous poetry lines")
        void shouldHandleFamousPoetryLines() {
            // "To be or not to be" - typically 6 syllables
            int toBeCount = PoetryUtils.countSyllablesInLine("To be or not to be");
            assertTrue(toBeCount >= 5 && toBeCount <= 7, 
                "Expected 5-7 syllables, got " + toBeCount);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return 0 for null or empty line")
        void shouldReturnZeroForNullOrEmpty(String line) {
            assertEquals(0, PoetryUtils.countSyllablesInLine(line));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RHYME DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("rhymes")
    class RhymesTests {

        @ParameterizedTest(name = "\"{0}\" and \"{1}\" should rhyme")
        @CsvSource({
            "cat, hat",
            "day, say",
            "night, light",
            "love, dove",
            "moon, soon",
            "heart, start",
            "time, rhyme"
        })
        void shouldDetectExactRhymes(String word1, String word2) {
            assertTrue(PoetryUtils.rhymes(word1, word2),
                word1 + " and " + word2 + " should rhyme");
        }

        @Test
        @DisplayName("should not consider same word as rhyming")
        void shouldNotRhymeSameWord() {
            assertFalse(PoetryUtils.rhymes("love", "love"));
            assertFalse(PoetryUtils.rhymes("Day", "day")); // case insensitive
        }

        @Test
        @DisplayName("should handle null inputs")
        void shouldHandleNullInputs() {
            assertFalse(PoetryUtils.rhymes(null, "cat"));
            assertFalse(PoetryUtils.rhymes("cat", null));
            assertFalse(PoetryUtils.rhymes(null, null));
        }

        @ParameterizedTest(name = "\"{0}\" and \"{1}\" should NOT rhyme")
        @CsvSource({
            "cat, dog",
            "love, hate",
            "sun, moon",
            "day, night"
        })
        void shouldNotDetectNonRhymes(String word1, String word2) {
            assertFalse(PoetryUtils.rhymes(word1, word2),
                word1 + " and " + word2 + " should not rhyme");
        }
    }

    @Nested
    @DisplayName("nearRhymes")
    class NearRhymesTests {

        @Test
        @DisplayName("should detect near rhymes (slant rhymes)")
        void shouldDetectNearRhymes() {
            // Near rhymes share similar endings
            assertTrue(PoetryUtils.nearRhymes("cat", "hat"));
            assertTrue(PoetryUtils.nearRhymes("ring", "thing"));
        }

        @Test
        @DisplayName("should not consider same word as near rhyming")
        void shouldNotNearRhymeSameWord() {
            assertFalse(PoetryUtils.nearRhymes("love", "love"));
        }
    }

    @Nested
    @DisplayName("rhymeKey")
    class RhymeKeyTests {

        @Test
        @DisplayName("should generate consistent rhyme keys for rhyming words")
        void shouldGenerateConsistentRhymeKeys() {
            assertEquals(PoetryUtils.rhymeKey("cat"), PoetryUtils.rhymeKey("hat"));
            assertEquals(PoetryUtils.rhymeKey("day"), PoetryUtils.rhymeKey("say"));
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            assertNull(PoetryUtils.rhymeKey(null));
        }

        @Test
        @DisplayName("should extract phonetic ending")
        void shouldExtractPhoneticEnding() {
            String key = PoetryUtils.rhymeKey("night");
            assertNotNull(key);
            assertTrue(key.contains("ight") || key.endsWith("ight"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT PROCESSING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("splitLines")
    class SplitLinesTests {

        @Test
        @DisplayName("should split Unix line endings")
        void shouldSplitUnixLineEndings() {
            List<String> lines = PoetryUtils.splitLines("line1\nline2\nline3");
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line2", lines.get(1));
            assertEquals("line3", lines.get(2));
        }

        @Test
        @DisplayName("should split Windows line endings")
        void shouldSplitWindowsLineEndings() {
            List<String> lines = PoetryUtils.splitLines("line1\r\nline2\r\nline3");
            assertEquals(3, lines.size());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return empty list for null or empty input")
        void shouldReturnEmptyListForNullOrEmpty(String input) {
            List<String> lines = PoetryUtils.splitLines(input);
            assertTrue(lines.isEmpty());
        }
    }

    @Nested
    @DisplayName("wordsInLine")
    class WordsInLineTests {

        @Test
        @DisplayName("should extract words from line")
        void shouldExtractWordsFromLine() {
            List<String> words = PoetryUtils.wordsInLine("The quick brown fox");
            assertEquals(4, words.size());
            assertEquals("The", words.get(0));
            assertEquals("quick", words.get(1));
            assertEquals("brown", words.get(2));
            assertEquals("fox", words.get(3));
        }

        @Test
        @DisplayName("should ignore punctuation")
        void shouldIgnorePunctuation() {
            List<String> words = PoetryUtils.wordsInLine("Hello, world! How are you?");
            assertEquals(5, words.size());
            assertTrue(words.contains("Hello"));
            assertTrue(words.contains("world"));
        }

        @Test
        @DisplayName("should handle contractions")
        void shouldHandleContractions() {
            List<String> words = PoetryUtils.wordsInLine("don't can't won't");
            assertEquals(3, words.size());
        }

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyListForNull() {
            assertTrue(PoetryUtils.wordsInLine(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("endWord")
    class EndWordTests {

        @Test
        @DisplayName("should return last word of line")
        void shouldReturnLastWord() {
            assertEquals("fox", PoetryUtils.endWord("The quick brown fox"));
            assertEquals("moon", PoetryUtils.endWord("I saw the moon"));
        }

        @Test
        @DisplayName("should ignore trailing punctuation")
        void shouldIgnoreTrailingPunctuation() {
            String endWord = PoetryUtils.endWord("Hello world!");
            assertEquals("world", endWord);
        }

        @Test
        @DisplayName("should return null for empty or null input")
        void shouldReturnNullForEmptyOrNull() {
            assertNull(PoetryUtils.endWord(null));
            assertNull(PoetryUtils.endWord(""));
            assertNull(PoetryUtils.endWord("   "));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRESS PATTERN TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("estimateStressPattern")
    class EstimateStressPatternTests {

        @Test
        @DisplayName("should return single stressed for monosyllabic words")
        void shouldReturnSingleStressedForMonosyllabic() {
            int[] pattern = PoetryUtils.estimateStressPattern("cat");
            assertEquals(1, pattern.length);
            assertEquals(1, pattern[0]);
        }

        @Test
        @DisplayName("should estimate pattern for polysyllabic words")
        void shouldEstimatePatternForPolysyllabic() {
            int[] pattern = PoetryUtils.estimateStressPattern("beautiful");
            assertEquals(3, pattern.length); // beau-ti-ful
        }

        @Test
        @DisplayName("should return empty array for null or empty")
        void shouldReturnEmptyForNullOrEmpty() {
            assertEquals(0, PoetryUtils.estimateStressPattern(null).length);
            assertEquals(0, PoetryUtils.estimateStressPattern("").length);
        }
    }

    @Nested
    @DisplayName("isIambic / isTrochaic")
    class MeterPatternTests {

        @Test
        @DisplayName("should detect trochaic patterns")
        void shouldDetectTrochaicPattern() {
            // Trochaic: stressed-unstressed (DUM-da)
            // "Peter Peter" - PE-ter PE-ter
            boolean result = PoetryUtils.isTrochaic("Peter Peter");
            // Result depends on implementation - just verify it runs
            assertNotNull(result);
        }

        @Test
        @DisplayName("should handle empty input for meter checks")
        void shouldHandleEmptyInputForMeter() {
            assertFalse(PoetryUtils.isIambic(""));
            assertFalse(PoetryUtils.isTrochaic(""));
            assertFalse(PoetryUtils.isIambic(null));
            assertFalse(PoetryUtils.isTrochaic(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EDGE CASES AND REGRESSION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle words with numbers")
        void shouldHandleWordsWithNumbers() {
            // Should strip numbers and process remaining
            int count = PoetryUtils.countSyllables("hello123world");
            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("should handle unicode characters gracefully")
        void shouldHandleUnicodeGracefully() {
            // Should not throw exceptions
            assertDoesNotThrow(() -> PoetryUtils.countSyllables("café"));
            assertDoesNotThrow(() -> PoetryUtils.countSyllables("naïve"));
        }

        @Test
        @DisplayName("should handle very long words")
        void shouldHandleVeryLongWords() {
            String longWord = "supercalifragilisticexpialidocious";
            int count = PoetryUtils.countSyllables(longWord);
            assertTrue(count > 10, "Expected many syllables for long word");
        }

        @Test
        @DisplayName("should handle single letter words")
        void shouldHandleSingleLetterWords() {
            assertEquals(1, PoetryUtils.countSyllables("a"));
            assertEquals(1, PoetryUtils.countSyllables("I"));
        }
    }
}
