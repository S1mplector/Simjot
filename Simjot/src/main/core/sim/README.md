# Sim AI Companion

The Sim AI system provides intelligent poetry assistance through a local Deepseek model via Ollama integration.

## Architecture Overview

### Core Components

#### SimBrain
- **Central reasoning engine** for poetry analysis and generation
- **Context management** with memory integration
- **Proactive triggering** based on user state
- **Multi-modal interaction** (text, mood, timing)

#### SimScheduler
- **Background task management** for AI operations
- **Priority-based execution** with resource optimization
- **Asynchronous processing** for responsive UI
- **Error handling** and retry mechanisms

#### MemoryStore
- **Long-term memory** for user preferences and patterns
- **Context retrieval** for personalized assistance
- **Persistent storage** with encryption
- **Memory consolidation** and pruning

#### UserState
- **Real-time user monitoring** (mood, activity, timing)
- **Behavioral pattern detection**
- **Context inference** for proactive assistance
- **Privacy-aware** data collection

### AI Integration

#### Local Model Support
- **Deepseek model** via Ollama integration
- **Offline processing** for privacy
- **Configurable model parameters**
- **Resource usage monitoring**

#### Prompt Engineering
- **CriticPromptDecorator**: Constructive feedback generation
- **NudgePrefaceBuilder**: Contextual suggestion framing
- **ReasoningHeuristics**: Poetry-specific logic
- **Persona management**: Consistent AI personality

## Features

### Proactive Assistance
- **Mood-based suggestions**: Tailored to emotional state
- **Timing optimization**: Intervenes at appropriate moments
- **Context awareness**: Understands current writing task
- **Learning adaptation**: Improves from user feedback

### Poetry Analysis
- **Form detection**: Identifies poetic structures
- **Meter analysis**: Suggests rhythmic improvements
- **Rhyme assistance**: Offers word alternatives
- **Style guidance**: Provides poetic techniques

### Creative Collaboration
- **Line completion**: Maintains user's voice
- **Imagery enhancement**: Suggests vivid descriptions
- **Metaphor generation**: Creative figurative language
- **Theme development**: Explores poetic concepts

## Configuration

### SimSettings
```java
// Enable/disable features
sim.setProactiveMode(true);
setMoodSensitivity(0.7);
setResponseDelay(Duration.ofSeconds(5));

// Model configuration
setModelTemperature(0.8);
setMaxTokens(150);
setContextWindow(2048);
```

### Personality Customization
```java
// AI persona traits
SimPersonality persona = new SimPersonality()
    .setCreativity(0.8)
    .setFormality(0.6)
    .setEncouragement(0.9)
    .setTechnicalDepth(0.7);
```

## Privacy & Security

### Data Protection
- **Local processing**: No cloud data transmission
- **Encrypted storage**: Memory data is encrypted at rest
- **User control**: Granular permission settings
- **Data retention**: Configurable retention policies

### Memory Management
- **Automatic pruning**: Removes outdated memories
- **Consolidation**: Merges related memories
- **Export/Import**: User data portability
- **Wipe capability**: Complete data deletion

## Performance

### Resource Usage
- **Memory**: ~200MB base + model size
- **CPU**: Minimal during idle, moderate during generation
- **Disk**: ~1GB for model + memories
- **Network**: None (fully offline)

### Optimization
- **Lazy loading**: Model loads on first use
- **Batch processing**: Groups related operations
- **Caching**: Repeated query optimization
- **Throttling**: Prevents resource exhaustion

## Integration Points

### UI Integration
- **ChatViewPanel**: Real-time conversation interface
- **QuickMoodWidget**: Mood input and visualization
- **StatsSidebarPanel**: Poetry analysis integration
- **Settings UI**: Configuration management

### Core Integration
- **Poetry engine**: Form and meter analysis
- **NotebookStore**: Entry context and history
- **SettingsStore**: User preferences
- **BackupManager**: Memory backup/restore

## Development

### Extending Sim
- **Custom prompts**: Add new interaction patterns
- **New triggers**: Implement proactive behaviors
- **Memory types**: Extend storage schemas
- **Persona traits**: Define personality dimensions

### Testing
- **Unit tests**: Component isolation
- **Integration tests**: End-to-end workflows
- **Performance tests**: Resource usage validation
- **User testing**: Experience validation

## Troubleshooting

### Common Issues
- **Model not loading**: Check Ollama installation
- **Slow responses**: Verify system resources
- **Memory corruption**: Use backup restore
- **Privacy concerns**: Review data settings

### Debug Tools
- **SimEventBus**: Event monitoring
- **TriggerStatsStore**: Performance metrics
- **RecencyBuffer**: Memory inspection
- **UserState**: Current context viewer
