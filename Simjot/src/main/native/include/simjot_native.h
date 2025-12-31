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

/* Dictionary JSON parsing - optimized for simple-english-dictionary format */
int32_t simjot_json_parse_dict_words(const char* json, int32_t json_len, char* output, int32_t output_len);
int32_t simjot_json_parse_dict_entry(const char* json, const char* word, uint8_t* out, int32_t out_len);
int32_t simjot_json_load_dict_file(const char* file_path, char* output, int32_t output_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD ANALYTICS - Fast mood log parsing and statistics
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Load and parse mood log file */
int32_t simjot_mood_load(const char* file_path);

/* Compute daily statistics (call after load) */
int32_t simjot_mood_compute_daily(int32_t days_back);

/* Compute analytics summary (call after compute_daily) */
int32_t simjot_mood_compute_summary(int32_t threshold);

/* Get daily stats by index (binary output) */
int32_t simjot_mood_get_daily(int32_t index, uint8_t* out, int32_t out_len);

/* Get analytics summary (binary output) */
int32_t simjot_mood_get_summary(uint8_t* out, int32_t out_len);

/* Get counts */
int32_t simjot_mood_daily_count(void);
int32_t simjot_mood_sample_count(void);

/* Clear all loaded data */
void simjot_mood_clear(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD GRAPHICS - Native chart rendering to pixel buffers
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Render sparkline chart (mood trend line) */
int32_t simjot_mood_sparkline(const int32_t* values, int32_t count,
                               int32_t width, int32_t height,
                               uint32_t* out, uint32_t bg_color,
                               int32_t line_thickness);

/* Render bar chart (daily averages) */
int32_t simjot_mood_barchart(const int32_t* values, int32_t count,
                              int32_t width, int32_t height,
                              uint32_t* out, uint32_t bg_color,
                              int32_t bar_spacing);

/* Render gauge/radial chart */
int32_t simjot_mood_gauge(int32_t value, int32_t size,
                           uint32_t* out, uint32_t bg_color,
                           uint32_t track_color, int32_t thickness);

/* Render heatmap grid (contribution graph style) */
int32_t simjot_mood_heatmap(const int32_t* values, int32_t count,
                             int32_t cols, int32_t cell_size, int32_t cell_gap,
                             uint32_t* out, int32_t out_width, int32_t out_height,
                             uint32_t bg_color, uint32_t empty_color);

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

/* Poetry analysis */
int32_t simjot_poetry_analyze_sounds(const char* text);
int32_t simjot_poetry_get_sound_device(int32_t index, char* type_buf, int32_t type_len, char* pattern_buf, int32_t pattern_len, int32_t* line_number);
int32_t simjot_poetry_analyze_themes(const char* text);
double simjot_poetry_get_theme_score(const char* theme);
int32_t simjot_poetry_get_themes(char* output, int32_t output_len);
int32_t simjot_poetry_analyze_vocab(const char* text);
void simjot_poetry_get_vocab_stats(int32_t* total, int32_t* unique, double* diversity, double* avg_len);
int32_t simjot_poetry_count_syllables(const char* word);
int32_t simjot_poetry_analyze_meter(const char* text);
int32_t simjot_poetry_get_line_syllables(int32_t line_index);
int32_t simjot_poetry_detect_meter(char* output, int32_t output_len);

/* Rhyme engine */
void simjot_rhyme_add_word(const char* word);
int32_t simjot_rhyme_add_words(const char* words);
int32_t simjot_rhyme_find(const char* word, int32_t max_results);
int32_t simjot_rhyme_get_result(int32_t index, char* output, int32_t output_len);
int32_t simjot_rhyme_get_all_results(char* output, int32_t output_len);
int32_t simjot_rhyme_get_key(const char* word, char* output, int32_t output_len);
int32_t simjot_rhyme_check(const char* word1, const char* word2);
int32_t simjot_rhyme_detect_scheme(const char* text, char* output, int32_t output_len);
int32_t simjot_rhyme_get_pair_count(void);
int32_t simjot_rhyme_get_pair(int32_t index, int32_t* line1, int32_t* line2);
void simjot_rhyme_clear(void);
int32_t simjot_rhyme_db_size(void);

/* Math utilities */
double simjot_math_vec2_length(double x, double y);
double simjot_math_vec2_dot(double x1, double y1, double x2, double y2);
double simjot_math_vec2_cross(double x1, double y1, double x2, double y2);
void simjot_math_vec2_normalize(double x, double y, double* out_x, double* out_y);
void simjot_math_vec2_rotate(double x, double y, double angle_rad, double* out_x, double* out_y);
double simjot_math_vec2_angle(double x, double y);
double simjot_math_vec2_distance(double x1, double y1, double x2, double y2);
void simjot_math_bezier_quad(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y, double t, double* out_x, double* out_y);
void simjot_math_bezier_cubic(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y, double p3x, double p3y, double t, double* out_x, double* out_y);
double simjot_math_ease(int32_t type, double t);
uint32_t simjot_math_color_blend(uint32_t color1, uint32_t color2, double t);
uint32_t simjot_math_hsl_to_rgb(double h, double s, double l);
void simjot_math_rgb_to_hsl(uint32_t argb, double* h, double* s, double* l);
int32_t simjot_math_compute_stats(const double* data, int32_t count);
double simjot_math_get_stat(int32_t which);
double simjot_math_deg_to_rad(double degrees);
double simjot_math_rad_to_deg(double radians);
double simjot_math_normalize_angle(double radians);
double simjot_math_lerp(double a, double b, double t);
double simjot_math_clamp(double value, double min_val, double max_val);
double simjot_math_map_range(double value, double in_min, double in_max, double out_min, double out_max);

/* Concurrent/task utilities */
int32_t simjot_task_create(const char* data, int32_t priority);
int32_t simjot_task_pending_count(void);
int32_t simjot_task_pop(char* data_out, int32_t data_len, int32_t timeout_ms);
void simjot_task_complete(int32_t task_id, const char* result);
void simjot_task_clear(void);
void simjot_task_stop(void);
void simjot_parallel_set_threads(int32_t count);
int32_t simjot_parallel_get_hw_threads(void);
void simjot_thread_sleep(int32_t milliseconds);
int64_t simjot_thread_id(void);
int64_t simjot_atomic_inc(int32_t counter_id);
int64_t simjot_atomic_dec(int32_t counter_id);
int64_t simjot_atomic_get(int32_t counter_id);
void simjot_atomic_set(int32_t counter_id, int64_t value);
int32_t simjot_atomic_cas(int32_t counter_id, int64_t expected, int64_t desired);
int64_t simjot_atomic_add(int32_t counter_id, int64_t value);
int64_t simjot_hrtime_ns(void);
int64_t simjot_monotonic_ms(void);

/* Collection utilities */
int32_t simjot_set_create(void);
void simjot_set_add(int32_t set_id, const char* str);
int32_t simjot_set_contains(int32_t set_id, const char* str);
void simjot_set_remove(int32_t set_id, const char* str);
int32_t simjot_set_size(int32_t set_id);
void simjot_set_clear(int32_t set_id);
int32_t simjot_set_add_bulk(int32_t set_id, const char* strings);
int32_t simjot_map_create(void);
void simjot_map_set(int32_t map_id, const char* key, const char* value);
int32_t simjot_map_get(int32_t map_id, const char* key, char* output, int32_t output_len);
int32_t simjot_map_has(int32_t map_id, const char* key);
void simjot_map_remove(int32_t map_id, const char* key);
int32_t simjot_map_size(int32_t map_id);
void simjot_map_clear(int32_t map_id);
int32_t simjot_freq_create(void);
void simjot_freq_add(int32_t map_id, const char* str, int32_t count);
int32_t simjot_freq_get(int32_t map_id, const char* str);
int32_t simjot_freq_top_n(int32_t map_id, int32_t n, char* output, int32_t output_len);
int32_t simjot_freq_unique_count(int32_t map_id);
int32_t simjot_freq_total_count(int32_t map_id);
void simjot_freq_clear(int32_t map_id);
int32_t simjot_freq_add_text(int32_t map_id, const char* text);
int32_t simjot_cache_create(int32_t max_size);
void simjot_cache_set(int32_t cache_id, const char* key, const char* value);
int32_t simjot_cache_get(int32_t cache_id, const char* key, char* output, int32_t output_len);
int32_t simjot_cache_size(int32_t cache_id);
void simjot_cache_clear(int32_t cache_id);

/* SIMD operations */
int32_t simjot_simd_strlen(const char* str);
int32_t simjot_simd_memchr(const void* haystack, int needle, int32_t len);
int32_t simjot_simd_strcasechr(const char* str, int c);
int64_t simjot_simd_sum_i32(const int32_t* arr, int32_t len);
double simjot_simd_sum_f64(const double* arr, int32_t len);
void simjot_simd_minmax_f64(const double* arr, int32_t len, double* out_min, double* out_max);
int32_t simjot_simd_memcmp(const void* a, const void* b, int32_t len);
int32_t simjot_simd_support_level(void);

/* Memory pool operations */
int32_t simjot_pool_create(int32_t block_size, int32_t initial_blocks);
void* simjot_pool_alloc(int32_t pool_id);
void simjot_pool_free(int32_t pool_id, void* ptr);
void simjot_pool_stats(int32_t pool_id, int32_t* total, int32_t* used, int32_t* block_size);
void simjot_pool_destroy(int32_t pool_id);

/* Arena allocator */
int32_t simjot_arena_create(void);
void* simjot_arena_alloc(int32_t arena_id, int32_t size, int32_t alignment);
const char* simjot_arena_strdup(int32_t arena_id, const char* str);
void simjot_arena_reset(int32_t arena_id);
void simjot_arena_destroy(int32_t arena_id);
void simjot_arena_stats(int32_t arena_id, int64_t* total_allocated, int64_t* total_used);

/* String interning */
int32_t simjot_intern_init(void);
const char* simjot_intern(const char* str);
int32_t simjot_intern_contains(const char* str);
int32_t simjot_intern_count(void);
void simjot_intern_clear(void);

/* Scratch buffer */
void* simjot_scratch_alloc(int32_t size);
void simjot_scratch_reset(void);
int32_t simjot_scratch_capacity(void);

/* File system operations */
int64_t simjot_fs_size(const char* path);
int64_t simjot_fs_mtime(const char* path);
int32_t simjot_fs_exists(const char* path);
int32_t simjot_fs_is_dir(const char* path);
int32_t simjot_fs_is_file(const char* path);
int32_t simjot_fs_count(const char* path, int32_t max_depth, int32_t* file_count, int32_t* dir_count, int64_t* total_size);
int32_t simjot_fs_list_recursive(const char* path, const char* extension, int32_t max_depth, char* output, int32_t output_len);
int32_t simjot_fs_find(const char* path, const char* pattern, char* output, int32_t output_len);
int32_t simjot_fs_read_all(const char* path, uint8_t* output, int32_t output_len);
int32_t simjot_fs_write_all(const char* path, const uint8_t* data, int32_t len);
int32_t simjot_fs_append(const char* path, const uint8_t* data, int32_t len);
int32_t simjot_fs_mkdir(const char* path);
int32_t simjot_fs_remove(const char* path);
int32_t simjot_fs_rename(const char* old_path, const char* new_path);
int32_t simjot_fs_watch_create(const char* path);
int32_t simjot_fs_watch_poll(int32_t watch_id, int32_t timeout_ms);
void simjot_fs_watch_destroy(int32_t watch_id);
int32_t simjot_fs_extension(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_basename(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_dirname(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_join(const char* base, const char* child, char* output, int32_t output_len);
int32_t simjot_fs_list_filtered(const char* dir_path, const char* extensions, int32_t include_hidden, char* output, int32_t output_len);
int32_t simjot_fs_count_entries(const char* dir_path, int32_t include_hidden);

/* Image operations */
int32_t simjot_image_resize_bilinear(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize_bicubic(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize_area(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h, int32_t quality);
void simjot_image_calc_fit_size(int32_t src_w, int32_t src_h, int32_t max_w, int32_t max_h, int32_t* out_w, int32_t* out_h);
void simjot_image_argb_to_rgba(const int32_t* argb, uint8_t* rgba, int32_t pixel_count);
void simjot_image_rgba_to_argb(const uint8_t* rgba, int32_t* argb, int32_t pixel_count);
int32_t simjot_image_resize_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h, int32_t* dst_argb, int32_t dst_w, int32_t dst_h, int32_t quality);

/* Background image processing (native cache + scaling + opacity) */
void simjot_bg_calc_cover_fit(int32_t src_w, int32_t src_h, int32_t panel_w, int32_t panel_h, int32_t* out_w, int32_t* out_h, int32_t* out_x, int32_t* out_y);
void simjot_bg_apply_opacity(int32_t* argb, int32_t pixel_count, float opacity);
int32_t simjot_bg_process(const int32_t* src_argb, int32_t src_w, int32_t src_h, int32_t panel_w, int32_t panel_h, float opacity, const char* cache_key, int32_t** out_pixels, int32_t* out_w, int32_t* out_h, int32_t* out_x, int32_t* out_y);
void simjot_bg_cache_clear(void);
void simjot_bg_cache_invalidate(const char* cache_key, int32_t panel_w, int32_t panel_h, float opacity);
void simjot_bg_cache_stats(int32_t* count, int64_t* total_bytes);
void simjot_bg_blur(int32_t* argb, int32_t width, int32_t height, int32_t radius, int32_t passes);

/* Aero/Glass UI effect computations */
void simjot_aero_outer_glow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas);
int32_t simjot_aero_outer_glow_alpha(int32_t layer, int32_t size, int32_t max_alpha);
void simjot_aero_inner_shadow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas);
int32_t simjot_aero_inner_shadow_alpha(int32_t layer, int32_t size, int32_t max_alpha);
int32_t simjot_aero_lerp_color(int32_t color1, int32_t color2, float t);
void simjot_aero_gradient_colors(int32_t top_color, int32_t bottom_color, int32_t height, int32_t* out_colors);
int32_t simjot_aero_blend_over(int32_t fg, int32_t bg);
int32_t simjot_aero_apply_alpha(int32_t color, int32_t alpha);
void simjot_aero_glass_overlay(int32_t height, uint8_t* out_sheen_alphas, uint8_t* out_shadow_alphas);
void simjot_aero_frosted_glass(int32_t height, int32_t base_top_alpha, int32_t base_bottom_alpha, uint8_t* out_base_alphas);
void simjot_aero_glow_stroke_widths(int32_t size, int32_t* out_widths);
void simjot_aero_shadow_insets(int32_t size, int32_t* out_insets);

/* Animation math - easing functions */
float simjot_ease_cosine(float t);
float simjot_ease_smootherstep(float t);
float simjot_ease_smoothstep(float t);
float simjot_ease_out_cubic(float t);
float simjot_ease_in_cubic(float t);
float simjot_ease_in_out_cubic(float t);
float simjot_ease_elastic(float t);
float simjot_spring_decay(float current, float damping, float threshold);

/* Animation math - heartbeat */
float simjot_heartbeat_scale(float phase, float baseAmplitude, float spring);
int32_t simjot_heartbeat_is_peak(float currentEased, float lastEased, float threshold);
void simjot_heartbeat_update(float* phase, float phaseStep, float* spring, float springDamping, float springKick, float* lastEased, float baseAmplitude, float* outScale, int32_t* outPeaked);

/* Animation math - ECG waveform */
float simjot_ecg_sample(float phase);
void simjot_ecg_generate(float startPhase, float phaseStep, float* output, int32_t count);

/* Animation math - fade transitions */
float simjot_fade_alpha(int64_t elapsedMs, int64_t durationMs, int32_t fadeOut, int32_t easingType);
int32_t simjot_fade_is_complete(int64_t elapsedMs, int64_t durationMs);

/* Animation math - color interpolation */
int32_t simjot_color_lerp(int32_t color1, int32_t color2, float t);
int32_t simjot_color_lerp_eased(int32_t color1, int32_t color2, float t, int32_t easingType);

/* Animation math - disappear animation */
void simjot_disappear_anim(float t, float* outAlpha, float* outScale, float* outOffsetX);
float simjot_disappear_value(float t);
float simjot_collapse_height(float t);

/* ═══════════════════════════════════════════════════════════════════════════
 * UI SCALING - Cross-platform DPI-aware scaling
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Display information */
int32_t simjot_get_display_count(void);
float simjot_get_display_scale(int32_t displayIndex);
float simjot_get_primary_display_scale(void);
float simjot_get_display_dpi(int32_t displayIndex);
void simjot_invalidate_display_cache(void);

/* Scaling utilities */
int32_t simjot_scale_dimension(int32_t value, float scale);
float simjot_scale_value(float value, float scale);
float simjot_scale_font_size(float baseSize, float scale);
void simjot_scale_insets(int32_t top, int32_t left, int32_t bottom, int32_t right,
                         float scale, int32_t* outInsets);
void simjot_scale_dimensions(int32_t width, int32_t height, float scale, int32_t* outDimensions);

/* ═══════════════════════════════════════════════════════════════════════════
 * SETUP & INITIALIZATION - First-time setup utilities
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Initialize directory structure */
int32_t simjot_setup_init(const char* root_path);

/* Verify setup is complete (returns bitmask) */
int32_t simjot_setup_verify(const char* root_path);

/* Check if directory is truly writable */
int32_t simjot_verify_writable(const char* dir_path);

/* Get detailed status (8-element array) */
int32_t simjot_setup_status(const char* root_path, int32_t* out_status);

/* Config file operations */
int32_t simjot_write_config(const char* config_path, const char* root_path);
int32_t simjot_read_config(const char* config_path, char* out_root_path);

/* Directory operations */
int32_t simjot_create_directory(const char* dir_path);

/* Check if setup is needed */
int32_t simjot_needs_setup(const char* config_path);

/* ═══════════════════════════════════════════════════════════════════════════
 * WATCHDOG SYSTEM - Application lifecycle management
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Start a watchdog timer (action: 0=none, 1=callback, 2=exit, 3=halt) */
int32_t simjot_watchdog_start(int64_t timeout_ms, int32_t action, const char* name);

/* Cancel a running watchdog */
int32_t simjot_watchdog_cancel(int32_t id);

/* Reset watchdog timer (restart countdown) */
int32_t simjot_watchdog_reset(int32_t id);

/* Get watchdog state (0=inactive, 1=running, 2=triggered, 3=cancelled) */
int32_t simjot_watchdog_state(int32_t id);

/* Get remaining time in milliseconds */
int64_t simjot_watchdog_remaining(int32_t id);

/* Force immediate process halt */
void simjot_force_halt(void);

/* Get monotonic time in milliseconds */
int64_t simjot_monotonic_time_ms(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE METADATA UTILITIES - Fast native file operations
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Count words in a text buffer */
int32_t simjot_count_words(const char* text, int32_t len);

/* Count words in a file (reads in chunks, memory efficient) */
int32_t simjot_count_words_file(const char* path);

/* Extract first non-empty line from file as title */
int32_t simjot_extract_title(const char* path, char* out, int32_t out_len);

/* Get file size and modification time */
int32_t simjot_file_meta(const char* path, int64_t* out_size, int64_t* out_mtime);

/* Batch metadata: word count, size, mtime, title in one call */
int32_t simjot_file_meta_batch(const char* path, uint8_t* out, int32_t out_len);

/* List files in directory with metadata (name, size, mtime) */
int32_t simjot_list_files_meta(const char* dir_path, const char* extension,
                                uint8_t* out, int32_t out_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * COMPONENT PROFILER - Per-component CPU and memory tracking
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Initialize profiler with sampling interval in milliseconds */
int32_t simjot_profiler_init(int32_t sample_interval_ms);

/* Start/stop profiling */
int32_t simjot_profiler_start(void);
int32_t simjot_profiler_stop(void);
void simjot_profiler_reset(void);

/* Component registration */
int32_t simjot_profiler_register_component(const char* name);
int32_t simjot_profiler_register_thread(const char* component_name, uint64_t thread_id);
int32_t simjot_profiler_unregister_thread(const char* component_name, uint64_t thread_id);

/* Memory tracking */
void simjot_profiler_track_alloc(const char* component_name, int64_t bytes);
void simjot_profiler_track_free(const char* component_name, int64_t bytes);

/* Sampling - call periodically to update metrics */
int32_t simjot_profiler_sample(void);

/* Snapshot/reporting */
int32_t simjot_profiler_component_count(void);
int32_t simjot_profiler_get_component_snapshot(int32_t index, uint8_t* out, int32_t out_len);
int32_t simjot_profiler_get_summary(uint8_t* out, int32_t out_len);

/* CLI output - returns length written */
int32_t simjot_profiler_print_report(char* out, int32_t out_len);
int32_t simjot_profiler_status_line(char* out, int32_t out_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * IMAGE SCALING - SIMD-accelerated resize
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Bilinear interpolation (fast) */
int32_t simjot_image_scale_bilinear(const uint32_t* src, int32_t src_w, int32_t src_h,
                                     uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Bicubic interpolation (high quality) */
int32_t simjot_image_scale_bicubic(const uint32_t* src, int32_t src_w, int32_t src_h,
                                    uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Progressive downscale (best for large reductions) */
int32_t simjot_image_scale_progressive(const uint32_t* src, int32_t src_w, int32_t src_h,
                                        uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Auto-select algorithm: quality 0=fast, 1=balanced, 2=best */
int32_t simjot_image_scale(const uint32_t* src, int32_t src_w, int32_t src_h,
                            uint32_t* dst, int32_t dst_w, int32_t dst_h,
                            int32_t quality);

/* Gaussian blur (separable) */
int32_t simjot_image_blur(uint32_t* pixels, int32_t width, int32_t height, int32_t radius);

/* Tint image with color */
int32_t simjot_image_tint(uint32_t* pixels, int32_t width, int32_t height,
                           uint32_t tint_color, float intensity);

/* ═══════════════════════════════════════════════════════════════════════════
 * SPELL CHECK - Edit distance and candidate generation
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Generate edit-distance-1 candidates (null-separated output) */
int32_t simjot_spell_edit1(const char* word, char* output, int32_t output_len);

/* Generate edit-distance-2 candidates (limited for performance) */
int32_t simjot_spell_edit2(const char* word, char* output, int32_t output_len);

/* Levenshtein distance */
int32_t simjot_levenshtein(const char* a, const char* b);

/* Damerau-Levenshtein distance (with transpositions) */
int32_t simjot_damerau_levenshtein(const char* a, const char* b);

/* Batch Levenshtein: compute distance to multiple candidates */
int32_t simjot_levenshtein_batch(const char* word, const char* candidates, int32_t candidates_len,
                                  int32_t* distances, int32_t max_results);

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* String sanitization */
char* simjot_text_sanitize(const char* input);
char* simjot_text_collapse_whitespace(const char* input);
char* simjot_text_strip_html(const char* input);
char* simjot_text_to_lower(const char* input);
char* simjot_text_to_upper(const char* input);
char* simjot_text_title_case(const char* input);

/* Text statistics (alternate signatures) */
int32_t simjot_text_char_count_nospace(const char* text);
int32_t simjot_text_syllable_count(const char* word);
int32_t simjot_text_analyze(const char* text, int32_t* out_stats, int32_t stats_count);

/* Text validation */
int32_t simjot_text_is_ascii(const char* text);
int32_t simjot_text_is_alnum(const char* text);
int32_t simjot_text_is_identifier(const char* text);
int32_t simjot_text_is_email(const char* text);
int32_t simjot_text_is_safe_filename(const char* text);

/* Parsing */
int32_t simjot_parse_int(const char* text, int32_t default_val);
double simjot_parse_double(const char* text, double default_val);
int32_t simjot_parse_bool(const char* text, int32_t default_val);

/* String extraction */
char* simjot_text_first_words(const char* text, int32_t count);
char* simjot_text_first_line(const char* text);
char* simjot_text_ellipsis(const char* text, int32_t max_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * MATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Basic statistics */
double simjot_math_mean(const double* values, int32_t count);
double simjot_math_mean_int(const int32_t* values, int32_t count);
double simjot_math_variance(const double* values, int32_t count);
double simjot_math_stddev(const double* values, int32_t count);
double simjot_math_min(const double* values, int32_t count);
double simjot_math_max(const double* values, int32_t count);
double simjot_math_sum(const double* values, int32_t count);
double simjot_math_median(double* values, int32_t count);
double simjot_math_percentile(double* values, int32_t count, double percentile);

/* Moving averages */
int32_t simjot_math_sma(const double* values, int32_t count, int32_t window, double* out);
int32_t simjot_math_ema(const double* values, int32_t count, double alpha, double* out);

/* Correlation & regression */
double simjot_math_correlation(const double* x, const double* y, int32_t count);
double simjot_math_linear_regression(const double* x, const double* y, int32_t count,
                                      double* out_slope, double* out_intercept);

/* Clamping & normalization */
int32_t simjot_math_clamp_int(int32_t value, int32_t min, int32_t max);
double simjot_math_clamp(double value, double min, double max);
double simjot_math_lerp(double a, double b, double t);
double simjot_math_inverse_lerp(double a, double b, double value);
int32_t simjot_math_normalize(const double* values, int32_t count, double* out);

/* Comprehensive stats */
int32_t simjot_math_stats(const double* values, int32_t count, double* out, int32_t out_count);

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* File information */
int64_t simjot_file_size(const char* path);
int64_t simjot_file_mtime(const char* path);
int32_t simjot_file_exists(const char* path);
int32_t simjot_file_is_file(const char* path);
int32_t simjot_file_is_dir(const char* path);
int32_t simjot_file_is_readable(const char* path);
int32_t simjot_file_is_writable(const char* path);

/* Disk space */
int64_t simjot_disk_available(const char* path);
int64_t simjot_disk_total(const char* path);

/* File operations */
char* simjot_file_read(const char* path, int64_t* out_size);
char* simjot_file_read_text(const char* path);
int32_t simjot_file_write_atomic(const char* path, const char* data, int64_t size);
int32_t simjot_file_copy(const char* src, const char* dst);
int32_t simjot_file_delete(const char* path);
int32_t simjot_mkdir_p(const char* path);

/* Directory listing */
int32_t simjot_dir_count(const char* path, const char* extension);
int32_t simjot_dir_list(const char* path, const char* extension, char* out_names, int32_t out_size);

/* Path utilities */
const char* simjot_path_basename(const char* path);
const char* simjot_path_extension(const char* path);
char* simjot_path_dirname(const char* path);
char* simjot_path_join(const char* dir, const char* name);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD ANALYTICS ENGINE
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Daily averages computation */
int32_t simjot_mood_daily_averages(
    const int32_t* samples, const int64_t* timestamps, int32_t count,
    int32_t start_day, int32_t num_days,
    double* out_averages, int32_t* out_counts);

/* Smoothing (rolling average) */
int32_t simjot_mood_smooth(const double* values, int32_t count, int32_t window, double* out_smoothed);

/* Volatility (standard deviation) */
double simjot_mood_volatility(const double* values, int32_t count);

/* Streak detection */
int32_t simjot_mood_streaks(
    const double* values, int32_t count, double threshold,
    int32_t* out_current, int32_t* out_longest_good, int32_t* out_longest_bad);

/* Overall average */
double simjot_mood_average(const double* values, int32_t count);

/* Complete mood analysis */
int32_t simjot_mood_analyze(
    const int32_t* mood_values, const int64_t* timestamps, int32_t sample_count,
    int32_t days_back, int32_t smoothing_window,
    double* out_stats, double* out_daily_avgs, double* out_smoothed,
    int32_t max_days);

/* Trend analysis */
double simjot_mood_trend(const double* values, int32_t count);
double simjot_mood_weighted_recent(const double* values, int32_t count, double decay);

/* Correlation and anomaly detection */
double simjot_mood_correlation(const int32_t* a, const int32_t* b, int32_t count);
int32_t simjot_mood_anomalies(const double* values, int32_t count, double threshold, int32_t* out_anomalies);

/* ═══════════════════════════════════════════════════════════════════════════
 * VOCABULARY ANALYZER
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Word extraction */
int32_t simjot_vocab_extract_words(const char* text, char* out_words, int32_t out_size, int32_t* out_count);

/* Syllable counting */
int32_t simjot_vocab_syllables(const char* word);

/* Readability metrics */
double simjot_vocab_flesch_ease(int32_t words, int32_t sentences, int32_t syllables);
double simjot_vocab_flesch_kincaid(int32_t words, int32_t sentences, int32_t syllables);
double simjot_vocab_gunning_fog(int32_t words, int32_t sentences, int32_t complex_words);
double simjot_vocab_smog(int32_t polysyllabic, int32_t sentences);
double simjot_vocab_coleman_liau(int32_t words, int32_t sentences, int32_t characters);
double simjot_vocab_ari(int32_t words, int32_t sentences, int32_t characters);

/* Lexical diversity */
double simjot_vocab_ttr(int32_t unique_words, int32_t total_words);
double simjot_vocab_yules_k(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words);
double simjot_vocab_simpsons_d(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words);

/* Comprehensive analysis */
int32_t simjot_vocab_analyze(const char* text, double* out_stats);
int32_t simjot_vocab_top_words(const char* text, int32_t n, char* out_words, int32_t* out_counts, int32_t out_size);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST SEARCH (SIMD-accelerated)
 * ═══════════════════════════════════════════════════════════════════════════ */

int64_t simjot_search_find(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int64_t simjot_search_find_ci(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int32_t simjot_search_count(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int32_t simjot_search_find_all(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len, int64_t* out_positions, int32_t max_results);

/* Multi-pattern search (Aho-Corasick) */
void* simjot_search_ac_build(const char* patterns, int32_t pattern_count);
int32_t simjot_search_ac_find(void* ac_handle, const char* text, int64_t text_len, int64_t* out_positions, int32_t* out_patterns, int32_t max_results);
void simjot_search_ac_free(void* ac_handle);

/* Fuzzy search */
int32_t simjot_search_fuzzy(const char* text, int64_t text_len, const char* pattern, int32_t pattern_len, int32_t max_distance, int64_t* out_positions, int32_t* out_distances, int32_t max_results);

/* ═══════════════════════════════════════════════════════════════════════════
 * MEMORY POOL ALLOCATORS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Fixed-size block pool (handle-based API) */
void* simjot_pool_create_ex(int32_t block_size, int32_t block_count);
void* simjot_pool_alloc_ex(void* pool_handle);
void simjot_pool_free_ex(void* pool_handle, void* ptr);
void simjot_pool_stats_ex(void* pool_handle, int32_t* out_total, int32_t* out_allocated, int32_t* out_block_size);
void simjot_pool_reset_ex(void* pool_handle);
void simjot_pool_destroy_ex(void* pool_handle);

/* Arena allocator (bump pointer, handle-based API) */
void* simjot_arena_create_ex(int32_t initial_size);
void* simjot_arena_alloc_ex(void* arena_handle, int32_t size, int32_t align);
void* simjot_arena_calloc_ex(void* arena_handle, int32_t count, int32_t size);
char* simjot_arena_strdup_ex(void* arena_handle, const char* str);
void simjot_arena_stats_ex(void* arena_handle, int64_t* out_used, int64_t* out_capacity);
void simjot_arena_reset_ex(void* arena_handle);
void simjot_arena_destroy_ex(void* arena_handle);

/* Slab allocator (multiple size classes) */
void* simjot_slab_create(void);
void* simjot_slab_alloc(void* slab_handle, int32_t size);
void simjot_slab_free(void* slab_handle, void* ptr, int32_t size);
void simjot_slab_destroy(void* slab_handle);

/* ═══════════════════════════════════════════════════════════════════════════
 * LRU CACHE
 * ═══════════════════════════════════════════════════════════════════════════ */

void* simjot_lru_cache_create(int32_t max_entries, int64_t max_memory);
void simjot_lru_cache_set_destructor(void* cache_handle, void (*destructor)(void*));
int32_t simjot_lru_cache_put(void* cache_handle, const char* key, void* value, int32_t value_size, int64_t ttl_ms);
void* simjot_lru_cache_get(void* cache_handle, const char* key, int32_t* out_size);
int32_t simjot_lru_cache_contains(void* cache_handle, const char* key);
int32_t simjot_lru_cache_remove(void* cache_handle, const char* key);
void simjot_lru_cache_clear(void* cache_handle);
void simjot_lru_cache_stats(void* cache_handle, int64_t* out_hits, int64_t* out_misses, int32_t* out_size, int64_t* out_memory);
void simjot_lru_cache_destroy(void* cache_handle);

/* String interning */
void* simjot_interner_create(void);
const char* simjot_interner_intern(void* interner_handle, const char* str);
void simjot_interner_stats(void* interner_handle, int64_t* out_count, int64_t* out_saved);
void simjot_interner_destroy(void* interner_handle);

/* Bloom filter */
void* simjot_bloom_create(int32_t expected_items, double false_positive_rate);
void simjot_bloom_add(void* filter_handle, const char* item);
int32_t simjot_bloom_test(void* filter_handle, const char* item);
void simjot_bloom_destroy(void* filter_handle);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST COMPRESSION
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_lz4_compress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_lz4_decompress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_lz4_compress_bound(int32_t src_size);

/* RLE compression */
int32_t simjot_rle_compress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_rle_decompress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);

/* Delta encoding */
int32_t simjot_delta_encode_i32(const int32_t* src, int32_t count, int32_t* dst);
int32_t simjot_delta_decode_i32(const int32_t* src, int32_t count, int32_t* dst);
int32_t simjot_delta_encode_f64(const double* src, int32_t count, int64_t* dst);
int32_t simjot_delta_decode_f64(const int64_t* src, int32_t count, double* dst);

/* Zigzag encoding */
int32_t simjot_zigzag_encode_i32(const int32_t* src, int32_t count, uint32_t* dst);
int32_t simjot_zigzag_decode_i32(const uint32_t* src, int32_t count, int32_t* dst);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST JSON PARSER
 * ═══════════════════════════════════════════════════════════════════════════ */

void* simjot_json_parse(const char* json, int32_t len);
int32_t simjot_json_type(void* parser);
const char* simjot_json_get_string_at(void* parser, int32_t index, int32_t* out_len);
double simjot_json_get_number(void* parser, int32_t index);
int32_t simjot_json_get_bool(void* parser, int32_t index);
int32_t simjot_json_array_length(void* parser, int32_t index);
int32_t simjot_json_array_get(void* parser, int32_t array_idx, int32_t elem_idx);
int32_t simjot_json_object_size(void* parser, int32_t index);
int32_t simjot_json_object_get(void* parser, int32_t obj_idx, const char* key);
const char* simjot_json_error(void* parser);
void simjot_json_free(void* parser);

/* JSON builder */
void* simjot_json_builder_create(void);
void simjot_json_builder_object_start(void* builder);
void simjot_json_builder_object_end(void* builder);
void simjot_json_builder_array_start(void* builder);
void simjot_json_builder_array_end(void* builder);
void simjot_json_builder_key(void* builder, const char* key);
void simjot_json_builder_string(void* builder, const char* value);
void simjot_json_builder_number(void* builder, double value);
void simjot_json_builder_bool(void* builder, int32_t value);
void simjot_json_builder_null(void* builder);
const char* simjot_json_builder_get(void* builder, int32_t* out_len);
void simjot_json_builder_free(void* builder);

#ifdef __cplusplus
}
#endif

#endif
