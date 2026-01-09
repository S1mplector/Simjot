{-
 - SIMJOT - MIT License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE.md for full terms.
 -}

module Simjot.Poetry.SoundDevices
  ( SoundAnalysis(..)
  , SoundDevice(..)
  , SoundDeviceType(..)
  , analyzeSoundDevices
  , detectAlliteration
  , detectAssonance
  , detectConsonance
  , detectInternalRhyme
  , detectRichRhyme
  , detectPararhyme
  , calculatePhoneticDensity
  , analyzeProsodicFeatures
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as M
import Data.List (foldl')
import Data.Set (Set)
import qualified Data.Set as S

import Simjot.Poetry.Internal (isVowel)

-- | Types of sound devices with academic precision
data SoundDeviceType
  = Alliteration   -- ^ Repeated initial consonants (Classical rhetoric)
  | Assonance      -- ^ Repeated vowel sounds (Provençal poetry tradition)
  | Consonance     -- ^ Repeated consonant sounds (Modernist poetry)
  | Sibilance      -- ^ S/sh/z sounds (Sibilant consonance)
  | Onomatopoeia   -- ^ Sound-imitating words (Phonaesthetic devices)
  | InternalRhyme  -- ^ Rhyme within lines (Middle English poetry)
  | RichRhyme      -- ^ Multiple phoneme matches (French rime riche)
  | EyeRhyme       -- ^ Visual but not phonetic matches (Modern poetry)
  | Pararhyme      -- ^ Consonant matching with different vowels (Wilfred Owen)
  | Dissonance     -- ^ Clashing sounds (Experimental poetry)
  deriving (Show, Eq, Ord)

-- | A detected sound device
data SoundDevice = SoundDevice
  { deviceType    :: !SoundDeviceType
  , deviceLine    :: !Int
  , deviceSound   :: !Text
  , deviceWords   :: ![Text]
  , deviceStrength :: !Double
  } deriving (Show, Eq)

-- | Complete sound analysis with academic metrics
data SoundAnalysis = SoundAnalysis
  { soundDevices     :: ![SoundDevice]
  , soundDensity     :: !Double          -- ^ Phonetic density per line
  , soundDominant    :: ![Text]          -- ^ Dominant sound patterns
  , soundByType      :: !(Map SoundDeviceType Int)
  , phoneticComplexity :: !Double        -- ^ Phonetic complexity score
  , prosodicHarmony   :: !Double        -- ^ Prosodic harmony measure
  , euphonyScore     :: !Double        -- ^ Euphony vs dissonance balance
  , alliterationRate :: !Double        -- ^ Alliteration frequency
  , assonanceRate    :: !Double        -- ^ Assonance frequency
  } deriving (Show, Eq)

-- | Analyze sound devices in a poem (Text input)
analyzeSoundDevices :: Text -> SoundAnalysis
analyzeSoundDevices poem =
  let ws = T.words $ T.toLower poem
      allit = detectAlliteration ws
      asson = detectAssonance ws
      conson = detectConsonance ws
      internal = detectInternalRhyme ws
      rich = detectRichRhyme ws
      para = detectPararhyme ws
      devices = allit ++ asson ++ conson ++ internal ++ rich ++ para
      counts = foldr (\d -> M.insertWith (+) (deviceType d) 1) M.empty devices
      density = calculatePhoneticDensity ws
      prosody = analyzeProsodicFeatures devices
      totalWords = max 1 (length ws)
      allitRate = fromIntegral (length allit) / fromIntegral totalWords
      assonRate = fromIntegral (length asson) / fromIntegral totalWords
      dominant = take 3 . map fst . filter ((>1) . snd) $ M.toList counts
  in SoundAnalysis
      { soundDevices      = devices
      , soundDensity      = density
      , soundDominant     = dominant
      , soundByType       = counts
      , phoneticComplexity = prosody
      , prosodicHarmony   = 1 - abs (prosody - 0.5)
      , euphonyScore      = min 1.0 (density * 0.6 + 0.4 + prosody * 0.2)
      , alliterationRate  = allitRate
      , assonanceRate     = assonRate
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

-- | Detect internal rhyme (very lightweight heuristic: repeated ending bigrams)
detectInternalRhyme :: [Text] -> [SoundDevice]
detectInternalRhyme ws =
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = foldr addWord M.empty ws
    addWord w m =
      let end = T.toLower $ T.takeEnd 2 w
      in if T.length end < 2 then m else M.insertWith (++) end [w] m
    hasMultiple (_, xs) = length xs >= 2
    toDevice (sound, xs) = SoundDevice InternalRhyme 0 sound (uniqueTexts xs)
      (min 1.0 (fromIntegral (length xs) / 4.0))

-- | Detect rich rhyme (matching last 3 letters)
detectRichRhyme :: [Text] -> [SoundDevice]
detectRichRhyme ws =
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = foldr addWord M.empty ws
    addWord w m =
      let end = T.toLower $ T.takeEnd 3 w
      in if T.length end < 3 then m else M.insertWith (++) end [w] m
    hasMultiple (_, xs) = length xs >= 2
    toDevice (sound, xs) = SoundDevice RichRhyme 0 sound (uniqueTexts xs)
      (min 1.0 (fromIntegral (length xs) / 3.0))

-- | Detect pararhyme (same consonants, varying vowels)
detectPararhyme :: [Text] -> [SoundDevice]
detectPararhyme ws =
  map toDevice $ filter hasMultiple $ M.toList grouped
  where
    grouped = foldr addWord M.empty ws
    addWord w m =
      let consonants = T.filter (not . isVowel) (T.toLower w)
      in if T.length consonants < 2 then m else M.insertWith (++) consonants [w] m
    hasMultiple (_, xs) = length xs >= 2
    toDevice (sound, xs) = SoundDevice Pararhyme 0 sound (uniqueTexts xs)
      (min 1.0 (fromIntegral (length xs) / 4.0))

-- | Phonetic density: ratio of letters that are vowels or common digraphs
calculatePhoneticDensity :: [Text] -> Double
calculatePhoneticDensity ws =
  let allChars = T.concat ws
      chars = T.length allChars
      vowels = T.length (T.filter isVowel allChars)
  in if chars == 0 then 0 else fromIntegral vowels / fromIntegral chars

uniqueTexts :: [Text] -> [Text]
uniqueTexts = reverse . snd . foldl' step (S.empty, [])
  where
    step (seen, acc) t
      | S.member t seen = (seen, acc)
      | otherwise = (S.insert t seen, t : acc)

-- | Prosodic feature summary (simple blend of device strengths)
analyzeProsodicFeatures :: [SoundDevice] -> Double
analyzeProsodicFeatures ds =
  let strengths = map deviceStrength ds
      total = sum strengths
  in if null strengths then 0 else min 1.0 (total / fromIntegral (length strengths + 2))
