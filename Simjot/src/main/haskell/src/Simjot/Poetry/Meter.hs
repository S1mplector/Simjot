{-# LANGUAGE OverloadedStrings #-}
{-|
Module      : Simjot.Poetry.Meter
Description : Metrical analysis of poetry
Copyright   : (c) 2024-2025 Ilgaz Mehmetoğlu
License     : Proprietary

Meter detection and scansion for poetry analysis.
-}
module Simjot.Poetry.Meter
  ( MeterAnalysis(..)
  , FootType(..)
  , MeterType(..)
  , LineScansion(..)
  , analyzeMeter
  , detectFoot
  , scanLine
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.List (maximumBy, group, sort)
import Data.Ord (comparing)

-- | Types of metrical feet
data FootType
  = Iamb        -- ^ unstressed-stressed (u /)
  | Trochee     -- ^ stressed-unstressed (/ u)
  | Spondee     -- ^ stressed-stressed (/ /)
  | Pyrrhic     -- ^ unstressed-unstressed (u u)
  | Anapest     -- ^ unstressed-unstressed-stressed (u u /)
  | Dactyl      -- ^ stressed-unstressed-unstressed (/ u u)
  | Amphibrach  -- ^ unstressed-stressed-unstressed (u / u)
  deriving (Show, Eq, Ord)

-- | Meter types based on foot count per line
data MeterType
  = Monometer   -- ^ 1 foot
  | Dimeter     -- ^ 2 feet
  | Trimeter    -- ^ 3 feet
  | Tetrameter  -- ^ 4 feet
  | Pentameter  -- ^ 5 feet
  | Hexameter   -- ^ 6 feet
  | Heptameter  -- ^ 7 feet
  | FreeVerse   -- ^ No consistent meter
  deriving (Show, Eq)

-- | Scansion results for a single line
data LineScansion = LineScansion
  { scansionLine      :: !Int
  , scansionText      :: !Text
  , scansionPattern   :: !Text      -- e.g., "u / u / u /"
  , scansionFeet      :: ![FootType]
  , scansionSyllables :: !Int
  } deriving (Show, Eq)

-- | Complete meter analysis
data MeterAnalysis = MeterAnalysis
  { meterName        :: !Text
  , meterDominantFoot :: !FootType
  , meterType        :: !MeterType
  , meterRegularity  :: !Double     -- 0.0 to 1.0
  , meterScansions   :: ![LineScansion]
  , meterAvgSyllables :: !Double
  } deriving (Show, Eq)

-- | Analyze meter of a poem
analyzeMeter :: a -> MeterAnalysis
analyzeMeter poem = MeterAnalysis
  { meterName         = determineMeterName dominant mtype
  , meterDominantFoot = dominant
  , meterType         = mtype
  , meterRegularity   = 0.85  -- Placeholder
  , meterScansions    = []    -- Would be filled with actual scansions
  , meterAvgSyllables = 10.0  -- Placeholder
  }
  where
    dominant = Iamb  -- Default for English poetry
    mtype = Pentameter

-- | Determine meter name from foot type and length
determineMeterName :: FootType -> MeterType -> Text
determineMeterName foot mtype = T.concat [footName, " ", meterName']
  where
    footName = case foot of
      Iamb       -> "Iambic"
      Trochee    -> "Trochaic"
      Spondee    -> "Spondaic"
      Pyrrhic    -> "Pyrrhic"
      Anapest    -> "Anapestic"
      Dactyl     -> "Dactylic"
      Amphibrach -> "Amphibrachic"
    
    meterName' = case mtype of
      Monometer  -> "Monometer"
      Dimeter    -> "Dimeter"
      Trimeter   -> "Trimeter"
      Tetrameter -> "Tetrameter"
      Pentameter -> "Pentameter"
      Hexameter  -> "Hexameter"
      Heptameter -> "Heptameter"
      FreeVerse  -> "Free Verse"

-- | Detect foot type from stress pattern
detectFoot :: [Bool] -> FootType
detectFoot pattern = case pattern of
  [False, True]        -> Iamb
  [True, False]        -> Trochee
  [True, True]         -> Spondee
  [False, False]       -> Pyrrhic
  [False, False, True] -> Anapest
  [True, False, False] -> Dactyl
  [False, True, False] -> Amphibrach
  _                    -> Iamb  -- Default

-- | Scan a single line for meter
scanLine :: Int -> Text -> [Bool] -> LineScansion
scanLine lineNum text stresses = LineScansion
  { scansionLine      = lineNum
  , scansionText      = text
  , scansionPattern   = patternText
  , scansionFeet      = feet
  , scansionSyllables = length stresses
  }
  where
    patternText = T.intercalate " " $ map (\s -> if s then "/" else "u") stresses
    feet = identifyFeet stresses

-- | Identify feet from stress pattern
identifyFeet :: [Bool] -> [FootType]
identifyFeet [] = []
identifyFeet stresses
  | length stresses < 2 = []
  | otherwise = detectFoot (take footSize stresses) : identifyFeet (drop footSize stresses)
  where
    footSize = if length stresses >= 3 && detectTriplePattern stresses
               then 3 else 2
    
    detectTriplePattern (False:False:True:_) = True
    detectTriplePattern (True:False:False:_) = True
    detectTriplePattern (False:True:False:_) = True
    detectTriplePattern _ = False
