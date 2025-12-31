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

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import main.infrastructure.io.IoLog;
import main.infrastructure.io.ResourceLoader;

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

    public static PerfSnapshot perfSnapshot() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.perfSnapshot();
            if (data == null || data.length < 64) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            int version = buf.getInt();
            int cpuCount = buf.getInt();
            long timestampNs = buf.getLong();
            long cpuUserNs = buf.getLong();
            long cpuSystemNs = buf.getLong();
            long rssBytes = buf.getLong();
            long vmemBytes = buf.getLong();
            long sysTotalBytes = buf.getLong();
            long sysAvailBytes = buf.getLong();
            return new PerfSnapshot(version, cpuCount, timestampNs, cpuUserNs, cpuSystemNs,
                    rssBytes, vmemBytes, sysTotalBytes, sysAvailBytes);
        } catch (Throwable t) {
            IoLog.warn("native-perf", "Native performance snapshot failed; falling back to Java.", t);
            return null;
        }
    }

    public static BinaryHealth binaryHealth(Path path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            byte[] data = lib.binaryHealth(path);
            if (data == null || data.length < 56) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            int version = buf.getInt();
            int flags = buf.getInt();
            long sizeBytes = buf.getLong();
            long modifiedEpochSeconds = buf.getLong();
            byte[] hash = new byte[32];
            buf.get(hash);
            String hashHex = HexFormat.of().formatHex(hash);
            return new BinaryHealth(version, flags, sizeBytes, modifiedEpochSeconds, hashHex);
        } catch (Throwable t) {
            IoLog.warn("native-health", "Native binary health check failed; falling back to Java.", t);
            return null;
        }
    }

    public static Boolean copyFile(Path src, Path dst, boolean copyAttributes) {
        NativeLibrary lib = library();
        if (lib == null || src == null || dst == null) return null;
        try {
            return lib.copyFile(src, dst, copyAttributes);
        } catch (Throwable t) {
            IoLog.warn("native-copy", "Native file copy failed; falling back to Java.", t);
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

    public static Integer dictionarySize() {
        if (!dictionaryReady()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.dictionarySize();
        } catch (Throwable t) {
            IoLog.warn("native-dict", "Native dictionary size failed; falling back to Java.", t);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPELL CHECK API
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Object SPELL_LOCK = new Object();
    private static volatile boolean spellReady;
    private static volatile boolean spellInitAttempted;

    public static boolean spellReady() {
        if (spellReady) return true;
        if (spellInitAttempted) return false;
        synchronized (SPELL_LOCK) {
            if (spellReady) return true;
            if (spellInitAttempted) return false;
            spellInitAttempted = true;
            NativeLibrary lib = library();
            if (lib == null || !lib.hasSpellSupport()) return false;
            Path basePath = resolveSpellDictionaryPath();
            if (basePath == null) return false;
            spellReady = lib.initSpellDictionary(basePath.toString());
            return spellReady;
        }
    }

    public static Boolean spellContains(String word) {
        if (!spellReady() || word == null || word.isBlank()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.spellContains(word);
        } catch (Throwable t) {
            IoLog.warn("native-spell", "Native spell contains failed; falling back to Java.", t);
            return null;
        }
    }

    public static List<String> spellSuggestions(String word, int maxResults) {
        if (!spellReady() || word == null || word.isBlank()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.spellSuggestions(word, maxResults);
        } catch (Throwable t) {
            IoLog.warn("native-spell", "Native spell suggestions failed; falling back to Java.", t);
            return null;
        }
    }

    public static String spellBestCorrection(String word) {
        if (!spellReady() || word == null || word.isBlank()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.spellBestCorrection(word);
        } catch (Throwable t) {
            IoLog.warn("native-spell", "Native spell correction failed; falling back to Java.", t);
            return null;
        }
    }

    public static boolean addUserDictionaryWord(String word) {
        if (!spellReady() || word == null || word.isBlank()) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        try {
            return lib.addUserDictionaryWord(word);
        } catch (Throwable t) {
            IoLog.warn("native-spell", "Native add user word failed.", t);
            return false;
        }
    }

    public static void clearUserDictionary() {
        if (!spellReady()) return;
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.clearUserDictionary();
        } catch (Throwable t) {
            IoLog.warn("native-spell", "Native clear user dictionary failed.", t);
        }
    }

    private static Path resolveSpellDictionaryPath() {
        String override = System.getProperty("simjot.spell.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("SIMJOT_SPELL_PATH");
        }
        if (override != null && !override.isBlank()) {
            Path candidate = Paths.get(override);
            if (Files.isDirectory(candidate)) return candidate.toAbsolutePath();
        }

        try {
            URL url = ResourceLoader.getResource("simple-english-dictionary/processed");
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                Path path = Paths.get(url.toURI());
                if (Files.isDirectory(path)) return path.toAbsolutePath();
            }
        } catch (Throwable ignored) {}

        String[] fallbacks = {
            "src/main/resources/simple-english-dictionary/processed",
            "Simjot/src/main/resources/simple-english-dictionary/processed",
            "simple-english-dictionary/processed"
        };
        for (String fb : fallbacks) {
            Path path = Paths.get(fb);
            if (Files.isDirectory(path)) return path.toAbsolutePath();
        }
        return null;
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

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT UTILITIES API
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean textUtilsReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasTextUtilsSupport();
    }

    public static Integer textWordCount(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textWordCount(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native word count failed.", t);
            return null;
        }
    }

    public static Integer textSentenceCount(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textSentenceCount(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native sentence count failed.", t);
            return null;
        }
    }

    public static Integer textCharCount(String text, boolean includeSpaces) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textCharCount(text, includeSpaces);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native char count failed.", t);
            return null;
        }
    }

    public static List<String> textExtractWords(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textExtractWords(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native word extraction failed.", t);
            return null;
        }
    }

    public static String textLastWord(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textLastWord(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native last word failed.", t);
            return null;
        }
    }

    public static String textNormalize(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textNormalize(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native normalize failed.", t);
            return null;
        }
    }

    public static boolean textFuzzyMatch(String text, String query) {
        if (!textUtilsReady() || text == null || query == null) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        try {
            return lib.textFuzzyMatch(text, query);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int textFuzzyScore(String text, String query) {
        if (!textUtilsReady() || text == null || query == null) return 0;
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.textFuzzyScore(text, query);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Integer textLineCount(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textLineCount(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native line count failed.", t);
            return null;
        }
    }

    public static String textGetLine(String text, int lineNum) {
        if (!textUtilsReady() || text == null || lineNum < 0) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textGetLine(text, lineNum);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native get line failed.", t);
            return null;
        }
    }

    public static Integer textLevenshtein(String a, String b) {
        if (!textUtilsReady() || a == null || b == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textLevenshtein(a, b);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Integer textDamerauLevenshtein(String a, String b) {
        if (!textUtilsReady() || a == null || b == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textDamerauLevenshtein(a, b);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Integer textSimilarity(String a, String b) {
        if (!textUtilsReady() || a == null || b == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textSimilarity(a, b);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPRESSION API
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean compressionReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasCompressionSupport();
    }

    public static byte[] compress(byte[] data, int level) {
        if (!compressionReady() || data == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.compress(data, level);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] compress(byte[] data) {
        return compress(data, 9);  // Best compression
    }

    public static byte[] decompress(byte[] data, int expectedSize) {
        if (!compressionReady() || data == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.decompress(data, expectedSize);
        } catch (Throwable t) {
            return null;
        }
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

    public static final class PerfSnapshot {
        public final int version;
        public final int cpuCount;
        public final long timestampNs;
        public final long cpuUserNs;
        public final long cpuSystemNs;
        public final long rssBytes;
        public final long vmemBytes;
        public final long sysTotalBytes;
        public final long sysAvailBytes;

        public PerfSnapshot(int version, int cpuCount, long timestampNs, long cpuUserNs, long cpuSystemNs,
                            long rssBytes, long vmemBytes, long sysTotalBytes, long sysAvailBytes) {
            this.version = version;
            this.cpuCount = cpuCount;
            this.timestampNs = timestampNs;
            this.cpuUserNs = cpuUserNs;
            this.cpuSystemNs = cpuSystemNs;
            this.rssBytes = rssBytes;
            this.vmemBytes = vmemBytes;
            this.sysTotalBytes = sysTotalBytes;
            this.sysAvailBytes = sysAvailBytes;
        }
    }

    public static final class BinaryHealth {
        public final int version;
        public final int flags;
        public final long sizeBytes;
        public final long modifiedEpochSeconds;
        public final String sha256Hex;

        public BinaryHealth(int version, int flags, long sizeBytes, long modifiedEpochSeconds, String sha256Hex) {
            this.version = version;
            this.flags = flags;
            this.sizeBytes = sizeBytes;
            this.modifiedEpochSeconds = modifiedEpochSeconds;
            this.sha256Hex = sha256Hex;
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
