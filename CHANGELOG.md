# Simjot Changelog

All notable changes to Simjot will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive documentation and licensing notices for all third-party assets
- Streamline Ultimate Light icon set integration
- Enhanced poetry analysis engine with scansion and sound device detection
- Sim AI companion with proactive triggering and long-term memory
- Breathing exercise system with customizable patterns
- Advanced backup system with verification and selective includes
- Performance monitoring with RAM tracking and optimization modes

### Changed
- Updated build documentation to reflect pure Java Swing approach
- Improved resource organization and licensing compliance
- Enhanced UI component architecture with better modularity

### Fixed
- Resource loading optimizations
- Memory management improvements
- UI rendering performance enhancements

## [1.0.0] - 2024-12-29

### Added
- **Core Journaling Features**
  - Rich text journal entry editor with mood tracking
  - Multiple notebook types (Journal, Notetaking, Poetry)
  - Auto-save functionality with timestamp-based naming
  - Entry templates with customizable fields

- **Poetry Analysis Engine**
  - Real-time syllable counting
  - Rhyme scheme detection and suggestions
  - Meter analysis with foot-by-foot breakdown
  - Sound device detection (alliteration, assonance, consonance)
  - Form recognition (sonnet, haiku, etc.)
  - Comprehensive rhyme and synonym database

- **Sim AI Companion**
  - Local LLM integration (Ollama support)
  - Proactive context-aware triggering
  - Long-term memory with persistent storage
  - Personality system with customizable traits
  - Emotion-aware responses based on mood tracking

- **UI/UX Features**
  - Modern Aero theme with customizable colors
  - Multiple themes (Light, Sepia, custom backgrounds)
  - Smooth animations and transitions
  - Performance optimization modes (60/30 FPS)
  - Real-time RAM monitoring
  - Widget system (Pomodoro, breathing, sticky notes)

- **Security & Backup**
  - AES-256 encryption for journal content
  - Password protection with auto-lock
  - Comprehensive backup system with verification
  - Selective backup options (mood, settings, wallpapers)
  - Easy restore functionality

- **Productivity Features**
  - Global hotkey support for quick capture
  - Quick settings overlay
  - Tutorial system for new users
  - Search and filter capabilities
  - Export functionality (PDF, text formats)

- **Wellness Integration**
  - Interactive mood slider with visual feedback
  - Mood chart visualization with date filtering
  - Breathing exercise widget
  - Mindfulness reminders and prompts

### Technical Features
- **Architecture**
  - Java 17+ with JPMS (Java Platform Module System)
  - Pure Java Swing with custom Look & Feel
  - Event-driven design with event bus
  - Modular component architecture
  - File-based persistence with custom serialization

- **Performance**
  - Progressive image scaling for better performance
  - Memory-efficient caching systems
  - Configurable animation quality
  - Resource optimization for lower-spec systems

- **Dependencies**
  - Apache PDFBox for PDF export
  - JNativeHook for global hotkeys
  - Simple English Dictionary for linguistic analysis
  - Streamline Ultimate Light icon set

### Documentation
- Comprehensive build instructions
- Testing framework setup
- API documentation
- User guides and tutorials
- Licensing and attribution notices

## [0.9.0] - 2024-11-XX

### Added
- Initial poetry workspace
- Basic mood tracking
- Simple journal entry editor
- First iteration of Sim AI

## [0.8.0] - 2024-10-XX

### Added
- Basic journaling functionality
- Simple UI framework
- Initial backup system

## [0.1.0] - 2024-XX-XX

### Added
- Project inception
- Initial architecture setup
- Basic UI components

---

## Version History Summary

- **1.0.0** - Full-featured creative wellness application
- **0.9.x** - Poetry and AI features integration
- **0.8.x** - Core journaling functionality
- **0.1.x** - Project foundation

## Support and Documentation

For detailed information about features, setup, and usage, please see:
- `README.md` - Project overview and quick start
- `README_BUILD.md` - Build instructions
- `TESTING.md` - Testing guide
- `CONTRIBUTING.md` - Contribution guidelines
- `src/main/resources/DOCUMENTATION_INDEX.md` - Complete documentation index

---

**Note**: This changelog covers major features and changes. For detailed commit history, please refer to the Git repository.
