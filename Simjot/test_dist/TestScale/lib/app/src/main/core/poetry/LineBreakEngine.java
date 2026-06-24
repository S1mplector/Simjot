/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.poetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Line break analysis engine for poetry formatting and prose-to-poetry conversion.
 * 
 * <p>This engine analyzes text to suggest optimal line breaks based on multiple
 * factors including meter, meaning, breath pauses, and poetic structure. It supports various
 * formatting styles and can convert prose into poetic form while maintaining readability and
 * aesthetic appeal.</p>
 * 
 * <p><strong>Key Capabilities:</strong></p>
 * <ul>
 *   <li><strong>Meter-Aware Breaking</strong> - Suggests breaks that maintain poetic meter</li>
 *   <li><strong>Semantic Analysis</strong> - Detects clause boundaries and natural pause points</li>
 *   <li><strong>Enjambment Detection</strong> - Identifies opportunities for line continuation</li>
 *   <li><strong>Stanza Organization</strong> - Groups lines into coherent stanzas</li>
 *   <li><strong>Multiple Styles</strong> - Supports various poetic forms and formatting preferences</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Analyze text for line breaks
 * LineBreakEngine engine = new LineBreakEngine();
 * LineBreakEngine.BreakConfig config = LineBreakEngine.BreakConfig.defaultConfig();
 * LineBreakEngine.BreakAnalysis analysis = engine.analyze(text, config);
 * 
 * // Get reformatted poetry
 * String formattedPoem = analysis.reformattedText;
 * List<BreakSuggestion> suggestions = analysis.suggestions;
 * }</pre>
 * 
 * @author S1mplector
 * @version 1.0.0
 */
public class LineBreakEngine {
    
    /**
     * Represents a single line break suggestion with detailed metadata.
     * 
     * <p>Each suggestion includes the position, type, confidence score, and reasoning
     * for why this particular break point was recommended.</p>
     */
    public static class BreakSuggestion {
        /** Character position in the original text where the break should occur. */
        public final int position;
        
        /** The type of break being suggested (line break, stanza break, etc.). */
        public final BreakType type;
        
        /** Confidence score from 0.0 to 1.0 indicating how strongly this break is recommended. */
        public final double confidence;
        
        /** Human-readable explanation of why this break was suggested. */
        public final String reason;
        
        /**
         * Creates a new break suggestion.
         * 
         * @param position Character position in original text (0-based index)
         * @param type The type of break being suggested
         * @param confidence Confidence score (0.0 to 1.0)
         * @param reason Explanation for the suggestion
         */
        public BreakSuggestion(int position, BreakType type, double confidence, String reason) {
            this.position = position;
            this.type = type;
            this.confidence = confidence;
            this.reason = reason;
        }
    }
    
    /**
     * Enumeration of different types of line breaks that can be suggested.
     * 
     * <p>Each break type represents a different poetic structure or formatting decision,
     * from simple line breaks to complex stanza divisions and enjambment opportunities.</p>
     */
    public enum BreakType {
        /** Standard line break ending a line of poetry. */
        LINE_BREAK("Line break"),
        
        /** Break between stanzas, typically with additional spacing. */
        STANZA_BREAK("Stanza break"),
        
        /** Mid-line pause (caesura) for dramatic effect or breath. */
        CAESURA("Mid-line pause"),
        
        /** Opportunity for enjambment (continuation of thought without punctuation). */
        ENJAMBMENT("Enjambment opportunity");
        
        /** Human-readable description of the break type. */
        public final String description;
        
        /**
         * Creates a new break type with description.
         * 
         * @param description Human-readable description
         */
        BreakType(String description) { this.description = description; }
    }
    
    /**
     * Complete analysis result containing all suggestions and reformatted text.
     * 
     * <p>This class encapsulates the full output of the line break analysis process,
     * including the original text, all suggestions, and a reformatted version with
     * the suggested breaks applied.</p>
     */
    public static class BreakAnalysis {
        /** The original text that was analyzed. */
        public final String originalText;
        
        /** Immutable list of all break suggestions, sorted by position. */
        public final List<BreakSuggestion> suggestions;
        
        /** The reformatted text with suggested breaks applied. */
        public final String reformattedText;
        
        /** Total number of lines suggested in the reformatted text. */
        public final int suggestedLineCount;
        
        /** Total number of stanzas suggested in the reformatted text. */
        public final int suggestedStanzaCount;
        
        /** The target meter pattern that was used or detected. */
        public final String meterTarget;
        
        /**
         * Creates a new break analysis result.
         * 
         * @param originalText The original text that was analyzed
         * @param suggestions List of break suggestions (will be copied to immutable list)
         * @param reformattedText The reformatted text with breaks applied
         * @param suggestedLineCount Number of lines in reformatted text
         * @param suggestedStanzaCount Number of stanzas in reformatted text
         * @param meterTarget The meter pattern used for analysis
         */
        public BreakAnalysis(String originalText, List<BreakSuggestion> suggestions,
                            String reformattedText, int suggestedLineCount,
                            int suggestedStanzaCount, String meterTarget) {
            this.originalText = originalText;
            this.suggestions = Collections.unmodifiableList(suggestions);
            this.reformattedText = reformattedText;
            this.suggestedLineCount = suggestedLineCount;
            this.suggestedStanzaCount = suggestedStanzaCount;
            this.meterTarget = meterTarget;
        }
    }
    
    /**
     * Configuration object controlling how line break analysis is performed.
     * 
     * <p>This class allows fine-tuning of the analysis process including syllable targets,
     * formatting preferences, and structural constraints.</p>
     */
    public static class BreakConfig {
        /** Target syllables per line (0 = auto-detect from text). */
        public final int targetSyllables;
        
        /** Minimum syllables allowed per line. */
        public final int minSyllables;
        
        /** Maximum syllables allowed per line. */
        public final int maxSyllables;
        
        /** Whether to prefer lines ending with punctuation (end-stopped lines). */
        public final boolean preferEndStopped;
        
        /** Number of lines per stanza (0 = auto-detect based on content). */
        public final int linesPerStanza;
        
        /**
         * Creates a new break configuration.
         * 
         * @param targetSyllables Target syllables per line (0 for auto-detect)
         * @param minSyllables Minimum syllables per line
         * @param maxSyllables Maximum syllables per line
         * @param preferEndStopped Whether to prefer end-stopped lines
         * @param linesPerStanza Lines per stanza (0 for auto-detect)
         */
        public BreakConfig(int targetSyllables, int minSyllables, int maxSyllables,
                          boolean preferEndStopped, int linesPerStanza) {
            this.targetSyllables = targetSyllables;
            this.minSyllables = minSyllables;
            this.maxSyllables = maxSyllables;
            this.preferEndStopped = preferEndStopped;
            this.linesPerStanza = linesPerStanza;
        }
        
        /**
         * Creates a default configuration suitable for most poetry.
         * 
         * @return Default configuration with 10 target syllables, 6-14 range, end-stopped preference, 4-line stanzas
         */
        public static BreakConfig defaultConfig() {
            return new BreakConfig(10, 6, 14, true, 4);
        }
        
        /**
         * Creates a configuration optimized for free verse poetry.
         * 
         * @return Free verse configuration with flexible syllable counts and stanza structure
         */
        public static BreakConfig freeVerse() {
            return new BreakConfig(0, 3, 20, false, 0);
        }
        
        public static BreakConfig iambicPentameter() {
            return new BreakConfig(10, 9, 11, true, 0);
        }
        
        public static BreakConfig haiku() {
            return new BreakConfig(0, 5, 7, false, 3);
        }
    }
    
    /**
     * Analyze text and suggest line breaks.
     */
    public BreakAnalysis analyze(String text, BreakConfig config) {
        if (text == null || text.isBlank()) {
            return new BreakAnalysis(text, Collections.emptyList(), "", 0, 0, "");
        }
        
        if (config == null) config = BreakConfig.defaultConfig();
        
        List<BreakSuggestion> suggestions = new ArrayList<>();
        List<WordInfo> words = extractWords(text);
        
        if (words.isEmpty()) {
            return new BreakAnalysis(text, suggestions, text, 0, 0, "");
        }
        
        // Find natural break points
        List<Integer> naturalBreaks = findNaturalBreaks(text, words);
        
        // Find meter-based breaks
        List<Integer> meterBreaks = findMeterBreaks(words, config);
        
        // Combine and score breaks
        Set<Integer> allBreakPositions = new TreeSet<>();
        allBreakPositions.addAll(naturalBreaks);
        allBreakPositions.addAll(meterBreaks);
        
        // Score each potential break
        for (int pos : allBreakPositions) {
            double confidence = 0.0;
            StringBuilder reason = new StringBuilder();
            
            // Check if it's a natural break
            if (naturalBreaks.contains(pos)) {
                confidence += 0.4;
                reason.append("Natural pause point");
            }
            
            // Check if it's a meter break
            if (meterBreaks.contains(pos)) {
                confidence += 0.4;
                if (reason.length() > 0) reason.append("; ");
                reason.append("Meter alignment");
            }
            
            // Check punctuation at break
            if (isPunctuationBefore(text, pos)) {
                confidence += 0.2;
                if (reason.length() > 0) reason.append("; ");
                reason.append("After punctuation");
            }
            
            suggestions.add(new BreakSuggestion(pos, BreakType.LINE_BREAK, 
                    Math.min(1.0, confidence), reason.toString()));
        }
        
        // Sort by position
        suggestions.sort(Comparator.comparingInt(s -> s.position));
        
        // Filter to best breaks based on config
        List<BreakSuggestion> filteredSuggestions = filterBreaks(suggestions, words, config);
        
        // Add stanza breaks
        filteredSuggestions = addStanzaBreaks(filteredSuggestions, config);
        
        // Generate reformatted text
        String reformatted = applyBreaks(text, filteredSuggestions);
        
        // Count lines and stanzas
        int lineCount = countLines(reformatted);
        int stanzaCount = countStanzas(reformatted);
        
        String meterTarget = config.targetSyllables > 0 ? 
                config.targetSyllables + " syllables" : "Free verse";
        
        return new BreakAnalysis(text, filteredSuggestions, reformatted, 
                lineCount, stanzaCount, meterTarget);
    }
    
    /**
     * Analyze existing poetry for enjambment.
     */
    public List<EnjambmentInfo> detectEnjambment(String text) {
        List<EnjambmentInfo> results = new ArrayList<>();
        List<String> lines = PoetryUtils.splitLines(text);
        
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String nextLine = lines.get(i + 1);
            
            if (line == null || line.isBlank()) continue;
            if (nextLine == null || nextLine.isBlank()) continue;
            
            EnjambmentType type = classifyEnjambment(line, nextLine);
            if (type != EnjambmentType.NONE) {
                results.add(new EnjambmentInfo(i, type, getEnjambmentDescription(type)));
            }
        }
        
        return results;
    }
    
    /**
     * Enjambment information.
     */
    public static class EnjambmentInfo {
        public final int lineIndex;
        public final EnjambmentType type;
        public final String description;
        
        public EnjambmentInfo(int lineIndex, EnjambmentType type, String description) {
            this.lineIndex = lineIndex;
            this.type = type;
            this.description = description;
        }
    }
    
    /**
     * Types of enjambment.
     */
    public enum EnjambmentType {
        NONE,
        SOFT,      // Natural phrase continues
        HARD,      // Sentence continues mid-clause
        LEXICAL,   // Word broken across lines
        SYNTACTIC  // Syntax broken (adj-noun, verb-object)
    }
    
    // --- Helper classes and methods ---
    
    private static class WordInfo {
        final String word;
        final int startPos;
        final int endPos;
        final int syllables;
        
        WordInfo(String word, int startPos, int endPos) {
            this.word = word;
            this.startPos = startPos;
            this.endPos = endPos;
            this.syllables = PoetryUtils.countSyllables(word);
        }
    }
    
    /**
     * Extract words with positions.
     */
    private List<WordInfo> extractWords(String text) {
        List<WordInfo> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startPos = -1;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '\'') {
                if (current.length() == 0) startPos = i;
                current.append(c);
            } else {
                if (current.length() > 0) {
                    words.add(new WordInfo(current.toString(), startPos, i));
                    current = new StringBuilder();
                }
            }
        }
        if (current.length() > 0) {
            words.add(new WordInfo(current.toString(), startPos, text.length()));
        }
        
        return words;
    }
    
    /**
     * Find natural break points (punctuation, clause boundaries).
     */
    private List<Integer> findNaturalBreaks(String text, List<WordInfo> words) {
        List<Integer> breaks = new ArrayList<>();
        
        for (int i = 0; i < words.size() - 1; i++) {
            WordInfo word = words.get(i);
            WordInfo next = words.get(i + 1);
            
            // Check text between words for punctuation
            String between = text.substring(word.endPos, next.startPos);
            
            // Strong break indicators
            if (between.contains(".") || between.contains("!") || between.contains("?") ||
                between.contains(";") || between.contains(":")) {
                breaks.add(next.startPos);
            }
            // Moderate break indicators
            else if (between.contains(",") || between.contains("—") || between.contains("–")) {
                breaks.add(next.startPos);
            }
            // Conjunctions often indicate clause boundaries
            else if (isConjunction(next.word)) {
                breaks.add(next.startPos);
            }
        }
        
        return breaks;
    }
    
    /**
     * Find meter-based break points.
     */
    private List<Integer> findMeterBreaks(List<WordInfo> words, BreakConfig config) {
        List<Integer> breaks = new ArrayList<>();
        int target = config.targetSyllables > 0 ? config.targetSyllables : 10;
        int min = config.minSyllables;
        int max = config.maxSyllables;
        
        int syllableCount = 0;
        for (int i = 0; i < words.size(); i++) {
            WordInfo word = words.get(i);
            syllableCount += word.syllables;
            
            // Check if we should break after this word
            if (syllableCount >= min) {
                if (syllableCount >= target && syllableCount <= max) {
                    // Good break point
                    if (i < words.size() - 1) {
                        breaks.add(words.get(i + 1).startPos);
                    }
                    syllableCount = 0;
                } else if (syllableCount > max) {
                    // Need to break
                    if (i < words.size() - 1) {
                        breaks.add(words.get(i + 1).startPos);
                    }
                    syllableCount = 0;
                }
            }
        }
        
        return breaks;
    }
    
    /**
     * Check if position has punctuation before it.
     */
    private boolean isPunctuationBefore(String text, int pos) {
        if (pos <= 0 || pos > text.length()) return false;
        // Look back for punctuation
        for (int i = pos - 1; i >= 0 && i >= pos - 3; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?') {
                return true;
            }
            if (Character.isLetter(c)) break;
        }
        return false;
    }
    
    /**
     * Check if word is a conjunction.
     */
    private boolean isConjunction(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return Set.of("and", "or", "but", "nor", "for", "yet", "so", "while", "when",
                "where", "because", "although", "though", "if", "unless", "until",
                "before", "after", "since", "as").contains(lower);
    }
    
    /**
     * Filter breaks to optimal set.
     */
    private List<BreakSuggestion> filterBreaks(List<BreakSuggestion> suggestions, 
                                               List<WordInfo> words, BreakConfig config) {
        if (suggestions.isEmpty()) return suggestions;
        
        List<BreakSuggestion> filtered = new ArrayList<>();
        int lastBreakPos = 0;
        
        for (BreakSuggestion suggestion : suggestions) {
            // Ensure minimum distance between breaks
            if (suggestion.position - lastBreakPos >= 20) { // At least ~20 chars
                filtered.add(suggestion);
                lastBreakPos = suggestion.position;
            } else if (suggestion.confidence > 0.7) {
                // High confidence breaks can be closer
                filtered.add(suggestion);
                lastBreakPos = suggestion.position;
            }
        }
        
        return filtered;
    }
    
    /**
     * Add stanza breaks.
     */
    private List<BreakSuggestion> addStanzaBreaks(List<BreakSuggestion> suggestions, 
                                                   BreakConfig config) {
        if (config.linesPerStanza <= 0 || suggestions.isEmpty()) return suggestions;
        
        List<BreakSuggestion> result = new ArrayList<>();
        int lineCount = 0;
        
        for (BreakSuggestion s : suggestions) {
            result.add(s);
            lineCount++;
            
            if (lineCount >= config.linesPerStanza) {
                // Add stanza break after this line break
                result.add(new BreakSuggestion(s.position, BreakType.STANZA_BREAK, 
                        0.8, "Stanza division"));
                lineCount = 0;
            }
        }
        
        return result;
    }
    
    /**
     * Apply breaks to text.
     */
    private String applyBreaks(String text, List<BreakSuggestion> breaks) {
        if (breaks.isEmpty()) return text;
        
        StringBuilder result = new StringBuilder();
        int lastPos = 0;
        
        for (BreakSuggestion b : breaks) {
            if (b.position > lastPos && b.position <= text.length()) {
                result.append(text.substring(lastPos, b.position).trim());
                if (b.type == BreakType.STANZA_BREAK) {
                    result.append("\n\n");
                } else {
                    result.append("\n");
                }
                lastPos = b.position;
            }
        }
        
        if (lastPos < text.length()) {
            result.append(text.substring(lastPos).trim());
        }
        
        return result.toString();
    }
    
    /**
     * Count lines in text.
     */
    private int countLines(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) text.lines().filter(l -> !l.isBlank()).count();
    }
    
    /**
     * Count stanzas in text.
     */
    private int countStanzas(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\n\n+").length;
    }
    
    /**
     * Classify enjambment between two lines.
     */
    private EnjambmentType classifyEnjambment(String line, String nextLine) {
        String trimmed = line.trim();
        
        // Check if line ends with punctuation (no enjambment)
        if (trimmed.isEmpty()) return EnjambmentType.NONE;
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == ';' || last == ':') {
            return EnjambmentType.NONE;
        }
        
        // Get last word of line and first word of next line
        List<String> words1 = PoetryUtils.wordsInLine(line);
        List<String> words2 = PoetryUtils.wordsInLine(nextLine);
        
        if (words1.isEmpty() || words2.isEmpty()) return EnjambmentType.NONE;
        
        String lastWord = words1.get(words1.size() - 1).toLowerCase(Locale.ROOT);
        String firstWord = words2.get(0).toLowerCase(Locale.ROOT);
        
        // Check for syntactic enjambment patterns
        if (isArticleOrDeterminer(lastWord)) return EnjambmentType.SYNTACTIC;
        if (isPreposition(lastWord)) return EnjambmentType.SYNTACTIC;
        if (isAuxiliaryVerb(lastWord)) return EnjambmentType.SYNTACTIC;
        if (lastWord.equals("to") && !isPreposition(firstWord)) return EnjambmentType.SYNTACTIC;
        
        // Soft enjambment (comma or minor pause)
        if (last == ',') return EnjambmentType.SOFT;
        
        // Hard enjambment (no punctuation)
        return EnjambmentType.HARD;
    }
    
    private boolean isArticleOrDeterminer(String word) {
        return Set.of("a", "an", "the", "this", "that", "these", "those", 
                "my", "your", "his", "her", "its", "our", "their").contains(word);
    }
    
    private boolean isPreposition(String word) {
        return Set.of("in", "on", "at", "by", "for", "with", "to", "from", "of",
                "into", "onto", "upon", "through", "across", "over", "under",
                "between", "among", "about", "around", "before", "after").contains(word);
    }
    
    private boolean isAuxiliaryVerb(String word) {
        return Set.of("is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would",
                "shall", "should", "may", "might", "must", "can", "could").contains(word);
    }
    
    private String getEnjambmentDescription(EnjambmentType type) {
        return switch (type) {
            case SOFT -> "Soft enjambment - phrase continues naturally";
            case HARD -> "Hard enjambment - sentence breaks mid-flow";
            case SYNTACTIC -> "Syntactic enjambment - grammar structure spans lines";
            case LEXICAL -> "Lexical enjambment - word split across lines";
            default -> "";
        };
    }
    
    /**
     * Get summary of break analysis.
     */
    public String getSummary(BreakAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Line Break Analysis ===\n\n");
        sb.append(String.format("Target: %s\n", analysis.meterTarget));
        sb.append(String.format("Suggested Lines: %d\n", analysis.suggestedLineCount));
        sb.append(String.format("Suggested Stanzas: %d\n", analysis.suggestedStanzaCount));
        sb.append(String.format("Break Points: %d\n\n", analysis.suggestions.size()));
        
        sb.append("Reformatted:\n");
        sb.append("─".repeat(40)).append("\n");
        sb.append(analysis.reformattedText);
        sb.append("\n").append("─".repeat(40));
        
        return sb.toString();
    }
}
