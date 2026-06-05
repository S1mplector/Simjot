/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CryptoConfig - encryption configuration options.
 */
@DisplayName("CryptoConfig")
class CryptoConfigTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // DEFAULT VALUES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfigTests {

        @Test
        @DisplayName("should have STANDARD security level by default")
        void shouldHaveStandardSecurityByDefault() {
            CryptoConfig config = new CryptoConfig();
            
            assertEquals(CryptoConfig.SecurityLevel.STANDARD, config.getSecurityLevel());
        }

        @Test
        @DisplayName("should have compression enabled by default")
        void shouldHaveCompressionEnabledByDefault() {
            CryptoConfig config = new CryptoConfig();
            
            assertTrue(config.isCompressBeforeEncrypt());
        }

        @Test
        @DisplayName("should return correct iterations for security level")
        void shouldReturnCorrectIterations() {
            CryptoConfig config = new CryptoConfig();
            
            assertEquals(100_000, config.getIterations());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FLUENT API
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fluent API")
    class FluentApiTests {

        @Test
        @DisplayName("should chain security level setting")
        void shouldChainSecurityLevelSetting() {
            CryptoConfig config = new CryptoConfig()
                .withSecurityLevel(CryptoConfig.SecurityLevel.HIGH);
            
            assertEquals(CryptoConfig.SecurityLevel.HIGH, config.getSecurityLevel());
        }

        @Test
        @DisplayName("should chain compression setting")
        void shouldChainCompressionSetting() {
            CryptoConfig config = new CryptoConfig()
                .withCompression(false);
            
            assertFalse(config.isCompressBeforeEncrypt());
        }

        @Test
        @DisplayName("should chain multiple settings")
        void shouldChainMultipleSettings() {
            CryptoConfig config = new CryptoConfig()
                .withSecurityLevel(CryptoConfig.SecurityLevel.MAXIMUM)
                .withCompression(false)
                .withIdentifier("test-backup");
            
            assertEquals(CryptoConfig.SecurityLevel.MAXIMUM, config.getSecurityLevel());
            assertFalse(config.isCompressBeforeEncrypt());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY LEVELS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security Levels")
    class SecurityLevelTests {

        @Test
        @DisplayName("STANDARD should have 100,000 iterations")
        void standardShouldHaveCorrectIterations() {
            assertEquals(100_000, CryptoConfig.SecurityLevel.STANDARD.getIterations());
        }

        @Test
        @DisplayName("HIGH should have 250,000 iterations")
        void highShouldHaveCorrectIterations() {
            assertEquals(250_000, CryptoConfig.SecurityLevel.HIGH.getIterations());
        }

        @Test
        @DisplayName("MAXIMUM should have 500,000 iterations")
        void maximumShouldHaveCorrectIterations() {
            assertEquals(500_000, CryptoConfig.SecurityLevel.MAXIMUM.getIterations());
        }

        @Test
        @DisplayName("should have display names")
        void shouldHaveDisplayNames() {
            assertNotNull(CryptoConfig.SecurityLevel.STANDARD.getDisplayName());
            assertNotNull(CryptoConfig.SecurityLevel.HIGH.getDisplayName());
            assertNotNull(CryptoConfig.SecurityLevel.MAXIMUM.getDisplayName());
        }

        @Test
        @DisplayName("should have unique markers")
        void shouldHaveUniqueMarkers() {
            byte standardMarker = CryptoConfig.SecurityLevel.STANDARD.getMarker();
            byte highMarker = CryptoConfig.SecurityLevel.HIGH.getMarker();
            byte maxMarker = CryptoConfig.SecurityLevel.MAXIMUM.getMarker();
            
            assertNotEquals(standardMarker, highMarker);
            assertNotEquals(highMarker, maxMarker);
            assertNotEquals(standardMarker, maxMarker);
        }

        @Test
        @DisplayName("should resolve from marker")
        void shouldResolveFromMarker() {
            for (CryptoConfig.SecurityLevel level : CryptoConfig.SecurityLevel.values()) {
                byte marker = level.getMarker();
                CryptoConfig.SecurityLevel resolved = CryptoConfig.SecurityLevel.fromMarker(marker);
                assertEquals(level, resolved);
            }
        }

        @Test
        @DisplayName("should fallback to STANDARD for invalid marker")
        void shouldFallbackForInvalidMarker() {
            CryptoConfig.SecurityLevel resolved = CryptoConfig.SecurityLevel.fromMarker((byte) 99);
            assertEquals(CryptoConfig.SecurityLevel.STANDARD, resolved);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("forBackups should create appropriate config")
        void forBackupsShouldCreateAppropriateConfig() {
            CryptoConfig config = CryptoConfig.forBackups();
            
            assertNotNull(config);
            // Backups typically have compression disabled to avoid double compression
        }

        @Test
        @DisplayName("constructor with security level should set level")
        void constructorWithSecurityLevelShouldSetLevel() {
            CryptoConfig config = new CryptoConfig(CryptoConfig.SecurityLevel.HIGH);
            
            assertEquals(CryptoConfig.SecurityLevel.HIGH, config.getSecurityLevel());
        }
    }
}
