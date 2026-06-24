/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.poetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for VocabularyAnalyzer - word frequency, readability, and vocabulary metrics.
 */
@DisplayName("VocabularyAnalyzer")
class VocabularyAnalyzerTest {

    private VocabularyAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new VocabularyAnalyzer();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyze - Basic")
    class BasicAnalysisTests {

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(null);
            
            assertNotNull(result);
            assertEquals(0, result.totalWords);
            assertEquals(0, result.uniqueWords);
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("");
            
            assertNotNull(result);
            assertEquals(0, result.totalWords);
        }

        @Test
        @DisplayName("should handle blank input")
        void shouldHandleBlankInput() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("   ");
            
            assertNotNull(result);
            assertEquals(0, result.totalWords);
        }

        @Test
        @DisplayName("should count total words correctly")
        void shouldCountTotalWordsCorrectly() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("one two three four five");
            
            assertEquals(5, result.totalWords);
        }

        @Test
        @DisplayName("should count unique words correctly")
        void shouldCountUniqueWordsCorrectly() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("the cat and the dog and the bird");
            
            assertEquals(8, result.totalWords);
            assertEquals(5, result.uniqueWords); // the, cat, and, dog, bird
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VOCABULARY METRICS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Vocabulary Metrics")
    class VocabularyMetricsTests {

        @Test
        @DisplayName("should calculate type-token ratio")
        void shouldCalculateTypeTokenRatio() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("the cat sat on the mat");
            
            // 6 words, 5 unique (the appears twice)
            assertTrue(result.typeTokenRatio > 0);
            assertTrue(result.typeTokenRatio <= 1);
        }

        @Test
        @DisplayName("should detect hapax legomena")
        void shouldDetectHapaxLegomena() {
            // "unique" appears once, "the" appears twice
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("the unique word and the special term");
            
            assertTrue(result.hapaxLegomena > 0);
        }

        @Test
        @DisplayName("should calculate average word length")
        void shouldCalculateAverageWordLength() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("cat dog bird");
            
            // cat=3, dog=3, bird=4, avg=3.33
            assertTrue(result.avgWordLength > 3);
            assertTrue(result.avgWordLength < 4);
        }

        @Test
        @DisplayName("should calculate lexical density")
        void shouldCalculateLexicalDensity() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("The beautiful sunset illuminated the vast horizon");
            
            // Should be between 0 and 1
            assertTrue(result.lexicalDensity >= 0);
            assertTrue(result.lexicalDensity <= 1);
        }

        @Test
        @DisplayName("should calculate vocabulary richness score")
        void shouldCalculateVocabularyRichnessScore() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "The magnificent azure butterfly gracefully descended upon the verdant meadow, " +
                "its iridescent wings shimmering in the golden afternoon sunlight."
            );
            
            // Score should be 0-100
            assertTrue(result.vocabularyRichnessScore >= 0);
            assertTrue(result.vocabularyRichnessScore <= 100);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // READABILITY METRICS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Readability Metrics")
    class ReadabilityMetricsTests {

        @Test
        @DisplayName("should calculate Flesch Reading Ease")
        void shouldCalculateFleschReadingEase() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "The cat sat on the mat. The dog ran in the park."
            );
            
            // Simple text should have high readability score
            assertNotEquals(0.0, result.fleschReadingEase);
        }

        @Test
        @DisplayName("should calculate Flesch-Kincaid Grade")
        void shouldCalculateFleschKincaidGrade() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "The cat sat on the mat. The dog ran in the park."
            );
            
            // Simple text should have low grade level
            assertNotEquals(0.0, result.fleschKincaidGrade);
        }

        @Test
        @DisplayName("complex text should have lower readability")
        void complexTextShouldHaveLowerReadability() {
            VocabularyAnalyzer.VocabularyAnalysis simple = analyzer.analyze(
                "The cat sat. The dog ran."
            );
            VocabularyAnalyzer.VocabularyAnalysis complex = analyzer.analyze(
                "The phenomenological manifestation of consciousness transcends epistemological boundaries."
            );
            
            // Complex text typically has lower Flesch Reading Ease
            assertTrue(complex.fleschReadingEase < simple.fleschReadingEase);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORD FREQUENCY
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Word Frequency")
    class WordFrequencyTests {

        @Test
        @DisplayName("should return top words")
        void shouldReturnTopWords() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "the cat and the dog and the bird and the fish"
            );
            
            assertNotNull(result.topWords);
            assertFalse(result.topWords.isEmpty());
            
            // "the" should be most frequent
            assertEquals("the", result.topWords.get(0).word);
            assertEquals(4, result.topWords.get(0).count);
        }

        @Test
        @DisplayName("should track word frequencies")
        void shouldTrackWordFrequencies() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("word word word other");
            
            assertNotNull(result.wordFrequencies);
            assertEquals(3, result.wordFrequencies.get("word").intValue());
            assertEquals(1, result.wordFrequencies.get("other").intValue());
        }

        @Test
        @DisplayName("should identify rare words")
        void shouldIdentifyRareWords() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "the the the common common unique special"
            );
            
            assertNotNull(result.rareWords);
            // Rare words are those appearing once (excluding function words)
        }

        @Test
        @DisplayName("word frequencies should be immutable")
        void wordFrequenciesShouldBeImmutable() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("test words");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.wordFrequencies.put("new", 1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORD LENGTH DISTRIBUTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Word Length Distribution")
    class WordLengthDistributionTests {

        @Test
        @DisplayName("should calculate word length distribution")
        void shouldCalculateWordLengthDistribution() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("a to cat bird elephant");
            
            assertNotNull(result.wordLengthDistribution);
            assertFalse(result.wordLengthDistribution.isEmpty());
            
            // Should have entries for lengths 1, 2, 3, 4, 8
            assertEquals(1, result.wordLengthDistribution.get(1).intValue()); // a
            assertEquals(1, result.wordLengthDistribution.get(2).intValue()); // to
            assertEquals(1, result.wordLengthDistribution.get(3).intValue()); // cat
            assertEquals(1, result.wordLengthDistribution.get(4).intValue()); // bird
            assertEquals(1, result.wordLengthDistribution.get(8).intValue()); // elephant
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYWORDS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Keywords")
    class KeywordsTests {

        @Test
        @DisplayName("should extract keywords")
        void shouldExtractKeywords() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(
                "Poetry analysis reveals patterns in vocabulary usage. " +
                "Poetry helps express emotions through carefully chosen vocabulary."
            );
            
            assertNotNull(result.keywords);
            // Keywords should be content words, not function words
        }

        @Test
        @DisplayName("keywords should be immutable")
        void keywordsShouldBeImmutable() {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze("test content");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.keywords.add("new"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getSummary")
    class GetSummaryTests {

        @Test
        @DisplayName("should generate summary")
        void shouldGenerateSummary() {
            VocabularyAnalyzer.VocabularyAnalysis analysis = analyzer.analyze(
                "The quick brown fox jumps over the lazy dog."
            );
            
            String summary = analyzer.getSummary(analysis);
            
            assertNotNull(summary);
            assertTrue(summary.contains("Vocabulary Analysis"));
        }

        @Test
        @DisplayName("summary should include word counts")
        void summaryShouldIncludeWordCounts() {
            VocabularyAnalyzer.VocabularyAnalysis analysis = analyzer.analyze("word word test");
            
            String summary = analyzer.getSummary(analysis);
            
            assertTrue(summary.contains("Total Words:"));
            assertTrue(summary.contains("Unique Words:"));
        }

        @Test
        @DisplayName("summary should include readability")
        void summaryShouldIncludeReadability() {
            VocabularyAnalyzer.VocabularyAnalysis analysis = analyzer.analyze(
                "The cat sat on the mat."
            );
            
            String summary = analyzer.getSummary(analysis);
            
            assertTrue(summary.contains("Flesch"));
        }
    }
}
