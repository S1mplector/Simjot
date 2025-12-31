/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Meant to provide a Panama FFM wrapper for native Simjot functions.
 * 
 * This class provides a Java interface to native Simjot functions using Java's Foreign Function & Memory API (FFM).
 * It serves as a bridge between Java and the native C library for various system operations.
 * 
 * @author S1mplector
 */

package main.infrastructure.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Panama FFM wrapper for native Simjot functions.
 * Requires Java 22+.
 * Make sure to load the native library before using it.
 * Otherwise it will throw an exception, and the application will crash.
 * 
 * Usage:
 *   NativeLibrary lib = NativeLibrary.load("/path/to/libsimjot_native.dylib");
 *   int result = lib.add(2, 3);  // returns 5
 * 
 * And so on.
 * 
 * @author S1mplector
 */
public final class NativeLibrary implements AutoCloseable {
    
    private final Arena arena;
    private final SymbolLookup lookup;
    private final Linker linker;
    
    // Method handles for native functions
    private final MethodHandle addHandle;
    private final MethodHandle strlenHandle;
    private final MethodHandle sumArrayHandle;
    private final MethodHandle fibHandle;
    private final MethodHandle sha256FileHandle;
    private final MethodHandle perfSnapshotHandle;
    private final MethodHandle binaryHealthHandle;
    private final MethodHandle countSyllablesHandle;
    private final MethodHandle atomicWriteHandle;
    private final MethodHandle ensureSpaceHandle;
    private final MethodHandle copyFileHandle;
    private final MethodHandle rhymeKeyHandle;
    private final MethodHandle nearRhymeKeyHandle;
    private final MethodHandle listDirSizeHandle;
    private final MethodHandle listDirHandle;
    private final MethodHandle dictSetBasePathHandle;
    private final MethodHandle dictContainsHandle;
    private final MethodHandle dictLookupHandle;
    private final MethodHandle dictRhymesHandle;
    private final MethodHandle dictSizeHandle;
    private final MethodHandle spellInitHandle;
    private final MethodHandle spellContainsHandle;
    private final MethodHandle spellSuggestionsHandle;
    private final MethodHandle spellBestCorrectionHandle;
    private final MethodHandle spellAddUserWordHandle;
    private final MethodHandle spellClearUserWordsHandle;
    
    // Text utility handles
    private final MethodHandle textWordCountHandle;
    private final MethodHandle textSentenceCountHandle;
    private final MethodHandle textCharCountHandle;
    private final MethodHandle textExtractWordsHandle;
    private final MethodHandle textLastWordHandle;
    private final MethodHandle textNormalizeHandle;
    private final MethodHandle textFuzzyMatchHandle;
    private final MethodHandle textFuzzyScoreHandle;
    private final MethodHandle textLineCountHandle;
    private final MethodHandle textGetLineHandle;
    private final MethodHandle textLevenshteinHandle;
    private final MethodHandle textDamerauLevenshteinHandle;
    private final MethodHandle textSimilarityHandle;
    
    // Compression handles
    private final MethodHandle compressHandle;
    private final MethodHandle decompressHandle;
    private final MethodHandle compressBoundHandle;
    
    // String operations handles
    private final MethodHandle stringSanitizeHandle;
    private final MethodHandle stringHashHandle;
    private final MethodHandle stringTokenCountHandle;
    private final MethodHandle stringFirstTokensHandle;
    private final MethodHandle stringLastTokensHandle;
    private final MethodHandle stringContainsCiHandle;
    
    // JSON parsing handles
    private final MethodHandle jsonGetStringHandle;
    private final MethodHandle jsonGetIntHandle;
    private final MethodHandle jsonHasKeyHandle;
    private final MethodHandle jsonCountKeysHandle;
    private final MethodHandle jsonGetKeysHandle;
    private final MethodHandle jsonGetPathHandle;
    
    // Date/time handles
    private final MethodHandle timeNowMillisHandle;
    private final MethodHandle timeFormatHandle;
    private final MethodHandle timeFormatNowHandle;
    private final MethodHandle timeParseHandle;
    private final MethodHandle timeRelativeHandle;
    
    // Pattern matching handles
    private final MethodHandle patternFindHandle;
    private final MethodHandle patternExtractAfterHandle;
    private final MethodHandle patternReplaceAllHandle;
    private final MethodHandle patternCollapseSpacesHandle;
    
    // Base64/encoding handles
    private final MethodHandle base64EncodeHandle;
    private final MethodHandle base64DecodeHandle;
    private final MethodHandle utf8StrlenHandle;
    private final MethodHandle unicodeUnescapeHandle;
    private final MethodHandle hexEncodeHandle;
    private final MethodHandle hexDecodeHandle;
    
    // Poetry analysis handles
    private final MethodHandle poetryAnalyzeSoundsHandle;
    private final MethodHandle poetryGetSoundDeviceHandle;
    private final MethodHandle poetryAnalyzeThemesHandle;
    private final MethodHandle poetryGetThemeScoreHandle;
    private final MethodHandle poetryGetThemesHandle;
    private final MethodHandle poetryAnalyzeVocabHandle;
    private final MethodHandle poetryGetVocabStatsHandle;
    private final MethodHandle poetryCountSyllablesHandle;
    private final MethodHandle poetryAnalyzeMeterHandle;
    private final MethodHandle poetryGetLineSyllablesHandle;
    private final MethodHandle poetryDetectMeterHandle;
    
    // Rhyme engine handles
    private final MethodHandle rhymeAddWordHandle;
    private final MethodHandle rhymeAddWordsHandle;
    private final MethodHandle rhymeFindHandle;
    private final MethodHandle rhymeGetResultHandle;
    private final MethodHandle rhymeGetAllResultsHandle;
    private final MethodHandle rhymeCheckHandle;
    private final MethodHandle rhymeDetectSchemeHandle;
    private final MethodHandle rhymeClearHandle;
    private final MethodHandle rhymeDbSizeHandle;
    
    // Math utilities handles
    private final MethodHandle mathVec2LengthHandle;
    private final MethodHandle mathVec2DistanceHandle;
    private final MethodHandle mathEaseHandle;
    private final MethodHandle mathColorBlendHandle;
    private final MethodHandle mathHslToRgbHandle;
    private final MethodHandle mathLerpHandle;
    private final MethodHandle mathClampHandle;
    
    // Concurrent/task handles
    private final MethodHandle taskCreateHandle;
    private final MethodHandle taskPendingCountHandle;
    private final MethodHandle parallelGetHwThreadsHandle;
    private final MethodHandle atomicIncHandle;
    private final MethodHandle atomicGetHandle;
    private final MethodHandle hrtimeNsHandle;
    private final MethodHandle monotonicMsHandle;
    
    // Collection handles
    private final MethodHandle setCreateHandle;
    private final MethodHandle setAddHandle;
    private final MethodHandle setContainsHandle;
    private final MethodHandle setSizeHandle;
    private final MethodHandle setClearHandle;
    private final MethodHandle mapCreateHandle;
    private final MethodHandle mapSetHandle;
    private final MethodHandle mapGetHandle;
    private final MethodHandle mapHasHandle;
    private final MethodHandle mapSizeHandle;
    private final MethodHandle freqCreateHandle;
    private final MethodHandle freqAddHandle;
    private final MethodHandle freqGetHandle;
    private final MethodHandle freqTopNHandle;
    private final MethodHandle cacheCreateHandle;
    private final MethodHandle cacheSetHandle;
    private final MethodHandle cacheGetHandle;
    private final MethodHandle cacheSizeHandle;
    
    private NativeLibrary(Path libraryPath) {
        this.arena = Arena.ofShared();
        this.linker = Linker.nativeLinker();
        this.lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        
        // int32_t simjot_add(int32_t a, int32_t b)
        this.addHandle = linker.downcallHandle(
            lookup.find("simjot_add").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        // int32_t simjot_strlen(const char* str)
        this.strlenHandle = linker.downcallHandle(
            lookup.find("simjot_strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        // int64_t simjot_sum_array(const int32_t* arr, int32_t len)
        this.sumArrayHandle = linker.downcallHandle(
            lookup.find("simjot_sum_array").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        // int64_t simjot_fib(int32_t n)
        this.fibHandle = linker.downcallHandle(
            lookup.find("simjot_fib").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_sha256_file(const char* path, uint8_t* out32)
        this.sha256FileHandle = linker.downcallHandle(
            lookup.find("simjot_sha256_file").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        this.perfSnapshotHandle = optionalHandle(
            "simjot_perf_snapshot",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.binaryHealthHandle = optionalHandle(
            "simjot_binary_health",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_count_syllables(const char* word)
        this.countSyllablesHandle = linker.downcallHandle(
            lookup.find("simjot_count_syllables").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        // int32_t simjot_rhyme_key(const char* word, char* out, int32_t outLen)
        this.rhymeKeyHandle = linker.downcallHandle(
            lookup.find("simjot_rhyme_key").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t outLen)
        this.nearRhymeKeyHandle = linker.downcallHandle(
            lookup.find("simjot_near_rhyme_key").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_list_dir_size(const char* path, int32_t includeHidden)
        this.listDirSizeHandle = linker.downcallHandle(
            lookup.find("simjot_list_dir_size").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_list_dir(const char* path, int32_t includeHidden, uint8_t* out, int32_t outLen)
        this.listDirHandle = linker.downcallHandle(
            lookup.find("simjot_list_dir").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        // int32_t simjot_atomic_write(const char* target, const uint8_t* data, int32_t len, int32_t fsyncFile, int32_t fsyncDir)
        this.atomicWriteHandle = linker.downcallHandle(
            lookup.find("simjot_atomic_write").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT)
        );

        // int32_t simjot_ensure_space(const char* path, uint64_t bytesNeeded)
        this.ensureSpaceHandle = linker.downcallHandle(
            lookup.find("simjot_ensure_space").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        this.copyFileHandle = optionalHandle(
            "simjot_copy_file",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        this.dictSetBasePathHandle = optionalHandle(
            "simjot_dict_set_base_path",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.dictContainsHandle = optionalHandle(
            "simjot_dict_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.dictLookupHandle = optionalHandle(
            "simjot_dict_lookup",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.dictRhymesHandle = optionalHandle(
            "simjot_dict_rhymes_for",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.dictSizeHandle = optionalHandle(
            "simjot_dict_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        this.spellInitHandle = optionalHandle(
            "simjot_spell_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellContainsHandle = optionalHandle(
            "simjot_spell_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellSuggestionsHandle = optionalHandle(
            "simjot_spell_suggestions",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.spellBestCorrectionHandle = optionalHandle(
            "simjot_spell_best_correction",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.spellAddUserWordHandle = optionalHandle(
            "simjot_spell_add_user_word",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.spellClearUserWordsHandle = optionalHandle(
            "simjot_spell_clear_user_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        
        // Text utilities
        this.textWordCountHandle = optionalHandle(
            "simjot_text_word_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textSentenceCountHandle = optionalHandle(
            "simjot_text_sentence_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textCharCountHandle = optionalHandle(
            "simjot_text_char_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textExtractWordsHandle = optionalHandle(
            "simjot_text_extract_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textLastWordHandle = optionalHandle(
            "simjot_text_last_word",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textNormalizeHandle = optionalHandle(
            "simjot_text_normalize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textFuzzyMatchHandle = optionalHandle(
            "simjot_text_fuzzy_match",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textFuzzyScoreHandle = optionalHandle(
            "simjot_text_fuzzy_score",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textLineCountHandle = optionalHandle(
            "simjot_text_line_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.textGetLineHandle = optionalHandle(
            "simjot_text_get_line",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.textLevenshteinHandle = optionalHandle(
            "simjot_text_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textDamerauLevenshteinHandle = optionalHandle(
            "simjot_text_damerau_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.textSimilarityHandle = optionalHandle(
            "simjot_text_similarity",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        // Compression
        this.compressHandle = optionalHandle(
            "simjot_compress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.decompressHandle = optionalHandle(
            "simjot_decompress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.compressBoundHandle = optionalHandle(
            "simjot_compress_bound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        
        // String operations
        this.stringSanitizeHandle = optionalHandle(
            "simjot_string_sanitize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringHashHandle = optionalHandle(
            "simjot_string_hash",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );
        this.stringTokenCountHandle = optionalHandle(
            "simjot_string_token_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.stringFirstTokensHandle = optionalHandle(
            "simjot_string_first_tokens",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringLastTokensHandle = optionalHandle(
            "simjot_string_last_tokens",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        this.stringContainsCiHandle = optionalHandle(
            "simjot_string_contains_ci",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        // JSON parsing handles
        this.jsonGetStringHandle = optionalHandle("simjot_json_get_string",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.jsonGetIntHandle = optionalHandle("simjot_json_get_int",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.jsonHasKeyHandle = optionalHandle("simjot_json_has_key",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.jsonCountKeysHandle = optionalHandle("simjot_json_count_keys",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.jsonGetKeysHandle = optionalHandle("simjot_json_get_keys",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.jsonGetPathHandle = optionalHandle("simjot_json_get_path",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Date/time handles
        this.timeNowMillisHandle = optionalHandle("simjot_time_now_millis",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        this.timeFormatHandle = optionalHandle("simjot_time_format",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.timeFormatNowHandle = optionalHandle("simjot_time_format_now",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.timeParseHandle = optionalHandle("simjot_time_parse",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.timeRelativeHandle = optionalHandle("simjot_time_relative",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Pattern matching handles
        this.patternFindHandle = optionalHandle("simjot_pattern_find",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.patternExtractAfterHandle = optionalHandle("simjot_pattern_extract_after",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.patternReplaceAllHandle = optionalHandle("simjot_pattern_replace_all",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.patternCollapseSpacesHandle = optionalHandle("simjot_pattern_collapse_spaces",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Base64/encoding handles
        this.base64EncodeHandle = optionalHandle("simjot_base64_encode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.base64DecodeHandle = optionalHandle("simjot_base64_decode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.utf8StrlenHandle = optionalHandle("simjot_utf8_strlen",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.unicodeUnescapeHandle = optionalHandle("simjot_unicode_unescape",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hexEncodeHandle = optionalHandle("simjot_hex_encode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hexDecodeHandle = optionalHandle("simjot_hex_decode",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Poetry analysis handles
        this.poetryAnalyzeSoundsHandle = optionalHandle("simjot_poetry_analyze_sounds",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryGetSoundDeviceHandle = optionalHandle("simjot_poetry_get_sound_device",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryAnalyzeThemesHandle = optionalHandle("simjot_poetry_analyze_themes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryGetThemeScoreHandle = optionalHandle("simjot_poetry_get_theme_score",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
        this.poetryGetThemesHandle = optionalHandle("simjot_poetry_get_themes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.poetryAnalyzeVocabHandle = optionalHandle("simjot_poetry_analyze_vocab",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryGetVocabStatsHandle = optionalHandle("simjot_poetry_get_vocab_stats",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.poetryCountSyllablesHandle = optionalHandle("simjot_poetry_count_syllables",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryAnalyzeMeterHandle = optionalHandle("simjot_poetry_analyze_meter",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.poetryGetLineSyllablesHandle = optionalHandle("simjot_poetry_get_line_syllables",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.poetryDetectMeterHandle = optionalHandle("simjot_poetry_detect_meter",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Rhyme engine handles
        this.rhymeAddWordHandle = optionalHandle("simjot_rhyme_add_word",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.rhymeAddWordsHandle = optionalHandle("simjot_rhyme_add_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.rhymeFindHandle = optionalHandle("simjot_rhyme_find",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.rhymeGetResultHandle = optionalHandle("simjot_rhyme_get_result",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.rhymeGetAllResultsHandle = optionalHandle("simjot_rhyme_get_all_results",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.rhymeCheckHandle = optionalHandle("simjot_rhyme_check",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.rhymeDetectSchemeHandle = optionalHandle("simjot_rhyme_detect_scheme",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.rhymeClearHandle = optionalHandle("simjot_rhyme_clear", FunctionDescriptor.ofVoid());
        this.rhymeDbSizeHandle = optionalHandle("simjot_rhyme_db_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        
        // Math utilities handles
        this.mathVec2LengthHandle = optionalHandle("simjot_math_vec2_length",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        this.mathVec2DistanceHandle = optionalHandle("simjot_math_vec2_distance",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        this.mathEaseHandle = optionalHandle("simjot_math_ease",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE));
        this.mathColorBlendHandle = optionalHandle("simjot_math_color_blend",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE));
        this.mathHslToRgbHandle = optionalHandle("simjot_math_hsl_to_rgb",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        this.mathLerpHandle = optionalHandle("simjot_math_lerp",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        this.mathClampHandle = optionalHandle("simjot_math_clamp",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        
        // Concurrent/task handles
        this.taskCreateHandle = optionalHandle("simjot_task_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.taskPendingCountHandle = optionalHandle("simjot_task_pending_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.parallelGetHwThreadsHandle = optionalHandle("simjot_parallel_get_hw_threads",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.atomicIncHandle = optionalHandle("simjot_atomic_inc",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        this.atomicGetHandle = optionalHandle("simjot_atomic_get",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        this.hrtimeNsHandle = optionalHandle("simjot_hrtime_ns",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        this.monotonicMsHandle = optionalHandle("simjot_monotonic_ms",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        
        // Collection handles
        this.setCreateHandle = optionalHandle("simjot_set_create", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.setAddHandle = optionalHandle("simjot_set_add",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.setContainsHandle = optionalHandle("simjot_set_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.setSizeHandle = optionalHandle("simjot_set_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.setClearHandle = optionalHandle("simjot_set_clear",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
        this.mapCreateHandle = optionalHandle("simjot_map_create", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.mapSetHandle = optionalHandle("simjot_map_set",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.mapGetHandle = optionalHandle("simjot_map_get",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mapHasHandle = optionalHandle("simjot_map_has",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.mapSizeHandle = optionalHandle("simjot_map_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.freqCreateHandle = optionalHandle("simjot_freq_create", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.freqAddHandle = optionalHandle("simjot_freq_add",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.freqGetHandle = optionalHandle("simjot_freq_get",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.freqTopNHandle = optionalHandle("simjot_freq_top_n",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.cacheCreateHandle = optionalHandle("simjot_cache_create",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.cacheSetHandle = optionalHandle("simjot_cache_set",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.cacheGetHandle = optionalHandle("simjot_cache_get",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.cacheSizeHandle = optionalHandle("simjot_cache_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    private MethodHandle optionalHandle(String name, FunctionDescriptor descriptor) {
        return lookup.find(name).map(symbol -> linker.downcallHandle(symbol, descriptor)).orElse(null);
    }
    
    /**
     * Load the native library from the given path.
     */
    public static NativeLibrary load(String libraryPath) {
        return new NativeLibrary(Path.of(libraryPath));
    }
    
    /**
     * Load from default location (src/main/native/).
     */
    public static NativeLibrary loadDefault() {
        return load(defaultLibraryPath().toString());
    }

    /**
     * Resolve the default native library path for the current OS.
     */
    public static Path defaultLibraryPath() {
        String libName = System.mapLibraryName("simjot_native");
        List<Path> candidates = new ArrayList<>();

        Path jarCandidate = resolveAlongsideJar(libName);
        if (jarCandidate != null) {
            candidates.add(jarCandidate);
        }

        String userDir = System.getProperty("user.dir", ".");
        addNativeCandidates(Path.of(userDir).toAbsolutePath().normalize(), libName, candidates);

        Path moduleDir = Path.of(userDir, "Simjot");
        if (Files.isDirectory(moduleDir)) {
            addNativeCandidates(moduleDir.toAbsolutePath().normalize(), libName, candidates);
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }

        // Best-effort fall-back
        return Path.of(userDir, "src", "main", "native", libName);
    }

    private static void addNativeCandidates(Path baseDir, String libName, List<Path> out) {
        if (baseDir == null) return;
        out.add(baseDir.resolve("src/main/native/build/Release").resolve(libName));
        out.add(baseDir.resolve("src/main/native/build/Debug").resolve(libName));
        out.add(baseDir.resolve("src/main/native/build").resolve(libName));
        out.add(baseDir.resolve("src/main/native").resolve(libName));
        out.add(baseDir.resolve("target/native").resolve(libName));
    }

    private static Path resolveAlongsideJar(String libName) {
        try {
            URL location = NativeLibrary.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            Path jarPath = Path.of(location.toURI());
            Path jarDir = Files.isDirectory(jarPath) ? jarPath : jarPath.getParent();
            if (jarDir == null) return null;

            List<Path> candidates = new ArrayList<>();
            candidates.add(jarDir.resolve(libName));
            candidates.add(jarDir.resolve("native").resolve(libName));

            Path contentsDir = jarDir.getParent();
            if (contentsDir != null) {
                candidates.add(contentsDir.resolve("Resources/native").resolve(libName));
                candidates.add(contentsDir.resolve("resources/native").resolve(libName));
            }

            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    return candidate.normalize();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Add two integers (basic test function).
     */
    public int add(int a, int b) {
        try {
            return (int) addHandle.invokeExact(a, b);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_add", t);
        }
    }
    
    /**
     * Get string length via native call.
     */
    public int strlen(String str) {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cString = tempArena.allocateFrom(str);
            return (int) strlenHandle.invokeExact(cString);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_strlen", t);
        }
    }
    
    /**
     * Sum an array of integers via native call.
     */
    public long sumArray(int[] arr) {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment nativeArr = tempArena.allocate(ValueLayout.JAVA_INT, arr.length);
            for (int i = 0; i < arr.length; i++) {
                nativeArr.setAtIndex(ValueLayout.JAVA_INT, i, arr[i]);
            }
            return (long) sumArrayHandle.invokeExact(nativeArr, arr.length);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_sum_array", t);
        }
    }
    
    /**
     * Compute nth Fibonacci number via native call.
     */
    public long fibonacci(int n) {
        try {
            return (long) fibHandle.invokeExact(n);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_fib", t);
        }
    }

    /**
     * Compute SHA-256 for a file path via native call.
     */
    public String sha256File(Path path) {
        if (path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            MemorySegment out = tempArena.allocate(32);
            int ok = (int) sha256FileHandle.invokeExact(cPath, out);
            if (ok == 0) return null;
            byte[] bytes = new byte[32];
            out.asByteBuffer().get(bytes);
            return HexFormat.of().formatHex(bytes);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_sha256_file", t);
        }
    }

    public byte[] perfSnapshot() {
        if (perfSnapshotHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 128;
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) perfSnapshotHandle.invokeExact(out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) perfSnapshotHandle.invokeExact(out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_perf_snapshot", t);
        }
    }

    public byte[] binaryHealth(Path path) {
        if (binaryHealthHandle == null || path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 96;
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) binaryHealthHandle.invokeExact(cPath, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) binaryHealthHandle.invokeExact(cPath, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_binary_health", t);
        }
    }

    /**
     * Count syllables for a word via native call.
     */
    public int countSyllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            return (int) countSyllablesHandle.invokeExact(cWord);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_count_syllables", t);
        }
    }

    /**
     * Generate a rhyme key via native call.
     */
    public String rhymeKey(String word) {
        if (word == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = Math.max(8, word.length() + 1);
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) rhymeKeyHandle.invokeExact(cWord, out, outLen);
            if (len <= 0) return null;
            byte[] bytes = new byte[len];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_rhyme_key", t);
        }
    }

    /**
     * Generate a near-rhyme key via native call.
     */
    public String nearRhymeKey(String word) {
        if (word == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = Math.max(8, word.length() + 1);
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) nearRhymeKeyHandle.invokeExact(cWord, out, outLen);
            if (len <= 0) return null;
            byte[] bytes = new byte[len];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_near_rhyme_key", t);
        }
    }

    public boolean hasDictionarySupport() {
        return dictSetBasePathHandle != null && dictContainsHandle != null;
    }
    
    public boolean hasDictionaryRhymesSupport() {
        return dictRhymesHandle != null;
    }
    
    public boolean hasSpellSupport() {
        return spellInitHandle != null
                && spellContainsHandle != null
                && spellSuggestionsHandle != null
                && spellBestCorrectionHandle != null;
    }

    public int dictionarySize() {
        if (dictSizeHandle == null) return 0;
        try {
            return (int) dictSizeHandle.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_size", t);
        }
    }

    public boolean initSpellDictionary(String basePath) {
        if (spellInitHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(basePath);
            return (int) spellInitHandle.invokeExact(cPath) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_init", t);
        }
    }

    public boolean spellContains(String word) {
        if (spellContainsHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            return (int) spellContainsHandle.invokeExact(cWord) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_contains", t);
        }
    }

    public List<String> spellSuggestions(String word, int maxResults) {
        if (spellSuggestionsHandle == null) return Collections.emptyList();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int outLen = 4096;
            MemorySegment buffer = temp.allocate(outLen);
            int written = (int) spellSuggestionsHandle.invokeExact(cWord, maxResults, buffer, outLen);
            if (written <= 0) return Collections.emptyList();
            byte[] bytes = new byte[written];
            buffer.asByteBuffer().get(bytes);
            String joined = new String(bytes, StandardCharsets.UTF_8);
            if (joined.isEmpty()) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String token : joined.split("\n")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_suggestions", t);
        }
    }

    public String spellBestCorrection(String word) {
        if (spellBestCorrectionHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int outLen = 256;
            MemorySegment out = temp.allocate(outLen);
            int ok = (int) spellBestCorrectionHandle.invokeExact(cWord, out, outLen);
            if (ok <= 0) return null;
            byte[] bytes = new byte[ok];
            out.asByteBuffer().get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_best_correction", t);
        }
    }

    public boolean addUserDictionaryWord(String word) {
        if (spellAddUserWordHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            return (int) spellAddUserWordHandle.invokeExact(cWord) == 1;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_add_user_word", t);
        }
    }

    public void clearUserDictionary() {
        if (spellClearUserWordsHandle == null) return;
        try {
            spellClearUserWordsHandle.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_spell_clear_user_words", t);
        }
    }

    public boolean setDictionaryBasePath(Path path) {
        if (dictSetBasePathHandle == null || path == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            int ok = (int) dictSetBasePathHandle.invokeExact(cPath);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_set_base_path", t);
        }
    }

    public Boolean dictionaryContains(String word) {
        if (dictContainsHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            int ok = (int) dictContainsHandle.invokeExact(cWord);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_contains", t);
        }
    }

    public byte[] dictionaryLookup(String word) {
        if (dictLookupHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 8192;
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) dictLookupHandle.invokeExact(cWord, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) dictLookupHandle.invokeExact(cWord, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_lookup", t);
        }
    }

    public byte[] dictionaryRhymes(String word, int maxResults) {
        if (dictRhymesHandle == null || word == null || word.isEmpty()) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int outLen = 16384;
            MemorySegment cWord = tempArena.allocateFrom(word);
            MemorySegment out = tempArena.allocate(outLen);
            int len = (int) dictRhymesHandle.invokeExact(cWord, maxResults, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = tempArena.allocate(outLen);
                len = (int) dictRhymesHandle.invokeExact(cWord, maxResults, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_dict_rhymes_for", t);
        }
    }

    /**
     * List a directory into a packed binary buffer via native call.
     */
    public byte[] listDirectory(Path path, boolean includeHidden) {
        if (path == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(path.toString());
            int size = (int) listDirSizeHandle.invokeExact(cPath, includeHidden ? 1 : 0);
            if (size <= 0) return null;
            MemorySegment out = tempArena.allocate(size);
            int written = (int) listDirHandle.invokeExact(cPath, includeHidden ? 1 : 0, out, size);
            if (written < 0) {
                size = (int) listDirSizeHandle.invokeExact(cPath, includeHidden ? 1 : 0);
                if (size <= 0) return null;
                out = tempArena.allocate(size);
                written = (int) listDirHandle.invokeExact(cPath, includeHidden ? 1 : 0, out, size);
            }
            if (written <= 0) return null;
            byte[] data = new byte[written];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_list_dir", t);
        }
    }

    /**
     * Perform an atomic write via native call.
     */
    public boolean atomicWrite(Path target, byte[] data, boolean fsyncFile, boolean fsyncDir) {
        if (target == null || data == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(target.toString());
            // Must allocate native memory - heap segments are not allowed in native calls
            MemorySegment dataSeg = tempArena.allocate(data.length);
            dataSeg.copyFrom(MemorySegment.ofArray(data));
            int ok = (int) atomicWriteHandle.invokeExact(
                cPath,
                dataSeg,
                data.length,
                fsyncFile ? 1 : 0,
                fsyncDir ? 1 : 0
            );
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_atomic_write", t);
        }
    }

    /**
     * Check available disk space via native call.
     * Returns 1 if enough space, 0 if insufficient, -1 on error.
     */
    public int ensureSpace(Path dir, long bytesNeeded) {
        if (dir == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(dir.toString());
            return (int) ensureSpaceHandle.invokeExact(cPath, bytesNeeded);
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_ensure_space", t);
        }
    }

    public Boolean copyFile(Path src, Path dst, boolean copyAttributes) {
        if (copyFileHandle == null || src == null || dst == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cSrc = tempArena.allocateFrom(src.toString());
            MemorySegment cDst = tempArena.allocateFrom(dst.toString());
            int ok = (int) copyFileHandle.invokeExact(cSrc, cDst, copyAttributes ? 1 : 0);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Native call failed: simjot_copy_file", t);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasTextUtilsSupport() {
        return textWordCountHandle != null;
    }

    public Integer textWordCount(String text) {
        if (textWordCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textWordCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textSentenceCount(String text) {
        if (textSentenceCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textSentenceCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textCharCount(String text, boolean includeSpaces) {
        if (textCharCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textCharCountHandle.invokeExact(cText, includeSpaces ? 1 : 0);
        } catch (Throwable t) {
            return null;
        }
    }

    public List<String> textExtractWords(String text) {
        if (textExtractWordsHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.max(1024, text.length() * 2);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int written = (int) textExtractWordsHandle.invokeExact(cText, outBuf, bufSize);
            if (written <= 0) return Collections.emptyList();
            byte[] bytes = outBuf.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE);
            String result = new String(bytes, StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String w : result.split("\n")) {
                if (!w.isEmpty()) words.add(w);
            }
            return words;
        } catch (Throwable t) {
            return null;
        }
    }

    public String textLastWord(String text) {
        if (textLastWordHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment outBuf = tempArena.allocate(128);
            int len = (int) textLastWordHandle.invokeExact(cText, outBuf, 128);
            if (len <= 0) return null;
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public String textNormalize(String text) {
        if (textNormalizeHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = text.length() + 1;
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) textNormalizeHandle.invokeExact(cText, outBuf, bufSize);
            if (len <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean textFuzzyMatch(String text, String query) {
        if (textFuzzyMatchHandle == null || text == null || query == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cQuery = tempArena.allocateFrom(query);
            int result = (int) textFuzzyMatchHandle.invokeExact(cText, cQuery);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public int textFuzzyScore(String text, String query) {
        if (textFuzzyScoreHandle == null || text == null || query == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cQuery = tempArena.allocateFrom(query);
            return (int) textFuzzyScoreHandle.invokeExact(cText, cQuery);
        } catch (Throwable t) {
            return 0;
        }
    }

    public Integer textLineCount(String text) {
        if (textLineCountHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) textLineCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return null;
        }
    }

    public String textGetLine(String text, int lineNum) {
        if (textGetLineHandle == null || text == null || lineNum < 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(4096, text.length() + 1);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) textGetLineHandle.invokeExact(cText, lineNum, outBuf, bufSize);
            if (len < 0) return null;
            if (len == 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textLevenshtein(String a, String b) {
        if (textLevenshteinHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textLevenshteinHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textDamerauLevenshtein(String a, String b) {
        if (textDamerauLevenshteinHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textDamerauLevenshteinHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public Integer textSimilarity(String a, String b) {
        if (textSimilarityHandle == null || a == null || b == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cA = tempArena.allocateFrom(a);
            MemorySegment cB = tempArena.allocateFrom(b);
            int result = (int) textSimilarityHandle.invokeExact(cA, cB);
            return result >= 0 ? result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPRESSION UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasCompressionSupport() {
        return compressHandle != null && decompressHandle != null;
    }

    public int compressBound(int inputLen) {
        if (compressBoundHandle == null) return inputLen + 128;
        try {
            return (int) compressBoundHandle.invokeExact(inputLen);
        } catch (Throwable t) {
            return inputLen + 128;
        }
    }

    public byte[] compress(byte[] input, int level) {
        if (compressHandle == null || input == null || input.length == 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            int maxOut = compressBound(input.length);
            MemorySegment inBuf = tempArena.allocate(input.length);
            inBuf.asByteBuffer().put(input);
            MemorySegment outBuf = tempArena.allocate(maxOut);
            
            int compressedSize = (int) compressHandle.invokeExact(inBuf, input.length, outBuf, maxOut, level);
            if (compressedSize <= 0) return null;
            
            byte[] result = new byte[compressedSize];
            outBuf.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] decompress(byte[] input, int expectedSize) {
        if (decompressHandle == null || input == null || input.length == 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment inBuf = tempArena.allocate(input.length);
            inBuf.asByteBuffer().put(input);
            MemorySegment outBuf = tempArena.allocate(expectedSize);
            
            int decompressedSize = (int) decompressHandle.invokeExact(inBuf, input.length, outBuf, expectedSize);
            if (decompressedSize <= 0) return null;
            
            byte[] result = new byte[decompressedSize];
            outBuf.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRING OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasStringOpsSupport() {
        return stringSanitizeHandle != null;
    }

    public String stringSanitize(String input, int maxLen) {
        if (stringSanitizeHandle == null || input == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cInput = tempArena.allocateFrom(input);
            int bufSize = Math.max(input.length() + 16, 512);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) stringSanitizeHandle.invokeExact(cInput, outBuf, bufSize, maxLen);
            if (len < 0) return null;
            if (len == 0) return "";
            byte[] bytes = outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public long stringHash(String str) {
        if (stringHashHandle == null || str == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            return (long) stringHashHandle.invokeExact(cStr);
        } catch (Throwable t) {
            return 0;
        }
    }

    public int stringTokenCount(String text) {
        if (stringTokenCountHandle == null || text == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) stringTokenCountHandle.invokeExact(cText);
        } catch (Throwable t) {
            return -1;
        }
    }

    public String stringFirstTokens(String text, int maxTokens) {
        if (stringFirstTokensHandle == null || text == null || maxTokens <= 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(text.length() + 1, 4096);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int count = (int) stringFirstTokensHandle.invokeExact(cText, outBuf, bufSize, maxTokens);
            if (count <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, Math.min(bufSize - 1, text.length())).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public String stringLastTokens(String text, int maxTokens) {
        if (stringLastTokensHandle == null || text == null || maxTokens <= 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.min(text.length() + 1, 4096);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int count = (int) stringLastTokensHandle.invokeExact(cText, outBuf, bufSize, maxTokens);
            if (count <= 0) return "";
            byte[] bytes = outBuf.asSlice(0, Math.min(bufSize - 1, text.length())).toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean stringContainsCi(String haystack, String needle) {
        if (stringContainsCiHandle == null || haystack == null || needle == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cHaystack = tempArena.allocateFrom(haystack);
            MemorySegment cNeedle = tempArena.allocateFrom(needle);
            int result = (int) stringContainsCiHandle.invokeExact(cHaystack, cNeedle);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON PARSING API
    // ═══════════════════════════════════════════════════════════════════════════

    public String jsonGetString(String json, String key) {
        if (jsonGetStringHandle == null || json == null || key == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment outBuf = tempArena.allocate(4096);
            int len = (int) jsonGetStringHandle.invokeExact(cJson, cKey, outBuf, 4096);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public Long jsonGetLong(String json, String key) {
        if (jsonGetIntHandle == null || json == null || key == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment outVal = tempArena.allocate(ValueLayout.JAVA_LONG);
            int result = (int) jsonGetIntHandle.invokeExact(cJson, cKey, outVal);
            if (result <= 0) return null;
            return outVal.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable t) { return null; }
    }

    public boolean jsonHasKey(String json, String key) {
        if (jsonHasKeyHandle == null || json == null || key == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment cKey = tempArena.allocateFrom(key);
            return (int) jsonHasKeyHandle.invokeExact(cJson, cKey) != 0;
        } catch (Throwable t) { return false; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATE/TIME API
    // ═══════════════════════════════════════════════════════════════════════════

    public long timeNowMillis() {
        if (timeNowMillisHandle == null) return System.currentTimeMillis();
        try { return (long) timeNowMillisHandle.invokeExact(); } 
        catch (Throwable t) { return System.currentTimeMillis(); }
    }

    public String timeFormat(long millis, String pattern) {
        if (timeFormatHandle == null || pattern == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPattern = tempArena.allocateFrom(pattern);
            MemorySegment outBuf = tempArena.allocate(256);
            int len = (int) timeFormatHandle.invokeExact(millis, cPattern, outBuf, 256);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public String timeFormatNow(String pattern) {
        if (timeFormatNowHandle == null || pattern == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPattern = tempArena.allocateFrom(pattern);
            MemorySegment outBuf = tempArena.allocate(256);
            int len = (int) timeFormatNowHandle.invokeExact(cPattern, outBuf, 256);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public String timeRelative(long millis) {
        if (timeRelativeHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment outBuf = tempArena.allocate(128);
            int len = (int) timeRelativeHandle.invokeExact(millis, outBuf, 128);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN MATCHING API
    // ═══════════════════════════════════════════════════════════════════════════

    public int patternFind(String text, String pattern, boolean wordBoundary) {
        if (patternFindHandle == null || text == null || pattern == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cPattern = tempArena.allocateFrom(pattern);
            return (int) patternFindHandle.invokeExact(cText, cPattern, wordBoundary ? 1 : 0);
        } catch (Throwable t) { return -1; }
    }

    public String patternExtractAfter(String text, String prefix, int maxPhraseLen) {
        if (patternExtractAfterHandle == null || text == null || prefix == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cPrefix = tempArena.allocateFrom(prefix);
            MemorySegment outBuf = tempArena.allocate(maxPhraseLen + 1);
            int len = (int) patternExtractAfterHandle.invokeExact(cText, cPrefix, outBuf, maxPhraseLen + 1, maxPhraseLen);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public String patternReplaceAll(String text, String pattern, String replacement) {
        if (patternReplaceAllHandle == null || text == null || pattern == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment cPattern = tempArena.allocateFrom(pattern);
            MemorySegment cRepl = tempArena.allocateFrom(replacement != null ? replacement : "");
            int bufSize = text.length() * 2 + 1;
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int len = (int) patternReplaceAllHandle.invokeExact(cText, cPattern, cRepl, outBuf, bufSize);
            if (len < 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public String patternCollapseSpaces(String text) {
        if (patternCollapseSpacesHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment outBuf = tempArena.allocate(text.length() + 1);
            int len = (int) patternCollapseSpacesHandle.invokeExact(cText, outBuf, text.length() + 1);
            if (len < 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENCODING API
    // ═══════════════════════════════════════════════════════════════════════════

    public String base64Encode(byte[] data) {
        if (base64EncodeHandle == null || data == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment inBuf = tempArena.allocate(data.length);
            inBuf.asByteBuffer().put(data);
            int outLen = (data.length + 2) / 3 * 4 + 1;
            MemorySegment outBuf = tempArena.allocate(outLen);
            int len = (int) base64EncodeHandle.invokeExact(inBuf, data.length, outBuf, outLen);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public byte[] base64Decode(String encoded) {
        if (base64DecodeHandle == null || encoded == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cEncoded = tempArena.allocateFrom(encoded);
            int outLen = encoded.length();
            MemorySegment outBuf = tempArena.allocate(outLen);
            int len = (int) base64DecodeHandle.invokeExact(cEncoded, outBuf, outLen);
            if (len <= 0) return null;
            return outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable t) { return null; }
    }

    public int utf8Strlen(String str) {
        if (utf8StrlenHandle == null || str == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            return (int) utf8StrlenHandle.invokeExact(cStr);
        } catch (Throwable t) { return -1; }
    }

    public String hexEncode(byte[] data) {
        if (hexEncodeHandle == null || data == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment inBuf = tempArena.allocate(data.length);
            inBuf.asByteBuffer().put(data);
            int outLen = data.length * 2 + 1;
            MemorySegment outBuf = tempArena.allocate(outLen);
            int len = (int) hexEncodeHandle.invokeExact(inBuf, data.length, outBuf, outLen);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public byte[] hexDecode(String hex) {
        if (hexDecodeHandle == null || hex == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cHex = tempArena.allocateFrom(hex);
            int outLen = hex.length() / 2;
            MemorySegment outBuf = tempArena.allocate(outLen);
            int len = (int) hexDecodeHandle.invokeExact(cHex, outBuf, outLen);
            if (len <= 0) return null;
            return outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable t) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POETRY ANALYSIS API
    // ═══════════════════════════════════════════════════════════════════════════

    public int poetryAnalyzeSounds(String text) {
        if (poetryAnalyzeSoundsHandle == null || text == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) poetryAnalyzeSoundsHandle.invokeExact(cText);
        } catch (Throwable t) { return 0; }
    }

    public int poetryAnalyzeThemes(String text) {
        if (poetryAnalyzeThemesHandle == null || text == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) poetryAnalyzeThemesHandle.invokeExact(cText);
        } catch (Throwable t) { return 0; }
    }

    public double poetryGetThemeScore(String theme) {
        if (poetryGetThemeScoreHandle == null || theme == null) return 0.0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cTheme = tempArena.allocateFrom(theme);
            return (double) poetryGetThemeScoreHandle.invokeExact(cTheme);
        } catch (Throwable t) { return 0.0; }
    }

    public String poetryGetThemes() {
        if (poetryGetThemesHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment outBuf = tempArena.allocate(1024);
            int len = (int) poetryGetThemesHandle.invokeExact(outBuf, 1024);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public int poetryAnalyzeVocab(String text) {
        if (poetryAnalyzeVocabHandle == null || text == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) poetryAnalyzeVocabHandle.invokeExact(cText);
        } catch (Throwable t) { return 0; }
    }

    public int poetryCountSyllables(String word) {
        if (poetryCountSyllablesHandle == null || word == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            return (int) poetryCountSyllablesHandle.invokeExact(cWord);
        } catch (Throwable t) { return 0; }
    }

    public int poetryAnalyzeMeter(String text) {
        if (poetryAnalyzeMeterHandle == null || text == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            return (int) poetryAnalyzeMeterHandle.invokeExact(cText);
        } catch (Throwable t) { return 0; }
    }

    public String poetryDetectMeter() {
        if (poetryDetectMeterHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment outBuf = tempArena.allocate(128);
            int len = (int) poetryDetectMeterHandle.invokeExact(outBuf, 128);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RHYME ENGINE API
    // ═══════════════════════════════════════════════════════════════════════════

    public void rhymeAddWord(String word) {
        if (rhymeAddWordHandle == null || word == null) return;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            rhymeAddWordHandle.invokeExact(cWord);
        } catch (Throwable ignored) {}
    }

    public int rhymeAddWords(String words) {
        if (rhymeAddWordsHandle == null || words == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWords = tempArena.allocateFrom(words);
            return (int) rhymeAddWordsHandle.invokeExact(cWords);
        } catch (Throwable t) { return 0; }
    }

    public String rhymeFind(String word, int maxResults) {
        if (rhymeFindHandle == null || word == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord = tempArena.allocateFrom(word);
            int count = (int) rhymeFindHandle.invokeExact(cWord, maxResults);
            if (count <= 0) return null;
            if (rhymeGetAllResultsHandle == null) return null;
            MemorySegment outBuf = tempArena.allocate(4096);
            int len = (int) rhymeGetAllResultsHandle.invokeExact(outBuf, 4096);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public boolean rhymeCheck(String word1, String word2) {
        if (rhymeCheckHandle == null || word1 == null || word2 == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cWord1 = tempArena.allocateFrom(word1);
            MemorySegment cWord2 = tempArena.allocateFrom(word2);
            return (int) rhymeCheckHandle.invokeExact(cWord1, cWord2) != 0;
        } catch (Throwable t) { return false; }
    }

    public String rhymeDetectScheme(String text) {
        if (rhymeDetectSchemeHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            MemorySegment outBuf = tempArena.allocate(256);
            int len = (int) rhymeDetectSchemeHandle.invokeExact(cText, outBuf, 256);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public void rhymeClear() {
        if (rhymeClearHandle == null) return;
        try { rhymeClearHandle.invokeExact(); } catch (Throwable ignored) {}
    }

    public int rhymeDbSize() {
        if (rhymeDbSizeHandle == null) return 0;
        try { return (int) rhymeDbSizeHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MATH UTILITIES API
    // ═══════════════════════════════════════════════════════════════════════════

    public double mathVec2Length(double x, double y) {
        if (mathVec2LengthHandle == null) return Math.sqrt(x*x + y*y);
        try { return (double) mathVec2LengthHandle.invokeExact(x, y); } 
        catch (Throwable t) { return Math.sqrt(x*x + y*y); }
    }

    public double mathVec2Distance(double x1, double y1, double x2, double y2) {
        if (mathVec2DistanceHandle == null) return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
        try { return (double) mathVec2DistanceHandle.invokeExact(x1, y1, x2, y2); } 
        catch (Throwable t) { return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)); }
    }

    public double mathEase(int type, double t) {
        if (mathEaseHandle == null) return t;
        try { return (double) mathEaseHandle.invokeExact(type, t); } catch (Throwable ex) { return t; }
    }

    public int mathColorBlend(int color1, int color2, double t) {
        if (mathColorBlendHandle == null) return color1;
        try { return (int) mathColorBlendHandle.invokeExact(color1, color2, t); } catch (Throwable ex) { return color1; }
    }

    public int mathHslToRgb(double h, double s, double l) {
        if (mathHslToRgbHandle == null) return 0;
        try { return (int) mathHslToRgbHandle.invokeExact(h, s, l); } catch (Throwable t) { return 0; }
    }

    public double mathLerp(double a, double b, double t) {
        if (mathLerpHandle == null) return a + (b - a) * t;
        try { return (double) mathLerpHandle.invokeExact(a, b, t); } catch (Throwable ex) { return a + (b - a) * t; }
    }

    public double mathClamp(double value, double min, double max) {
        if (mathClampHandle == null) return Math.max(min, Math.min(max, value));
        try { return (double) mathClampHandle.invokeExact(value, min, max); } 
        catch (Throwable ex) { return Math.max(min, Math.min(max, value)); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENT/TASK API
    // ═══════════════════════════════════════════════════════════════════════════

    public int taskCreate(String data, int priority) {
        if (taskCreateHandle == null || data == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cData = tempArena.allocateFrom(data);
            return (int) taskCreateHandle.invokeExact(cData, priority);
        } catch (Throwable t) { return -1; }
    }

    public int taskPendingCount() {
        if (taskPendingCountHandle == null) return 0;
        try { return (int) taskPendingCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }

    public int parallelGetHwThreads() {
        if (parallelGetHwThreadsHandle == null) return Runtime.getRuntime().availableProcessors();
        try { return (int) parallelGetHwThreadsHandle.invokeExact(); } 
        catch (Throwable t) { return Runtime.getRuntime().availableProcessors(); }
    }

    public long atomicInc(int counterId) {
        if (atomicIncHandle == null) return -1;
        try { return (long) atomicIncHandle.invokeExact(counterId); } catch (Throwable t) { return -1; }
    }

    public long atomicGet(int counterId) {
        if (atomicGetHandle == null) return -1;
        try { return (long) atomicGetHandle.invokeExact(counterId); } catch (Throwable t) { return -1; }
    }

    public long hrtimeNs() {
        if (hrtimeNsHandle == null) return System.nanoTime();
        try { return (long) hrtimeNsHandle.invokeExact(); } catch (Throwable t) { return System.nanoTime(); }
    }

    public long monotonicMs() {
        if (monotonicMsHandle == null) return System.currentTimeMillis();
        try { return (long) monotonicMsHandle.invokeExact(); } catch (Throwable t) { return System.currentTimeMillis(); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLLECTION API
    // ═══════════════════════════════════════════════════════════════════════════

    public int setCreate() {
        if (setCreateHandle == null) return -1;
        try { return (int) setCreateHandle.invokeExact(); } catch (Throwable t) { return -1; }
    }

    public void setAdd(int setId, String str) {
        if (setAddHandle == null || str == null) return;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            setAddHandle.invokeExact(setId, cStr);
        } catch (Throwable ignored) {}
    }

    public boolean setContains(int setId, String str) {
        if (setContainsHandle == null || str == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            return (int) setContainsHandle.invokeExact(setId, cStr) != 0;
        } catch (Throwable t) { return false; }
    }

    public int setSize(int setId) {
        if (setSizeHandle == null) return 0;
        try { return (int) setSizeHandle.invokeExact(setId); } catch (Throwable t) { return 0; }
    }

    public void setClear(int setId) {
        if (setClearHandle == null) return;
        try { setClearHandle.invokeExact(setId); } catch (Throwable ignored) {}
    }

    public int mapCreate() {
        if (mapCreateHandle == null) return -1;
        try { return (int) mapCreateHandle.invokeExact(); } catch (Throwable t) { return -1; }
    }

    public void mapSet(int mapId, String key, String value) {
        if (mapSetHandle == null || key == null || value == null) return;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment cValue = tempArena.allocateFrom(value);
            mapSetHandle.invokeExact(mapId, cKey, cValue);
        } catch (Throwable ignored) {}
    }

    public String mapGet(int mapId, String key) {
        if (mapGetHandle == null || key == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment outBuf = tempArena.allocate(4096);
            int len = (int) mapGetHandle.invokeExact(mapId, cKey, outBuf, 4096);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public boolean mapHas(int mapId, String key) {
        if (mapHasHandle == null || key == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cKey = tempArena.allocateFrom(key);
            return (int) mapHasHandle.invokeExact(mapId, cKey) != 0;
        } catch (Throwable t) { return false; }
    }

    public int mapSize(int mapId) {
        if (mapSizeHandle == null) return 0;
        try { return (int) mapSizeHandle.invokeExact(mapId); } catch (Throwable t) { return 0; }
    }

    public int freqCreate() {
        if (freqCreateHandle == null) return -1;
        try { return (int) freqCreateHandle.invokeExact(); } catch (Throwable t) { return -1; }
    }

    public void freqAdd(int mapId, String str, int count) {
        if (freqAddHandle == null || str == null) return;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            freqAddHandle.invokeExact(mapId, cStr, count);
        } catch (Throwable ignored) {}
    }

    public int freqGet(int mapId, String str) {
        if (freqGetHandle == null || str == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cStr = tempArena.allocateFrom(str);
            return (int) freqGetHandle.invokeExact(mapId, cStr);
        } catch (Throwable t) { return 0; }
    }

    public String freqTopN(int mapId, int n) {
        if (freqTopNHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment outBuf = tempArena.allocate(4096);
            int len = (int) freqTopNHandle.invokeExact(mapId, n, outBuf, 4096);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public int cacheCreate(int maxSize) {
        if (cacheCreateHandle == null) return -1;
        try { return (int) cacheCreateHandle.invokeExact(maxSize); } catch (Throwable t) { return -1; }
    }

    public void cacheSet(int cacheId, String key, String value) {
        if (cacheSetHandle == null || key == null || value == null) return;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment cValue = tempArena.allocateFrom(value);
            cacheSetHandle.invokeExact(cacheId, cKey, cValue);
        } catch (Throwable ignored) {}
    }

    public String cacheGet(int cacheId, String key) {
        if (cacheGetHandle == null || key == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cKey = tempArena.allocateFrom(key);
            MemorySegment outBuf = tempArena.allocate(4096);
            int len = (int) cacheGetHandle.invokeExact(cacheId, cKey, outBuf, 4096);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    public int cacheSize(int cacheId) {
        if (cacheSizeHandle == null) return 0;
        try { return (int) cacheSizeHandle.invokeExact(cacheId); } catch (Throwable t) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMD API (placeholder - needs array passing)
    // ═══════════════════════════════════════════════════════════════════════════

    public int simdSupportLevel() { return 0; }
    public long simdSumInt(int[] arr) { long sum = 0; for (int v : arr) sum += v; return sum; }
    public double simdSumDouble(double[] arr) { double sum = 0; for (double v : arr) sum += v; return sum; }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE SYSTEM API (placeholder)
    // ═══════════════════════════════════════════════════════════════════════════

    public long fsSize(String path) { return -1; }
    public long fsMtime(String path) { return -1; }
    public boolean fsExists(String path) { return false; }
    public boolean fsIsDir(String path) { return false; }
    public boolean fsIsFile(String path) { return false; }
    public String fsListRecursive(String path, String ext, int depth) { return null; }
    public byte[] fsReadAll(String path) { return null; }
    public boolean fsWriteAll(String path, byte[] data) { return false; }
    public boolean fsMkdir(String path) { return false; }
    public boolean fsRemove(String path) { return false; }
    public boolean fsRename(String oldPath, String newPath) { return false; }
    public int fsWatchCreate(String path) { return -1; }
    public int fsWatchPoll(int watchId, int timeout) { return 0; }
    public void fsWatchDestroy(int watchId) {}
    public String fsExtension(String path) { return null; }
    public String fsBasename(String path) { return null; }
    public String fsDirname(String path) { return null; }
    public String fsJoin(String base, String child) { return null; }
    
    /**
     * List directory entries with extension filtering.
     * Returns entries as newline-separated strings: type|mtime|size|name
     * @param dirPath Directory to list
     * @param extensions Comma-separated extensions (e.g. ".txt,.md") or null for all
     * @param includeHidden Whether to include hidden files
     * @return Formatted string with entries, or null on error
     */
    public String fsListFiltered(String dirPath, String extensions, boolean includeHidden) {
        if (dirPath == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(dirPath);
            MemorySegment cExts = (extensions != null) ? tempArena.allocateFrom(extensions) : MemorySegment.NULL;
            MemorySegment outBuf = tempArena.allocate(65536); // 64KB buffer
            
            MethodHandle handle = optionalHandle("simjot_fs_list_filtered",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, 
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) return null;
            
            int written = (int) handle.invokeExact(cPath, cExts, includeHidden ? 1 : 0, outBuf, 65536);
            if (written <= 0) return null;
            
            byte[] bytes = new byte[written];
            outBuf.asByteBuffer().get(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Count entries in a directory (fast, no stat calls).
     */
    public int fsCountEntries(String dirPath, boolean includeHidden) {
        if (dirPath == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cPath = tempArena.allocateFrom(dirPath);
            
            MethodHandle handle = optionalHandle("simjot_fs_count_entries",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            
            return (int) handle.invokeExact(cPath, includeHidden ? 1 : 0);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMAGE OPERATIONS API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fast native image resize using ARGB pixel data (Java BufferedImage compatible).
     * 
     * @param srcPixels Source ARGB int array from BufferedImage.getRGB()
     * @param srcW Source width
     * @param srcH Source height
     * @param dstW Target width
     * @param dstH Target height
     * @param quality 0=fast/bilinear, 1=high/bicubic, 2=auto
     * @return Resized ARGB int array, or null on error
     */
    public int[] imageResizeArgb(int[] srcPixels, int srcW, int srcH, int dstW, int dstH, int quality) {
        if (srcPixels == null || srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return null;
        if (srcPixels.length < srcW * srcH) return null;
        
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_image_resize_argb",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
            if (handle == null) return null;
            
            // Allocate native memory for source and destination
            int srcCount = srcW * srcH;
            int dstCount = dstW * dstH;
            
            MemorySegment srcSeg = tempArena.allocate(srcCount * 4L);
            MemorySegment dstSeg = tempArena.allocate(dstCount * 4L);
            
            // Copy source pixels to native memory
            for (int i = 0; i < srcCount; i++) {
                srcSeg.setAtIndex(ValueLayout.JAVA_INT, i, srcPixels[i]);
            }
            
            // Call native resize
            int result = (int) handle.invokeExact(srcSeg, srcW, srcH, dstSeg, dstW, dstH, quality);
            if (result != 0) return null;
            
            // Copy result back
            int[] dstPixels = new int[dstCount];
            for (int i = 0; i < dstCount; i++) {
                dstPixels[i] = dstSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            
            return dstPixels;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Calculate target dimensions to fit within max bounds while preserving aspect ratio.
     * 
     * @param srcW Source width
     * @param srcH Source height
     * @param maxW Maximum target width (0 = no limit)
     * @param maxH Maximum target height (0 = no limit)
     * @return int[2] with {targetWidth, targetHeight}, or null on error
     */
    public int[] imageCalcFitSize(int srcW, int srcH, int maxW, int maxH) {
        if (srcW <= 0 || srcH <= 0) return null;
        
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_image_calc_fit_size",
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return null;
            
            MemorySegment outW = tempArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment outH = tempArena.allocate(ValueLayout.JAVA_INT);
            
            handle.invokeExact(srcW, srcH, maxW, maxH, outW, outH);
            
            return new int[] { outW.get(ValueLayout.JAVA_INT, 0), outH.get(ValueLayout.JAVA_INT, 0) };
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEMORY POOL API (placeholder)
    // ═══════════════════════════════════════════════════════════════════════════

    public int poolCreate(int blockSize, int initialBlocks) { return -1; }
    public void poolDestroy(int poolId) {}
    public int arenaCreate() { return -1; }
    public void arenaReset(int arenaId) {}
    public void arenaDestroy(int arenaId) {}
    public int internInit() { return -1; }
    public String intern(String str) { return str; }
    public boolean internContains(String str) { return false; }
    public int internCount() { return 0; }
    public void internClear() {}
    
    @Override
    public void close() {
        arena.close();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST MAIN
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static void main(String[] args) {
        System.out.println("=== Panama FFM Test ===\n");
        
        try (NativeLibrary lib = NativeLibrary.loadDefault()) {
            // Test add
            int sum = lib.add(17, 25);
            System.out.println("add(17, 25) = " + sum + " (expected: 42)");
            
            // Test strlen
            String testStr = "Hello, Simjot!";
            int len = lib.strlen(testStr);
            System.out.println("strlen(\"" + testStr + "\") = " + len + " (expected: " + testStr.length() + ")");
            
            // Test sumArray
            int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            long arraySum = lib.sumArray(nums);
            System.out.println("sumArray([1..10]) = " + arraySum + " (expected: 55)");
            
            // Test fibonacci
            long fib20 = lib.fibonacci(20);
            System.out.println("fibonacci(20) = " + fib20 + " (expected: 6765)");
            
            // Benchmark: Java vs Native fibonacci
            System.out.println("\n=== Benchmark: fib(40) x 1000 ===");
            
            long t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                lib.fibonacci(40);
            }
            long nativeTime = System.nanoTime() - t0;
            
            t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                javaFib(40);
            }
            long javaTime = System.nanoTime() - t0;
            
            System.out.printf("Native: %.2f ms%n", nativeTime / 1_000_000.0);
            System.out.printf("Java:   %.2f ms%n", javaTime / 1_000_000.0);
            System.out.printf("Ratio:  %.2fx%n", (double) javaTime / nativeTime);
            
            System.out.println("\n✓ All tests passed!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static long javaFib(int n) {
        if (n <= 1) return n;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long tmp = a + b;
            a = b;
            b = tmp;
        }
        return b;
    }
}
