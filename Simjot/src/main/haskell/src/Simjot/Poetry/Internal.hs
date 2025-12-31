{-# LANGUAGE OverloadedStrings #-}
{-|
Module      : Simjot.Poetry.Internal
Description : Internal utilities for poetry analysis
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : Proprietary

Internal helper functions for poetry analysis.
-}
module Simjot.Poetry.Internal
  ( countSyllables
  , estimateStress
  , extractRhymeKey
  , isVowel
  , getPhoneme
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Char (toLower)

-- | Count syllables in a word using heuristics
countSyllables :: Text -> Int
countSyllables word
  | T.null cleaned = 0
  | otherwise = max 1 $ vowelGroups - silentE - silentEs
  where
    cleaned = T.toLower $ T.filter isAlpha word
    isAlpha c = c >= 'a' && c <= 'z'
    
    -- Count vowel groups
    vowelGroups = countVowelGroups cleaned
    
    -- Silent 'e' at end
    silentE = if T.length cleaned > 2 
              && T.last cleaned == 'e' 
              && not (isVowel $ T.index cleaned (T.length cleaned - 2))
              && not (cleaned `elem` ["the", "be", "he", "me", "we", "she"])
              then 1 else 0
    
    -- Silent 'es' ending
    silentEs = if T.length cleaned > 3
               && T.takeEnd 2 cleaned == "es"
               && not (isVowel $ T.index cleaned (T.length cleaned - 3))
               then 1 else 0

-- | Count vowel groups (consecutive vowels count as one)
countVowelGroups :: Text -> Int
countVowelGroups = go False 0 . T.unpack
  where
    go _ n []     = n
    go prev n (c:cs)
      | isVowel c && not prev = go True (n + 1) cs
      | isVowel c             = go True n cs
      | otherwise             = go False n cs

-- | Check if character is a vowel
isVowel :: Char -> Bool
isVowel c = toLower c `elem` ("aeiouy" :: String)

-- | Estimate stress pattern for a word
estimateStress :: Text -> [Bool]
estimateStress word
  | sylCount <= 1 = [True]
  | sylCount == 2 = estimateTwoSyllable word
  | otherwise     = estimateMultiSyllable word sylCount
  where
    sylCount = countSyllables word

-- | Estimate stress for two-syllable words
estimateTwoSyllable :: Text -> [Bool]
estimateTwoSyllable word
  -- Common unstressed-stressed patterns (iambic)
  | hasPrefix ["a", "be", "de", "re", "un", "en", "em", "in", "im"] = [False, True]
  -- Common stressed-unstressed patterns (trochaic)
  | hasSuffix ["ly", "ness", "ment", "ful", "less", "ing", "ed"] = [True, False]
  | otherwise = [True, False]  -- Default trochaic
  where
    lower = T.toLower word
    hasPrefix ps = any (`T.isPrefixOf` lower) ps
    hasSuffix ss = any (`T.isSuffixOf` lower) ss

-- | Estimate stress for multi-syllable words
estimateMultiSyllable :: Text -> Int -> [Bool]
estimateMultiSyllable word n
  -- Words ending in -tion, -sion typically stress penultimate
  | hasSuffix ["tion", "sion"] = replicate (n-2) False ++ [True, False]
  -- Words ending in -ic stress antepenultimate
  | hasSuffix ["ic"] = replicate (n-2) False ++ [True, False]
  -- Default: alternating with primary stress on first/second
  | hasPrefix ["a", "be", "de", "re"] = False : alternating (n-1)
  | otherwise = alternating n
  where
    lower = T.toLower word
    hasSuffix ss = any (`T.isSuffixOf` lower) ss
    hasPrefix ps = any (`T.isPrefixOf` lower) ps
    alternating m = take m $ cycle [True, False]

-- | Extract rhyme key from word (last vowel sound onwards)
extractRhymeKey :: Text -> Text
extractRhymeKey word
  | T.null cleaned = ""
  | otherwise = T.toLower $ T.takeWhileEnd (not . isConsonantCluster) cleaned
  where
    cleaned = T.filter isAlpha word
    isAlpha c = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
    
    isConsonantCluster _ = False  -- Simplified

-- | Get phoneme representation (simplified)
getPhoneme :: Char -> Text
getPhoneme c = case toLower c of
  'a' -> "AE"
  'e' -> "EH"
  'i' -> "IH"
  'o' -> "OH"
  'u' -> "UH"
  'y' -> "IY"
  'b' -> "B"
  'c' -> "K"
  'd' -> "D"
  'f' -> "F"
  'g' -> "G"
  'h' -> "HH"
  'j' -> "JH"
  'k' -> "K"
  'l' -> "L"
  'm' -> "M"
  'n' -> "N"
  'p' -> "P"
  'q' -> "K"
  'r' -> "R"
  's' -> "S"
  't' -> "T"
  'v' -> "V"
  'w' -> "W"
  'x' -> "KS"
  'z' -> "Z"
  _   -> ""
