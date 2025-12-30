package main.infrastructure.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DateFormatUtil - date pattern validation and formatting.
 */
@DisplayName("DateFormatUtil")
class DateFormatUtilTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCommonPatterns")
    class GetCommonPatternsTests {

        @Test
        @DisplayName("should return non-empty array of patterns")
        void shouldReturnNonEmptyArray() {
            String[] patterns = DateFormatUtil.getCommonPatterns();
            
            assertNotNull(patterns);
            assertTrue(patterns.length > 0);
        }

        @Test
        @DisplayName("all common patterns should be valid")
        void allCommonPatternsShouldBeValid() {
            String[] patterns = DateFormatUtil.getCommonPatterns();
            
            for (String pattern : patterns) {
                assertTrue(DateFormatUtil.isValidPattern(pattern),
                    "Pattern should be valid: " + pattern);
            }
        }

        @Test
        @DisplayName("should include standard date formats")
        void shouldIncludeStandardFormats() {
            String[] patterns = DateFormatUtil.getCommonPatterns();
            
            assertTrue(containsPattern(patterns, "yyyy-MM-dd"), "Should include ISO format");
        }
        
        private boolean containsPattern(String[] patterns, String target) {
            for (String p : patterns) {
                if (p.equals(target)) return true;
            }
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isValidPattern")
    class IsValidPatternTests {

        @ParameterizedTest(name = "pattern \"{0}\" should be valid")
        @ValueSource(strings = {
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "MM/dd/yyyy",
            "EEE, MMM d, yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "HH:mm",
            "d MMM yyyy"
        })
        void shouldAcceptValidPatterns(String pattern) {
            assertTrue(DateFormatUtil.isValidPattern(pattern));
        }

        @ParameterizedTest(name = "pattern \"{0}\" should be invalid")
        @ValueSource(strings = {
            "not-a-pattern",
            "QQQQ",
            "aaaa-bb-cc"
        })
        void shouldRejectInvalidPatterns(String pattern) {
            assertFalse(DateFormatUtil.isValidPattern(pattern));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should reject null and empty patterns")
        void shouldRejectNullAndEmpty(String pattern) {
            assertFalse(DateFormatUtil.isValidPattern(pattern));
        }

        @Test
        @DisplayName("should reject whitespace-only pattern")
        void shouldRejectWhitespaceOnly() {
            assertFalse(DateFormatUtil.isValidPattern("   "));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FORMAT NOW
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("formatNow")
    class FormatNowTests {

        @Test
        @DisplayName("should format current date with valid pattern")
        void shouldFormatWithValidPattern() {
            String result = DateFormatUtil.formatNow("yyyy-MM-dd");
            
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
                "Result should match yyyy-MM-dd format: " + result);
        }

        @Test
        @DisplayName("should fallback to ISO format for invalid pattern")
        void shouldFallbackForInvalidPattern() {
            String result = DateFormatUtil.formatNow("invalid-pattern");
            
            assertNotNull(result);
            // Should fallback to ISO_LOCAL_DATE format
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
                "Should fallback to ISO format: " + result);
        }

        @Test
        @DisplayName("should handle complex pattern")
        void shouldHandleComplexPattern() {
            String result = DateFormatUtil.formatNow("EEE, MMM d, yyyy");
            
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSE SAMPLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("canParseSample")
    class CanParseSampleTests {

        @Test
        @DisplayName("should parse matching sample and pattern")
        void shouldParseMatchingSample() {
            assertTrue(DateFormatUtil.canParseSample("2024-12-30", "yyyy-MM-dd"));
        }

        @Test
        @DisplayName("should reject non-matching sample")
        void shouldRejectNonMatchingSample() {
            assertFalse(DateFormatUtil.canParseSample("30-12-2024", "yyyy-MM-dd"));
        }

        @Test
        @DisplayName("should reject null sample")
        void shouldRejectNullSample() {
            assertFalse(DateFormatUtil.canParseSample(null, "yyyy-MM-dd"));
        }

        @Test
        @DisplayName("should reject null pattern")
        void shouldRejectNullPattern() {
            assertFalse(DateFormatUtil.canParseSample("2024-12-30", null));
        }

        @Test
        @DisplayName("should handle various valid formats")
        void shouldHandleVariousFormats() {
            assertTrue(DateFormatUtil.canParseSample("30.12.2024", "dd.MM.yyyy"));
            assertTrue(DateFormatUtil.canParseSample("12/30/2024", "MM/dd/yyyy"));
        }
    }
}
