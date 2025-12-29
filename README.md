# Simjot

<p align="left">
  <img src="Simjot/docs/images/simjot_logo.png" alt="Simjot Logo" width="240">
</p>

A highly personalizable creative wellness application built with pure Java Swing, designed to help you capture your thoughts, express creativity, and track your well-being in an elegant environment.

## Features

### **Multi-Format Content Creation**
- **Journal Entries**: Traditional diary-style entries with mood tracking, rich formatting, and customizable templates
- **Poetry Writing**: Dedicated poetry editor with real-time syllable counting, rhyme scheme detection, meter analysis, and form recognition (sonnet, haiku, etc.)

### **Mood & Wellness Tracking**
- **Interactive mood slider** with visual feedback (0-100 scale)
- **Mood chart visualization** with date range filtering (7 days, 30 days, all time)
- **Automatic mood logging** integrated with journal entries
- **Visual mood trends** to track emotional patterns over time

### **Organization & Management**
- **Notebook system** with different types (Journal, Notetaking, Poetry)
- **File browser** with entry previews and word counts
- **Smart auto-save functionality** with timestamp-based filenames
- **Search and filter** capabilities across all content
- **Entry templates** with customizable fields and quick selection

### **Security & Backup**
- **Password Protection**: Lock your journal with AES-256 encryption
- **Auto-lock**: Configurable inactivity timeout
- **Comprehensive Backup System**: Automatic, scheduled, and manual backups
- **Selective Backup**: Choose to include mood data, settings, wallpapers
- **Backup Verification**: Integrity checking and pruning by age
- **Easy Restore**: Browse and restore from any backup point

### **User Experience**
- **Modern UI design** with smooth animations and transitions (can be disabled)
- **Multiple themes**: Aero, Light, Sepia, and custom backgrounds
- **Quick Settings Overlay**: Rapid access to common settings
- **Intuitive navigation** with card-based interface
- **Tutorial system** for new users
- **Sound effects** and visual feedback
- **RAM monitoring** and performance metrics
- **Global hotkeys** for quick capture

## Screenshots

Below are a few highlights from the current UI. More images live in `Simjot/Simjot/docs/images`.

- **Main Interface**

  ![Main Interface](Simjot/docs/images/main_interface.png)

- **Journaling Interface**

  ![Journaling Interface](Simjot/docs/images/journaling%20interface.png)

- **Poetry Workspace**

  ![Poetry Workspace](Simjot/docs/images/poem_interface.png)

- **Notebook Manager**

  ![Notebook Manager](Simjot/docs/images/notebook_manager.png)

- **Entry Manager**

  ![Entry Manager](Simjot/docs/images/entry_manager.png)

- **Settings**

  ![Settings](Simjot/docs/images/settings_interface.png)

- **Breathing Exercise**

  ![Breathing Circle](Simjot/docs/images/breathing_circle.png)
  
  ![Breathing Configuration](Simjot/docs/images/breathing_config.png)

## Quick Start

### Prerequisites
- **Java 17 or higher** installed on your system
- **JDK 17 or higher** for building the project
- **Maven** (optional) for dependency management
- **Ollama** (optional) for Sim AI companion features

### Installation & Build

#### Using Maven (Recommended)
```bash
cd Simjot
mvn clean package
```

#### Manual Build
See [README_BUILD.md](README_BUILD.md) for detailed build instructions including:
- Windows packaging with `jpackage`
- macOS/Linux builds
- IDE setup

### Running the Application

**Option 1: Via Maven**
```bash
cd Simjot
mvn exec:java -Dexec.mainClass="main.ui.app.JournalApp"
```

**Option 2: JAR File**
```bash
java -jar Simjot.jar
```

**Option 3: Native Executable (after packaging)**
- Windows: `dist/Simjot/Simjot.exe`
- macOS: `build/macos-installer/Simjot.app`

## Usage Guide

### First Launch
On first startup, Simjot will prompt you to:
1. **Choose a journal folder** where all your content will be stored
2. **Take an optional tutorial** to learn the interface
3. **Set up your preferences** in the settings panel

### Creating Content

#### **Journal Entries**
1. Click "New Entry" from the main menu
2. Select your mood using the interactive slider
3. Write your thoughts in the rich text editor
4. Entries are automatically saved with timestamps

#### **Poetry Writing**
1. Select "New Poem" for the dedicated poetry interface
2. Choose from multiple fonts (Serif, Georgia, Verdana, Cursive)
3. Use the "Inspire Me" button for creative prompts
4. Track stanza count in real-time

#### **Notetaking**
1. Create or select a Notetaking notebook
2. Use the streamlined editor for quick notes

### Organization

#### **Notebooks**
- Create separate notebooks for different topics or time periods
- Choose notebook types: Journal, Notetaking, or Poetry
- Each notebook maintains its own file structure

#### **Viewing Content**
- Use "View Entries" to browse all your created content
- Filter by type (entries, poems, notes)
- Preview content before opening
- See word counts and creation dates

## Project Structure

```
Simjot/
├── Simjot/                     # Main application module
│   ├── src/main/
│   │   ├── core/               # Domain models and business logic
│   │   │   ├── analytics/      # Usage analytics
│   │   │   ├── export/         # Export functionality
│   │   │   ├── poetry/         # Poetry analysis engine
│   │   │   ├── security/       # Encryption and locking
│   │   │   ├── service/        # Core services
│   │   │   ├── sim/            # Sim AI companion system
│   │   │   │   ├── api/        # Ollama API integration
│   │   │   │   ├── engine/     # AI reasoning engine
│   │   │   │   ├── llm/        # LLM prompt engineering
│   │   │   │   ├── memory/     # Long-term memory store
│   │   │   │   └── proactive/  # Proactive triggering
│   │   │   └── spelling/       # Spell checking
│   │   ├── infrastructure/     # System services
│   │   │   ├── backup/         # Backup management
│   │   │   ├── hotkeys/        # Global hotkey support
│   │   │   ├── io/             # File I/O utilities
│   │   │   └── monitoring/     # Performance monitoring
│   │   ├── resources/          # Images, audio, dictionaries
│   │   └── ui/                 # User interface
│   │       ├── animations/     # Animation utilities
│   │       ├── components/     # Reusable UI components
│   │       ├── dialog/         # Dialog windows
│   │       ├── features/       # Feature modules
│   │       │   ├── drawing/    # Drawing canvas
│   │       │   ├── entries/    # Entry management
│   │       │   ├── home/       # Main menu and dashboard
│   │       │   ├── poetry/     # Poetry editor
│   │       │   ├── settings/   # Settings pages
│   │       │   └── widgets/    # Productivity widgets
│   │       ├── sim/            # Sim chat interface
│   │       └── theme/          # Theming system
│   ├── tests/                  # Unit tests (JUnit 5)
│   ├── docs/                   # Documentation and screenshots
│   └── pom.xml                 # Maven configuration
├── scripts/                    # Build and test scripts
├── build/                      # Compiled output
├── README.md                   # This file
├── README_BUILD.md             # Build instructions
├── TESTING.md                  # Testing guide
└── LICENSE.md                  # License
```

## Technical Details

### Architecture
- **Modular Java application** using Java Platform Module System
- **Swing-based UI** with custom Look & Feel
- **CardLayout navigation** for smooth panel transitions
- **MVC pattern** with observer-based UI updates
- **Event-driven design** with event bus for component communication
- **File-based persistence** with custom serialization

### Technologies Used
- **Java 17+** with Project Jigsaw (modular system)
- **Java Swing** for cross-platform GUI
- **Java 2D Graphics** for drawing and image processing
- **Ollama API** for local LLM integration (Sim AI)
- **AES-256 encryption** for security features
- **Built-in audio support** for sound effects

### File Formats
- **Journal entries**: `.note` files with metadata and mood data
- **Poems**: `.poem` files with title, content, and analysis metadata
- **Settings**: JSON configuration in user directory
- **Backups**: Compressed archives with selective content
- **Sim Memory**: Encrypted memory store for AI context

## Customization

### Themes & Appearance
- Multiple background options for different writing modes
- Customizable font sizes for journal entries and notes
- Adjustable mood tracking visualization
- Personalized color schemes for drawing tools

### Settings Options
- **General**: Default brush sizes, colors, auto-save intervals
- **Appearance**: Themes, animations, font scaling
- **Drawing**: Brush presets, pressure sensitivity
- **Security**: Password, auto-lock, encryption
- **Storage**: Backup schedules, destinations, selective includes
- **Sim**: AI personality, proactive mode, memory management

## Testing

Run the test suite:
```bash
bash scripts/run_tests.sh
```

See [TESTING.md](TESTING.md) for detailed testing information.

## License

Simjot is released under a **Source-Available Personal Use License**. You may:
- View and study the source code
- Use the software for personal purposes
- Create private forks for personal modification

You may **not**:
- Distribute the original or modified software
- Use for commercial purposes
- Sublicense the software

See [LICENSE.md](LICENSE.md) for full terms.

## Troubleshooting

### Common Issues
- **Application won't start**: Ensure Java 17+ is installed and in your PATH
- **File not found errors**: Check that the journal folder path is accessible
- **Backup failures**: Verify write permissions to backup destination

### Debug Mode
Enable debug logging by setting environment variable:
```bash
SIMJOT_LOG=debug java -jar Simjot.jar
```

---

I started developing Simjot when I was battling some extremely hard feelings and when I was in a bad state of mind. I needed a tool to help me express my thoughts and emotions, and Simjot was born. I hope it helps you too.

*Happy Journaling!*
