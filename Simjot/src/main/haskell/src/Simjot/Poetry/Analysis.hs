{-
 - SIMJOT - MIT License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE.md for full terms.
 -}

module Simjot.Poetry.Analysis
  ( -- * Analysis Functions
    analyzeWithNative
  , analyzeThemes
  , analyzeSounds
  , getThemeScore
    
    -- * Utility
  , syllableCount
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Control.Exception (catch, SomeException)
import System.IO.Unsafe (unsafePerformIO)

import Simjot.Poetry.FFI
import Simjot.Poetry.Internal (countSyllables)

-- | Analyze text using native implementation when available
analyzeWithNative :: Text -> IO (Maybe Int)
analyzeWithNative text = catch tryNative fallback
  where
    tryNative = withCText text $ \cstr -> do
      result <- c_poetry_analyze_sounds cstr
      return $ if result > 0 then Just (fromIntegral result) else Nothing
    fallback :: SomeException -> IO (Maybe Int)
    fallback _ = return Nothing

-- | Analyze themes using native implementation
analyzeThemes :: Text -> IO Int
analyzeThemes text = catch tryNative fallback
  where
    tryNative = withCText text $ \cstr -> do
      result <- c_poetry_analyze_themes cstr
      return $ fromIntegral result
    fallback :: SomeException -> IO Int
    fallback _ = return 0

-- | Analyze sound devices using native implementation
analyzeSounds :: Text -> IO Int
analyzeSounds text = catch tryNative fallback
  where
    tryNative = withCText text $ \cstr -> do
      result <- c_poetry_analyze_sounds cstr
      return $ fromIntegral result
    fallback :: SomeException -> IO Int
    fallback _ = return 0

-- | Get theme score using native implementation
getThemeScore :: Text -> IO Double
getThemeScore theme = catch tryNative fallback
  where
    tryNative = withCText theme $ \cstr -> do
      result <- c_poetry_get_theme_score cstr
      return $ realToFrac result
    fallback :: SomeException -> IO Double
    fallback _ = return 0.0

-- | Count syllables with native fallback to pure Haskell
syllableCount :: Text -> Int
syllableCount word = unsafePerformIO $ catch tryNative fallback
  where
    tryNative = withCText word $ \cstr -> do
      result <- c_poetry_count_syllables cstr
      let n = fromIntegral result
      return $ if n > 0 then n else countSyllables word
    fallback :: SomeException -> IO Int
    fallback _ = return $ countSyllables word
