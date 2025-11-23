package main.core.security;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class LockUtil {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LEN_BITS = 256;
    private static final String PBKDF2_PREFIX = "pbkdf2";

    private LockUtil() {}

    public static String newSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hashPassword(String password, String salt) {
        return hashPassword(password, salt, PBKDF2_ITERATIONS);
    }

    private static String hashPassword(String password, String salt, int iterations) {
        if (password == null) password = "";
        if (salt == null) salt = "";
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

    private static boolean constantTimeEquals(String a, String b){
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= (ba[i] ^ bb[i]);
        return r == 0;
    }

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
