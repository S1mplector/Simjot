# Simjot

<p align="left">
  <img src="Simjot/docs/images/simjot_logo.png" alt="Simjot Logo" width="240">
</p>

A personalizable creative wellness and writing studio with a Aero-inspired UI and rich journaling and poetry tools.

## Features

### **Writing tools and mood tracking**

- Simjot supports journal entries with mood tracking, rich formatting, images, and customizable templates. 
- Additionally, Simjot provides a dedicated poetry editor with real-time syllable counting, rhyme and meter analysis, and form detection powered by native engines (C/C++ and Haskell)

- Mood is set per entry via a **Interactive mood slider** with visual feedback (0-100 scale)
- In the **Mood chart**, you can view your mood data with date range filtering (7 days, 30 days, all time)
- Mood data is stored in a custom binary format optimized for most optimal parsing. 

### **Organization & management**
- Notebook system with different types (Journal, Poetry)
- Smart auto-save functionality with timestamp-based filenames
- Global search and filtering across all content
- Entry templates with quick selection and customization

### **Security & Backup**
- **Password Protection**: Lock your journal with AES-256 encryption
- **Selective Backup**: Choose to include mood data, settings, wallpapers
- **Backup Verification**: Integrity checking and pruning by age
- **Easy Restore**: Browse and restore from any backup point

### **Personalization & UI**
- Aero-inspired UI with glass panels and smooth animations (can be disabled)
- Themes, backgrounds, and a custom font studio for creating or importing fonts
- Global hotkeys and quick actions for fast capture
- Optional Sim AI companion via local Ollama
- Native-accelerated search and analytics for large notebooks

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
- **CMake 3.20+** and a C/C++ compiler for the native library (recommended)
- **GHC/Cabal** for the Haskell poetry module (optional)
- **Ollama** for the Sim AI companion (optional)

### Installation & Build

#### Recommended (Native-Enabled)
```bash
# From the repository root
./compile-native.sh --release
./run.sh
```

#### Using Maven (Core Build)
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

**Option 1: Run Script (Recommended)**
```bash
./run.sh
```

**Option 2: Via Maven**
```bash
cd Simjot
mvn exec:java -Dexec.mainClass="main.ui.app.JournalApp"
```

**Option 3: JAR File**
```bash
cd Simjot
java -jar target/Simjot-*.jar
```

**Option 4: Native Executable (after packaging)**
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

### Architecture
- **Modular Java application** using Java Platform Module System
- **Swing-based UI** with custom Look & Feel
- **CardLayout navigation** for smooth panel transitions
- **MVC pattern** with observer-based UI updates
- **Event-driven design** with event bus for component communication
- **File-based persistence** with custom serialization
- **Panama FFM integration** for native modules (C/C++)
- **Optional Haskell FFI** for advanced poetry analysis

### Technologies Used
- **Java 24** with Project Jigsaw and Panama FFM (preview)
- **Java Swing** for cross-platform GUI
- **Java 2D Graphics** for drawing and image processing
- **AES-256 encryption** for security features
- **Native C/C++ libraries** for performance-critical operations
- **Optional Haskell module** for poetry analysis
- **Ollama** (optional) for Sim AI companion features

### File Formats
- **Journal entries**: `.note` files with metadata and mood data
- **Poems**: `.poem` files with title, content, and analysis metadata
- **Settings**: JSON configuration in user directory
- **Backups**: Compressed .sjbackup archives with selective content
- **Custom fonts**: `.sjf` files created in the Custom Font Studio

## Customization

### Themes & Appearance
- Multiple background options for different writing modes
- Customizable font sizes and custom fonts for journal entries and notes
- Adjustable mood tracking visualization

### Settings Options
- **General**: Default brush sizes, colors, auto-save intervals
- **Appearance**: Themes, animations, font scaling, custom fonts
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
SIMJOT_LOG=debug ./run.sh
```

---

I started developing Simjot when I was battling some extremely hard feelings and when I was in a bad state of mind. I needed a tool to help me express my thoughts and emotions, and Simjot was born. I hope it helps you too.

*Happy Journaling!*
