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
| `-h, --help` | Show help |

### Examples

```bash
# Build PKG only
./build-macos-pkg.sh

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
