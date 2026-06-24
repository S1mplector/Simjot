{-
 - SIMJOT - No Derivatives License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE for full terms.
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

-- | Types of metrical feet with classical precision
data FootType
  = Iamb        -- ^ unstressed-stressed (u /) - Classical Latin iambo
  | Trochee     -- ^ stressed-unstressed (/ u) - Greek trochaic
  | Spondee     -- ^ stressed-stressed (/ /) - Classical spondee
  | Pyrrhic     -- ^ unstressed-unstressed (u u) - Greek pyrrhic
  | Anapest     -- ^ unstressed-unstressed-stressed (u u /) - Greek anapestic
  | Dactyl      -- ^ stressed-unstressed-unstressed (/ u u) - Greek dactylic
  | Amphibrach  -- ^ unstressed-stressed-unstressed (u / u) - Greek amphibrach
  | Amphimacer  -- ^ stressed-unstressed-stressed (/ u /) - Greek amphimacer
  | Bacchius    -- ^ unstressed-stressed-stressed (u / /) - Greek bacchius
  | Antibacchius -- ^ stressed-stressed-unstressed (/ / u) - Greek antibacchius
  | Tribrach    -- ^ unstressed-unstressed-unstressed (u u u) - Greek tribrach
  | Molossus    -- ^ stressed-stressed-stressed (/ / /) - Greek molossus
  | Proceleusmatic -- ^ unstressed-stressed-unstressed-stressed (u / u u) - Rare
  deriving (Show, Eq, Ord)

-- | Meter types based on foot count per line with classical names
data MeterType
  = Monometer   -- ^ 1 foot - Monometer
  | Dimeter     -- ^ 2 feet - Dimeter  
  | Trimeter    -- ^ 3 feet - Trimeter
  | Tetrameter  -- ^ 4 feet - Tetrameter
  | Pentameter  -- ^ 5 feet - Pentameter (Shakespearean)
  | Hexameter   -- ^ 6 feet - Hexameter (Classical epic)
  | Heptameter  -- ^ 7 feet - Heptameter (Keatsian)
  | Octameter   -- ^ 8 feet - Octameter (Rare)
  | FreeVerse   -- ^ No consistent meter - Vers libre
  | CommonMeter -- ^ 8.6.8.6 - Hymnal meter (Emily Dickinson)
  | BalladMeter  -- ^ 8.6.8.6 - Ballad stanza
  | Spenserian   -- ^ 9 lines - Spenserian stanza
  | Sonnet       -- ^ 14 lines - Shakespearean/Petrarchan
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
analyzeMeter :: HasLines a => a -> MeterAnalysis
analyzeMeter poem = MeterAnalysis
  { meterName         = determineMeterName dominant mtype
  , meterDominantFoot = dominant
  , meterType         = mtype
  , meterRegularity   = regularity
  , meterScansions    = scansions
  , meterAvgSyllables = avgSyl
  }
  where
    lineTexts = getLines poem
    scansions = zipWith scanLineFromText [1..] lineTexts
    
    -- Get all feet from all lines
    allFeet = concatMap scansionFeet scansions
    
    -- Find dominant foot type
    dominant = if null allFeet then Iamb
               else head $ maximumBy (comparing length) $ group $ sort allFeet
    
    -- Determine meter type from average syllables
    avgSyl = if null scansions then 0
             else fromIntegral (sum $ map scansionSyllables scansions) / 
                  fromIntegral (length scansions)
    mtype = syllablesToMeterType (round avgSyl) dominant
    
    -- Calculate regularity (how consistent the meter is)
    regularity = calculateRegularity scansions dominant

-- | Type class for things that have lines
class HasLines a where
  getLines :: a -> [Text]

instance HasLines [Text] where
  getLines = id

-- | Scan a line from text
scanLineFromText :: Int -> Text -> LineScansion
scanLineFromText lineNum text = 
  let words = T.words text
      stresses = concatMap estimateWordStress words
      sylCount = length stresses
  in scanLine lineNum text stresses

-- | Estimate stress pattern for a word
estimateWordStress :: Text -> [Bool]
estimateWordStress word = estimateStressFromSyllables cleaned
  where
    cleaned = T.filter isAlphaOrApos word
    isAlphaOrApos c = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '\''

-- | Estimate stress from syllable count
estimateStressFromSyllables :: Text -> [Bool]
estimateStressFromSyllables word
  | T.null word = []
  | sylCount <= 0 = []
  | sylCount == 1 = [True]
  | sylCount == 2 = estimateTwoSyl word
  | otherwise = estimateMultiSyl word sylCount
  where
    sylCount = countSyllablesSimple word
    
    estimateTwoSyl w
      | hasIambicPrefix w = [False, True]
      | otherwise = [True, False]
    
    estimateMultiSyl w n
      | hasSuffix w "tion" || hasSuffix w "sion" = 
          replicate (n-2) False ++ [True, False]
      | hasIambicPrefix w = False : take (n-1) (cycle [True, False])
      | otherwise = take n (cycle [True, False])
    
    hasIambicPrefix w = any (`T.isPrefixOf` T.toLower w) 
      ["a", "be", "de", "re", "un", "en", "em", "in", "im", "ex", "pre"]
    
    hasSuffix w s = T.isSuffixOf (T.pack s) (T.toLower w)

-- | Simple syllable counting
countSyllablesSimple :: Text -> Int
countSyllablesSimple word
  | T.null cleaned = 0
  | otherwise = max 1 $ vowelGroups - silentE
  where
    cleaned = T.toLower $ T.filter isAlpha word
    isAlpha c = c >= 'a' && c <= 'z'
    
    vowelGroups = countVGs False 0 (T.unpack cleaned)
    countVGs _ n [] = n
    countVGs prev n (c:cs)
      | isVowelChar c && not prev = countVGs True (n+1) cs
      | isVowelChar c = countVGs True n cs
      | otherwise = countVGs False n cs
    
    isVowelChar c = c `elem` ("aeiouy" :: String)
    
    silentE = if T.length cleaned > 2 && T.last cleaned == 'e' 
              && not (isVowelChar $ T.index cleaned (T.length cleaned - 2))
              then 1 else 0

-- | Convert syllable count to meter type
syllablesToMeterType :: Int -> FootType -> MeterType
syllablesToMeterType sylCount foot = case (sylCount, footSize foot) of
  (s, f) | s <= f     -> Monometer
         | s <= f * 2 -> Dimeter
         | s <= f * 3 -> Trimeter
         | s <= f * 4 -> Tetrameter
         | s <= f * 5 -> Pentameter
         | s <= f * 6 -> Hexameter
         | s <= f * 7 -> Heptameter
         | otherwise  -> FreeVerse
  where
    footSize Anapest = 3
    footSize Dactyl = 3
    footSize Amphibrach = 3
    footSize _ = 2

-- | Calculate regularity of meter
calculateRegularity :: [LineScansion] -> FootType -> Double
calculateRegularity scansions dominant
  | null allFeet = 0.0
  | otherwise = fromIntegral matches / fromIntegral total
  where
    allFeet = concatMap scansionFeet scansions
    total = length allFeet
    matches = length $ filter (== dominant) allFeet

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
