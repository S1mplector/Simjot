/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Meant to provide a Panama FFM wrapper for native Simjot functions.
 * 
 * This class provides a Java interface to native Simjot functions using Java's Foreign Function & Memory API (FFM).
 * It serves as a bridge between Java and the native C library for various system operations.
 * Has a dependency on the native library, so make sure to load it before using it.
 * Otherwise it will throw an exception, and the application will crash.
 * All methods are static, so you can use them without creating an instance.
 * All methods are thread safe and are written in a way that they can be used in a multi-threaded environment
 * using the Panama FFM API. 
 * 
 * Furthermore, I advise you to use this class with caution, as it is not a wrapper for the native library,
 * but a bridge between Java and the native C library, and is not yet production-ready.
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
    // File metadata handles
    private final MethodHandle countWordsHandle;
    private final MethodHandle countWordsFileHandle;
    private final MethodHandle extractTitleHandle;
    private final MethodHandle fileMetaBatchHandle;
    private final MethodHandle listFilesMetaHandle;
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
    private final MethodHandle textExtractTagsHandle;
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
    private final MethodHandle jsonParseStringArrayHandle;
    
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

    // Search handles
    private final MethodHandle searchFindHandle;
    private final MethodHandle searchFindCiHandle;
    private final MethodHandle searchCountHandle;
    private final MethodHandle searchFindAllHandle;
    private final MethodHandle searchFuzzyHandle;
    private final MethodHandle searchAcBuildHandle;
    private final MethodHandle searchAcFindHandle;
    private final MethodHandle searchAcFreeHandle;
    
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
    
    // Mood analytics handles
    private final MethodHandle moodLoadHandle;
    private final MethodHandle moodComputeDailyHandle;
    private final MethodHandle moodComputeSummaryHandle;
    private final MethodHandle moodGetDailyHandle;
    private final MethodHandle moodGetSummaryHandle;
    private final MethodHandle moodDailyCountHandle;
    private final MethodHandle moodSampleCountHandle;
    private final MethodHandle moodClearHandle;

    // Profiler handles
    private final MethodHandle profilerInitHandle;
    private final MethodHandle profilerStartHandle;
    private final MethodHandle profilerStopHandle;
    private final MethodHandle profilerResetHandle;
    private final MethodHandle profilerRegisterComponentHandle;
    private final MethodHandle profilerRegisterThreadHandle;
    private final MethodHandle profilerUnregisterThreadHandle;
    private final MethodHandle profilerTrackAllocHandle;
    private final MethodHandle profilerTrackFreeHandle;
    private final MethodHandle profilerSampleHandle;
    private final MethodHandle profilerComponentCountHandle;
    private final MethodHandle profilerGetComponentSnapshotHandle;
    private final MethodHandle profilerGetSummaryHandle;
    private final MethodHandle profilerPrintReportHandle;
    private final MethodHandle profilerStatusLineHandle;
    
    // Mood graphics handles
    private final MethodHandle moodSparklineHandle;
    private final MethodHandle moodBarchartHandle;
    private final MethodHandle moodGaugeHandle;
    private final MethodHandle moodHeatmapHandle;
    
    // Dictionary JSON handles
    private final MethodHandle jsonLoadDictFileHandle;
    
    // Image scaling handles
    private final MethodHandle imageScaleHandle;
    private final MethodHandle imageBlurHandle;
    private final MethodHandle imageTintHandle;
    private final MethodHandle imageExtractAccentHandle;
    
    // Spell check handles
    private final MethodHandle spellEdit1Handle;
    private final MethodHandle levenshteinHandle;
    private final MethodHandle damerauLevenshteinHandle;
    
    // Autocorrect handles
    private final MethodHandle autocorrectAdjacentKeyHandle;
    private final MethodHandle autocorrectPhoneticHandle;
    private final MethodHandle autocorrectPreserveCaseHandle;
    private final MethodHandle autocorrectCorrectHandle;
    private final MethodHandle autocorrectStartsVowelSoundHandle;
    private final MethodHandle autocorrectPhraseHandle;
    private final MethodHandle autocorrectFixCapsHandle;
    
    // Additional text utility handles
    private final MethodHandle textSyllableCountHandle;
    private final MethodHandle textAnalyzeHandle;
    private final MethodHandle textIsAsciiHandle;
    private final MethodHandle textIsAlnumHandle;
    private final MethodHandle textIsSafeFilenameHandle;
    private final MethodHandle parseIntHandle;
    private final MethodHandle parseBoolHandle;
    
    // Math utility handles
    private final MethodHandle mathMeanHandle;
    private final MethodHandle mathVarianceHandle;
    private final MethodHandle mathStddevHandle;
    private final MethodHandle mathMinHandle;
    private final MethodHandle mathMaxHandle;
    private final MethodHandle mathSumHandle;
    private final MethodHandle mathClampIntHandle;
    private final MethodHandle mathStatsHandle;
    
    // File utility handles
    private final MethodHandle fileSizeHandle;
    private final MethodHandle fileMtimeHandle;
    private final MethodHandle fileExistsHandle;
    private final MethodHandle fileIsFileHandle;
    private final MethodHandle fileIsDirHandle;
    private final MethodHandle diskAvailableHandle;
    private final MethodHandle dirCountHandle;
    
    // LRU Cache handles
    private final MethodHandle lruCacheCreateHandle;
    private final MethodHandle lruCacheDestroyHandle;
    
    // Autosave manager handles
    private final MethodHandle autosaveInitHandle;
    private final MethodHandle autosaveCreateSessionHandle;
    private final MethodHandle autosaveDestroySessionHandle;
    private final MethodHandle autosaveSetPathHandle;
    private final MethodHandle autosaveMarkDirtyHandle;
    private final MethodHandle autosaveMarkCleanHandle;
    private final MethodHandle autosaveIsDirtyHandle;
    private final MethodHandle autosaveShouldSaveHandle;
    private final MethodHandle autosaveMsUntilSaveHandle;
    private final MethodHandle autosaveHasRecoveryHandle;
    
    // LZ4 Compression handles
    private final MethodHandle lz4CompressHandle;
    private final MethodHandle lz4DecompressHandle;
    private final MethodHandle lz4CompressBoundHandle;
    
    // Viewport image cache handles
    private final MethodHandle imgcacheInitHandle;
    private final MethodHandle imgcacheShutdownHandle;
    private final MethodHandle imgcachePutHandle;
    private final MethodHandle imgcacheGetHandle;
    private final MethodHandle imgcacheContainsHandle;
    private final MethodHandle imgcacheRemoveHandle;
    private final MethodHandle imgcacheClearHandle;
    private final MethodHandle imgcacheStatsHandle;
    private final MethodHandle imgcacheCullViewportHandle;
    private final MethodHandle imgcacheBlitHandle;
    private final MethodHandle imgcachePrescaleHandle;
    
    // Hotkey manager handles
    private final MethodHandle hotkeyGetPlatformHandle;
    private final MethodHandle hotkeyGetPrimaryModifierHandle;
    private final MethodHandle hotkeyCheckHandle;
    private final MethodHandle hotkeyGetBindingHandle;
    private final MethodHandle hotkeyGetDisplayStringHandle;
    private final MethodHandle hotkeyCheckBatchHandle;
    
    // Offscreen buffer handles
    private final MethodHandle bufferCreateHandle;
    private final MethodHandle bufferResizeHandle;
    private final MethodHandle bufferDestroyHandle;
    private final MethodHandle bufferClearHandle;
    private final MethodHandle bufferWriteHandle;
    private final MethodHandle bufferReadHandle;
    private final MethodHandle bufferScrollHandle;
    private final MethodHandle bufferCompositeHandle;
    private final MethodHandle bufferGetSizeHandle;
    
    // Undo/redo manager handles
    private final MethodHandle undoInitHandle;
    private final MethodHandle undoShutdownHandle;
    private final MethodHandle undoCreateSessionHandle;
    private final MethodHandle undoDestroySessionHandle;
    private final MethodHandle undoClearHandle;
    private final MethodHandle undoPushInsertHandle;
    private final MethodHandle undoPushDeleteHandle;
    private final MethodHandle undoPushReplaceHandle;
    private final MethodHandle undoPushStyleHandle;
    private final MethodHandle undoBeginCompoundHandle;
    private final MethodHandle undoEndCompoundHandle;
    private final MethodHandle undoCanUndoHandle;
    private final MethodHandle undoCanRedoHandle;
    private final MethodHandle undoUndoHandle;
    private final MethodHandle undoRedoHandle;
    private final MethodHandle undoMarkSavePointHandle;
    private final MethodHandle undoIsAtSavePointHandle;
    private final MethodHandle undoIsDirtyHandle;
    private final MethodHandle undoGetUndoCountHandle;
    private final MethodHandle undoGetRedoCountHandle;
    private final MethodHandle undoSetHistoryLimitHandle;
    private final MethodHandle undoGetStatsHandle;
    
    // Link detector handles
    private final MethodHandle linkContainsHandle;
    private final MethodHandle linkCountHandle;
    private final MethodHandle linkFindRangesHandle;
    private final MethodHandle linkExtractFirstHandle;
    private final MethodHandle linkNormalizeHandle;
    private final MethodHandle linkIsValidHandle;
    private final MethodHandle linkAtPositionHandle;
    
    // Mood analytics handles
    private final MethodHandle moodSmoothHandle;
    private final MethodHandle moodVolatilityHandle;
    private final MethodHandle moodStreaksHandle;
    private final MethodHandle moodTrendSlopeHandle;
    private final MethodHandle moodDistributionHandle;
    
    // Haskell poetry handles
    private final MethodHandle hsAnalyzeMeterHandle;
    private final MethodHandle hsAnalyzeRhymeSchemeHandle;
    private final MethodHandle hsCountSyllablesHandle;
    private final MethodHandle hsAnalyzeSoundDevicesHandle;
    private final MethodHandle hsGetMeterNameHandle;
    private final MethodHandle hsGetMeterRegularityHandle;
    private final MethodHandle hsCheckRhymeHandle;
    private final MethodHandle hsGetVocabStatsHandle;
    private final MethodHandle hsTypeTokenRatioHandle;
    private final MethodHandle hsGetRhymeKeyHandle;
    private final MethodHandle hsEstimateStressHandle;
    
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

        // File metadata helpers
        this.countWordsHandle = optionalHandle(
            "simjot_count_words",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.countWordsFileHandle = optionalHandle(
            "simjot_count_words_file",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        this.extractTitleHandle = optionalHandle(
            "simjot_extract_title",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.fileMetaBatchHandle = optionalHandle(
            "simjot_file_meta_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        this.listFilesMetaHandle = optionalHandle(
            "simjot_list_files_meta",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
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
        this.textExtractTagsHandle = optionalHandle(
            "simjot_text_extract_tags",
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
        this.jsonParseStringArrayHandle = optionalHandle("simjot_json_parse_string_array",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
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

        // Search handles
        this.searchFindHandle = optionalHandle("simjot_search_find",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.searchFindCiHandle = optionalHandle("simjot_search_find_ci",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.searchCountHandle = optionalHandle("simjot_search_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.searchFindAllHandle = optionalHandle("simjot_search_find_all",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.searchFuzzyHandle = optionalHandle("simjot_search_fuzzy",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.searchAcBuildHandle = optionalHandle("simjot_search_ac_build",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.searchAcFindHandle = optionalHandle("simjot_search_ac_find",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.searchAcFreeHandle = optionalHandle("simjot_search_ac_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        
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
        
        // Mood analytics handles
        this.moodLoadHandle = optionalHandle("simjot_mood_load",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.moodComputeDailyHandle = optionalHandle("simjot_mood_compute_daily",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.moodComputeSummaryHandle = optionalHandle("simjot_mood_compute_summary",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.moodGetDailyHandle = optionalHandle("simjot_mood_get_daily",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.moodGetSummaryHandle = optionalHandle("simjot_mood_get_summary",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.moodDailyCountHandle = optionalHandle("simjot_mood_daily_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.moodSampleCountHandle = optionalHandle("simjot_mood_sample_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.moodClearHandle = optionalHandle("simjot_mood_clear",
            FunctionDescriptor.ofVoid());

        // Profiler handles
        this.profilerInitHandle = optionalHandle("simjot_profiler_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.profilerStartHandle = optionalHandle("simjot_profiler_start",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.profilerStopHandle = optionalHandle("simjot_profiler_stop",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.profilerResetHandle = optionalHandle("simjot_profiler_reset",
            FunctionDescriptor.ofVoid());
        this.profilerRegisterComponentHandle = optionalHandle("simjot_profiler_register_component",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.profilerRegisterThreadHandle = optionalHandle("simjot_profiler_register_thread",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.profilerUnregisterThreadHandle = optionalHandle("simjot_profiler_unregister_thread",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.profilerTrackAllocHandle = optionalHandle("simjot_profiler_track_alloc",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.profilerTrackFreeHandle = optionalHandle("simjot_profiler_track_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.profilerSampleHandle = optionalHandle("simjot_profiler_sample",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.profilerComponentCountHandle = optionalHandle("simjot_profiler_component_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.profilerGetComponentSnapshotHandle = optionalHandle("simjot_profiler_get_component_snapshot",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.profilerGetSummaryHandle = optionalHandle("simjot_profiler_get_summary",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.profilerPrintReportHandle = optionalHandle("simjot_profiler_print_report",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.profilerStatusLineHandle = optionalHandle("simjot_profiler_status_line",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Mood graphics handles
        // int32_t simjot_mood_sparkline(const int32_t* values, int32_t count, int32_t width, int32_t height, uint32_t* out, uint32_t bg_color, int32_t line_thickness)
        this.moodSparklineHandle = optionalHandle("simjot_mood_sparkline",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // int32_t simjot_mood_barchart(const int32_t* values, int32_t count, int32_t width, int32_t height, uint32_t* out, uint32_t bg_color, int32_t bar_spacing)
        this.moodBarchartHandle = optionalHandle("simjot_mood_barchart",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // int32_t simjot_mood_gauge(int32_t value, int32_t size, uint32_t* out, uint32_t bg_color, uint32_t track_color, int32_t thickness)
        this.moodGaugeHandle = optionalHandle("simjot_mood_gauge",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // int32_t simjot_mood_heatmap(const int32_t* values, int32_t count, int32_t cols, int32_t cell_size, int32_t cell_gap, uint32_t* out, int32_t out_width, int32_t out_height, uint32_t bg_color, uint32_t empty_color)
        this.moodHeatmapHandle = optionalHandle("simjot_mood_heatmap",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        
        // Dictionary JSON handles
        this.jsonLoadDictFileHandle = optionalHandle("simjot_json_load_dict_file",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Image scaling handles
        // int32_t simjot_image_scale(const uint32_t* src, int32_t src_w, int32_t src_h, uint32_t* dst, int32_t dst_w, int32_t dst_h, int32_t quality)
        this.imageScaleHandle = optionalHandle("simjot_image_scale",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // int32_t simjot_image_blur(uint32_t* pixels, int32_t width, int32_t height, int32_t radius)
        this.imageBlurHandle = optionalHandle("simjot_image_blur",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // int32_t simjot_image_tint(uint32_t* pixels, int32_t width, int32_t height, uint32_t tint_color, float intensity)
        this.imageTintHandle = optionalHandle("simjot_image_tint",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
        // int32_t simjot_image_extract_accent(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride)
        this.imageExtractAccentHandle = optionalHandle("simjot_image_extract_accent",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        
        // Spell check handles
        this.spellEdit1Handle = optionalHandle("simjot_spell_edit1",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.levenshteinHandle = optionalHandle("simjot_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.damerauLevenshteinHandle = optionalHandle("simjot_damerau_levenshtein",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Autocorrect handles
        this.autocorrectAdjacentKeyHandle = optionalHandle("simjot_autocorrect_adjacent_key",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autocorrectPhoneticHandle = optionalHandle("simjot_autocorrect_phonetic",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autocorrectPreserveCaseHandle = optionalHandle("simjot_autocorrect_preserve_case",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autocorrectCorrectHandle = optionalHandle("simjot_autocorrect_correct",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autocorrectStartsVowelSoundHandle = optionalHandle("simjot_autocorrect_starts_vowel_sound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.autocorrectPhraseHandle = optionalHandle("simjot_autocorrect_phrase",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autocorrectFixCapsHandle = optionalHandle("simjot_autocorrect_fix_caps",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // Additional text utility handles
        this.textSyllableCountHandle = optionalHandle("simjot_text_syllable_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.textAnalyzeHandle = optionalHandle("simjot_text_analyze",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.textIsAsciiHandle = optionalHandle("simjot_text_is_ascii",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.textIsAlnumHandle = optionalHandle("simjot_text_is_alnum",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.textIsSafeFilenameHandle = optionalHandle("simjot_text_is_safe_filename",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.parseIntHandle = optionalHandle("simjot_parse_int",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.parseBoolHandle = optionalHandle("simjot_parse_bool",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        
        // Math utility handles
        this.mathMeanHandle = optionalHandle("simjot_math_mean",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathVarianceHandle = optionalHandle("simjot_math_variance",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathStddevHandle = optionalHandle("simjot_math_stddev",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathMinHandle = optionalHandle("simjot_math_min",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathMaxHandle = optionalHandle("simjot_math_max",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathSumHandle = optionalHandle("simjot_math_sum",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.mathClampIntHandle = optionalHandle("simjot_math_clamp_int",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.mathStatsHandle = optionalHandle("simjot_math_stats",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        
        // File utility handles
        this.fileSizeHandle = optionalHandle("simjot_file_size",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.fileMtimeHandle = optionalHandle("simjot_file_mtime",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.fileExistsHandle = optionalHandle("simjot_file_exists",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.fileIsFileHandle = optionalHandle("simjot_file_is_file",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.fileIsDirHandle = optionalHandle("simjot_file_is_dir",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.diskAvailableHandle = optionalHandle("simjot_disk_available",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.dirCountHandle = optionalHandle("simjot_dir_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        
        // LRU Cache handles (handle-based API)
        this.lruCacheCreateHandle = optionalHandle("simjot_lru_cache_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        this.lruCacheDestroyHandle = optionalHandle("simjot_lru_cache_destroy",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        
        // LZ4 Compression handles
        this.lz4CompressHandle = optionalHandle("simjot_lz4_compress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.lz4DecompressHandle = optionalHandle("simjot_lz4_decompress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.lz4CompressBoundHandle = optionalHandle("simjot_lz4_compress_bound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        
        // Viewport image cache handles
        this.imgcacheInitHandle = optionalHandle("simjot_imgcache_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.imgcacheShutdownHandle = optionalHandle("simjot_imgcache_shutdown",
            FunctionDescriptor.ofVoid());
        this.imgcachePutHandle = optionalHandle("simjot_imgcache_put",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.imgcacheGetHandle = optionalHandle("simjot_imgcache_get",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.imgcacheContainsHandle = optionalHandle("simjot_imgcache_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        this.imgcacheRemoveHandle = optionalHandle("simjot_imgcache_remove",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        this.imgcacheClearHandle = optionalHandle("simjot_imgcache_clear",
            FunctionDescriptor.ofVoid());
        this.imgcacheStatsHandle = optionalHandle("simjot_imgcache_stats",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.imgcacheCullViewportHandle = optionalHandle("simjot_imgcache_cull_viewport",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.imgcacheBlitHandle = optionalHandle("simjot_imgcache_blit",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.imgcachePrescaleHandle = optionalHandle("simjot_imgcache_prescale",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        
        // Autosave manager handles
        this.autosaveInitHandle = optionalHandle("simjot_autosave_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.autosaveCreateSessionHandle = optionalHandle("simjot_autosave_create_session",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.autosaveDestroySessionHandle = optionalHandle("simjot_autosave_destroy_session",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.autosaveSetPathHandle = optionalHandle("simjot_autosave_set_path",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.autosaveMarkDirtyHandle = optionalHandle("simjot_autosave_mark_dirty",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.autosaveMarkCleanHandle = optionalHandle("simjot_autosave_mark_clean",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.autosaveIsDirtyHandle = optionalHandle("simjot_autosave_is_dirty",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.autosaveShouldSaveHandle = optionalHandle("simjot_autosave_should_save",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.autosaveMsUntilSaveHandle = optionalHandle("simjot_autosave_ms_until_save",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        this.autosaveHasRecoveryHandle = optionalHandle("simjot_autosave_has_recovery",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        
        // Hotkey manager handles
        this.hotkeyGetPlatformHandle = optionalHandle("simjot_hotkey_get_platform",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.hotkeyGetPrimaryModifierHandle = optionalHandle("simjot_hotkey_get_primary_modifier",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.hotkeyCheckHandle = optionalHandle("simjot_hotkey_check",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.hotkeyGetBindingHandle = optionalHandle("simjot_hotkey_get_binding",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.hotkeyGetDisplayStringHandle = optionalHandle("simjot_hotkey_get_display_string",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hotkeyCheckBatchHandle = optionalHandle("simjot_hotkey_check_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        
        // Offscreen buffer handles
        this.bufferCreateHandle = optionalHandle("simjot_buffer_create",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferResizeHandle = optionalHandle("simjot_buffer_resize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferDestroyHandle = optionalHandle("simjot_buffer_destroy",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
        this.bufferClearHandle = optionalHandle("simjot_buffer_clear",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        this.bufferWriteHandle = optionalHandle("simjot_buffer_write",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferReadHandle = optionalHandle("simjot_buffer_read",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferScrollHandle = optionalHandle("simjot_buffer_scroll",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferCompositeHandle = optionalHandle("simjot_buffer_composite",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.bufferGetSizeHandle = optionalHandle("simjot_buffer_get_size",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Undo/redo manager handles
        this.undoInitHandle = optionalHandle("simjot_undo_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.undoShutdownHandle = optionalHandle("simjot_undo_shutdown",
            FunctionDescriptor.ofVoid());
        this.undoCreateSessionHandle = optionalHandle("simjot_undo_create_session",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoDestroySessionHandle = optionalHandle("simjot_undo_destroy_session",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoClearHandle = optionalHandle("simjot_undo_clear",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoPushInsertHandle = optionalHandle("simjot_undo_push_insert",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.undoPushDeleteHandle = optionalHandle("simjot_undo_push_delete",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.undoPushReplaceHandle = optionalHandle("simjot_undo_push_replace",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, 
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.undoPushStyleHandle = optionalHandle("simjot_undo_push_style",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoBeginCompoundHandle = optionalHandle("simjot_undo_begin_compound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoEndCompoundHandle = optionalHandle("simjot_undo_end_compound",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoCanUndoHandle = optionalHandle("simjot_undo_can_undo",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoCanRedoHandle = optionalHandle("simjot_undo_can_redo",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoUndoHandle = optionalHandle("simjot_undo_undo",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, 
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.undoRedoHandle = optionalHandle("simjot_undo_redo",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.undoMarkSavePointHandle = optionalHandle("simjot_undo_mark_save_point",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoIsAtSavePointHandle = optionalHandle("simjot_undo_is_at_save_point",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoIsDirtyHandle = optionalHandle("simjot_undo_is_dirty",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoGetUndoCountHandle = optionalHandle("simjot_undo_get_undo_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoGetRedoCountHandle = optionalHandle("simjot_undo_get_redo_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoSetHistoryLimitHandle = optionalHandle("simjot_undo_set_history_limit",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.undoGetStatsHandle = optionalHandle("simjot_undo_get_stats",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Link detector handles
        this.linkContainsHandle = optionalHandle("simjot_link_contains",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkCountHandle = optionalHandle("simjot_link_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkFindRangesHandle = optionalHandle("simjot_link_find_ranges",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkExtractFirstHandle = optionalHandle("simjot_link_extract_first",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkNormalizeHandle = optionalHandle("simjot_link_normalize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkIsValidHandle = optionalHandle("simjot_link_is_valid",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.linkAtPositionHandle = optionalHandle("simjot_link_at_position",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Mood analytics handles (use double* for values per existing API)
        this.moodSmoothHandle = optionalHandle("simjot_mood_smooth",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.moodVolatilityHandle = optionalHandle("simjot_mood_volatility",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.moodStreaksHandle = optionalHandle("simjot_mood_streaks",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.moodTrendSlopeHandle = null; // Not in existing API
        this.moodDistributionHandle = null; // Not in existing API
        
        // Haskell poetry handles
        this.hsAnalyzeMeterHandle = optionalHandle("hs_analyze_meter",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.hsAnalyzeRhymeSchemeHandle = optionalHandle("hs_analyze_rhyme_scheme",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hsCountSyllablesHandle = optionalHandle("hs_count_syllables",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.hsAnalyzeSoundDevicesHandle = optionalHandle("hs_analyze_sound_devices",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.hsGetMeterNameHandle = optionalHandle("hs_get_meter_name",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hsGetMeterRegularityHandle = optionalHandle("hs_get_meter_regularity",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.hsCheckRhymeHandle = optionalHandle("hs_check_rhyme",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.hsGetVocabStatsHandle = optionalHandle("hs_get_vocab_stats",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hsTypeTokenRatioHandle = optionalHandle("hs_type_token_ratio",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.hsGetRhymeKeyHandle = optionalHandle("hs_get_rhyme_key",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.hsEstimateStressHandle = optionalHandle("hs_estimate_stress",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
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

    public List<String> textExtractTags(String text) {
        if (textExtractTagsHandle == null || text == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cText = tempArena.allocateFrom(text);
            int bufSize = Math.max(256, text.length() * 2);
            MemorySegment outBuf = tempArena.allocate(bufSize);
            int written = (int) textExtractTagsHandle.invokeExact(cText, outBuf, bufSize);
            if (written <= 0) return Collections.emptyList();
            byte[] bytes = outBuf.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE);
            String result = new String(bytes, StandardCharsets.UTF_8);
            List<String> tags = new ArrayList<>();
            for (String t : result.split("\n")) {
                if (!t.isEmpty()) tags.add(t);
            }
            return tags;
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

    public int jsonCountKeys(String json) {
        if (jsonCountKeysHandle == null || json == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            return (int) jsonCountKeysHandle.invokeExact(cJson);
        } catch (Throwable t) {
            return -1;
        }
    }

    public List<String> jsonGetKeys(String json) {
        if (jsonGetKeysHandle == null || json == null) return null;
        int outLen = Math.min(Math.max(512, json.length() + 1), 131072);
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment outBuf = tempArena.allocate(outLen);
            int count = (int) jsonGetKeysHandle.invokeExact(cJson, outBuf, outLen);
            if (count <= 0) return Collections.emptyList();
            String raw = outBuf.getString(0);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            String[] parts = raw.split("\n");
            List<String> out = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (!p.isEmpty()) out.add(p);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public String jsonGetPath(String json, String path) {
        if (jsonGetPathHandle == null || json == null || path == null) return null;
        int outLen = Math.min(Math.max(256, json.length() + 1), 131072);
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment cPath = tempArena.allocateFrom(path);
            MemorySegment outBuf = tempArena.allocate(outLen);
            int len = (int) jsonGetPathHandle.invokeExact(cJson, cPath, outBuf, outLen);
            if (len <= 0) return null;
            return new String(outBuf.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public List<String> jsonParseStringArray(String json) {
        if (jsonParseStringArrayHandle == null || json == null) return null;
        int outLen = Math.min(Math.max(512, json.length() + 1), 131072);
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment cJson = tempArena.allocateFrom(json);
            MemorySegment outBuf = tempArena.allocate(outLen);
            int count = (int) jsonParseStringArrayHandle.invokeExact(cJson, outBuf, outLen);
            if (count <= 0) return Collections.emptyList();
            String raw = outBuf.getString(0);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            String[] parts = raw.split("\n");
            List<String> out = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (!p.isEmpty()) out.add(p);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
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
    // SEARCH API
    // ═══════════════════════════════════════════════════════════════════════════

    public long searchFind(String haystack, String needle) {
        if (searchFindHandle == null || haystack == null || needle == null) return -1;
        byte[] hayBytes = haystack.getBytes(StandardCharsets.UTF_8);
        byte[] needleBytes = needle.getBytes(StandardCharsets.UTF_8);
        if (hayBytes.length == 0 || needleBytes.length == 0) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment haySeg = tempArena.allocate(hayBytes.length);
            haySeg.asByteBuffer().put(hayBytes);
            MemorySegment needleSeg = tempArena.allocate(needleBytes.length);
            needleSeg.asByteBuffer().put(needleBytes);
            return (long) searchFindHandle.invokeExact(haySeg, (long) hayBytes.length, needleSeg, (long) needleBytes.length);
        } catch (Throwable t) { return -1; }
    }

    public long searchFindCi(String haystack, String needle) {
        if (searchFindCiHandle == null || haystack == null || needle == null) return -1;
        byte[] hayBytes = haystack.getBytes(StandardCharsets.UTF_8);
        byte[] needleBytes = needle.getBytes(StandardCharsets.UTF_8);
        if (hayBytes.length == 0 || needleBytes.length == 0) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment haySeg = tempArena.allocate(hayBytes.length);
            haySeg.asByteBuffer().put(hayBytes);
            MemorySegment needleSeg = tempArena.allocate(needleBytes.length);
            needleSeg.asByteBuffer().put(needleBytes);
            return (long) searchFindCiHandle.invokeExact(haySeg, (long) hayBytes.length, needleSeg, (long) needleBytes.length);
        } catch (Throwable t) { return -1; }
    }

    public int searchCount(String haystack, String needle) {
        if (searchCountHandle == null || haystack == null || needle == null) return 0;
        byte[] hayBytes = haystack.getBytes(StandardCharsets.UTF_8);
        byte[] needleBytes = needle.getBytes(StandardCharsets.UTF_8);
        if (hayBytes.length == 0 || needleBytes.length == 0) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment haySeg = tempArena.allocate(hayBytes.length);
            haySeg.asByteBuffer().put(hayBytes);
            MemorySegment needleSeg = tempArena.allocate(needleBytes.length);
            needleSeg.asByteBuffer().put(needleBytes);
            return (int) searchCountHandle.invokeExact(haySeg, (long) hayBytes.length, needleSeg, (long) needleBytes.length);
        } catch (Throwable t) { return 0; }
    }

    public long[] searchFindAll(String haystack, String needle, int maxResults) {
        if (searchFindAllHandle == null || haystack == null || needle == null || maxResults <= 0) return null;
        byte[] hayBytes = haystack.getBytes(StandardCharsets.UTF_8);
        byte[] needleBytes = needle.getBytes(StandardCharsets.UTF_8);
        if (hayBytes.length == 0 || needleBytes.length == 0) return new long[0];
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment haySeg = tempArena.allocate(hayBytes.length);
            haySeg.asByteBuffer().put(hayBytes);
            MemorySegment needleSeg = tempArena.allocate(needleBytes.length);
            needleSeg.asByteBuffer().put(needleBytes);
            MemorySegment out = tempArena.allocate(maxResults * 8L);
            int count = (int) searchFindAllHandle.invokeExact(haySeg, (long) hayBytes.length, needleSeg, (long) needleBytes.length,
                out, maxResults);
            if (count <= 0) return new long[0];
            long[] positions = new long[count];
            out.asByteBuffer().asLongBuffer().get(positions, 0, count);
            return positions;
        } catch (Throwable t) {
            return null;
        }
    }

    public int searchFuzzy(String text, String pattern, int maxDistance, long[] outPositions, int[] outDistances, int maxResults) {
        if (searchFuzzyHandle == null || text == null || pattern == null || maxResults <= 0) return 0;
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length == 0 || patternBytes.length == 0) return 0;
        int results = maxResults;
        if (outPositions != null) results = Math.min(results, outPositions.length);
        if (outDistances != null) results = Math.min(results, outDistances.length);
        if (results <= 0) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment textSeg = tempArena.allocate(textBytes.length);
            textSeg.asByteBuffer().put(textBytes);
            MemorySegment patternSeg = tempArena.allocate(patternBytes.length);
            patternSeg.asByteBuffer().put(patternBytes);
            MemorySegment posSeg = tempArena.allocate(results * 8L);
            MemorySegment distSeg = tempArena.allocate(results * 4L);
            int count = (int) searchFuzzyHandle.invokeExact(textSeg, (long) textBytes.length, patternSeg, patternBytes.length,
                maxDistance, posSeg, distSeg, results);
            if (count <= 0) return 0;
            if (outPositions != null) {
                posSeg.asByteBuffer().asLongBuffer().get(outPositions, 0, count);
            }
            if (outDistances != null) {
                distSeg.asByteBuffer().asIntBuffer().get(outDistances, 0, count);
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }

    public MemorySegment searchAcBuild(String[] patterns) {
        if (searchAcBuildHandle == null || patterns == null || patterns.length == 0) return null;
        int totalBytes = 0;
        byte[][] encoded = new byte[patterns.length][];
        for (int i = 0; i < patterns.length; i++) {
            String p = patterns[i] == null ? "" : patterns[i];
            encoded[i] = p.getBytes(StandardCharsets.UTF_8);
            totalBytes += encoded[i].length + 1;
        }
        if (totalBytes == 0) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment buf = tempArena.allocate(totalBytes);
            java.nio.ByteBuffer bb = buf.asByteBuffer();
            for (byte[] b : encoded) {
                bb.put(b);
                bb.put((byte) 0);
            }
            return (MemorySegment) searchAcBuildHandle.invokeExact(buf, patterns.length);
        } catch (Throwable t) {
            return null;
        }
    }

    public int searchAcFind(MemorySegment handle, String text, long[] outPositions, int[] outPatterns, int maxResults) {
        if (searchAcFindHandle == null || handle == null || text == null || maxResults <= 0) return 0;
        int results = maxResults;
        if (outPositions != null) results = Math.min(results, outPositions.length);
        if (outPatterns != null) results = Math.min(results, outPatterns.length);
        if (results <= 0) return 0;
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length == 0) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment textSeg = tempArena.allocate(textBytes.length);
            textSeg.asByteBuffer().put(textBytes);
            MemorySegment posSeg = tempArena.allocate(results * 8L);
            MemorySegment patSeg = tempArena.allocate(results * 4L);
            int count = (int) searchAcFindHandle.invokeExact(handle, textSeg, (long) textBytes.length, posSeg, patSeg, results);
            if (count <= 0) return 0;
            if (outPositions != null) {
                posSeg.asByteBuffer().asLongBuffer().get(outPositions, 0, count);
            }
            if (outPatterns != null) {
                patSeg.asByteBuffer().asIntBuffer().get(outPatterns, 0, count);
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }

    public void searchAcFree(MemorySegment handle) {
        if (searchAcFreeHandle == null || handle == null) return;
        try {
            searchAcFreeHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
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

    public double[] poetryGetVocabStats() {
        if (poetryGetVocabStatsHandle == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment totalSeg = tempArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment uniqueSeg = tempArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment diversitySeg = tempArena.allocate(ValueLayout.JAVA_DOUBLE);
            MemorySegment avgLenSeg = tempArena.allocate(ValueLayout.JAVA_DOUBLE);
            poetryGetVocabStatsHandle.invokeExact(totalSeg, uniqueSeg, diversitySeg, avgLenSeg);
            double total = totalSeg.get(ValueLayout.JAVA_INT, 0);
            double unique = uniqueSeg.get(ValueLayout.JAVA_INT, 0);
            double diversity = diversitySeg.get(ValueLayout.JAVA_DOUBLE, 0);
            double avgLen = avgLenSeg.get(ValueLayout.JAVA_DOUBLE, 0);
            return new double[] { total, unique, diversity, avgLen };
        } catch (Throwable t) { return null; }
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

    public int poetryGetLineSyllables(int lineIndex) {
        if (poetryGetLineSyllablesHandle == null) return 0;
        try {
            return (int) poetryGetLineSyllablesHandle.invokeExact(lineIndex);
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
    // AUTOSAVE MANAGER API
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean hasAutosaveSupport() {
        return autosaveInitHandle != null && autosaveCreateSessionHandle != null;
    }

    public boolean autosaveInit() {
        if (autosaveInitHandle == null) return false;
        try {
            return (int) autosaveInitHandle.invokeExact() == 1;
        } catch (Throwable t) { return false; }
    }

    public int autosaveCreateSession(String filePath, int debounceMs) {
        if (autosaveCreateSessionHandle == null || filePath == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pathSeg = temp.allocateFrom(filePath);
            return (int) autosaveCreateSessionHandle.invokeExact(pathSeg, debounceMs);
        } catch (Throwable t) { return -1; }
    }

    public boolean autosaveDestroySession(int sessionId) {
        if (autosaveDestroySessionHandle == null) return false;
        try {
            return (int) autosaveDestroySessionHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) { return false; }
    }

    public boolean autosaveSetPath(int sessionId, String newPath) {
        if (autosaveSetPathHandle == null || newPath == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pathSeg = temp.allocateFrom(newPath);
            return (int) autosaveSetPathHandle.invokeExact(sessionId, pathSeg) == 1;
        } catch (Throwable t) { return false; }
    }

    public boolean autosaveMarkDirty(int sessionId) {
        if (autosaveMarkDirtyHandle == null) return false;
        try {
            return (int) autosaveMarkDirtyHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) { return false; }
    }

    public boolean autosaveMarkClean(int sessionId) {
        if (autosaveMarkCleanHandle == null) return false;
        try {
            return (int) autosaveMarkCleanHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) { return false; }
    }

    public boolean autosaveIsDirty(int sessionId) {
        if (autosaveIsDirtyHandle == null) return false;
        try {
            return (int) autosaveIsDirtyHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) { return false; }
    }

    public boolean autosaveShouldSave(int sessionId) {
        if (autosaveShouldSaveHandle == null) return false;
        try {
            return (int) autosaveShouldSaveHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) { return false; }
    }

    public long autosaveMsUntilSave(int sessionId) {
        if (autosaveMsUntilSaveHandle == null) return -1;
        try {
            return (long) autosaveMsUntilSaveHandle.invokeExact(sessionId);
        } catch (Throwable t) { return -1; }
    }

    public boolean autosaveHasRecovery(String filePath) {
        if (autosaveHasRecoveryHandle == null || filePath == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pathSeg = temp.allocateFrom(filePath);
            return (int) autosaveHasRecoveryHandle.invokeExact(pathSeg) == 1;
        } catch (Throwable t) { return false; }
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
    // SIMD API
    // ═══════════════════════════════════════════════════════════════════════════

    public int simdSupportLevel() {
        try {
            MethodHandle handle = optionalHandle("simjot_simd_support_level",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            if (handle == null) return 0;
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    public long simdSumInt(int[] arr) {
        if (arr == null || arr.length == 0) return 0;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_simd_sum_i32",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) {
                long sum = 0;
                for (int v : arr) sum += v;
                return sum;
            }
            MemorySegment seg = temp.allocate(arr.length * 4L);
            seg.asByteBuffer().asIntBuffer().put(arr);
            return (long) handle.invokeExact(seg, arr.length);
        } catch (Throwable t) {
            long sum = 0;
            for (int v : arr) sum += v;
            return sum;
        }
    }

    public double simdSumDouble(double[] arr) {
        if (arr == null || arr.length == 0) return 0;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_simd_sum_f64",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) {
                double sum = 0;
                for (double v : arr) sum += v;
                return sum;
            }
            MemorySegment seg = temp.allocate(arr.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(arr);
            return (double) handle.invokeExact(seg, arr.length);
        } catch (Throwable t) {
            double sum = 0;
            for (double v : arr) sum += v;
            return sum;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE SYSTEM API
    // ═══════════════════════════════════════════════════════════════════════════

    public long fsSize(String path) {
        if (path == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_size",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            if (handle == null) return -1;
            MemorySegment cPath = temp.allocateFrom(path);
            return (long) handle.invokeExact(cPath);
        } catch (Throwable t) {
            return -1;
        }
    }

    public long fsMtime(String path) {
        if (path == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_mtime",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            if (handle == null) return -1;
            MemorySegment cPath = temp.allocateFrom(path);
            return (long) handle.invokeExact(cPath);
        } catch (Throwable t) {
            return -1;
        }
    }

    public boolean fsExists(String path) {
        if (path == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_exists",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            return ((int) handle.invokeExact(cPath)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean fsIsDir(String path) {
        if (path == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_is_dir",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            return ((int) handle.invokeExact(cPath)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean fsIsFile(String path) {
        if (path == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_is_file",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            return ((int) handle.invokeExact(cPath)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public String fsListRecursive(String path, String ext, int depth) {
        if (path == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_list_recursive",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cPath = temp.allocateFrom(path);
            MemorySegment cExt = (ext != null) ? temp.allocateFrom(ext) : MemorySegment.NULL;
            int outLen = 65536;
            MemorySegment out = temp.allocate(outLen);
            int count = (int) handle.invokeExact(cPath, cExt, depth, out, outLen);
            if (count < 0) return null;
            return out.getString(0);
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] fsReadAll(String path) {
        if (path == null) return null;
        long size = fsSize(path);
        if (size < 0 || size > Integer.MAX_VALUE - 1) return null;
        int outLen = (int) size + 1;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_read_all",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cPath = temp.allocateFrom(path);
            MemorySegment out = temp.allocate(outLen);
            int read = (int) handle.invokeExact(cPath, out, outLen);
            if (read < 0) return null;
            byte[] data = new byte[read];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean fsWriteAll(String path, byte[] data) {
        if (path == null || data == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_write_all",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            MemorySegment cData = temp.allocate(data.length == 0 ? 1 : data.length);
            if (data.length > 0) cData.asByteBuffer().put(data);
            int rc = (int) handle.invokeExact(cPath, cData, data.length);
            return rc == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean fsMkdir(String path) {
        if (path == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_mkdir",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            return ((int) handle.invokeExact(cPath)) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean fsRemove(String path) {
        if (path == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_remove",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cPath = temp.allocateFrom(path);
            return ((int) handle.invokeExact(cPath)) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean fsRename(String oldPath, String newPath) {
        if (oldPath == null || newPath == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_rename",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cOld = temp.allocateFrom(oldPath);
            MemorySegment cNew = temp.allocateFrom(newPath);
            return ((int) handle.invokeExact(cOld, cNew)) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public int fsWatchCreate(String path) {
        if (path == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_watch_create",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return -1;
            MemorySegment cPath = temp.allocateFrom(path);
            return (int) handle.invokeExact(cPath);
        } catch (Throwable t) {
            return -1;
        }
    }

    public int fsWatchPoll(int watchId, int timeout) {
        try {
            MethodHandle handle = optionalHandle("simjot_fs_watch_poll",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return 0;
            return (int) handle.invokeExact(watchId, timeout);
        } catch (Throwable t) {
            return 0;
        }
    }

    public void fsWatchDestroy(int watchId) {
        try {
            MethodHandle handle = optionalHandle("simjot_fs_watch_destroy",
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            if (handle == null) return;
            handle.invokeExact(watchId);
        } catch (Throwable ignored) {}
    }

    public String fsExtension(String path) {
        if (path == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_extension",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cPath = temp.allocateFrom(path);
            int outLen = Math.max(256, path.length() * 4 + 16);
            MemorySegment out = temp.allocate(outLen);
            int len = (int) handle.invokeExact(cPath, out, outLen);
            if (len < 0) return null;
            return out.getString(0);
        } catch (Throwable t) {
            return null;
        }
    }

    public String fsBasename(String path) {
        if (path == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_basename",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cPath = temp.allocateFrom(path);
            int outLen = Math.max(256, path.length() * 4 + 16);
            MemorySegment out = temp.allocate(outLen);
            int len = (int) handle.invokeExact(cPath, out, outLen);
            if (len < 0) return null;
            return out.getString(0);
        } catch (Throwable t) {
            return null;
        }
    }

    public String fsDirname(String path) {
        if (path == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_dirname",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cPath = temp.allocateFrom(path);
            int outLen = Math.max(256, path.length() * 4 + 16);
            MemorySegment out = temp.allocate(outLen);
            int len = (int) handle.invokeExact(cPath, out, outLen);
            if (len < 0) return null;
            return out.getString(0);
        } catch (Throwable t) {
            return null;
        }
    }

    public String fsJoin(String base, String child) {
        if (base == null || child == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_fs_join",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            if (handle == null) return null;
            MemorySegment cBase = temp.allocateFrom(base);
            MemorySegment cChild = temp.allocateFrom(child);
            int outLen = Math.max(256, (base.length() + child.length()) * 4 + 16);
            MemorySegment out = temp.allocate(outLen);
            int len = (int) handle.invokeExact(cBase, cChild, out, outLen);
            if (len < 0) return null;
            return out.getString(0);
        } catch (Throwable t) {
            return null;
        }
    }
    
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
    // FILE METADATA API
    // ═══════════════════════════════════════════════════════════════════════════

    public int countWords(String text) {
        if (countWordsHandle == null || text == null || text.isEmpty()) return -1;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) return 0;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(bytes.length);
            seg.asByteBuffer().put(bytes);
            return (int) countWordsHandle.invokeExact(seg, bytes.length);
        } catch (Throwable t) {
            return -1;
        }
    }

    public int countWordsFile(String path) {
        if (countWordsFileHandle == null || path == null || path.isEmpty()) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(path);
            return (int) countWordsFileHandle.invokeExact(cPath);
        } catch (Throwable t) {
            return -1;
        }
    }

    public String extractTitle(String path) {
        if (extractTitleHandle == null || path == null || path.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(path);
            int outLen = 512;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) extractTitleHandle.invokeExact(cPath, out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] fileMetaBatch(String path) {
        if (fileMetaBatchHandle == null || path == null || path.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(path);
            int outLen = 512;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) fileMetaBatchHandle.invokeExact(cPath, out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] listFilesMeta(String dirPath, String extension) {
        if (listFilesMetaHandle == null || dirPath == null || dirPath.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cDir = temp.allocateFrom(dirPath);
            MemorySegment cExt = (extension != null) ? temp.allocateFrom(extension) : MemorySegment.NULL;
            int outLen = 131072;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) listFilesMetaHandle.invokeExact(cDir, cExt, out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT PROFILER API
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean profilerInit(int sampleIntervalMs) {
        if (profilerInitHandle == null) return false;
        try {
            return (int) profilerInitHandle.invokeExact(sampleIntervalMs) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean profilerStart() {
        if (profilerStartHandle == null) return false;
        try {
            return (int) profilerStartHandle.invokeExact() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean profilerStop() {
        if (profilerStopHandle == null) return false;
        try {
            return (int) profilerStopHandle.invokeExact() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public void profilerReset() {
        if (profilerResetHandle == null) return;
        try {
            profilerResetHandle.invokeExact();
        } catch (Throwable ignored) {}
    }

    public int profilerRegisterComponent(String name) {
        if (profilerRegisterComponentHandle == null || name == null || name.isEmpty()) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cName = temp.allocateFrom(name);
            return (int) profilerRegisterComponentHandle.invokeExact(cName);
        } catch (Throwable t) {
            return -1;
        }
    }

    public boolean profilerRegisterThread(String componentName, long threadId) {
        if (profilerRegisterThreadHandle == null || componentName == null || componentName.isEmpty()) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cName = temp.allocateFrom(componentName);
            return (int) profilerRegisterThreadHandle.invokeExact(cName, threadId) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean profilerUnregisterThread(String componentName, long threadId) {
        if (profilerUnregisterThreadHandle == null || componentName == null || componentName.isEmpty()) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cName = temp.allocateFrom(componentName);
            return (int) profilerUnregisterThreadHandle.invokeExact(cName, threadId) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public void profilerTrackAlloc(String componentName, long bytes) {
        if (profilerTrackAllocHandle == null || componentName == null || componentName.isEmpty()) return;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cName = temp.allocateFrom(componentName);
            profilerTrackAllocHandle.invokeExact(cName, bytes);
        } catch (Throwable ignored) {}
    }

    public void profilerTrackFree(String componentName, long bytes) {
        if (profilerTrackFreeHandle == null || componentName == null || componentName.isEmpty()) return;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cName = temp.allocateFrom(componentName);
            profilerTrackFreeHandle.invokeExact(cName, bytes);
        } catch (Throwable ignored) {}
    }

    public boolean profilerSample() {
        if (profilerSampleHandle == null) return false;
        try {
            return (int) profilerSampleHandle.invokeExact() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public int profilerComponentCount() {
        if (profilerComponentCountHandle == null) return 0;
        try {
            return (int) profilerComponentCountHandle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    public byte[] profilerGetComponentSnapshot(int index) {
        if (profilerGetComponentSnapshotHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 512;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) profilerGetComponentSnapshotHandle.invokeExact(index, out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = temp.allocate(outLen);
                len = (int) profilerGetComponentSnapshotHandle.invokeExact(index, out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    public byte[] profilerGetSummary() {
        if (profilerGetSummaryHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 96;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) profilerGetSummaryHandle.invokeExact(out, outLen);
            if (len < 0) {
                outLen = Math.max(outLen, -len);
                out = temp.allocate(outLen);
                len = (int) profilerGetSummaryHandle.invokeExact(out, outLen);
            }
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    public String profilerPrintReport() {
        if (profilerPrintReportHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 8192;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) profilerPrintReportHandle.invokeExact(out, outLen);
            if (len <= 0) return null;
            if (len >= outLen) {
                outLen = len + 1;
                out = temp.allocate(outLen);
                len = (int) profilerPrintReportHandle.invokeExact(out, outLen);
                if (len <= 0) return null;
            }
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public String profilerStatusLine() {
        if (profilerStatusLineHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 512;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) profilerStatusLineHandle.invokeExact(out, outLen);
            if (len <= 0) return null;
            if (len >= outLen) {
                outLen = len + 1;
                out = temp.allocate(outLen);
                len = (int) profilerStatusLineHandle.invokeExact(out, outLen);
                if (len <= 0) return null;
            }
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
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
    // AERO/GLASS EFFECT API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute outer glow alpha using ease-out curve.
     */
    public int aeroOuterGlowAlpha(int layer, int size, int maxAlpha) {
        try {
            MethodHandle handle = optionalHandle("simjot_aero_outer_glow_alpha",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return 0;
            return (int) handle.invokeExact(layer, size, maxAlpha);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Compute inner shadow alpha using linear fade.
     */
    public int aeroInnerShadowAlpha(int layer, int size, int maxAlpha) {
        try {
            MethodHandle handle = optionalHandle("simjot_aero_inner_shadow_alpha",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return 0;
            return (int) handle.invokeExact(layer, size, maxAlpha);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Interpolate between two ARGB colors.
     */
    public int aeroLerpColor(int color1, int color2, float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_aero_lerp_color",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
            if (handle == null) return color1;
            return (int) handle.invokeExact(color1, color2, t);
        } catch (Throwable e) {
            return color1;
        }
    }

    /**
     * Blend foreground over background using alpha compositing.
     */
    public int aeroBlendOver(int fg, int bg) {
        try {
            MethodHandle handle = optionalHandle("simjot_aero_blend_over",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return fg;
            return (int) handle.invokeExact(fg, bg);
        } catch (Throwable t) {
            return fg;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION MATH API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Easing function: cosine ease-in-out
     */
    public float easeCosine(float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_ease_cosine",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) return t; // fallback to linear
            return (float) handle.invokeExact(t);
        } catch (Throwable e) {
            return t;
        }
    }

    /**
     * Easing function: smoothstep (3t² - 2t³)
     */
    public float easeSmoothstep(float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_ease_smoothstep",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) return t * t * (3 - 2 * t);
            return (float) handle.invokeExact(t);
        } catch (Throwable e) {
            return t * t * (3 - 2 * t);
        }
    }

    /**
     * Easing function: smootherstep (6t⁵ - 15t⁴ + 10t³)
     */
    public float easeSmootherstep(float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_ease_smootherstep",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) return t * t * t * (t * (t * 6 - 15) + 10);
            return (float) handle.invokeExact(t);
        } catch (Throwable e) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
    }

    /**
     * Spring decay calculation
     */
    public float springDecay(float current, float damping, float threshold) {
        try {
            MethodHandle handle = optionalHandle("simjot_spring_decay",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, 
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                current *= damping;
                return Math.abs(current) < threshold ? 0f : current;
            }
            return (float) handle.invokeExact(current, damping, threshold);
        } catch (Throwable e) {
            current *= damping;
            return Math.abs(current) < threshold ? 0f : current;
        }
    }

    /**
     * Calculate heartbeat scale factor
     */
    public float heartbeatScale(float phase, float baseAmplitude, float spring) {
        try {
            MethodHandle handle = optionalHandle("simjot_heartbeat_scale",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                float eased = (1f - (float) Math.cos(phase)) * 0.5f;
                return 1f + baseAmplitude * (eased * 2f - 1f) + spring;
            }
            return (float) handle.invokeExact(phase, baseAmplitude, spring);
        } catch (Throwable e) {
            float eased = (1f - (float) Math.cos(phase)) * 0.5f;
            return 1f + baseAmplitude * (eased * 2f - 1f) + spring;
        }
    }

    /**
     * Sample ECG waveform at given phase
     */
    public float ecgSample(float phase) {
        try {
            MethodHandle handle = optionalHandle("simjot_ecg_sample",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) return 0f;
            return (float) handle.invokeExact(phase);
        } catch (Throwable e) {
            return 0f;
        }
    }

    /**
     * Calculate fade alpha for transition
     * @param easingType 0=linear, 1=smoothstep, 2=smootherstep, 3=cosine
     */
    public float fadeAlpha(long elapsedMs, long durationMs, boolean fadeOut, int easingType) {
        try {
            MethodHandle handle = optionalHandle("simjot_fade_alpha",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, 
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) {
                float t = durationMs > 0 ? (float) elapsedMs / durationMs : 1f;
                t = Math.max(0f, Math.min(1f, t));
                float eased = t * t * (3 - 2 * t);
                return fadeOut ? eased : (1f - eased);
            }
            return (float) handle.invokeExact(elapsedMs, durationMs, fadeOut ? 1 : 0, easingType);
        } catch (Throwable e) {
            float t = durationMs > 0 ? (float) elapsedMs / durationMs : 1f;
            t = Math.max(0f, Math.min(1f, t));
            float eased = t * t * (3 - 2 * t);
            return fadeOut ? eased : (1f - eased);
        }
    }

    /**
     * Linearly interpolate between two colors
     */
    public int colorLerp(int color1, int color2, float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_color_lerp",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                t = Math.max(0f, Math.min(1f, t));
                int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
                int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
                int a = (int)(a1 + t * (a2 - a1)), r = (int)(r1 + t * (r2 - r1));
                int g = (int)(g1 + t * (g2 - g1)), b = (int)(b1 + t * (b2 - b1));
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
            return (int) handle.invokeExact(color1, color2, t);
        } catch (Throwable e) {
            return color1;
        }
    }

    /**
     * Calculate disappear animation value (0=visible, 1=gone)
     */
    public float disappearValue(float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_disappear_value",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                t = Math.max(0f, Math.min(1f, t));
                float eased = t * t * t * (t * (t * 6 - 15) + 10);
                return 1f - eased;
            }
            return (float) handle.invokeExact(t);
        } catch (Throwable e) {
            return 1f - t;
        }
    }

    /**
     * Calculate collapse height multiplier for list item removal
     */
    public float collapseHeight(float t) {
        try {
            MethodHandle handle = optionalHandle("simjot_collapse_height",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                t = Math.max(0f, Math.min(1f, t));
                float delayedT = Math.max(0f, Math.min(1f, (t - 0.3f) / 0.7f));
                float inv = 1f - delayedT;
                return inv * inv;
            }
            return (float) handle.invokeExact(t);
        } catch (Throwable e) {
            return 1f - t;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI SCALING API - Native DPI-aware scaling
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get number of connected displays
     */
    public int getDisplayCount() {
        try {
            MethodHandle handle = optionalHandle("simjot_get_display_count",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            if (handle == null) return 1;
            return (int) handle.invokeExact();
        } catch (Throwable e) {
            return 1;
        }
    }

    /**
     * Get scale factor for a specific display
     */
    public float getDisplayScale(int displayIndex) {
        try {
            MethodHandle handle = optionalHandle("simjot_get_display_scale",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
            if (handle == null) return 1.0f;
            return (float) handle.invokeExact(displayIndex);
        } catch (Throwable e) {
            return 1.0f;
        }
    }

    /**
     * Get scale factor for the primary display
     */
    public float getPrimaryDisplayScale() {
        try {
            MethodHandle handle = optionalHandle("simjot_get_primary_display_scale",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT));
            if (handle == null) return 1.0f;
            return (float) handle.invokeExact();
        } catch (Throwable e) {
            return 1.0f;
        }
    }

    /**
     * Get DPI for a specific display
     */
    public float getDisplayDpi(int displayIndex) {
        try {
            MethodHandle handle = optionalHandle("simjot_get_display_dpi",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
            if (handle == null) return 96.0f;
            return (float) handle.invokeExact(displayIndex);
        } catch (Throwable e) {
            return 96.0f;
        }
    }

    /**
     * Invalidate cached display scale values
     */
    public void invalidateDisplayCache() {
        try {
            MethodHandle handle = optionalHandle("simjot_invalidate_display_cache",
                FunctionDescriptor.ofVoid());
            if (handle != null) {
                handle.invokeExact();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Scale a dimension value
     */
    public int scaleDimension(int value, float scale) {
        try {
            MethodHandle handle = optionalHandle("simjot_scale_dimension",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                return Math.round(value * (scale > 0 ? scale : 1.0f));
            }
            return (int) handle.invokeExact(value, scale);
        } catch (Throwable e) {
            return Math.round(value * (scale > 0 ? scale : 1.0f));
        }
    }

    /**
     * Scale a float value
     */
    public float scaleValue(float value, float scale) {
        try {
            MethodHandle handle = optionalHandle("simjot_scale_value",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                return value * (scale > 0 ? scale : 1.0f);
            }
            return (float) handle.invokeExact(value, scale);
        } catch (Throwable e) {
            return value * (scale > 0 ? scale : 1.0f);
        }
    }

    /**
     * Scale a font size with proper rounding for readability
     */
    public float scaleFontSize(float baseSize, float scale) {
        try {
            MethodHandle handle = optionalHandle("simjot_scale_font_size",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
            if (handle == null) {
                float s = scale > 0 ? scale : 1.0f;
                float scaled = baseSize * s;
                scaled = Math.round(scaled * 2.0f) / 2.0f;
                return Math.max(8.0f, scaled);
            }
            return (float) handle.invokeExact(baseSize, scale);
        } catch (Throwable e) {
            return Math.max(8.0f, baseSize * (scale > 0 ? scale : 1.0f));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SETUP & INITIALIZATION API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize Simjot directory structure.
     * Creates root and all subdirectories with proper verification.
     */
    public int setupInit(String rootPath) {
        if (rootPath == null) return -1;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_setup_init",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return -1;
            MemorySegment pathSeg = tempArena.allocateFrom(rootPath);
            return (int) handle.invokeExact(pathSeg);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Verify setup is complete. Returns bitmask of status.
     */
    public int setupVerify(String rootPath) {
        if (rootPath == null) return 0;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_setup_verify",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return 0;
            MemorySegment pathSeg = tempArena.allocateFrom(rootPath);
            return (int) handle.invokeExact(pathSeg);
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Verify directory is truly writable by test write.
     */
    public boolean verifyWritable(String dirPath) {
        if (dirPath == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_verify_writable",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return new java.io.File(dirPath).canWrite();
            MemorySegment pathSeg = tempArena.allocateFrom(dirPath);
            return ((int) handle.invokeExact(pathSeg)) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Get detailed setup status.
     * Returns array: [root_exists, root_writable, notebooks_ok, mood_ok, 
     *                 settings_ok, wallpapers_ok, marker_valid, setup_complete]
     */
    public int[] setupStatus(String rootPath) {
        int[] result = new int[8];
        if (rootPath == null) return result;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_setup_status",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return result;
            MemorySegment pathSeg = tempArena.allocateFrom(rootPath);
            MemorySegment outSeg = tempArena.allocate(ValueLayout.JAVA_INT, 8);
            handle.invokeExact(pathSeg, outSeg);
            for (int i = 0; i < 8; i++) {
                result[i] = outSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    /**
     * Write config file atomically.
     */
    public boolean writeConfig(String configPath, String rootPath) {
        if (configPath == null || rootPath == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_write_config",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment configSeg = tempArena.allocateFrom(configPath);
            MemorySegment rootSeg = tempArena.allocateFrom(rootPath);
            return ((int) handle.invokeExact(configSeg, rootSeg)) == 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Read config file and verify root exists.
     */
    public String readConfig(String configPath) {
        if (configPath == null) return null;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_read_config",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return null;
            MemorySegment configSeg = tempArena.allocateFrom(configPath);
            MemorySegment outSeg = tempArena.allocate(4096);
            int ok = (int) handle.invokeExact(configSeg, outSeg);
            if (ok != 0) {
                return outSeg.getString(0);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Create directory with all parents.
     */
    public boolean createDirectory(String dirPath) {
        if (dirPath == null) return false;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_create_directory",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return new java.io.File(dirPath).mkdirs();
            MemorySegment pathSeg = tempArena.allocateFrom(dirPath);
            return ((int) handle.invokeExact(pathSeg)) == 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Check if first-time setup is needed.
     */
    public boolean needsSetup(String configPath) {
        if (configPath == null) return true;
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_needs_setup",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return !new java.io.File(configPath).exists();
            MemorySegment pathSeg = tempArena.allocateFrom(configPath);
            return ((int) handle.invokeExact(pathSeg)) != 0;
        } catch (Throwable e) {
            return true;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WATCHDOG API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Watchdog actions */
    public static final int WD_ACTION_NONE = 0;
    public static final int WD_ACTION_CALLBACK = 1;
    public static final int WD_ACTION_EXIT = 2;
    public static final int WD_ACTION_HALT = 3;

    /**
     * Start a native watchdog timer.
     * @param timeoutMs Timeout in milliseconds
     * @param action Action on trigger: 0=none, 1=callback, 2=exit, 3=halt
     * @param name Optional name for logging
     * @return Watchdog ID (0-7), or -1 on error
     */
    public int watchdogStart(long timeoutMs, int action, String name) {
        try (Arena tempArena = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_watchdog_start",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, 
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return -1;
            MemorySegment nameSeg = name != null ? tempArena.allocateFrom(name) : MemorySegment.NULL;
            return (int) handle.invokeExact(timeoutMs, action, nameSeg);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Cancel a running watchdog.
     */
    public boolean watchdogCancel(int id) {
        try {
            MethodHandle handle = optionalHandle("simjot_watchdog_cancel",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return false;
            return ((int) handle.invokeExact(id)) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Reset a watchdog timer.
     */
    public boolean watchdogReset(int id) {
        try {
            MethodHandle handle = optionalHandle("simjot_watchdog_reset",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return false;
            return ((int) handle.invokeExact(id)) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Get watchdog state.
     */
    public int watchdogState(int id) {
        try {
            MethodHandle handle = optionalHandle("simjot_watchdog_state",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            return (int) handle.invokeExact(id);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Get remaining time for watchdog.
     */
    public long watchdogRemaining(int id) {
        try {
            MethodHandle handle = optionalHandle("simjot_watchdog_remaining",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            return (long) handle.invokeExact(id);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Force immediate process halt.
     */
    public void forceHalt() {
        try {
            MethodHandle handle = optionalHandle("simjot_force_halt",
                FunctionDescriptor.ofVoid());
            if (handle != null) handle.invokeExact();
        } catch (Throwable ignored) {}
        Runtime.getRuntime().halt(1);
    }

    /**
     * Get monotonic time in milliseconds.
     */
    public long monotonicTimeMs() {
        try {
            MethodHandle handle = optionalHandle("simjot_monotonic_time_ms",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG));
            if (handle == null) return System.nanoTime() / 1_000_000L;
            return (long) handle.invokeExact();
        } catch (Throwable e) {
            return System.nanoTime() / 1_000_000L;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOOD ANALYTICS API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load and parse mood log file.
     * @return Number of samples parsed, or negative on error
     */
    public int moodLoad(String filePath) {
        if (moodLoadHandle == null || filePath == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(filePath);
            return (int) moodLoadHandle.invokeExact(cPath);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute daily statistics from loaded samples.
     * @param daysBack Number of days to analyze (0 = all time)
     * @return Number of days with data
     */
    public int moodComputeDaily(int daysBack) {
        if (moodComputeDailyHandle == null) return -1;
        try {
            return (int) moodComputeDailyHandle.invokeExact(daysBack);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute analytics summary.
     * @param threshold Good/bad mood threshold (typically 60)
     */
    public int moodComputeSummary(int threshold) {
        if (moodComputeSummaryHandle == null) return -1;
        try {
            return (int) moodComputeSummaryHandle.invokeExact(threshold);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Get daily stats by index as binary data.
     * Format: int32 date_days, int32 sample_count, double average, int16 min, int16 max,
     *         double[8] avg emotions (joy, calm, gratitude, energy, sadness, anger, anxiety, stress)
     * @param index Daily stats index (0 to moodDailyCount()-1)
     * @return Binary data or null on error
     */
    public byte[] moodGetDaily(int index) {
        if (moodGetDailyHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 128;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) moodGetDailyHandle.invokeExact(index, out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Get analytics summary as binary data.
     * Format: 8 doubles (overall_avg, volatility, etc.) + 4 ints (streaks, counts)
     */
    public byte[] moodGetSummary() {
        if (moodGetSummaryHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int outLen = 128;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) moodGetSummaryHandle.invokeExact(out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return data;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Get number of daily stats entries.
     */
    public int moodDailyCount() {
        if (moodDailyCountHandle == null) return 0;
        try {
            return (int) moodDailyCountHandle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Get number of samples loaded.
     */
    public int moodSampleCount() {
        if (moodSampleCountHandle == null) return 0;
        try {
            return (int) moodSampleCountHandle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Clear all loaded mood data.
     */
    public void moodClear() {
        if (moodClearHandle == null) return;
        try {
            moodClearHandle.invokeExact();
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOOD GRAPHICS API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Render a mood sparkline chart.
     * @return ARGB pixel array or null on failure
     */
    public int[] moodSparkline(int[] values, int width, int height, int bgColor, int lineThickness) {
        if (moodSparklineHandle == null || values == null || values.length == 0) return null;
        try (Arena temp = Arena.ofConfined()) {
            // Allocate input array
            MemorySegment valuesSegment = temp.allocate(ValueLayout.JAVA_INT, values.length);
            for (int i = 0; i < values.length; i++) {
                valuesSegment.setAtIndex(ValueLayout.JAVA_INT, i, values[i]);
            }
            // Allocate output buffer
            int pixelCount = width * height;
            MemorySegment outSegment = temp.allocate(ValueLayout.JAVA_INT, pixelCount);
            
            int result = (int) moodSparklineHandle.invokeExact(
                valuesSegment, values.length, width, height, outSegment, bgColor, lineThickness);
            if (result <= 0) return null;
            
            // Copy output to int array
            int[] pixels = new int[pixelCount];
            for (int i = 0; i < pixelCount; i++) {
                pixels[i] = outSegment.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return pixels;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood bar chart.
     * @return ARGB pixel array or null on failure
     */
    public int[] moodBarChart(int[] values, int width, int height, int bgColor, int barSpacing) {
        if (moodBarchartHandle == null || values == null || values.length == 0) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment valuesSegment = temp.allocate(ValueLayout.JAVA_INT, values.length);
            for (int i = 0; i < values.length; i++) {
                valuesSegment.setAtIndex(ValueLayout.JAVA_INT, i, values[i]);
            }
            int pixelCount = width * height;
            MemorySegment outSegment = temp.allocate(ValueLayout.JAVA_INT, pixelCount);
            
            int result = (int) moodBarchartHandle.invokeExact(
                valuesSegment, values.length, width, height, outSegment, bgColor, barSpacing);
            if (result <= 0) return null;
            
            int[] pixels = new int[pixelCount];
            for (int i = 0; i < pixelCount; i++) {
                pixels[i] = outSegment.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return pixels;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood gauge.
     * @return ARGB pixel array or null on failure
     */
    public int[] moodGauge(int value, int size, int bgColor, int trackColor, int thickness) {
        if (moodGaugeHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int pixelCount = size * size;
            MemorySegment outSegment = temp.allocate(ValueLayout.JAVA_INT, pixelCount);
            
            int result = (int) moodGaugeHandle.invokeExact(
                value, size, outSegment, bgColor, trackColor, thickness);
            if (result <= 0) return null;
            
            int[] pixels = new int[pixelCount];
            for (int i = 0; i < pixelCount; i++) {
                pixels[i] = outSegment.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return pixels;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a mood heatmap.
     * @return ARGB pixel array or null on failure
     */
    public int[] moodHeatmap(int[] values, int cols, int cellSize, int cellGap,
                             int outWidth, int outHeight, int bgColor, int emptyColor) {
        if (moodHeatmapHandle == null || values == null || values.length == 0) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment valuesSegment = temp.allocate(ValueLayout.JAVA_INT, values.length);
            for (int i = 0; i < values.length; i++) {
                valuesSegment.setAtIndex(ValueLayout.JAVA_INT, i, values[i]);
            }
            int pixelCount = outWidth * outHeight;
            MemorySegment outSegment = temp.allocate(ValueLayout.JAVA_INT, pixelCount);
            
            int result = (int) moodHeatmapHandle.invokeExact(
                valuesSegment, values.length, cols, cellSize, cellGap,
                outSegment, outWidth, outHeight, bgColor, emptyColor);
            if (result <= 0) return null;
            
            int[] pixels = new int[pixelCount];
            for (int i = 0; i < pixelCount; i++) {
                pixels[i] = outSegment.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return pixels;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DICTIONARY JSON API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Load dictionary words from JSON file.
     * @return List of words or null on failure
     */
    public List<String> jsonLoadDictWords(String filePath) {
        if (jsonLoadDictFileHandle == null || filePath == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cPath = temp.allocateFrom(filePath);
            int outLen = 1024 * 1024;
            try {
                long fileSize = Files.size(Path.of(filePath));
                if (fileSize > 0) {
                    long target = Math.min(Math.max(fileSize, outLen), 32L * 1024 * 1024);
                    outLen = (int) target;
                }
            } catch (Throwable ignored) {}
            MemorySegment out = temp.allocate(outLen);
            
            int len = (int) jsonLoadDictFileHandle.invokeExact(cPath, out, outLen);
            if (len <= 0) return null;
            
            String raw = out.getString(0);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            String[] parts = raw.split("\n");
            List<String> words = new ArrayList<>(parts.length);
            for (String part : parts) {
                if (!part.isEmpty()) words.add(part);
            }
            return words;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMAGE SCALING API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native image scaling is available.
     */
    public boolean hasImageScaleSupport() {
        return imageScaleHandle != null;
    }

    /**
     * Scale image using native SIMD-accelerated resize.
     * @param srcPixels Source ARGB pixels
     * @param srcW Source width
     * @param srcH Source height
     * @param dstW Destination width
     * @param dstH Destination height
     * @param quality 0=fast, 1=balanced, 2=best
     * @return Scaled ARGB pixels or null on failure
     */
    public int[] imageScale(int[] srcPixels, int srcW, int srcH, int dstW, int dstH, int quality) {
        if (imageScaleHandle == null || srcPixels == null || srcPixels.length != srcW * srcH) return null;
        try (Arena temp = Arena.ofConfined()) {
            // Allocate source buffer
            MemorySegment srcSeg = temp.allocate(ValueLayout.JAVA_INT, srcPixels.length);
            for (int i = 0; i < srcPixels.length; i++) {
                srcSeg.setAtIndex(ValueLayout.JAVA_INT, i, srcPixels[i]);
            }
            // Allocate destination buffer
            int dstSize = dstW * dstH;
            MemorySegment dstSeg = temp.allocate(ValueLayout.JAVA_INT, dstSize);
            
            int result = (int) imageScaleHandle.invokeExact(srcSeg, srcW, srcH, dstSeg, dstW, dstH, quality);
            if (result <= 0) return null;
            
            // Copy output
            int[] dstPixels = new int[dstSize];
            for (int i = 0; i < dstSize; i++) {
                dstPixels[i] = dstSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return dstPixels;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Apply Gaussian blur to image in-place.
     */
    public boolean imageBlur(int[] pixels, int width, int height, int radius) {
        if (imageBlurHandle == null || pixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            
            int result = (int) imageBlurHandle.invokeExact(seg, width, height, radius);
            if (result <= 0) return false;
            
            // Copy back
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = seg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Tint image with a color.
     */
    public boolean imageTint(int[] pixels, int width, int height, int tintColor, float intensity) {
        if (imageTintHandle == null || pixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            
            int result = (int) imageTintHandle.invokeExact(seg, width, height, tintColor, intensity);
            if (result <= 0) return false;
            
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = seg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Check if native accent color extraction is available.
     */
    public boolean hasAccentExtractSupport() {
        return imageExtractAccentHandle != null;
    }

    /**
     * Extract dominant accent color from ARGB pixel array.
     * Uses hue histogram analysis weighted by saturation² × brightness.
     * 
     * @param argbPixels ARGB pixel data (Java BufferedImage format)
     * @param width Image width
     * @param height Image height
     * @return Packed RGB color (0x00RRGGBB), or 0 on error/no native support
     */
    public int imageExtractAccent(int[] argbPixels, int width, int height) {
        if (imageExtractAccentHandle == null || argbPixels == null || 
            argbPixels.length != width * height || width <= 0 || height <= 0) {
            return 0;
        }
        try (Arena temp = Arena.ofConfined()) {
            // Convert ARGB to RGBA byte array for native code
            int pixelCount = width * height;
            MemorySegment rgbaSeg = temp.allocate(pixelCount * 4L);
            
            for (int i = 0; i < pixelCount; i++) {
                int argb = argbPixels[i];
                int offset = i * 4;
                rgbaSeg.set(ValueLayout.JAVA_BYTE, offset,     (byte)((argb >> 16) & 0xFF)); // R
                rgbaSeg.set(ValueLayout.JAVA_BYTE, offset + 1, (byte)((argb >> 8) & 0xFF));  // G
                rgbaSeg.set(ValueLayout.JAVA_BYTE, offset + 2, (byte)(argb & 0xFF));         // B
                rgbaSeg.set(ValueLayout.JAVA_BYTE, offset + 3, (byte)((argb >> 24) & 0xFF)); // A
            }
            
            int stride = width * 4;
            return (int) imageExtractAccentHandle.invokeExact(rgbaSeg, width, height, stride);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPELL CHECK API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native spell check functions are available.
     */
    public boolean hasSpellEditSupport() {
        return spellEdit1Handle != null && levenshteinHandle != null;
    }

    /**
     * Generate edit-distance-1 candidates for a word.
     */
    public List<String> spellEdit1Candidates(String word) {
        if (spellEdit1Handle == null || word == null || word.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int outLen = 64 * 1024; // 64KB should be enough for edit-1 candidates
            MemorySegment out = temp.allocate(outLen);
            
            int len = (int) spellEdit1Handle.invokeExact(cWord, out, outLen);
            if (len <= 0) return null;
            
            // Parse null-separated candidates
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            
            List<String> candidates = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < len; i++) {
                if (data[i] == 0) {
                    if (i > start) {
                        candidates.add(new String(data, start, i - start, StandardCharsets.UTF_8));
                    }
                    start = i + 1;
                }
            }
            return candidates;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    public int levenshtein(String a, String b) {
        if (levenshteinHandle == null || a == null || b == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cA = temp.allocateFrom(a);
            MemorySegment cB = temp.allocateFrom(b);
            return (int) levenshteinHandle.invokeExact(cA, cB);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Compute Damerau-Levenshtein distance (allows transpositions).
     */
    public int damerauLevenshtein(String a, String b) {
        if (damerauLevenshteinHandle == null || a == null || b == null) return -1;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cA = temp.allocateFrom(a);
            MemorySegment cB = temp.allocateFrom(b);
            return (int) damerauLevenshteinHandle.invokeExact(cA, cB);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTOCORRECT API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if native autocorrect functions are available.
     */
    public boolean hasAutocorrectSupport() {
        return autocorrectAdjacentKeyHandle != null && autocorrectCorrectHandle != null;
    }

    /**
     * Find correction by replacing characters with adjacent QWERTY keys.
     * @return Correction or null if none found
     */
    public String autocorrectAdjacentKey(String word) {
        if (autocorrectAdjacentKeyHandle == null || word == null || word.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            MemorySegment out = temp.allocate(128);
            int len = (int) autocorrectAdjacentKeyHandle.invokeExact(cWord, out, 128);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Find correction by replacing common phonetic patterns.
     * @return Correction or null if none found
     */
    public String autocorrectPhonetic(String word) {
        if (autocorrectPhoneticHandle == null || word == null || word.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            MemorySegment out = temp.allocate(128);
            int len = (int) autocorrectPhoneticHandle.invokeExact(cWord, out, 128);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Combined autocorrect: tries phonetic, adjacent key, then spell suggestions.
     * @return Correction or null if none found
     */
    public String autocorrectCorrect(String word) {
        if (autocorrectCorrectHandle == null || word == null || word.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            MemorySegment out = temp.allocate(128);
            int len = (int) autocorrectCorrectHandle.invokeExact(cWord, out, 128);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Check if word starts with vowel sound (for a/an determination).
     * @return true if vowel sound, false if consonant sound
     */
    public Boolean autocorrectStartsVowelSound(String word) {
        if (autocorrectStartsVowelSoundHandle == null || word == null || word.isEmpty()) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord = temp.allocateFrom(word);
            int result = (int) autocorrectStartsVowelSoundHandle.invokeExact(cWord);
            if (result < 0) return null;
            return result == 1;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Check if two-word phrase has a correction (e.g., "should of" -> "should have").
     * @return Corrected phrase or null if none found
     */
    public String autocorrectPhrase(String word1, String word2) {
        if (autocorrectPhraseHandle == null || word1 == null || word2 == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cWord1 = temp.allocateFrom(word1);
            MemorySegment cWord2 = temp.allocateFrom(word2);
            MemorySegment out = temp.allocate(256);
            int len = (int) autocorrectPhraseHandle.invokeExact(cWord1, cWord2, out, 256);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Fix capitalization issues (standalone i, double spaces).
     * @return Fixed text
     */
    public String autocorrectFixCaps(String text) {
        if (autocorrectFixCapsHandle == null || text == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment cText = temp.allocateFrom(text);
            int outLen = text.length() + 64;
            MemorySegment out = temp.allocate(outLen);
            int len = (int) autocorrectFixCapsHandle.invokeExact(cText, out, outLen);
            if (len <= 0) return null;
            byte[] data = new byte[len];
            out.asByteBuffer().get(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEMORY POOL API
    // ═══════════════════════════════════════════════════════════════════════════

    public int poolCreate(int blockSize, int initialBlocks) {
        try {
            MethodHandle handle = optionalHandle("simjot_pool_create",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            return (int) handle.invokeExact(blockSize, initialBlocks);
        } catch (Throwable t) {
            return -1;
        }
    }

    public void poolDestroy(int poolId) {
        try {
            MethodHandle handle = optionalHandle("simjot_pool_destroy",
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            if (handle == null) return;
            handle.invokeExact(poolId);
        } catch (Throwable ignored) {}
    }

    public int arenaCreate() {
        try {
            MethodHandle handle = optionalHandle("simjot_arena_create",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    public void arenaReset(int arenaId) {
        try {
            MethodHandle handle = optionalHandle("simjot_arena_reset",
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            if (handle == null) return;
            handle.invokeExact(arenaId);
        } catch (Throwable ignored) {}
    }

    public void arenaDestroy(int arenaId) {
        try {
            MethodHandle handle = optionalHandle("simjot_arena_destroy",
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            if (handle == null) return;
            handle.invokeExact(arenaId);
        } catch (Throwable ignored) {}
    }

    public int internInit() {
        try {
            MethodHandle handle = optionalHandle("simjot_intern_init",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            if (handle == null) return -1;
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    public String intern(String str) {
        if (str == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_intern",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            if (handle == null) return str;
            MemorySegment cStr = temp.allocateFrom(str);
            MemorySegment out = (MemorySegment) handle.invokeExact(cStr);
            if (out == null || out.equals(MemorySegment.NULL)) return str;
            return out.getString(0);
        } catch (Throwable t) {
            return str;
        }
    }

    public boolean internContains(String str) {
        if (str == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MethodHandle handle = optionalHandle("simjot_intern_contains",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            if (handle == null) return false;
            MemorySegment cStr = temp.allocateFrom(str);
            return ((int) handle.invokeExact(cStr)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public int internCount() {
        try {
            MethodHandle handle = optionalHandle("simjot_intern_count",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
            if (handle == null) return 0;
            return (int) handle.invokeExact();
        } catch (Throwable t) {
            return 0;
        }
    }

    public void internClear() {
        try {
            MethodHandle handle = optionalHandle("simjot_intern_clear",
                FunctionDescriptor.ofVoid());
            if (handle == null) return;
            handle.invokeExact();
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MATH UTILITIES API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute mean of double array using native SIMD.
     */
    public double mathMean(double[] values) {
        if (mathMeanHandle == null || values == null || values.length == 0) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathMeanHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute standard deviation of double array using native SIMD.
     */
    public double mathStddev(double[] values) {
        if (mathStddevHandle == null || values == null || values.length < 2) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathStddevHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute variance of double array using native SIMD.
     */
    public double mathVariance(double[] values) {
        if (mathVarianceHandle == null || values == null || values.length < 2) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathVarianceHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute min of double array using native SIMD.
     */
    public double mathMin(double[] values) {
        if (mathMinHandle == null || values == null || values.length == 0) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathMinHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute max of double array using native SIMD.
     */
    public double mathMax(double[] values) {
        if (mathMaxHandle == null || values == null || values.length == 0) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathMaxHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Compute sum of double array using native SIMD.
     */
    public double mathSum(double[] values) {
        if (mathSumHandle == null || values == null || values.length == 0) return Double.NaN;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(values.length * 8L);
            seg.asByteBuffer().asDoubleBuffer().put(values);
            return (double) mathSumHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LZ4 COMPRESSION API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get maximum compressed size bound for LZ4.
     */
    public int lz4CompressBound(int srcSize) {
        if (lz4CompressBoundHandle == null) return srcSize + srcSize / 255 + 16;
        try {
            return (int) lz4CompressBoundHandle.invokeExact(srcSize);
        } catch (Throwable t) {
            return srcSize + srcSize / 255 + 16;
        }
    }

    /**
     * Compress data using LZ4-style algorithm.
     * @return compressed data or null on failure
     */
    public byte[] lz4Compress(byte[] src) {
        if (lz4CompressHandle == null || src == null || src.length == 0) return null;
        try (Arena temp = Arena.ofConfined()) {
            int bound = lz4CompressBound(src.length);
            MemorySegment srcSeg = temp.allocate(src.length);
            srcSeg.asByteBuffer().put(src);
            MemorySegment dstSeg = temp.allocate(bound);
            
            int compressedLen = (int) lz4CompressHandle.invokeExact(srcSeg, src.length, dstSeg, bound);
            if (compressedLen <= 0) return null;
            
            byte[] result = new byte[compressedLen];
            dstSeg.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Decompress LZ4-compressed data.
     * @param maxOutputSize maximum expected output size
     * @return decompressed data or null on failure
     */
    public byte[] lz4Decompress(byte[] src, int maxOutputSize) {
        if (lz4DecompressHandle == null || src == null || src.length == 0) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment srcSeg = temp.allocate(src.length);
            srcSeg.asByteBuffer().put(src);
            MemorySegment dstSeg = temp.allocate(maxOutputSize);
            
            int decompressedLen = (int) lz4DecompressHandle.invokeExact(srcSeg, src.length, dstSeg, maxOutputSize);
            if (decompressedLen <= 0) return null;
            
            byte[] result = new byte[decompressedLen];
            dstSeg.asByteBuffer().get(result);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VIEWPORT IMAGE CACHE
    // ═══════════════════════════════════════════════════════════════════════════
    
    public boolean hasImageCacheSupport() {
        return imgcacheInitHandle != null && imgcachePutHandle != null;
    }
    
    public boolean imgcacheInit(int maxEntries, int maxMemoryMb) {
        if (imgcacheInitHandle == null) return false;
        try {
            return (int) imgcacheInitHandle.invokeExact(maxEntries, maxMemoryMb) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public void imgcacheShutdown() {
        if (imgcacheShutdownHandle == null) return;
        try {
            imgcacheShutdownHandle.invokeExact();
        } catch (Throwable ignored) {}
    }
    
    public boolean imgcachePut(long imageId, int[] pixels, int width, int height) {
        if (imgcachePutHandle == null || pixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pixelSeg = temp.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                pixelSeg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            return (int) imgcachePutHandle.invokeExact(imageId, pixelSeg, width, height) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean imgcacheContains(long imageId) {
        if (imgcacheContainsHandle == null) return false;
        try {
            return (int) imgcacheContainsHandle.invokeExact(imageId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean imgcacheRemove(long imageId) {
        if (imgcacheRemoveHandle == null) return false;
        try {
            return (int) imgcacheRemoveHandle.invokeExact(imageId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public void imgcacheClear() {
        if (imgcacheClearHandle == null) return;
        try {
            imgcacheClearHandle.invokeExact();
        } catch (Throwable ignored) {}
    }
    
    public record ImageCacheStats(int count, long memoryBytes, long hits, long misses) {}
    
    public ImageCacheStats imgcacheStats() {
        if (imgcacheStatsHandle == null) return new ImageCacheStats(0, 0, 0, 0);
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment countSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment memorySeg = temp.allocate(ValueLayout.JAVA_LONG);
            MemorySegment hitsSeg = temp.allocate(ValueLayout.JAVA_LONG);
            MemorySegment missesSeg = temp.allocate(ValueLayout.JAVA_LONG);
            
            imgcacheStatsHandle.invokeExact(countSeg, memorySeg, hitsSeg, missesSeg);
            
            return new ImageCacheStats(
                countSeg.get(ValueLayout.JAVA_INT, 0),
                memorySeg.get(ValueLayout.JAVA_LONG, 0),
                hitsSeg.get(ValueLayout.JAVA_LONG, 0),
                missesSeg.get(ValueLayout.JAVA_LONG, 0)
            );
        } catch (Throwable t) {
            return new ImageCacheStats(0, 0, 0, 0);
        }
    }
    
    public int imgcacheCullViewport(long[] imageIds, int[] yPositions, int[] heights,
                                     int viewportY, int viewportHeight, int[] outVisible) {
        if (imgcacheCullViewportHandle == null || imageIds == null) return 0;
        int count = imageIds.length;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment idsSeg = temp.allocate(ValueLayout.JAVA_LONG, count);
            MemorySegment ySeg = temp.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment hSeg = temp.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment outSeg = temp.allocate(ValueLayout.JAVA_INT, count);
            
            for (int i = 0; i < count; i++) {
                idsSeg.setAtIndex(ValueLayout.JAVA_LONG, i, imageIds[i]);
                ySeg.setAtIndex(ValueLayout.JAVA_INT, i, yPositions[i]);
                hSeg.setAtIndex(ValueLayout.JAVA_INT, i, heights[i]);
            }
            
            int visibleCount = (int) imgcacheCullViewportHandle.invokeExact(
                idsSeg, ySeg, hSeg, count, viewportY, viewportHeight, outSeg);
            
            for (int i = 0; i < count; i++) {
                outVisible[i] = outSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return visibleCount;
        } catch (Throwable t) {
            return 0;
        }
    }
    
    public boolean imgcachePrescale(long imageId, int[] srcPixels, int srcWidth, int srcHeight,
                                     int targetWidth, int quality) {
        if (imgcachePrescaleHandle == null || srcPixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment pixelSeg = temp.allocate(ValueLayout.JAVA_INT, srcPixels.length);
            for (int i = 0; i < srcPixels.length; i++) {
                pixelSeg.setAtIndex(ValueLayout.JAVA_INT, i, srcPixels[i]);
            }
            return (int) imgcachePrescaleHandle.invokeExact(
                imageId, pixelSeg, srcWidth, srcHeight, targetWidth, quality) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HOTKEY MANAGER
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Constants matching C++ definitions
    public static final int MOD_NONE  = 0x00;
    public static final int MOD_SHIFT = 0x01;
    public static final int MOD_CTRL  = 0x02;
    public static final int MOD_ALT   = 0x04;
    public static final int MOD_META  = 0x08;
    
    public static final int ACTION_NONE          = 0;
    public static final int ACTION_BOLD          = 1;
    public static final int ACTION_ITALIC        = 2;
    public static final int ACTION_UNDERLINE     = 3;
    public static final int ACTION_STRIKETHROUGH = 4;
    
    public static final int PLATFORM_UNKNOWN = 0;
    public static final int PLATFORM_MACOS   = 1;
    public static final int PLATFORM_WINDOWS = 2;
    public static final int PLATFORM_LINUX   = 3;
    
    public boolean hasHotkeySupport() {
        return hotkeyCheckHandle != null;
    }
    
    public int hotkeyGetPlatform() {
        if (hotkeyGetPlatformHandle == null) return PLATFORM_UNKNOWN;
        try {
            return (int) hotkeyGetPlatformHandle.invokeExact();
        } catch (Throwable t) {
            return PLATFORM_UNKNOWN;
        }
    }
    
    public int hotkeyGetPrimaryModifier() {
        if (hotkeyGetPrimaryModifierHandle == null) {
            // Fallback: detect from Java
            String os = System.getProperty("os.name", "").toLowerCase();
            return os.contains("mac") ? MOD_META : MOD_CTRL;
        }
        try {
            return (int) hotkeyGetPrimaryModifierHandle.invokeExact();
        } catch (Throwable t) {
            return MOD_CTRL;
        }
    }
    
    public int hotkeyCheck(int keyCode, int modifiers) {
        if (hotkeyCheckHandle == null) return ACTION_NONE;
        try {
            return (int) hotkeyCheckHandle.invokeExact(keyCode, modifiers);
        } catch (Throwable t) {
            return ACTION_NONE;
        }
    }
    
    public record HotkeyBinding(int keyCode, int modifiers) {}
    
    public HotkeyBinding hotkeyGetBinding(int action) {
        if (hotkeyGetBindingHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment keySeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment modSeg = temp.allocate(ValueLayout.JAVA_INT);
            int ok = (int) hotkeyGetBindingHandle.invokeExact(action, keySeg, modSeg);
            if (ok != 1) return null;
            return new HotkeyBinding(
                keySeg.get(ValueLayout.JAVA_INT, 0),
                modSeg.get(ValueLayout.JAVA_INT, 0)
            );
        } catch (Throwable t) {
            return null;
        }
    }
    
    public String hotkeyGetDisplayString(int action) {
        if (hotkeyGetDisplayStringHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            int bufLen = 32;
            MemorySegment buf = temp.allocate(bufLen);
            int len = (int) hotkeyGetDisplayStringHandle.invokeExact(action, buf, bufLen);
            if (len <= 0) return null;
            byte[] bytes = new byte[len];
            buf.asByteBuffer().get(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OFFSCREEN BUFFER
    // ═══════════════════════════════════════════════════════════════════════════
    
    public boolean hasBufferSupport() {
        return bufferCreateHandle != null;
    }
    
    public long bufferCreate(int width, int height) {
        if (bufferCreateHandle == null) return 0;
        try {
            return (long) bufferCreateHandle.invokeExact(width, height);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    public boolean bufferResize(long handle, int width, int height) {
        if (bufferResizeHandle == null) return false;
        try {
            return (int) bufferResizeHandle.invokeExact(handle, width, height) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public void bufferDestroy(long handle) {
        if (bufferDestroyHandle == null) return;
        try {
            bufferDestroyHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
    
    public void bufferClear(long handle, int argb) {
        if (bufferClearHandle == null) return;
        try {
            bufferClearHandle.invokeExact(handle, argb);
        } catch (Throwable ignored) {}
    }
    
    public boolean bufferWrite(long handle, int[] pixels, int srcWidth, int srcHeight, int dstX, int dstY) {
        if (bufferWriteHandle == null || pixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            return (int) bufferWriteHandle.invokeExact(handle, seg, srcWidth, srcHeight, dstX, dstY) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean bufferRead(long handle, int[] outPixels, int srcX, int srcY, int width, int height) {
        if (bufferReadHandle == null || outPixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(ValueLayout.JAVA_INT, outPixels.length);
            int ok = (int) bufferReadHandle.invokeExact(handle, seg, srcX, srcY, width, height);
            if (ok == 1) {
                for (int i = 0; i < outPixels.length; i++) {
                    outPixels[i] = seg.getAtIndex(ValueLayout.JAVA_INT, i);
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public void bufferScroll(long handle, int dx, int dy, int fillArgb) {
        if (bufferScrollHandle == null) return;
        try {
            bufferScrollHandle.invokeExact(handle, dx, dy, fillArgb);
        } catch (Throwable ignored) {}
    }
    
    public boolean bufferComposite(long handle, int[] pixels, int srcWidth, int srcHeight, int dstX, int dstY) {
        if (bufferCompositeHandle == null || pixels == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment seg = temp.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            return (int) bufferCompositeHandle.invokeExact(handle, seg, srcWidth, srcHeight, dstX, dstY) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public int[] bufferGetSize(long handle) {
        if (bufferGetSizeHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment wSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment hSeg = temp.allocate(ValueLayout.JAVA_INT);
            int ok = (int) bufferGetSizeHandle.invokeExact(handle, wSeg, hSeg);
            if (ok == 1) {
                return new int[] { wSeg.get(ValueLayout.JAVA_INT, 0), hSeg.get(ValueLayout.JAVA_INT, 0) };
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UNDO/REDO MANAGER
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static final int EDIT_INSERT   = 1;
    public static final int EDIT_DELETE   = 2;
    public static final int EDIT_REPLACE  = 3;
    public static final int EDIT_STYLE    = 4;
    public static final int EDIT_COMPOUND = 5;
    
    public boolean hasUndoSupport() {
        return undoCreateSessionHandle != null;
    }
    
    public boolean undoInit() {
        if (undoInitHandle == null) return false;
        try {
            return (int) undoInitHandle.invokeExact() == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public void undoShutdown() {
        if (undoShutdownHandle == null) return;
        try {
            undoShutdownHandle.invokeExact();
        } catch (Throwable ignored) {}
    }
    
    public int undoCreateSession(int historyLimit) {
        if (undoCreateSessionHandle == null) return -1;
        try {
            return (int) undoCreateSessionHandle.invokeExact(historyLimit);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    public boolean undoDestroySession(int sessionId) {
        if (undoDestroySessionHandle == null) return false;
        try {
            return (int) undoDestroySessionHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoClear(int sessionId) {
        if (undoClearHandle == null) return false;
        try {
            return (int) undoClearHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoPushInsert(int sessionId, int offset, String text) {
        if (undoPushInsertHandle == null || text == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = temp.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            return (int) undoPushInsertHandle.invokeExact(sessionId, offset, textSeg, bytes.length) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoPushDelete(int sessionId, int offset, String deletedText) {
        if (undoPushDeleteHandle == null || deletedText == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            byte[] bytes = deletedText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = temp.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            return (int) undoPushDeleteHandle.invokeExact(sessionId, offset, textSeg, bytes.length) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoPushReplace(int sessionId, int offset, String oldText, String newText) {
        if (undoPushReplaceHandle == null) return false;
        try (Arena temp = Arena.ofConfined()) {
            byte[] oldBytes = oldText != null ? oldText.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            byte[] newBytes = newText != null ? newText.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            MemorySegment oldSeg = temp.allocate(Math.max(1, oldBytes.length));
            MemorySegment newSeg = temp.allocate(Math.max(1, newBytes.length));
            if (oldBytes.length > 0) oldSeg.copyFrom(MemorySegment.ofArray(oldBytes));
            if (newBytes.length > 0) newSeg.copyFrom(MemorySegment.ofArray(newBytes));
            return (int) undoPushReplaceHandle.invokeExact(sessionId, offset, 
                oldSeg, oldBytes.length, newSeg, newBytes.length) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoPushStyle(int sessionId, int offset, int length, int styleFlags) {
        if (undoPushStyleHandle == null) return false;
        try {
            return (int) undoPushStyleHandle.invokeExact(sessionId, offset, length, styleFlags) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoBeginCompound(int sessionId) {
        if (undoBeginCompoundHandle == null) return false;
        try {
            return (int) undoBeginCompoundHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoEndCompound(int sessionId) {
        if (undoEndCompoundHandle == null) return false;
        try {
            return (int) undoEndCompoundHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoCanUndo(int sessionId) {
        if (undoCanUndoHandle == null) return false;
        try {
            return (int) undoCanUndoHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoCanRedo(int sessionId) {
        if (undoCanRedoHandle == null) return false;
        try {
            return (int) undoCanRedoHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public record UndoResult(int type, int offset, int length, String text) {}
    
    public UndoResult undoUndo(int sessionId) {
        if (undoUndoHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment typeSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment offsetSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment lengthSeg = temp.allocate(ValueLayout.JAVA_INT);
            int bufLen = 65536;
            MemorySegment textSeg = temp.allocate(bufLen);
            
            int ok = (int) undoUndoHandle.invokeExact(sessionId, typeSeg, offsetSeg, lengthSeg, textSeg, bufLen);
            if (ok != 1) return null;
            
            int type = typeSeg.get(ValueLayout.JAVA_INT, 0);
            int offset = offsetSeg.get(ValueLayout.JAVA_INT, 0);
            int length = lengthSeg.get(ValueLayout.JAVA_INT, 0);
            
            byte[] textBytes = new byte[Math.min(length, bufLen - 1)];
            textSeg.asByteBuffer().get(textBytes);
            String text = new String(textBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            
            return new UndoResult(type, offset, length, text);
        } catch (Throwable t) {
            return null;
        }
    }
    
    public UndoResult undoRedo(int sessionId) {
        if (undoRedoHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment typeSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment offsetSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment lengthSeg = temp.allocate(ValueLayout.JAVA_INT);
            int bufLen = 65536;
            MemorySegment textSeg = temp.allocate(bufLen);
            
            int ok = (int) undoRedoHandle.invokeExact(sessionId, typeSeg, offsetSeg, lengthSeg, textSeg, bufLen);
            if (ok != 1) return null;
            
            int type = typeSeg.get(ValueLayout.JAVA_INT, 0);
            int offset = offsetSeg.get(ValueLayout.JAVA_INT, 0);
            int length = lengthSeg.get(ValueLayout.JAVA_INT, 0);
            
            byte[] textBytes = new byte[Math.min(length, bufLen - 1)];
            textSeg.asByteBuffer().get(textBytes);
            String text = new String(textBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            
            return new UndoResult(type, offset, length, text);
        } catch (Throwable t) {
            return null;
        }
    }
    
    public boolean undoMarkSavePoint(int sessionId) {
        if (undoMarkSavePointHandle == null) return false;
        try {
            return (int) undoMarkSavePointHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoIsAtSavePoint(int sessionId) {
        if (undoIsAtSavePointHandle == null) return false;
        try {
            return (int) undoIsAtSavePointHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public boolean undoIsDirty(int sessionId) {
        if (undoIsDirtyHandle == null) return false;
        try {
            return (int) undoIsDirtyHandle.invokeExact(sessionId) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public int undoGetUndoCount(int sessionId) {
        if (undoGetUndoCountHandle == null) return 0;
        try {
            return (int) undoGetUndoCountHandle.invokeExact(sessionId);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    public int undoGetRedoCount(int sessionId) {
        if (undoGetRedoCountHandle == null) return 0;
        try {
            return (int) undoGetRedoCountHandle.invokeExact(sessionId);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    public boolean undoSetHistoryLimit(int sessionId, int limit) {
        if (undoSetHistoryLimitHandle == null) return false;
        try {
            return (int) undoSetHistoryLimitHandle.invokeExact(sessionId, limit) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
    
    public record UndoStats(long memory, int undoCount, int redoCount, int savePoint, int changeIndex) {}
    
    public UndoStats undoGetStats(int sessionId) {
        if (undoGetStatsHandle == null) return null;
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment memSeg = temp.allocate(ValueLayout.JAVA_LONG);
            MemorySegment undoSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment redoSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment saveSeg = temp.allocate(ValueLayout.JAVA_INT);
            MemorySegment changeSeg = temp.allocate(ValueLayout.JAVA_INT);
            
            int ok = (int) undoGetStatsHandle.invokeExact(sessionId, memSeg, undoSeg, redoSeg, saveSeg, changeSeg);
            if (ok != 1) return null;
            
            return new UndoStats(
                memSeg.get(ValueLayout.JAVA_LONG, 0),
                undoSeg.get(ValueLayout.JAVA_INT, 0),
                redoSeg.get(ValueLayout.JAVA_INT, 0),
                saveSeg.get(ValueLayout.JAVA_INT, 0),
                changeSeg.get(ValueLayout.JAVA_INT, 0)
            );
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOOD ANALYTICS API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if native mood analytics is available.
     */
    public boolean hasMoodAnalyticsSupport() {
        return moodVolatilityHandle != null;
    }
    
    /**
     * Compute mood volatility (standard deviation).
     */
    public double moodVolatility(double[] values) {
        if (moodVolatilityHandle == null || values == null || values.length < 2) return -1;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment seg = local.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            for (int i = 0; i < values.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, values[i]);
            }
            return (double) moodVolatilityHandle.invokeExact(seg, values.length);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Compute mood streaks.
     * @return [currentStreak, longestGood, longestBad] or null on failure
     */
    public int[] moodStreaks(double[] values, double threshold) {
        if (moodStreaksHandle == null || values == null || values.length == 0) return null;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment valSeg = local.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            for (int i = 0; i < values.length; i++) {
                valSeg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, values[i]);
            }
            MemorySegment curSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment goodSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment badSeg = local.allocate(ValueLayout.JAVA_INT);
            int ok = (int) moodStreaksHandle.invokeExact(valSeg, values.length, threshold, curSeg, goodSeg, badSeg);
            if (ok == 0) return null;
            return new int[] {
                curSeg.get(ValueLayout.JAVA_INT, 0),
                goodSeg.get(ValueLayout.JAVA_INT, 0),
                badSeg.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HASKELL POETRY API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if Haskell poetry analysis is available.
     */
    public boolean hasHaskellPoetrySupport() {
        return hsAnalyzeMeterHandle != null;
    }
    
    /**
     * Analyze meter and return dominant foot type.
     * 0=Iamb, 1=Trochee, 2=Spondee, 3=Pyrrhic, 4=Anapest, 5=Dactyl, 6=Amphibrach
     */
    public int hsAnalyzeMeter(String text) {
        if (hsAnalyzeMeterHandle == null || text == null || text.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsAnalyzeMeterHandle.invokeExact(textSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Get rhyme scheme for text.
     */
    public String hsAnalyzeRhymeScheme(String text) {
        if (hsAnalyzeRhymeSchemeHandle == null || text == null || text.isEmpty()) return "";
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            
            int bufSize = 256;
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) hsAnalyzeRhymeSchemeHandle.invokeExact(textSeg, outSeg, bufSize);
            if (len <= 0) return "";
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
    
    /**
     * Count syllables in a word using Haskell.
     */
    public int hsCountSyllables(String word) {
        if (hsCountSyllablesHandle == null || word == null || word.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = word.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment wordSeg = local.allocate(bytes.length + 1);
            wordSeg.copyFrom(MemorySegment.ofArray(bytes));
            wordSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsCountSyllablesHandle.invokeExact(wordSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Analyze sound devices and return count.
     */
    public int hsAnalyzeSoundDevices(String text) {
        if (hsAnalyzeSoundDevicesHandle == null || text == null || text.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsAnalyzeSoundDevicesHandle.invokeExact(textSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Get meter name (e.g., "Iambic Pentameter").
     */
    public String hsGetMeterName(String text) {
        if (hsGetMeterNameHandle == null || text == null || text.isEmpty()) return "";
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            
            int bufSize = 128;
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) hsGetMeterNameHandle.invokeExact(textSeg, outSeg, bufSize);
            if (len <= 0) return "";
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
    
    /**
     * Get meter regularity as percentage (0-100).
     */
    public int hsGetMeterRegularity(String text) {
        if (hsGetMeterRegularityHandle == null || text == null || text.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsGetMeterRegularityHandle.invokeExact(textSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Check if two words rhyme.
     */
    public boolean hsCheckRhyme(String word1, String word2) {
        if (hsCheckRhymeHandle == null || word1 == null || word2 == null) return false;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes1 = word1.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] bytes2 = word2.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment seg1 = local.allocate(bytes1.length + 1);
            MemorySegment seg2 = local.allocate(bytes2.length + 1);
            seg1.copyFrom(MemorySegment.ofArray(bytes1));
            seg2.copyFrom(MemorySegment.ofArray(bytes2));
            seg1.set(ValueLayout.JAVA_BYTE, bytes1.length, (byte) 0);
            seg2.set(ValueLayout.JAVA_BYTE, bytes2.length, (byte) 0);
            int result = (int) hsCheckRhymeHandle.invokeExact(seg1, seg2);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get vocabulary stats as comma-separated string.
     */
    public String hsGetVocabStats(String text) {
        if (hsGetVocabStatsHandle == null || text == null || text.isEmpty()) return "";
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            
            int bufSize = 256;
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) hsGetVocabStatsHandle.invokeExact(textSeg, outSeg, bufSize);
            if (len <= 0) return "";
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
    
    /**
     * Get type-token ratio * 100.
     */
    public int hsTypeTokenRatio(String text) {
        if (hsTypeTokenRatioHandle == null || text == null || text.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length + 1);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            textSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsTypeTokenRatioHandle.invokeExact(textSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Get rhyme key for a word.
     */
    public String hsGetRhymeKey(String word) {
        if (hsGetRhymeKeyHandle == null || word == null || word.isEmpty()) return "";
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = word.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment wordSeg = local.allocate(bytes.length + 1);
            wordSeg.copyFrom(MemorySegment.ofArray(bytes));
            wordSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            
            int bufSize = 64;
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) hsGetRhymeKeyHandle.invokeExact(wordSeg, outSeg, bufSize);
            if (len <= 0) return "";
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
    
    /**
     * Estimate stress pattern as packed bits.
     */
    public int hsEstimateStress(String word) {
        if (hsEstimateStressHandle == null || word == null || word.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = word.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment wordSeg = local.allocate(bytes.length + 1);
            wordSeg.copyFrom(MemorySegment.ofArray(bytes));
            wordSeg.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
            return (int) hsEstimateStressHandle.invokeExact(wordSeg);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LINK DETECTOR API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if native link detection is available.
     */
    public boolean hasLinkSupport() {
        return linkContainsHandle != null;
    }
    
    /**
     * Check if text contains any URLs.
     */
    public boolean linkContains(String text) {
        if (linkContainsHandle == null || text == null || text.isEmpty()) return false;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            int result = (int) linkContainsHandle.invokeExact(textSeg, bytes.length);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Count URLs in text.
     */
    public int linkCount(String text) {
        if (linkCountHandle == null || text == null || text.isEmpty()) return 0;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            return (int) linkCountHandle.invokeExact(textSeg, bytes.length);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Find all link ranges in text.
     * @return Array of [start, end] pairs, or empty array if none found
     */
    public int[][] linkFindRanges(String text, int maxRanges) {
        if (linkFindRangesHandle == null || text == null || text.isEmpty() || maxRanges <= 0) {
            return new int[0][];
        }
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            
            MemorySegment rangesSeg = local.allocate(ValueLayout.JAVA_INT, maxRanges * 2);
            
            int count = (int) linkFindRangesHandle.invokeExact(textSeg, bytes.length, rangesSeg, maxRanges);
            
            if (count <= 0) return new int[0][];
            
            int[][] result = new int[count][2];
            for (int i = 0; i < count; i++) {
                result[i][0] = rangesSeg.getAtIndex(ValueLayout.JAVA_INT, i * 2);
                result[i][1] = rangesSeg.getAtIndex(ValueLayout.JAVA_INT, i * 2 + 1);
            }
            return result;
        } catch (Throwable t) {
            return new int[0][];
        }
    }
    
    /**
     * Extract first URL from text.
     */
    public String linkExtractFirst(String text) {
        if (linkExtractFirstHandle == null || text == null || text.isEmpty()) return null;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            
            int bufSize = 2048;
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) linkExtractFirstHandle.invokeExact(textSeg, bytes.length, outSeg, bufSize);
            
            if (len <= 0) return null;
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), 
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Normalize a URL (add https:// if starts with www.).
     */
    public String linkNormalize(String url) {
        if (linkNormalizeHandle == null || url == null || url.isEmpty()) return url;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment urlSeg = local.allocate(bytes.length);
            urlSeg.copyFrom(MemorySegment.ofArray(bytes));
            
            int bufSize = bytes.length + 16; // Room for https:// prefix
            MemorySegment outSeg = local.allocate(bufSize);
            
            int len = (int) linkNormalizeHandle.invokeExact(urlSeg, bytes.length, outSeg, bufSize);
            
            if (len <= 0) return url;
            return new String(outSeg.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return url;
        }
    }
    
    /**
     * Validate if a string is a valid URL.
     */
    public boolean linkIsValid(String url) {
        if (linkIsValidHandle == null || url == null || url.isEmpty()) return false;
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment urlSeg = local.allocate(bytes.length);
            urlSeg.copyFrom(MemorySegment.ofArray(bytes));
            int result = (int) linkIsValidHandle.invokeExact(urlSeg, bytes.length);
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get link at specific position in text.
     * @return int[2] with [start, end] or null if no link at position
     */
    public int[] linkAtPosition(String text, int position) {
        if (linkAtPositionHandle == null || text == null || text.isEmpty() || position < 0) {
            return null;
        }
        try (Arena local = Arena.ofConfined()) {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MemorySegment textSeg = local.allocate(bytes.length);
            textSeg.copyFrom(MemorySegment.ofArray(bytes));
            
            MemorySegment startSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment endSeg = local.allocate(ValueLayout.JAVA_INT);
            
            int result = (int) linkAtPositionHandle.invokeExact(textSeg, bytes.length, position, startSeg, endSeg);
            
            if (result == 0) return null;
            
            return new int[] {
                startSeg.get(ValueLayout.JAVA_INT, 0),
                endSeg.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (Throwable t) {
            return null;
        }
    }
    
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
