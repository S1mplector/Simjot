package main.core.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CryptoHeader - encrypted file header serialization and parsing.
 */
@DisplayName("CryptoHeader")
class CryptoHeaderTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create header with default values")
        void shouldCreateHeaderWithDefaultValues() {
            CryptoHeader header = new CryptoHeader.Builder().build();
            
            assertNotNull(header);
            assertEquals(ContentType.BINARY, header.getContentType());
            assertEquals(CryptoConfig.SecurityLevel.STANDARD, header.getSecurityLevel());
        }

        @Test
        @DisplayName("should set content type")
        void shouldSetContentType() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .contentType(ContentType.POEM)
                    .build();
            
            assertEquals(ContentType.POEM, header.getContentType());
        }

        @Test
        @DisplayName("should set security level")
        void shouldSetSecurityLevel() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .securityLevel(CryptoConfig.SecurityLevel.HIGH)
                    .build();
            
            assertEquals(CryptoConfig.SecurityLevel.HIGH, header.getSecurityLevel());
        }

        @Test
        @DisplayName("should set compressed flag")
        void shouldSetCompressedFlag() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .compressed(true)
                    .build();
            
            assertTrue(header.isCompressed());
        }

        @Test
        @DisplayName("should set metadata flag")
        void shouldSetMetadataFlag() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .hasMetadata(true)
                    .build();
            
            assertTrue(header.hasMetadata());
        }

        @Test
        @DisplayName("should set identifier")
        void shouldSetIdentifier() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .identifier("test-file-123")
                    .build();
            
            assertEquals("test-file-123", header.getIdentifier());
        }

        @Test
        @DisplayName("should set sizes")
        void shouldSetSizes() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .originalSize(1000)
                    .encryptedSize(1024)
                    .build();
            
            assertEquals(1000, header.getOriginalSize());
            assertEquals(1024, header.getEncryptedSize());
        }

        @Test
        @DisplayName("should set timestamp")
        void shouldSetTimestamp() {
            long timestamp = System.currentTimeMillis();
            CryptoHeader header = new CryptoHeader.Builder()
                    .timestamp(timestamp)
                    .build();
            
            assertEquals(timestamp, header.getTimestamp());
        }

        @Test
        @DisplayName("should set salt")
        void shouldSetSalt() {
            byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
            salt[0] = 0x42;
            
            CryptoHeader header = new CryptoHeader.Builder()
                    .salt(salt)
                    .build();
            
            byte[] retrievedSalt = header.getSalt();
            assertEquals(0x42, retrievedSalt[0]);
        }

        @Test
        @DisplayName("should set IV")
        void shouldSetIV() {
            byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
            iv[0] = 0x24;
            
            CryptoHeader header = new CryptoHeader.Builder()
                    .iv(iv)
                    .build();
            
            byte[] retrievedIv = header.getIv();
            assertEquals(0x24, retrievedIv[0]);
        }

        @Test
        @DisplayName("should apply config from CryptoConfig")
        void shouldApplyConfigFromCryptoConfig() {
            CryptoConfig config = CryptoConfig.builder()
                    .securityLevel(CryptoConfig.SecurityLevel.MAXIMUM)
                    .compressBeforeEncrypt(true)
                    .includeMetadata(true)
                    .customIdentifier("from-config")
                    .build();
            
            CryptoHeader header = new CryptoHeader.Builder()
                    .fromConfig(config)
                    .build();
            
            assertEquals(CryptoConfig.SecurityLevel.MAXIMUM, header.getSecurityLevel());
            assertTrue(header.isCompressed());
            assertTrue(header.hasMetadata());
            assertEquals("from-config", header.getIdentifier());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("should serialize to bytes")
        void shouldSerializeToBytes() throws IOException {
            CryptoHeader header = new CryptoHeader.Builder()
                    .contentType(ContentType.ENTRY)
                    .originalSize(500)
                    .build();
            
            byte[] bytes = header.toBytes();
            
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
        }

        @Test
        @DisplayName("should serialize for signing")
        void shouldSerializeForSigning() throws IOException {
            CryptoHeader header = new CryptoHeader.Builder().build();
            
            byte[] forSigning = header.toBytesForSigning();
            byte[] full = header.toBytes();
            
            // For signing should be shorter by signature length
            assertEquals(full.length - CryptoConfig.HEADER_SIGNATURE_BYTES, forSigning.length);
        }

        @Test
        @DisplayName("serialized header should start with magic bytes")
        void serializedHeaderShouldStartWithMagicBytes() throws IOException {
            CryptoHeader header = new CryptoHeader.Builder().build();
            byte[] bytes = header.toBytes();
            
            // Check magic bytes
            for (int i = 0; i < CryptoConfig.MAGIC_BYTES.length; i++) {
                assertEquals(CryptoConfig.MAGIC_BYTES[i], bytes[i]);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DESERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deserialization")
    class DeserializationTests {

        @Test
        @DisplayName("should deserialize from bytes")
        void shouldDeserializeFromBytes() throws IOException {
            CryptoHeader original = new CryptoHeader.Builder()
                    .contentType(ContentType.POEM)
                    .securityLevel(CryptoConfig.SecurityLevel.HIGH)
                    .originalSize(1234)
                    .encryptedSize(1300)
                    .identifier("test-poem")
                    .compressed(true)
                    .build();
            
            byte[] bytes = original.toBytes();
            CryptoHeader parsed = CryptoHeader.fromBytes(bytes);
            
            assertEquals(ContentType.POEM, parsed.getContentType());
            assertEquals(CryptoConfig.SecurityLevel.HIGH, parsed.getSecurityLevel());
            assertEquals(1234, parsed.getOriginalSize());
            assertEquals(1300, parsed.getEncryptedSize());
            assertEquals("test-poem", parsed.getIdentifier());
            assertTrue(parsed.isCompressed());
        }

        @Test
        @DisplayName("should throw on null input")
        void shouldThrowOnNullInput() {
            assertThrows(IOException.class, () -> CryptoHeader.fromBytes(null));
        }

        @Test
        @DisplayName("should throw on too short input")
        void shouldThrowOnTooShortInput() {
            byte[] tooShort = new byte[10];
            assertThrows(IOException.class, () -> CryptoHeader.fromBytes(tooShort));
        }

        @Test
        @DisplayName("should throw on invalid magic bytes")
        void shouldThrowOnInvalidMagicBytes() {
            byte[] invalid = new byte[200];
            invalid[0] = 'X'; // Wrong magic
            
            assertThrows(IOException.class, () -> CryptoHeader.fromBytes(invalid));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROUND TRIP
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Round Trip")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all fields in round trip")
        void shouldPreserveAllFieldsInRoundTrip() throws IOException {
            byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
            byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
            for (int i = 0; i < salt.length; i++) salt[i] = (byte) i;
            for (int i = 0; i < iv.length; i++) iv[i] = (byte) (i * 2);
            
            long timestamp = System.currentTimeMillis();
            
            CryptoHeader original = new CryptoHeader.Builder()
                    .contentType(ContentType.BACKUP)
                    .securityLevel(CryptoConfig.SecurityLevel.MAXIMUM)
                    .compressed(true)
                    .hasMetadata(true)
                    .salt(salt)
                    .iv(iv)
                    .originalSize(999999)
                    .encryptedSize(1000100)
                    .timestamp(timestamp)
                    .identifier("round-trip-test-identifier")
                    .build();
            
            byte[] bytes = original.toBytes();
            CryptoHeader parsed = CryptoHeader.fromBytes(bytes);
            
            assertEquals(original.getContentType(), parsed.getContentType());
            assertEquals(original.getSecurityLevel(), parsed.getSecurityLevel());
            assertEquals(original.isCompressed(), parsed.isCompressed());
            assertEquals(original.hasMetadata(), parsed.hasMetadata());
            assertArrayEquals(original.getSalt(), parsed.getSalt());
            assertArrayEquals(original.getIv(), parsed.getIv());
            assertEquals(original.getOriginalSize(), parsed.getOriginalSize());
            assertEquals(original.getEncryptedSize(), parsed.getEncryptedSize());
            assertEquals(original.getTimestamp(), parsed.getTimestamp());
            assertEquals(original.getIdentifier(), parsed.getIdentifier());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Getters")
    class GetterTests {

        @Test
        @DisplayName("getVersionString should format correctly")
        void getVersionStringShouldFormatCorrectly() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .versionMajor(1)
                    .versionMinor(2)
                    .build();
            
            assertEquals("1.2", header.getVersionString());
        }

        @Test
        @DisplayName("getSalt should return copy")
        void getSaltShouldReturnCopy() {
            byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
            salt[0] = 0x42;
            
            CryptoHeader header = new CryptoHeader.Builder()
                    .salt(salt)
                    .build();
            
            byte[] retrieved = header.getSalt();
            retrieved[0] = 0x00; // Modify the copy
            
            // Original should be unchanged
            assertEquals(0x42, header.getSalt()[0]);
        }

        @Test
        @DisplayName("getIv should return copy")
        void getIvShouldReturnCopy() {
            byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
            iv[0] = 0x24;
            
            CryptoHeader header = new CryptoHeader.Builder()
                    .iv(iv)
                    .build();
            
            byte[] retrieved = header.getIv();
            retrieved[0] = 0x00; // Modify the copy
            
            // Original should be unchanged
            assertEquals(0x24, header.getIv()[0]);
        }

        @Test
        @DisplayName("getHeaderSize should calculate correctly")
        void getHeaderSizeShouldCalculateCorrectly() {
            CryptoHeader withoutId = new CryptoHeader.Builder().build();
            CryptoHeader withId = new CryptoHeader.Builder()
                    .identifier("test")
                    .build();
            
            assertTrue(withId.getHeaderSize() > withoutId.getHeaderSize());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TO STRING
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should include content type")
        void shouldIncludeContentType() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .contentType(ContentType.ENTRY)
                    .build();
            
            String str = header.toString();
            
            assertTrue(str.contains("Journal Entry"));
        }

        @Test
        @DisplayName("should include security level")
        void shouldIncludeSecurityLevel() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .securityLevel(CryptoConfig.SecurityLevel.HIGH)
                    .build();
            
            String str = header.toString();
            
            assertTrue(str.contains("High"));
        }

        @Test
        @DisplayName("should include sizes")
        void shouldIncludeSizes() {
            CryptoHeader header = new CryptoHeader.Builder()
                    .originalSize(1000)
                    .encryptedSize(1024)
                    .build();
            
            String str = header.toString();
            
            assertTrue(str.contains("1000"));
            assertTrue(str.contains("1024"));
        }
    }
}
