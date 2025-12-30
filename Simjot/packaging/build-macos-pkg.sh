#!/usr/bin/env bash
#
# Simjot macOS Installer Builder
# Creates a professional .pkg installer with proper app icon
#
# Usage: ./build-macos-pkg.sh [--dmg] [--clean]
#
# Requirements:
#   - Java 17+ (for jpackage)
#   - Maven (mvn)
#   - Xcode Command Line Tools (for iconutil, pkgbuild, productbuild)
#
set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================
APP_NAME="Simjot"
BUNDLE_ID="com.s1mplector.simjot"
VENDOR="S1mplector"
CATEGORY="public.app-category.productivity"

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/macos-installer"
ICONSET_DIR="$BUILD_DIR/AppIcon.iconset"
CUSTOM_ICON_ICNS="$ROOT_DIR/src/main/resources/img/icons/original/simjot.icns"
DIST_DIR="$ROOT_DIR/dist"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# Helper Functions
# ============================================================================
log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

check_command() {
    if ! command -v "$1" &>/dev/null; then
        log_error "Required command not found: $1"
        log_error "Please install $1 and try again."
        exit 1
    fi
}

# ============================================================================
# Parse Arguments
# ============================================================================
CREATE_DMG=false
CLEAN_BUILD=false
SIGN_APP=false
COPY_TO_DESKTOP=false
ICON_COLOR="blue"
DEVELOPER_ID=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dmg)      CREATE_DMG=true; shift ;;
        --clean)    CLEAN_BUILD=true; shift ;;
        --desktop)  COPY_TO_DESKTOP=true; shift ;;
        --color)    ICON_COLOR="${2:-blue}"; shift 2 ;;
        --sign)     SIGN_APP=true; DEVELOPER_ID="${2:-}"; shift 2 ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --dmg          Also create a DMG disk image"
            echo "  --clean        Clean build directories before starting"
            echo "  --desktop      Copy installer(s) to Desktop"
            echo "  --color COLOR  Icon color: blue, green, purple, red, orange, teal, pink, gold, or #RRGGBB"
            echo "  --sign ID      Code sign with Developer ID"
            echo "  -h, --help     Show this help"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ============================================================================
# Pre-flight Checks
# ============================================================================
log_info "Checking requirements..."
check_command java
check_command mvn
check_command iconutil
check_command jpackage

# Verify we're on macOS
if [[ "$(uname)" != "Darwin" ]]; then
    log_error "This script must be run on macOS"
    exit 1
fi

# Get Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 17 ]]; then
    log_error "Java 17 or higher is required (found: $JAVA_VERSION)"
    exit 1
fi
log_ok "Java $JAVA_VERSION detected"

# ============================================================================
# Setup
# ============================================================================
cd "$ROOT_DIR"

if [[ "$CLEAN_BUILD" == true ]]; then
    log_info "Cleaning build directories..."
    rm -rf "$BUILD_DIR" "$DIST_DIR"/*.pkg "$DIST_DIR"/*.dmg
fi

mkdir -p "$BUILD_DIR" "$ICONSET_DIR" "$DIST_DIR"

# Get version from pom.xml
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null || echo "1.0.0")"
log_info "Building $APP_NAME version $VERSION"

# ============================================================================
# Step 1: Build the Application JAR
# ============================================================================
log_info "Building application JAR..."
mvn -DskipTests clean package -q
SHADED_JAR="$APP_NAME-$VERSION.jar"
JAR_PATH="$ROOT_DIR/target/$SHADED_JAR"

if [[ ! -f "$JAR_PATH" ]]; then
    log_error "JAR not found: $JAR_PATH"
    exit 1
fi
log_ok "JAR built: $SHADED_JAR"

# ============================================================================
# Step 2: Generate App Icon (.icns)
# ============================================================================
log_info "Generating application icon..."

if [[ -f "$CUSTOM_ICON_ICNS" ]]; then
    ICNS_PATH="$CUSTOM_ICON_ICNS"
    log_ok "Using bundled icon: $ICNS_PATH"
else
    # Compile and run the icon exporter
    ICON_EXPORTER="$SCRIPT_DIR/IconExporter.java"
    if [[ -f "$ICON_EXPORTER" ]]; then
    log_info "  Compiling IconExporter..."
    javac -d "$BUILD_DIR" "$ICON_EXPORTER"
    
    log_info "  Generating icon PNGs..."
    (cd "$BUILD_DIR" && java IconExporter "$ICONSET_DIR" "$ICON_COLOR")
    
    # Rename files to match Apple's iconset naming convention
    log_info "  Preparing iconset..."
    cd "$ICONSET_DIR"
    
    # Standard naming for iconutil
    [[ -f icon_16x16.png ]]     && mv icon_16x16.png icon_16x16.png 2>/dev/null || true
    [[ -f icon_16x16@2x.png ]]  && mv icon_16x16@2x.png icon_16x16@2x.png 2>/dev/null || true
    [[ -f icon_32x32.png ]]     && mv icon_32x32.png icon_32x32.png 2>/dev/null || true
    [[ -f icon_32x32@2x.png ]]  && mv icon_32x32@2x.png icon_32x32@2x.png 2>/dev/null || true
    [[ -f icon_64x64.png ]]     && mv icon_64x64.png icon_32x32@2x.png 2>/dev/null || true
    [[ -f icon_128x128.png ]]   && mv icon_128x128.png icon_128x128.png 2>/dev/null || true
    [[ -f icon_128x128@2x.png ]] && mv icon_128x128@2x.png icon_128x128@2x.png 2>/dev/null || true
    [[ -f icon_256x256.png ]]   && mv icon_256x256.png icon_256x256.png 2>/dev/null || true
    [[ -f icon_256x256@2x.png ]] && mv icon_256x256@2x.png icon_256x256@2x.png 2>/dev/null || true
    [[ -f icon_512x512.png ]]   && mv icon_512x512.png icon_512x512.png 2>/dev/null || true
    [[ -f icon_512x512@2x.png ]] && mv icon_512x512@2x.png icon_512x512@2x.png 2>/dev/null || true
    [[ -f icon_1024x1024.png ]] && cp icon_1024x1024.png icon_512x512@2x.png 2>/dev/null || true
    
    cd "$ROOT_DIR"
    
    # Create .icns from iconset
    ICNS_PATH="$BUILD_DIR/app.icns"
    iconutil -c icns "$ICONSET_DIR" -o "$ICNS_PATH"
    
    # Also copy to packaging folder for future use
    cp "$ICNS_PATH" "$SCRIPT_DIR/app.icns"
        log_ok "Icon created: app.icns"
    else
        log_warn "IconExporter.java not found, checking for existing icon..."
        if [[ -f "$SCRIPT_DIR/app.icns" ]]; then
            ICNS_PATH="$SCRIPT_DIR/app.icns"
            log_ok "Using existing icon: $ICNS_PATH"
        else
            log_warn "No icon found - app will use default Java icon"
            ICNS_PATH=""
        fi
    fi
fi

# ============================================================================
# Step 3: Create Custom Runtime (jlink)
# ============================================================================
log_info "Creating optimized Java runtime..."
RUNTIME_DIR="$BUILD_DIR/runtime"
rm -rf "$RUNTIME_DIR"

# Detect required modules
log_info "  Analyzing module dependencies..."
# Filter out warning lines from jdeps output (split package warnings go to stdout)
MODULES=$(jdeps --multi-release 17 --ignore-missing-deps --print-module-deps "$JAR_PATH" 2>/dev/null | grep -v "^Warning:" | tail -1 || echo "java.base,java.desktop,java.logging,java.prefs,java.sql")

# Add modules that might be missed but are commonly needed for Swing apps
EXTRA_MODULES="jdk.unsupported,java.naming,java.management"
MODULES="$MODULES,$EXTRA_MODULES"

log_info "  Required modules: $MODULES"
log_info "  Building runtime with jlink..."

jlink \
    --add-modules "$MODULES" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output "$RUNTIME_DIR"

log_ok "Custom runtime created ($(du -sh "$RUNTIME_DIR" | cut -f1))"

# ============================================================================
# Step 4: Create macOS App Bundle with jpackage
# ============================================================================
log_info "Creating macOS application bundle..."

# Prepare jpackage arguments
JPKG_ARGS=(
    --type app-image
    --name "$APP_NAME"
    --app-version "$VERSION"
    --vendor "$VENDOR"
    --dest "$BUILD_DIR"
    --input "$ROOT_DIR/target"
    --main-jar "$SHADED_JAR"
    --runtime-image "$RUNTIME_DIR"
    --mac-package-identifier "$BUNDLE_ID"
    --mac-package-name "$APP_NAME"
    --java-options "-Xmx1G"
    --java-options "-Dapple.laf.useScreenMenuBar=true"
    --java-options "-Dapple.awt.application.name=$APP_NAME"
    --java-options "-Dcom.apple.mrj.application.apple.menu.about.name=$APP_NAME"
)

# Add icon if available
if [[ -n "${ICNS_PATH:-}" && -f "$ICNS_PATH" ]]; then
    JPKG_ARGS+=(--icon "$ICNS_PATH")
fi

# Remove existing app bundle
rm -rf "$BUILD_DIR/$APP_NAME.app"

jpackage "${JPKG_ARGS[@]}"
log_ok "App bundle created: $APP_NAME.app"

# ============================================================================
# Step 5: Post-process Info.plist for better macOS integration
# ============================================================================
log_info "Enhancing Info.plist..."
PLIST_PATH="$BUILD_DIR/$APP_NAME.app/Contents/Info.plist"

if [[ -f "$PLIST_PATH" ]]; then
    # Add additional plist entries for better macOS integration
    /usr/libexec/PlistBuddy -c "Add :LSApplicationCategoryType string $CATEGORY" "$PLIST_PATH" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :NSHighResolutionCapable bool true" "$PLIST_PATH" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :NSSupportsAutomaticGraphicsSwitching bool true" "$PLIST_PATH" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :LSMinimumSystemVersion string 10.14" "$PLIST_PATH" 2>/dev/null || true
    log_ok "Info.plist enhanced"
fi

# ============================================================================
# Step 6: Code Sign (optional)
# ============================================================================
if [[ "$SIGN_APP" == true && -n "$DEVELOPER_ID" ]]; then
    log_info "Code signing application..."
    codesign --force --deep --sign "$DEVELOPER_ID" "$BUILD_DIR/$APP_NAME.app"
    log_ok "Application signed"
fi

# ============================================================================
# Step 7: Create .pkg Installer
# ============================================================================
log_info "Creating .pkg installer..."
PKG_PATH="$DIST_DIR/$APP_NAME-$VERSION.pkg"

# Create component package
COMPONENT_PKG="$BUILD_DIR/component.pkg"
COMPONENT_PLIST="$BUILD_DIR/component.plist"

# Write component plist to file (process substitution doesn't work with pkgbuild)
cat > "$COMPONENT_PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<array>
    <dict>
        <key>BundleHasStrictIdentifier</key>
        <true/>
        <key>BundleIsRelocatable</key>
        <false/>
        <key>BundleIsVersionChecked</key>
        <true/>
        <key>BundleOverwriteAction</key>
        <string>upgrade</string>
        <key>RootRelativeBundlePath</key>
        <string>$APP_NAME.app</string>
    </dict>
</array>
</plist>
EOF

pkgbuild \
    --root "$BUILD_DIR" \
    --identifier "$BUNDLE_ID" \
    --version "$VERSION" \
    --install-location "/Applications" \
    --component-plist "$COMPONENT_PLIST" \
    "$COMPONENT_PKG"

# Create distribution XML for customizable installer
DIST_XML="$BUILD_DIR/distribution.xml"

# Check for optional background image
BG_IMAGE_TAG=""
if [[ -f "$SCRIPT_DIR/installer-background.png" ]]; then
    cp "$SCRIPT_DIR/installer-background.png" "$RESOURCES_DIR/"
    BG_IMAGE_TAG='<background file="installer-background.png" alignment="bottomleft" scaling="proportional"/>'
    log_info "Using custom installer background"
elif [[ -f "$SCRIPT_DIR/installer-background.jpg" ]]; then
    cp "$SCRIPT_DIR/installer-background.jpg" "$RESOURCES_DIR/"
    BG_IMAGE_TAG='<background file="installer-background.jpg" alignment="bottomleft" scaling="proportional"/>'
    log_info "Using custom installer background"
fi

cat > "$DIST_XML" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<installer-gui-script minSpecVersion="2">
    <title>$APP_NAME</title>
    <organization>$BUNDLE_ID</organization>
    <domains enable_localSystem="true"/>
    <options customize="never" require-scripts="false" hostArchitectures="x86_64,arm64"/>
    
    $BG_IMAGE_TAG
    
    <welcome file="welcome.html" mime-type="text/html"/>
    <conclusion file="conclusion.html" mime-type="text/html"/>
    
    <choices-outline>
        <line choice="default">
            <line choice="$BUNDLE_ID"/>
        </line>
    </choices-outline>
    
    <choice id="default"/>
    <choice id="$BUNDLE_ID" visible="false">
        <pkg-ref id="$BUNDLE_ID"/>
    </choice>
    
    <pkg-ref id="$BUNDLE_ID" version="$VERSION" onConclusion="none">component.pkg</pkg-ref>
</installer-gui-script>
EOF

# Create welcome and conclusion HTML files
RESOURCES_DIR="$BUILD_DIR/resources"
mkdir -p "$RESOURCES_DIR"

cat > "$RESOURCES_DIR/welcome.html" <<EOF
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 20px; }
        h1 { color: #1F5FA9; }
        .version { color: #666; font-size: 0.9em; }
    </style>
</head>
<body>
    <h1>Welcome to $APP_NAME</h1>
    <p class="version">Version $VERSION</p>
    <p>$APP_NAME is your personal journaling companion with rich text editing, mood tracking, and an optional AI assistant.</p>
    <p>This installer will guide you through the installation process.</p>
    <p><strong>Features:</strong></p>
    <ul>
        <li>Rich text journal entries with images</li>
        <li>Mood tracking and visualization</li>
        <li>Multiple notebooks organization</li>
        <li>Automatic backup system</li>
        <li>Optional AI companion (Sim)</li>
    </ul>
</body>
</html>
EOF

cat > "$RESOURCES_DIR/conclusion.html" <<EOF
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 20px; }
        h1 { color: #28a745; }
        .path { background: #f5f5f5; padding: 8px 12px; border-radius: 4px; font-family: monospace; }
    </style>
</head>
<body>
    <h1>Installation Complete!</h1>
    <p>$APP_NAME has been installed successfully.</p>
    <p>You can find it in your Applications folder:</p>
    <p class="path">/Applications/$APP_NAME.app</p>
    <p>You can also search for "$APP_NAME" in Spotlight (⌘ + Space).</p>
    <p><strong>Getting Started:</strong></p>
    <ol>
        <li>Launch $APP_NAME from Applications</li>
        <li>Choose or create a folder for your journals</li>
        <li>Start writing!</li>
    </ol>
    <p>Thank you for installing $APP_NAME!</p>
</body>
</html>
EOF

# Build the final product archive (.pkg)
productbuild \
    --distribution "$DIST_XML" \
    --resources "$RESOURCES_DIR" \
    --package-path "$BUILD_DIR" \
    "$PKG_PATH"

log_ok "Installer created: $PKG_PATH"

# ============================================================================
# Step 8: Create DMG (optional)
# ============================================================================
if [[ "$CREATE_DMG" == true ]]; then
    log_info "Creating DMG disk image..."
    DMG_PATH="$DIST_DIR/$APP_NAME-$VERSION.dmg"
    
    # Create a temporary DMG staging directory
    DMG_STAGE="$BUILD_DIR/dmg-stage"
    rm -rf "$DMG_STAGE"
    mkdir -p "$DMG_STAGE"
    
    # Copy app bundle
    cp -R "$BUILD_DIR/$APP_NAME.app" "$DMG_STAGE/"
    
    # Create Applications symlink
    ln -s /Applications "$DMG_STAGE/Applications"
    
    # Create DMG
    hdiutil create -volname "$APP_NAME $VERSION" \
        -srcfolder "$DMG_STAGE" \
        -ov -format UDZO \
        "$DMG_PATH"
    
    log_ok "DMG created: $DMG_PATH"
fi

# ============================================================================
# Step 9: Copy to Desktop (optional)
# ============================================================================
if [[ "$COPY_TO_DESKTOP" == true ]]; then
    log_info "Copying installer(s) to Desktop..."
    DESKTOP_DIR="$HOME/Desktop"
    
    cp "$PKG_PATH" "$DESKTOP_DIR/"
    log_ok "Copied: $(basename "$PKG_PATH") → Desktop"
    
    if [[ "$CREATE_DMG" == true && -f "$DMG_PATH" ]]; then
        cp "$DMG_PATH" "$DESKTOP_DIR/"
        log_ok "Copied: $(basename "$DMG_PATH") → Desktop"
    fi
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "=============================================="
echo -e "${GREEN}Build Complete!${NC}"
echo "=============================================="
echo ""
echo "Outputs:"
echo "  📦 PKG Installer: $PKG_PATH"
if [[ "$CREATE_DMG" == true ]]; then
    echo "  💿 DMG Image:     $DMG_PATH"
fi
echo "  📱 App Bundle:    $BUILD_DIR/$APP_NAME.app"
if [[ "$COPY_TO_DESKTOP" == true ]]; then
    echo ""
    echo "Copied to Desktop:"
    echo "  🖥️  ~/Desktop/$(basename "$PKG_PATH")"
    if [[ "$CREATE_DMG" == true ]]; then
        echo "  🖥️  ~/Desktop/$(basename "$DMG_PATH")"
    fi
fi
echo ""
echo "To install:"
echo "  1. Double-click the .pkg file"
echo "  2. Follow the installation wizard"
echo ""
echo "To test the app bundle directly:"
echo "  open \"$BUILD_DIR/$APP_NAME.app\""
echo ""

if [[ "$SIGN_APP" != true ]]; then
    echo -e "${YELLOW}Note:${NC} App is not code-signed. Users may need to:"
    echo "  1. Right-click the app and select 'Open'"
    echo "  2. Or allow it in System Preferences > Security & Privacy"
    echo ""
fi
