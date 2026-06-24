# Poetry Engine

The poetry engine provides advanced linguistic analysis and creative assistance for poetry writing and analysis.

## Core Components

### PoetryUtils
- **Syllable counting** with exception handling for irregular words
- **Rhyme detection** (exact, near, slant rhymes)
- **Meter analysis** (iambic, trochaic patterns)
- **Stress pattern estimation**

### RhymeDatabase
- **Comprehensive rhyme dictionary** with 50+ rhyme groups
- **Synonym database** for poetic vocabulary enhancement
- **Dictionary integration** with Simple English Dictionary
- **Performance optimized** with caching and lazy loading

### MeterScanner
- **Real-time meter analysis** with stress pattern detection
- **Poetic form detection** (sonnets, haiku, limerick, etc.)
- **Rhyme scheme analysis** with automatic labeling
- **Statistical analysis** (syllables, words, stanzas)

### MeterAnalysis
- **Immutable analysis results** for UI rendering
- **Comprehensive metrics** and statistics
- **Tooltip generation** for educational feedback
- **Form detection** with confidence scoring

## Features

### Syllable Counting
- Advanced algorithm with 70+ word overrides
- Handles silent 'e', diphthongs, and special endings
- Supports compound words and proper nouns
- Performance optimized for real-time analysis

### Rhyme Detection
- **Exact rhymes**: Perfect sound matching
- **Near rhymes**: Slant rhymes and consonance
- **Rhyme groups**: Predefined common endings
- **Dictionary integration**: 10,000+ word database

### Meter Analysis
- **Iambic detection**: unstressed-stressed patterns
- **Trochaic detection**: stressed-unstressed patterns
- **Form recognition**: Sonnet, haiku, limerick, quatrain
- **Educational tooltips**: Explain meter and form

### Poetic Forms
- **Sonnet**: Shakespearean (ABAB CDCD EFEF GG) and Petrarchan (ABBAABBA)
- **Haiku**: 5-7-5 syllable pattern detection
- **Limerick**: AABBA rhyme scheme with meter
- **Quatrain**: ABAB, ABBA, AABB patterns
- **Couplet**: AA rhyme scheme
- **Free verse**: No consistent pattern detection

## Usage Examples

```java
// Basic syllable counting
int syllables = PoetryUtils.countSyllables("beautiful"); // 3

// Rhyme detection
boolean rhymes = PoetryUtils.rhymes("night", "light"); // true
boolean nearRhymes = PoetryUtils.nearRhymes("heart", "part"); // true

// Meter analysis
MeterAnalysis analysis = new MeterScanner().analyze(poemText, false);
String form = analysis.detectedForm; // "Shakespearean Sonnet"

// Get rhymes for a word
List<String> rhymes = RhymeDatabase.getRhymesFor("love");
// Returns: ["above", "dove", "shove", "glove"]

// Get synonyms for poetic enhancement
List<String> synonyms = RhymeDatabase.getSynonymsFor("dark");
// Returns: ["dim", "shadowy", "murky", "gloomy", "dusky"]
```

## Performance

- **Real-time analysis**: <10ms for typical poems
- **Memory efficient**: Lazy loading of dictionary data
- **Scalable**: Handles poems of any length
- **Thread-safe**: All methods are synchronized where needed

## Integration

The poetry engine integrates with:
- **UI Components**: StatsSidebarPanel for real-time feedback
- **Editor**: RichTextStyler for visual meter highlighting
- **AI Assistant**: Sim for creative suggestions
- **Export**: PoemExporter for formatted output

## Data Sources

- **Simple English Dictionary**: 26 JSON files (a-z) with synonyms
- **Internal rhyme groups**: 50+ common poetic rhyme patterns
- **Syllable overrides**: 70+ irregular word pronunciations
- **Form patterns**: Predefined poetic form templates
