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
  , lexicalDiversity
  , wordFrequencyDistribution
  , registerAnalysis
  , semanticFieldAnalysis
  , lexicalComplexityIndex
  , colemanLiauIndex
  , gunningFogIndex
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

-- | Complete vocabulary analysis with academic linguistic metrics
data VocabAnalysis = VocabAnalysis
  { vocabTotalWords     :: !Int
  , vocabUniqueWords    :: !Int
  , vocabTypeTokenRatio :: !Double      -- ^ TTR: Type-Token Ratio
  , vocabLexicalDensity :: !Double      -- ^ Content words / total words
  , vocabPolysyllables  :: !Int         -- ^ Count of words with 3+ syllables
  , vocabPolysyllableRatio :: !Double   -- ^ Words with 3+ syllables
  , vocabHapaxCount     :: !Int         -- ^ Count of words occurring once
  , vocabHapaxLegomena :: ![Text]      -- ^ Words appearing only once
  , vocabAvgWordLength :: !Double      -- ^ Average word length
  , vocabLexicalDiversity :: !Double   -- ^ Yule's K characteristic
  , vocabColemanLiau :: !Double         -- ^ Readability index
  , vocabGunningFog :: !Double          -- ^ Readability index
  , vocabRegisterLevel :: !Text        -- ^ Formal/informal register
  , vocabSemanticFields :: ![Text]     -- ^ Dominant semantic domains
  , vocabComplexityIndex :: !Double     -- ^ Overall lexical complexity
  , vocabWordFreqDist :: ![WordFreq]    -- ^ Word frequency distribution top list
  } deriving (Show, Eq)

-- | Analyze vocabulary in text
analyzeVocabulary :: Text -> VocabAnalysis
analyzeVocabulary text = VocabAnalysis
  { vocabTotalWords     = total
  , vocabUniqueWords    = unique
  , vocabTypeTokenRatio = ttr
  , vocabLexicalDensity = lexDensity
  , vocabPolysyllables  = polysyl
  , vocabPolysyllableRatio = polyRatio
  , vocabHapaxCount     = hapaxCount
  , vocabHapaxLegomena = hapaxList
  , vocabAvgWordLength  = avgLen
  , vocabLexicalDiversity = yulesK
  , vocabColemanLiau = cli
  , vocabGunningFog = gfi
  , vocabRegisterLevel = registerAnalysis words
  , vocabSemanticFields = semanticFieldAnalysis words
  , vocabComplexityIndex = lexicalComplexityIndex ttr lexDensity polyRatio yulesK
  , vocabWordFreqDist = topWords
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
    hapaxList = map fst $ filter ((==1) . snd) $ M.toList freqMap
    hapaxCount = length hapaxList
    yulesK = lexicalDiversity freqMap
    cli = colemanLiauIndex words
    gfi = gunningFogIndex words
    topWords = wordFrequencyDistribution freqMap

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

-- | Yule's K lexical diversity
lexicalDiversity :: Map Text Int -> Double
lexicalDiversity freq
  | nTokens == 0 = 0
  | otherwise = 10000 * (sumSquares - nTokens) / (nTokens * nTokens)
  where
    counts = M.elems freq
    nTokens = fromIntegral (sum counts) :: Double
    sumSquares = sum [ fromIntegral c * fromIntegral c | c <- counts ]

-- | Word frequency distribution (top 10)
wordFrequencyDistribution :: Map Text Int -> [WordFreq]
wordFrequencyDistribution freqMap =
  take 10 $ zipWith toWF [1..] sorted
  where
    sorted = sortBy (comparing (Down . snd)) $ M.toList freqMap
    toWF rank (w,c) = WordFreq w c rank

-- | Simple register analysis (heuristic): more polysyllables => more formal
registerAnalysis :: [Text] -> Text
registerAnalysis ws
  | polyRatio > 0.25 = "formal"
  | polyRatio > 0.15 = "neutral"
  | otherwise = "informal"
  where
    total = max 1 (length ws)
    polys = length $ filter (\w -> countSyllables w >= 3) ws
    polyRatio = fromIntegral polys / fromIntegral total

-- | Semantic field analysis (stub: most frequent stems by prefix)
semanticFieldAnalysis :: [Text] -> [Text]
semanticFieldAnalysis ws =
  take 5 . map fst . sortBy (comparing (Down . snd)) . M.toList $
    foldr (\w -> M.insertWith (+) (T.take 3 w) 1) M.empty ws

-- | Lexical complexity index (blend of TTR, density, polysyllable ratio, diversity)
lexicalComplexityIndex :: Double -> Double -> Double -> Double -> Double
lexicalComplexityIndex ttr lexD polyK yuleK =
  min 1.0 $ 0.35*ttr + 0.25*lexD + 0.2*polyK + 0.2*(min 1.0 (yuleK/100.0))

-- | Coleman–Liau Index (approximate for poetry)
colemanLiauIndex :: [Text] -> Double
colemanLiauIndex ws
  | sentences == 0 || wordsCount == 0 = 0
  | otherwise = 0.0588 * l - 0.296 * s - 15.8
  where
    wordsCount = fromIntegral (length ws) :: Double
    letters = fromIntegral (sum (map T.length ws)) :: Double
    sentences = max 1 (wordsCount / 10) -- heuristic: ~10 words per poetic sentence
    l = letters / wordsCount * 100
    s = sentences / wordsCount * 100

-- | Gunning Fog Index (approximate for poetry)
gunningFogIndex :: [Text] -> Double
gunningFogIndex ws
  | wordsCount == 0 = 0
  | otherwise = 0.4 * (wordsCount / sentences + 100 * complex / wordsCount)
  where
    wordsCount = fromIntegral (length ws) :: Double
    sentences = max 1 (wordsCount / 10) -- heuristic
    complex = fromIntegral (length $ filter (\w -> countSyllables w >= 3) ws) :: Double

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
