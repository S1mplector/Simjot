/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.ffi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import main.infrastructure.io.ResourceLoader;
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
    private static final Object DICT_LOCK = new Object();

    private static volatile NativeLibrary library;
    private static volatile boolean attempted;
    private static volatile boolean dictReady;
    private static volatile boolean dictInitAttempted;

    private NativeAccess() {}

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROP_ENABLED, "true"));
    }

    private static boolean isFfmSupported() {
        try {
            int feature = Runtime.version().feature();
            if (feature >= 22) return true;
            if (feature == 21 && Boolean.getBoolean("simjot.native.preview")) return true;
        } catch (Throwable ignored) {}
        return false;
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

    public static List<NativeDirEntry> listDirectory(Path dir, boolean includeHidden) {
        NativeLibrary lib = library();
        if (lib == null || dir == null) return null;
        try {
            byte[] data = lib.listDirectory(dir, includeHidden);
            if (data == null || data.length == 0) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            List<NativeDirEntry> entries = new ArrayList<>();
            while (buf.remaining() >= 8) {
                int nameLen = buf.getInt();
                if (nameLen <= 0 || nameLen > buf.remaining() - 4) break;
                int isDir = buf.get() & 0xFF;
                int isHidden = buf.get() & 0xFF;
                buf.get();
                buf.get();
                if (nameLen > buf.remaining()) break;
                byte[] nameBytes = new byte[nameLen];
                buf.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                entries.add(new NativeDirEntry(name, isDir != 0, isHidden != 0));
            }
            return entries;
        } catch (Throwable t) {
            IoLog.warn("native-list-dir", "Native directory listing failed; falling back to Java.", t);
            return null;
        }
    }

    public static boolean dictionaryReady() {
        if (dictReady) return true;
        if (dictInitAttempted) return false;
        synchronized (DICT_LOCK) {
            if (dictReady) return true;
            if (dictInitAttempted) return false;
            dictInitAttempted = true;
            NativeLibrary lib = library();
            if (lib == null || !lib.hasDictionarySupport()) return false;
            Path basePath = resolveDictionaryBasePath();
            if (basePath == null) return false;
            dictReady = lib.setDictionaryBasePath(basePath);
            return dictReady;
        }
    }

    public static NativeDictionaryEntry dictionaryLookup(String word) {
        if (!dictionaryReady() || word == null || word.isBlank()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.dictionaryLookup(word);
            if (data == null || data.length < 12) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            int posCount = buf.getInt();
            int synCount = buf.getInt();
            int antCount = buf.getInt();
            List<String> pos = readStringList(buf, posCount);
            List<String> syn = readStringList(buf, synCount);
            List<String> ant = readStringList(buf, antCount);
            return new NativeDictionaryEntry(pos, syn, ant);
        } catch (Throwable t) {
            IoLog.warn("native-dict", "Native dictionary lookup failed; falling back to Java.", t);
            return null;
        }
    }

    public static boolean dictionaryContains(String word) {
        if (!dictionaryReady() || word == null || word.isBlank()) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        try {
            Boolean ok = lib.dictionaryContains(word);
            return ok != null && ok;
        } catch (Throwable t) {
            IoLog.warn("native-dict", "Native dictionary contains failed; falling back to Java.", t);
            return false;
        }
    }

    public static List<String> dictionaryRhymes(String word, int maxResults) {
        if (!dictionaryReady() || word == null || word.isBlank()) return null;
        NativeLibrary lib = library();
        if (lib == null || !lib.hasDictionaryRhymesSupport()) return null;
        try {
            byte[] data = lib.dictionaryRhymes(word, Math.max(0, maxResults));
            if (data == null || data.length < 4) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            int count = buf.getInt();
            return readStringList(buf, count);
        } catch (Throwable t) {
            IoLog.warn("native-rhymes", "Native rhyme lookup failed; falling back to Java.", t);
            return null;
        }
    }

    private static List<String> readStringList(ByteBuffer buf, int count) {
        if (count <= 0) return Collections.emptyList();
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (buf.remaining() < 4) break;
            int len = buf.getInt();
            if (len <= 0 || len > buf.remaining()) break;
            byte[] bytes = new byte[len];
            buf.get(bytes);
            out.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static Path resolveDictionaryBasePath() {
        String override = System.getProperty("simjot.dict.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("SIMJOT_DICT_PATH");
        }
        if (override != null && !override.isBlank()) {
            Path candidate = Paths.get(override);
            if (Files.isDirectory(candidate)) return candidate.toAbsolutePath();
        }

        try {
            URL url = ResourceLoader.getResource("simple-english-dictionary/data");
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                Path path = Paths.get(url.toURI());
                if (Files.isDirectory(path)) return path.toAbsolutePath();
            }
        } catch (Throwable ignored) {}

        String[] fallbacks = {
            "src/main/resources/simple-english-dictionary/data",
            "Simjot/src/main/resources/simple-english-dictionary/data",
            "simple-english-dictionary/data"
        };
        for (String fb : fallbacks) {
            Path path = Paths.get(fb);
            if (Files.isDirectory(path)) return path.toAbsolutePath();
        }
        return null;
    }

    public static final class NativeDirEntry {
        public final String name;
        public final boolean isDirectory;
        public final boolean isHidden;

        public NativeDirEntry(String name, boolean isDirectory, boolean isHidden) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.isHidden = isHidden;
        }
    }

    public static final class NativeDictionaryEntry {
        public final List<String> partsOfSpeech;
        public final List<String> synonyms;
        public final List<String> antonyms;

        public NativeDictionaryEntry(List<String> partsOfSpeech, List<String> synonyms, List<String> antonyms) {
            this.partsOfSpeech = partsOfSpeech != null ? partsOfSpeech : Collections.emptyList();
            this.synonyms = synonyms != null ? synonyms : Collections.emptyList();
            this.antonyms = antonyms != null ? antonyms : Collections.emptyList();
        }
    }

    private static NativeLibrary library() {
        if (!isEnabled() || !isFfmSupported()) return null;
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
