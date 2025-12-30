#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef _WIN32
#include <sys/statvfs.h>
#include <unistd.h>
#include <sys/stat.h>
#endif

static int failures = 0;

static void expect_int(const char* name, long long got, long long expected) {
    if (got != expected) {
        fprintf(stderr, "[FAIL] %s: got %lld, expected %lld\n", name, got, expected);
        failures++;
    } else {
        printf("[OK] %s\n", name);
    }
}

static void expect_str(const char* name, const char* got, const char* expected) {
    if (got == NULL || expected == NULL || strcmp(got, expected) != 0) {
        fprintf(stderr, "[FAIL] %s: got '%s', expected '%s'\n", name, got ? got : "(null)", expected ? expected : "(null)");
        failures++;
    } else {
        printf("[OK] %s\n", name);
    }
}

static int write_file(const char* path, const char* data) {
    FILE* f = fopen(path, "wb");
    if (!f) return 0;
    size_t len = strlen(data);
    size_t written = fwrite(data, 1, len, f);
    fclose(f);
    return written == len;
}

static void bytes_to_hex(const uint8_t* in, size_t len, char* out) {
    static const char* hex = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[i * 2] = hex[(in[i] >> 4) & 0xF];
        out[i * 2 + 1] = hex[in[i] & 0xF];
    }
    out[len * 2] = '\0';
}

static int list_contains(const uint8_t* data, int32_t len, const char* name, int expect_dir) {
    int32_t offset = 0;
    while (offset + 8 <= len) {
        uint32_t name_len = 0;
        memcpy(&name_len, data + offset, sizeof(uint32_t));
        offset += 4;
        int is_dir = data[offset++];
        int is_hidden = data[offset++];
        (void)is_hidden;
        offset += 2; // padding
        if (offset + (int32_t)name_len > len) return 0;
        if (name_len == strlen(name) && memcmp(data + offset, name, name_len) == 0) {
            return (is_dir ? 1 : 0) == (expect_dir ? 1 : 0);
        }
        offset += (int32_t)name_len;
    }
    return 0;
}

static int make_temp_path(char* out, size_t size) {
#ifdef _WIN32
    (void)size;
    return tmpnam(out) != NULL;
#else
    if (size < 1) return 0;
    snprintf(out, size, "/tmp/simjot_native_XXXXXX");
    int fd = mkstemp(out);
    if (fd < 0) return 0;
    close(fd);
    return 1;
#endif
}

static int make_temp_dir(char* out, size_t size) {
#ifdef _WIN32
    (void)size;
    return tmpnam(out) != NULL;
#else
    if (size < 1) return 0;
    snprintf(out, size, "/tmp/simjot_native_dir_XXXXXX");
    return mkdtemp(out) != NULL;
#endif
}

int main(void) {
    printf("=== Simjot Native CLI Tests ===\n");

    expect_int("simjot_add", simjot_add(17, 25), 42);
    expect_int("simjot_strlen", simjot_strlen("Hello"), 5);

    int32_t nums[] = {1,2,3,4,5,6,7,8,9,10};
    expect_int("simjot_sum_array", simjot_sum_array(nums, 10), 55);
    expect_int("simjot_fib", simjot_fib(20), 6765);

    expect_int("simjot_count_syllables(hello)", simjot_count_syllables("hello"), 2);
    expect_int("simjot_count_syllables(poetry)", simjot_count_syllables("poetry"), 3);
    expect_int("simjot_count_syllables(wanted)", simjot_count_syllables("wanted"), 2);
    expect_int("simjot_count_syllables(the)", simjot_count_syllables("the"), 1);
    expect_int("simjot_count_syllables(a)", simjot_count_syllables("a"), 1);

    char rhyme[32] = {0};
    if (simjot_rhyme_key("day", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_rhyme_key(day)", rhyme, "ay");
    } else {
        fprintf(stderr, "[FAIL] simjot_rhyme_key(day): call failed\n");
        failures++;
    }
    if (simjot_rhyme_key("hear", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_rhyme_key(hear)", rhyme, "ar");
    } else {
        fprintf(stderr, "[FAIL] simjot_rhyme_key(hear): call failed\n");
        failures++;
    }
    if (simjot_near_rhyme_key("thing", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_near_rhyme_key(thing)", rhyme, "ing");
    } else {
        fprintf(stderr, "[FAIL] simjot_near_rhyme_key(thing): call failed\n");
        failures++;
    }

    char tmp_path[L_tmpnam];
    if (make_temp_path(tmp_path, sizeof(tmp_path))) {
        if (write_file(tmp_path, "abc")) {
            uint8_t hash[32];
            if (simjot_sha256_file(tmp_path, hash)) {
                char hex[65];
                bytes_to_hex(hash, 32, hex);
                expect_str("simjot_sha256_file", hex, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            } else {
                fprintf(stderr, "[FAIL] simjot_sha256_file: call failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] write temp file for sha256\n");
            failures++;
        }
        remove(tmp_path);
    } else {
        fprintf(stderr, "[FAIL] tmpnam failed for sha256 test\n");
        failures++;
    }

#ifndef _WIN32
    char dir_path[64];
    if (make_temp_dir(dir_path, sizeof(dir_path))) {
        char file_path[96];
        char subdir_path[96];
        snprintf(file_path, sizeof(file_path), "%s/%s", dir_path, "alpha.txt");
        snprintf(subdir_path, sizeof(subdir_path), "%s/%s", dir_path, "subdir");
        if (!write_file(file_path, "x")) {
            fprintf(stderr, "[FAIL] write temp file for list dir test\n");
            failures++;
        }
        if (mkdir(subdir_path, 0700) != 0) {
            fprintf(stderr, "[FAIL] mkdir for list dir test\n");
            failures++;
        }
        int size = simjot_list_dir_size(dir_path, 1);
        if (size > 0) {
            uint8_t* data = (uint8_t*)malloc((size_t)size);
            if (data) {
                int written = simjot_list_dir(dir_path, 1, data, size);
                if (written > 0) {
                    if (!list_contains(data, written, "alpha.txt", 0)) {
                        fprintf(stderr, "[FAIL] simjot_list_dir missing alpha.txt\n");
                        failures++;
                    } else {
                        printf("[OK] simjot_list_dir file\n");
                    }
                    if (!list_contains(data, written, "subdir", 1)) {
                        fprintf(stderr, "[FAIL] simjot_list_dir missing subdir\n");
                        failures++;
                    } else {
                        printf("[OK] simjot_list_dir dir\n");
                    }
                } else {
                    fprintf(stderr, "[FAIL] simjot_list_dir: call failed\n");
                    failures++;
                }
                free(data);
            } else {
                fprintf(stderr, "[FAIL] simjot_list_dir: alloc failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] simjot_list_dir_size: %d\n", size);
            failures++;
        }
        remove(file_path);
        rmdir(subdir_path);
        rmdir(dir_path);
    } else {
        fprintf(stderr, "[FAIL] mkdtemp failed for list dir test\n");
        failures++;
    }
#else
    printf("[SKIP] simjot_list_dir (Windows stub)\n");
#endif

#ifndef _WIN32
    if (make_temp_path(tmp_path, sizeof(tmp_path))) {
        const char* payload = "native io";
        if (simjot_atomic_write(tmp_path, (const uint8_t*)payload, (int32_t)strlen(payload), 1, 1)) {
            FILE* f = fopen(tmp_path, "rb");
            if (f) {
                char buf[32] = {0};
                size_t n = fread(buf, 1, sizeof(buf) - 1, f);
                fclose(f);
                buf[n] = '\0';
                expect_str("simjot_atomic_write", buf, payload);
            } else {
                fprintf(stderr, "[FAIL] simjot_atomic_write: read back failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] simjot_atomic_write: call failed\n");
            failures++;
        }
        remove(tmp_path);
    } else {
        fprintf(stderr, "[FAIL] tmpnam failed for atomic write test\n");
        failures++;
    }

    int space_ok = simjot_ensure_space(".", 1);
    if (space_ok == 1) {
        printf("[OK] simjot_ensure_space small\n");
    } else {
        fprintf(stderr, "[FAIL] simjot_ensure_space small: %d\n", space_ok);
        failures++;
    }

    int space_fail = simjot_ensure_space(".", UINT64_MAX);
    if (space_fail == 0) {
        printf("[OK] simjot_ensure_space huge\n");
    } else {
        fprintf(stderr, "[FAIL] simjot_ensure_space huge: %d\n", space_fail);
        failures++;
    }
#else
    printf("[SKIP] simjot_atomic_write and simjot_ensure_space (Windows stub)\n");
#endif

    if (failures == 0) {
        printf("\nAll native tests passed.\n");
        return 0;
    }

    fprintf(stderr, "\nNative tests failed: %d\n", failures);
    return 1;
}
