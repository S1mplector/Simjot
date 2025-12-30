/*
 * SIMJOT POETRY ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Poetry Engine Proprietary License.
 * You may inspect this code for educational and research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */
package main.core.poetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * VocabularyAnalyzer
 * 
 * Vocabulary analysis with word frequency analysis and vocabulary richness metrics.
 * 
 * Features:
 * - Word frequency analysis with ranking
 * - Type-Token Ratio (TTR) for vocabulary richness
 * - Hapax legomena detection (words appearing once)
 * - Lexical density calculation
 * - Word length distribution
 * - Part-of-speech distribution
 * - Rare/common word identification
 * - Keyword extraction
 * - Readability metrics (Flesch-Kincaid, etc.)
 * 
 * Uses PoetryDictionary for POS tagging and word lookups.
 */
public class VocabularyAnalyzer {
    
    /**
     * Complete vocabulary analysis result.
     */
    public static class VocabularyAnalysis {
        public final int totalWords;
        public final int uniqueWords;
        public final double typeTokenRatio;
        public final int hapaxLegomena; // Words appearing only once
        public final int disLegomena; // Words appearing only twice
        public final double lexicalDensity;
        public final double avgWordLength;
        public final Map<String, Integer> wordFrequencies;
        public final List<WordFrequency> topWords;
        public final List<WordFrequency> rareWords;
        public final Map<String, Integer> posDistribution;
        public final Map<Integer, Integer> wordLengthDistribution;
        public final double fleschReadingEase;
        public final double fleschKincaidGrade;
        public final List<String> keywords;
        public final double vocabularyRichnessScore;
        
        public VocabularyAnalysis(int totalWords, int uniqueWords, double typeTokenRatio,
                                 int hapaxLegomena, int disLegomena, double lexicalDensity,
                                 double avgWordLength, Map<String, Integer> wordFrequencies,
                                 List<WordFrequency> topWords, List<WordFrequency> rareWords,
                                 Map<String, Integer> posDistribution,
                                 Map<Integer, Integer> wordLengthDistribution,
                                 double fleschReadingEase, double fleschKincaidGrade,
                                 List<String> keywords, double vocabularyRichnessScore) {
            this.totalWords = totalWords;
            this.uniqueWords = uniqueWords;
            this.typeTokenRatio = typeTokenRatio;
            this.hapaxLegomena = hapaxLegomena;
            this.disLegomena = disLegomena;
            this.lexicalDensity = lexicalDensity;
            this.avgWordLength = avgWordLength;
            this.wordFrequencies = Collections.unmodifiableMap(wordFrequencies);
            this.topWords = Collections.unmodifiableList(topWords);
            this.rareWords = Collections.unmodifiableList(rareWords);
            this.posDistribution = Collections.unmodifiableMap(posDistribution);
            this.wordLengthDistribution = Collections.unmodifiableMap(wordLengthDistribution);
            this.fleschReadingEase = fleschReadingEase;
            this.fleschKincaidGrade = fleschKincaidGrade;
            this.keywords = Collections.unmodifiableList(keywords);
            this.vocabularyRichnessScore = vocabularyRichnessScore;
        }
    }
    
    /**
     * Word with its frequency info.
     */
    public static class WordFrequency {
        public final String word;
        public final int count;
        public final double percentage;
        public final String partOfSpeech;
        
        public WordFrequency(String word, int count, double percentage, String partOfSpeech) {
            this.word = word;
            this.count = count;
            this.percentage = percentage;
            this.partOfSpeech = partOfSpeech;
        }
    }
    
    /**
     * Analyze vocabulary in a text.
     */
    public VocabularyAnalysis analyze(String text) {
        if (text == null || text.isBlank()) {
            return emptyAnalysis();
        }
        
        List<String> allWords = extractAllWords(text);
        if (allWords.isEmpty()) {
            return emptyAnalysis();
        }
        
        // Word frequency count
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String word : allWords) {
            String lower = word.toLowerCase(Locale.ROOT);
            frequencies.merge(lower, 1, Integer::sum);
        }
        
        int totalWords = allWords.size();
        int uniqueWords = frequencies.size();
        
        // Type-Token Ratio
        double ttr = totalWords > 0 ? (double) uniqueWords / totalWords : 0.0;
        
        // Hapax legomena (words appearing once) and dis legomena (twice)
        int hapax = 0, dis = 0;
        for (int count : frequencies.values()) {
            if (count == 1) hapax++;
            else if (count == 2) dis++;
        }
        
        // Average word length
        double avgLen = allWords.stream().mapToInt(String::length).average().orElse(0.0);
        
        // Word length distribution
        Map<Integer, Integer> lengthDist = new TreeMap<>();
        for (String word : allWords) {
            lengthDist.merge(word.length(), 1, Integer::sum);
        }
        
        // POS distribution
        Map<String, Integer> posDist = new LinkedHashMap<>();
        for (String word : frequencies.keySet()) {
            String pos = PoetryDictionary.getPOS(word);
            posDist.merge(pos, frequencies.get(word), Integer::sum);
        }
        
        // Lexical density (content words / total words)
        int contentWords = 0;
        for (String word : allWords) {
            if (!PoetryDictionary.isFunctionWord(word.toLowerCase(Locale.ROOT))) {
                contentWords++;
            }
        }
        double lexicalDensity = totalWords > 0 ? (double) contentWords / totalWords : 0.0;
        
        // Top words (most frequent)
        List<WordFrequency> topWords = frequencies.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(20)
                .map(e -> new WordFrequency(e.getKey(), e.getValue(), 
                        (double) e.getValue() / totalWords * 100,
                        PoetryDictionary.getPOS(e.getKey())))
                .collect(Collectors.toList());
        
        // Rare words (appearing once, excluding common function words)
        List<WordFrequency> rareWords = frequencies.entrySet().stream()
                .filter(e -> e.getValue() == 1)
                .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
                .limit(20)
                .map(e -> new WordFrequency(e.getKey(), e.getValue(),
                        (double) e.getValue() / totalWords * 100,
                        PoetryDictionary.getPOS(e.getKey())))
                .collect(Collectors.toList());
        
        // Readability metrics
        int sentences = countSentences(text);
        int syllables = allWords.stream().mapToInt(PoetryUtils::countSyllables).sum();
        
        double fleschEase = calculateFleschReadingEase(totalWords, sentences, syllables);
        double fleschGrade = calculateFleschKincaidGrade(totalWords, sentences, syllables);
        
        // Keywords (high TF, not function words)
        List<String> keywords = extractKeywords(frequencies, totalWords);
        
        // Vocabulary richness score (composite metric)
        double richnessScore = calculateRichnessScore(ttr, hapax, uniqueWords, totalWords, lexicalDensity);
        
        return new VocabularyAnalysis(totalWords, uniqueWords, ttr, hapax, dis,
                lexicalDensity, avgLen, frequencies, topWords, rareWords,
                posDist, lengthDist, fleschEase, fleschGrade, keywords, richnessScore);
    }
    
    /**
     * Extract all words from text.
     */
    private List<String> extractAllWords(String text) {
        List<String> words = new ArrayList<>();
        for (String line : PoetryUtils.splitLines(text)) {
            words.addAll(PoetryUtils.wordsInLine(line));
        }
        return words;
    }
    
    /**
     * Count sentences (approximate).
     */
    private int countSentences(String text) {
        if (text == null || text.isBlank()) return 0;
        // Count sentence-ending punctuation
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '.' || c == '!' || c == '?') count++;
        }
        return Math.max(1, count);
    }
    
    /**
     * Calculate Flesch Reading Ease score.
     * Higher = easier to read (0-100 scale typically)
     */
    private double calculateFleschReadingEase(int words, int sentences, int syllables) {
        if (words == 0 || sentences == 0) return 0.0;
        return 206.835 - 1.015 * ((double) words / sentences) - 84.6 * ((double) syllables / words);
    }
    
    /**
     * Calculate Flesch-Kincaid Grade Level.
     * Returns approximate US grade level needed to understand the text.
     */
    private double calculateFleschKincaidGrade(int words, int sentences, int syllables) {
        if (words == 0 || sentences == 0) return 0.0;
        return 0.39 * ((double) words / sentences) + 11.8 * ((double) syllables / words) - 15.59;
    }
    
    /**
     * Extract keywords using TF-IDF-like scoring.
     */
    private List<String> extractKeywords(Map<String, Integer> frequencies, int totalWords) {
        // Simple keyword extraction: frequent content words
        return frequencies.entrySet().stream()
                .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
                .filter(e -> e.getKey().length() > 2)
                .sorted((a, b) -> {
                    // Score = frequency * length (longer words more significant)
                    int scoreA = a.getValue() * a.getKey().length();
                    int scoreB = b.getValue() * b.getKey().length();
                    return Integer.compare(scoreB, scoreA);
                })
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate composite vocabulary richness score (0-100).
     */
    private double calculateRichnessScore(double ttr, int hapax, int unique, int total, double lexDensity) {
        if (total == 0) return 0.0;
        
        // Components:
        // 1. TTR (normalized, higher = richer)
        double ttrScore = Math.min(ttr * 100, 50); // Cap at 50
        
        // 2. Hapax ratio (unique words appearing once = vocabulary breadth)
        double hapaxRatio = unique > 0 ? (double) hapax / unique : 0;
        double hapaxScore = hapaxRatio * 30; // Up to 30 points
        
        // 3. Lexical density
        double lexScore = lexDensity * 20; // Up to 20 points
        
        return Math.min(100, ttrScore + hapaxScore + lexScore);
    }
    
    /**
     * Empty analysis result.
     */
    private VocabularyAnalysis emptyAnalysis() {
        return new VocabularyAnalysis(0, 0, 0.0, 0, 0, 0.0, 0.0,
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), 0.0, 0.0,
                Collections.emptyList(), 0.0);
    }
    
    /**
     * Get a summary report of the vocabulary analysis.
     */
    public String getSummary(VocabularyAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Vocabulary Analysis ===\n\n");
        
        sb.append(String.format("Total Words: %d\n", analysis.totalWords));
        sb.append(String.format("Unique Words: %d\n", analysis.uniqueWords));
        sb.append(String.format("Type-Token Ratio: %.2f (%.1f%%)\n", 
                analysis.typeTokenRatio, analysis.typeTokenRatio * 100));
        sb.append(String.format("Hapax Legomena: %d (words appearing once)\n", analysis.hapaxLegomena));
        sb.append(String.format("Lexical Density: %.1f%%\n", analysis.lexicalDensity * 100));
        sb.append(String.format("Avg Word Length: %.1f chars\n", analysis.avgWordLength));
        sb.append(String.format("Vocabulary Richness Score: %.0f/100\n", analysis.vocabularyRichnessScore));
        
        sb.append("\nReadability:\n");
        sb.append(String.format("  Flesch Reading Ease: %.1f", analysis.fleschReadingEase));
        sb.append(getReadabilityLabel(analysis.fleschReadingEase)).append("\n");
        sb.append(String.format("  Flesch-Kincaid Grade: %.1f\n", analysis.fleschKincaidGrade));
        
        if (!analysis.keywords.isEmpty()) {
            sb.append("\nKeywords: ").append(String.join(", ", analysis.keywords)).append("\n");
        }
        
        if (!analysis.topWords.isEmpty()) {
            sb.append("\nTop Words:\n");
            for (int i = 0; i < Math.min(10, analysis.topWords.size()); i++) {
                WordFrequency wf = analysis.topWords.get(i);
                sb.append(String.format("  %d. %s (%d, %.1f%%) - %s\n",
                        i + 1, wf.word, wf.count, wf.percentage, wf.partOfSpeech));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Get readability label for Flesch score.
     */
    private String getReadabilityLabel(double score) {
        if (score >= 90) return " (Very Easy)";
        if (score >= 80) return " (Easy)";
        if (score >= 70) return " (Fairly Easy)";
        if (score >= 60) return " (Standard)";
        if (score >= 50) return " (Fairly Difficult)";
        if (score >= 30) return " (Difficult)";
        return " (Very Difficult)";
    }
}
