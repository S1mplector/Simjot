/**
 * @file json_parser.c
 * @brief Lightweight JSON Parser for Simjot
 * 
 * A fast, single-pass JSON parser optimized for common Simjot use cases:
 * - Extracting string values by key
 * - Iterating over object keys
 * - Parsing arrays of strings
 * - Dictionary file parsing
 * 
 * Zero external dependencies, minimal memory allocation.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/**
 * ════════════════════════════════════════════════════════════════════════════
 * JSON PARSER HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Skip whitespace */
static const char* skip_ws(const char* p, const char* end) {
    while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) p++;
    return p;
}

/* Skip a JSON string (after opening quote), return pointer after closing quote */
static const char* skip_string(const char* p, const char* end) {
    int escaped = 0;
    while (p < end) {
        char c = *p++;
        if (escaped) {
            escaped = 0;
            continue;
        }
        if (c == '\\') {
            escaped = 1;
            continue;
        }
        if (c == '"') return p;
    }
    return end;
}

/* Skip a JSON value (string, number, object, array, true/false/null) */
static const char* skip_value(const char* p, const char* end) {
    p = skip_ws(p, end);
    if (p >= end) return end;
    
    char c = *p;
    if (c == '"') {
        return skip_string(p + 1, end);
    } else if (c == '{') {
        int depth = 1;
        p++;
        while (p < end && depth > 0) {
            c = *p++;
            if (c == '"') p = skip_string(p, end);
            else if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return p;
    } else if (c == '[') {
        int depth = 1;
        p++;
        while (p < end && depth > 0) {
            c = *p++;
            if (c == '"') p = skip_string(p, end);
            else if (c == '[') depth++;
            else if (c == ']') depth--;
        }
        return p;
    } else if (c == 't') {
        return p + 4; /* true */
    } else if (c == 'f') {
        return p + 5; /* false */
    } else if (c == 'n') {
        return p + 4; /* null */
    } else if (c == '-' || isdigit((unsigned char)c)) {
        while (p < end && (isdigit((unsigned char)*p) || *p == '.' || 
               *p == 'e' || *p == 'E' || *p == '+' || *p == '-')) p++;
        return p;
    }
    return p + 1;
}

/* Unescape a JSON string in-place, return new length */
static int32_t unescape_string(char* str, int32_t len) {
    char* dst = str;
    const char* src = str;
    const char* end = str + len;
    
    while (src < end) {
        if (*src == '\\' && src + 1 < end) {
            src++;
            switch (*src) {
                case '"': *dst++ = '"'; break;
                case '\\': *dst++ = '\\'; break;
                case '/': *dst++ = '/'; break;
                case 'b': *dst++ = '\b'; break;
                case 'f': *dst++ = '\f'; break;
                case 'n': *dst++ = '\n'; break;
                case 'r': *dst++ = '\r'; break;
                case 't': *dst++ = '\t'; break;
                case 'u':
                    /* Unicode escape - simplified: just copy as-is or skip */
                    if (src + 4 < end) {
                        /* For now, just output ? for non-ASCII unicode */
                        *dst++ = '?';
                        src += 4;
                    }
                    break;
                default: *dst++ = *src; break;
            }
            src++;
        } else {
            *dst++ = *src++;
        }
    }
    *dst = '\0';
    return (int32_t)(dst - str);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Extract a string value by key from JSON object
 * 
 * @param json JSON string (null-terminated)
 * @param key Key to search for
 * @param output Output buffer for value
 * @param output_len Size of output buffer
 * @return Length of extracted value, or negative on error
 */
int32_t simjot_json_get_string(const char* json, const char* key,
                               char* output, int32_t output_len) {
    if (!json || !key || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    /* Find opening brace */
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -2;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end) break;
        if (*p == '}') break;
        if (*p == ',') { p++; continue; }
        
        /* Expect key string */
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        /* Skip colon */
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_ws(p, end);
        
        /* Check if this is the key we want */
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            /* Found the key, extract value */
            if (*p == '"') {
                p++;
                const char* vs = p;
                p = skip_string(p, end);
                const char* ve = p - 1;
                int32_t vlen = (int32_t)(ve - vs);
                
                if (vlen >= output_len) vlen = output_len - 1;
                memcpy(output, vs, vlen);
                output[vlen] = '\0';
                return unescape_string(output, vlen);
            } else {
                /* Value is not a string */
                return -3;
            }
        }
        
        /* Skip this value */
        p = skip_value(p, end);
    }
    
    return -4; /* Key not found */
}

/**
 * @brief Extract an integer value by key from JSON object
 * 
 * @param json JSON string
 * @param key Key to search for
 * @param out_value Pointer to store result
 * @return 1 on success, 0 on failure
 */
int32_t simjot_json_get_int(const char* json, const char* key, int64_t* out_value) {
    if (!json || !key || !out_value) return 0;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return 0;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_ws(p, end);
        
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            /* Parse integer */
            char* endptr;
            *out_value = strtoll(p, &endptr, 10);
            return endptr > p ? 1 : 0;
        }
        
        p = skip_value(p, end);
    }
    
    return 0;
}

/**
 * @brief Check if a key exists in JSON object
 * 
 * @param json JSON string
 * @param key Key to check
 * @return 1 if key exists, 0 otherwise
 */
int32_t simjot_json_has_key(const char* json, const char* key) {
    if (!json || !key) return 0;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return 0;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            return 1;
        }
        
        p = skip_value(p, end);
    }
    
    return 0;
}

/**
 * @brief Count keys in a JSON object
 * 
 * @param json JSON string
 * @return Number of keys, or negative on error
 */
int32_t simjot_json_count_keys(const char* json) {
    if (!json) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        p = skip_string(p, end);
        count++;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        p = skip_value(p, end);
    }
    
    return count;
}

/**
 * @brief Extract keys from JSON object to buffer (newline-separated)
 * 
 * @param json JSON string
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Number of keys extracted, or negative on error
 */
int32_t simjot_json_get_keys(const char* json, char* output, int32_t output_len) {
    if (!json || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t out_pos = 0;
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        int32_t klen = (int32_t)(ke - ks);
        
        /* Copy key to output */
        if (out_pos + klen + 1 < output_len) {
            if (out_pos > 0) output[out_pos++] = '\n';
            memcpy(output + out_pos, ks, klen);
            out_pos += klen;
            count++;
        }
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        p = skip_value(p, end);
    }
    
    output[out_pos] = '\0';
    return count;
}

/**
 * @brief Parse a JSON array of strings into newline-separated output
 * 
 * @param json JSON string containing array
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Number of strings extracted, or negative on error
 */
int32_t simjot_json_parse_string_array(const char* json, char* output, int32_t output_len) {
    if (!json || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t out_pos = 0;
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '[') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == ']') break;
        if (*p == ',') { p++; continue; }
        
        if (*p == '"') {
            p++;
            const char* vs = p;
            p = skip_string(p, end);
            const char* ve = p - 1;
            int32_t vlen = (int32_t)(ve - vs);
            
            if (out_pos + vlen + 1 < output_len) {
                if (out_pos > 0) output[out_pos++] = '\n';
                memcpy(output + out_pos, vs, vlen);
                out_pos += vlen;
                count++;
            }
        } else {
            p = skip_value(p, end);
        }
    }
    
    output[out_pos] = '\0';
    return count;
}

/**
 * @brief Extract a nested string value using dot notation path
 * 
 * Example: simjot_json_get_path(json, "user.name", buf, 256)
 * 
 * @param json JSON string
 * @param path Dot-separated path (e.g., "user.name")
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of value, or negative on error
 */
int32_t simjot_json_get_path(const char* json, const char* path,
                             char* output, int32_t output_len) {
    if (!json || !path || !output || output_len <= 0) return -1;
    
    char key[256];
    const char* dot;
    char temp[8192];
    const char* current = json;
    
    while ((dot = strchr(path, '.')) != NULL) {
        size_t klen = dot - path;
        if (klen >= sizeof(key)) return -2;
        memcpy(key, path, klen);
        key[klen] = '\0';
        
        /* Get nested object */
        const char* p = current;
        const char* end = current + strlen(current);
        size_t key_len = strlen(key);
        
        p = skip_ws(p, end);
        if (p >= end || *p != '{') return -3;
        p++;
        
        int found = 0;
        while (p < end && !found) {
            p = skip_ws(p, end);
            if (p >= end || *p == '}') break;
            if (*p == ',') { p++; continue; }
            
            if (*p != '"') break;
            p++;
            const char* ks = p;
            p = skip_string(p, end);
            const char* ke = p - 1;
            size_t kl = ke - ks;
            
            p = skip_ws(p, end);
            if (p >= end || *p != ':') break;
            p++;
            p = skip_ws(p, end);
            
            if (kl == key_len && memcmp(ks, key, key_len) == 0) {
                /* Found - extract the value as new current */
                const char* vs = p;
                const char* ve = skip_value(p, end);
                size_t vlen = ve - vs;
                if (vlen >= sizeof(temp)) return -4;
                memcpy(temp, vs, vlen);
                temp[vlen] = '\0';
                current = temp;
                found = 1;
            } else {
                p = skip_value(p, end);
            }
        }
        
        if (!found) return -5;
        path = dot + 1;
    }
    
    /* Final key */
    return simjot_json_get_string(current, path, output, output_len);
}
