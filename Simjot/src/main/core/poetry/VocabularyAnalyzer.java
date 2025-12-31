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
        public final double avgSyllablesPerWord;
        public final int polysyllabicWords;
        public final double polysyllabicRatio;
        public final Map<String, Integer> wordFrequencies;
        public final List<WordFrequency> topWords;
        public final List<WordFrequency> rareWords;
        public final Map<String, Integer> posDistribution;
        public final Map<Integer, Integer> wordLengthDistribution;
        public final Map<Integer, Integer> syllableDistribution;
        public final double fleschReadingEase;
        public final double fleschKincaidGrade;
        public final double gunningFog;
        public final double smogIndex;
        public final double colemanLiauIndex;
        public final double automatedReadabilityIndex;
        public final List<String> keywords;
        public final double vocabularyRichnessScore;
        public final double mattr;
        public final double mtld;
        public final double yulesK;
        public final double simpsonsD;
        public final double lexicalSophistication;
        
        public VocabularyAnalysis(int totalWords, int uniqueWords, double typeTokenRatio,
                                 int hapaxLegomena, int disLegomena, double lexicalDensity,
                                 double avgWordLength, Map<String, Integer> wordFrequencies,
                                 List<WordFrequency> topWords, List<WordFrequency> rareWords,
                                 Map<String, Integer> posDistribution,
                                 Map<Integer, Integer> wordLengthDistribution,
                                 double fleschReadingEase, double fleschKincaidGrade,
                                 List<String> keywords, double vocabularyRichnessScore) {
            this(totalWords, uniqueWords, typeTokenRatio, hapaxLegomena, disLegomena, lexicalDensity,
                    avgWordLength, 0.0, 0, 0.0, wordFrequencies, topWords, rareWords, posDistribution,
                    wordLengthDistribution, Collections.emptyMap(), fleschReadingEase, fleschKincaidGrade,
                    0.0, 0.0, 0.0, 0.0, keywords, vocabularyRichnessScore, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        
        public VocabularyAnalysis(int totalWords, int uniqueWords, double typeTokenRatio,
                                 int hapaxLegomena, int disLegomena, double lexicalDensity,
                                 double avgWordLength, double avgSyllablesPerWord,
                                 int polysyllabicWords, double polysyllabicRatio,
                                 Map<String, Integer> wordFrequencies,
                                 List<WordFrequency> topWords, List<WordFrequency> rareWords,
                                 Map<String, Integer> posDistribution,
                                 Map<Integer, Integer> wordLengthDistribution,
                                 Map<Integer, Integer> syllableDistribution,
                                 double fleschReadingEase, double fleschKincaidGrade,
                                 double gunningFog, double smogIndex, double colemanLiauIndex,
                                 double automatedReadabilityIndex,
                                 List<String> keywords, double vocabularyRichnessScore,
                                 double mattr, double mtld, double yulesK, double simpsonsD,
                                 double lexicalSophistication) {
            this.totalWords = totalWords;
            this.uniqueWords = uniqueWords;
            this.typeTokenRatio = typeTokenRatio;
            this.hapaxLegomena = hapaxLegomena;
            this.disLegomena = disLegomena;
            this.lexicalDensity = lexicalDensity;
            this.avgWordLength = avgWordLength;
            this.avgSyllablesPerWord = avgSyllablesPerWord;
            this.polysyllabicWords = polysyllabicWords;
            this.polysyllabicRatio = polysyllabicRatio;
            this.wordFrequencies = Collections.unmodifiableMap(wordFrequencies);
            this.topWords = Collections.unmodifiableList(topWords);
            this.rareWords = Collections.unmodifiableList(rareWords);
            this.posDistribution = Collections.unmodifiableMap(posDistribution);
            this.wordLengthDistribution = Collections.unmodifiableMap(wordLengthDistribution);
            this.syllableDistribution = Collections.unmodifiableMap(syllableDistribution);
            this.fleschReadingEase = fleschReadingEase;
            this.fleschKincaidGrade = fleschKincaidGrade;
            this.gunningFog = gunningFog;
            this.smogIndex = smogIndex;
            this.colemanLiauIndex = colemanLiauIndex;
            this.automatedReadabilityIndex = automatedReadabilityIndex;
            this.keywords = Collections.unmodifiableList(keywords);
            this.vocabularyRichnessScore = vocabularyRichnessScore;
            this.mattr = mattr;
            this.mtld = mtld;
            this.yulesK = yulesK;
            this.simpsonsD = simpsonsD;
            this.lexicalSophistication = lexicalSophistication;
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
        
        List<String> normalizedWords = new ArrayList<>(allWords.size());
        
        // Word frequency count
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String word : allWords) {
            String lower = word.toLowerCase(Locale.ROOT);
            normalizedWords.add(lower);
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
        
        // Length/syllable distributions and lexical density
        Map<Integer, Integer> lengthDist = new TreeMap<>();
        Map<Integer, Integer> syllableDist = new TreeMap<>();
        int totalLen = 0;
        int totalSyllables = 0;
        int polysyllabicWords = 0;
        int advancedWords = 0;
        int contentWords = 0;
        int characterCount = 0;
        
        for (String word : allWords) {
            String lower = word.toLowerCase(Locale.ROOT);
            int len = word.length();
            int syllables = PoetryUtils.countSyllables(word);
            
            totalLen += len;
            totalSyllables += syllables;
            characterCount += len;
            
            lengthDist.merge(len, 1, Integer::sum);
            syllableDist.merge(syllables, 1, Integer::sum);
            
            if (syllables >= 3) polysyllabicWords++;
            if (!PoetryDictionary.isFunctionWord(lower)) {
                contentWords++;
                if (syllables >= 3 || len >= 7) advancedWords++;
            }
        }
        
        double avgLen = totalWords > 0 ? (double) totalLen / totalWords : 0.0;
        double avgSyllablesPerWord = totalWords > 0 ? (double) totalSyllables / totalWords : 0.0;
        double polysyllabicRatio = totalWords > 0 ? (double) polysyllabicWords / totalWords : 0.0;
        double lexicalDensity = totalWords > 0 ? (double) contentWords / totalWords : 0.0;
        double lexicalSophistication = totalWords > 0 ? (double) advancedWords / totalWords : 0.0;
        
        // POS distribution
        Map<String, Integer> posDist = new LinkedHashMap<>();
        for (String word : frequencies.keySet()) {
            String pos = PoetryDictionary.getPOS(word);
            posDist.merge(pos, frequencies.get(word), Integer::sum);
        }
        
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
        
        double fleschEase = calculateFleschReadingEase(totalWords, sentences, totalSyllables);
        double fleschGrade = calculateFleschKincaidGrade(totalWords, sentences, totalSyllables);
        double gunningFog = calculateGunningFog(totalWords, sentences, polysyllabicWords);
        double smogIndex = calculateSmogIndex(polysyllabicWords, sentences);
        double colemanLiau = calculateColemanLiauIndex(totalWords, sentences, characterCount);
        double ari = calculateAutomatedReadabilityIndex(totalWords, sentences, characterCount);
        
        // Lexical diversity indices
        double mattr = calculateMATTR(normalizedWords, 50);
        double mtld = calculateMTLD(normalizedWords, 0.72);
        double yulesK = calculateYulesK(frequencies, totalWords);
        double simpsonsD = calculateSimpsonsD(frequencies, totalWords);
        
        // Keywords (high TF, not function words)
        List<String> keywords = extractKeywords(frequencies, totalWords);
        
        // Vocabulary richness score (composite metric)
        double richnessScore = calculateRichnessScore(ttr, hapax, uniqueWords, totalWords,
                lexicalDensity, lexicalSophistication, mattr);
        
        return new VocabularyAnalysis(totalWords, uniqueWords, ttr, hapax, dis,
                lexicalDensity, avgLen, avgSyllablesPerWord, polysyllabicWords, polysyllabicRatio,
                frequencies, topWords, rareWords, posDist, lengthDist, syllableDist,
                fleschEase, fleschGrade, gunningFog, smogIndex, colemanLiau, ari,
                keywords, richnessScore, mattr, mtld, yulesK, simpsonsD, lexicalSophistication);
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
    
    private double calculateGunningFog(int words, int sentences, int polysyllabicWords) {
        if (words == 0 || sentences == 0) return 0.0;
        double complexRatio = (double) polysyllabicWords / words;
        return 0.4 * (((double) words / sentences) + 100 * complexRatio);
    }
    
    private double calculateSmogIndex(int polysyllabicWords, int sentences) {
        if (polysyllabicWords == 0 || sentences == 0) return 0.0;
        return 1.043 * Math.sqrt(polysyllabicWords * (30.0 / sentences)) + 3.1291;
    }
    
    private double calculateColemanLiauIndex(int words, int sentences, int characters) {
        if (words == 0 || sentences == 0) return 0.0;
        double l = ((double) characters / words) * 100;
        double s = ((double) sentences / words) * 100;
        return 0.0588 * l - 0.296 * s - 15.8;
    }
    
    private double calculateAutomatedReadabilityIndex(int words, int sentences, int characters) {
        if (words == 0 || sentences == 0) return 0.0;
        return 4.71 * ((double) characters / words) + 0.5 * ((double) words / sentences) - 21.43;
    }
    
    private double calculateMATTR(List<String> words, int windowSize) {
        if (words.isEmpty()) return 0.0;
        if (words.size() <= windowSize) {
            return (double) new java.util.HashSet<>(words).size() / words.size();
        }
        double total = 0.0;
        int windows = words.size() - windowSize + 1;
        for (int i = 0; i < windows; i++) {
            java.util.Set<String> window = new java.util.HashSet<>(words.subList(i, i + windowSize));
            total += (double) window.size() / windowSize;
        }
        return total / windows;
    }
    
    private double calculateMTLD(List<String> words, double threshold) {
        if (words.isEmpty()) return 0.0;
        double forward = calculateMTLDForward(words, threshold);
        List<String> reversed = new ArrayList<>(words);
        java.util.Collections.reverse(reversed);
        double backward = calculateMTLDForward(reversed, threshold);
        return (forward + backward) / 2.0;
    }
    
    private double calculateMTLDForward(List<String> words, double threshold) {
        java.util.Map<String, Integer> freq = new java.util.HashMap<>();
        int tokenCount = 0;
        int typeCount = 0;
        int factors = 0;
        
        for (String word : words) {
            tokenCount++;
            Integer existing = freq.put(word, freq.getOrDefault(word, 0) + 1);
            if (existing == null) typeCount++;
            
            double ttr = tokenCount > 0 ? (double) typeCount / tokenCount : 0.0;
            if (ttr <= threshold) {
                factors++;
                tokenCount = 0;
                typeCount = 0;
                freq.clear();
            }
        }
        
        double ttr = tokenCount > 0 ? (double) typeCount / tokenCount : 0.0;
        double remainder = (ttr == 0) ? 0.0 : (1 - ttr) / (1 - threshold);
        double totalFactors = factors + remainder;
        return totalFactors > 0 ? words.size() / totalFactors : 0.0;
    }
    
    private double calculateYulesK(Map<String, Integer> frequencies, int totalWords) {
        if (totalWords == 0) return 0.0;
        double sum = 0.0;
        for (int count : frequencies.values()) {
            sum += count * count;
        }
        return 10000.0 * (sum - totalWords) / (totalWords * (double) totalWords);
    }
    
    private double calculateSimpsonsD(Map<String, Integer> frequencies, int totalWords) {
        if (totalWords <= 1) return 0.0;
        double sum = 0.0;
        for (int count : frequencies.values()) {
            sum += (double) count * (count - 1);
        }
        return sum / (totalWords * (double) (totalWords - 1));
    }
    
    /**
     * Extract keywords using TF-IDF-like scoring.
     */
    private List<String> extractKeywords(Map<String, Integer> frequencies, int totalWords) {
        // Weighted keyword extraction: frequency + length + syllable complexity
        return frequencies.entrySet().stream()
                .filter(e -> !PoetryDictionary.isFunctionWord(e.getKey()))
                .filter(e -> e.getKey().length() > 2)
                .sorted((a, b) -> Double.compare(
                        keywordScore(b.getKey(), b.getValue(), totalWords),
                        keywordScore(a.getKey(), a.getValue(), totalWords)))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    private double keywordScore(String word, int count, int totalWords) {
        if (totalWords == 0) return 0.0;
        double tf = (double) count / totalWords;
        int syllables = PoetryUtils.countSyllables(word);
        double lengthBoost = Math.log(1 + word.length());
        double syllableBoost = 1.0 + Math.max(0, syllables - 1) * 0.15;
        String pos = PoetryDictionary.getPOS(word);
        double posBoost = switch (pos) {
            case "Noun", "Verb", "Adjective" -> 1.2;
            default -> 1.0;
        };
        return tf * lengthBoost * syllableBoost * posBoost;
    }
    
    /**
     * Calculate composite vocabulary richness score (0-100).
     */
    private double calculateRichnessScore(double ttr, int hapax, int unique, int total,
                                         double lexDensity, double lexicalSophistication, double mattr) {
        if (total == 0) return 0.0;
        
        // Components:
        // 1. TTR (normalized, higher = richer)
        double ttrScore = Math.min(ttr * 100, 40); // Cap at 40
        
        // 2. Hapax ratio (unique words appearing once = vocabulary breadth)
        double hapaxRatio = unique > 0 ? (double) hapax / unique : 0;
        double hapaxScore = hapaxRatio * 25; // Up to 25 points
        
        // 3. Lexical density
        double lexScore = lexDensity * 15; // Up to 15 points
        
        // 4. Lexical sophistication
        double sophisticationScore = lexicalSophistication * 10; // Up to 10 points
        
        // 5. Moving-average TTR
        double mattrScore = Math.min(mattr * 100, 10); // Up to 10 points
        
        return Math.min(100, ttrScore + hapaxScore + lexScore + sophisticationScore + mattrScore);
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
        sb.append(String.format("Avg Syllables/Word: %.2f\n", analysis.avgSyllablesPerWord));
        sb.append(String.format("Polysyllabic Words: %d (%.1f%%)\n",
                analysis.polysyllabicWords, analysis.polysyllabicRatio * 100));
        sb.append(String.format("Lexical Sophistication: %.1f%%\n", analysis.lexicalSophistication * 100));
        sb.append(String.format("Vocabulary Richness Score: %.0f/100\n", analysis.vocabularyRichnessScore));
        
        sb.append("\nLexical Diversity:\n");
        sb.append(String.format("  MATTR (50): %.3f\n", analysis.mattr));
        sb.append(String.format("  MTLD: %.1f\n", analysis.mtld));
        sb.append(String.format("  Yule's K: %.1f\n", analysis.yulesK));
        sb.append(String.format("  Simpson's D: %.3f\n", analysis.simpsonsD));
        
        sb.append("\nReadability:\n");
        sb.append(String.format("  Flesch Reading Ease: %.1f", analysis.fleschReadingEase));
        sb.append(getReadabilityLabel(analysis.fleschReadingEase)).append("\n");
        sb.append(String.format("  Flesch-Kincaid Grade: %.1f\n", analysis.fleschKincaidGrade));
        sb.append(String.format("  Gunning Fog: %.1f\n", analysis.gunningFog));
        sb.append(String.format("  SMOG: %.1f\n", analysis.smogIndex));
        sb.append(String.format("  Coleman-Liau: %.1f\n", analysis.colemanLiauIndex));
        sb.append(String.format("  ARI: %.1f\n", analysis.automatedReadabilityIndex));
        
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
