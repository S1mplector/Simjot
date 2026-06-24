# Infrastructure

The infrastructure layer provides core services, utilities, and system integration for Simjot, including backup management, file I/O, monitoring, and security features.

## Core Services

### Backup System
- **BackupManager**: Comprehensive backup orchestration
- **BackupService**: Scheduled backup operations
- **EntryHistoryManager**: Revision history tracking
- **NotebookInfo**: Metadata management

### File I/O & Storage
- **AppDirectories**: Application directory management
- **ResourceLoader**: Resource loading utilities
- **DateFormatUtil**: Date formatting and parsing
- **FileWatcher**: File system monitoring

### Monitoring & Performance
- **RamMonitor**: Memory usage tracking
- **PerformanceMonitor**: System performance metrics
- **ResourceTracker**: Resource usage analytics

### Security
- **LockController**: Application security management
- **LockUtil**: Encryption and authentication utilities
- **SecurityManager**: Access control and permissions

## Backup Architecture

### Backup Features
- **Automatic backups**: Configurable schedule and triggers
- **Incremental backups**: Only changed data
- **Compression**: Space-efficient storage
- **Encryption**: Secure backup protection
- **Verification**: Backup integrity checking
- **Restore workflow**: Easy recovery process

### Backup Configuration
```java
// Backup settings
BackupManager.setDestination("/path/to/backups");
BackupManager.setAlwaysBackupOnExit(true);
BackupManager.setIncludeMood(true);
BackupManager.setIncludeSettings(true);
BackupManager.setIncludeWallpapers(false);
BackupManager.setVerificationEnabled(true);
BackupManager.setPruneByAge(30); // days
```

### Backup Types
- **Full backup**: Complete application state
- **Incremental backup**: Changes since last backup
- **Selective backup**: Specific data types
- **Manual backup**: User-initiated backups

### Restore System
- **Backup selection**: Browse available backups
- **Version comparison**: Preview changes
- **Selective restore**: Choose data to restore
- **Conflict resolution**: Handle data conflicts

## Directory Management

### Application Directories
```java
// Standard directory structure
AppDirectories.getHome()           // Application home
AppDirectories.getData()           // User data directory
AppDirectories.getConfig()        // Configuration files
AppDirectories.getCache()         // Cache directory
AppDirectories.getTemp()          // Temporary files
AppDirectories.getBackups()       // Backup storage
```

### Directory Features
- **Cross-platform** path handling
- **Automatic creation** of required directories
- **Permission management** for security
- **Migration support** for directory changes

## File I/O Operations

### Resource Loading
- **Classpath resources**: JAR-embedded files
- **External resources**: File system resources
- **URL resources**: Network-based resources
- **Cached loading**: Performance optimization

### File Operations
- **Atomic writes**: Prevent data corruption
- **File locking**: Concurrent access control
- **Encoding handling**: UTF-8 and character sets
- **Error recovery**: Graceful failure handling

## Monitoring System

### Memory Monitoring
```java
// RAM monitoring
RamMonitor monitor = new RamMonitor();
long usedMemory = monitor.getUsedMemory();
long totalMemory = monitor.getTotalMemory();
double usagePercent = monitor.getUsagePercentage();
```

### Performance Metrics
- **CPU usage**: Processor utilization
- **Memory usage**: RAM consumption tracking
- **Disk usage**: Storage space monitoring
- **Network activity**: Connection monitoring

### Alert System
- **Threshold alerts**: Resource usage warnings
- **Performance degradation**: System slowdown detection
- **Resource exhaustion**: Critical shortage alerts
- **Health checks**: System status validation

## Security Architecture

### Application Locking
- **Password protection**: User authentication
- **Encryption**: AES-256 data protection
- **Session management**: Login state tracking
- **Auto-lock**: Inactivity-based locking

### Security Features
```java
// Security configuration
LockController.setPassword("userPassword");
LockController.setAutoLockMinutes(15);
LockController.setEncryptionEnabled(true);
LockController.setSessionTimeout(Duration.ofHours(8));
```

### Data Protection
- **Encryption at rest**: File system protection
- **Encryption in transit**: Network security
- **Key management**: Secure key storage
- **Access control**: User permission management

## Hotkey System

### Global Hotkeys
- **GlobalHotkeyManager**: System-wide hotkey registration
- **Hotkey registration**: Custom key combinations
- **Platform integration**: Native hotkey APIs
- **Conflict resolution**: Handle hotkey conflicts

### Hotkey Features
- **Global capture**: System-wide key listening
- **Application focus**: Context-aware hotkeys
- **Custom actions**: User-defined hotkey behaviors
- **Hotkey persistence**: Remember user preferences

## Utilities

### Date and Time
- **DateFormatUtil**: Consistent date formatting
- **Timezone handling**: Multi-timezone support
- **Relative dates**: Human-readable time formats
- **Calendar integration**: System calendar interaction

### System Integration
- **OS detection**: Platform-specific behavior
- **Native APIs**: System library integration
- **Environment variables**: System configuration
- **Process management**: External process control

## Performance Optimization

### Caching Strategies
- **Resource caching**: Frequently used resources
- **Result caching**: Computed result storage
- **File caching**: I/O operation optimization
- **Memory caching**: In-memory data storage

### Resource Management
- **Connection pooling**: Database/network connections
- **Thread pooling**: Concurrent task execution
- **Memory pooling**: Object reuse patterns
- **Resource cleanup**: Automatic disposal

## Configuration

### Infrastructure Settings
```java
// System configuration
AppDirectories.setBasePath("/custom/path");
BackupManager.setEnabled(true);
RamMonitor.setAlertThreshold(0.8);
LockController.setAutoLockEnabled(true);
GlobalHotkeyManager.setEnabled(true);
```

### Environment Variables
- **SIMJOT_HOME**: Custom application directory
- **SIMJOT_CACHE**: Cache directory override
- **SIMJOT_BACKUP**: Backup directory override
- **SIMJOT_LOG**: Log file location

## Error Handling

### Recovery Strategies
- **Graceful degradation**: Reduced functionality on errors
- **Fallback mechanisms**: Alternative approaches
- **Data recovery**: Corrupted data repair
- **User notification**: Error communication

### Logging
- **Structured logging**: Consistent log format
- **Log levels**: Debug, info, warning, error
- **Log rotation**: File size management
- **Performance logging**: Operation timing
