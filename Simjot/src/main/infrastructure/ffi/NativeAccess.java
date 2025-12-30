package main.infrastructure.ffi;

import java.nio.file.Path;

import main.infrastructure.io.IoLog;

/**
 * Lazily loads the native library and provides safe fallbacks.
 * 
 * @author S1mplector
 */
public final class NativeAccess {
    private static final String PROP_ENABLED = "simjot.native.enabled";
    private static final String PROP_PATH = "simjot.native.path";
    private static final String ENV_PATH = "SIMJOT_NATIVE_PATH";
    private static final Object LOCK = new Object();

    private static volatile NativeLibrary library;
    private static volatile boolean attempted;

    private NativeAccess() {}

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROP_ENABLED, "true"));
    }

    public static boolean isAvailable() {
        return library() != null;
    }

    public static String sha256(Path path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.sha256File(path);
        } catch (Throwable t) {
            IoLog.warn("native-sha256", "Native SHA-256 failed; falling back to Java.", t);
            return null;
        }
    }

    public static Integer countSyllables(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null || word.isEmpty()) return null;
        try {
            return lib.countSyllables(word);
        } catch (Throwable t) {
            IoLog.warn("native-syllables", "Native syllable count failed; falling back to Java.", t);
            return null;
        }
    }

    public static String rhymeKey(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return null;
        try {
            return lib.rhymeKey(word);
        } catch (Throwable t) {
            IoLog.warn("native-rhyme-key", "Native rhyme key failed; falling back to Java.", t);
            return null;
        }
    }

    public static String nearRhymeKey(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return null;
        try {
            return lib.nearRhymeKey(word);
        } catch (Throwable t) {
            IoLog.warn("native-near-rhyme", "Native near-rhyme key failed; falling back to Java.", t);
            return null;
        }
    }

    public static Boolean atomicWrite(Path target, byte[] data, boolean fsyncFile, boolean fsyncDir) {
        NativeLibrary lib = library();
        if (lib == null || target == null || data == null) return null;
        try {
            return lib.atomicWrite(target, data, fsyncFile, fsyncDir);
        } catch (Throwable t) {
            IoLog.warn("native-atomic-write", "Native atomic write failed; falling back to Java.", t);
            return null;
        }
    }

    public static Boolean ensureSpace(Path dir, long bytesNeeded) {
        NativeLibrary lib = library();
        if (lib == null || dir == null) return null;
        try {
            int result = lib.ensureSpace(dir, bytesNeeded);
            if (result < 0) return null;
            return result == 1;
        } catch (Throwable t) {
            IoLog.warn("native-space", "Native space check failed; falling back to Java.", t);
            return null;
        }
    }

    private static NativeLibrary library() {
        if (!isEnabled()) return null;
        if (library != null || attempted) return library;
        synchronized (LOCK) {
            if (library != null || attempted) return library;
            attempted = true;
            String path = System.getProperty(PROP_PATH);
            if (path == null || path.isBlank()) {
                String env = System.getenv(ENV_PATH);
                if (env != null && !env.isBlank()) path = env;
            }
            try {
                library = (path != null && !path.isBlank())
                        ? NativeLibrary.load(path)
                        : NativeLibrary.loadDefault();
            } catch (Throwable t) {
                IoLog.warn("native-load", "Native library not available; using Java implementations.", t);
                library = null;
            }
        }
        return library;
    }
}
