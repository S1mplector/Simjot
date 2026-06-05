{-
 - SIMJOT - No Derivatives License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE for full terms.
 -}

{-# LANGUAGE ForeignFunctionInterface #-}
{-|
Module      : Simjot.Poetry.Exports
Description : FFI exports for calling Haskell from C/Java
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : MIT

Exports Haskell poetry analysis functions for use via FFI.
-}
module Simjot.Poetry.Exports
  ( -- * Exported Functions
    hs_analyze_meter
  , hs_analyze_rhyme_scheme
  , hs_count_syllables
  , hs_analyze_sound_devices
  , hs_get_meter_name
  , hs_get_meter_regularity
  , hs_check_rhyme
  , hs_get_vocab_stats
  , hs_type_token_ratio
  , hs_get_rhyme_key
  , hs_estimate_stress
  ) where

import Foreign
import Foreign.C.Types
import Foreign.C.String
import Data.Bits (shiftL)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.ByteString as BS
import Control.Exception (catch, SomeException)

import Simjot.Poetry.Meter
import Simjot.Poetry.Rhyme
import Simjot.Poetry.SoundDevices
import Simjot.Poetry.Vocabulary
import Simjot.Poetry.Internal (countSyllables, extractRhymeKey, estimateStress)

-- ═══════════════════════════════════════════════════════════════════════════
-- FFI EXPORTS
-- ═══════════════════════════════════════════════════════════════════════════

-- | Analyze meter and return dominant foot type as int
-- 0=Iamb, 1=Trochee, 2=Spondee, 3=Pyrrhic, 4=Anapest, 5=Dactyl, 6=Amphibrach
foreign export ccall hs_analyze_meter :: CString -> IO CInt
hs_analyze_meter :: CString -> IO CInt
hs_analyze_meter cstr = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let lines = T.lines text
          result = analyzeMeter lines
          foot = meterDominantFoot result
      return $ footToInt foot
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Analyze rhyme scheme and write to output buffer
foreign export ccall hs_analyze_rhyme_scheme :: CString -> CString -> CInt -> IO CInt
hs_analyze_rhyme_scheme :: CString -> CString -> CInt -> IO CInt
hs_analyze_rhyme_scheme cstr outBuf outLen = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let lines = T.lines text
          result = analyzeRhymes lines
          scheme = rhymeScheme result
          schemeBytes = TE.encodeUtf8 scheme
      if outLen <= 0 then return 0
      else do
        let len = min (fromIntegral outLen - 1) (BS.length schemeBytes)
        pokeArray (castPtr outBuf) (BS.unpack $ BS.take len schemeBytes)
        pokeByteOff outBuf len (0 :: Word8)
        return $ fromIntegral len
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Count syllables in a word
foreign export ccall hs_count_syllables :: CString -> IO CInt
hs_count_syllables :: CString -> IO CInt
hs_count_syllables cstr = catch count fallback
  where
    count = do
      word <- peekText cstr
      return $ fromIntegral $ countSyllables word
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Analyze sound devices and return count
foreign export ccall hs_analyze_sound_devices :: CString -> IO CInt
hs_analyze_sound_devices :: CString -> IO CInt
hs_analyze_sound_devices cstr = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let words = T.words text
          alliteration = detectAlliteration words
          assonance = detectAssonance words
          consonance = detectConsonance words
          total = length alliteration + length assonance + length consonance
      return $ fromIntegral total
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Get meter name and write to output buffer
foreign export ccall hs_get_meter_name :: CString -> CString -> CInt -> IO CInt
hs_get_meter_name :: CString -> CString -> CInt -> IO CInt
hs_get_meter_name cstr outBuf outLen = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let lines = T.lines text
          result = analyzeMeter lines
          name = meterName result
          nameBytes = TE.encodeUtf8 name
      if outLen <= 0 then return 0
      else do
        let len = min (fromIntegral outLen - 1) (BS.length nameBytes)
        pokeArray (castPtr outBuf) (BS.unpack $ BS.take len nameBytes)
        pokeByteOff outBuf len (0 :: Word8)
        return $ fromIntegral len
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Get meter regularity as percentage (0-100)
foreign export ccall hs_get_meter_regularity :: CString -> IO CInt
hs_get_meter_regularity :: CString -> IO CInt
hs_get_meter_regularity cstr = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let lines = T.lines text
          result = analyzeMeter lines
          reg = meterRegularity result
      return $ round (reg * 100)
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Check if two words rhyme (1=yes, 0=no)
foreign export ccall hs_check_rhyme :: CString -> CString -> IO CInt
hs_check_rhyme :: CString -> CString -> IO CInt
hs_check_rhyme cstr1 cstr2 = catch check fallback
  where
    check = do
      w1 <- peekText cstr1
      w2 <- peekText cstr2
      return $ if rhymesWithWord w1 w2 then 1 else 0
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Get vocabulary stats: returns total|unique|polysyllables packed into output
-- Format: "total,unique,polysyl,hapax,avglen*100"
foreign export ccall hs_get_vocab_stats :: CString -> CString -> CInt -> IO CInt
hs_get_vocab_stats :: CString -> CString -> CInt -> IO CInt
hs_get_vocab_stats cstr outBuf outLen = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let result = analyzeVocabulary text
          stats = T.pack $ show (vocabTotalWords result) ++ "," ++
                           show (vocabUniqueWords result) ++ "," ++
                           show (vocabPolysyllables result) ++ "," ++
                           show (vocabHapaxCount result) ++ "," ++
                           show (round (vocabAvgWordLength result * 100) :: Int)
          statsBytes = TE.encodeUtf8 stats
      if outLen <= 0 then return 0
      else do
        let len = min (fromIntegral outLen - 1) (BS.length statsBytes)
        pokeArray (castPtr outBuf) (BS.unpack $ BS.take len statsBytes)
        pokeByteOff outBuf len (0 :: Word8)
        return $ fromIntegral len
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Get type-token ratio * 100 (for precision)
foreign export ccall hs_type_token_ratio :: CString -> IO CInt
hs_type_token_ratio :: CString -> IO CInt
hs_type_token_ratio cstr = catch analyze fallback
  where
    analyze = do
      text <- peekText cstr
      let result = analyzeVocabulary text
      return $ round (vocabTypeTokenRatio result * 100)
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Get rhyme key for a word
foreign export ccall hs_get_rhyme_key :: CString -> CString -> CInt -> IO CInt
hs_get_rhyme_key :: CString -> CString -> CInt -> IO CInt
hs_get_rhyme_key cstr outBuf outLen = catch analyze fallback
  where
    analyze = do
      word <- peekText cstr
      let key = extractRhymeKey word
          keyBytes = TE.encodeUtf8 key
      if outLen <= 0 then return 0
      else do
        let len = min (fromIntegral outLen - 1) (BS.length keyBytes)
        pokeArray (castPtr outBuf) (BS.unpack $ BS.take len keyBytes)
        pokeByteOff outBuf len (0 :: Word8)
        return $ fromIntegral len
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- | Estimate stress pattern, returns packed bits as int
-- Each bit represents a syllable: 1=stressed, 0=unstressed
foreign export ccall hs_estimate_stress :: CString -> IO CInt
hs_estimate_stress :: CString -> IO CInt
hs_estimate_stress cstr = catch analyze fallback
  where
    analyze = do
      word <- peekText cstr
      let stresses = estimateStress word
          -- Pack booleans into int, LSB = first syllable
          packed = foldr (\(i, s) acc -> if s then acc + (1 `shiftL` i) else acc) 0 
                         (zip [0..] stresses)
      return $ fromIntegral (packed :: Int)
    fallback :: SomeException -> IO CInt
    fallback _ = return 0

-- ═══════════════════════════════════════════════════════════════════════════
-- HELPERS
-- ═══════════════════════════════════════════════════════════════════════════

-- | Read CString as Text
peekText :: CString -> IO Text
peekText cstr
  | cstr == nullPtr = return T.empty
  | otherwise = do
      bs <- BS.packCString cstr
      return $ TE.decodeUtf8 bs

-- | Convert foot type to int
footToInt :: FootType -> CInt
footToInt Iamb = 0
footToInt Trochee = 1
footToInt Spondee = 2
footToInt Pyrrhic = 3
footToInt Anapest = 4
footToInt Dactyl = 5
footToInt Amphibrach = 6
