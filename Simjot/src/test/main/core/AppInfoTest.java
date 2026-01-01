/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for AppInfo - application metadata.
 */
@DisplayName("AppInfo")
class AppInfoTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("NAME should be Simjot")
        void nameShouldBeSimjot() {
            assertEquals("Simjot", AppInfo.NAME);
        }

        @Test
        @DisplayName("VERSION should not be null or empty")
        void versionShouldNotBeNullOrEmpty() {
            assertNotNull(AppInfo.VERSION);
            assertFalse(AppInfo.VERSION.isEmpty());
        }

        @Test
        @DisplayName("VERSION should follow semver format")
        void versionShouldFollowSemver() {
            // Should match x.y.z pattern
            assertTrue(AppInfo.VERSION.matches("\\d+\\.\\d+\\.\\d+"),
                "Version should match semver format (x.y.z)");
        }

        @Test
        @DisplayName("AUTHOR should not be null or empty")
        void authorShouldNotBeNullOrEmpty() {
            assertNotNull(AppInfo.AUTHOR);
            assertFalse(AppInfo.AUTHOR.isEmpty());
        }

        @Test
        @DisplayName("LICENSE should not be null or empty")
        void licenseShouldNotBeNullOrEmpty() {
            assertNotNull(AppInfo.LICENSE);
            assertFalse(AppInfo.LICENSE.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VERSION STRING
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("versionString")
    class VersionStringTests {

        @Test
        @DisplayName("should start with 'v'")
        void shouldStartWithV() {
            String versionString = AppInfo.versionString();
            assertTrue(versionString.startsWith("v"));
        }

        @Test
        @DisplayName("should contain VERSION")
        void shouldContainVersion() {
            String versionString = AppInfo.versionString();
            assertTrue(versionString.contains(AppInfo.VERSION));
        }

        @Test
        @DisplayName("should match format 'vX.Y.Z'")
        void shouldMatchFormat() {
            String versionString = AppInfo.versionString();
            assertTrue(versionString.matches("v\\d+\\.\\d+\\.\\d+"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FULL TITLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fullTitle")
    class FullTitleTests {

        @Test
        @DisplayName("should contain NAME")
        void shouldContainName() {
            String fullTitle = AppInfo.fullTitle();
            assertTrue(fullTitle.contains(AppInfo.NAME));
        }

        @Test
        @DisplayName("should contain version string")
        void shouldContainVersionString() {
            String fullTitle = AppInfo.fullTitle();
            assertTrue(fullTitle.contains(AppInfo.versionString()));
        }

        @Test
        @DisplayName("should match format 'Name vX.Y.Z'")
        void shouldMatchFormat() {
            String fullTitle = AppInfo.fullTitle();
            assertTrue(fullTitle.matches(AppInfo.NAME + " v\\d+\\.\\d+\\.\\d+"));
        }

        @Test
        @DisplayName("should be 'Simjot vX.Y.Z'")
        void shouldBeSimjotWithVersion() {
            String fullTitle = AppInfo.fullTitle();
            assertTrue(fullTitle.startsWith("Simjot v"));
        }
    }
}
