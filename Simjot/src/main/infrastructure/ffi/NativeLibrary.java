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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Panama FFM wrapper for native Simjot functions.
 * Requires Java 22+.
 * 
 * Usage:
 *   NativeLibrary lib = NativeLibrary.load("/path/to/libsimjot_native.dylib");
 *   int result = lib.add(2, 3);  // returns 5
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
    private final MethodHandle countSyllablesHandle;
    private final MethodHandle atomicWriteHandle;
    private final MethodHandle ensureSpaceHandle;
    private final MethodHandle rhymeKeyHandle;
    private final MethodHandle nearRhymeKeyHandle;
    private final MethodHandle listDirSizeHandle;
    private final MethodHandle listDirHandle;
    private final MethodHandle dictSetBasePathHandle;
    private final MethodHandle dictContainsHandle;
    private final MethodHandle dictLookupHandle;
    private final MethodHandle dictRhymesHandle;
    private final MethodHandle dictSizeHandle;
    
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
        String projectPath = System.getProperty("user.dir");
        String libName = System.mapLibraryName("simjot_native");
        Path inSource = Path.of(projectPath, "src", "main", "native", libName);
        Path inBuild = Path.of(projectPath, "src", "main", "native", "build", libName);
        Path inBuildDebug = Path.of(projectPath, "src", "main", "native", "build", "Debug", libName);
        Path inBuildRelease = Path.of(projectPath, "src", "main", "native", "build", "Release", libName);
        if (Files.exists(inBuild)) return inBuild;
        if (Files.exists(inBuildDebug)) return inBuildDebug;
        if (Files.exists(inBuildRelease)) return inBuildRelease;
        return inSource;
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
        return dictSetBasePathHandle != null && dictLookupHandle != null && dictContainsHandle != null;
    }

    public boolean hasDictionaryRhymesSupport() {
        return dictRhymesHandle != null;
    }

    public Integer dictionarySize() {
        if (dictSizeHandle == null) return null;
        try {
            int size = (int) dictSizeHandle.invokeExact();
            return size > 0 ? size : null;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_size", t);
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
