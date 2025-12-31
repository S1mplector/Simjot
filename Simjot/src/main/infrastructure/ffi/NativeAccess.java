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

    // ═══════════════════════════════════════════════════════════════════════════
    // STRING OPERATIONS API
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean stringOpsReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasStringOpsSupport();
    }

    public static String stringSanitize(String input, int maxLen) {
        if (!stringOpsReady() || input == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.stringSanitize(input, maxLen);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String stringSanitize(String input) {
        return stringSanitize(input, 0);  // No max length
    }

    public static long stringHash(String str) {
        if (!stringOpsReady() || str == null) return 0;
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.stringHash(str);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int stringTokenCount(String text) {
        if (!stringOpsReady() || text == null) return -1;
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.stringTokenCount(text);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String stringFirstTokens(String text, int maxTokens) {
        if (!stringOpsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.stringFirstTokens(text, maxTokens);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String stringLastTokens(String text, int maxTokens) {
        if (!stringOpsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.stringLastTokens(text, maxTokens);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean stringContainsCi(String haystack, String needle) {
        if (!stringOpsReady() || haystack == null || needle == null) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        try {
            return lib.stringContainsCi(haystack, needle);
        } catch (Throwable t) {
            return false;
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

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON PARSING API
    // ═══════════════════════════════════════════════════════════════════════════

    public static String jsonGetString(String json, String key) {
        NativeLibrary lib = library();
        if (lib == null || json == null || key == null) return null;
        try {
            return lib.jsonGetString(json, key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Long jsonGetLong(String json, String key) {
        NativeLibrary lib = library();
        if (lib == null || json == null || key == null) return null;
        try {
            return lib.jsonGetLong(json, key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean jsonHasKey(String json, String key) {
        NativeLibrary lib = library();
        if (lib == null || json == null || key == null) return false;
        try {
            return lib.jsonHasKey(json, key);
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATE/TIME API
    // ═══════════════════════════════════════════════════════════════════════════

    public static long timeNowMillis() {
        NativeLibrary lib = library();
        if (lib == null) return System.currentTimeMillis();
        try {
            return lib.timeNowMillis();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    public static String timeFormat(long millis, String pattern) {
        NativeLibrary lib = library();
        if (lib == null || pattern == null) return null;
        try {
            return lib.timeFormat(millis, pattern);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String timeFormatNow(String pattern) {
        NativeLibrary lib = library();
        if (lib == null || pattern == null) return null;
        try {
            return lib.timeFormatNow(pattern);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String timeRelative(long millis) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.timeRelative(millis);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN MATCHING API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int patternFind(String text, String pattern, boolean wordBoundary) {
        NativeLibrary lib = library();
        if (lib == null || text == null || pattern == null) return -1;
        try {
            return lib.patternFind(text, pattern, wordBoundary);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String patternExtractAfter(String text, String prefix, int maxPhraseLen) {
        NativeLibrary lib = library();
        if (lib == null || text == null || prefix == null) return null;
        try {
            return lib.patternExtractAfter(text, prefix, maxPhraseLen);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String patternReplaceAll(String text, String pattern, String replacement) {
        NativeLibrary lib = library();
        if (lib == null || text == null || pattern == null) return null;
        try {
            return lib.patternReplaceAll(text, pattern, replacement);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String patternCollapseSpaces(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return null;
        try {
            return lib.patternCollapseSpaces(text);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENCODING API
    // ═══════════════════════════════════════════════════════════════════════════

    public static String base64Encode(byte[] data) {
        NativeLibrary lib = library();
        if (lib == null || data == null) return null;
        try {
            return lib.base64Encode(data);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] base64Decode(String encoded) {
        NativeLibrary lib = library();
        if (lib == null || encoded == null) return null;
        try {
            return lib.base64Decode(encoded);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int utf8Strlen(String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return -1;
        try {
            return lib.utf8Strlen(str);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String hexEncode(byte[] data) {
        NativeLibrary lib = library();
        if (lib == null || data == null) return null;
        try {
            return lib.hexEncode(data);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] hexDecode(String hex) {
        NativeLibrary lib = library();
        if (lib == null || hex == null) return null;
        try {
            return lib.hexDecode(hex);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POETRY ANALYSIS API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int poetryAnalyzeSounds(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return 0;
        try {
            return lib.poetryAnalyzeSounds(text);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int poetryAnalyzeThemes(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return 0;
        try {
            return lib.poetryAnalyzeThemes(text);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static double poetryGetThemeScore(String theme) {
        NativeLibrary lib = library();
        if (lib == null || theme == null) return 0.0;
        try {
            return lib.poetryGetThemeScore(theme);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public static String poetryGetThemes() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.poetryGetThemes();
        } catch (Throwable t) {
            return null;
        }
    }

    public static int poetryAnalyzeVocab(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return 0;
        try {
            return lib.poetryAnalyzeVocab(text);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int poetryCountSyllables(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return 0;
        try {
            return lib.poetryCountSyllables(word);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int poetryAnalyzeMeter(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return 0;
        try {
            return lib.poetryAnalyzeMeter(text);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String poetryDetectMeter() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.poetryDetectMeter();
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RHYME ENGINE API
    // ═══════════════════════════════════════════════════════════════════════════

    public static void rhymeAddWord(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return;
        try {
            lib.rhymeAddWord(word);
        } catch (Throwable ignored) {}
    }

    public static int rhymeAddWords(String words) {
        NativeLibrary lib = library();
        if (lib == null || words == null) return 0;
        try {
            return lib.rhymeAddWords(words);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static List<String> rhymeFind(String word, int maxResults) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return Collections.emptyList();
        try {
            String results = lib.rhymeFind(word, maxResults);
            if (results == null || results.isEmpty()) return Collections.emptyList();
            List<String> list = new ArrayList<>();
            for (String s : results.split("\n")) {
                if (!s.isEmpty()) list.add(s);
            }
            return list;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static boolean rhymeCheck(String word1, String word2) {
        NativeLibrary lib = library();
        if (lib == null || word1 == null || word2 == null) return false;
        try {
            return lib.rhymeCheck(word1, word2);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String rhymeDetectScheme(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return null;
        try {
            return lib.rhymeDetectScheme(text);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void rhymeClear() {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.rhymeClear();
        } catch (Throwable ignored) {}
    }

    public static int rhymeDbSize() {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.rhymeDbSize();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MATH UTILITIES API
    // ═══════════════════════════════════════════════════════════════════════════

    public static double mathVec2Length(double x, double y) {
        NativeLibrary lib = library();
        if (lib == null) return Math.sqrt(x * x + y * y);
        try {
            return lib.mathVec2Length(x, y);
        } catch (Throwable t) {
            return Math.sqrt(x * x + y * y);
        }
    }

    public static double mathVec2Distance(double x1, double y1, double x2, double y2) {
        NativeLibrary lib = library();
        if (lib == null) return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
        try {
            return lib.mathVec2Distance(x1, y1, x2, y2);
        } catch (Throwable t) {
            return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
        }
    }

    public static double mathEase(int type, double t) {
        NativeLibrary lib = library();
        if (lib == null) return t;
        try {
            return lib.mathEase(type, t);
        } catch (Throwable ex) {
            return t;
        }
    }

    public static int mathColorBlend(int color1, int color2, double t) {
        NativeLibrary lib = library();
        if (lib == null) return color1;
        try {
            return lib.mathColorBlend(color1, color2, t);
        } catch (Throwable ex) {
            return color1;
        }
    }

    public static int mathHslToRgb(double h, double s, double l) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.mathHslToRgb(h, s, l);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static double mathLerp(double a, double b, double t) {
        NativeLibrary lib = library();
        if (lib == null) return a + (b - a) * t;
        try {
            return lib.mathLerp(a, b, t);
        } catch (Throwable ex) {
            return a + (b - a) * t;
        }
    }

    public static double mathClamp(double value, double min, double max) {
        NativeLibrary lib = library();
        if (lib == null) return Math.max(min, Math.min(max, value));
        try {
            return lib.mathClamp(value, min, max);
        } catch (Throwable ex) {
            return Math.max(min, Math.min(max, value));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENT/TASK API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int taskCreate(String data, int priority) {
        NativeLibrary lib = library();
        if (lib == null || data == null) return -1;
        try {
            return lib.taskCreate(data, priority);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int taskPendingCount() {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.taskPendingCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int parallelGetHwThreads() {
        NativeLibrary lib = library();
        if (lib == null) return Runtime.getRuntime().availableProcessors();
        try {
            return lib.parallelGetHwThreads();
        } catch (Throwable t) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    public static long atomicInc(int counterId) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.atomicInc(counterId);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long atomicGet(int counterId) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.atomicGet(counterId);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long hrtimeNs() {
        NativeLibrary lib = library();
        if (lib == null) return System.nanoTime();
        try {
            return lib.hrtimeNs();
        } catch (Throwable t) {
            return System.nanoTime();
        }
    }

    public static long monotonicMs() {
        NativeLibrary lib = library();
        if (lib == null) return System.currentTimeMillis();
        try {
            return lib.monotonicMs();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLLECTION API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int setCreate() {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.setCreate();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void setAdd(int setId, String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return;
        try {
            lib.setAdd(setId, str);
        } catch (Throwable ignored) {}
    }

    public static boolean setContains(int setId, String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return false;
        try {
            return lib.setContains(setId, str);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int setSize(int setId) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.setSize(setId);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setClear(int setId) {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.setClear(setId);
        } catch (Throwable ignored) {}
    }

    public static int mapCreate() {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.mapCreate();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void mapSet(int mapId, String key, String value) {
        NativeLibrary lib = library();
        if (lib == null || key == null || value == null) return;
        try {
            lib.mapSet(mapId, key, value);
        } catch (Throwable ignored) {}
    }

    public static String mapGet(int mapId, String key) {
        NativeLibrary lib = library();
        if (lib == null || key == null) return null;
        try {
            return lib.mapGet(mapId, key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean mapHas(int mapId, String key) {
        NativeLibrary lib = library();
        if (lib == null || key == null) return false;
        try {
            return lib.mapHas(mapId, key);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int mapSize(int mapId) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.mapSize(mapId);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int freqCreate() {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.freqCreate();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void freqAdd(int mapId, String str, int count) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return;
        try {
            lib.freqAdd(mapId, str, count);
        } catch (Throwable ignored) {}
    }

    public static int freqGet(int mapId, String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return 0;
        try {
            return lib.freqGet(mapId, str);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String freqTopN(int mapId, int n) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.freqTopN(mapId, n);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int cacheCreate(int maxSize) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.cacheCreate(maxSize);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void cacheSet(int cacheId, String key, String value) {
        NativeLibrary lib = library();
        if (lib == null || key == null || value == null) return;
        try {
            lib.cacheSet(cacheId, key, value);
        } catch (Throwable ignored) {}
    }

    public static String cacheGet(int cacheId, String key) {
        NativeLibrary lib = library();
        if (lib == null || key == null) return null;
        try {
            return lib.cacheGet(cacheId, key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int cacheSize(int cacheId) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.cacheSize(cacheId);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMD OPERATIONS API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int simdSupportLevel() {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.simdSupportLevel();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static long simdSumInt(int[] arr) {
        NativeLibrary lib = library();
        if (lib == null || arr == null) return 0;
        try {
            return lib.simdSumInt(arr);
        } catch (Throwable t) {
            long sum = 0;
            for (int v : arr) sum += v;
            return sum;
        }
    }

    public static double simdSumDouble(double[] arr) {
        NativeLibrary lib = library();
        if (lib == null || arr == null) return 0;
        try {
            return lib.simdSumDouble(arr);
        } catch (Throwable t) {
            double sum = 0;
            for (double v : arr) sum += v;
            return sum;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE SYSTEM API
    // ═══════════════════════════════════════════════════════════════════════════

    public static long fsSize(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return -1;
        try {
            return lib.fsSize(path);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long fsMtime(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return -1;
        try {
            return lib.fsMtime(path);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean fsExists(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return false;
        try {
            return lib.fsExists(path);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fsIsDir(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return false;
        try {
            return lib.fsIsDir(path);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fsIsFile(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return false;
        try {
            return lib.fsIsFile(path);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String fsListRecursive(String path, String extension, int maxDepth) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.fsListRecursive(path, extension, maxDepth);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] fsReadAll(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.fsReadAll(path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean fsWriteAll(String path, byte[] data) {
        NativeLibrary lib = library();
        if (lib == null || path == null || data == null) return false;
        try {
            return lib.fsWriteAll(path, data);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fsMkdir(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return false;
        try {
            return lib.fsMkdir(path);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fsRemove(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return false;
        try {
            return lib.fsRemove(path);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean fsRename(String oldPath, String newPath) {
        NativeLibrary lib = library();
        if (lib == null || oldPath == null || newPath == null) return false;
        try {
            return lib.fsRename(oldPath, newPath);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int fsWatchCreate(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return -1;
        try {
            return lib.fsWatchCreate(path);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int fsWatchPoll(int watchId, int timeoutMs) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.fsWatchPoll(watchId, timeoutMs);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void fsWatchDestroy(int watchId) {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.fsWatchDestroy(watchId);
        } catch (Throwable ignored) {}
    }

    public static String fsExtension(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.fsExtension(path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String fsBasename(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.fsBasename(path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String fsDirname(String path) {
        NativeLibrary lib = library();
        if (lib == null || path == null) return null;
        try {
            return lib.fsDirname(path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String fsJoin(String base, String child) {
        NativeLibrary lib = library();
        if (lib == null || base == null || child == null) return null;
        try {
            return lib.fsJoin(base, child);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * List directory entries with extension filtering using native implementation.
     * Returns entries as newline-separated strings: type|mtime|size|name
     * 
     * @param dirPath Directory to list
     * @param extensions Comma-separated extensions (e.g. ".txt,.md") or null for all
     * @param includeHidden Whether to include hidden files
     * @return Formatted string with entries, or null on error/unavailable
     */
    public static String fsListFiltered(String dirPath, String extensions, boolean includeHidden) {
        NativeLibrary lib = library();
        if (lib == null || dirPath == null) return null;
        try {
            return lib.fsListFiltered(dirPath, extensions, includeHidden);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Count entries in a directory (fast, no stat calls).
     */
    public static int fsCountEntries(String dirPath, boolean includeHidden) {
        NativeLibrary lib = library();
        if (lib == null || dirPath == null) return -1;
        try {
            return lib.fsCountEntries(dirPath, includeHidden);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMAGE OPERATIONS API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fast native image resize using ARGB pixel data (Java BufferedImage compatible).
     * 
     * @param srcPixels Source ARGB int array from BufferedImage.getRGB()
     * @param srcW Source width
     * @param srcH Source height
     * @param dstW Target width
     * @param dstH Target height
     * @param quality 0=fast/bilinear, 1=high/bicubic, 2=auto
     * @return Resized ARGB int array, or null if native unavailable/error
     */
    public static int[] imageResizeArgb(int[] srcPixels, int srcW, int srcH, int dstW, int dstH, int quality) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.imageResizeArgb(srcPixels, srcW, srcH, dstW, dstH, quality);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Calculate target dimensions to fit within max bounds while preserving aspect ratio.
     * 
     * @param srcW Source width
     * @param srcH Source height
     * @param maxW Maximum target width (0 = no limit)
     * @param maxH Maximum target height (0 = no limit)
     * @return int[2] with {targetWidth, targetHeight}, or null if native unavailable
     */
    public static int[] imageCalcFitSize(int srcW, int srcH, int maxW, int maxH) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.imageCalcFitSize(srcW, srcH, maxW, maxH);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION MATH API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Easing function: cosine ease-in-out */
    public static float easeCosine(float t) {
        NativeLibrary lib = library();
        if (lib == null) return (1f - (float) Math.cos(t * Math.PI)) * 0.5f;
        try { return lib.easeCosine(t); } catch (Throwable e) { return t; }
    }

    /** Easing function: smoothstep (3t² - 2t³) */
    public static float easeSmoothstep(float t) {
        NativeLibrary lib = library();
        if (lib == null) { t = Math.max(0f, Math.min(1f, t)); return t * t * (3 - 2 * t); }
        try { return lib.easeSmoothstep(t); } catch (Throwable e) { return t * t * (3 - 2 * t); }
    }

    /** Easing function: smootherstep (6t⁵ - 15t⁴ + 10t³) */
    public static float easeSmootherstep(float t) {
        NativeLibrary lib = library();
        if (lib == null) { t = Math.max(0f, Math.min(1f, t)); return t * t * t * (t * (t * 6 - 15) + 10); }
        try { return lib.easeSmootherstep(t); } catch (Throwable e) { return t * t * t * (t * (t * 6 - 15) + 10); }
    }

    /** Spring decay calculation */
    public static float springDecay(float current, float damping, float threshold) {
        NativeLibrary lib = library();
        if (lib == null) { current *= damping; return Math.abs(current) < threshold ? 0f : current; }
        try { return lib.springDecay(current, damping, threshold); } catch (Throwable e) { return current * damping; }
    }

    /** Calculate heartbeat scale factor */
    public static float heartbeatScale(float phase, float baseAmplitude, float spring) {
        NativeLibrary lib = library();
        if (lib == null) {
            float eased = (1f - (float) Math.cos(phase)) * 0.5f;
            return 1f + baseAmplitude * (eased * 2f - 1f) + spring;
        }
        try { return lib.heartbeatScale(phase, baseAmplitude, spring); } catch (Throwable e) {
            float eased = (1f - (float) Math.cos(phase)) * 0.5f;
            return 1f + baseAmplitude * (eased * 2f - 1f) + spring;
        }
    }

    /** Sample ECG waveform at given phase */
    public static float ecgSample(float phase) {
        NativeLibrary lib = library();
        if (lib == null) return 0f;
        try { return lib.ecgSample(phase); } catch (Throwable e) { return 0f; }
    }

    /** Calculate fade alpha for transition (easingType: 0=linear, 1=smoothstep, 2=smootherstep, 3=cosine) */
    public static float fadeAlpha(long elapsedMs, long durationMs, boolean fadeOut, int easingType) {
        NativeLibrary lib = library();
        if (lib == null) {
            float t = durationMs > 0 ? (float) elapsedMs / durationMs : 1f;
            t = Math.max(0f, Math.min(1f, t));
            float eased = t * t * (3 - 2 * t);
            return fadeOut ? eased : (1f - eased);
        }
        try { return lib.fadeAlpha(elapsedMs, durationMs, fadeOut, easingType); } catch (Throwable e) {
            float t = durationMs > 0 ? (float) elapsedMs / durationMs : 1f;
            t = Math.max(0f, Math.min(1f, t));
            float eased = t * t * (3 - 2 * t);
            return fadeOut ? eased : (1f - eased);
        }
    }

    /** Linearly interpolate between two ARGB colors */
    public static int colorLerp(int color1, int color2, float t) {
        NativeLibrary lib = library();
        if (lib == null) {
            t = Math.max(0f, Math.min(1f, t));
            int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
            int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
            int a = (int)(a1 + t * (a2 - a1)), r = (int)(r1 + t * (r2 - r1));
            int g = (int)(g1 + t * (g2 - g1)), b = (int)(b1 + t * (b2 - b1));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        try { return lib.colorLerp(color1, color2, t); } catch (Throwable e) { return color1; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEMORY POOL API
    // ═══════════════════════════════════════════════════════════════════════════

    public static int poolCreate(int blockSize, int initialBlocks) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.poolCreate(blockSize, initialBlocks);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void poolDestroy(int poolId) {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.poolDestroy(poolId);
        } catch (Throwable ignored) {}
    }

    public static int arenaCreate() {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.arenaCreate();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void arenaReset(int arenaId) {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.arenaReset(arenaId);
        } catch (Throwable ignored) {}
    }

    public static void arenaDestroy(int arenaId) {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.arenaDestroy(arenaId);
        } catch (Throwable ignored) {}
    }

    public static int internInit() {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.internInit();
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String intern(String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return str;
        try {
            return lib.intern(str);
        } catch (Throwable t) {
            return str;
        }
    }

    public static boolean internContains(String str) {
        NativeLibrary lib = library();
        if (lib == null || str == null) return false;
        try {
            return lib.internContains(str);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int internCount() {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.internCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void internClear() {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.internClear();
        } catch (Throwable ignored) {}
    }
}
