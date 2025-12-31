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

#ifdef __cplusplus
}
#endif

#endif
