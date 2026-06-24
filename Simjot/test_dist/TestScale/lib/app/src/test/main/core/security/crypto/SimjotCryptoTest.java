/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SimjotCrypto - encryption, decryption, 
 * header parsing, and password validation.
 */
@DisplayName("SimjotCrypto")
class SimjotCryptoTest {

    private SimjotCrypto crypto;
    
    @BeforeEach
    void setUp() {
        crypto = new SimjotCrypto();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BASIC ENCRYPTION/DECRYPTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("String Encryption/Decryption")
    class StringEncryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt a simple string")
        void shouldEncryptAndDecryptSimpleString() throws CryptoException {
            String original = "Hello, Simjot!";
            String password = "testPassword123";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should encrypt and decrypt unicode text")
        void shouldEncryptAndDecryptUnicodeText() throws CryptoException {
            String original = "Hello 世界! Привет мир! 🎉";
            String password = "unicodePassword";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should encrypt and decrypt long text")
        void shouldEncryptAndDecryptLongText() throws CryptoException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("This is line ").append(i).append(" of the test document.\n");
            }
            String original = sb.toString();
            String password = "longTextPassword";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @ParameterizedTest(name = "content type: {0}")
        @ValueSource(strings = {"ENTRY", "POEM", "BACKUP", "NOTEBOOK"})
        @DisplayName("should work with all content types")
        void shouldWorkWithAllContentTypes(String contentTypeName) throws CryptoException {
            ContentType type = ContentType.valueOf(contentTypeName);
            String original = "Test content for " + contentTypeName;
            String password = "typeTestPassword";
            
            byte[] encrypted = crypto.encrypt(original, password, type);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }
    }

    @Nested
    @DisplayName("Binary Data Encryption")
    class BinaryEncryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt binary data")
        void shouldEncryptAndDecryptBinaryData() throws CryptoException {
            byte[] original = new byte[1024];
            new SecureRandom().nextBytes(original);
            String password = "binaryPassword";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            byte[] decrypted = crypto.decrypt(encrypted, password);
            
            assertArrayEquals(original, decrypted);
        }

        @Test
        @DisplayName("should encrypt and decrypt empty byte array")
        void shouldRejectEmptyData() {
            byte[] empty = new byte[0];
            String password = "password";
            
            assertThrows(CryptoException.class, () -> 
                crypto.encrypt(empty, password, ContentType.ENTRY));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASSWORD VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Password Validation")
    class PasswordValidationTests {

        @Test
        @DisplayName("should reject null password for encryption")
        void shouldRejectNullPasswordForEncryption() {
            assertThrows(CryptoException.class, () -> 
                crypto.encrypt("test", null, ContentType.ENTRY));
        }

        @Test
        @DisplayName("should reject empty password for encryption")
        void shouldRejectEmptyPasswordForEncryption() {
            assertThrows(CryptoException.class, () -> 
                crypto.encrypt("test", "", ContentType.ENTRY));
        }

        @Test
        @DisplayName("should fail decryption with wrong password")
        void shouldFailDecryptionWithWrongPassword() throws CryptoException {
            String original = "Secret message";
            byte[] encrypted = crypto.encrypt(original, "correctPassword", ContentType.ENTRY);
            
            assertThrows(CryptoException.class, () -> 
                crypto.decrypt(encrypted, "wrongPassword"));
        }

        @Test
        @DisplayName("should validate correct password")
        void shouldValidateCorrectPassword() throws CryptoException {
            String password = "testPassword";
            byte[] encrypted = crypto.encrypt("test", password, ContentType.ENTRY);
            
            assertTrue(crypto.validatePassword(encrypted, password));
        }

        @Test
        @DisplayName("should invalidate wrong password")
        void shouldInvalidateWrongPassword() throws CryptoException {
            byte[] encrypted = crypto.encrypt("test", "correctPassword", ContentType.ENTRY);
            
            assertFalse(crypto.validatePassword(encrypted, "wrongPassword"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HEADER INSPECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Header Inspection")
    class HeaderInspectionTests {

        @Test
        @DisplayName("should detect Simjot encrypted data")
        void shouldDetectSimjotEncryptedData() throws CryptoException {
            byte[] encrypted = crypto.encrypt("test", "password", ContentType.ENTRY);
            
            assertTrue(crypto.isSimjotEncrypted(encrypted));
        }

        @Test
        @DisplayName("should reject non-Simjot data")
        void shouldRejectNonSimjotData() {
            byte[] randomData = "This is not encrypted data".getBytes(StandardCharsets.UTF_8);
            
            assertFalse(crypto.isSimjotEncrypted(randomData));
        }

        @Test
        @DisplayName("should reject null data")
        void shouldRejectNullData() {
            assertFalse(crypto.isSimjotEncrypted((byte[]) null));
        }

        @Test
        @DisplayName("should reject data too short")
        void shouldRejectDataTooShort() {
            byte[] shortData = new byte[10];
            
            assertFalse(crypto.isSimjotEncrypted(shortData));
        }

        @Test
        @DisplayName("should read header from encrypted data")
        void shouldReadHeaderFromEncryptedData() throws CryptoException {
            byte[] encrypted = crypto.encrypt("test", "password", ContentType.POEM);
            
            CryptoHeader header = crypto.readHeader(encrypted);
            
            assertNotNull(header);
            assertEquals(ContentType.POEM, header.getContentType());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRYPTO CONFIG TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Configuration Options")
    class ConfigurationTests {

        @Test
        @DisplayName("should work with compression disabled")
        void shouldWorkWithCompressionDisabled() throws CryptoException {
            CryptoConfig config = new CryptoConfig().withCompression(false);
            String original = "Test without compression";
            String password = "password";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY, config);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should work with high security level")
        void shouldWorkWithHighSecurityLevel() throws CryptoException {
            CryptoConfig config = new CryptoConfig()
                .withSecurityLevel(CryptoConfig.SecurityLevel.HIGH);
            String original = "High security content";
            String password = "password";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY, config);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should preserve security level in header")
        void shouldPreserveSecurityLevelInHeader() throws CryptoException {
            CryptoConfig config = new CryptoConfig()
                .withSecurityLevel(CryptoConfig.SecurityLevel.HIGH);
            byte[] encrypted = crypto.encrypt("test", "password", ContentType.ENTRY, config);
            
            CryptoHeader header = crypto.readHeader(encrypted);
            
            assertEquals(CryptoConfig.SecurityLevel.HIGH, header.getSecurityLevel());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE ENCRYPTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("File Encryption")
    class FileEncryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt file")
        void shouldEncryptAndDecryptFile() throws CryptoException, IOException {
            // Create temp files
            File inputFile = Files.createTempFile("simjot_test_input", ".txt").toFile();
            File encryptedFile = Files.createTempFile("simjot_test_encrypted", ".sjcrypt").toFile();
            File decryptedFile = Files.createTempFile("simjot_test_decrypted", ".txt").toFile();
            
            try {
                // Write test content
                String original = "File content to encrypt";
                Files.writeString(inputFile.toPath(), original);
                
                // Encrypt
                crypto.encryptFile(inputFile, encryptedFile, "password", ContentType.ENTRY);
                
                // Verify encrypted file exists and is different
                assertTrue(encryptedFile.exists());
                assertTrue(encryptedFile.length() > 0);
                
                // Decrypt
                crypto.decryptFile(encryptedFile, decryptedFile, "password");
                
                // Verify decrypted content
                String decrypted = Files.readString(decryptedFile.toPath());
                assertEquals(original, decrypted);
                
            } finally {
                // Cleanup
                inputFile.delete();
                encryptedFile.delete();
                decryptedFile.delete();
            }
        }

        @Test
        @DisplayName("should detect encrypted file")
        void shouldDetectEncryptedFile() throws CryptoException, IOException {
            File encryptedFile = Files.createTempFile("simjot_encrypted", ".sjcrypt").toFile();
            
            try {
                byte[] encrypted = crypto.encrypt("test", "password", ContentType.ENTRY);
                Files.write(encryptedFile.toPath(), encrypted);
                
                assertTrue(crypto.isSimjotEncrypted(encryptedFile));
            } finally {
                encryptedFile.delete();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security Properties")
    class SecurityTests {

        @Test
        @DisplayName("encrypting same data twice should produce different ciphertext")
        void shouldProduceDifferentCiphertextForSameData() throws CryptoException {
            String original = "Same content";
            String password = "samePassword";
            
            byte[] encrypted1 = crypto.encrypt(original, password, ContentType.ENTRY);
            byte[] encrypted2 = crypto.encrypt(original, password, ContentType.ENTRY);
            
            // Ciphertext should be different due to random IV
            assertFalse(java.util.Arrays.equals(encrypted1, encrypted2));
            
            // But both should decrypt to the same plaintext
            assertEquals(original, crypto.decryptToString(encrypted1, password));
            assertEquals(original, crypto.decryptToString(encrypted2, password));
        }

        @Test
        @DisplayName("should handle tampered data")
        void shouldRejectTamperedData() throws CryptoException {
            byte[] encrypted = crypto.encrypt("test", "password", ContentType.ENTRY);
            
            // Tamper with the ciphertext (not header)
            int tamperedIndex = encrypted.length - 10;
            encrypted[tamperedIndex] ^= 0xFF;
            
            // Should fail authentication
            assertThrows(CryptoException.class, () -> 
                crypto.decrypt(encrypted, "password"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle special characters in password")
        void shouldHandleSpecialCharactersInPassword() throws CryptoException {
            String password = "p@$$w0rd!#$%^&*(){}[]|\\:\";<>,.?/~`";
            String original = "Secret content";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should handle very long password")
        void shouldHandleVeryLongPassword() throws CryptoException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("longPassword");
            }
            String password = sb.toString();
            String original = "Content with long password";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("should handle single character password")
        void shouldHandleSingleCharacterPassword() throws CryptoException {
            String password = "a";
            String original = "Content";
            
            byte[] encrypted = crypto.encrypt(original, password, ContentType.ENTRY);
            String decrypted = crypto.decryptToString(encrypted, password);
            
            assertEquals(original, decrypted);
        }
    }
}
