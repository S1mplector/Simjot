{-# LANGUAGE OverloadedStrings #-}
{-|
Module      : Simjot.Poetry.SoundDevices
Description : Sound device detection in poetry
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : Proprietary

Detection of alliteration, assonance, consonance, and other sound devices.
-}
module Simjot.Poetry.SoundDevices
  ( SoundAnalysis(..)
  , SoundDevice(..)
  , SoundDeviceType(..)
  , analyzeSoundDevices
  , detectAlliteration
  , detectAssonance
  , detectConsonance
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Char (toLower)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as M

import Simjot.Poetry.Internal (isVowel)

-- | Types of sound devices
data SoundDeviceType
  = Alliteration   -- ^ Repeated initial consonants
  | Assonance      -- ^ Repeated vowel sounds
  | Consonance     -- ^ Repeated consonant sounds
  | Sibilance      -- ^ S/sh/z sounds
  | Onomatopoeia   -- ^ Sound-imitating words
  deriving (Show, Eq, Ord)

-- | A detected sound device
data SoundDevice = SoundDevice
  { deviceType    :: !SoundDeviceType
  , deviceLine    :: !Int
  , deviceSound   :: !Text
  , deviceWords   :: ![Text]
  , deviceStrength :: !Double
  } deriving (Show, Eq)

-- | Complete sound analysis
data SoundAnalysis = SoundAnalysis
  { soundDevices     :: ![SoundDevice]
  , soundDensity     :: !Double
  , soundDominant    :: ![Text]
  , soundByType      :: !(Map SoundDeviceType Int)
  } deriving (Show, Eq)

-- | Analyze sound devices in a poem
analyzeSoundDevices :: a -> SoundAnalysis
analyzeSoundDevices poem = SoundAnalysis
  { soundDevices  = []
  , soundDensity  = 0.0
  , soundDominant = []
  , soundByType   = M.empty
  }

-- | Detect alliteration in words
detectAlliteration :: [Text] -> [SoundDevice]
detectAlliteration words = 
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = groupByInitial words
    hasMultiple (_, ws) = length ws >= 2
    toDevice (sound, ws) = SoundDevice
      { deviceType     = Alliteration
      , deviceLine     = 0
      , deviceSound    = sound
      , deviceWords    = ws
      , deviceStrength = min 1.0 (fromIntegral (length ws) / 4.0)
      }

-- | Group words by initial sound
groupByInitial :: [Text] -> Map Text [Text]
groupByInitial = foldr addWord M.empty
  where
    addWord w m
      | T.null w = m
      | otherwise = 
          let sound = getInitialSound w
          in if T.null sound then m
             else M.insertWith (++) sound [w] m

-- | Get initial sound of a word
getInitialSound :: Text -> Text
getInitialSound w
  | T.null w = ""
  | T.length w >= 2 && isDigraph (T.take 2 w) = T.toLower (T.take 2 w)
  | otherwise = T.toLower (T.take 1 w)
  where
    isDigraph d = d `elem` ["ch", "sh", "th", "ph", "wh", "gh", "kn", "wr"]

-- | Detect assonance in words
detectAssonance :: [Text] -> [SoundDevice]
detectAssonance words =
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = groupByVowel words
    hasMultiple (_, ws) = length ws >= 3
    toDevice (sound, ws) = SoundDevice
      { deviceType     = Assonance
      , deviceLine     = 0
      , deviceSound    = sound
      , deviceWords    = ws
      , deviceStrength = min 1.0 (fromIntegral (length ws) / 5.0)
      }

-- | Group words by dominant vowel sound
groupByVowel :: [Text] -> Map Text [Text]
groupByVowel = foldr addWord M.empty
  where
    addWord w m =
      let vowels = T.filter isVowel (T.toLower w)
      in if T.null vowels then m
         else M.insertWith (++) (T.take 1 vowels) [w] m

-- | Detect consonance in words
detectConsonance :: [Text] -> [SoundDevice]
detectConsonance words =
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = groupByConsonant words
    hasMultiple (_, ws) = length ws >= 3
    toDevice (sound, ws) = SoundDevice
      { deviceType     = Consonance
      , deviceLine     = 0
      , deviceSound    = sound
      , deviceWords    = ws
      , deviceStrength = min 1.0 (fromIntegral (length ws) / 5.0)
      }

-- | Group words by consonant sounds
groupByConsonant :: [Text] -> Map Text [Text]
groupByConsonant = foldr addWord M.empty
  where
    addWord w m =
      let consonants = T.filter (not . isVowel) (T.toLower w)
      in if T.null consonants then m
         else foldr (\c -> M.insertWith (++) (T.singleton c) [w]) m (T.unpack consonants)
