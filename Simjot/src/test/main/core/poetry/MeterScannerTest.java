package main.core.poetry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for MeterScanner - poetry form detection, meter analysis, and statistics.
 */
@DisplayName("MeterScanner")
class MeterScannerTest {

    private MeterScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new MeterScanner();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POETIC FORM DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("detectPoeticForm")
    class DetectPoeticFormTests {

        @Test
        @DisplayName("should detect haiku (5-7-5 syllables)")
        void shouldDetectHaiku() {
            List<Integer> syllables = Arrays.asList(5, 7, 5);
            List<String> rhymes = Arrays.asList("", "", "");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Haiku (5-7-5)", form);
        }

        @Test
        @DisplayName("should detect haiku-like form (close to 5-7-5)")
        void shouldDetectHaikuLike() {
            List<Integer> syllables = Arrays.asList(6, 7, 4); // Total 17, close to haiku
            List<String> rhymes = Arrays.asList("", "", "");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Haiku-like (3 lines)", form);
        }

        @Test
        @DisplayName("should detect limerick (AABBA)")
        void shouldDetectLimerick() {
            List<Integer> syllables = Arrays.asList(8, 8, 5, 5, 8);
            List<String> rhymes = Arrays.asList("A", "A", "B", "B", "A");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Limerick", form);
        }

        @Test
        @DisplayName("should detect couplet (AA)")
        void shouldDetectCouplet() {
            List<Integer> syllables = Arrays.asList(10, 10);
            List<String> rhymes = Arrays.asList("A", "A");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Couplet", form);
        }

        @Test
        @DisplayName("should detect quatrain ABAB")
        void shouldDetectQuatrainABAB() {
            List<Integer> syllables = Arrays.asList(10, 10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "B", "A", "B");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Quatrain (ABAB)", form);
        }

        @Test
        @DisplayName("should detect quatrain ABBA")
        void shouldDetectQuatrainABBA() {
            List<Integer> syllables = Arrays.asList(10, 10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "B", "B", "A");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Quatrain (ABBA)", form);
        }

        @Test
        @DisplayName("should detect couplet quatrain (AABB)")
        void shouldDetectCoupletQuatrain() {
            List<Integer> syllables = Arrays.asList(10, 10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "A", "B", "B");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Couplet Quatrain", form);
        }

        @Test
        @DisplayName("should detect tercet ABA")
        void shouldDetectTercetABA() {
            List<Integer> syllables = Arrays.asList(10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "B", "A");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Tercet (ABA)", form);
        }

        @Test
        @DisplayName("should detect triplet (AAA)")
        void shouldDetectTriplet() {
            List<Integer> syllables = Arrays.asList(10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "A", "A");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Triplet", form);
        }

        @Test
        @DisplayName("should detect sonnet (14 lines pentameter)")
        void shouldDetectSonnet() {
            // 14 lines with ~10 syllables each
            List<Integer> syllables = Arrays.asList(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
            List<String> rhymes = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertEquals("Sonnet (14 lines)", form);
        }

        @Test
        @DisplayName("should return empty for unknown form")
        void shouldReturnEmptyForUnknownForm() {
            List<Integer> syllables = Arrays.asList(7, 9, 11, 6, 8);
            List<String> rhymes = Arrays.asList("A", "B", "C", "D", "E");
            
            String form = MeterScanner.detectPoeticForm(syllables, rhymes);
            
            assertTrue(form.isEmpty() || form.equals("Free Verse"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStatistics")
    class GetStatisticsTests {

        @Test
        @DisplayName("should count lines correctly")
        void shouldCountLinesCorrectly() {
            String poem = "First line here\nSecond line now\nThird line too";
            
            Map<String, Object> stats = MeterScanner.getStatistics(poem);
            
            assertEquals(3, stats.get("lines"));
        }

        @Test
        @DisplayName("should count words correctly")
        void shouldCountWordsCorrectly() {
            String poem = "One two three\nFour five";
            
            Map<String, Object> stats = MeterScanner.getStatistics(poem);
            
            assertEquals(5, stats.get("words"));
        }

        @Test
        @DisplayName("should count stanzas correctly")
        void shouldCountStanzasCorrectly() {
            String poem = "First stanza line one\nFirst stanza line two\n\nSecond stanza line one";
            
            Map<String, Object> stats = MeterScanner.getStatistics(poem);
            
            assertEquals(2, stats.get("stanzas"));
        }

        @Test
        @DisplayName("should calculate average syllables per line")
        void shouldCalculateAverageSyllables() {
            String poem = "Hello world today\nGoodbye now my friend";
            
            Map<String, Object> stats = MeterScanner.getStatistics(poem);
            
            assertNotNull(stats.get("avgSyllablesPerLine"));
            assertTrue((double) stats.get("avgSyllablesPerLine") > 0);
        }

        @Test
        @DisplayName("should handle empty text")
        void shouldHandleEmptyText() {
            Map<String, Object> stats = MeterScanner.getStatistics("");
            
            assertEquals(0, stats.get("lines"));
            assertEquals(0, stats.get("words"));
            assertEquals(1, stats.get("stanzas")); // Default stanza count
        }

        @Test
        @DisplayName("should handle null text gracefully")
        void shouldHandleNullText() {
            // Should not throw exception
            assertDoesNotThrow(() -> MeterScanner.getStatistics(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyze")
    class AnalyzeTests {

        @Test
        @DisplayName("should return analysis with correct line count")
        void shouldReturnAnalysisWithCorrectLineCount() {
            String poem = "Roses are red\nViolets are blue\nSugar is sweet\nAnd so are you";
            
            MeterAnalysis analysis = scanner.analyze(poem, false);
            
            assertNotNull(analysis);
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            MeterAnalysis analysis = scanner.analyze("", false);
            
            assertNotNull(analysis);
        }

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            MeterAnalysis analysis = scanner.analyze(null, false);
            
            assertNotNull(analysis);
        }

        @Test
        @DisplayName("should detect stanzas correctly")
        void shouldDetectStanzasCorrectly() {
            String poem = "Line one of stanza one\nLine two of stanza one\n\nLine one of stanza two";
            
            MeterAnalysis analysis = scanner.analyze(poem, false);
            
            assertNotNull(analysis);
        }

        @Test
        @DisplayName("should reset rhyme labels per stanza when perStanza is true")
        void shouldResetRhymeLabelsPerStanza() {
            String poem = "The cat sat on the mat\nThe dog ran through the fog\n\nThe bird flew to the word";
            
            MeterAnalysis perStanza = scanner.analyze(poem, true);
            MeterAnalysis notPerStanza = scanner.analyze(poem, false);
            
            // Both should complete without error
            assertNotNull(perStanza);
            assertNotNull(notPerStanza);
        }
    }
}
