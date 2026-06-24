/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.security;

import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

import javax.swing.SwingUtilities;

import main.core.security.crypto.ContentType;
import main.core.security.crypto.CryptoConfig;
import main.core.security.crypto.CryptoException;
import main.core.security.crypto.CryptoHeader;
import main.core.security.crypto.EncryptedMetadata;
import main.core.security.crypto.SimjotCrypto;
import main.core.service.SettingsStore;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.security.ElegantUnlockDialog;

/**
 * Manages encryption settings, session password caching, and file-level helpers.
 */
public final class EncryptionManager {
    private static final Object LOCK = new Object();
    private static char[] sessionPassword;

    private static final SimjotCrypto CRYPTO = new SimjotCrypto();

    private EncryptionManager() {}

    public static boolean isEncryptionEnabled() {
        return SettingsStore.get().isEncryptionEnabled();
    }

    public static boolean hasPasswordSet() {
        SettingsStore s = SettingsStore.get();
        String hash = s.getEncryptionPasswordHash();
        return hash != null && !hash.isBlank();
    }

    public static void cacheSessionPassword(char[] password) {
        if (password == null || password.length == 0) return;
        synchronized (LOCK) {
            clearSessionPasswordLocked();
            sessionPassword = Arrays.copyOf(password, password.length);
        }
    }

    public static void clearSessionPassword() {
        synchronized (LOCK) {
            clearSessionPasswordLocked();
        }
    }

    private static void clearSessionPasswordLocked() {
        if (sessionPassword != null) {
            Arrays.fill(sessionPassword, '\0');
            sessionPassword = null;
        }
    }

    private static char[] getSessionPassword() {
        synchronized (LOCK) {
            if (sessionPassword == null || sessionPassword.length == 0) return null;
            return Arrays.copyOf(sessionPassword, sessionPassword.length);
        }
    }

    public static boolean isEncrypted(File file) {
        return CRYPTO.isSimjotEncrypted(file);
    }

    /**
     * Reads the Simjot crypto header without decrypting.
     */
    public static CryptoHeader readHeader(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[CryptoConfig.MAGIC_BYTES.length];
            if (in.read(magic) != magic.length) return null;
            if (!Arrays.equals(magic, CryptoConfig.MAGIC_BYTES)) return null;

            byte[] fixed = new byte[1 + 1 + 1 + 4];
            if (in.read(fixed) != fixed.length) return null;
            ByteBuffer buf = ByteBuffer.wrap(fixed).order(ByteOrder.BIG_ENDIAN);
            buf.get(); // content type
            buf.get(); // security level
            buf.get(); // flags
            int headerLength = buf.getInt();
            int prefix = magic.length + fixed.length;
            if (headerLength < prefix) return null;

            byte[] header = new byte[headerLength];
            System.arraycopy(magic, 0, header, 0, magic.length);
            System.arraycopy(fixed, 0, header, magic.length, fixed.length);
            int remaining = headerLength - prefix;
            int offset = prefix;
            while (remaining > 0) {
                int read = in.read(header, offset, remaining);
                if (read < 0) return null;
                remaining -= read;
                offset += read;
            }
            return CryptoHeader.fromBytes(header);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static EncryptedMetadata.Meta readMetadata(File file) {
        CryptoHeader header = readHeader(file);
        if (header == null) return null;
        return EncryptedMetadata.parse(header.getIdentifier());
    }

    /**
     * Reads file content, decrypting if needed.
     */
    public static byte[] readFileMaybeDecrypt(File file, Component parent, boolean allowPrompt)
            throws IOException, CryptoException {
        if (file == null) return null;
        byte[] raw = Files.readAllBytes(file.toPath());
        if (!CRYPTO.isSimjotEncrypted(raw)) return raw;
        String password = getPasswordForUse(parent, allowPrompt);
        if (password == null || password.isBlank()) {
            throw CryptoException.invalidPassword();
        }
        return CRYPTO.decrypt(raw, password);
    }

    public static byte[] encrypt(byte[] data, String password, ContentType type, CryptoConfig config)
            throws CryptoException {
        return CRYPTO.encrypt(data, password, type, config);
    }

    /**
     * Returns a usable password, prompting if allowed and necessary.
     */
    public static String getPasswordForUse(Component parent, boolean allowPrompt) {
        char[] cached = getSessionPassword();
        if (cached != null) {
            return new String(cached);
        }
        if (!allowPrompt) return null;
        if (!hasPasswordSet()) {
            showMessage(parent, "Encryption password not set. Set one in Security settings.", true);
            return null;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return promptForPassword(parent);
        }
        final String[] result = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = promptForPassword(parent));
        } catch (Exception ignored) {
            return null;
        }
        return result[0];
    }

    private static String promptForPassword(Component parent) {
        ElegantUnlockDialog.Result res = ElegantUnlockDialog.prompt(parent);
        if (res == null || res.password == null || res.password.length == 0) return null;
        SettingsStore s = SettingsStore.get();
        String salt = s.getEncryptionPasswordSalt();
        String hash = s.getEncryptionPasswordHash();
        String attempt = new String(res.password);
        boolean ok = LockUtil.verify(attempt, salt, hash);
        if (!ok) {
            showMessage(parent, "Incorrect encryption password.", true);
            Arrays.fill(res.password, '\0');
            return null;
        }
        if (res.remember) {
            cacheSessionPassword(res.password);
        }
        Arrays.fill(res.password, '\0');
        return attempt;
    }

    private static void showMessage(Component parent, String message, boolean error) {
        CustomMessageDialog.display(parent, "Encryption", message, error);
    }
}
