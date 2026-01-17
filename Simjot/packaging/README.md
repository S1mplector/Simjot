# Simjot Packaging & Distribution

This directory contains scripts to build distributable packages for Simjot.

## macOS Installer (.pkg)

### Quick Start

```bash
cd packaging
./build-macos-pkg.sh
```

This creates a professional `.pkg` installer in `dist/Simjot-X.X.X.pkg`.

### Requirements

- **Java 17+** with `jpackage` (included in JDK 17+)
- **JDK 24+** for building the app JAR
- **arm64 + x86_64 JDKs** (required for `--universal`, see notes below)
- **macOS 11+** (runtime from JDK 24 requires macOS 11 or newer)
- **Maven** (`mvn`)
- **Xcode Command Line Tools** (for `iconutil`, `pkgbuild`, `productbuild`)

Install Xcode tools:
```bash
xcode-select --install
```

### Options

| Option | Description |
|--------|-------------|
| `--dmg` | Also create a DMG disk image |
| `--clean` | Clean build directories before starting |
| `--sign "Developer ID"` | Code sign with your Developer ID |
| `--universal` | Build a universal app (arm64 + x86_64) |
| `-h, --help` | Show help |

### Examples

```bash
# Build PKG only
./build-macos-pkg.sh

# Build universal PKG (Intel + Apple Silicon)
JDK_ARM64_HOME="/path/to/jdk-arm64" \
JDK_X86_64_HOME="/path/to/jdk-x86_64" \
./build-macos-pkg.sh --universal

# Build both PKG and DMG
./build-macos-pkg.sh --dmg

# Clean build with DMG
./build-macos-pkg.sh --clean --dmg

# Build and code sign (for distribution)
./build-macos-pkg.sh --sign "Developer ID Application: Your Name (XXXXXXXXXX)"
```

### What It Does

1. **Builds** the shaded JAR with Maven
2. **Generates** a proper macOS `.icns` icon from the app's vector icon
3. **Creates** an optimized Java runtime with `jlink` (smaller than full JRE)
4. **Packages** as a native macOS app bundle (`.app`)
5. **Enhances** `Info.plist` for better macOS integration
6. **Creates** a `.pkg` installer with welcome/conclusion screens
7. **(Optional)** Creates a `.dmg` disk image

### Universal Build Notes

For `--universal`, provide both arm64 and x86_64 JDKs:

```bash
JDK_ARM64_HOME="/path/to/jdk-arm64"
JDK_X86_64_HOME="/path/to/jdk-x86_64"
./build-macos-pkg.sh --universal
```

If you already have a universal2 JDK, you can skip the env vars and ensure its
`jpackage` is on your `PATH`.

If you keep JDKs under `~/.local/jdks`, the script auto-detects the newest
arm64 + x86_64 JDKs there when `--universal` is used.

### Output Files

After running the script:

```
dist/
├── Simjot-1.0.0.pkg      # macOS Installer
└── Simjot-1.0.0.dmg      # DMG (if --dmg used)

build/macos-installer/
├── Simjot.app/           # The app bundle
├── app.icns              # Generated icon
└── runtime/              # Custom JRE
```

### App Icon

The script automatically generates a proper `.icns` icon file using `IconExporter.java`. The icon features:
- Windows 7 Aero-style glossy design
- Blue gradient background
- White "S" lettermark
- Multiple sizes for Retina displays

The generated `app.icns` is also saved to `packaging/app.icns` for future use.

### Troubleshooting

#### "App is damaged" or Gatekeeper warning
For unsigned apps, users need to:
1. Right-click the app → "Open" → "Open" again
2. Or: System Preferences → Security & Privacy → "Open Anyway"

#### Code signing for distribution
To distribute publicly, sign with a Developer ID:
```bash
./build-macos-pkg.sh --sign "Developer ID Application: Your Name"
```

Then notarize:
```bash
xcrun notarytool submit dist/Simjot-1.0.0.pkg --apple-id YOUR_APPLE_ID --team-id TEAM_ID --password APP_SPECIFIC_PASSWORD --wait
xcrun stapler staple dist/Simjot-1.0.0.pkg
```

---

## Windows Portable Executable (.exe)

### Quick Start

**PowerShell (recommended):**
```powershell
cd packaging
.\build-windows-exe.ps1
```

**Command Prompt:**
```cmd
cd packaging
build-windows-exe.bat
```

This creates a portable executable distribution in `dist/Simjot-X.X.X-portable/`.

### Requirements

- **Java 17+** JDK with `jpackage` and `jlink`
- **Maven** (`mvn`)
- **WiX Toolset 3.x** (optional, for MSI installer)

### Options

| Option | Description |
|--------|-------------|
| `-Clean` / `--clean` | Clean build directories before starting |
| `-Msi` / `--msi` | Also create an MSI installer |
| `-Zip` / `--zip` | Also create a portable ZIP archive |
| `-Help` / `--help` | Show help |

### Examples

```powershell
# Basic build - portable executable only
.\build-windows-exe.ps1

# Full build with ZIP and MSI
.\build-windows-exe.ps1 -Clean -Zip -Msi

# Command Prompt equivalent
build-windows-exe.bat --clean --zip --msi
```

### What It Does

1. **Builds** the shaded JAR with Maven
2. **Creates** an optimized Java runtime with `jlink` (smaller than full JRE)
3. **Packages** as a native Windows application with `jpackage`
4. **Bundles** the native library (`simjot_native.dll`) if available
5. **(Optional)** Creates a portable ZIP archive
6. **(Optional)** Creates an MSI installer

### Output Files

After running the script:

```
dist/
├── Simjot-1.0.0-portable/           # Portable app (just extract and run)
│   ├── Simjot.exe                   # Main executable
│   ├── app/                         # Application files
│   │   └── simjot_native.dll        # Native library
│   └── runtime/                     # Bundled JRE
├── Simjot-1.0.0-windows-portable.zip  # ZIP (if --zip used)
└── Simjot-1.0.0.msi                   # MSI (if --msi used)
```

### Building the Native Library on Windows

To include native optimizations:

```cmd
cd src\main\native
cmake -B build
cmake --build build --config Release
```

The build script will automatically bundle `simjot_native.dll` if found.

### MSI Installer Notes

The MSI installer requires **WiX Toolset 3.x**:
- Download from: https://wixtoolset.org/releases/
- Install and ensure `candle.exe` and `light.exe` are in PATH

The MSI installer provides:
- Start menu shortcut
- Desktop shortcut (optional)
- Add/Remove Programs entry
- Custom install directory selection

---

## Linux Installer (.deb)

### Quick Start

```bash
cd packaging
./build-linux-deb.sh
```

This creates a `.deb` package in `dist/`.

### Requirements

- **Java 17+** with `jpackage`, `jdeps`, and `jlink`
- **Maven** (`mvn`)
- **dpkg-deb** and **fakeroot**

On Debian/Ubuntu:
```bash
sudo apt-get install -y fakeroot dpkg-dev
```

### Options

| Option | Description |
|--------|-------------|
| `--clean` | Clean build directories before starting |
| `--tests` | Run tests before packaging |
| `--verbose` | Verbose `jpackage` output |

### What It Does

1. **Builds** the shaded JAR with Maven
2. **Creates** a custom runtime image with `jlink`
3. **Packages** the app into a `.deb` using `jpackage`

### Output Files

After running the script:

```
dist/
└── simjot_0.1.0-1_amd64.deb
```

---

## Other Scripts

### `make-dist.sh`
Creates a simple cross-platform distribution folder with launcher scripts:
```bash
./make-dist.sh
```

Output: `dist/Simjot-X.X.X/` with:
- `Simjot-X.X.X.jar` - The application
- `simjot` - Unix launcher script
- `simjot.bat` - Windows launcher script

### `jpackage-macos.sh`
Legacy script for creating DMG only. Use `build-macos-pkg.sh` instead.

---

## Icon Export Utility

`IconExporter.java` is a standalone utility to export the app icon to PNG files:

```bash
# Compile
javac IconExporter.java

# Run (outputs to AppIcon.iconset/)
java IconExporter AppIcon.iconset

# Convert to .icns
iconutil -c icns AppIcon.iconset -o app.icns
```

The main build script runs this automatically.
