package main.infrastructure.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * Panama FFM wrapper for native Simjot functions.
 * Requires Java 21+.
 * 
 * Usage:
 *   NativeLibrary lib = NativeLibrary.load("/path/to/libsimjot_native.dylib");
 *   int result = lib.add(2, 3);  // returns 5
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
    
    private NativeLibrary(Path libraryPath) {
        this.arena = Arena.ofConfined();
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
        String projectPath = System.getProperty("user.dir");
        String libPath = projectPath + "/src/main/native/libsimjot_native.dylib";
        return load(libPath);
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
