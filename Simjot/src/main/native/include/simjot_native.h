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

/* String operations */
int32_t simjot_string_sanitize(const char* input, char* output, int32_t output_len, int32_t max_len);
int32_t simjot_string_collapse_whitespace(char* str);
uint64_t simjot_string_hash(const char* str);
uint64_t simjot_string_hash_multi(const char** strings, int32_t count);
int32_t simjot_string_token_count(const char* text);
int32_t simjot_string_first_tokens(const char* text, char* output, int32_t output_len, int32_t max_tokens);
int32_t simjot_string_last_tokens(const char* text, char* output, int32_t output_len, int32_t max_tokens);
int32_t simjot_string_contains_ci(const char* haystack, const char* needle);
int32_t simjot_string_starts_with_ci(const char* str, const char* prefix);
int32_t simjot_string_join(const char** strings, int32_t count, const char* separator, char* output, int32_t output_len);
int32_t simjot_buffer_append_circular(char* buffer, int32_t buffer_len, int32_t max_size, const char* append, const char* separator);

/* JSON parsing */
int32_t simjot_json_get_string(const char* json, const char* key, char* output, int32_t output_len);
int32_t simjot_json_get_int(const char* json, const char* key, int64_t* out_value);
int32_t simjot_json_has_key(const char* json, const char* key);
int32_t simjot_json_count_keys(const char* json);
int32_t simjot_json_get_keys(const char* json, char* output, int32_t output_len);
int32_t simjot_json_parse_string_array(const char* json, char* output, int32_t output_len);
int32_t simjot_json_get_path(const char* json, const char* path, char* output, int32_t output_len);

/* Date/time utilities */
int64_t simjot_time_now_millis(void);
int64_t simjot_time_now_secs(void);
int32_t simjot_time_format(int64_t millis, const char* pattern, char* output, int32_t output_len);
int32_t simjot_time_format_now(const char* pattern, char* output, int32_t output_len);
int32_t simjot_time_format_iso(int64_t millis, char* output, int32_t output_len);
int32_t simjot_time_format_backup(int64_t millis, char* output, int32_t output_len);
int64_t simjot_time_parse(const char* str, const char* pattern);
int64_t simjot_time_add_days(int64_t millis, int32_t days);
int32_t simjot_time_diff_days(int64_t millis1, int64_t millis2);
int64_t simjot_time_start_of_day(int64_t millis);
int32_t simjot_time_day_of_week(int64_t millis);
int32_t simjot_time_is_leap_year(int32_t year);
int32_t simjot_time_days_in_month(int32_t year, int32_t month);
int32_t simjot_time_relative(int64_t millis, char* output, int32_t output_len);

/* Pattern matching */
int32_t simjot_pattern_match_wildcard(const char* str, const char* pattern);
int32_t simjot_pattern_find(const char* text, const char* pattern, int32_t word_boundary);
int32_t simjot_pattern_count(const char* text, const char* pattern, int32_t word_boundary);
int32_t simjot_pattern_extract_after(const char* text, const char* prefix, char* output, int32_t output_len, int32_t max_phrase_len);
int32_t simjot_pattern_extract_all(const char* text, const char* prefix, char* output, int32_t output_len, int32_t max_phrase_len);
int32_t simjot_pattern_extract_words(const char* text, const char* pattern, char* output, int32_t output_len);
int32_t simjot_pattern_replace_all(const char* text, const char* pattern, const char* replacement, char* output, int32_t output_len);
int32_t simjot_pattern_collapse_spaces(const char* text, char* output, int32_t output_len);

/* Base64 encoding */
int32_t simjot_base64_encode_len(int32_t input_len);
int32_t simjot_base64_decode_len(int32_t input_len);
int32_t simjot_base64_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_base64_decode(const char* input, uint8_t* output, int32_t output_len);
int32_t simjot_base64url_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_base64url_decode(const char* input, uint8_t* output, int32_t output_len);

/* Unicode/UTF-8 utilities */
int32_t simjot_utf8_strlen(const char* str);
int32_t simjot_utf8_valid(const char* str);
int32_t simjot_utf8_encode_codepoint(uint32_t codepoint, char* output, int32_t output_len);
int32_t simjot_utf8_decode_codepoint(const char* str, int32_t* bytes_consumed);
int32_t simjot_unicode_unescape(const char* input, char* output, int32_t output_len);

/* Hex encoding */
int32_t simjot_hex_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_hex_decode(const char* input, uint8_t* output, int32_t output_len);

#ifdef __cplusplus
}
#endif

#endif
