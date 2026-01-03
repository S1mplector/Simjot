# Simjot Build Guide

This document covers building Simjot from source on all platforms.

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 24+ | Ensure `java`, `javac`, and `jpackage` are on your `PATH` |
| Maven | 3.8+ | Required for building |
| CMake | 3.20+ | Required for native C/C++ library |
| C/C++ compiler | Clang/GCC/MSVC | Required for native library |
| GHC/Cabal | 9.4+ | Required for Haskell poetry module |

## Quick Build and Run (Recommended)

```bash
# From the repository root
./compile-native.sh --release
./run.sh
```

This builds the native library, runs native tests, and launches Simjot with the correct FFM flags.

## Build Methods

### 1. Maven Build (Core Java)

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

For native acceleration, build the C/C++ library with `./compile-native.sh` and use `./run.sh`.

### 2. IDE Build

1. Import the `Simjot/` directory as a Maven project
2. Set the main class to `main.ui.app.JournalApp`
3. Build and run from your IDE

If you want native acceleration inside the IDE, add VM options:
`--enable-preview --enable-native-access=ALL-UNNAMED` and ensure the native library path is configured.

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

When running the jar manually, include `--enable-preview` and `--enable-native-access=ALL-UNNAMED`,
and set `-Djava.library.path` to the native library directory (or use `./run.sh`).

## Native Packaging with jpackage (recommended)

Before packaging, build the native library so it is copied into `Simjot/src/main/resources/native`:
```bash
./compile-native.sh --release
```

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

### Recommended (Run Script)
```bash
./run.sh
```

### From JAR
```bash
cd Simjot
java -jar target/Simjot-*.jar
```

### With Custom Memory
```bash
./run.sh -- -Xmx2g
```

### With Debug Logging
```bash
SIMJOT_LOG=debug ./run.sh
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `javac` not found | Ensure JDK 24+ `bin` is on your `PATH` |
| `jpackage` not found | Included with JDK 14+; verify installation |
| Missing resources | Ensure resources are copied to `build/classes/` |
| Maven build fails | Run `mvn clean` then retry |
| Native library not found | Run `./compile-native.sh` and ensure it copies into `Simjot/src/main/resources/native` |
| FFM preview warnings | Expected with JDK 24; use `./run.sh` for the correct flags |

## Convenience Scripts

### Requirements

| Script | Required | Install (macOS) | Install (Linux) |
|--------|----------|-----------------|-----------------|
| `./compile-native.sh` | CMake 3.20+ | `brew install cmake` | `sudo apt install cmake` |
| `./compile-native.sh` | C/C++ compiler | `xcode-select --install` | `sudo apt install build-essential` |
| `./compile-native.sh --haskell` | GHC + Cabal | `brew install ghc cabal-install` | `sudo apt install ghc cabal-install` |
| `./run.sh` | JDK 24+ | `brew install openjdk@24` | See [Adoptium](https://adoptium.net) |
| `./run.sh` | Maven 3.8+ | `brew install maven` | `sudo apt install maven` |

Haskell is optional and only required if you want to build the Haskell poetry module.

### `./compile-native.sh` to build native library

Compiles the C/C++ native library.
Please make sure to do this before running the application,
as many features rely on the native library for full performance.

```bash
./compile-native.sh
```

**Options:**
| Flag | Description |
|------|-------------|
| `--clean` | Clean build directories before compiling |
| `--release` | Build with optimizations (default: debug) |
| `--haskell` | Also build the Haskell FFI library |
| `--skip-tests` | Skip running native tests |

**Examples:**
```bash
./compile-native.sh --release # Optimized build
./compile-native.sh --clean --haskell # Full clean build with Haskell
```

Output: `libsimjot_native.dylib` (macOS) / `libsimjot_native.so` (Linux) / `simjot_native.dll` (Windows)

## Manually building native library

If you prefer manual compilation:

```bash
cd Simjot/src/main/native
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
make
```

## Manually building Haskell module

Poetry analysis module via Haskell FFI:

```bash
cd Simjot/src/main/haskell
cabal build
```


### `./run.sh`

The easiest way to run Simjot:

```bash
./run.sh
```

This script:
- Automatically detects and uses JDK 24+
- Builds the JAR if missing (via Maven)
- Configures native library paths for FFM integration

**Options:**
| Flag | Description |
|------|-------------|
| `--no-build` | Skip Maven build, use existing JAR |
| `--build` | Force rebuild even if JAR exists |
| `--no-native` | Run in pure Java mode (no native library) |
| `--` | Pass remaining arguments to Java |

**Examples:**
```bash
./run.sh --no-build # Quick start with existing build
./run.sh --no-native # Skip native library
./run.sh -- -Xmx2g # Pass custom JVM args
```

## Project Dependencies

Core dependencies are as follows: 
- **JUnit 5**: Testing framework
- **Apache PDFBox 2.0.29**: PDF export functionality
- **JNativeHook 2.2.2**: Global hotkey support
- **Batik 1.17**: SVG rendering
