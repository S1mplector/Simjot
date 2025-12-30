/**
 * Simple native library for Panama FFM testing and core utilities.
 * Compile on macOS: clang -shared -O2 -fPIC -o libsimjot_native.dylib simjot_native.c
 */

#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>

#ifndef _WIN32
#include <unistd.h>
#include <fcntl.h>
#include <sys/statvfs.h>
#endif

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

// =============================================================================
// SHA-256 (file checksum)
// =============================================================================

typedef struct {
    uint8_t data[64];
    uint32_t datalen;
    uint64_t bitlen;
    uint32_t state[8];
} sha256_ctx;

static const uint32_t sha256_k[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

static uint32_t sha256_rotr(uint32_t a, uint32_t b) {
    return (a >> b) | (a << (32 - b));
}

static uint32_t sha256_ch(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (~x & z);
}

static uint32_t sha256_maj(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (x & z) ^ (y & z);
}

static uint32_t sha256_ep0(uint32_t x) {
    return sha256_rotr(x, 2) ^ sha256_rotr(x, 13) ^ sha256_rotr(x, 22);
}

static uint32_t sha256_ep1(uint32_t x) {
    return sha256_rotr(x, 6) ^ sha256_rotr(x, 11) ^ sha256_rotr(x, 25);
}

static uint32_t sha256_sig0(uint32_t x) {
    return sha256_rotr(x, 7) ^ sha256_rotr(x, 18) ^ (x >> 3);
}

static uint32_t sha256_sig1(uint32_t x) {
    return sha256_rotr(x, 17) ^ sha256_rotr(x, 19) ^ (x >> 10);
}

static void sha256_transform(sha256_ctx* ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, e, f, g, h;
    uint32_t m[64];

    for (uint32_t i = 0, j = 0; i < 16; i++, j += 4) {
        m[i] = (uint32_t)data[j] << 24 | (uint32_t)data[j + 1] << 16 |
               (uint32_t)data[j + 2] << 8 | (uint32_t)data[j + 3];
    }

    for (uint32_t i = 16; i < 64; i++) {
        m[i] = sha256_sig1(m[i - 2]) + m[i - 7] + sha256_sig0(m[i - 15]) + m[i - 16];
    }

    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];

    for (uint32_t i = 0; i < 64; i++) {
        uint32_t t1 = h + sha256_ep1(e) + sha256_ch(e, f, g) + sha256_k[i] + m[i];
        uint32_t t2 = sha256_ep0(a) + sha256_maj(a, b, c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }

    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

static void sha256_init(sha256_ctx* ctx) {
    ctx->datalen = 0;
    ctx->bitlen = 0;
    ctx->state[0] = 0x6a09e667;
    ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372;
    ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f;
    ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab;
    ctx->state[7] = 0x5be0cd19;
}

static void sha256_update(sha256_ctx* ctx, const uint8_t data[], size_t len) {
    for (size_t i = 0; i < len; i++) {
        ctx->data[ctx->datalen] = data[i];
        ctx->datalen++;
        if (ctx->datalen == 64) {
            sha256_transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

static void sha256_final(sha256_ctx* ctx, uint8_t hash[]) {
    uint32_t i = ctx->datalen;

    if (ctx->datalen < 56) {
        ctx->data[i++] = 0x80;
        while (i < 56) ctx->data[i++] = 0x00;
    } else {
        ctx->data[i++] = 0x80;
        while (i < 64) ctx->data[i++] = 0x00;
        sha256_transform(ctx, ctx->data);
        memset(ctx->data, 0, 56);
    }

    ctx->bitlen += ctx->datalen * 8;
    ctx->data[63] = (uint8_t)(ctx->bitlen);
    ctx->data[62] = (uint8_t)(ctx->bitlen >> 8);
    ctx->data[61] = (uint8_t)(ctx->bitlen >> 16);
    ctx->data[60] = (uint8_t)(ctx->bitlen >> 24);
    ctx->data[59] = (uint8_t)(ctx->bitlen >> 32);
    ctx->data[58] = (uint8_t)(ctx->bitlen >> 40);
    ctx->data[57] = (uint8_t)(ctx->bitlen >> 48);
    ctx->data[56] = (uint8_t)(ctx->bitlen >> 56);
    sha256_transform(ctx, ctx->data);

    for (i = 0; i < 4; i++) {
        hash[i]      = (uint8_t)(ctx->state[0] >> (24 - i * 8));
        hash[i + 4]  = (uint8_t)(ctx->state[1] >> (24 - i * 8));
        hash[i + 8]  = (uint8_t)(ctx->state[2] >> (24 - i * 8));
        hash[i + 12] = (uint8_t)(ctx->state[3] >> (24 - i * 8));
        hash[i + 16] = (uint8_t)(ctx->state[4] >> (24 - i * 8));
        hash[i + 20] = (uint8_t)(ctx->state[5] >> (24 - i * 8));
        hash[i + 24] = (uint8_t)(ctx->state[6] >> (24 - i * 8));
        hash[i + 28] = (uint8_t)(ctx->state[7] >> (24 - i * 8));
    }
}

int32_t simjot_sha256_file(const char* path, uint8_t* out32) {
    if (path == NULL || out32 == NULL) return 0;
    FILE* f = fopen(path, "rb");
    if (!f) return 0;

    sha256_ctx ctx;
    sha256_init(&ctx);

    uint8_t buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        sha256_update(&ctx, buf, n);
    }

    if (ferror(f)) {
        fclose(f);
        return 0;
    }

    sha256_final(&ctx, out32);
    fclose(f);
    return 1;
}

// =============================================================================
// Poetry analysis (syllable counting)
// =============================================================================

typedef struct {
    const char* word;
    int count;
} syllable_override;

static const syllable_override SYLLABLE_OVERRIDES[] = {
    {"every", 3}, {"evening", 3},
    {"different", 3}, {"interesting", 4},
    {"beautiful", 3}, {"favorite", 3},
    {"family", 3}, {"chocolate", 3},
    {"comfortable", 4}, {"vegetable", 4},
    {"camera", 3}, {"actually", 4},
    {"generally", 4}, {"naturally", 4},
    {"practically", 4}, {"literally", 4},
    {"probably", 3}, {"definitely", 4},
    {"especially", 5}, {"unfortunately", 5},
    {"immediately", 5}, {"particularly", 5},
    {"area", 3}, {"idea", 3},
    {"real", 1}, {"really", 2},
    {"being", 2}, {"doing", 2},
    {"going", 2}, {"saying", 2},
    {"having", 2}, {"making", 2},
    {"taking", 2}, {"coming", 2},
    {"getting", 2}, {"looking", 2},
    {"nothing", 2}, {"something", 2},
    {"everything", 4}, {"everyone", 3},
    {"someone", 2}, {"anyone", 3},
    {"ourselves", 2}, {"themselves", 2},
    {"fire", 1}, {"desire", 2},
    {"hour", 1}, {"flower", 2},
    {"power", 2}, {"tower", 2},
    {"poem", 2}, {"poet", 2},
    {"poetry", 3}, {"quiet", 2},
    {"science", 2}, {"patient", 2},
    {"ancient", 2}, {"ocean", 2},
    {"heaven", 2}, {"seven", 2},
    {"even", 2}, {"given", 2},
    {"driven", 2}, {"written", 2},
    {"rhythm", 2}, {"prism", 2},
    {"naive", 2}, {"cafe", 2}
};

static const char* SILENT_E_EXCEPTIONS[] = {
    "be", "he", "me", "we", "she", "the", "cafe", "forte", "finale", "recipe",
    "adobe", "coyote", "karate", "maybe", "sesame", "simile", "apostrophe"
};

static int is_vowel(char c) {
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

static int ends_with(const char* word, const char* suffix) {
    size_t wlen = strlen(word);
    size_t slen = strlen(suffix);
    if (slen > wlen) return 0;
    return strcmp(word + (wlen - slen), suffix) == 0;
}

static int contains(const char* word, const char* needle) {
    return strstr(word, needle) != NULL;
}

static int is_silent_e_exception(const char* word) {
    size_t count = sizeof(SILENT_E_EXCEPTIONS) / sizeof(SILENT_E_EXCEPTIONS[0]);
    for (size_t i = 0; i < count; i++) {
        if (strcmp(word, SILENT_E_EXCEPTIONS[i]) == 0) return 1;
    }
    return 0;
}

static int override_count(const char* word) {
    size_t count = sizeof(SYLLABLE_OVERRIDES) / sizeof(SYLLABLE_OVERRIDES[0]);
    for (size_t i = 0; i < count; i++) {
        if (strcmp(word, SYLLABLE_OVERRIDES[i].word) == 0) return SYLLABLE_OVERRIDES[i].count;
    }
    return -1;
}

int32_t simjot_count_syllables(const char* word) {
    if (word == NULL || *word == '\0') return 0;

    size_t in_len = strlen(word);
    char* w = (char*)malloc(in_len + 1);
    if (!w) return 0;

    size_t out_len = 0;
    for (size_t i = 0; i < in_len; i++) {
        unsigned char c = (unsigned char)word[i];
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
            w[out_len++] = (char)tolower(c);
        } else if (c == '\'') {
            w[out_len++] = (char)c;
        }
    }
    w[out_len] = '\0';

    if (out_len == 0) {
        free(w);
        return 0;
    }

    int override = override_count(w);
    if (override >= 0) {
        free(w);
        return override;
    }

    int count = 0;
    int prev_vowel = 0;
    for (size_t i = 0; i < out_len; i++) {
        char c = w[i];
        int is_v = is_vowel(c);
        if (is_v && !prev_vowel) count++;
        prev_vowel = is_v;
    }

    if (ends_with(w, "e") && out_len > 2 && !is_silent_e_exception(w)) {
        char prev = w[out_len - 2];
        if (!is_vowel(prev) && prev != 'l') {
            count--;
        }
    }

    if (ends_with(w, "ed") && out_len > 3) {
        char prev = w[out_len - 3];
        if (prev != 't' && prev != 'd') {
            count--;
        }
    }

    if (contains(w, "ious") || contains(w, "eous")) count--;
    if (contains(w, "tion") || contains(w, "sion")) {
        if (count < 1) count = 1;
    }
    if (ends_with(w, "ism") && count < 2) count = 2;
    if (ends_with(w, "ity") && count < 2) count = 2;

    if (count < 1) count = 1;
    free(w);
    return count;
}

// =============================================================================
// File I/O helpers (atomic write, space check)
// =============================================================================

#ifndef _WIN32
static char* simjot_strdup(const char* s) {
    size_t len = strlen(s);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, s, len + 1);
    return out;
}

static char* simjot_parent_dir(const char* path) {
    if (!path || *path == '\0') return NULL;
    const char* slash = strrchr(path, '/');
    if (!slash) return simjot_strdup(".");
    if (slash == path) return simjot_strdup("/");
    size_t len = (size_t)(slash - path);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, path, len);
    out[len] = '\0';
    return out;
}

static const char* simjot_basename(const char* path) {
    if (!path) return NULL;
    const char* slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    if (!target_path || !data || data_len < 0) return 0;
    const char* base = simjot_basename(target_path);
    if (!base || *base == '\0') return 0;

    char* dir = simjot_parent_dir(target_path);
    if (!dir) return 0;

    size_t template_len = strlen(dir) + strlen(base) + 12;
    char* tmp = (char*)malloc(template_len + 1);
    if (!tmp) {
        free(dir);
        return 0;
    }
    snprintf(tmp, template_len + 1, "%s/.%s.tmpXXXXXX", dir, base);

    int fd = mkstemp(tmp);
    if (fd < 0) {
        free(tmp);
        free(dir);
        return 0;
    }

    const uint8_t* p = data;
    int32_t remaining = data_len;
    while (remaining > 0) {
        ssize_t n = write(fd, p, (size_t)remaining);
        if (n < 0) {
            close(fd);
            unlink(tmp);
            free(tmp);
            free(dir);
            return 0;
        }
        p += n;
        remaining -= (int32_t)n;
    }

    if (fsync_file) {
        (void)fsync(fd);
    }
    if (close(fd) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (rename(tmp, target_path) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (fsync_dir) {
        int dfd = open(dir, O_RDONLY);
        if (dfd >= 0) {
            (void)fsync(dfd);
            close(dfd);
        }
    }

    free(tmp);
    free(dir);
    return 1;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    if (!path) return -1;
    struct statvfs fs;
    if (statvfs(path, &fs) != 0) {
        char* dir = simjot_parent_dir(path);
        if (!dir) return -1;
        int ok = statvfs(dir, &fs);
        free(dir);
        if (ok != 0) return -1;
    }
    unsigned long long block = fs.f_frsize ? fs.f_frsize : fs.f_bsize;
    unsigned long long avail = (unsigned long long)fs.f_bavail * block;
    return (avail < bytes_needed) ? 0 : 1;
}
#else
int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    (void)target_path;
    (void)data;
    (void)data_len;
    (void)fsync_file;
    (void)fsync_dir;
    return 0;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    (void)path;
    (void)bytes_needed;
    return -1;
}
#endif
