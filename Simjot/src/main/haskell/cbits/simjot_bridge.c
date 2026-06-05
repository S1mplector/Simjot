/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

#include <string.h>
#include <stdlib.h>

/* Forward declarations - these are implemented in the main native library */
extern int poetry_analyze_sounds(const char* text);
extern int poetry_analyze_themes(const char* text);
extern double poetry_get_theme_score(const char* theme);
extern int poetry_count_syllables(const char* word);
extern int poetry_analyze_meter(const char* text);
extern int poetry_detect_meter(char* out, int out_size);

extern void rhyme_add_word(const char* word);
extern int rhyme_find(const char* word, int max_results);
extern int rhyme_check(const char* word1, const char* word2);
extern int rhyme_detect_scheme(const char* text, char* out, int out_size);
extern void rhyme_clear(void);
extern int rhyme_db_size(void);

/* Bridge functions - provide safe wrappers for Haskell FFI */

int hs_poetry_analyze_sounds(const char* text) {
    if (!text) return 0;
    return poetry_analyze_sounds(text);
}

int hs_poetry_analyze_themes(const char* text) {
    if (!text) return 0;
    return poetry_analyze_themes(text);
}

double hs_poetry_get_theme_score(const char* theme) {
    if (!theme) return 0.0;
    return poetry_get_theme_score(theme);
}

int hs_poetry_count_syllables(const char* word) {
    if (!word) return 0;
    return poetry_count_syllables(word);
}

int hs_poetry_analyze_meter(const char* text) {
    if (!text) return 0;
    return poetry_analyze_meter(text);
}

int hs_poetry_detect_meter(char* out, int out_size) {
    if (!out || out_size <= 0) return 0;
    return poetry_detect_meter(out, out_size);
}

void hs_rhyme_add_word(const char* word) {
    if (word) rhyme_add_word(word);
}

int hs_rhyme_find(const char* word, int max_results) {
    if (!word) return 0;
    return rhyme_find(word, max_results);
}

int hs_rhyme_check(const char* word1, const char* word2) {
    if (!word1 || !word2) return 0;
    return rhyme_check(word1, word2);
}

int hs_rhyme_detect_scheme(const char* text, char* out, int out_size) {
    if (!text || !out || out_size <= 0) return 0;
    return rhyme_detect_scheme(text, out, out_size);
}

void hs_rhyme_clear(void) {
    rhyme_clear();
}

int hs_rhyme_db_size(void) {
    return rhyme_db_size();
}
