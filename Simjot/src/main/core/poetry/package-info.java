/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * <h1>Simjot Poetry Analysis Package</h1>
 * 
 * <p>Professional-grade poetry analysis utilities providing academic-level
 * prosodic, phonetic, structural, lexical, and sentiment analysis.</p>
 * 
 * <h2>Components</h2>
 * <ul>
 *   <li><b>PoetryAnalyzer</b> - Main analysis engine with comprehensive poem analysis</li>
 *   <li><b>PoetryUtils</b> - Core utilities for syllable counting, rhyme detection, meter</li>
 *   <li><b>PoetryDictionary</b> - Dictionary service with POS, synonyms, stress patterns</li>
 * </ul>
 * 
 * <h2>Analysis Categories</h2>
 * 
 * <h3>Prosodic Analysis</h3>
 * <ul>
 *   <li>Metrical foot identification (iamb, trochee, anapest, dactyl, spondee, pyrrhic)</li>
 *   <li>Meter length detection (monometer through octameter)</li>
 *   <li>Line-by-line scansion with stress patterns</li>
 *   <li>Metrical regularity scoring</li>
 *   <li>Foot distribution statistics</li>
 * </ul>
 * 
 * <h3>Phonetic Analysis</h3>
 * <ul>
 *   <li>Rhyme scheme detection (ABAB, ABBA, etc.)</li>
 *   <li>Rhyme grouping by line</li>
 *   <li>Alliteration detection (repeated initial consonants)</li>
 *   <li>Assonance detection (repeated vowel sounds)</li>
 *   <li>Consonance detection (repeated end consonants)</li>
 *   <li>Phonetic device density calculation</li>
 * </ul>
 * 
 * <h3>Structural Analysis</h3>
 * <ul>
 *   <li>Form detection (sonnet, haiku, limerick, villanelle, etc.)</li>
 *   <li>Stanza structure analysis</li>
 *   <li>Line and syllable counting</li>
 *   <li>Refrain detection</li>
 *   <li>Enjambment vs end-stopped line analysis</li>
 * </ul>
 * 
 * <h3>Lexical Analysis</h3>
 * <ul>
 *   <li>Word frequency analysis</li>
 *   <li>Type-token ratio (vocabulary richness)</li>
 *   <li>Part of speech distribution</li>
 *   <li>Average word length</li>
 *   <li>Polysyllabic word counting</li>
 *   <li>Flesch readability scoring</li>
 * </ul>
 * 
 * <h3>Sentiment Analysis</h3>
 * <ul>
 *   <li>Overall tone classification</li>
 *   <li>Positive/negative/neutral scoring</li>
 *   <li>Emotional intensity measurement</li>
 *   <li>Imagery type detection (visual, auditory, tactile, etc.)</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Full analysis
 * PoemAnalysis analysis = PoetryAnalyzer.analyze("Sonnet 18", poemText);
 * 
 * // Access specific analysis
 * String meter = analysis.prosody.meterName;  // "Iambic Pentameter"
 * String scheme = analysis.phonetics.rhymeScheme;  // "ABABCDCDEFEFGG"
 * String form = analysis.structure.form;  // "Shakespearean Sonnet"
 * 
 * // Line-by-line scansion
 * for (LineScansion line : analysis.prosody.scansions) {
 *     System.out.println(line.scansionMarks);  // "˘ ´ ˘ ´ ˘ ´ ˘ ´ ˘ ´"
 * }
 * 
 * // Individual utilities
 * int syllables = PoetryUtils.countSyllables("beautiful");  // 3
 * boolean rhymes = PoetryUtils.rhymes("love", "dove");  // true
 * String rhymeKey = PoetryUtils.rhymeKey("night");  // "ight"
 * }</pre>
 * 
 * <h2>Metrical Foot Reference</h2>
 * <table border="1">
 *   <tr><th>Foot</th><th>Pattern</th><th>Symbol</th><th>Example</th></tr>
 *   <tr><td>Iamb</td><td>u S</td><td>˘ ´</td><td>a-LONE</td></tr>
 *   <tr><td>Trochee</td><td>S u</td><td>´ ˘</td><td>GAR-den</td></tr>
 *   <tr><td>Anapest</td><td>u u S</td><td>˘ ˘ ´</td><td>in-ter-VENE</td></tr>
 *   <tr><td>Dactyl</td><td>S u u</td><td>´ ˘ ˘</td><td>MER-ri-ly</td></tr>
 *   <tr><td>Spondee</td><td>S S</td><td>´ ´</td><td>HEART-BREAK</td></tr>
 *   <tr><td>Pyrrhic</td><td>u u</td><td>˘ ˘</td><td>of the</td></tr>
 * </table>
 * 
 * <h2>Form Detection</h2>
 * <p>The analyzer can detect:</p>
 * <ul>
 *   <li><b>Sonnet</b> - 14 lines (Shakespearean or Petrarchan)</li>
 *   <li><b>Haiku</b> - 3 lines with 5-7-5 syllable pattern</li>
 *   <li><b>Limerick</b> - 5 lines with AABBA rhyme</li>
 *   <li><b>Quatrains</b> - Stanzas of 4 lines</li>
 *   <li><b>Tercets</b> - Stanzas of 3 lines</li>
 *   <li><b>Couplets</b> - Stanzas of 2 lines</li>
 *   <li><b>Free Verse</b> - Irregular structure</li>
 * </ul>
 * 
 * @author S1mplector
 * @version 1.0.0
 */
package main.core.poetry;
