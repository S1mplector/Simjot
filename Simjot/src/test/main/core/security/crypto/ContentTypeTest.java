package main.core.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for ContentType enum - encrypted content type markers.
 */
@DisplayName("ContentType")
class ContentTypeTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUM VALUES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum Values")
    class EnumValueTests {

        @Test
        @DisplayName("should have all expected content types")
        void shouldHaveAllExpectedContentTypes() {
            ContentType[] types = ContentType.values();
            
            assertEquals(8, types.length);
            assertNotNull(ContentType.ENTRY);
            assertNotNull(ContentType.POEM);
            assertNotNull(ContentType.NOTEBOOK);
            assertNotNull(ContentType.BACKUP);
            assertNotNull(ContentType.SETTINGS);
            assertNotNull(ContentType.BINARY);
            assertNotNull(ContentType.TEXT);
            assertNotNull(ContentType.ATTACHMENT);
        }

        @ParameterizedTest
        @EnumSource(ContentType.class)
        @DisplayName("each type should have a marker")
        void eachTypeShouldHaveMarker(ContentType type) {
            byte marker = type.getMarker();
            assertTrue(marker != 0, "Marker should not be zero for " + type);
        }

        @ParameterizedTest
        @EnumSource(ContentType.class)
        @DisplayName("each type should have a display name")
        void eachTypeShouldHaveDisplayName(ContentType type) {
            String displayName = type.getDisplayName();
            assertNotNull(displayName);
            assertFalse(displayName.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MARKER VALUES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Marker Values")
    class MarkerValueTests {

        @Test
        @DisplayName("ENTRY should have marker 0x01")
        void entryShouldHaveCorrectMarker() {
            assertEquals((byte) 0x01, ContentType.ENTRY.getMarker());
        }

        @Test
        @DisplayName("POEM should have marker 0x02")
        void poemShouldHaveCorrectMarker() {
            assertEquals((byte) 0x02, ContentType.POEM.getMarker());
        }

        @Test
        @DisplayName("BACKUP should have marker 0x03")
        void backupShouldHaveCorrectMarker() {
            assertEquals((byte) 0x03, ContentType.BACKUP.getMarker());
        }

        @Test
        @DisplayName("NOTEBOOK should have marker 0x08")
        void notebookShouldHaveCorrectMarker() {
            assertEquals((byte) 0x08, ContentType.NOTEBOOK.getMarker());
        }

        @Test
        @DisplayName("SETTINGS should have marker 0x04")
        void settingsShouldHaveCorrectMarker() {
            assertEquals((byte) 0x04, ContentType.SETTINGS.getMarker());
        }

        @Test
        @DisplayName("BINARY should have marker 0x05")
        void binaryShouldHaveCorrectMarker() {
            assertEquals((byte) 0x05, ContentType.BINARY.getMarker());
        }

        @Test
        @DisplayName("TEXT should have marker 0x06")
        void textShouldHaveCorrectMarker() {
            assertEquals((byte) 0x06, ContentType.TEXT.getMarker());
        }

        @Test
        @DisplayName("ATTACHMENT should have marker 0x07")
        void attachmentShouldHaveCorrectMarker() {
            assertEquals((byte) 0x07, ContentType.ATTACHMENT.getMarker());
        }

        @Test
        @DisplayName("all markers should be unique")
        void allMarkersShouldBeUnique() {
            ContentType[] types = ContentType.values();
            for (int i = 0; i < types.length; i++) {
                for (int j = i + 1; j < types.length; j++) {
                    assertNotEquals(types[i].getMarker(), types[j].getMarker(),
                        types[i] + " and " + types[j] + " have same marker");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FROM MARKER
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fromMarker")
    class FromMarkerTests {

        @ParameterizedTest
        @EnumSource(ContentType.class)
        @DisplayName("should resolve each type from its marker")
        void shouldResolveEachTypeFromMarker(ContentType type) {
            byte marker = type.getMarker();
            ContentType resolved = ContentType.fromMarker(marker);
            
            assertEquals(type, resolved);
        }

        @Test
        @DisplayName("should return BINARY for unknown marker")
        void shouldReturnBinaryForUnknownMarker() {
            ContentType resolved = ContentType.fromMarker((byte) 0xFF);
            
            assertEquals(ContentType.BINARY, resolved);
        }

        @Test
        @DisplayName("should return BINARY for zero marker")
        void shouldReturnBinaryForZeroMarker() {
            ContentType resolved = ContentType.fromMarker((byte) 0x00);
            
            assertEquals(ContentType.BINARY, resolved);
        }

        @Test
        @DisplayName("should return BINARY for negative marker")
        void shouldReturnBinaryForNegativeMarker() {
            ContentType resolved = ContentType.fromMarker((byte) -1);
            
            assertEquals(ContentType.BINARY, resolved);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DISPLAY NAMES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Display Names")
    class DisplayNameTests {

        @Test
        @DisplayName("ENTRY should have 'Journal Entry' display name")
        void entryShouldHaveCorrectDisplayName() {
            assertEquals("Journal Entry", ContentType.ENTRY.getDisplayName());
        }

        @Test
        @DisplayName("POEM should have 'Poem' display name")
        void poemShouldHaveCorrectDisplayName() {
            assertEquals("Poem", ContentType.POEM.getDisplayName());
        }

        @Test
        @DisplayName("BACKUP should have 'Backup Archive' display name")
        void backupShouldHaveCorrectDisplayName() {
            assertEquals("Backup Archive", ContentType.BACKUP.getDisplayName());
        }
    }
}
