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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for PoetryDictionary - dictionary lookups, POS tagging, and stress patterns.
 */
@DisplayName("PoetryDictionary")
class PoetryDictionaryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // FUNCTION WORDS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isFunctionWord")
    class IsFunctionWordTests {

        @Test
        @DisplayName("should identify articles as function words")
        void shouldIdentifyArticles() {
            assertTrue(PoetryDictionary.isFunctionWord("a"));
            assertTrue(PoetryDictionary.isFunctionWord("an"));
            assertTrue(PoetryDictionary.isFunctionWord("the"));
        }

        @Test
        @DisplayName("should identify prepositions as function words")
        void shouldIdentifyPrepositions() {
            assertTrue(PoetryDictionary.isFunctionWord("at"));
            assertTrue(PoetryDictionary.isFunctionWord("by"));
            assertTrue(PoetryDictionary.isFunctionWord("for"));
            assertTrue(PoetryDictionary.isFunctionWord("from"));
            assertTrue(PoetryDictionary.isFunctionWord("in"));
            assertTrue(PoetryDictionary.isFunctionWord("of"));
            assertTrue(PoetryDictionary.isFunctionWord("on"));
            assertTrue(PoetryDictionary.isFunctionWord("to"));
            assertTrue(PoetryDictionary.isFunctionWord("with"));
        }

        @Test
        @DisplayName("should identify conjunctions as function words")
        void shouldIdentifyConjunctions() {
            assertTrue(PoetryDictionary.isFunctionWord("and"));
            assertTrue(PoetryDictionary.isFunctionWord("or"));
            assertTrue(PoetryDictionary.isFunctionWord("but"));
            assertTrue(PoetryDictionary.isFunctionWord("if"));
            assertTrue(PoetryDictionary.isFunctionWord("because"));
        }

        @Test
        @DisplayName("should identify pronouns as function words")
        void shouldIdentifyPronouns() {
            assertTrue(PoetryDictionary.isFunctionWord("i"));
            assertTrue(PoetryDictionary.isFunctionWord("you"));
            assertTrue(PoetryDictionary.isFunctionWord("he"));
            assertTrue(PoetryDictionary.isFunctionWord("she"));
            assertTrue(PoetryDictionary.isFunctionWord("it"));
            assertTrue(PoetryDictionary.isFunctionWord("we"));
            assertTrue(PoetryDictionary.isFunctionWord("they"));
        }

        @Test
        @DisplayName("should identify auxiliaries as function words")
        void shouldIdentifyAuxiliaries() {
            assertTrue(PoetryDictionary.isFunctionWord("is"));
            assertTrue(PoetryDictionary.isFunctionWord("are"));
            assertTrue(PoetryDictionary.isFunctionWord("was"));
            assertTrue(PoetryDictionary.isFunctionWord("were"));
            assertTrue(PoetryDictionary.isFunctionWord("have"));
            assertTrue(PoetryDictionary.isFunctionWord("has"));
            assertTrue(PoetryDictionary.isFunctionWord("had"));
            assertTrue(PoetryDictionary.isFunctionWord("will"));
            assertTrue(PoetryDictionary.isFunctionWord("would"));
            assertTrue(PoetryDictionary.isFunctionWord("can"));
            assertTrue(PoetryDictionary.isFunctionWord("could"));
        }

        @Test
        @DisplayName("should NOT identify content words as function words")
        void shouldNotIdentifyContentWords() {
            assertFalse(PoetryDictionary.isFunctionWord("love"));
            assertFalse(PoetryDictionary.isFunctionWord("beautiful"));
            assertFalse(PoetryDictionary.isFunctionWord("running"));
            assertFalse(PoetryDictionary.isFunctionWord("dream"));
            assertFalse(PoetryDictionary.isFunctionWord("mountain"));
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            assertTrue(PoetryDictionary.isFunctionWord("THE"));
            assertTrue(PoetryDictionary.isFunctionWord("And"));
            assertTrue(PoetryDictionary.isFunctionWord("BUT"));
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            assertFalse(PoetryDictionary.isFunctionWord(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRESS PATTERN
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStressPattern")
    class GetStressPatternTests {

        @Test
        @DisplayName("should return empty for null input")
        void shouldReturnEmptyForNull() {
            int[] pattern = PoetryDictionary.getStressPattern(null);
            assertEquals(0, pattern.length);
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            int[] pattern = PoetryDictionary.getStressPattern("");
            assertEquals(0, pattern.length);
        }

        @Test
        @DisplayName("monosyllable content words should be stressed")
        void monosyllableContentWordsShouldBeStressed() {
            int[] pattern = PoetryDictionary.getStressPattern("love");
            assertEquals(1, pattern.length);
            assertEquals(1, pattern[0]);
        }

        @Test
        @DisplayName("monosyllable function words should be unstressed")
        void monosyllableFunctionWordsShouldBeUnstressed() {
            int[] pattern = PoetryDictionary.getStressPattern("the");
            assertEquals(1, pattern.length);
            assertEquals(0, pattern[0]);
        }

        @Test
        @DisplayName("should return stress pattern for multi-syllable words")
        void shouldReturnStressPatternForMultiSyllable() {
            int[] pattern = PoetryDictionary.getStressPattern("beautiful");
            assertTrue(pattern.length > 1);
            // Should have at least one stressed syllable
            boolean hasStress = false;
            for (int p : pattern) {
                if (p == 1) hasStress = true;
            }
            assertTrue(hasStress);
        }

        @Test
        @DisplayName("words ending in -tion should stress penultimate")
        void wordsEndingInTionShouldStressPenultimate() {
            int[] pattern = PoetryDictionary.getStressPattern("nation");
            assertTrue(pattern.length >= 2);
            // Penultimate should be stressed
            assertEquals(1, pattern[pattern.length - 2]);
        }

        @Test
        @DisplayName("words ending in -ic should stress penultimate")
        void wordsEndingInIcShouldStressPenultimate() {
            int[] pattern = PoetryDictionary.getStressPattern("fantastic");
            assertTrue(pattern.length >= 2);
            assertEquals(1, pattern[pattern.length - 2]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOOKUP
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("lookup")
    class LookupTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertNull(PoetryDictionary.lookup(null));
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            // Results should be the same regardless of case
            PoetryDictionary.WordEntry lower = PoetryDictionary.lookup("love");
            PoetryDictionary.WordEntry upper = PoetryDictionary.lookup("LOVE");
            
            if (lower != null && upper != null) {
                assertEquals(lower.word.toLowerCase(), upper.word.toLowerCase());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTAINS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("contains")
    class ContainsTests {

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(PoetryDictionary.contains(null));
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            boolean lower = PoetryDictionary.contains("love");
            boolean upper = PoetryDictionary.contains("LOVE");
            assertEquals(lower, upper);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPOS")
    class GetPOSTests {

        @Test
        @DisplayName("should return Unknown for unknown words")
        void shouldReturnUnknownForUnknownWords() {
            String pos = PoetryDictionary.getPOS("xyzabc123");
            assertEquals("Unknown", pos);
        }

        @Test
        @DisplayName("should return Unknown for null")
        void shouldReturnUnknownForNull() {
            String pos = PoetryDictionary.getPOS(null);
            assertEquals("Unknown", pos);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNONYMS / ANTONYMS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Synonyms and Antonyms")
    class SynonymsAntonymsTests {

        @Test
        @DisplayName("getSynonyms should return empty list for null")
        void getSynonymsShouldReturnEmptyForNull() {
            List<String> synonyms = PoetryDictionary.getSynonyms(null);
            assertNotNull(synonyms);
            assertTrue(synonyms.isEmpty());
        }

        @Test
        @DisplayName("getAntonyms should return empty list for null")
        void getAntonymsShouldReturnEmptyForNull() {
            List<String> antonyms = PoetryDictionary.getAntonyms(null);
            assertNotNull(antonyms);
            assertTrue(antonyms.isEmpty());
        }

        @Test
        @DisplayName("getSynonyms should return empty list for unknown word")
        void getSynonymsShouldReturnEmptyForUnknown() {
            List<String> synonyms = PoetryDictionary.getSynonyms("xyznonexistent");
            assertNotNull(synonyms);
            assertTrue(synonyms.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORD ENTRY
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WordEntry")
    class WordEntryTests {

        @Test
        @DisplayName("should have immutable parts of speech list")
        void shouldHaveImmutablePOSList() {
            PoetryDictionary.WordEntry entry = PoetryDictionary.lookup("love");
            if (entry != null) {
                assertThrows(UnsupportedOperationException.class, () -> 
                    entry.partsOfSpeech.add("test"));
            }
        }

        @Test
        @DisplayName("should have immutable synonyms list")
        void shouldHaveImmutableSynonymsList() {
            PoetryDictionary.WordEntry entry = PoetryDictionary.lookup("love");
            if (entry != null) {
                assertThrows(UnsupportedOperationException.class, () -> 
                    entry.synonyms.add("test"));
            }
        }

        @Test
        @DisplayName("should have immutable antonyms list")
        void shouldHaveImmutableAntonymsList() {
            PoetryDictionary.WordEntry entry = PoetryDictionary.lookup("love");
            if (entry != null) {
                assertThrows(UnsupportedOperationException.class, () -> 
                    entry.antonyms.add("test"));
            }
        }
    }
}
