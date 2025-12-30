package main.core.poetry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for RhymeDatabase - rhyme and synonym lookups.
 */
@DisplayName("RhymeDatabase")
class RhymeDatabaseTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // GET RHYMES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getRhymesFor")
    class GetRhymesForTests {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyForNull() {
            List<String> rhymes = RhymeDatabase.getRhymesFor(null);
            assertNotNull(rhymes);
            assertTrue(rhymes.isEmpty());
        }

        @Test
        @DisplayName("should find rhymes for common words")
        void shouldFindRhymesForCommonWords() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("light");
            
            assertNotNull(rhymes);
            // light should rhyme with night, bright, sight, etc.
            assertTrue(rhymes.stream().anyMatch(r -> 
                r.equalsIgnoreCase("night") || 
                r.equalsIgnoreCase("bright") || 
                r.equalsIgnoreCase("sight")));
        }

        @Test
        @DisplayName("should find rhymes for 'day'")
        void shouldFindRhymesForDay() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("day");
            
            assertNotNull(rhymes);
            assertTrue(rhymes.stream().anyMatch(r -> 
                r.equalsIgnoreCase("way") || 
                r.equalsIgnoreCase("say") || 
                r.equalsIgnoreCase("play")));
        }

        @Test
        @DisplayName("should find rhymes for 'love'")
        void shouldFindRhymesForLove() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("love");
            
            assertNotNull(rhymes);
            assertTrue(rhymes.stream().anyMatch(r -> 
                r.equalsIgnoreCase("dove") || 
                r.equalsIgnoreCase("above")));
        }

        @Test
        @DisplayName("should not include the word itself in rhymes")
        void shouldNotIncludeWordItself() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("light");
            
            assertFalse(rhymes.stream().anyMatch(r -> r.equalsIgnoreCase("light")));
        }

        @Test
        @DisplayName("should limit results to reasonable count")
        void shouldLimitResults() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("day");
            
            // Should be capped at 10
            assertTrue(rhymes.size() <= 10);
        }

        @Test
        @DisplayName("should find rhymes for 'dream'")
        void shouldFindRhymesForDream() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("dream");
            
            assertNotNull(rhymes);
            assertTrue(rhymes.stream().anyMatch(r -> 
                r.equalsIgnoreCase("stream") || 
                r.equalsIgnoreCase("beam") ||
                r.equalsIgnoreCase("gleam")));
        }

        @Test
        @DisplayName("should find rhymes for 'heart'")
        void shouldFindRhymesForHeart() {
            List<String> rhymes = RhymeDatabase.getRhymesFor("heart");
            
            assertNotNull(rhymes);
            assertTrue(rhymes.stream().anyMatch(r -> 
                r.equalsIgnoreCase("part") || 
                r.equalsIgnoreCase("start") ||
                r.equalsIgnoreCase("art")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET SYNONYMS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getSynonymsFor")
    class GetSynonymsForTests {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyForNull() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor(null);
            assertNotNull(synonyms);
            assertTrue(synonyms.isEmpty());
        }

        @Test
        @DisplayName("should find synonyms for 'love'")
        void shouldFindSynonymsForLove() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("love");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("affection") || 
                s.equalsIgnoreCase("devotion") ||
                s.equalsIgnoreCase("passion")));
        }

        @Test
        @DisplayName("should find synonyms for 'beautiful'")
        void shouldFindSynonymsForBeautiful() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("beautiful");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("lovely") || 
                s.equalsIgnoreCase("gorgeous") ||
                s.equalsIgnoreCase("stunning")));
        }

        @Test
        @DisplayName("should find synonyms for 'sad'")
        void shouldFindSynonymsForSad() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("sad");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("sorrowful") || 
                s.equalsIgnoreCase("melancholy") ||
                s.equalsIgnoreCase("mournful")));
        }

        @Test
        @DisplayName("should find synonyms for 'happy'")
        void shouldFindSynonymsForHappy() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("happy");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("joyful") || 
                s.equalsIgnoreCase("blissful") ||
                s.equalsIgnoreCase("cheerful")));
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            List<String> lower = RhymeDatabase.getSynonymsFor("love");
            List<String> upper = RhymeDatabase.getSynonymsFor("LOVE");
            
            // Both should return results
            assertEquals(lower.size(), upper.size());
        }

        @Test
        @DisplayName("should find synonyms for 'dark'")
        void shouldFindSynonymsForDark() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("dark");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("dim") || 
                s.equalsIgnoreCase("shadowy") ||
                s.equalsIgnoreCase("gloomy")));
        }

        @Test
        @DisplayName("should find synonyms for 'light'")
        void shouldFindSynonymsForLight() {
            List<String> synonyms = RhymeDatabase.getSynonymsFor("light");
            
            assertNotNull(synonyms);
            assertTrue(synonyms.stream().anyMatch(s -> 
                s.equalsIgnoreCase("glow") || 
                s.equalsIgnoreCase("radiance") ||
                s.equalsIgnoreCase("shine")));
        }
    }
}
