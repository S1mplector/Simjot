package main.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class LockUtil {
    private static final SecureRandom RNG = new SecureRandom();

    private LockUtil() {}

    public static String newSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hashPassword(String password, String salt) {
        if (password == null) password = "";
        if (salt == null) salt = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            md.update((byte)':');
            md.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] dig = md.digest();
            return Base64.getEncoder().encodeToString(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(String candidate, String salt, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) return false;
        String h = hashPassword(candidate, salt);
        return constantTimeEquals(expectedHash, h);
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
}
