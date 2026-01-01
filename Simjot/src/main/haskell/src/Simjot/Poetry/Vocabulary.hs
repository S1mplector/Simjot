{-
 - SIMJOT - MIT License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE.md for full terms.
 -}

{-|
Module      : Simjot.Poetry.Vocabulary
Description : Vocabulary analysis for poetry
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : MIT

Provides vocabulary richness analysis, word frequency stats,
and lexical complexity metrics for poetry.
-}
module Simjot.Poetry.Vocabulary
  ( VocabAnalysis(..)
  , WordFreq(..)
  , analyzeVocabulary
  , typeTokenRatio
  , lexicalDensity
  , hapaxLegomena
  , averageWordLength
  , polysyllableRatio
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as M
import Data.List (sortBy)
import Data.Ord (comparing, Down(..))
import Data.Char (isAlpha, toLower)

import Simjot.Poetry.Internal (countSyllables)

-- | Word frequency information
data WordFreq = WordFreq
  { wfWord     :: !Text
  , wfCount    :: !Int
  , wfRank     :: !Int
  } deriving (Show, Eq)

-- | Complete vocabulary analysis
data VocabAnalysis = VocabAnalysis
  { vocabTotalWords     :: !Int
  , vocabUniqueWords    :: !Int
  , vocabTypeTokenRatio :: !Double
  , vocabLexicalDensity :: !Double
  , vocabAvgWordLength  :: !Double
  , vocabPolysyllables  :: !Int
  , vocabPolysyllableRatio :: !Double
  , vocabHapaxCount     :: !Int        -- Words appearing only once
  , vocabTopWords       :: ![WordFreq]
  } deriving (Show, Eq)

-- | Analyze vocabulary in text
analyzeVocabulary :: Text -> VocabAnalysis
analyzeVocabulary text = VocabAnalysis
  { vocabTotalWords     = total
  , vocabUniqueWords    = unique
  , vocabTypeTokenRatio = ttr
  , vocabLexicalDensity = lexDensity
  , vocabAvgWordLength  = avgLen
  , vocabPolysyllables  = polysyl
  , vocabPolysyllableRatio = polyRatio
  , vocabHapaxCount     = hapax
  , vocabTopWords       = topWords
  }
  where
    words = extractWords text
    total = length words
    freqMap = buildFrequencyMap words
    unique = M.size freqMap
    
    ttr = typeTokenRatio total unique
    lexDensity = lexicalDensity words
    avgLen = averageWordLength words
    
    polysyl = length $ filter (\w -> countSyllables w >= 3) words
    polyRatio = if total > 0 then fromIntegral polysyl / fromIntegral total else 0
    
    hapax = hapaxLegomena freqMap
    
    topWords = take 10 $ zipWith toWordFreq [1..] $ 
               sortBy (comparing (Down . snd)) $ M.toList freqMap
    
    toWordFreq rank (w, c) = WordFreq w c rank

-- | Extract words from text
extractWords :: Text -> [Text]
extractWords = filter (not . T.null) . map cleanWord . T.words
  where
    cleanWord = T.toLower . T.filter isAlpha

-- | Build word frequency map
buildFrequencyMap :: [Text] -> Map Text Int
buildFrequencyMap = foldr (\w -> M.insertWith (+) w 1) M.empty

-- | Type-Token Ratio (vocabulary richness)
typeTokenRatio :: Int -> Int -> Double
typeTokenRatio total unique
  | total == 0 = 0
  | otherwise = fromIntegral unique / fromIntegral total

-- | Lexical density (content words / total words)
-- Simplified: assumes non-function words are content words
lexicalDensity :: [Text] -> Double
lexicalDensity words
  | null words = 0
  | otherwise = fromIntegral contentCount / fromIntegral (length words)
  where
    contentCount = length $ filter (not . isFunctionWord) words
    
    isFunctionWord w = w `elem` functionWords
    functionWords = ["the", "a", "an", "is", "are", "was", "were", "be", "been",
                     "being", "have", "has", "had", "do", "does", "did", "will",
                     "would", "could", "should", "may", "might", "must", "shall",
                     "can", "of", "to", "in", "for", "on", "with", "at", "by",
                     "from", "as", "into", "through", "during", "before", "after",
                     "above", "below", "between", "under", "again", "further",
                     "then", "once", "here", "there", "when", "where", "why",
                     "how", "all", "each", "few", "more", "most", "other", "some",
                     "such", "no", "nor", "not", "only", "own", "same", "so",
                     "than", "too", "very", "just", "and", "but", "if", "or",
                     "because", "until", "while", "although", "though", "after",
                     "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
                     "you", "your", "yours", "yourself", "yourselves", "he", "him",
                     "his", "himself", "she", "her", "hers", "herself", "it", "its",
                     "itself", "they", "them", "their", "theirs", "themselves",
                     "what", "which", "who", "whom", "this", "that", "these",
                     "those", "am"]

-- | Count hapax legomena (words appearing only once)
hapaxLegomena :: Map Text Int -> Int
hapaxLegomena = M.size . M.filter (== 1)

-- | Average word length
averageWordLength :: [Text] -> Double
averageWordLength words
  | null words = 0
  | otherwise = fromIntegral totalChars / fromIntegral (length words)
  where
    totalChars = sum $ map T.length words

-- | Polysyllable ratio (words with 3+ syllables)
polysyllableRatio :: [Text] -> Double
polysyllableRatio words
  | null words = 0
  | otherwise = fromIntegral polysyl / fromIntegral (length words)
  where
    polysyl = length $ filter (\w -> countSyllables w >= 3) words
