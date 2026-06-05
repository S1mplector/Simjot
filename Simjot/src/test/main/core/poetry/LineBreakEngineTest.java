/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.poetry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for LineBreakEngine - line break analysis and poetry formatting.
 */
@DisplayName("LineBreakEngine")
class LineBreakEngineTest {

    private LineBreakEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LineBreakEngine();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BREAK CONFIG TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BreakConfig")
    class BreakConfigTests {

        @Test
        @DisplayName("defaultConfig should have standard values")
        void defaultConfigShouldHaveStandardValues() {
            LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.defaultConfig();
            
            assertEquals(10, config.targetSyllables);
            assertEquals(6, config.minSyllables);
            assertEquals(14, config.maxSyllables);
            assertTrue(config.preferEndStopped);
            assertEquals(4, config.linesPerStanza);
        }

        @Test
        @DisplayName("freeVerse should have flexible values")
        void freeVerseShouldHaveFlexibleValues() {
            LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.freeVerse();
            
            assertEquals(0, config.targetSyllables);
            assertEquals(3, config.minSyllables);
            assertEquals(20, config.maxSyllables);
            assertFalse(config.preferEndStopped);
            assertEquals(0, config.linesPerStanza);
        }

        @Test
        @DisplayName("iambicPentameter should target 10 syllables")
        void iambicPentameterShouldTarget10Syllables() {
            LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.iambicPentameter();
            
            assertEquals(10, config.targetSyllables);
            assertEquals(9, config.minSyllables);
            assertEquals(11, config.maxSyllables);
        }

        @Test
        @DisplayName("haiku should have 3 lines per stanza")
        void haikuShouldHave3LinesPerStanza() {
            LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.haiku();
            
            assertEquals(3, config.linesPerStanza);
            assertEquals(5, config.minSyllables);
            assertEquals(7, config.maxSyllables);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANALYSIS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyze")
    class AnalyzeTests {

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(null, null);
            
            assertNotNull(analysis);
            assertEquals(0, analysis.suggestedLineCount);
            assertEquals(0, analysis.suggestedStanzaCount);
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            LineBreakEngine.BreakAnalysis analysis = engine.analyze("", null);
            
            assertNotNull(analysis);
            assertEquals("", analysis.reformattedText);
        }

        @Test
        @DisplayName("should handle blank input")
        void shouldHandleBlankInput() {
            LineBreakEngine.BreakAnalysis analysis = engine.analyze("   ", null);
            
            assertNotNull(analysis);
        }

        @Test
        @DisplayName("should return original text reference")
        void shouldReturnOriginalTextReference() {
            String text = "The quick brown fox jumps over the lazy dog.";
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            assertEquals(text, analysis.originalText);
        }

        @Test
        @DisplayName("should use default config when null")
        void shouldUseDefaultConfigWhenNull() {
            String text = "The quick brown fox jumps over the lazy dog.";
            
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            assertNotNull(analysis);
            assertNotNull(analysis.meterTarget);
        }

        @Test
        @DisplayName("should produce reformatted text")
        void shouldProduceReformattedText() {
            String prose = "The wind blows softly through the trees. Birds sing their morning songs.";
            LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.defaultConfig();
            
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(prose, config);
            
            assertNotNull(analysis.reformattedText);
        }

        @Test
        @DisplayName("should count suggested lines")
        void shouldCountSuggestedLines() {
            String text = "First sentence here. Second sentence now. Third one too. Fourth and more.";
            
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, LineBreakEngine.BreakConfig.defaultConfig());
            
            assertTrue(analysis.suggestedLineCount >= 0);
        }

        @Test
        @DisplayName("should return immutable suggestions list")
        void shouldReturnImmutableSuggestionsList() {
            String text = "Some sample text for analysis.";
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            assertThrows(UnsupportedOperationException.class, () -> 
                analysis.suggestions.add(new LineBreakEngine.BreakSuggestion(0, 
                    LineBreakEngine.BreakType.LINE_BREAK, 1.0, "test")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENJAMBMENT DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("detectEnjambment")
    class DetectEnjambmentTests {

        @Test
        @DisplayName("should detect no enjambment with period")
        void shouldDetectNoEnjambmentWithPeriod() {
            String text = "This is a complete sentence.\nAnother sentence here.";
            
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment(text);
            
            assertTrue(results.isEmpty() || 
                results.stream().allMatch(e -> e.type == LineBreakEngine.EnjambmentType.NONE));
        }

        @Test
        @DisplayName("should detect soft enjambment with comma")
        void shouldDetectSoftEnjambmentWithComma() {
            String text = "The morning sun rises slowly,\nbringing warmth to the valley.";
            
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment(text);
            
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(e -> e.type == LineBreakEngine.EnjambmentType.SOFT));
        }

        @Test
        @DisplayName("should detect hard enjambment without punctuation")
        void shouldDetectHardEnjambmentWithoutPunctuation() {
            String text = "I wandered lonely as a\ncloud that floats on high";
            
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment(text);
            
            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("should detect syntactic enjambment with article")
        void shouldDetectSyntacticEnjambmentWithArticle() {
            String text = "I saw the\nbeautiful sunset";
            
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment(text);
            
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(e -> e.type == LineBreakEngine.EnjambmentType.SYNTACTIC));
        }

        @Test
        @DisplayName("should handle empty text")
        void shouldHandleEmptyText() {
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment("");
            
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("should handle single line")
        void shouldHandleSingleLine() {
            List<LineBreakEngine.EnjambmentInfo> results = engine.detectEnjambment("Just one line");
            
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BREAK TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BreakType")
    class BreakTypeTests {

        @Test
        @DisplayName("should have descriptions for all types")
        void shouldHaveDescriptionsForAllTypes() {
            for (LineBreakEngine.BreakType type : LineBreakEngine.BreakType.values()) {
                assertNotNull(type.description);
                assertFalse(type.description.isEmpty());
            }
        }

        @Test
        @DisplayName("LINE_BREAK should have correct description")
        void lineBreakShouldHaveCorrectDescription() {
            assertEquals("Line break", LineBreakEngine.BreakType.LINE_BREAK.description);
        }

        @Test
        @DisplayName("STANZA_BREAK should have correct description")
        void stanzaBreakShouldHaveCorrectDescription() {
            assertEquals("Stanza break", LineBreakEngine.BreakType.STANZA_BREAK.description);
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
            String text = "Some prose text to analyze for line breaks and poetry formatting.";
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            String summary = engine.getSummary(analysis);
            
            assertNotNull(summary);
            assertTrue(summary.contains("Line Break Analysis"));
        }

        @Test
        @DisplayName("should include line count in summary")
        void shouldIncludeLineCountInSummary() {
            String text = "Words to format into poetry lines.";
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            String summary = engine.getSummary(analysis);
            
            assertTrue(summary.contains("Suggested Lines:"));
        }

        @Test
        @DisplayName("should include stanza count in summary")
        void shouldIncludeStanzaCountInSummary() {
            String text = "Words to format into poetry stanzas.";
            LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, null);
            
            String summary = engine.getSummary(analysis);
            
            assertTrue(summary.contains("Suggested Stanzas:"));
        }
    }
}
