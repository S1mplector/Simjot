/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import main.infrastructure.ffi.MacNativeBridge;

/**
 * Persists and reuses macOS security-scoped bookmarks for user-selected roots.
 */
public final class MacSecurityBookmarkStore {
    private static final Object LOCK = new Object();
    private static final Map<String, Long> ACTIVE_TOKENS = new ConcurrentHashMap<>();
    private static final String STORE_NAME = ".simjournal_security_bookmarks.properties";

    private MacSecurityBookmarkStore() {}

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("mac");
    }

    private static File storeFile() {
        String home = System.getProperty("user.home", "");
        return new File(home, STORE_NAME);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        File file = storeFile();
        if (!file.exists()) return props;
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (Throwable ignored) {
            // Keep best-effort behavior.
        }
        return props;
    }

    private static void saveProperties(Properties props) {
        File file = storeFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Simjot macOS security-scoped bookmarks");
        } catch (Throwable ignored) {
            // Keep best-effort behavior.
        }
    }

    private static String keyFor(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    public static void ensureAccess(File folder) {
        if (!isMac() || folder == null) return;
        Path path = folder.toPath();
        String key = keyFor(path);
        if (ACTIVE_TOKENS.containsKey(key)) return;

        byte[] bookmark = null;
        synchronized (LOCK) {
            Properties props = loadProperties();
            String encoded = props.getProperty(key);
            if (encoded != null && !encoded.isBlank()) {
                try {
                    bookmark = Base64.getDecoder().decode(encoded);
                } catch (IllegalArgumentException ignored) {
                    bookmark = null;
                }
            }
        }

        if (bookmark != null && bookmark.length > 0) {
            long token = MacNativeBridge.startSecurityBookmarkAccess(bookmark);
            if (token > 0) {
                ACTIVE_TOKENS.put(key, token);
                return;
            }
        }

        remember(folder);
    }

    public static void remember(File folder) {
        if (!isMac() || folder == null) return;
        Path path = folder.toPath();
        String key = keyFor(path);
        byte[] bookmark = MacNativeBridge.createSecurityBookmark(key);
        if (bookmark == null || bookmark.length == 0) return;

        synchronized (LOCK) {
            Properties props = loadProperties();
            props.setProperty(key, Base64.getEncoder().encodeToString(bookmark));
            saveProperties(props);
        }

        if (!ACTIVE_TOKENS.containsKey(key)) {
            long token = MacNativeBridge.startSecurityBookmarkAccess(bookmark);
            if (token > 0) {
                ACTIVE_TOKENS.put(key, token);
            }
        }
    }

    public static void releaseAll() {
        for (Map.Entry<String, Long> entry : ACTIVE_TOKENS.entrySet()) {
            Long token = entry.getValue();
            if (token != null && token > 0) {
                MacNativeBridge.stopSecurityBookmarkAccess(token);
            }
        }
        ACTIVE_TOKENS.clear();
    }
}
