{-# LANGUAGE OverloadedStrings #-}
{-|
Module      : Simjot.Poetry
Description : Functional poetry analysis for Simjot
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : Proprietary
Maintainer  : contact@simjot.app

Main entry point for Simjot's Haskell poetry analysis library.
Provides pure functional analysis of poems including meter detection,
rhyme scheme analysis, sound device identification, and thematic analysis.
-}
module Simjot.Poetry
  ( -- * Core Types
    Poem(..)
  , PoemAnalysis(..)
  , Line(..)
  , Word(..)
  
    -- * Analysis Functions
  , analyzePoem
  , analyzeText
  
    -- * Re-exports
  , module Simjot.Poetry.Meter
  , module Simjot.Poetry.Rhyme
  , module Simjot.Poetry.SoundDevices
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.List (foldl')

import Simjot.Poetry.Analysis
import Simjot.Poetry.Meter
import Simjot.Poetry.Rhyme
import Simjot.Poetry.SoundDevices
import Simjot.Poetry.Internal

-- | Represents a complete poem with metadata
data Poem = Poem
  { poemTitle   :: !Text
  , poemAuthor  :: !(Maybe Text)
  , poemText    :: !Text
  , poemLines   :: ![Line]
  } deriving (Show, Eq)

-- | A single line of poetry
data Line = Line
  { lineNumber   :: !Int
  , lineText     :: !Text
  , lineWords    :: ![Word]
  , lineSyllables :: !Int
  } deriving (Show, Eq)

-- | A word with phonetic information
data Word = Word
  { wordText      :: !Text
  , wordSyllables :: !Int
  , wordStress    :: ![Bool]  -- True = stressed
  , wordRhymeKey  :: !Text
  } deriving (Show, Eq)

-- | Complete analysis results
data PoemAnalysis = PoemAnalysis
  { analysisPoem       :: !Poem
  , analysisMeter      :: !MeterAnalysis
  , analysisRhyme      :: !RhymeAnalysis
  , analysisSounds     :: !SoundAnalysis
  , analysisVocab      :: !VocabStats
  } deriving (Show, Eq)

-- | Vocabulary statistics
data VocabStats = VocabStats
  { vocabTotal       :: !Int
  , vocabUnique      :: !Int
  , vocabTTR         :: !Double  -- Type-Token Ratio
  , vocabAvgLength   :: !Double
  , vocabPolysyllabic :: !Int
  } deriving (Show, Eq)

-- | Parse text into a Poem structure
parsePoem :: Text -> Maybe Text -> Text -> Poem
parsePoem title author text = Poem
  { poemTitle  = title
  , poemAuthor = author
  , poemText   = text
  , poemLines  = zipWith parseLine [1..] (T.lines text)
  }

-- | Parse a single line
parseLine :: Int -> Text -> Line
parseLine num text = Line
  { lineNumber    = num
  , lineText      = text
  , lineWords     = map parseWord (T.words text)
  , lineSyllables = sum $ map wordSyllables (map parseWord (T.words text))
  }

-- | Parse a word with phonetic info
parseWord :: Text -> Word
parseWord text = Word
  { wordText      = cleaned
  , wordSyllables = countSyllables cleaned
  , wordStress    = estimateStress cleaned
  , wordRhymeKey  = extractRhymeKey cleaned
  }
  where
    cleaned = T.filter isAlphaOrApostrophe text
    isAlphaOrApostrophe c = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '\''

-- | Analyze a poem given title and text
analyzePoem :: Text -> Text -> PoemAnalysis
analyzePoem title text = analyzeText title Nothing text

-- | Analyze poem text with optional author
analyzeText :: Text -> Maybe Text -> Text -> PoemAnalysis
analyzeText title author text = PoemAnalysis
  { analysisPoem   = poem
  , analysisMeter  = analyzeMeter poem
  , analysisRhyme  = analyzeRhymes poem
  , analysisSounds = analyzeSoundDevices poem
  , analysisVocab  = analyzeVocab poem
  }
  where
    poem = parsePoem title author text

-- | Analyze vocabulary statistics
analyzeVocab :: Poem -> VocabStats
analyzeVocab poem = VocabStats
  { vocabTotal       = total
  , vocabUnique      = unique
  , vocabTTR         = if total > 0 then fromIntegral unique / fromIntegral total else 0
  , vocabAvgLength   = if total > 0 then avgLen else 0
  , vocabPolysyllabic = polysyl
  }
  where
    allWords = concatMap lineWords (poemLines poem)
    total    = length allWords
    unique   = length $ nubOrd $ map (T.toLower . wordText) allWords
    avgLen   = fromIntegral (sum $ map (T.length . wordText) allWords) / fromIntegral (max 1 total)
    polysyl  = length $ filter (\w -> wordSyllables w >= 3) allWords
    
    nubOrd :: Ord a => [a] -> [a]
    nubOrd = go mempty
      where
        go _ []     = []
        go s (x:xs)
          | x `elem` s = go s xs
          | otherwise  = x : go (x : s) xs
