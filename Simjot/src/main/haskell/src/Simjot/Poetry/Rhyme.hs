{-
 - SIMJOT - MIT License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE.md for full terms.
 -}

module Simjot.Poetry.Rhyme
  ( RhymeAnalysis(..)
  , RhymeType(..)
  , RhymeGroup(..)
  , analyzeRhymes
  , detectRhymeScheme
  , findRhymes
  , rhymesWithWord
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as M
import Data.Char (toLower)

import Simjot.Poetry.Internal (extractRhymeKey)

-- | Types of rhyme
data RhymeType
  = PerfectRhyme   -- ^ Exact sound match (cat/hat)
  | NearRhyme      -- ^ Similar but not exact (cat/bed)
  | SlantRhyme     -- ^ Consonance-based (cat/cut)
  | EyeRhyme       -- ^ Visual similarity (love/move)
  | InternalRhyme  -- ^ Rhyme within a line
  deriving (Show, Eq, Ord)

-- | A group of rhyming lines
data RhymeGroup = RhymeGroup
  { rhymeGroupKey   :: !Text
  , rhymeGroupLines :: ![Int]
  , rhymeGroupType  :: !RhymeType
  } deriving (Show, Eq)

-- | Complete rhyme analysis
data RhymeAnalysis = RhymeAnalysis
  { rhymeScheme      :: !Text           -- e.g., "ABAB CDCD EFEF GG"
  , rhymeGroups      :: ![RhymeGroup]
  , rhymeNearGroups  :: ![RhymeGroup]
  , rhymeDensity     :: !Double         -- Proportion of rhyming lines
  , rhymeInternal    :: ![(Int, Text)]  -- (line, rhyming words)
  } deriving (Show, Eq)

-- | Analyze rhymes in a poem
analyzeRhymes :: a -> RhymeAnalysis
analyzeRhymes poem = RhymeAnalysis
  { rhymeScheme     = "ABAB"  -- Placeholder
  , rhymeGroups     = []
  , rhymeNearGroups = []
  , rhymeDensity    = 0.75
  , rhymeInternal   = []
  }

-- | Detect rhyme scheme from end words
detectRhymeScheme :: [Text] -> Text
detectRhymeScheme endWords = T.pack $ go 'A' M.empty endWords
  where
    go _ _ [] = []
    go nextLabel seen (w:ws) =
      let key = extractRhymeKey w
      in case findRhymingKey key seen of
           Just label -> label : go nextLabel seen ws
           Nothing    -> nextLabel : go (succ nextLabel) (M.insert key nextLabel seen) ws
    
    findRhymingKey key seen = 
      case M.lookup key seen of
        Just label -> Just label
        Nothing    -> findSimilarKey key seen
    
    findSimilarKey key seen =
      let matches = filter (\(k, _) -> keysRhyme key k) (M.toList seen)
      in case matches of
           ((_, label):_) -> Just label
           []             -> Nothing

-- | Check if two rhyme keys match
keysRhyme :: Text -> Text -> Bool
keysRhyme k1 k2
  | k1 == k2 = True
  | T.length k1 < 2 || T.length k2 < 2 = False
  | otherwise = T.takeEnd 2 k1 == T.takeEnd 2 k2 
             || T.takeEnd 3 k1 == T.takeEnd 3 k2

-- | Find rhymes for a word from a dictionary
findRhymes :: Text -> [Text] -> [Text]
findRhymes word dict = filter (rhymesWithWord word) dict

-- | Check if two words rhyme
rhymesWithWord :: Text -> Text -> Bool
rhymesWithWord w1 w2
  | T.toLower w1 == T.toLower w2 = False  -- Same word doesn't count
  | otherwise = keysRhyme (extractRhymeKey w1) (extractRhymeKey w2)
