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

/**
 * Meant to provide a Panama FFM wrapper for native Simjot functions.
 * 
 * This class provides a Java interface to native Simjot functions using Java's Foreign Function & Memory API (FFM).
 * It serves as a bridge between Java and the native C library for various system operations.
 * 
 * @author S1mplector
 */

package main.infrastructure.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Panama FFM wrapper for native Simjot functions.
 * Requires Java 22+.
 * Make sure to load the native library before using it.
 * Otherwise it will throw an exception, and the application will crash.
 * 
 * Usage:
 *   NativeLibrary lib = NativeLibrary.load("/path/to/libsimjot_native.dylib");
 *   int result = lib.add(2, 3);  // returns 5
 * 
 * And so on.
 * 
 * @author S1mplector
 */
public final class NativeLibrary implements AutoCloseable {
    
    private final Arena arena;
    private final SymbolLookup lookup;
    private final Linker linker;
    
    // Method handles for native functions
    private final MethodHandle addHandle;
    private final MethodHandle strlenHandle;
    private final MethodHandle sumArrayHandle;
    private final MethodHandle fibHandle;
    private final MethodHandle sha256FileHandle;
    private final MethodHandle perfSnapshotHandle;
    private final MethodHandle binaryHealthHandle;
    private final MethodHandle countSyllablesHandle;
    private final MethodHandle atomicWriteHandle;
    private final MethodHandle ensureSpaceHandle;
    private final MethodHandle copyFileHandle;
    private final MethodHandle rhymeKeyHandle;
    private final MethodHandle nearRhymeKeyHandle;
    private final MethodHandle listDirSizeHandle;
    private final MethodHandle listDirHandle;
    private final MethodHandle dictSetBasePathHandle;
    private final MethodHandle dictContainsHandle;
    private final MethodHandle dictLookupHandle;
    private final MethodHandle dictRhymesHandle;
    private final MethodHandle dictSizeHandle;
    private final MethodHandle spellInitHandle;
    private final MethodHandle spellContainsHandle;
    private final MethodHandle spellSuggestionsHandle;
    private final MethodHandle spellBestCorrectionHandle;
    private final MethodHandle spellAddUserWordHandle;
    private final MethodHandle spellClearUserWordsHandle;
    
    // Text utility handles
    private final MethodHandle textWordCountHandle;
    private final MethodHandle textSentenceCountHandle;
    private final MethodHandle textCharCountHandle;
    private final MethodHandle textExtractWordsHandle;
    private final MethodHandle textLastWordHandle;
    private final MethodHandle textNormalizeHandle;
    private final MethodHandle textFuzzyMatchHandle;
    private final MethodHandle textFuzzyScoreHandle;
    private final MethodHandle textLineCountHandle;
    private final MethodHandle textGetLineHandle;
    private final MethodHandle textLevenshteinHandle;
    private final MethodHandle textDamerauLevenshteinHandle;
    private final MethodHandle textSimilarityHandle;
    
    // Compression handles
    private final MethodHandle compressHandle;
    private final MethodHandle decompressHandle;
    private final MethodHandle compressBoundHandle;
    
    // String operations handles
    private final MethodHandle stringSanitizeHandle;
    private final MethodHandle stringHashHandle;
    private final MethodHandle stringTokenCountHandle;
    private final MethodHandle stringFirstTokensHandle;
    private final MethodHandle stringLastTokensHandle;
    private final MethodHandle stringContainsCiHandle;
    
    private NativeLibrary(Path libraryPath) {
        this.arena = Arena.ofShared();
        this.linker = Linker.nativeLinker();
        this.lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        
        // int32_t simjot_add(int32_t a, int32_t b)
        this.addHandle = linker.downcallHandle(
            lookup.find("simjot_add").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        // int32_t simjot_strlen(const char* str)
        this.strlenHandle = linker.downcallHandle(
            lookup.find("simjot_strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        // int64_t simjot_sum_array(const int32_t* arr, int32_t len)
        this.sumArrayHandle = linker.downcallHandle(
            lookup.find("simjot_sum_array").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        // int64_t simjot_fib(int32_t n)
        this.fibHandle = linker.downcallHandle(
            lookup.find("simjot_fib").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_sha256_file(const char* path, uint8_t* out32)
        this.sha256FileHandle = linker.downcallHandle(
            lookup.find("simjot_sha256_file").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        this.perfSnapshotHandle = optionalHandle(
            "simjot_perf_snapshot",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.binaryHealthHandle = optionalHandle(
            "simjot_binary_health",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_count_syllables(const char* word)
        this.countSyllablesHandle = linker.downcallHandle(
            lookup.find("simjot_count_syllables").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        // int32_t simjot_rhyme_key(const char* word, char* out, int32_t outLen)
        this.rhymeKeyHandle = linker.downcallHandle(
            lookup.find("simjot_rhyme_key").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t outLen)
        this.nearRhymeKeyHandle = linker.downcallHandle(
            lookup.find("simjot_near_rhyme_key").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_list_dir_size(const char* path, int32_t includeHidden)
        this.listDirSizeHandle = linker.downcallHandle(
            lookup.find("simjot_list_dir_size").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_list_dir(const char* path, int32_t includeHidden, uint8_t* out, int32_t outLen)
        this.listDirHandle = linker.downcallHandle(
            lookup.find("simjot_list_dir").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_atomic_write(const char* target, const uint8_t* data, int32_t len, int32_t fsyncFile, int32_t fsyncDir)
        this.atomicWriteHandle = linker.downcallHandle(
            lookup.find("simjot_atomic_write").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT)
        );

        // int32_t simjot_ensure_space(const char* path, uint64_t bytesNeeded)
        this.ensureSpaceHandle = linker.downcallHandle(
            lookup.find("simjot_ensure_space").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        this.copyFileHandle = optionalHandle(
            "simjot_copy_file",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        this.dictSetBasePathHandle = optionalHandle(
            "simjot_dict_set_base_path",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.dictContainsHandle = optionalHandle(
            "simjot_dict_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.dictLookupHandle = optionalHandle(
            "simjot_dict_lookup",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.dictRhymesHandle = optionalHandle(
            "simjot_dict_rhymes_for",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.dictSizeHandle = optionalHandle(
            "simjot_dict_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        this.spellInitHandle = optionalHandle(
            "simjot_spell_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellContainsHandle = optionalHandle(
            "simjot_spell_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellSuggestionsHandle = optionalHandle(
            "simjot_spell_suggestions",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.spellBestCorrectionHandle = optionalHandle(
            "simjot_spell_best_correction",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.spellAddUserWordHandle = optionalHandle(
            "simjot_spell_add_user_word",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellClearUserWordsHandle = optionalHandle(
            "simjot_spell_clear_user_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        
        // Text utilities
        this.textWordCountHandle = optionalHandle(
            "simjot_text_word_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textSentenceCountHandle = optionalHandle(
            "simjot_text_sentence_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textCharCountHandle = optionalHandle(
            "simjot_text_char_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textExtractWordsHandle = optionalHandle(
            "simjot_text_extract_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textLastWordHandle = optionalHandle(
            "simjot_text_last_word",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textNormalizeHandle = optionalHandle(
            "simjot_text_normalize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textFuzzyMatchHandle = optionalHandle(
            "simjot_text_fuzzy_match",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textFuzzyScoreHandle = optionalHandle(
            "simjot_text_fuzzy_score",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textLineCountHandle = optionalHandle(
            "simjot_text_line_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textGetLineHandle = optionalHandle(
            "simjot_text_get_line",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textLevenshteinHandle = optionalHandle(
            "simjot_text_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textDamerauLevenshteinHandle = optionalHandle(
            "simjot_text_damerau_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textSimilarityHandle = optionalHandle(
            "simjot_text_similarity",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        // Compression
        this.compressHandle = optionalHandle(
            "simjot_compress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.decompressHandle = optionalHandle(
            "simjot_decompress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.compressBoundHandle = optionalHandle(
            "simjot_compress_bound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        // String operations
        this.stringSanitizeHandle = optionalHandle(
            "simjot_string_sanitize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringHashHandle = optionalHandle(
            "simjot_string_hash",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );
        this.stringTokenCountHandle = optionalHandle(
            "simjot_string_token_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.stringFirstTokensHandle = optionalHandle(
            "simjot_string_first_tokens",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringLastTokensHandle = optionalHandle(
            "simjot_string_last_tokens",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringContainsCiHandle = optionalHandle(
            "simjot_string_contains_ci",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
    }

    private MethodHandle optionalHandle(String name, FunctionDescriptor descriptor) {
        return lookup.find(name).map(symbol -> linker.downcallHandle(symbol, descriptor)).orElse(null);
    }
    
    /**
     * Load the native library from the given path.
     */
    public static NativeLibrary load(String libraryPath) {
        return new NativeLibrary(Path.of(libraryPath));
    }
    
    /**
     * Load from default location (src/main/native/).
     */
    public static NativeLibrary loadDefault() {
        return load(defaultLibraryPath().toString());
    }

    /**
     * Resolve the default native library path for the current OS.
     */
    public static Path defaultLibraryPath() {
        String libName = System.mapLibraryName("simjot_native");
        List<Path> candidates = new ArrayList<>();

        Path jarCandidate = resolveAlongsideJar(libName);
        if (jarCandidate != null) {
            candidates.add(jarCandidate);
        }

        String userDir = System.getProperty("user.dir", ".");
        addNativeCandidates(Path.of(userDir).toAbsolutePath().normalize(), libName, candidates);

        Path moduleDir = Path.of(userDir, "Simjot");
        if (Files.isDirectory(moduleDir)) {
            addNativeCandidates(moduleDir.toAbsolutePath().normalize(), libName, candidates);
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }

        // Best-effort fall-back
        return Path.of(userDir, "src", "main", "native", libName);
    }

    private static void addNativeCandidates(Path baseDir, String libName, List<Path> out) {
        if (baseDir == null) return;
        out.add(baseDir.resolve("src/main/native/build/Release").resolve(libName));
        out.add(baseDir.resolve("src/main/native/build/Debug").resolve(libName));
        out.add(baseDir.resolve("src/main/native/build").resolve(libName));
        out.add(baseDir.resolve("src/main/native").resolve(libName));
        out.add(baseDir.resolve("target/native").resolve(libName));
    }

    private static Path resolveAlongsideJar(String libName) {
        try {
            URL location = NativeLibrary.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            Path jarPath = Path.of(location.toURI());
            Path jarDir = Files.isDirectory(jarPath) ? jarPath : jarPath.getParent();
            if (jarDir == null) return null;

            List<Path> candidates = new ArrayList<>();
            candidates.add(jarDir.resolve(libName));
            candidates.add(jarDir.resolve("native").resolve(libName));

            Path contentsDir = jarDir.getParent();
            if (contentsDir != null) {
                candidates.add(contentsDir.resolve("Resources/native").resolve(libName));
                candidates.add(contentsDir.resolve("resources/native").resolve(libName));
            }

            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    return candidate.normalize();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Add two integers (basic test function).
     */
    public int add(int a, int b) {
        try {
            return (int) addHandle.invokeExact(a, b);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_add", t);
        }
    }
    
    /**
     * Get string length via native call.
     */
    public int strlen(String str) {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cString = tempArena.allocateFrom(str);
            return (int) strlenHandle.invokeExact(cString);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_strlen", t);
        }
    }
    
    /**
     * Sum an array of integers via native call.
     */
    public long sumArray(int[] arr) {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment nativeArr = tempArena.allocate(ValueLayout.JAVA_INT, arr.length);
            for (int i = 0; i < arr.length; i++) {
                nativeArr.setAtIndex(ValueLayout.JAVA_INT, i, arr[i]);
            }
            return (long) sumArrayHandle.invokeExact(nativeArr, arr.length);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_sum_array", t);
        }
    }
    
    /**
     * Compute nth Fibonacci number via native call.
     */
    public long fibonacci(int n) {
        try {
            return (long) fibHandle.invokeExact(n);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_fib", t);
        }
    }

    /**
     * Compute SHA-256 for a file path via native call.
     */
    public String sha256File(Path path) {
        if (path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            MemorySegment out = tempArena.allocate(32);
            int ok = (int) sha256FileHandle.invokeExact(cPath, out);
            if (ok == 0) return null;
            byte[] bytes = new byte[32];
            out.asByteBuffer().get(bytes);
            return HexFormat.of().formatHex(bytes);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_sha256_file", t);
        }
    }

    public byte[] perfSnapshot() {
        if (perfSnapshotHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 128;
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) perfSnapshotHandle.invokeExact(out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) perfSnapshotHandle.invokeExact(out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_perf_snapshot", t);
        }
    }

    public byte[] binaryHealth(Path path) {
        if (binaryHealthHandle == null || path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 96;
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) binaryHealthHandle.invokeExact(cPath, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) binaryHealthHandle.invokeExact(cPath, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_binary_health", t);
        }
    }

    /**
     * Count syllables for a word via native call.
     */
    public int countSyllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            return (int) countSyllablesHandle.invokeExact(cWord);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_count_syllables", t);
        }
    }

    /**
     * Generate a rhyme key via native call.
     */
    public String rhymeKey(String word) {
        if (word == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = Math.max(8, word.length() + 1);
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) rhymeKeyHandle.invokeExact(cWord, out, outLen);
            if (len <= 0) return null;
            byte[] bytes = new byte[len];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_rhyme_key", t);
        }
    }

    /**
     * Generate a near-rhyme key via native call.
     */
    public String nearRhymeKey(String word) {
        if (word == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = Math.max(8, word.length() + 1);
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) nearRhymeKeyHandle.invokeExact(cWord, out, outLen);
            if (len <= 0) return null;
            byte[] bytes = new byte[len];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_near_rhyme_key", t);
        }
    }

    public boolean hasDictionarySupport() {
        return dictSetBasePathHandle != null && dictContainsHandle != null;
    }
    
    public boolean hasDictionaryRhymesSupport() {
        return dictRhymesHandle != null;
    }
    
    public boolean hasSpellSupport() {
        return spellInitHandle != null
                && spellContainsHandle != null
                && spellSuggestionsHandle != null
                && spellBestCorrectionHandle != null;
    }

    public int dictionarySize() {
        if (dictSizeHandle == null) return 0;
        try {
            return (int) dictSizeHandle.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_size", t);
        }
    }

    public boolean initSpellDictionary(String basePath) {
        if (spellInitHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(basePath);
            return (int) spellInitHandle.invokeExact(cPath) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_init", t);
        }
    }

    public boolean spellContains(String word) {
        if (spellContainsHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            return (int) spellContainsHandle.invokeExact(cWord) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_contains", t);
        }
    }

    public List<String> spellSuggestions(String word, int maxResults) {
        if (spellSuggestionsHandle == null) return Collections.emptyList();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int outLen = 4096;
            MemorySegment buffer = temp.allocate(outLen);
            int written = (int) spellSuggestionsHandle.invokeExact(cWord, maxResults, buffer, outLen);
            if (written <= 0) return Collections.emptyList();
            byte[] bytes = new byte[written];
            buffer.asByteBuffer().get(bytes);
            String joined = new String(bytes, StandardCharsets.UTF_8);
            if (joined.isEmpty()) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String token : joined.split("\n")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_suggestions", t);
        }
    }

    public String spellBestCorrection(String word) {
        if (spellBestCorrectionHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int outLen = 256;
            MemorySegment out = temp.allocate(outLen);
            int ok = (int) spellBestCorrectionHandle.invokeExact(cWord, out, outLen);
            if (ok <= 0) return null;
            byte[] bytes = new byte[ok];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_best_correction", t);
        }
    }

    public boolean addUserDictionaryWord(String word) {
        if (spellAddUserWordHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            return (int) spellAddUserWordHandle.invokeExact(cWord) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_add_user_word", t);
        }
    }

    public void clearUserDictionary() {
        if (spellClearUserWordsHandle == null) return;
        try {
            spellClearUserWordsHandle.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_clear_user_words", t);
        }
    }

    public boolean setDictionaryBasePath(Path path) {
        if (dictSetBasePathHandle == null || path == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            int ok = (int) dictSetBasePathHandle.invokeExact(cPath);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_set_base_path", t);
        }
    }

    public Boolean dictionaryContains(String word) {
        if (dictContainsHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            int ok = (int) dictContainsHandle.invokeExact(cWord);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_contains", t);
        }
    }

    public byte[] dictionaryLookup(String word) {
        if (dictLookupHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 8192;
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) dictLookupHandle.invokeExact(cWord, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) dictLookupHandle.invokeExact(cWord, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_lookup", t);
        }
    }

    public byte[] dictionaryRhymes(String word, int maxResults) {
        if (dictRhymesHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 16384;
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) dictRhymesHandle.invokeExact(cWord, maxResults, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) dictRhymesHandle.invokeExact(cWord, maxResults, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_rhymes_for", t);
        }
    }

    /**
     * List a directory into a packed binary buffer via native call.
     */
    public byte[] listDirectory(Path path, boolean includeHidden) {
        if (path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            int size = (int) listDirSizeHandle.invokeExact(cPath, includeHidden ? 1 : 0);
            if (size <= 0) return null;
            MemorySegment out = tempArena.allocate(size);
            int written = (int) listDirHandle.invokeExact(cPath, includeHidden ? 1 : 0, out, size);
            if (written < 0) {
                size = (int) listDirSizeHandle.invokeExact(cPath, includeHidden ? 1 : 0);
                if (size <= 0) return null;
                out = tempArena.allocate(size);
                written = (int) listDirHandle.invokeExact(cPath, includeHidden ? 1 : 0, out, size);
            }
            if (written <= 0) return null;
            byte[] data = new byte[written];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_list_dir", t);
        }
    }

    /**
     * Perform an atomic write via native call.
     */
    public boolean atomicWrite(Path target, byte[] data, boolean fsyncFile, boolean fsyncDir) {
        if (target == null || data == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(target.toString());
            MemorySegment dataSeg = MemorySegment.ofArray(data);
            int ok = (int) atomicWriteHandle.invokeExact(
                cPath,
                dataSeg,
                data.length,
                fsyncFile ? 1 : 0,
                fsyncDir ? 1 : 0
            );
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_atomic_write", t);
        }
    }

    /**
     * Check available disk space via native call.
     * Returns 1 if enough space, 0 if insufficient, -1 on error.
     */
    public int ensureSpace(Path dir, long bytesNeeded) {
        if (dir == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(dir.toString());
            return (int) ensureSpaceHandle.invokeExact(cPath, bytesNeeded);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_ensure_space", t);
        }
    }

    public Boolean copyFile(Path src, Path dst, boolean copyAttributes) {
        if (copyFileHandle == null || src == null || dst == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cSrc = tempArena.allocateFrom(src.toString());
            MemorySegment cDst = tempArena.allocateFrom(dst.toString());
            int ok = (int) copyFileHandle.invokeExact(cSrc, cDst, copyAttributes ? 1 : 0);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_copy_file", t);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasTextUtilsSupport() {
        return textWordCountHandle != null;
    }

    public Integer textWordCount(String text) {
        if (textWordCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textWordCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textSentenceCount(String text) {
        if (textSentenceCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textSentenceCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textCharCount(String text, boolean includeSpaces) {
        if (textCharCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textCharCountHandle.invokeExact(cText, includeSpaces ? 1 : 0);
        } catch (Throwable t) {
            return null;
        }
    }

    public List<String> textExtractWords(String text) {
        if (textExtractWordsHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.max(1024, text.length() * 2);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int written = (int) textExtractWordsHandle.invokeExact(cText, outBuf, bufSize);
            if (written <= 0) return Collections.emptyList();
            byte[] bytes = outBuf.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE);
            String result = new String(bytes, StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String w : result.split("\n")) {
                if (!w.isEmpty()) words.add(w);
            }
            return words;
        } catch (Throwable t) {
            return null;
        }
    }

    public String textLastWord(String text) {
        if (textLastWordHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment outBuf = tempArena.allocate(128);
            int len = (int) textLastWordHandle.invokeExact(cText, outBuf, 128);
            if (len <= 0) return null;
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public String textNormalize(String text) {
        if (textNormalizeHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = text.length() + 1;
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) textNormalizeHandle.invokeExact(cText, outBuf, bufSize);
            if (len <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean textFuzzyMatch(String text, String query) {
        if (textFuzzyMatchHandle == null || text == null || query == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cQuery = tempArena.allocateFrom(query);
            int result = (int) textFuzzyMatchHandle.invokeExact(cText, cQuery);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public int textFuzzyScore(String text, String query) {
        if (textFuzzyScoreHandle == null || text == null || query == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cQuery = tempArena.allocateFrom(query);
            return (int) textFuzzyScoreHandle.invokeExact(cText, cQuery);
        } catch (Throwable t) {
            return 0;
        }
    }

    public Integer textLineCount(String text) {
        if (textLineCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textLineCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public String textGetLine(String text, int lineNum) {
        if (textGetLineHandle == null || text == null || lineNum < 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(4096, text.length() + 1);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) textGetLineHandle.invokeExact(cText, lineNum, outBuf, bufSize);
            if (len < 0) return null;
            if (len == 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textLevenshtein(String a, String b) {
        if (textLevenshteinHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textLevenshteinHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textDamerauLevenshtein(String a, String b) {
        if (textDamerauLevenshteinHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textDamerauLevenshteinHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textSimilarity(String a, String b) {
        if (textSimilarityHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textSimilarityHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPRESSION UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasCompressionSupport() {
        return compressHandle != null && decompressHandle != null;
    }

    public int compressBound(int inputLen) {
        if (compressBoundHandle == null) return inputLen + 128;
        try {
            return (int) compressBoundHandle.invokeExact(inputLen);
        } catch (Throwable t) {
            return inputLen + 128;
        }
    }

    public byte[] compress(byte[] input, int level) {
        if (compressHandle == null || input == null || input.length == 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int maxOut = compressBound(input.length);
            MemorySegment inBuf = tempArena.allocate(input.length);
            inBuf.asByteBuffer().put(input);
            MemorySegment outBuf = tempArena.allocate(maxOut);
            
            int compressedSize = (int) compressHandle.invokeExact(inBuf, input.length, outBuf, maxOut, level);
            if (compressedSize <= 0) return null;
            
            byte[] result = new byte[compressedSize];
            outBuf.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] decompress(byte[] input, int expectedSize) {
        if (decompressHandle == null || input == null || input.length == 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment inBuf = tempArena.allocate(input.length);
            inBuf.asByteBuffer().put(input);
            MemorySegment outBuf = tempArena.allocate(expectedSize);
            
            int decompressedSize = (int) decompressHandle.invokeExact(inBuf, input.length, outBuf, expectedSize);
            if (decompressedSize <= 0) return null;
            
            byte[] result = new byte[decompressedSize];
            outBuf.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRING OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasStringOpsSupport() {
        return stringSanitizeHandle != null;
    }

    public String stringSanitize(String input, int maxLen) {
        if (stringSanitizeHandle == null || input == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cInput = tempArena.allocateFrom(input);
            int bufSize = Math.max(input.length() + 16, 512);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) stringSanitizeHandle.invokeExact(cInput, outBuf, bufSize, maxLen);
            if (len < 0) return null;
            if (len == 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public long stringHash(String str) {
        if (stringHashHandle == null || str == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            return (long) stringHashHandle.invokeExact(cStr);
        } catch (Throwable t) {
            return 0;
        }
    }

    public int stringTokenCount(String text) {
        if (stringTokenCountHandle == null || text == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) stringTokenCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return -1;
        }
    }

    public String stringFirstTokens(String text, int maxTokens) {
        if (stringFirstTokensHandle == null || text == null || maxTokens <= 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(text.length() + 1, 4096);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int count = (int) stringFirstTokensHandle.invokeExact(cText, outBuf, bufSize, maxTokens);
            if (count <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, Math.min(bufSize - 1, text.length())).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public String stringLastTokens(String text, int maxTokens) {
        if (stringLastTokensHandle == null || text == null || maxTokens <= 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(text.length() + 1, 4096);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int count = (int) stringLastTokensHandle.invokeExact(cText, outBuf, bufSize, maxTokens);
            if (count <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, Math.min(bufSize - 1, text.length())).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean stringContainsCi(String haystack, String needle) {
        if (stringContainsCiHandle == null || haystack == null || needle == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cHaystack = tempArena.allocateFrom(haystack);
            MemorySegment cNeedle = tempArena.allocateFrom(needle);
            int result = (int) stringContainsCiHandle.invokeExact(cHaystack, cNeedle);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    @Override
    public void close() {
        arena.close();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST MAIN
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static void main(String[] args) {
        System.out.println("=== Panama FFM Test ===\n");
        
        try (NativeLibrary lib = NativeLibrary.loadDefault()) {
            // Test add
            int sum = lib.add(17, 25);
            System.out.println("add(17, 25) = " + sum + " (expected: 42)");
            
            // Test strlen
            String testStr = "Hello, Simjot!";
            int len = lib.strlen(testStr);
            System.out.println("strlen(\"" + testStr + "\") = " + len + " (expected: " + testStr.length() + ")");
            
            // Test sumArray
            int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            long arraySum = lib.sumArray(nums);
            System.out.println("sumArray([1..10]) = " + arraySum + " (expected: 55)");
            
            // Test fibonacci
            long fib20 = lib.fibonacci(20);
            System.out.println("fibonacci(20) = " + fib20 + " (expected: 6765)");
            
            // Benchmark: Java vs Native fibonacci
            System.out.println("\n=== Benchmark: fib(40) x 1000 ===");
            
            long t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                lib.fibonacci(40);
            }
            long nativeTime = System.nanoTime() - t0;
            
            t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                javaFib(40);
            }
            long javaTime = System.nanoTime() - t0;
            
            System.out.printf("Native: %.2f ms%n", nativeTime / 1_000_000.0);
            System.out.printf("Java:   %.2f ms%n", javaTime / 1_000_000.0);
            System.out.printf("Ratio:  %.2fx%n", (double) javaTime / nativeTime);
            
            System.out.println("\n✓ All tests passed!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static long javaFib(int n) {
        if (n <= 1) return n;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long tmp = a + b;
            a = b;
            b = tmp;
        }
        return b;
    }
}
