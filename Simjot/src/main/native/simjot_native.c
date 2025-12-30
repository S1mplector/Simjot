/**
 * Simple native library for Panama FFM testing.
 * Compile on macOS: clang -shared -o libsimjot_native.dylib simjot_native.c
 */

#include <stdint.h>
#include <string.h>

// Simple add function to test basic FFM call
int32_t simjot_add(int32_t a, int32_t b) {
    return a + b;
}

// String length (useful for text processing tests)
int32_t simjot_strlen(const char* str) {
    if (str == NULL) return 0;
    return (int32_t)strlen(str);
}

// Fast sum of int array (SIMD candidate for future)
int64_t simjot_sum_array(const int32_t* arr, int32_t len) {
    int64_t sum = 0;
    for (int32_t i = 0; i < len; i++) {
        sum += arr[i];
    }
    return sum;
}

// Fibonacci (compute-bound test)
int64_t simjot_fib(int32_t n) {
    if (n <= 1) return n;
    int64_t a = 0, b = 1;
    for (int32_t i = 2; i <= n; i++) {
        int64_t tmp = a + b;
        a = b;
        b = tmp;
    }
    return b;
}
