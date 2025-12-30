#ifndef SIMJOT_NATIVE_H
#define SIMJOT_NATIVE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t simjot_add(int32_t a, int32_t b);
int32_t simjot_strlen(const char* str);
int64_t simjot_sum_array(const int32_t* arr, int32_t len);
int64_t simjot_fib(int32_t n);

int32_t simjot_sha256_file(const char* path, uint8_t* out32);

int32_t simjot_count_syllables(const char* word);
int32_t simjot_rhyme_key(const char* word, char* out, int32_t out_len);
int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t out_len);

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir);
int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed);
int32_t simjot_list_dir_size(const char* path, int32_t include_hidden);
int32_t simjot_list_dir(const char* path, int32_t include_hidden, uint8_t* out, int32_t out_len);

#ifdef __cplusplus
}
#endif

#endif
