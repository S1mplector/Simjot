{-# LANGUAGE ForeignFunctionInterface #-}
{-# LANGUAGE CApiFFI #-}
{-|
Module      : Simjot.Poetry.FFI
Description : FFI bindings to Simjot native C library
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : Proprietary

Foreign function interface to Simjot's native C/C++ poetry analysis modules.
-}
module Simjot.Poetry.FFI
  ( -- * Native Poetry Analysis
    c_poetry_analyze_sounds
  , c_poetry_analyze_themes
  , c_poetry_get_theme_score
  , c_poetry_count_syllables
  , c_poetry_analyze_meter
  , c_poetry_detect_meter
    
    -- * Native Rhyme Engine
  , c_rhyme_add_word
  , c_rhyme_find
  , c_rhyme_check
  , c_rhyme_detect_scheme
  , c_rhyme_clear
  , c_rhyme_db_size
  
    -- * Helper Functions
  , withCText
  , peekCText
  ) where

import Foreign
import Foreign.C.Types
import Foreign.C.String
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.ByteString as BS

-- ═══════════════════════════════════════════════════════════════════════════
-- Poetry Analysis FFI
-- ═══════════════════════════════════════════════════════════════════════════

foreign import capi "simjot_native.h poetry_analyze_sounds"
  c_poetry_analyze_sounds :: CString -> IO CInt

foreign import capi "simjot_native.h poetry_analyze_themes"
  c_poetry_analyze_themes :: CString -> IO CInt

foreign import capi "simjot_native.h poetry_get_theme_score"
  c_poetry_get_theme_score :: CString -> IO CDouble

foreign import capi "simjot_native.h poetry_count_syllables"
  c_poetry_count_syllables :: CString -> IO CInt

foreign import capi "simjot_native.h poetry_analyze_meter"
  c_poetry_analyze_meter :: CString -> IO CInt

foreign import capi "simjot_native.h poetry_detect_meter"
  c_poetry_detect_meter :: Ptr CChar -> CInt -> IO CInt

-- ═══════════════════════════════════════════════════════════════════════════
-- Rhyme Engine FFI
-- ═══════════════════════════════════════════════════════════════════════════

foreign import capi "simjot_native.h rhyme_add_word"
  c_rhyme_add_word :: CString -> IO ()

foreign import capi "simjot_native.h rhyme_find"
  c_rhyme_find :: CString -> CInt -> IO CInt

foreign import capi "simjot_native.h rhyme_check"
  c_rhyme_check :: CString -> CString -> IO CInt

foreign import capi "simjot_native.h rhyme_detect_scheme"
  c_rhyme_detect_scheme :: CString -> Ptr CChar -> CInt -> IO CInt

foreign import capi "simjot_native.h rhyme_clear"
  c_rhyme_clear :: IO ()

foreign import capi "simjot_native.h rhyme_db_size"
  c_rhyme_db_size :: IO CInt

-- ═══════════════════════════════════════════════════════════════════════════
-- Helper Functions
-- ═══════════════════════════════════════════════════════════════════════════

-- | Convert Text to CString for FFI call
withCText :: Text -> (CString -> IO a) -> IO a
withCText t action = BS.useAsCString (TE.encodeUtf8 t) action

-- | Read CString result as Text
peekCText :: CString -> IO Text
peekCText cstr
  | cstr == nullPtr = return T.empty
  | otherwise = do
      bs <- BS.packCString cstr
      return $ TE.decodeUtf8 bs
