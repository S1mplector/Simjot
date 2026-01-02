# Simjot

<p align="left">
  <img src="Simjot/docs/images/simjot_logo.png" alt="Simjot Logo" width="240">
</p>

A highly personalizable and lightweight creative wellness application designed to help you capture your thoughts, express creativity, and track your well-being in an elegant environment.

## Features

### **Multi-Format Content Creation**
- Traditional diary-style entries with mood tracking, rich formatting, and customizable templates
- Dedicated poetry editor with real-time syllable counting, rhyme scheme detection, meter analysis, and form recognition using Haskell poetry analysis module

### **Mood & Wellness Tracking**
- **Interactive mood slider** with visual feedback (0-100 scale)
- **Mood chart** with date range filtering (7 days, 30 days, all time)
- **Automatic mood logging** integrated with journal entries

### **Organization & Management**
- Simjot features a **notebook system** with different types (Journal, Poetry)
- All editors include a **smart auto-save functionality** with timestamp-based filenames
- Additionally, Simjot has global **search and filtering** capabilities across all content
- **Entry templates** with customizable fields and quick selection

### **Security & Backup**
- **Password Protection**: Lock your journal with AES-256 encryption
- **Selective Backup**: Choose to include mood data, settings, wallpapers
- **Backup Verification**: Integrity checking and pruning by age
- **Easy Restore**: Browse and restore from any backup point

### **User Experience**
- **Modern UI design** with smooth animations and transitions (can be disabled)
- **Intuitive navigation** with card-based interface
- **Tutorial system** for new users
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

## Quick Start

### Prerequisites
- **Java 24 or higher** installed on your system
- **JDK 24 or higher** for building the project
- **Maven 3.8+** for dependency management and building
- **GHC/Cabal** for Haskell poetry analysis module
- **CMake** for native C/C++ library

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
2. Track stanza count in real-time
3. Use the metering and analysis utilities to improve your poetry

### Organization

#### **Notebooks**
- Create separate notebooks for different topics or time periods
- Choose notebook types: Journal or Poetry
- Each notebook maintains its own file structure

#### **Viewing Content**
- Use "View Entries" to browse all your created content
- Filter by type (entries, poems)
- Preview content before opening
- See word counts and creation dates

## Project Structure

```
Simjot/
├── Simjot/                     # Main application module
│   ├── src/main/
│   │   ├── core/               # Domain models and business logic
│   │   │   ├── analytics/      # Usage analytics
│   │   │   ├── export/         # Export functionality (PDF support)
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
│   │   ├── haskell/            # Haskell poetry analysis module
│   │   │   ├── src/            # Haskell source files
│   │   │   └── simjot-poetry.cabal
│   │   ├── infrastructure/     # System services
│   │   │   ├── backup/         # Backup management
│   │   │   ├── ffi/            # Foreign function interface
│   │   │   ├── hotkeys/        # Global hotkey support
│   │   │   ├── io/             # File I/O utilities
│   │   │   ├── monitoring/     # Performance monitoring
│   │   │   └── native/         # Native library bindings
│   │   ├── native/             # C/C++ native performance library
│   │   │   ├── src/            # Native source (analytics, compression, etc.)
│   │   │   ├── include/        # Header files
│   │   │   └── CMakeLists.txt  # CMake build configuration
│   │   ├── resources/          # Images, audio, dictionaries
│   │   └── ui/                 # User interface
│   │       ├── animations/     # Animation utilities
│   │       ├── components/     # Reusable UI components
│   │       ├── dialog/         # Dialog windows
│   │       ├── features/       # Feature modules
│   │       │   ├── drawing/    # Drawing canvas
│   │       │   ├── editing/    # Rich text editing
│   │       │   ├── entries/    # Entry management
│   │       │   ├── gallery/    # Image gallery
│   │       │   ├── home/       # Main menu and dashboard
│   │       │   ├── notebooks/  # Notebook management
│   │       │   ├── poetry/     # Poetry editor
│   │       │   ├── quicksettings/ # Quick settings overlay
│   │       │   ├── settings/   # Settings pages
│   │       │   ├── splash/     # Splash screen
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
└── LICENSE.md                  # MIT License
```

### Architecture
- **Modular Java application** using Java Platform Module System
- **Swing-based UI** with custom Look & Feel
- **CardLayout navigation** for smooth panel transitions
- **MVC pattern** with observer-based UI updates
- **Event-driven design** with event bus for component communication
- **File-based persistence** with custom serialization

### Technologies Used
- **Java 24** with Project Jigsaw (modular system)
- **Java Swing** for cross-platform GUI
- **Java 2D Graphics** for drawing and image processing
- **AES-256 encryption** for security features
- **Native C/C++ library** for performance-critical operations
- **Haskell FFI** for poetry analysis

### File Formats
- **Journal entries**: `.note` files with metadata and mood data
- **Poems**: `.poem` files with title, content, and analysis metadata
- **Settings**: JSON configuration in user directory
- **Backups**: Compressed .sjbackup archives with selective content

## Customization

### Themes & Appearance
- Multiple background options for different writing modes
- Customizable font sizes for journal entries and notes
- Adjustable mood tracking visualization

### Settings Options
- **General**: Default brush sizes, colors, auto-save intervals
- **Appearance**: Themes, animations, font scaling
- **Security**: Password, auto-lock, encryption
- **Storage**: Backup schedules, destinations, selective includes

## License

Simjot is released under the **MIT License**. You may:
- Use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
- Use for personal or commercial purposes
- Create derivative works

**Note**: Icon assets are under a separate license requiring attribution to the artist Lightning ([@LightningTheAn1](https://x.com/LightningTheAn1)).

See [LICENSE.md](LICENSE.md) for full terms.

## Troubleshooting

### Common Issues
- **Application won't start**: Ensure Java 24+ is installed and in your PATH
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
