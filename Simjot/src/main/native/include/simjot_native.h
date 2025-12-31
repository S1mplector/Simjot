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
int32_t simjot_perf_snapshot(uint8_t* out, int32_t out_len);
int32_t simjot_binary_health(const char* path, uint8_t* out, int32_t out_len);

int32_t simjot_count_syllables(const char* word);
int32_t simjot_rhyme_key(const char* word, char* out, int32_t out_len);
int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t out_len);

int32_t simjot_dict_set_base_path(const char* path);
int32_t simjot_dict_contains(const char* word);
int32_t simjot_dict_lookup(const char* word, uint8_t* out, int32_t out_len);
int32_t simjot_dict_rhymes_for(const char* word, int32_t max_results, uint8_t* out, int32_t out_len);
int32_t simjot_dict_size(void);

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir);
int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed);
int32_t simjot_copy_file(const char* src_path, const char* dst_path, int32_t copy_attrs);
int32_t simjot_list_dir_size(const char* path, int32_t include_hidden);
int32_t simjot_list_dir(const char* path, int32_t include_hidden, uint8_t* out, int32_t out_len);

int32_t simjot_spell_init(const char* base_path);
int32_t simjot_spell_contains(const char* word);
int32_t simjot_spell_suggestions(const char* word, int32_t max_results, uint8_t* out, int32_t out_len);
int32_t simjot_spell_best_correction(const char* word, char* out, int32_t out_len);
int32_t simjot_spell_add_user_word(const char* word);
int32_t simjot_spell_clear_user_words(void);

/* Text processing utilities */
int32_t simjot_text_word_count(const char* text);
int32_t simjot_text_sentence_count(const char* text);
int32_t simjot_text_char_count(const char* text, int32_t include_spaces);
int32_t simjot_text_extract_words(const char* text, char* out, int32_t out_len);
int32_t simjot_text_last_word(const char* text, char* out, int32_t out_len);
int32_t simjot_text_normalize(const char* text, char* out, int32_t out_len);
int32_t simjot_text_fuzzy_match(const char* text, const char* query);
int32_t simjot_text_fuzzy_score(const char* text, const char* query);
int32_t simjot_text_line_count(const char* text);
int32_t simjot_text_get_line(const char* text, int32_t line_num, char* out, int32_t out_len);

/* String distance functions */
int32_t simjot_text_levenshtein(const char* a, const char* b);
int32_t simjot_text_damerau_levenshtein(const char* a, const char* b);
int32_t simjot_text_similarity(const char* a, const char* b);

/* Compression utilities */
int32_t simjot_compress(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len, int32_t level);
int32_t simjot_decompress(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);
int32_t simjot_compress_bound(int32_t input_len);
int32_t simjot_compress_default(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);
int32_t simjot_compress_fast(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);

#ifdef __cplusplus
}
#endif

#endif
