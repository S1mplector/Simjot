/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.ffi;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
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
import java.util.Locale;

import main.infrastructure.io.IoLog;
import main.infrastructure.io.ResourceLoader;

/**
 * Lazily loads the native library and provides fallbacks, even if not ideal.
 * 
 * Meant to be 
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

    public static SymbolLookup symbolLookup() {
        NativeLibrary lib = library();
        return lib != null ? lib.symbolLookup() : null;
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

    public static byte[] sha256Bytes(byte[] data) {
        NativeLibrary lib = library();
        if (lib == null || data == null) return null;
        try {
            return lib.sha256Bytes(data);
        } catch (Throwable t) {
            IoLog.warn("native-sha256-bytes", "Native SHA-256 bytes failed; falling back to Java.", t);
            return null;
        }
    }

    public static byte[] pbkdf2HmacSha256(String password, byte[] salt, int iterations, int keyLength) {
        NativeLibrary lib = library();
        if (lib == null || password == null || salt == null) return null;
        try {
            return lib.pbkdf2HmacSha256(password, salt, iterations, keyLength);
        } catch (Throwable t) {
            IoLog.warn("native-pbkdf2", "Native PBKDF2-HMAC-SHA256 failed; falling back to Java.", t);
            return null;
        }
    }

    public static byte[] aes256GcmEncrypt(byte[] plaintext, byte[] key, byte[] iv) {
        NativeLibrary lib = library();
        if (lib == null || plaintext == null || key == null || iv == null) return null;
        try {
            return lib.aes256GcmEncrypt(plaintext, key, iv);
        } catch (Throwable t) {
            IoLog.warn("native-aes-encrypt", "Native AES-256-GCM encrypt failed; falling back to Java.", t);
            return null;
        }
    }

    public static byte[] aes256GcmDecrypt(byte[] ciphertext, byte[] key, byte[] iv) {
        NativeLibrary lib = library();
        if (lib == null || ciphertext == null || key == null || iv == null) return null;
        try {
            return lib.aes256GcmDecrypt(ciphertext, key, iv);
        } catch (Throwable t) {
            IoLog.warn("native-aes-decrypt", "Native AES-256-GCM decrypt failed; falling back to Java.", t);
            return null;
        }
    }

    /**
     * Unified poetry analysis that performs vocabulary, theme, sound, and meter analysis in one call.
     * Much faster than calling individual analysis methods separately, which is what I used to do before. 
     * 
     * @param text The poem text to analyze
     * @return PoetryAnalysisResult containing all metrics, or null if native analysis fails
     */
    public static PoetryAnalysisResult poetryAnalyzeAll(String text) {
        NativeLibrary lib = library();
        if (lib == null || text == null) return null;
        try {
            return lib.poetryAnalyzeAll(text);
        } catch (Throwable t) {
            IoLog.warn("native-poetry-analysis", "Native unified poetry analysis failed; falling back to Java.", t);
            return null;
        }
    }

    /**
     * Result container for unified poetry analysis.
     */
    public static class PoetryAnalysisResult {
        public final double ttr; // Type-Token Ratio percentage
        public final String dominantTheme;
        public final int soundDeviceCount;
        public final String dominantMeter;
        public final int uniqueWords;
        public final int totalWords;
        
        public PoetryAnalysisResult(double ttr, String dominantTheme, int soundDeviceCount, 
                                  String dominantMeter, int uniqueWords, int totalWords) {
            this.ttr = ttr;
            this.dominantTheme = dominantTheme;
            this.soundDeviceCount = soundDeviceCount;
            this.dominantMeter = dominantMeter;
            this.uniqueWords = uniqueWords;
            this.totalWords = totalWords;
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

    public static List<String> textExtractTags(String text) {
        if (!textUtilsReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.textExtractTags(text);
        } catch (Throwable t) {
            IoLog.warn("native-text", "Native tag extraction failed.", t);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTOCORRECT API
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean autocorrectReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasAutocorrectSupport();
    }

    /**
     * Find correction by replacing characters with adjacent QWERTY keys.
     * @return Correction or null if none found
     */
    public static String autocorrectAdjacentKey(String word) {
        if (!autocorrectReady() || word == null || word.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectAdjacentKey(word);
        } catch (Throwable t) {
            IoLog.warn("native-autocorrect", "Native adjacent key correction failed.", t);
            return null;
        }
    }

    /**
     * Find correction by replacing common phonetic patterns.
     * @return Correction or null if none found
     */
    public static String autocorrectPhonetic(String word) {
        if (!autocorrectReady() || word == null || word.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectPhonetic(word);
        } catch (Throwable t) {
            IoLog.warn("native-autocorrect", "Native phonetic correction failed.", t);
            return null;
        }
    }

    /**
     * Combined autocorrect: tries phonetic, adjacent key, then spell suggestions.
     * @return Correction or null if none found
     */
    public static String autocorrectCorrect(String word) {
        if (!autocorrectReady() || word == null || word.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectCorrect(word);
        } catch (Throwable t) {
            IoLog.warn("native-autocorrect", "Native autocorrect failed.", t);
            return null;
        }
    }

    /**
     * Check if word starts with vowel sound (for a/an determination).
     * @return true if vowel sound, false if consonant, null on error
     */
    public static Boolean autocorrectStartsVowelSound(String word) {
        if (!autocorrectReady() || word == null || word.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectStartsVowelSound(word);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Check if two-word phrase has a correction (e.g., "should of" -> "should have").
     * @return Corrected phrase or null if none found
     */
    public static String autocorrectPhrase(String word1, String word2) {
        if (!autocorrectReady() || word1 == null || word2 == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectPhrase(word1, word2);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Fix capitalization issues (standalone i, double spaces).
     * @return Fixed text or null on error
     */
    public static String autocorrectFixCaps(String text) {
        if (!autocorrectReady() || text == null) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.autocorrectFixCaps(text);
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

    public static Integer jsonCountKeys(String json) {
        NativeLibrary lib = library();
        if (lib == null || json == null) return null;
        try {
            return lib.jsonCountKeys(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> jsonGetKeys(String json) {
        NativeLibrary lib = library();
        if (lib == null || json == null) return null;
        try {
            return lib.jsonGetKeys(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String jsonGetPath(String json, String path) {
        NativeLibrary lib = library();
        if (lib == null || json == null || path == null) return null;
        try {
            return lib.jsonGetPath(json, path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> jsonParseStringArray(String json) {
        NativeLibrary lib = library();
        if (lib == null || json == null) return null;
        try {
            return lib.jsonParseStringArray(json);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH API
    // ═══════════════════════════════════════════════════════════════════════════

    public static long searchFind(String haystack, String needle) {
        NativeLibrary lib = library();
        if (lib == null || haystack == null || needle == null) return -1;
        try {
            return lib.searchFind(haystack, needle);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long searchFindCi(String haystack, String needle) {
        NativeLibrary lib = library();
        if (lib == null || haystack == null || needle == null) return -1;
        try {
            return lib.searchFindCi(haystack, needle);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean searchContains(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        if (needle.isEmpty()) return true;
        NativeLibrary lib = library();
        if (lib != null) {
            try {
                return lib.searchFind(haystack, needle) >= 0;
            } catch (Throwable ignored) {
                return haystack.contains(needle);
            }
        }
        return haystack.contains(needle);
    }

    public static boolean searchContainsCi(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        if (needle.isEmpty()) return true;
        NativeLibrary lib = library();
        if (lib != null) {
            try {
                return lib.searchFindCi(haystack, needle) >= 0;
            } catch (Throwable ignored) {
                return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
            }
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    public static int searchCount(String haystack, String needle) {
        NativeLibrary lib = library();
        if (lib == null || haystack == null || needle == null) return 0;
        try {
            return lib.searchCount(haystack, needle);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static long[] searchFindAll(String haystack, String needle, int maxResults) {
        NativeLibrary lib = library();
        if (lib == null || haystack == null || needle == null) return null;
        try {
            return lib.searchFindAll(haystack, needle, maxResults);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean searchFuzzyMatch(String text, String pattern, int maxDistance) {
        NativeLibrary lib = library();
        if (lib == null || text == null || pattern == null) return false;
        try {
            int count = lib.searchFuzzy(text, pattern, maxDistance, null, null, 1);
            return count > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static MemorySegment searchAcBuild(String[] patterns) {
        NativeLibrary lib = library();
        if (lib == null || patterns == null || patterns.length == 0) return null;
        try {
            return lib.searchAcBuild(patterns);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int searchAcFind(MemorySegment handle, String text, long[] outPositions, int[] outPatterns, int maxResults) {
        NativeLibrary lib = library();
        if (lib == null || handle == null || text == null) return 0;
        try {
            return lib.searchAcFind(handle, text, outPositions, outPatterns, maxResults);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void searchAcFree(MemorySegment handle) {
        NativeLibrary lib = library();
        if (lib == null || handle == null) return;
        try {
            lib.searchAcFree(handle);
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH SEARCH API - Global entry search optimization
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean searchBatchReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasSearchBatchSupport();
    }

    /**
     * Perform batch search across multiple directories.
     * @param query Search query
     * @param directories List of directory paths to search
     * @param extensions File extensions to include (e.g., ".note,.txt")
     * @param maxResults Maximum results to return
     * @return List of search results, or null if native not available
     */
    public static List<BatchSearchResult> searchBatch(String query, List<String> directories, 
                                                       String extensions, int maxResults) {
        NativeLibrary lib = library();
        if (lib == null || !lib.hasSearchBatchSupport()) return null;
        if (query == null || query.isEmpty() || directories == null || directories.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // Build newline-separated directory list
            StringBuilder dirBuilder = new StringBuilder();
            for (String dir : directories) {
                if (dir != null && !dir.isEmpty()) {
                    if (dirBuilder.length() > 0) dirBuilder.append('\n');
                    dirBuilder.append(dir);
                }
            }
            
            byte[] resultBytes = lib.searchBatch(query, dirBuilder.toString(), extensions, maxResults);
            if (resultBytes == null) return null;
            
            return parseBatchSearchResults(resultBytes);
        } catch (Throwable t) {
            IoLog.warn("native-search-batch", "Native batch search failed.", t);
            return null;
        }
    }

    private static List<BatchSearchResult> parseBatchSearchResults(byte[] data) {
        if (data == null || data.length < 4) return Collections.emptyList();
        
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.nativeOrder());
        int count = buf.getInt();
        if (count <= 0) return Collections.emptyList();
        
        List<BatchSearchResult> results = new ArrayList<>(count);
        
        for (int i = 0; i < count && buf.remaining() > 0; i++) {
            try {
                // Read path
                int pathLen = buf.getInt();
                if (pathLen <= 0 || pathLen > buf.remaining()) break;
                byte[] pathBytes = new byte[pathLen];
                buf.get(pathBytes);
                String path = new String(pathBytes, StandardCharsets.UTF_8);
                
                // Read title
                int titleLen = buf.getInt();
                if (titleLen < 0 || titleLen > buf.remaining()) break;
                byte[] titleBytes = new byte[titleLen];
                buf.get(titleBytes);
                String title = new String(titleBytes, StandardCharsets.UTF_8);
                
                // Read snippet
                int snippetLen = buf.getInt();
                if (snippetLen < 0 || snippetLen > buf.remaining()) break;
                byte[] snippetBytes = new byte[snippetLen];
                buf.get(snippetBytes);
                String snippet = new String(snippetBytes, StandardCharsets.UTF_8);
                
                // Read mood, savedAt, matchCount
                int mood = buf.getInt();
                long savedAt = buf.getLong();
                int matchCount = buf.getInt();
                
                // Read tags
                int tagCount = buf.getInt();
                List<String> tags = new ArrayList<>(tagCount);
                for (int t = 0; t < tagCount && buf.remaining() >= 4; t++) {
                    int tagLen = buf.getInt();
                    if (tagLen <= 0 || tagLen > buf.remaining()) break;
                    byte[] tagBytes = new byte[tagLen];
                    buf.get(tagBytes);
                    tags.add(new String(tagBytes, StandardCharsets.UTF_8));
                }
                
                results.add(new BatchSearchResult(path, title, snippet, mood, savedAt, matchCount, tags));
            } catch (Exception e) {
                break; // Stop on parsing error
            }
        }
        
        return results;
    }

    /**
     * Result from native batch search.
     */
    public static final class BatchSearchResult {
        public final String filePath;
        public final String title;
        public final String snippet;
        public final int mood;
        public final long savedAt;
        public final int matchCount;
        public final List<String> tags;

        public BatchSearchResult(String filePath, String title, String snippet, 
                                  int mood, long savedAt, int matchCount, List<String> tags) {
            this.filePath = filePath;
            this.title = title;
            this.snippet = snippet;
            this.mood = mood;
            this.savedAt = savedAt;
            this.matchCount = matchCount;
            this.tags = tags != null ? tags : Collections.emptyList();
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

    public static PoetryVocabStats poetryGetVocabStats() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            double[] stats = lib.poetryGetVocabStats();
            if (stats == null || stats.length < 4) return null;
            return new PoetryVocabStats((int) stats[0], (int) stats[1], stats[2], stats[3]);
        } catch (Throwable t) {
            return null;
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

    public static int poetryGetLineSyllables(int lineIndex) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.poetryGetLineSyllables(lineIndex);
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
    // AERO/GLASS EFFECT API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute outer glow alpha using ease-out curve.
     * @param layer Current layer (1 to size)
     * @param size Total layers
     * @param maxAlpha Maximum alpha (0-255)
     * @return Computed alpha, or Java fallback if native unavailable
     */
    public static int aeroOuterGlowAlpha(int layer, int size, int maxAlpha) {
        NativeLibrary lib = library();
        if (lib == null || size <= 0 || layer <= 0) {
            // Java fallback: alpha = maxAlpha * (layer/size)^2
            float t = (float) layer / size;
            return Math.min(255, Math.max(0, Math.round(maxAlpha * t * t)));
        }
        try {
            return lib.aeroOuterGlowAlpha(layer, size, maxAlpha);
        } catch (Throwable t) {
            float a = (float) layer / size;
            return Math.min(255, Math.max(0, Math.round(maxAlpha * a * a)));
        }
    }

    /**
     * Compute inner shadow alpha using linear fade.
     * @param layer Current layer (1 to size)
     * @param size Total layers
     * @param maxAlpha Maximum alpha (0-255)
     * @return Computed alpha, or Java fallback if native unavailable
     */
    public static int aeroInnerShadowAlpha(int layer, int size, int maxAlpha) {
        NativeLibrary lib = library();
        if (lib == null || size <= 0 || layer <= 0) {
            // Java fallback: alpha = maxAlpha * (1 - layer/size)
            float t = (float) layer / size;
            return Math.min(255, Math.max(0, Math.round(maxAlpha * (1f - t))));
        }
        try {
            return lib.aeroInnerShadowAlpha(layer, size, maxAlpha);
        } catch (Throwable t) {
            float a = (float) layer / size;
            return Math.min(255, Math.max(0, Math.round(maxAlpha * (1f - a))));
        }
    }

    /**
     * Interpolate between two ARGB colors.
     * @param color1 First color (ARGB)
     * @param color2 Second color (ARGB)
     * @param t Interpolation factor (0.0-1.0)
     * @return Interpolated color
     */
    public static int aeroLerpColor(int color1, int color2, float t) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Java fallback
            if (t <= 0f) return color1;
            if (t >= 1f) return color2;
            int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
            int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
            int a = (int)(a1 + (a2 - a1) * t + 0.5f);
            int r = (int)(r1 + (r2 - r1) * t + 0.5f);
            int g = (int)(g1 + (g2 - g1) * t + 0.5f);
            int b = (int)(b1 + (b2 - b1) * t + 0.5f);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        try {
            return lib.aeroLerpColor(color1, color2, t);
        } catch (Throwable e) {
            return color1;
        }
    }

    /**
     * Blend foreground color over background using alpha compositing.
     * @param fg Foreground color with alpha (ARGB)
     * @param bg Background color (ARGB)
     * @return Blended color
     */
    public static int aeroBlendOver(int fg, int bg) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Java fallback
            int fgA = (fg >> 24) & 0xFF;
            if (fgA == 255) return fg | 0xFF000000;
            if (fgA == 0) return bg | 0xFF000000;
            int fgR = (fg >> 16) & 0xFF, fgG = (fg >> 8) & 0xFF, fgB = fg & 0xFF;
            int bgR = (bg >> 16) & 0xFF, bgG = (bg >> 8) & 0xFF, bgB = bg & 0xFF;
            int invA = 255 - fgA;
            int r = (fgR * fgA + bgR * invA + 127) / 255;
            int g = (fgG * fgA + bgG * invA + 127) / 255;
            int b = (fgB * fgA + bgB * invA + 127) / 255;
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        try {
            return lib.aeroBlendOver(fg, bg);
        } catch (Throwable t) {
            return fg;
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

    /** Calculate disappear animation value (1=visible, 0=gone) */
    public static float disappearValue(float t) {
        NativeLibrary lib = library();
        if (lib == null) {
            t = Math.max(0f, Math.min(1f, t));
            float eased = t * t * t * (t * (t * 6 - 15) + 10);
            return 1f - eased;
        }
        try { return lib.disappearValue(t); } catch (Throwable e) { return 1f - t; }
    }

    /** Calculate collapse height multiplier (1=full height, 0=collapsed) */
    public static float collapseHeight(float t) {
        NativeLibrary lib = library();
        if (lib == null) {
            t = Math.max(0f, Math.min(1f, t));
            float delayedT = Math.max(0f, Math.min(1f, (t - 0.3f) / 0.7f));
            float inv = 1f - delayedT;
            return inv * inv;
        }
        try { return lib.collapseHeight(t); } catch (Throwable e) { return 1f - t; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI SCALING API - Native DPI-aware scaling
    // ═══════════════════════════════════════════════════════════════════════════

    private static float cachedPrimaryScale = -1f;

    /** Get number of connected displays */
    public static int getDisplayCount() {
        NativeLibrary lib = library();
        if (lib == null) return 1;
        try { return lib.getDisplayCount(); } catch (Throwable e) { return 1; }
    }

    /** Get scale factor for a specific display (0-indexed) */
    public static float getDisplayScale(int displayIndex) {
        NativeLibrary lib = library();
        if (lib == null) return 1.0f;
        try { return lib.getDisplayScale(displayIndex); } catch (Throwable e) { return 1.0f; }
    }

    /** Get scale factor for the primary display (cached for performance) */
    public static float getPrimaryDisplayScale() {
        if (cachedPrimaryScale > 0) return cachedPrimaryScale;
        NativeLibrary lib = library();
        if (lib == null) {
            cachedPrimaryScale = 1.0f;
            return 1.0f;
        }
        try {
            cachedPrimaryScale = lib.getPrimaryDisplayScale();
            return cachedPrimaryScale;
        } catch (Throwable e) {
            cachedPrimaryScale = 1.0f;
            return 1.0f;
        }
    }

    /** Get DPI for a specific display */
    public static float getDisplayDpi(int displayIndex) {
        NativeLibrary lib = library();
        if (lib == null) return 96.0f;
        try { return lib.getDisplayDpi(displayIndex); } catch (Throwable e) { return 96.0f; }
    }

    /** Invalidate cached display scale values (call when displays change) */
    public static void invalidateDisplayCache() {
        cachedPrimaryScale = -1f;
        NativeLibrary lib = library();
        if (lib != null) {
            try { lib.invalidateDisplayCache(); } catch (Throwable ignored) {}
        }
    }

    /** Scale a dimension value using native calculation */
    public static int scaleDimension(int value, float scale) {
        NativeLibrary lib = library();
        if (lib == null) {
            float s = scale > 0 ? scale : getPrimaryDisplayScale();
            return Math.round(value * s);
        }
        try { return lib.scaleDimension(value, scale); } catch (Throwable e) {
            return Math.round(value * (scale > 0 ? scale : 1.0f));
        }
    }

    /** Scale a dimension using primary display scale */
    public static int scale(int value) {
        return scaleDimension(value, 0);
    }

    /** Scale a float value */
    public static float scaleValue(float value, float scale) {
        NativeLibrary lib = library();
        if (lib == null) {
            float s = scale > 0 ? scale : getPrimaryDisplayScale();
            return value * s;
        }
        try { return lib.scaleValue(value, scale); } catch (Throwable e) {
            return value * (scale > 0 ? scale : 1.0f);
        }
    }

    /** Scale a font size with proper rounding for readability */
    public static float scaleFontSize(float baseSize, float scale) {
        NativeLibrary lib = library();
        if (lib == null) {
            float s = scale > 0 ? scale : getPrimaryDisplayScale();
            float scaled = baseSize * s;
            scaled = Math.round(scaled * 2.0f) / 2.0f;
            return Math.max(8.0f, scaled);
        }
        try { return lib.scaleFontSize(baseSize, scale); } catch (Throwable e) {
            return Math.max(8.0f, baseSize * (scale > 0 ? scale : 1.0f));
        }
    }

    /** Scale a font size using primary display scale */
    public static float scaleFontSize(float baseSize) {
        return scaleFontSize(baseSize, 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SETUP & INITIALIZATION API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize Simjot directory structure with native verification.
     * Creates root and all subdirectories atomically.
     * 
     * @param rootPath Path to Simjot root folder
     * @return 0 on success, negative error code on failure
     */
    public static int setupInit(String rootPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Fallback: use Java to create directories
            try {
                java.io.File root = new java.io.File(rootPath);
                if (!root.exists() && !root.mkdirs()) return -3;
                String[] subdirs = {"notebooks", "mood", "settings", "wallpapers"};
                for (String sub : subdirs) {
                    java.io.File f = new java.io.File(root, sub);
                    if (!f.exists() && !f.mkdirs()) return -4;
                }
                return 0;
            } catch (Exception e) {
                return -1;
            }
        }
        try { return lib.setupInit(rootPath); } catch (Throwable e) { return -1; }
    }

    /**
     * Verify setup is complete.
     * 
     * @param rootPath Path to Simjot root folder
     * @return Bitmask: bit 0 = root, bits 1-4 = subdirs, bit 7 = marker
     */
    public static int setupVerify(String rootPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Fallback check
            java.io.File root = new java.io.File(rootPath);
            if (!root.isDirectory()) return 0;
            int result = 1; // root exists
            String[] subdirs = {"notebooks", "mood", "settings", "wallpapers"};
            for (int i = 0; i < subdirs.length; i++) {
                if (new java.io.File(root, subdirs[i]).isDirectory()) {
                    result |= (1 << (i + 1));
                }
            }
            return result;
        }
        try { return lib.setupVerify(rootPath); } catch (Throwable e) { return 0; }
    }

    /**
     * Check if directory is truly writable by test write.
     */
    public static boolean verifyWritable(String dirPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            return new java.io.File(dirPath).canWrite();
        }
        try { return lib.verifyWritable(dirPath); } catch (Throwable e) { return false; }
    }

    /**
     * Get detailed setup status.
     * 
     * @param rootPath Path to Simjot root folder
     * @return Array: [root_exists, root_writable, notebooks_ok, mood_ok, 
     *                 settings_ok, wallpapers_ok, marker_valid, setup_complete]
     */
    public static int[] setupStatus(String rootPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            int[] result = new int[8];
            java.io.File root = new java.io.File(rootPath);
            result[0] = root.isDirectory() ? 1 : 0;
            result[1] = root.canWrite() ? 1 : 0;
            String[] subdirs = {"notebooks", "mood", "settings", "wallpapers"};
            int okCount = result[1];
            for (int i = 0; i < subdirs.length; i++) {
                java.io.File f = new java.io.File(root, subdirs[i]);
                result[2 + i] = f.isDirectory() && f.canWrite() ? 1 : 0;
                if (result[2 + i] == 1) okCount++;
            }
            result[7] = (okCount == 5) ? 1 : 0;
            return result;
        }
        try { return lib.setupStatus(rootPath); } catch (Throwable e) { return new int[8]; }
    }

    /**
     * Write config file atomically using native I/O.
     */
    public static boolean writeConfig(String configPath, String rootPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Fallback: Java write
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(configPath))) {
                w.println(rootPath);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        try { return lib.writeConfig(configPath, rootPath); } catch (Throwable e) { return false; }
    }

    /**
     * Read config file and verify root exists.
     */
    public static String readConfig(String configPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Fallback: Java read
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(configPath))) {
                String path = r.readLine();
                if (path != null && new java.io.File(path.trim()).isDirectory()) {
                    return path.trim();
                }
            } catch (Exception ignored) {}
            return null;
        }
        try { return lib.readConfig(configPath); } catch (Throwable e) { return null; }
    }

    /**
     * Create directory with all parents using native I/O.
     */
    public static boolean createDirectory(String dirPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            return new java.io.File(dirPath).mkdirs();
        }
        try { return lib.createDirectory(dirPath); } catch (Throwable e) { return false; }
    }

    /**
     * Check if first-time setup is needed.
     */
    public static boolean needsSetup(String configPath) {
        NativeLibrary lib = library();
        if (lib == null) {
            return !new java.io.File(configPath).exists();
        }
        try { return lib.needsSetup(configPath); } catch (Throwable e) { return true; }
    }

    /**
     * Check if setup is complete with all directories verified.
     */
    public static boolean isSetupComplete(String rootPath) {
        int[] status = setupStatus(rootPath);
        return status[7] == 1;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WATCHDOG API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Watchdog actions */
    public static final int WD_ACTION_NONE = 0;
    public static final int WD_ACTION_CALLBACK = 1;
    public static final int WD_ACTION_EXIT = 2;
    public static final int WD_ACTION_HALT = 3;

    /** Watchdog states */
    public static final int WD_STATE_INACTIVE = 0;
    public static final int WD_STATE_RUNNING = 1;
    public static final int WD_STATE_TRIGGERED = 2;
    public static final int WD_STATE_CANCELLED = 3;

    // Java fallback watchdog implementation
    private static final java.util.Map<Integer, Thread> javaWatchdogs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger javaWdCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * Start a native watchdog timer.
     * Falls back to Java thread-based watchdog if native unavailable.
     */
    public static int watchdogStart(long timeoutMs, int action, String name) {
        NativeLibrary lib = library();
        if (lib != null) {
            try {
                int id = lib.watchdogStart(timeoutMs, action, name);
                if (id >= 0) return id;
            } catch (Throwable ignored) {}
        }
        
        // Java fallback
        int id = 100 + javaWdCounter.getAndIncrement();
        final int finalId = id;
        final String finalName = name != null ? name : "watchdog-" + id;
        Thread wd = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
                System.err.println("[Watchdog-Java] '" + finalName + "' triggered after " + timeoutMs + " ms");
                if (action == WD_ACTION_HALT) {
                    Runtime.getRuntime().halt(1);
                } else if (action == WD_ACTION_EXIT) {
                    System.exit(1);
                }
            } catch (InterruptedException ignored) {
                // Cancelled
            } finally {
                javaWatchdogs.remove(finalId);
            }
        }, "JavaWatchdog-" + finalName);
        wd.setDaemon(true);
        javaWatchdogs.put(id, wd);
        wd.start();
        return id;
    }

    /**
     * Cancel a running watchdog.
     */
    public static boolean watchdogCancel(int id) {
        // Try native first
        if (id < 100) {
            NativeLibrary lib = library();
            if (lib != null) {
                try { return lib.watchdogCancel(id); } catch (Throwable ignored) {}
            }
        }
        
        // Java fallback
        Thread wd = javaWatchdogs.remove(id);
        if (wd != null) {
            wd.interrupt();
            return true;
        }
        return false;
    }

    /**
     * Reset a watchdog timer.
     */
    public static boolean watchdogReset(int id) {
        if (id < 100) {
            NativeLibrary lib = library();
            if (lib != null) {
                try { return lib.watchdogReset(id); } catch (Throwable ignored) {}
            }
        }
        return false; // Java fallback doesn't support reset
    }

    /**
     * Get watchdog state.
     */
    public static int watchdogState(int id) {
        if (id < 100) {
            NativeLibrary lib = library();
            if (lib != null) {
                try { return lib.watchdogState(id); } catch (Throwable ignored) {}
            }
        }
        
        // Java fallback
        Thread wd = javaWatchdogs.get(id);
        if (wd == null) return WD_STATE_INACTIVE;
        return wd.isAlive() ? WD_STATE_RUNNING : WD_STATE_TRIGGERED;
    }

    /**
     * Get remaining time for watchdog.
     */
    public static long watchdogRemaining(int id) {
        if (id < 100) {
            NativeLibrary lib = library();
            if (lib != null) {
                try { return lib.watchdogRemaining(id); } catch (Throwable ignored) {}
            }
        }
        return -1; // Java fallback doesn't track remaining time
    }

    /**
     * Force immediate process halt.
     */
    public static void forceHalt() {
        NativeLibrary lib = library();
        if (lib != null) {
            try { lib.forceHalt(); } catch (Throwable ignored) {}
        }
        Runtime.getRuntime().halt(1);
    }

    /**
     * Get monotonic time in milliseconds.
     */
    public static long monotonicTimeMs() {
        NativeLibrary lib = library();
        if (lib != null) {
            try { return lib.monotonicTimeMs(); } catch (Throwable ignored) {}
        }
        return System.nanoTime() / 1_000_000L;
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

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE JSON PARSING - Fast dictionary loading
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse dictionary JSON file and extract all words.
     * Returns words as a list.
     */
    public static java.util.List<String> jsonLoadDictWords(String filePath) {
        NativeLibrary lib = library();
        if (lib == null || filePath == null) return null;
        try {
            return lib.jsonLoadDictWords(filePath);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Poetry vocabulary stats from native analysis.
     */
    public record PoetryVocabStats(int totalWords, int uniqueWords, double lexicalDiversity, double avgWordLength) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE MOOD ANALYTICS - Fast mood log parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load and parse mood log file using native parser.
     * @return Number of samples parsed, or negative on error
     */
    public static int moodLoad(String filePath) {
        NativeLibrary lib = library();
        if (lib == null || filePath == null) return -1;
        try {
            return lib.moodLoad(filePath);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute daily statistics from loaded samples.
     * @param daysBack Number of days to analyze (0 = all time)
     * @return Number of days with data
     */
    public static int moodComputeDaily(int daysBack) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.moodComputeDaily(daysBack);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute analytics summary (volatility, streaks).
     * @param threshold Good/bad mood threshold (typically 60)
     */
    public static int moodComputeSummary(int threshold) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.moodComputeSummary(threshold);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Mood daily stats record.
     */
    public record MoodDailyStats(
        int dateDays,
        int sampleCount,
        double average,
        short min,
        short max,
        double avgJoy,
        double avgCalm,
        double avgGratitude,
        double avgEnergy,
        double avgSadness,
        double avgAnger,
        double avgAnxiety,
        double avgStress
    ) {}

    /**
     * Get daily stats by index.
     * @param index Daily stats index (0 to moodDailyCount()-1)
     * @return Daily stats or null on error
     */
    public static MoodDailyStats moodGetDaily(int index) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.moodGetDaily(index);
            if (data == null || data.length < 84) return null;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int dateDays = buf.getInt();
            int sampleCount = buf.getInt();
            double average = buf.getDouble();
            short min = buf.getShort();
            short max = buf.getShort();
            double avgJoy = buf.getDouble();
            double avgCalm = buf.getDouble();
            double avgGratitude = buf.getDouble();
            double avgEnergy = buf.getDouble();
            double avgSadness = buf.getDouble();
            double avgAnger = buf.getDouble();
            double avgAnxiety = buf.getDouble();
            double avgStress = buf.getDouble();
            return new MoodDailyStats(dateDays, sampleCount, average, min, max,
                avgJoy, avgCalm, avgGratitude, avgEnergy, avgSadness, avgAnger, avgAnxiety, avgStress);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Mood analytics summary record.
     */
    public record MoodSummary(
        double overallAverage,
        double volatility,
        int currentStreak,
        int longestGoodStreak,
        int longestBadStreak,
        int totalSamples,
        int totalDays
    ) {}

    /**
     * Get analytics summary after computing.
     */
    public static MoodSummary moodGetSummary() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.moodGetSummary();
            if (data == null || data.length < 56) return null;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            double overallAvg = buf.getDouble();
            double volatility = buf.getDouble();
            int currentStreak = buf.getInt();
            int longestGood = buf.getInt();
            int longestBad = buf.getInt();
            int totalSamples = buf.getInt();
            int totalDays = buf.getInt();
            return new MoodSummary(overallAvg, volatility, currentStreak, longestGood, longestBad, totalSamples, totalDays);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Get number of daily stats entries.
     */
    public static int moodDailyCount() {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        try {
            return lib.moodDailyCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Clear all loaded mood data.
     */
    public static void moodClear() {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.moodClear();
        } catch (Throwable ignored) {}
    }

    /**
     * Compute smoothed mood values (rolling average).
     * Input values should use -1 for missing entries.
     */
    public static double[] moodSmooth(double[] values, int window) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length == 0 || window <= 0) return null;
        try {
            return lib.moodSmooth(values, window);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Compute mood volatility (standard deviation).
     * Input values should use -1 for missing entries.
     */
    public static double moodVolatility(double[] values) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length < 2) return Double.NaN;
        try {
            return lib.moodVolatility(values);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute mood streaks.
     * Input values should use -1 for missing entries.
     */
    public static int[] moodStreaks(double[] values, double threshold) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length == 0) return null;
        try {
            return lib.moodStreaks(values, threshold);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE MOOD GRAPHICS - Fast chart rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Render a mood sparkline chart to a BufferedImage.
     * 
     * @param values Mood values (0-100)
     * @param width Output width
     * @param height Output height
     * @param bgColor Background color (ARGB)
     * @param lineThickness Line thickness (1-5)
     * @return BufferedImage or null if native unavailable
     */
    public static java.awt.image.BufferedImage moodSparkline(int[] values, int width, int height, 
                                                              int bgColor, int lineThickness) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length == 0) return null;
        try {
            int[] pixels = lib.moodSparkline(values, width, height, bgColor, lineThickness);
            if (pixels == null) return null;
            
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, width, height, pixels, 0, width);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood bar chart to a BufferedImage.
     * 
     * @param values Mood values (0-100, -1 for missing)
     * @param width Output width
     * @param height Output height
     * @param bgColor Background color (ARGB)
     * @param barSpacing Spacing between bars
     * @return BufferedImage or null if native unavailable
     */
    public static java.awt.image.BufferedImage moodBarChart(int[] values, int width, int height,
                                                             int bgColor, int barSpacing) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length == 0) return null;
        try {
            int[] pixels = lib.moodBarChart(values, width, height, bgColor, barSpacing);
            if (pixels == null) return null;
            
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, width, height, pixels, 0, width);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood gauge to a BufferedImage.
     * 
     * @param value Mood value (0-100)
     * @param size Output size (width=height)
     * @param bgColor Background color (ARGB)
     * @param trackColor Track color (ARGB)
     * @param thickness Arc thickness
     * @return BufferedImage or null if native unavailable
     */
    public static java.awt.image.BufferedImage moodGauge(int value, int size,
                                                          int bgColor, int trackColor, int thickness) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            int[] pixels = lib.moodGauge(value, size, bgColor, trackColor, thickness);
            if (pixels == null) return null;
            
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, size, size, pixels, 0, size);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood heatmap to a BufferedImage.
     * 
     * @param values Mood values (0-100, -1 for missing)
     * @param cols Number of columns
     * @param cellSize Cell size in pixels
     * @param cellGap Gap between cells
     * @param bgColor Background color (ARGB)
     * @param emptyColor Color for missing data (ARGB)
     * @return BufferedImage or null if native unavailable
     */
    public static java.awt.image.BufferedImage moodHeatmap(int[] values, int cols,
                                                            int cellSize, int cellGap,
                                                            int bgColor, int emptyColor) {
        NativeLibrary lib = library();
        if (lib == null || values == null || values.length == 0) return null;
        try {
            int rows = (values.length + cols - 1) / cols;
            int width = cols * (cellSize + cellGap) - cellGap;
            int height = rows * (cellSize + cellGap) - cellGap;
            
            int[] pixels = lib.moodHeatmap(values, cols, cellSize, cellGap, 
                                            width, height, bgColor, emptyColor);
            if (pixels == null) return null;
            
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, width, height, pixels, 0, width);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE METADATA UTILITIES - Fast native file operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Count words in a text string using native code.
     */
    public static int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.countWords(text);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Count words in a file using native code (memory efficient, reads in chunks).
     */
    public static int countWordsFile(String path) {
        if (path == null || path.isEmpty()) return -1;
        NativeLibrary lib = library();
        if (lib == null) return -1;
        try {
            return lib.countWordsFile(path);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Extract first non-empty line from file as title.
     */
    public static String extractTitle(String path) {
        if (path == null || path.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.extractTitle(path);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * File metadata record: word count, title, size, mtime.
     */
    public record FileMeta(int wordCount, String title, long size, long mtime) {}

    /**
     * Get file metadata in a single native call (word count, title, size, mtime).
     */
    public static FileMeta getFileMeta(String path) {
        if (path == null || path.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.fileMetaBatch(path);
            if (data == null || data.length < 24) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            int wordCount = buf.getInt();
            long size = buf.getLong();
            long mtime = buf.getLong();
            int titleLen = buf.getInt();
            if (titleLen < 0) titleLen = 0;
            if (titleLen > buf.remaining()) titleLen = buf.remaining();
            byte[] titleBytes = new byte[titleLen];
            buf.get(titleBytes);
            String title = new String(titleBytes, StandardCharsets.UTF_8);
            return new FileMeta(wordCount, title, size, mtime);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Directory entry with metadata.
     */
    public record DirEntry(String name, long size, long mtime) {}

    /**
     * List files in directory with metadata (name, size, mtime).
     * @param dirPath directory path
     * @param extension file extension filter (e.g., ".txt") or null for all
     */
    public static java.util.List<DirEntry> listFilesMeta(String dirPath, String extension) {
        if (dirPath == null || dirPath.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            byte[] data = lib.listFilesMeta(dirPath, extension);
            if (data == null || data.length < 4) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            List<DirEntry> entries = new ArrayList<>();
            while (buf.remaining() >= 4) {
                int nameLen = buf.getInt();
                if (nameLen <= 0 || nameLen > buf.remaining()) break;
                byte[] nameBytes = new byte[nameLen];
                buf.get(nameBytes);
                if (buf.remaining() < 16) break;
                long size = buf.getLong();
                long mtime = buf.getLong();
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                entries.add(new DirEntry(name, size, mtime));
            }
            return entries;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT PROFILER API
    // ═══════════════════════════════════════════════════════════════════════════

    public static Boolean profilerInit(int sampleIntervalMs) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerInit(sampleIntervalMs);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Boolean profilerStart() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerStart();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Boolean profilerStop() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerStop();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void profilerReset() {
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.profilerReset();
        } catch (Throwable ignored) {}
    }

    public static Integer profilerRegisterComponent(String name) {
        if (name == null || name.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerRegisterComponent(name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Boolean profilerRegisterThread(String componentName, long threadId) {
        if (componentName == null || componentName.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerRegisterThread(componentName, threadId);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Boolean profilerUnregisterThread(String componentName, long threadId) {
        if (componentName == null || componentName.isEmpty()) return null;
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerUnregisterThread(componentName, threadId);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void profilerTrackAlloc(String componentName, long bytes) {
        if (componentName == null || componentName.isEmpty()) return;
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.profilerTrackAlloc(componentName, bytes);
        } catch (Throwable ignored) {}
    }

    public static void profilerTrackFree(String componentName, long bytes) {
        if (componentName == null || componentName.isEmpty()) return;
        NativeLibrary lib = library();
        if (lib == null) return;
        try {
            lib.profilerTrackFree(componentName, bytes);
        } catch (Throwable ignored) {}
    }

    public static Boolean profilerSample() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerSample();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Integer profilerComponentCount() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerComponentCount();
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] profilerGetComponentSnapshot(int index) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerGetComponentSnapshot(index);
        } catch (Throwable t) {
            return null;
        }
    }

    public static byte[] profilerGetSummary() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerGetSummary();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String profilerPrintReport() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerPrintReport();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String profilerStatusLine() {
        NativeLibrary lib = library();
        if (lib == null) return null;
        try {
            return lib.profilerStatusLine();
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE IMAGE SCALING - SIMD-accelerated resize
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native image scaling is available.
     */
    public static boolean imageScaleReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasImageScaleSupport();
    }

    /**
     * Scale image using native SIMD-accelerated resize.
     * @param src Source BufferedImage
     * @param dstW Destination width
     * @param dstH Destination height
     * @param quality 0=fast, 1=balanced, 2=best
     * @return Scaled BufferedImage or null if native unavailable
     */
    public static java.awt.image.BufferedImage imageScale(java.awt.image.BufferedImage src, 
                                                           int dstW, int dstH, int quality) {
        NativeLibrary lib = library();
        if (lib == null || src == null || !lib.hasImageScaleSupport()) return null;
        
        try {
            int srcW = src.getWidth();
            int srcH = src.getHeight();
            
            // Extract pixels
            int[] srcPixels = new int[srcW * srcH];
            src.getRGB(0, 0, srcW, srcH, srcPixels, 0, srcW);
            
            // Native scale
            int[] dstPixels = lib.imageScale(srcPixels, srcW, srcH, dstW, dstH, quality);
            if (dstPixels == null) return null;
            
            // Create result image
            java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(
                dstW, dstH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            dst.setRGB(0, 0, dstW, dstH, dstPixels, 0, dstW);
            return dst;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Apply Gaussian blur to image.
     * @param img Image to blur (modified in place)
     * @param radius Blur radius
     * @return true if successful
     */
    public static boolean imageBlur(java.awt.image.BufferedImage img, int radius) {
        NativeLibrary lib = library();
        if (lib == null || img == null) return false;
        
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);
            
            if (!lib.imageBlur(pixels, w, h, radius)) return false;
            
            img.setRGB(0, 0, w, h, pixels, 0, w);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Tint image with a color.
     * @param img Image to tint (modified in place)
     * @param tintColor ARGB tint color
     * @param intensity Tint intensity (0.0-1.0)
     * @return true if successful
     */
    public static boolean imageTint(java.awt.image.BufferedImage img, int tintColor, float intensity) {
        NativeLibrary lib = library();
        if (lib == null || img == null) return false;
        
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);
            
            if (!lib.imageTint(pixels, w, h, tintColor, intensity)) return false;
            
            img.setRGB(0, 0, w, h, pixels, 0, w);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE SPELL CHECK - Edit distance generation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native spell check functions are available.
     */
    public static boolean spellEditReady() {
        NativeLibrary lib = library();
        return lib != null && lib.hasSpellEditSupport();
    }

    /**
     * Generate edit-distance-1 candidates for a word.
     * @param word Input word
     * @return List of candidate words or null if native unavailable
     */
    public static java.util.List<String> spellEdit1Candidates(String word) {
        NativeLibrary lib = library();
        if (lib == null || word == null) return null;
        try {
            return lib.spellEdit1Candidates(word);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Compute Levenshtein distance using native code.
     * @return Distance or -1 if native unavailable
     */
    public static int nativeLevenshtein(String a, String b) {
        NativeLibrary lib = library();
        if (lib == null || a == null || b == null) return -1;
        try {
            return lib.levenshtein(a, b);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute Damerau-Levenshtein distance using native code.
     * @return Distance or -1 if native unavailable
     */
    public static int nativeDamerauLevenshtein(String a, String b) {
        NativeLibrary lib = library();
        if (lib == null || a == null || b == null) return -1;
        try {
            return lib.damerauLevenshtein(a, b);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTOSAVE MANAGER
    // ═══════════════════════════════════════════════════════════════════════════

    public static boolean hasAutosaveSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasAutosaveSupport();
    }

    public static boolean autosaveInit() {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveInit();
    }

    public static int autosaveCreateSession(String filePath, int debounceMs) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        return lib.autosaveCreateSession(filePath, debounceMs);
    }

    public static boolean autosaveDestroySession(int sessionId) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveDestroySession(sessionId);
    }

    public static boolean autosaveSetPath(int sessionId, String newPath) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveSetPath(sessionId, newPath);
    }

    public static boolean autosaveMarkDirty(int sessionId) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveMarkDirty(sessionId);
    }

    public static boolean autosaveMarkClean(int sessionId) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveMarkClean(sessionId);
    }

    public static boolean autosaveIsDirty(int sessionId) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveIsDirty(sessionId);
    }

    public static boolean autosaveShouldSave(int sessionId) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveShouldSave(sessionId);
    }

    public static long autosaveMsUntilSave(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        return lib.autosaveMsUntilSave(sessionId);
    }

    public static boolean autosaveHasRecovery(String filePath) {
        NativeLibrary lib = library();
        return lib != null && lib.autosaveHasRecovery(filePath);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMAGE ACCENT COLOR EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native accent color extraction is available.
     */
    public static boolean hasAccentExtractSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasAccentExtractSupport();
    }

    /**
     * Extract dominant accent color from ARGB pixel array using native C++ implementation.
     * Uses hue histogram analysis weighted by saturation² × brightness.
     * 
     * @param argbPixels ARGB pixel data (Java BufferedImage TYPE_INT_ARGB format)
     * @param width Image width
     * @param height Image height
     * @return Packed RGB color (0x00RRGGBB), or 0 if native unavailable/error
     */
    public static int imageExtractAccent(int[] argbPixels, int width, int height) {
        if (argbPixels == null || width <= 0 || height <= 0) return 0;
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.imageExtractAccent(argbPixels, width, height);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MATH UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute mean using native SIMD-accelerated math.
     * @return mean or NaN if native unavailable
     */
    public static double mathMean(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathMean(values);
    }

    /**
     * Compute standard deviation using native SIMD-accelerated math.
     * @return stddev or NaN if native unavailable
     */
    public static double mathStddev(double[] values) {
        if (values == null || values.length < 2) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathStddev(values);
    }

    /**
     * Compute variance using native SIMD-accelerated math.
     * @return variance or NaN if native unavailable
     */
    public static double mathVariance(double[] values) {
        if (values == null || values.length < 2) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathVariance(values);
    }

    /**
     * Compute min using native SIMD-accelerated math.
     * @return min or NaN if native unavailable
     */
    public static double mathMin(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathMin(values);
    }

    /**
     * Compute max using native SIMD-accelerated math.
     * @return max or NaN if native unavailable
     */
    public static double mathMax(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathMax(values);
    }

    /**
     * Compute sum using native SIMD-accelerated math.
     * @return sum or NaN if native unavailable
     */
    public static double mathSum(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        NativeLibrary lib = library();
        if (lib == null) return Double.NaN;
        return lib.mathSum(values);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LZ4 COMPRESSION API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get maximum compressed size bound for LZ4.
     */
    public static int lz4CompressBound(int srcSize) {
        NativeLibrary lib = library();
        if (lib == null) return srcSize + srcSize / 255 + 16;
        return lib.lz4CompressBound(srcSize);
    }

    /**
     * Compress data using native LZ4-style algorithm.
     * @return compressed data or null on failure
     */
    public static byte[] lz4Compress(byte[] src) {
        NativeLibrary lib = library();
        if (lib == null || src == null) return null;
        return lib.lz4Compress(src);
    }

    /**
     * Decompress LZ4-compressed data using native code.
     * @param maxOutputSize maximum expected output size
     * @return decompressed data or null on failure
     */
    public static byte[] lz4Decompress(byte[] src, int maxOutputSize) {
        NativeLibrary lib = library();
        if (lib == null || src == null) return null;
        return lib.lz4Decompress(src, maxOutputSize);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VIEWPORT IMAGE CACHE API
    // ═══════════════════════════════════════════════════════════════════════════

    private static volatile boolean imgcacheInitialized;
    private static final Object IMGCACHE_LOCK = new Object();

    /**
     * Check if native image cache is available.
     */
    public static boolean hasImageCacheSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasImageCacheSupport();
    }

    /**
     * Initialize the viewport image cache with specified capacity.
     * Thread-safe; only initializes once.
     * @param maxEntries Maximum number of cached images (recommend 32-64)
     * @param maxMemoryMb Maximum memory in MB for the cache (recommend 64-128)
     * @return true if initialized successfully
     */
    public static boolean imgcacheInit(int maxEntries, int maxMemoryMb) {
        if (imgcacheInitialized) return true;
        synchronized (IMGCACHE_LOCK) {
            if (imgcacheInitialized) return true;
            NativeLibrary lib = library();
            if (lib == null) return false;
            imgcacheInitialized = lib.imgcacheInit(maxEntries, maxMemoryMb);
            return imgcacheInitialized;
        }
    }

    /**
     * Shutdown and free all cached images.
     */
    public static void imgcacheShutdown() {
        synchronized (IMGCACHE_LOCK) {
            if (!imgcacheInitialized) return;
            NativeLibrary lib = library();
            if (lib != null) lib.imgcacheShutdown();
            imgcacheInitialized = false;
        }
    }

    /**
     * Register an image in the cache with a unique ID.
     * @param imageId Unique identifier for this image
     * @param pixels ARGB pixel data
     * @param width Image width
     * @param height Image height
     * @return true if cached successfully
     */
    public static boolean imgcachePut(long imageId, int[] pixels, int width, int height) {
        if (!imgcacheInitialized) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.imgcachePut(imageId, pixels, width, height);
    }

    /**
     * Check if image is in cache.
     */
    public static boolean imgcacheContains(long imageId) {
        if (!imgcacheInitialized) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.imgcacheContains(imageId);
    }

    /**
     * Remove a specific image from the cache.
     */
    public static boolean imgcacheRemove(long imageId) {
        if (!imgcacheInitialized) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.imgcacheRemove(imageId);
    }

    /**
     * Clear all cached images.
     */
    public static void imgcacheClear() {
        if (!imgcacheInitialized) return;
        NativeLibrary lib = library();
        if (lib != null) lib.imgcacheClear();
    }

    /**
     * Get cache statistics.
     */
    public static NativeLibrary.ImageCacheStats imgcacheStats() {
        if (!imgcacheInitialized) return new NativeLibrary.ImageCacheStats(0, 0, 0, 0);
        NativeLibrary lib = library();
        if (lib == null) return new NativeLibrary.ImageCacheStats(0, 0, 0, 0);
        return lib.imgcacheStats();
    }

    /**
     * Perform viewport culling - returns which images are visible.
     * @param imageIds Array of image IDs to check
     * @param yPositions Y position of each image in document coordinates
     * @param heights Height of each image
     * @param viewportY Top of viewport in document coordinates
     * @param viewportHeight Height of viewport
     * @param outVisible Output array: 1 if visible, 0 if not (must be preallocated)
     * @return Number of visible images
     */
    public static int imgcacheCullViewport(long[] imageIds, int[] yPositions, int[] heights,
                                           int viewportY, int viewportHeight, int[] outVisible) {
        if (!imgcacheInitialized) return 0;
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.imgcacheCullViewport(imageIds, yPositions, heights, viewportY, viewportHeight, outVisible);
    }

    /**
     * Pre-scale an image and cache the result.
     * @param imageId ID for the scaled result
     * @param srcPixels Source ARGB pixels
     * @param srcWidth Source width
     * @param srcHeight Source height
     * @param targetWidth Desired width
     * @param quality 0=fast, 1=balanced, 2=best
     * @return true if scaled and cached successfully
     */
    public static boolean imgcachePrescale(long imageId, int[] srcPixels, int srcWidth, int srcHeight,
                                           int targetWidth, int quality) {
        if (!imgcacheInitialized) return false;
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.imgcachePrescale(imageId, srcPixels, srcWidth, srcHeight, targetWidth, quality);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HOTKEY MANAGER API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native hotkey support is available.
     */
    public static boolean hasHotkeySupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasHotkeySupport();
    }

    /**
     * Get the current platform identifier.
     * @return NativeLibrary.PLATFORM_* constant
     */
    public static int hotkeyGetPlatform() {
        NativeLibrary lib = library();
        if (lib == null) return NativeLibrary.PLATFORM_UNKNOWN;
        return lib.hotkeyGetPlatform();
    }

    /**
     * Get the platform-appropriate primary modifier.
     * Returns MOD_META on macOS, MOD_CTRL on Windows/Linux.
     */
    public static int hotkeyGetPrimaryModifier() {
        NativeLibrary lib = library();
        if (lib == null) {
            // Java fallback
            String os = System.getProperty("os.name", "").toLowerCase();
            return os.contains("mac") ? NativeLibrary.MOD_META : NativeLibrary.MOD_CTRL;
        }
        return lib.hotkeyGetPrimaryModifier();
    }

    /**
     * Check if a key event matches a text formatting hotkey.
     * @param keyCode The key code (ASCII for A-Z)
     * @param modifiers Combination of NativeLibrary.MOD_* flags
     * @return NativeLibrary.ACTION_* code, or ACTION_NONE if no match
     */
    public static int hotkeyCheck(int keyCode, int modifiers) {
        NativeLibrary lib = library();
            if (lib == null || !lib.hasHotkeySupport()) {
            return NativeLibrary.ACTION_NONE;
        }
        return lib.hotkeyCheck(keyCode, modifiers);
    }

    /**
     * Convert AWT KeyEvent modifiers to native modifier flags.
     * @param awtModifiers From KeyEvent.getModifiersEx()
     * @return Native modifier flags
     */
    public static int convertAwtModifiers(int awtModifiers) {
        int mods = NativeLibrary.MOD_NONE;
        if ((awtModifiers & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) {
            mods |= NativeLibrary.MOD_SHIFT;
        }
        if ((awtModifiers & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
            mods |= NativeLibrary.MOD_CTRL;
        }
        if ((awtModifiers & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) {
            mods |= NativeLibrary.MOD_ALT;
        }
        if ((awtModifiers & java.awt.event.InputEvent.META_DOWN_MASK) != 0) {
            mods |= NativeLibrary.MOD_META;
        }
        return mods;
    }

    /**
     * Check a KeyEvent for text formatting hotkeys.
     * Convenience method that handles modifier conversion.
     * @param e The KeyEvent
     * @return NativeLibrary.ACTION_* code, or ACTION_NONE if no match
     */
    public static int hotkeyCheckEvent(java.awt.event.KeyEvent e) {
        int keyCode = e.getKeyCode();
        // Convert VK_* to ASCII for letter keys
        if (keyCode >= java.awt.event.KeyEvent.VK_A && keyCode <= java.awt.event.KeyEvent.VK_Z) {
            keyCode = 'A' + (keyCode - java.awt.event.KeyEvent.VK_A);
        }
        int modifiers = convertAwtModifiers(e.getModifiersEx());
        return hotkeyCheck(keyCode, modifiers);
    }

    /**
     * Get a human-readable string for a hotkey (e.g., "⌘B" or "Ctrl+B").
     */
    public static String hotkeyGetDisplayString(int action) {
        NativeLibrary lib = library();
        if (lib == null) {
            // Java fallback
            String mod = hotkeyGetPrimaryModifier() == NativeLibrary.MOD_META ? "⌘" : "Ctrl+";
            return switch (action) {
                case NativeLibrary.ACTION_BOLD -> mod + "B";
                case NativeLibrary.ACTION_ITALIC -> mod + "I";
                case NativeLibrary.ACTION_UNDERLINE -> mod + "U";
                case NativeLibrary.ACTION_STRIKETHROUGH -> mod + "Shift+" + "S";
                default -> null;
            };
        }
        return lib.hotkeyGetDisplayString(action);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OFFSCREEN BUFFER API - Native double-buffering for smooth scrolling
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native buffer support is available.
     */
    public static boolean hasBufferSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasBufferSupport();
    }

    /**
     * Create a native offscreen buffer.
     * @param width Buffer width in pixels
     * @param height Buffer height in pixels
     * @return Buffer handle, or 0 on failure
     */
    public static long bufferCreate(int width, int height) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.bufferCreate(width, height);
    }

    /**
     * Resize an existing buffer.
     */
    public static boolean bufferResize(long handle, int width, int height) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.bufferResize(handle, width, height);
    }

    /**
     * Destroy a buffer and free its memory.
     */
    public static void bufferDestroy(long handle) {
        NativeLibrary lib = library();
        if (lib != null) lib.bufferDestroy(handle);
    }

    /**
     * Clear the buffer with a solid color.
     * @param handle Buffer handle
     * @param argb Color in 0xAARRGGBB format
     */
    public static void bufferClear(long handle, int argb) {
        NativeLibrary lib = library();
        if (lib != null) lib.bufferClear(handle, argb);
    }

    /**
     * Copy pixels into the buffer.
     */
    public static boolean bufferWrite(long handle, int[] pixels, int srcWidth, int srcHeight, int dstX, int dstY) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.bufferWrite(handle, pixels, srcWidth, srcHeight, dstX, dstY);
    }

    /**
     * Read pixels from buffer.
     */
    public static boolean bufferRead(long handle, int[] outPixels, int srcX, int srcY, int width, int height) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.bufferRead(handle, outPixels, srcX, srcY, width, height);
    }

    /**
     * Scroll buffer contents, filling exposed areas.
     * @param handle Buffer handle
     * @param dx Horizontal scroll (positive = content moves left, view moves right)
     * @param dy Vertical scroll (positive = content moves up, view moves down)
     * @param fillArgb Color for exposed areas
     */
    public static void bufferScroll(long handle, int dx, int dy, int fillArgb) {
        NativeLibrary lib = library();
        if (lib != null) lib.bufferScroll(handle, dx, dy, fillArgb);
    }

    /**
     * Alpha-blend pixels onto buffer.
     */
    public static boolean bufferComposite(long handle, int[] pixels, int srcWidth, int srcHeight, int dstX, int dstY) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.bufferComposite(handle, pixels, srcWidth, srcHeight, dstX, dstY);
    }

    /**
     * Get buffer dimensions.
     * @return [width, height] or null if invalid handle
     */
    public static int[] bufferGetSize(long handle) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.bufferGetSize(handle);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNDO/REDO MANAGER API - High-performance text editing history
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native undo/redo support is available.
     */
    public static boolean hasUndoSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasUndoSupport();
    }

    /**
     * Initialize the undo/redo system.
     */
    public static boolean undoInit() {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoInit();
    }

    /**
     * Shutdown and free all undo/redo resources.
     */
    public static void undoShutdown() {
        NativeLibrary lib = library();
        if (lib != null) lib.undoShutdown();
    }

    /**
     * Create a new undo session for an editor instance.
     * @param historyLimit Maximum undo steps (0 = default 1000)
     * @return Session ID (>0) or -1 on failure
     */
    public static int undoCreateSession(int historyLimit) {
        NativeLibrary lib = library();
        if (lib == null) return -1;
        return lib.undoCreateSession(historyLimit);
    }

    /**
     * Destroy an undo session.
     */
    public static boolean undoDestroySession(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoDestroySession(sessionId);
    }

    /**
     * Clear all undo/redo history for a session.
     */
    public static boolean undoClear(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoClear(sessionId);
    }

    /**
     * Push an insert operation onto the undo stack.
     */
    public static boolean undoPushInsert(int sessionId, int offset, String text) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoPushInsert(sessionId, offset, text);
    }

    /**
     * Push a delete operation onto the undo stack.
     */
    public static boolean undoPushDelete(int sessionId, int offset, String deletedText) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoPushDelete(sessionId, offset, deletedText);
    }

    /**
     * Push a replace operation onto the undo stack.
     */
    public static boolean undoPushReplace(int sessionId, int offset, String oldText, String newText) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoPushReplace(sessionId, offset, oldText, newText);
    }

    /**
     * Push a style change operation onto the undo stack.
     */
    public static boolean undoPushStyle(int sessionId, int offset, int length, int styleFlags) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoPushStyle(sessionId, offset, length, styleFlags);
    }

    /**
     * Begin a compound edit (groups multiple edits as one undo step).
     */
    public static boolean undoBeginCompound(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoBeginCompound(sessionId);
    }

    /**
     * End a compound edit.
     */
    public static boolean undoEndCompound(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoEndCompound(sessionId);
    }

    /**
     * Check if undo is available.
     */
    public static boolean undoCanUndo(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoCanUndo(sessionId);
    }

    /**
     * Check if redo is available.
     */
    public static boolean undoCanRedo(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoCanRedo(sessionId);
    }

    /**
     * Perform undo and get the edit details.
     * @return UndoResult with type, offset, length, text - or null if no undo available
     */
    public static NativeLibrary.UndoResult undoUndo(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.undoUndo(sessionId);
    }

    /**
     * Perform redo and get the edit details.
     * @return UndoResult with type, offset, length, text - or null if no redo available
     */
    public static NativeLibrary.UndoResult undoRedo(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.undoRedo(sessionId);
    }

    /**
     * Mark the current state as a save point for dirty detection.
     */
    public static boolean undoMarkSavePoint(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoMarkSavePoint(sessionId);
    }

    /**
     * Check if current state matches the save point.
     */
    public static boolean undoIsAtSavePoint(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoIsAtSavePoint(sessionId);
    }

    /**
     * Check if document has been modified since last save.
     */
    public static boolean undoIsDirty(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoIsDirty(sessionId);
    }

    /**
     * Get the number of available undo steps.
     */
    public static int undoGetUndoCount(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.undoGetUndoCount(sessionId);
    }

    /**
     * Get the number of available redo steps.
     */
    public static int undoGetRedoCount(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.undoGetRedoCount(sessionId);
    }

    /**
     * Set the history limit for a session.
     */
    public static boolean undoSetHistoryLimit(int sessionId, int limit) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.undoSetHistoryLimit(sessionId, limit);
    }

    /**
     * Get statistics for an undo session.
     * @return UndoStats with memory, undoCount, redoCount, savePoint, changeIndex
     */
    public static NativeLibrary.UndoStats undoGetStats(int sessionId) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.undoGetStats(sessionId);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LINK DETECTOR - Fast URL detection
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if native link detection is available.
     */
    public static boolean hasLinkSupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasLinkSupport();
    }
    
    /**
     * Check if text contains any URLs.
     */
    public static boolean linkContains(String text) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.linkContains(text);
    }
    
    /**
     * Count URLs in text.
     */
    public static int linkCount(String text) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.linkCount(text);
    }
    
    /**
     * Find all link ranges in text.
     * @return Array of [start, end] pairs
     */
    public static int[][] linkFindRanges(String text, int maxRanges) {
        NativeLibrary lib = library();
        if (lib == null) return new int[0][];
        return lib.linkFindRanges(text, maxRanges);
    }
    
    /**
     * Find all link ranges in text (default max 100).
     */
    public static int[][] linkFindRanges(String text) {
        return linkFindRanges(text, 100);
    }
    
    /**
     * Extract first URL from text.
     */
    public static String linkExtractFirst(String text) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.linkExtractFirst(text);
    }
    
    /**
     * Normalize a URL (add https:// if starts with www.).
     */
    public static String linkNormalize(String url) {
        NativeLibrary lib = library();
        if (lib == null) return url;
        return lib.linkNormalize(url);
    }
    
    /**
     * Validate if a string is a valid URL.
     */
    public static boolean linkIsValid(String url) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.linkIsValid(url);
    }
    
    /**
     * Get link at specific position in text.
     * @return int[2] with [start, end] or null if no link at position
     */
    public static int[] linkAtPosition(String text, int position) {
        NativeLibrary lib = library();
        if (lib == null) return null;
        return lib.linkAtPosition(text, position);
    }
    
    /**
     * Extract all URLs from text as a list of strings.
     */
    public static java.util.List<String> linkExtractAll(String text) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return urls;
        
        int[][] ranges = linkFindRanges(text);
        for (int[] range : ranges) {
            if (range.length == 2 && range[0] >= 0 && range[1] <= text.length()) {
                String url = text.substring(range[0], range[1]);
                urls.add(linkNormalize(url));
            }
        }
        return urls;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HASKELL POETRY - Functional poetry analysis via Haskell
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if Haskell poetry analysis is available.
     */
    public static boolean hasHaskellPoetrySupport() {
        NativeLibrary lib = library();
        return lib != null && lib.hasHaskellPoetrySupport();
    }
    
    /**
     * Analyze meter and return dominant foot type.
     * 0=Iamb, 1=Trochee, 2=Spondee, 3=Pyrrhic, 4=Anapest, 5=Dactyl, 6=Amphibrach
     */
    public static int hsAnalyzeMeter(String text) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsAnalyzeMeter(text);
    }
    
    /**
     * Get foot type name from code.
     */
    public static String footTypeName(int footType) {
        return switch (footType) {
            case 0 -> "Iamb";
            case 1 -> "Trochee";
            case 2 -> "Spondee";
            case 3 -> "Pyrrhic";
            case 4 -> "Anapest";
            case 5 -> "Dactyl";
            case 6 -> "Amphibrach";
            default -> "Unknown";
        };
    }
    
    /**
     * Get rhyme scheme for text.
     */
    public static String hsAnalyzeRhymeScheme(String text) {
        NativeLibrary lib = library();
        if (lib == null) return "";
        return lib.hsAnalyzeRhymeScheme(text);
    }
    
    /**
     * Count syllables in a word using Haskell.
     */
    public static int hsCountSyllables(String word) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsCountSyllables(word);
    }
    
    /**
     * Analyze sound devices and return count.
     */
    public static int hsAnalyzeSoundDevices(String text) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsAnalyzeSoundDevices(text);
    }
    
    /**
     * Get meter name (e.g., "Iambic Pentameter").
     */
    public static String hsGetMeterName(String text) {
        NativeLibrary lib = library();
        if (lib == null) return "";
        return lib.hsGetMeterName(text);
    }
    
    /**
     * Get meter regularity as percentage (0-100).
     */
    public static int hsGetMeterRegularity(String text) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsGetMeterRegularity(text);
    }
    
    /**
     * Check if two words rhyme.
     */
    public static boolean hsCheckRhyme(String word1, String word2) {
        NativeLibrary lib = library();
        if (lib == null) return false;
        return lib.hsCheckRhyme(word1, word2);
    }
    
    /**
     * Get vocabulary stats as comma-separated string.
     * Format: "total,unique,polysyl,hapax,avglen*100"
     */
    public static String hsGetVocabStats(String text) {
        NativeLibrary lib = library();
        if (lib == null) return "";
        return lib.hsGetVocabStats(text);
    }
    
    /**
     * Get type-token ratio (vocabulary richness) as percentage (0-100).
     */
    public static int hsTypeTokenRatio(String text) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsTypeTokenRatio(text);
    }
    
    /**
     * Get rhyme key for a word using Haskell.
     */
    public static String hsGetRhymeKey(String word) {
        NativeLibrary lib = library();
        if (lib == null) return "";
        return lib.hsGetRhymeKey(word);
    }
    
    /**
     * Estimate stress pattern as packed bits.
     * LSB = first syllable, 1 = stressed.
     */
    public static int hsEstimateStress(String word) {
        NativeLibrary lib = library();
        if (lib == null) return 0;
        return lib.hsEstimateStress(word);
    }
    
    /**
     * Unpack stress pattern bits into array.
     * @param packed The packed stress bits
     * @param syllableCount Number of syllables
     * @return Array of stress values (1=stressed, 0=unstressed)
     */
    public static int[] unpackStressPattern(int packed, int syllableCount) {
        int[] pattern = new int[syllableCount];
        for (int i = 0; i < syllableCount; i++) {
            pattern[i] = (packed >> i) & 1;
        }
        return pattern;
    }
}
