# Simjot – Build Guide

This document covers building Simjot from source on all platforms.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 17+ | Ensure `java`, `javac`, and `jpackage` are on your `PATH` |
| Maven | 3.8+ | Optional but recommended |

## Build Methods

### 1. Maven Build (Recommended)

The simplest way to build Simjot:

```bash
cd Simjot
mvn clean package
```

This produces:
- `target/Simjot-1.0.0.jar` – executable JAR with dependencies
- `target/classes/` – compiled classes

Run with:
```bash
java -jar target/Simjot-1.0.0.jar
```

### 2. IDE Build

1. Import the `Simjot/` directory as a Maven project
2. Set the main class to `main.ui.app.JournalApp`
3. Build and run from your IDE

Supported IDEs:
- IntelliJ IDEA
- Eclipse
- VS Code with Java extensions
- NetBeans

### 3. Manual Build

For environments without Maven:

```bash
# Compile sources (excluding module-info.java for classpath mode)
find Simjot/src/main -name "*.java" ! -name "module-info.java" > sources.txt
javac -encoding UTF-8 -d build/classes @sources.txt

# Create JAR
jar --create --file Simjot.jar --main-class main.ui.app.JournalApp -C build/classes .

# Copy resources
cp -r Simjot/src/main/resources/* build/classes/
```

## Native Packaging with jpackage (recommended)

### Windows

Using `jpackage` to create a native Windows executable:

```cmd
jpackage --type app-image ^
  --name Simjot ^
  --input target ^
  --main-jar Simjot-1.0.0.jar ^
  --main-class main.ui.app.JournalApp ^
  --dest dist ^
  --icon Simjot/src/main/resources/images/simjot_icon.ico
```

Output: `dist/Simjot/Simjot.exe`

### macOS

```bash
jpackage --type app-image \
  --name Simjot \
  --input target \
  --main-jar Simjot-1.0.0.jar \
  --main-class main.ui.app.JournalApp \
  --dest dist \
  --icon Simjot/src/main/resources/images/simjot_icon.icns
```

Output: `dist/Simjot.app`

### Linux

```bash
jpackage --type app-image \
  --name Simjot \
  --input target \
  --main-jar Simjot-1.0.0.jar \
  --main-class main.ui.app.JournalApp \
  --dest dist \
  --icon Simjot/src/main/resources/images/simjot_icon.png
```

Output: `dist/Simjot/bin/Simjot`

## Running the Application

### From JAR
```bash
java -jar Simjot.jar
```

### With Custom Memory
```bash
java -Xmx2g -jar Simjot.jar
```

### With Debug Logging
```bash
SIMJOT_LOG=debug java -jar Simjot.jar
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `javac` not found | Ensure JDK 17+ `bin` is on your `PATH` |
| `jpackage` not found | Included with JDK 14+; verify installation |
| Missing resources | Ensure resources are copied to `build/classes/` |
| Maven build fails | Run `mvn clean` then retry |

## Project Dependencies

Core dependencies (managed by Maven):
- **JUnit 5**: Testing framework
- No external runtime dependencies, Simjot is a pure Java Swing application
