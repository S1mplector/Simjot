# API Documentation

## Overview

This document provides API documentation for Simjot's core components and services. Simjot is built with a modular architecture that allows for extensibility and integration.

## Core Services

### SettingsStore

The main configuration management service for Simjot.

```java
// Get the singleton instance
SettingsStore settings = SettingsStore.get();

// Common settings operations
settings.setTheme("Aero");
settings.setJournalFontSize(16);
settings.setAutosaveDelayMs(5000);
settings.save();

// Reading settings
String theme = settings.getTheme();
int fontSize = settings.getJournalFontSize();
boolean animationsEnabled = settings.isMainMenuAnimationsEnabled();
```

#### Key Methods
- `get()` - Get singleton instance
- `save()` - Persist settings to disk
- `setTheme(String)` - Set UI theme
- `setJournalFontSize(int)` - Set journal font size
- `setAutosaveDelayMs(long)` - Set auto-save interval
- `isWidgetPanelVisible()` - Check widget panel visibility

### NotebookStore

Manages notebook creation, access, and organization.

```java
// Create a new notebook
NotebookStore store = NotebookStore.get();
Notebook journal = store.createNotebook("My Journal", NotebookType.JOURNAL);

// Access existing notebooks
List<Notebook> notebooks = store.getAllNotebooks();
Notebook current = store.getCurrentNotebook();

// Notebook operations
store.setCurrentNotebook(journal);
store.deleteNotebook(journal.getId());
```

#### Key Methods
- `createNotebook(String, NotebookType)` - Create new notebook
- `getAllNotebooks()` - Get all notebooks
- `getCurrentNotebook()` - Get active notebook
- `setCurrentNotebook(Notebook)` - Set active notebook
- `deleteNotebook(String)` - Remove notebook

## Poetry Analysis Engine

### ScansionEngine

Provides advanced poetry analysis including meter detection and stress patterns.

```java
// Analyze poem meter
ScansionEngine engine = new ScansionEngine();
String poem = "Shall I compare thee to a summer's day?";
MeterAnalysis analysis = engine.analyze(poem);

// Get meter information
String detectedForm = analysis.getDetectedForm();
List<int[]> stressPatterns = analysis.getStressByTextLine();
```

#### Key Methods
- `analyze(String)` - Analyze poem text
- `getDetectedForm()` - Get detected poetic form
- `getStressByTextLine()` - Get stress patterns by line
- `getSyllablesByTextLine()` - Get syllable counts

### RhymeDatabase

Comprehensive rhyme and synonym database for poetry assistance.

```java
// Get rhymes for a word
List<String> rhymes = RhymeDatabase.getRhymesFor("day");

// Get synonyms
List<String> synonyms = RhymeDatabase.getSynonymsFor("happy");

// Check if words rhyme
boolean rhymes = RhymeDatabase.doWordsRhyme("day", "say");
```

#### Key Methods
- `getRhymesFor(String)` - Get rhyming words
- `getSynonymsFor(String)` - Get synonyms
- `doWordsRhyme(String, String)` - Check rhyme relationship

### SoundDevicesEngine

Detects literary devices like alliteration, assonance, and consonance.

```java
// Analyze sound devices
SoundDevicesEngine engine = new SoundDevicesEngine();
SoundAnalysis analysis = engine.analyze("The silken, sad, uncertain rustling");

// Get detected devices
List<Alliteration> alliterations = analysis.getAlliterations();
List<Assonance> assonances = analysis.getAssonances();
```

#### Key Methods
- `analyze(String)` - Analyze text for sound devices
- `getAlliterations()` - Get alliteration instances
- `getAssonances()` - Get assonance instances
- `getConsonance()` - Get consonance instances

## Sim AI System

### SimBrain

Core AI companion engine with proactive triggering and memory management.

```java
// Initialize Sim AI
SimSettings settings = new SimSettings();
SimPersonality personality = new SimPersonality(SimPersonality.Type.GENTLE);
SimDataGateway data = new SimDataGateway();
SimBrain sim = new SimBrain(settings, personality, data);

// Trigger AI interaction
SimEventBus.get().triggerEvent(SimEventBus.EventType.USER_TYPED, "Hello Sim");
```

#### Key Methods
- `triggerEvent(EventType, String)` - Trigger AI events
- `setLlmEnabled(boolean)` - Enable/disable LLM
- `setPersonality(Type)` - Set AI personality

### MemoryStore

Manages AI companion's short-term and long-term memory.

```java
// Store memory
MemoryStore memory = new MemoryStore();
memory.store("user_preference", "likes_poetry");

// Retrieve memory
String preference = memory.retrieve("user_preference");

// Persistent memory
PersistentMemoryStore persistent = new PersistentMemoryStore();
persistent.saveLongTermMemory("conversation_history", data);
```

#### Key Methods
- `store(String, Object)` - Store in memory
- `retrieve(String)` - Retrieve from memory
- `clear()` - Clear memory store

## UI Components

### Modern Components

Simjot provides custom Swing components for modern UI design.

```java
// Modern button
RoundedButton button = new RoundedButton("Click me");
button.setBackground(Color.BLUE);
button.addActionListener(e -> handleAction());

// Modern spinner
ModernSpinner spinner = new ModernSpinner();
spinner.setValue(10);
spinner.addChangeListener(e -> handleValueChange());

// Frosted glass panel
FrostedGlassPanel panel = new FrostedGlassPanel();
panel.setBlurRadius(10);
panel.setBackground(new Color(255, 255, 255, 100));
```

### Theme System

Customizable theming system with multiple built-in themes.

```java
// Apply theme
AeroTheme.apply();
LightTheme.apply();
SepiaTheme.apply();

// Custom colors
Color accent = SettingsStore.get().getAccentColor();
AeroTheme.setAccentColor(accent);
```

## Event System

### SimEventBus

Central event system for component communication.

```java
// Register listener
SimEventBus.get().addListener(new SimEventBus.Listener() {
    @Override
    public void onEvent(SimEventBus.EventType type, String data) {
        if (type == SimEventBus.EventType.USER_TYPED) {
            handleUserInput(data);
        }
    }
});

// Trigger events
SimEventBus.get().triggerEvent(SimEventBus.EventType.MOOD_CHANGED, "75");
SimEventBus.get().triggerEvent(SimEventBus.EventType.ENTRY_SAVED, "entry_id");
```

#### Event Types
- `USER_TYPED` - User typing in editor
- `MOOD_CHANGED` - Mood slider changed
- `ENTRY_SAVED` - Journal entry saved
- `NOTEBOOK_CHANGED` - Active notebook changed

## Backup System

### BackupService

Comprehensive backup system with encryption and verification.

```java
// Create backup
BackupService backup = new BackupService();
BackupConfig config = new BackupConfig()
    .setIncludeMoodData(true)
    .setIncludeSettings(true)
    .setDestination("/path/to/backups");

BackupResult result = backup.createBackup(config);

// Restore from backup
RestoreResult restore = backup.restoreFrom(backupFile);
```

#### Key Methods
- `createBackup(BackupConfig)` - Create backup
- `restoreFrom(File)` - Restore from backup
- `verifyBackup(File)` - Verify backup integrity

## Security Features

### LockController

Application security and encryption management.

```java
// Enable password protection
LockController lock = new LockController();
lock.setPassword("secure_password");
lock.setAutoLockMinutes(5);

// Lock/unlock application
lock.lockApplication();
boolean unlocked = lock.unlockApplication("password");
```

#### Key Methods
- `setPassword(String)` - Set password
- `lockApplication()` - Lock application
- `unlockApplication(String)` - Unlock with password
- `setAutoLockMinutes(int)` - Set auto-lock timeout

## Utility Classes

### ResourceLoader

Resource loading and management utilities.

```java
// Load images
Image icon = ResourceLoader.createImage("img/icons/save.png");
BufferedImage scaled = ResourceLoader.loadAndScale("img/background.jpg", 800, 600);

// Load text resources
String quotes = ResourceLoader.loadText("quotes/quotes.json");
String dictionary = ResourceLoader.loadText("simple-english-dictionary/data.json");
```

### FileIO

File input/output operations with error handling.

```java
// Read/write files
String content = FileIO.readFile(path);
FileIO.writeFile(path, content);

// Backup operations
FileIO.backupFile(original, backup);
boolean exists = FileIO.fileExists(path);
```

## Integration Examples

### Custom Poetry Plugin

```java
public class CustomPoetryPlugin {
    private final ScansionEngine scansion;
    private final RhymeDatabase rhymes;
    
    public CustomPoetryPlugin() {
        this.scansion = new ScansionEngine();
        this.rhymes = new RhymeDatabase();
    }
    
    public PoetryAnalysis analyzePoem(String poem) {
        MeterAnalysis meter = scansion.analyze(poem);
        List<String> suggestions = generateRhymeSuggestions(poem);
        return new PoetryAnalysis(meter, suggestions);
    }
}
```

### Custom Widget

```java
public class CustomWidget implements Widget {
    private JPanel panel;
    
    @Override
    public JComponent getComponent() {
        return panel;
    }
    
    @Override
    public String getName() {
        return "Custom Widget";
    }
    
    @Override
    public void initialize() {
        // Initialize widget
    }
}
```

## Error Handling

Most API methods throw appropriate exceptions:

- `IOException` - File operations
- `SecurityException` - Security-related operations
- `IllegalArgumentException` - Invalid parameters
- `IllegalStateException` - Invalid state

Always handle exceptions appropriately in your code.

---

**Last Updated**: December 2024  
**API Version**: 1.0.0  
**Compatibility**: Java 17+
