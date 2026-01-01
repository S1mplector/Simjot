/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for LockUtil - password hashing and verification.
 */
@DisplayName("LockUtil")
class LockUtilTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // SALT GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("newSalt")
    class NewSaltTests {

        @Test
        @DisplayName("should generate non-null salt")
        void shouldGenerateNonNullSalt() {
            String salt = LockUtil.newSalt();
            assertNotNull(salt);
        }

        @Test
        @DisplayName("should generate non-empty salt")
        void shouldGenerateNonEmptySalt() {
            String salt = LockUtil.newSalt();
            assertFalse(salt.isEmpty());
        }

        @RepeatedTest(5)
        @DisplayName("should generate unique salts")
        void shouldGenerateUniqueSalts() {
            String salt1 = LockUtil.newSalt();
            String salt2 = LockUtil.newSalt();
            
            assertNotEquals(salt1, salt2);
        }

        @Test
        @DisplayName("salt should be valid Base64")
        void saltShouldBeValidBase64() {
            String salt = LockUtil.newSalt();
            
            // Should not throw
            assertDoesNotThrow(() -> 
                java.util.Base64.getDecoder().decode(salt));
        }

        @Test
        @DisplayName("salt should decode to 16 bytes")
        void saltShouldDecodeTo16Bytes() {
            String salt = LockUtil.newSalt();
            byte[] decoded = java.util.Base64.getDecoder().decode(salt);
            
            assertEquals(16, decoded.length);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASSWORD HASHING
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("hashPassword")
    class HashPasswordTests {

        @Test
        @DisplayName("should generate hash for password")
        void shouldGenerateHashForPassword() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("testPassword123", salt);
            
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        @Test
        @DisplayName("hash should start with pbkdf2 prefix")
        void hashShouldStartWithPbkdf2Prefix() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("password", salt);
            
            assertTrue(hash.startsWith("pbkdf2$"));
        }

        @Test
        @DisplayName("hash should contain iteration count")
        void hashShouldContainIterationCount() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("password", salt);
            
            String[] parts = hash.split("\\$");
            assertTrue(parts.length >= 4);
            
            // Second part should be iteration count
            int iterations = Integer.parseInt(parts[1]);
            assertTrue(iterations >= 10000);
        }

        @Test
        @DisplayName("same password and salt should produce same hash")
        void samePasswordAndSaltShouldProduceSameHash() {
            String salt = LockUtil.newSalt();
            String hash1 = LockUtil.hashPassword("password", salt);
            String hash2 = LockUtil.hashPassword("password", salt);
            
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("different passwords should produce different hashes")
        void differentPasswordsShouldProduceDifferentHashes() {
            String salt = LockUtil.newSalt();
            String hash1 = LockUtil.hashPassword("password1", salt);
            String hash2 = LockUtil.hashPassword("password2", salt);
            
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("different salts should produce different hashes")
        void differentSaltsShouldProduceDifferentHashes() {
            String salt1 = LockUtil.newSalt();
            String salt2 = LockUtil.newSalt();
            String hash1 = LockUtil.hashPassword("password", salt1);
            String hash2 = LockUtil.hashPassword("password", salt2);
            
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("should handle null password")
        void shouldHandleNullPassword() {
            String salt = LockUtil.newSalt();
            
            assertDoesNotThrow(() -> LockUtil.hashPassword(null, salt));
        }

        @Test
        @DisplayName("should handle null salt")
        void shouldHandleNullSalt() {
            assertDoesNotThrow(() -> LockUtil.hashPassword("password", null));
        }

        @Test
        @DisplayName("should handle empty password")
        void shouldHandleEmptyPassword() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("", salt);
            
            assertNotNull(hash);
            assertTrue(hash.startsWith("pbkdf2$"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASSWORD VERIFICATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("verify")
    class VerifyTests {

        @Test
        @DisplayName("should verify correct password")
        void shouldVerifyCorrectPassword() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("correctPassword", salt);
            
            assertTrue(LockUtil.verify("correctPassword", salt, hash));
        }

        @Test
        @DisplayName("should reject incorrect password")
        void shouldRejectIncorrectPassword() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("correctPassword", salt);
            
            assertFalse(LockUtil.verify("wrongPassword", salt, hash));
        }

        @Test
        @DisplayName("should reject null expected hash")
        void shouldRejectNullExpectedHash() {
            assertFalse(LockUtil.verify("password", "salt", null));
        }

        @Test
        @DisplayName("should reject empty expected hash")
        void shouldRejectEmptyExpectedHash() {
            assertFalse(LockUtil.verify("password", "salt", ""));
        }

        @Test
        @DisplayName("should reject blank expected hash")
        void shouldRejectBlankExpectedHash() {
            assertFalse(LockUtil.verify("password", "salt", "   "));
        }

        @Test
        @DisplayName("should verify empty password if hashed empty")
        void shouldVerifyEmptyPasswordIfHashedEmpty() {
            String salt = LockUtil.newSalt();
            String hash = LockUtil.hashPassword("", salt);
            
            assertTrue(LockUtil.verify("", salt, hash));
        }

        @Test
        @DisplayName("should handle special characters in password")
        void shouldHandleSpecialCharactersInPassword() {
            String salt = LockUtil.newSalt();
            String password = "p@$$w0rd!#%&*()_+-=[]{}|;':\",./<>?";
            String hash = LockUtil.hashPassword(password, salt);
            
            assertTrue(LockUtil.verify(password, salt, hash));
        }

        @Test
        @DisplayName("should handle unicode characters in password")
        void shouldHandleUnicodeCharactersInPassword() {
            String salt = LockUtil.newSalt();
            String password = "密码パスワード пароль";
            String hash = LockUtil.hashPassword(password, salt);
            
            assertTrue(LockUtil.verify(password, salt, hash));
        }

        @Test
        @DisplayName("should handle very long password")
        void shouldHandleVeryLongPassword() {
            String salt = LockUtil.newSalt();
            String password = "a".repeat(1000);
            String hash = LockUtil.hashPassword(password, salt);
            
            assertTrue(LockUtil.verify(password, salt, hash));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security Properties")
    class SecurityTests {

        @Test
        @DisplayName("hash should not contain original password")
        void hashShouldNotContainOriginalPassword() {
            String salt = LockUtil.newSalt();
            String password = "secretPassword123";
            String hash = LockUtil.hashPassword(password, salt);
            
            assertFalse(hash.contains(password));
        }

        @Test
        @DisplayName("similar passwords should have very different hashes")
        void similarPasswordsShouldHaveDifferentHashes() {
            String salt = LockUtil.newSalt();
            String hash1 = LockUtil.hashPassword("password1", salt);
            String hash2 = LockUtil.hashPassword("password2", salt);
            
            // Derived key portions should be completely different
            String[] parts1 = hash1.split("\\$");
            String[] parts2 = hash2.split("\\$");
            
            // Last part is the derived key
            assertNotEquals(parts1[parts1.length - 1], parts2[parts2.length - 1]);
        }
    }
}
