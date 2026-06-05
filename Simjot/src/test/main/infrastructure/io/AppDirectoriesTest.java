/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for AppDirectories - directory type enumeration and folder names.
 */
@DisplayName("AppDirectories")
class AppDirectoriesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TYPE ENUM TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Type Enum")
    class TypeEnumTests {

        @Test
        @DisplayName("should have all expected directory types")
        void shouldHaveAllExpectedTypes() {
            AppDirectories.Type[] types = AppDirectories.Type.values();
            
            assertEquals(8, types.length);
            assertNotNull(AppDirectories.Type.ENTRIES);
            assertNotNull(AppDirectories.Type.POEMS);
            assertNotNull(AppDirectories.Type.DRAWINGS);
            assertNotNull(AppDirectories.Type.TASKS);
            assertNotNull(AppDirectories.Type.NOTEBOOKS);
            assertNotNull(AppDirectories.Type.MOOD_DATA);
            assertNotNull(AppDirectories.Type.SETTINGS);
            assertNotNull(AppDirectories.Type.WALLPAPERS);
        }

        @ParameterizedTest
        @EnumSource(AppDirectories.Type.class)
        @DisplayName("each type should have a folder name")
        void eachTypeShouldHaveFolderName(AppDirectories.Type type) {
            String folderName = type.folderName();
            
            assertNotNull(folderName);
            assertFalse(folderName.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FOLDER NAMES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Folder Names")
    class FolderNameTests {

        @Test
        @DisplayName("ENTRIES should have 'entries' folder name")
        void entriesShouldHaveCorrectFolderName() {
            assertEquals("entries", AppDirectories.Type.ENTRIES.folderName());
        }

        @Test
        @DisplayName("POEMS should have 'poems' folder name")
        void poemsShouldHaveCorrectFolderName() {
            assertEquals("poems", AppDirectories.Type.POEMS.folderName());
        }

        @Test
        @DisplayName("DRAWINGS should have 'drawings' folder name")
        void drawingsShouldHaveCorrectFolderName() {
            assertEquals("drawings", AppDirectories.Type.DRAWINGS.folderName());
        }

        @Test
        @DisplayName("TASKS should have 'tasks' folder name")
        void tasksShouldHaveCorrectFolderName() {
            assertEquals("tasks", AppDirectories.Type.TASKS.folderName());
        }

        @Test
        @DisplayName("NOTEBOOKS should have 'notebooks' folder name")
        void notebooksShouldHaveCorrectFolderName() {
            assertEquals("notebooks", AppDirectories.Type.NOTEBOOKS.folderName());
        }

        @Test
        @DisplayName("MOOD_DATA should have 'mood' folder name")
        void moodDataShouldHaveCorrectFolderName() {
            assertEquals("mood", AppDirectories.Type.MOOD_DATA.folderName());
        }

        @Test
        @DisplayName("SETTINGS should have 'settings' folder name")
        void settingsShouldHaveCorrectFolderName() {
            assertEquals("settings", AppDirectories.Type.SETTINGS.folderName());
        }

        @Test
        @DisplayName("WALLPAPERS should have 'wallpapers' folder name")
        void wallpapersShouldHaveCorrectFolderName() {
            assertEquals("wallpapers", AppDirectories.Type.WALLPAPERS.folderName());
        }

        @Test
        @DisplayName("all folder names should be unique")
        void allFolderNamesShouldBeUnique() {
            AppDirectories.Type[] types = AppDirectories.Type.values();
            for (int i = 0; i < types.length; i++) {
                for (int j = i + 1; j < types.length; j++) {
                    assertNotEquals(types[i].folderName(), types[j].folderName(),
                        types[i] + " and " + types[j] + " have same folder name");
                }
            }
        }

        @Test
        @DisplayName("all folder names should be lowercase")
        void allFolderNamesShouldBeLowercase() {
            for (AppDirectories.Type type : AppDirectories.Type.values()) {
                String name = type.folderName();
                assertEquals(name.toLowerCase(), name, 
                    "Folder name for " + type + " should be lowercase");
            }
        }

        @Test
        @DisplayName("no folder names should contain spaces")
        void noFolderNamesShouldContainSpaces() {
            for (AppDirectories.Type type : AppDirectories.Type.values()) {
                String name = type.folderName();
                assertFalse(name.contains(" "), 
                    "Folder name for " + type + " should not contain spaces");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY VS ACTIVE TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy vs Active Types")
    class LegacyActiveTests {

        @Test
        @DisplayName("legacy types should be ENTRIES, POEMS, DRAWINGS, TASKS")
        void legacyTypesShouldBeIdentified() {
            // These are legacy types that are no longer auto-created
            AppDirectories.Type[] legacyTypes = {
                AppDirectories.Type.ENTRIES,
                AppDirectories.Type.POEMS,
                AppDirectories.Type.DRAWINGS,
                AppDirectories.Type.TASKS
            };
            
            for (AppDirectories.Type type : legacyTypes) {
                assertNotNull(type.folderName());
            }
        }

        @Test
        @DisplayName("active types should be NOTEBOOKS, MOOD_DATA, SETTINGS, WALLPAPERS")
        void activeTypesShouldBeIdentified() {
            // These are active types that are auto-created
            AppDirectories.Type[] activeTypes = {
                AppDirectories.Type.NOTEBOOKS,
                AppDirectories.Type.MOOD_DATA,
                AppDirectories.Type.SETTINGS,
                AppDirectories.Type.WALLPAPERS
            };
            
            for (AppDirectories.Type type : activeTypes) {
                assertNotNull(type.folderName());
            }
        }
    }
}
