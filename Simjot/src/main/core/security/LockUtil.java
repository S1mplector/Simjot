/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.security;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Utility class for password hashing and verification using secure cryptographic algorithms.
 * 
 * <p>This class provides secure password storage functionality using PBKDF2 (Password-Based
 * Key Derivation Function 2) with HMAC-SHA256. It includes backward compatibility with
 * legacy SHA-256 hashes for existing users.</p>
 * 
 * <p><strong>Security Features:</strong></p>
 * <ul>
 *   <li>PBKDF2 with 120,000 iterations for resistance against brute force attacks</li>
 *   <li>256-bit derived key length for strong security</li>
 *   <li>Cryptographically secure random salt generation</li>
 *   <li>Constant-time string comparison to prevent timing attacks</li>
 *   <li>Legacy SHA-256 support for backward compatibility</li>
 * </ul>
 * 
 * <p><strong>Hash Format:</strong></p>
 * <pre>pbkdf2$iterations$salt$derivedKey</pre>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Hash a new password
 * String salt = LockUtil.newSalt();
 * String hash = LockUtil.hashPassword("userPassword123", salt);
 * 
 * // Verify a password
 * boolean isValid = LockUtil.verify("userPassword123", salt, hash);
 * }</pre>
 * 
 * @author S1mplector
 * @since 1.0.0
 */
public final class LockUtil {
    
    /**
     * Cryptographically secure random number generator for salt generation.
     * Uses the default SecureRandom algorithm which is typically SHA1PRNG or similar.
     */
    private static final SecureRandom RNG = new SecureRandom();
    
    /**
     * Number of PBKDF2 iterations to perform.
     * 120,000 iterations provide strong resistance against brute force attacks
     * while maintaining reasonable performance on modern hardware.
     */
    private static final int PBKDF2_ITERATIONS = 120_000;
    
    /**
     * Length of the derived key in bits.
     * 256 bits provides strong security while being compatible with most systems.
     */
    private static final int PBKDF2_KEY_LEN_BITS = 256;
    
    /**
     * Prefix used to identify PBKDF2 hashes in the stored hash string.
     * This allows the verification method to distinguish between PBKDF2
     * and legacy SHA-256 hashes.
     */
    private static final String PBKDF2_PREFIX = "pbkdf2";

    /** Private constructor to prevent instantiation of utility class. */
    private LockUtil() {}

    /**
     * Generates a new cryptographically secure random salt for password hashing.
     * 
     * <p>The salt is 16 bytes (128 bits) long and encoded using Base64.
     * A unique salt should be generated for each password to prevent
     * rainbow table attacks.</p>
     * 
     * @return A Base64-encoded 16-byte salt string
     */
    public static String newSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /**
     * Hashes a password using PBKDF2 with the default number of iterations.
     * 
     * <p>This method uses the configured PBKDF2_ITERATIONS (120,000) for
     * optimal security. The resulting hash includes the iteration count,
     * salt, and derived key in a structured format.</p>
     * 
     * @param password The password to hash. Null values are treated as empty strings.
     * @param salt The salt to use for hashing. If null/blank, a new salt is generated.
     * @return A formatted hash string containing algorithm, iterations, salt, and derived key
     * @throws RuntimeException if the hashing algorithm is not available
     */
    public static String hashPassword(String password, String salt) {
        return hashPassword(password, salt, PBKDF2_ITERATIONS);
    }

    /**
     * Hashes a password using PBKDF2 with a specified number of iterations.
     * 
     * <p>This private method allows for custom iteration counts while maintaining
     * the same hashing algorithm. It includes safety checks to ensure a minimum
     * of 10,000 iterations even if a lower value is specified.</p>
     * 
     * @param password The password to hash. Null values are treated as empty strings.
     * @param salt The salt to use for hashing. Null values are treated as empty strings.
     * @param iterations The number of PBKDF2 iterations to perform
     * @return A formatted hash string in the format: pbkdf2$iterations$salt$derivedKey
     * @throws RuntimeException if the hashing algorithm is not available
     */
    private static String hashPassword(String password, String salt, int iterations) {
        if (password == null) password = "";
        if (salt == null || salt.isBlank()) {
            salt = newSalt();
        }
        try {
            char[] pwdChars = password.toCharArray();
            byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
            KeySpec spec = new PBEKeySpec(pwdChars, saltBytes, Math.max(10_000, iterations), PBKDF2_KEY_LEN_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = skf.generateSecret(spec).getEncoded();
            return PBKDF2_PREFIX + "$" + iterations + "$" + salt + "$" + Base64.getEncoder().encodeToString(derived);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Unable to hash password", e);
        }
    }

    /**
     * Verifies a candidate password against a stored hash.
     * 
     * <p>This method automatically detects whether the stored hash is in the new
     * PBKDF2 format or the legacy SHA-256 format and verifies accordingly.
     * This ensures backward compatibility with existing user accounts.</p>
     * 
     * @param candidate The password to verify. Null values are handled gracefully.
     * @param salt The salt that was used to hash the original password.
     * @param expectedHash The stored hash to verify against.
     * @return true if the candidate password matches the stored hash, false otherwise
     */
    public static boolean verify(String candidate, String salt, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) return false;
        if (expectedHash.startsWith(PBKDF2_PREFIX + "$")) {
            String[] parts = expectedHash.split("\\$", 4);
            if (parts.length == 4) {
                int iters = PBKDF2_ITERATIONS;
                try { iters = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                String storedSalt = parts[2];
                String computed = hashPassword(candidate, storedSalt, iters);
                return constantTimeEquals(expectedHash, computed);
            }
        }
        // Legacy SHA-256 fallback for backward compatibility
        String legacy = legacyHash(candidate, salt);
        return constantTimeEquals(expectedHash, legacy);
    }

    /**
     * Compares two strings in constant time to prevent timing attacks.
     * 
     * <p>This method performs a byte-by-byte comparison that takes the same
     * amount of time regardless of where the first difference occurs,
     * preventing attackers from gaining information through timing analysis.</p>
     * 
     * @param a The first string to compare
     * @param b The second string to compare
     * @return true if the strings are equal, false otherwise
     */
    private static boolean constantTimeEquals(String a, String b){
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= (ba[i] ^ bb[i]);
        return r == 0;
    }

    /**
     * Legacy SHA-256 hash method for backward compatibility.
     * 
     * <p>This method is used to verify passwords that were hashed using the
     * old SHA-256 algorithm before PBKDF2 was implemented. New passwords
     * should always use PBKDF2 for better security.</p>
     * 
     * <p>The legacy format is: SHA-256(salt + ":" + password)</p>
     * 
     * @param password The password to hash. Null values are treated as empty strings.
     * @param salt The salt to use for hashing. Null values are treated as empty strings.
     * @return Base64-encoded SHA-256 hash
     * @throws RuntimeException if SHA-256 algorithm is not available
     */
    // Legacy SHA-256 hash to keep existing users unlocked; new hashes use PBKDF2
    private static String legacyHash(String password, String salt) {
        if (password == null) password = "";
        if (salt == null) salt = "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            md.update((byte)':');
            md.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] dig = md.digest();
            return Base64.getEncoder().encodeToString(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
