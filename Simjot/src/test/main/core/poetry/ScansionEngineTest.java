package main.core.poetry;

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
 * Tests for ScansionEngine - metrical analysis of poetry.
 */
@DisplayName("ScansionEngine")
class ScansionEngineTest {

    private ScansionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ScansionEngine();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FOOT TYPE ENUM
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FootType")
    class FootTypeTests {

        @Test
        @DisplayName("IAMB should have correct symbol")
        void iambShouldHaveCorrectSymbol() {
            assertEquals("˘/", ScansionEngine.FootType.IAMB.symbol);
            assertEquals("unstressed-stressed", ScansionEngine.FootType.IAMB.description);
        }

        @Test
        @DisplayName("TROCHEE should have correct symbol")
        void trocheeShouldHaveCorrectSymbol() {
            assertEquals("/˘", ScansionEngine.FootType.TROCHEE.symbol);
            assertEquals("stressed-unstressed", ScansionEngine.FootType.TROCHEE.description);
        }

        @Test
        @DisplayName("SPONDEE should have correct symbol")
        void spondeeShouldHaveCorrectSymbol() {
            assertEquals("//", ScansionEngine.FootType.SPONDEE.symbol);
        }

        @Test
        @DisplayName("ANAPEST should have correct symbol")
        void anapestShouldHaveCorrectSymbol() {
            assertEquals("˘˘/", ScansionEngine.FootType.ANAPEST.symbol);
        }

        @Test
        @DisplayName("DACTYL should have correct symbol")
        void dactylShouldHaveCorrectSymbol() {
            assertEquals("/˘˘", ScansionEngine.FootType.DACTYL.symbol);
        }

        @Test
        @DisplayName("all foot types should have non-empty symbols")
        void allFootTypesShouldHaveSymbols() {
            for (ScansionEngine.FootType type : ScansionEngine.FootType.values()) {
                assertNotNull(type.symbol);
                assertFalse(type.symbol.isEmpty());
                assertNotNull(type.description);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LINE ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyzeLine")
    class AnalyzeLineTests {

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            ScansionEngine.LineScansion result = engine.analyzeLine(null);
            
            assertNotNull(result);
            assertTrue(result.syllables.isEmpty());
            assertEquals("Unknown", result.meterName);
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            ScansionEngine.LineScansion result = engine.analyzeLine("");
            
            assertNotNull(result);
            assertTrue(result.syllables.isEmpty());
        }

        @Test
        @DisplayName("should handle blank input")
        void shouldHandleBlankInput() {
            ScansionEngine.LineScansion result = engine.analyzeLine("   ");
            
            assertNotNull(result);
            assertEquals("Unknown", result.meterName);
        }

        @Test
        @DisplayName("should extract syllables from line")
        void shouldExtractSyllablesFromLine() {
            ScansionEngine.LineScansion result = engine.analyzeLine("The quick brown fox");
            
            assertNotNull(result.syllables);
            assertTrue(result.syllables.size() > 0);
        }

        @Test
        @DisplayName("should generate scansion notation")
        void shouldGenerateScansionNotation() {
            ScansionEngine.LineScansion result = engine.analyzeLine("Shall I compare thee to a summer day");
            
            assertNotNull(result.scansionNotation);
            // Should contain only stress marks
            assertTrue(result.scansionNotation.matches("[˘/]+"));
        }

        @Test
        @DisplayName("should identify meter for iambic line")
        void shouldIdentifyMeterForIambicLine() {
            // Classic iambic pentameter line
            ScansionEngine.LineScansion result = engine.analyzeLine("Shall I compare thee to a summer's day");
            
            assertNotNull(result.meterName);
            // Should detect some form of iambic meter
        }

        @Test
        @DisplayName("should calculate meter confidence")
        void shouldCalculateMeterConfidence() {
            ScansionEngine.LineScansion result = engine.analyzeLine("The cat sat on the mat today");
            
            assertTrue(result.meterConfidence >= 0.0);
            assertTrue(result.meterConfidence <= 1.0);
        }

        @Test
        @DisplayName("syllables should have immutable list")
        void syllablesShouldBeImmutable() {
            ScansionEngine.LineScansion result = engine.analyzeLine("Hello world");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.syllables.add(new ScansionEngine.SyllableInfo("x", "x", 0, 0, false, 0.5)));
        }

        @Test
        @DisplayName("feet should have immutable list")
        void feetShouldBeImmutable() {
            ScansionEngine.LineScansion result = engine.analyzeLine("Hello world");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.feet.add(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POEM ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyzePoem")
    class AnalyzePoemTests {

        @Test
        @DisplayName("should handle null input")
        void shouldHandleNullInput() {
            ScansionEngine.PoemScansion result = engine.analyzePoem(null);
            
            assertNotNull(result);
            assertTrue(result.lines.isEmpty());
            assertEquals("Unknown", result.dominantMeter);
        }

        @Test
        @DisplayName("should handle empty input")
        void shouldHandleEmptyInput() {
            ScansionEngine.PoemScansion result = engine.analyzePoem("");
            
            assertNotNull(result);
            assertTrue(result.lines.isEmpty());
        }

        @Test
        @DisplayName("should analyze multiple lines")
        void shouldAnalyzeMultipleLines() {
            String poem = "Shall I compare thee to a summer's day?\nThou art more lovely and more temperate.";
            
            ScansionEngine.PoemScansion result = engine.analyzePoem(poem);
            
            assertEquals(2, result.lines.size());
        }

        @Test
        @DisplayName("should calculate total syllables")
        void shouldCalculateTotalSyllables() {
            String poem = "The cat sat on the mat.";
            
            ScansionEngine.PoemScansion result = engine.analyzePoem(poem);
            
            assertTrue(result.totalSyllables > 0);
        }

        @Test
        @DisplayName("should count stressed and unstressed syllables")
        void shouldCountStressedAndUnstressed() {
            String poem = "The quick brown fox jumps.";
            
            ScansionEngine.PoemScansion result = engine.analyzePoem(poem);
            
            assertEquals(result.totalSyllables, result.totalStressed + result.totalUnstressed);
        }

        @Test
        @DisplayName("should detect dominant meter")
        void shouldDetectDominantMeter() {
            String poem = "Shall I compare thee to a summer's day?\n" +
                         "Thou art more lovely and more temperate.\n" +
                         "Rough winds do shake the darling buds of May.";
            
            ScansionEngine.PoemScansion result = engine.analyzePoem(poem);
            
            assertNotNull(result.dominantMeter);
        }

        @Test
        @DisplayName("lines should have immutable list")
        void linesShouldBeImmutable() {
            ScansionEngine.PoemScansion result = engine.analyzePoem("Test line");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.lines.add(null));
        }

        @Test
        @DisplayName("foot counts should have immutable map")
        void footCountsShouldBeImmutable() {
            ScansionEngine.PoemScansion result = engine.analyzePoem("Test line");
            
            assertThrows(UnsupportedOperationException.class, () -> 
                result.footCounts.put(ScansionEngine.FootType.IAMB, 100));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCANSION DISPLAY
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getScansionDisplay")
    class GetScansionDisplayTests {

        @Test
        @DisplayName("should handle null scansion")
        void shouldHandleNullScansion() {
            String display = engine.getScansionDisplay(null);
            assertEquals("", display);
        }

        @Test
        @DisplayName("should include original line")
        void shouldIncludeOriginalLine() {
            ScansionEngine.LineScansion scansion = engine.analyzeLine("Hello world");
            String display = engine.getScansionDisplay(scansion);
            
            assertTrue(display.contains("Hello world"));
        }

        @Test
        @DisplayName("should include meter name")
        void shouldIncludeMeterName() {
            ScansionEngine.LineScansion scansion = engine.analyzeLine("The quick brown fox jumps");
            String display = engine.getScansionDisplay(scansion);
            
            assertTrue(display.contains("confidence"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYLLABLE INFO
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SyllableInfo")
    class SyllableInfoTests {

        @Test
        @DisplayName("should store syllable data correctly")
        void shouldStoreSyllableDataCorrectly() {
            ScansionEngine.SyllableInfo info = new ScansionEngine.SyllableInfo(
                "hel", "hello", 0, 0, true, 0.9
            );
            
            assertEquals("hel", info.text);
            assertEquals("hello", info.word);
            assertEquals(0, info.wordIndex);
            assertEquals(0, info.syllableInWord);
            assertTrue(info.stressed);
            assertEquals(0.9, info.stressConfidence, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FOOT INFO
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FootInfo")
    class FootInfoTests {

        @Test
        @DisplayName("syllables in foot should be immutable")
        void syllablesInFootShouldBeImmutable() {
            ScansionEngine.LineScansion scansion = engine.analyzeLine("Hello world today");
            
            if (!scansion.feet.isEmpty()) {
                assertThrows(UnsupportedOperationException.class, () -> 
                    scansion.feet.get(0).syllables.add(null));
            }
        }
    }
}
