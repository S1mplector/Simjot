{-
 - SIMJOT - MIT License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE.md for full terms.
 -}

module Main where

import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.IO as TIO

import Simjot.Poetry

main :: IO ()
main = do
    putStrLn "═══════════════════════════════════════════════════════"
    putStrLn "  Simjot Haskell Poetry Analysis Library - Test Suite"
    putStrLn "═══════════════════════════════════════════════════════"
    putStrLn ""
    
    -- Test poem: Shakespeare's Sonnet 18
    let testPoem = T.unlines
          [ "Shall I compare thee to a summer's day?"
          , "Thou art more lovely and more temperate:"
          , "Rough winds do shake the darling buds of May,"
          , "And summer's lease hath all too short a date:"
          ]
    
    putStrLn "Test Poem:"
    putStrLn "----------"
    TIO.putStrLn testPoem
    putStrLn ""
    
    -- Analyze the poem
    let analysis = analyzePoem "Sonnet 18" testPoem
    
    putStrLn "Analysis Results:"
    putStrLn "-----------------"
    
    -- Meter analysis
    putStrLn $ "Meter: " ++ T.unpack (meterName $ analysisMeter analysis)
    putStrLn $ "Dominant Foot: " ++ show (meterDominantFoot $ analysisMeter analysis)
    putStrLn $ "Meter Type: " ++ show (meterType $ analysisMeter analysis)
    putStrLn $ "Regularity: " ++ show (meterRegularity $ analysisMeter analysis)
    putStrLn ""
    
    -- Rhyme analysis
    putStrLn $ "Rhyme Scheme: " ++ T.unpack (rhymeScheme $ analysisRhyme analysis)
    putStrLn $ "Rhyme Density: " ++ show (rhymeDensity $ analysisRhyme analysis)
    putStrLn ""
    
    -- Vocabulary stats
    let vocab = analysisVocab analysis
    putStrLn $ "Total Words: " ++ show (vocabTotal vocab)
    putStrLn $ "Unique Words: " ++ show (vocabUnique vocab)
    putStrLn $ "Type-Token Ratio: " ++ show (vocabTTR vocab)
    putStrLn $ "Average Word Length: " ++ show (vocabAvgLength vocab)
    putStrLn $ "Polysyllabic Words: " ++ show (vocabPolysyllabic vocab)
    putStrLn ""
    
    -- Test rhyme detection
    putStrLn "Rhyme Tests:"
    putStrLn "------------"
    testRhyme "day" "May"
    testRhyme "temperate" "date"
    testRhyme "love" "above"
    testRhyme "heart" "art"
    putStrLn ""
    
    -- Test syllable counting
    putStrLn "Syllable Counts:"
    putStrLn "----------------"
    testSyllables "beautiful"
    testSyllables "temperate"
    testSyllables "summer"
    testSyllables "compare"
    putStrLn ""
    
    putStrLn "✓ All tests completed!"

testRhyme :: Text -> Text -> IO ()
testRhyme w1 w2 = putStrLn $ T.unpack w1 ++ " / " ++ T.unpack w2 ++ " -> " ++ 
                             if rhymesWithWord w1 w2 then "RHYME" else "no rhyme"

testSyllables :: Text -> IO ()
testSyllables word = putStrLn $ T.unpack word ++ " -> " ++ 
                                show (countSyllables word) ++ " syllables"
