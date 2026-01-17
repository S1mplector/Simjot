#!/usr/bin/env bash
#
# Simjot macOS Installer Builder
# Creates a professional .pkg installer with proper app icon
#
# Usage: ./build-macos-pkg.sh [--dmg] [--clean] [--universal]
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
MACOS_MIN_VERSION="${SIMJOT_MACOS_MIN_VERSION:-${MACOSX_DEPLOYMENT_TARGET:-11.0}}"

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

get_java_version() {
    local java_bin="$1"
    "$java_bin" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1
}

discover_jdk_home() {
    local search_dir="$1"
    local arch="$2"
    local min_version="$3"
    local best=""
    local best_version=0

    if ! command -v lipo >/dev/null 2>&1; then
        echo ""
        return 0
    fi
    if [[ ! -d "$search_dir" ]]; then
        echo ""
        return 0
    fi

    while IFS= read -r -d '' java_bin; do
        local home
        home="$(cd "$(dirname "$java_bin")/.." && pwd)"
        local jvm_lib="$home/lib/server/libjvm.dylib"
        [[ -f "$jvm_lib" ]] || continue

        local archs
        archs="$(lipo -archs "$jvm_lib" 2>/dev/null || true)"
        case "$arch" in
            arm64)
                [[ "$archs" == *"arm64"* ]] || continue
                ;;
            x86_64)
                [[ "$archs" == *"x86_64"* ]] || continue
                ;;
            *)
                continue
                ;;
        esac

        local ver=""
        if [[ "$arch" == "x86_64" ]]; then
            if ! arch -x86_64 /usr/bin/true >/dev/null 2>&1; then
                continue
            fi
            ver=$(arch -x86_64 "$java_bin" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        else
            ver=$(get_java_version "$java_bin")
        fi

        if [[ "$ver" =~ ^[0-9]+$ && "$ver" -ge "$min_version" ]]; then
            if [[ "$ver" -gt "$best_version" ]]; then
                best="$home"
                best_version="$ver"
            fi
        fi
    done < <(find "$search_dir" -type f -path "*/bin/java" -perm -111 -print0 2>/dev/null)

    echo "$best"
}

require_jdk() {
    local jdk_path="$1"
    local arch="$2"
    local jvm_lib="$jdk_path/lib/server/libjvm.dylib"

    if [[ -z "$jdk_path" || ! -x "$jdk_path/bin/java" ]]; then
        log_error "JDK not found or invalid: $jdk_path"
        exit 1
    fi
    if [[ ! -f "$jvm_lib" ]]; then
        log_error "JDK missing libjvm.dylib: $jvm_lib"
        exit 1
    fi

    local archs
    archs="$(lipo -archs "$jvm_lib" 2>/dev/null || true)"
    case "$arch" in
        arm64)
            if [[ "$archs" != *"arm64"* ]]; then
                log_error "Expected arm64 JDK, found: ${archs:-unknown}"
                exit 1
            fi
            ;;
        x86_64)
            if [[ "$archs" != *"x86_64"* ]]; then
                log_error "Expected x86_64 JDK, found: ${archs:-unknown}"
                exit 1
            fi
            ;;
        universal)
            if [[ "$archs" != *"x86_64"* || "$archs" != *"arm64"* ]]; then
                log_error "Expected universal JDK, found: ${archs:-unknown}"
                exit 1
            fi
            ;;
        *)
            log_error "Unknown arch check: $arch"
            exit 1
            ;;
    esac
}

require_universal_binary() {
    local path="$1"
    if [[ ! -f "$path" ]]; then
        log_error "Expected binary not found: $path"
        exit 1
    fi
    local archs
    archs="$(lipo -archs "$path" 2>/dev/null || true)"
    if [[ "$archs" != *"x86_64"* || "$archs" != *"arm64"* ]]; then
        log_error "Universal build expected, but $path has archs: ${archs:-unknown}"
        exit 1
    fi
}

is_universal_binary() {
    local path="$1"
    local archs
    archs="$(lipo -archs "$path" 2>/dev/null || true)"
    [[ "$archs" == *"x86_64"* && "$archs" == *"arm64"* ]]
}

merge_universal_tree() {
    local arm_dir="$1"
    local x64_dir="$2"
    local out_dir="$3"

    rm -rf "$out_dir"
    mkdir -p "$out_dir"
    rsync -a "$arm_dir/" "$out_dir/"

    find "$arm_dir" -type f -print0 | while IFS= read -r -d '' arm_file; do
        local rel="${arm_file#$arm_dir/}"
        local x64_file="$x64_dir/$rel"
        local out_file="$out_dir/$rel"
        if [[ -f "$x64_file" ]]; then
            if file -b "$arm_file" | grep -q "Mach-O"; then
                lipo -create "$arm_file" "$x64_file" -output "$out_file"
                local mode
                mode=$(stat -f %Lp "$arm_file")
                chmod "$mode" "$out_file"
            fi
        fi
    done
}

# ============================================================================
# Parse Arguments
# ============================================================================
CREATE_DMG=false
CLEAN_BUILD=false
SIGN_APP=false
COPY_TO_DESKTOP=false
ICON_COLOR="blue"
BUILD_UNIVERSAL=false
DEVELOPER_ID=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dmg)      CREATE_DMG=true; shift ;;
        --clean)    CLEAN_BUILD=true; shift ;;
        --desktop)  COPY_TO_DESKTOP=true; shift ;;
        --color)    ICON_COLOR="${2:-blue}"; shift 2 ;;
        --universal) BUILD_UNIVERSAL=true; shift ;;
        --sign)     SIGN_APP=true; DEVELOPER_ID="${2:-}"; shift 2 ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --dmg          Also create a DMG disk image"
            echo "  --clean        Clean build directories before starting"
            echo "  --desktop      Copy installer(s) to Desktop"
            echo "  --color COLOR  Icon color: blue, green, purple, red, orange, teal, pink, gold, or #RRGGBB"
            echo "  --universal    Build a universal app (arm64 + x86_64)"
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

# Optional dual-JDK override for universal builds
JDK_ARM64_HOME="${JDK_ARM64_HOME:-${JDK_ARM64:-}}"
JDK_X86_64_HOME="${JDK_X86_64_HOME:-${JDK_X86_64:-}}"
MIN_JAVA_VERSION=24
AUTO_JDK_SEARCH_DIR="$HOME/.local/jdks"
if [[ "$BUILD_UNIVERSAL" == true && -z "$JDK_ARM64_HOME" && -z "$JDK_X86_64_HOME" ]]; then
    JDK_ARM64_HOME="$(discover_jdk_home "$AUTO_JDK_SEARCH_DIR" "arm64" "$MIN_JAVA_VERSION")"
    JDK_X86_64_HOME="$(discover_jdk_home "$AUTO_JDK_SEARCH_DIR" "x86_64" "$MIN_JAVA_VERSION")"
fi
USE_DUAL_JDK=false
if [[ "$BUILD_UNIVERSAL" == true && -n "$JDK_ARM64_HOME" && -n "$JDK_X86_64_HOME" ]]; then
    USE_DUAL_JDK=true
fi

# ============================================================================
# Pre-flight Checks
# ============================================================================
log_info "Checking requirements..."
check_command mvn
check_command iconutil
check_command pkgbuild
check_command productbuild

if [[ "$USE_DUAL_JDK" == true ]]; then
    check_command lipo
    check_command file
    require_jdk "$JDK_ARM64_HOME" "arm64"
    require_jdk "$JDK_X86_64_HOME" "x86_64"
    for tool in java jdeps jlink jpackage; do
        if [[ ! -x "$JDK_ARM64_HOME/bin/$tool" ]]; then
            log_error "Missing tool in arm64 JDK: $JDK_ARM64_HOME/bin/$tool"
            exit 1
        fi
        if [[ ! -x "$JDK_X86_64_HOME/bin/$tool" ]]; then
            log_error "Missing tool in x86_64 JDK: $JDK_X86_64_HOME/bin/$tool"
            exit 1
        fi
    done
    if ! arch -x86_64 /usr/bin/true >/dev/null 2>&1; then
        log_error "Rosetta is required to run x86_64 JDK tools."
        log_error "Install with: softwareupdate --install-rosetta --agree-to-license"
        exit 1
    fi
else
    check_command java
    check_command jdeps
    check_command jlink
    check_command jpackage
    if [[ "$BUILD_UNIVERSAL" == true ]]; then
        check_command lipo
        check_command file
    fi
fi

# Verify we're on macOS
if [[ "$(uname)" != "Darwin" ]]; then
    log_error "This script must be run on macOS"
    exit 1
fi

# Get Java version
JAVA_BIN="java"
if [[ "$USE_DUAL_JDK" == true ]]; then
    JAVA_BIN="$JDK_ARM64_HOME/bin/java"
fi
JAVA_VERSION=$(get_java_version "$JAVA_BIN")
if [[ "$JAVA_VERSION" -lt 17 ]]; then
    log_error "Java 17 or higher is required (found: $JAVA_VERSION)"
    exit 1
fi
log_ok "Java $JAVA_VERSION detected"
if [[ "$USE_DUAL_JDK" == true ]]; then
    log_ok "Using arm64 JDK: $JDK_ARM64_HOME"
    log_ok "Using x86_64 JDK: $JDK_X86_64_HOME"
fi
log_ok "macOS minimum version: $MACOS_MIN_VERSION"

# Validate universal JDK when requested (single-JDK path)
if [[ "$BUILD_UNIVERSAL" == true && "$USE_DUAL_JDK" != true ]]; then
    JPACKAGE_BIN="$(command -v jpackage)"
    JDK_HOME="$(cd "$(dirname "$JPACKAGE_BIN")/.." && pwd)"
    JVM_LIB="$JDK_HOME/lib/server/libjvm.dylib"
    JDK_ARCHS=""
    if [[ -f "$JVM_LIB" ]]; then
        JDK_ARCHS="$(lipo -archs "$JVM_LIB" 2>/dev/null || true)"
    fi
    if [[ "$JDK_ARCHS" != *"x86_64"* || "$JDK_ARCHS" != *"arm64"* ]]; then
        log_error "Universal build requested but JDK is not universal2."
        log_error "Install a universal2 JDK 17+ and ensure its bin is on PATH."
        log_error "Detected JDK: $JDK_HOME"
        log_error "Detected archs: ${JDK_ARCHS:-unknown}"
        exit 1
    fi
    log_ok "Universal2 JDK detected: $JDK_ARCHS"
fi

PKG_ARCHS="$(uname -m)"
if [[ "$BUILD_UNIVERSAL" == true ]]; then
    PKG_ARCHS="x86_64,arm64"
fi

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
if [[ "$USE_DUAL_JDK" == true ]]; then
    JAVA_HOME="$JDK_ARM64_HOME" PATH="$JDK_ARM64_HOME/bin:$PATH" mvn -DskipTests clean package -q
else
    mvn -DskipTests clean package -q
fi
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
RUNTIME_DIR_ARM="$BUILD_DIR/runtime-arm64"
RUNTIME_DIR_X64="$BUILD_DIR/runtime-x86_64"
rm -rf "$RUNTIME_DIR" "$RUNTIME_DIR_ARM" "$RUNTIME_DIR_X64"

# Detect required modules
log_info "  Analyzing module dependencies..."
# Filter out warning lines from jdeps output (split package warnings go to stdout)
if [[ "$USE_DUAL_JDK" == true ]]; then
    MODULES=$("$JDK_ARM64_HOME/bin/jdeps" --multi-release "$JAVA_VERSION" --ignore-missing-deps --print-module-deps "$JAR_PATH" 2>/dev/null | grep -v "^Warning:" | tail -1 || echo "java.base,java.desktop,java.logging,java.prefs,java.sql")
else
    MODULES=$(jdeps --multi-release "$JAVA_VERSION" --ignore-missing-deps --print-module-deps "$JAR_PATH" 2>/dev/null | grep -v "^Warning:" | tail -1 || echo "java.base,java.desktop,java.logging,java.prefs,java.sql")
fi

# Add modules that might be missed but are commonly needed for Swing apps
EXTRA_MODULES="jdk.unsupported,java.naming,java.management"
MODULES="$MODULES,$EXTRA_MODULES"

log_info "  Required modules: $MODULES"
log_info "  Building runtime with jlink..."

if [[ "$USE_DUAL_JDK" == true ]]; then
    "$JDK_ARM64_HOME/bin/jlink" \
        --add-modules "$MODULES" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=2 \
        --output "$RUNTIME_DIR_ARM"

    arch -x86_64 "$JDK_X86_64_HOME/bin/jlink" \
        --add-modules "$MODULES" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=2 \
        --output "$RUNTIME_DIR_X64"

    merge_universal_tree "$RUNTIME_DIR_ARM" "$RUNTIME_DIR_X64" "$RUNTIME_DIR"
    log_ok "Universal runtime created ($(du -sh "$RUNTIME_DIR" | cut -f1))"
else
    jlink \
        --add-modules "$MODULES" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=2 \
        --output "$RUNTIME_DIR"

    log_ok "Custom runtime created ($(du -sh "$RUNTIME_DIR" | cut -f1))"
fi
if [[ "$BUILD_UNIVERSAL" == true ]]; then
    require_universal_binary "$RUNTIME_DIR/lib/server/libjvm.dylib"
    log_ok "Universal runtime verified"
fi

# ============================================================================
# Step 4: Create macOS App Bundle with jpackage
# ============================================================================
log_info "Creating macOS application bundle..."

# Prepare jpackage arguments
JPKG_BASE_ARGS=(
    --type app-image
    --name "$APP_NAME"
    --app-version "$VERSION"
    --vendor "$VENDOR"
    --input "$ROOT_DIR/target"
    --main-jar "$SHADED_JAR"
    --mac-package-identifier "$BUNDLE_ID"
    --mac-package-name "$APP_NAME"
    --java-options "-Xmx1G"
    --java-options "-Dapple.laf.useScreenMenuBar=true"
    --java-options "-Dapple.awt.application.name=$APP_NAME"
    --java-options "-Dcom.apple.mrj.application.apple.menu.about.name=$APP_NAME"
    --java-options "--enable-preview"
)

# Add icon if available
if [[ -n "${ICNS_PATH:-}" && -f "$ICNS_PATH" ]]; then
    JPKG_BASE_ARGS+=(--icon "$ICNS_PATH")
fi

# Remove existing app bundle
rm -rf "$BUILD_DIR/$APP_NAME.app"

if [[ "$USE_DUAL_JDK" == true ]]; then
    BUILD_DIR_ARM="$BUILD_DIR/arm64"
    BUILD_DIR_X64="$BUILD_DIR/x86_64"
    mkdir -p "$BUILD_DIR_ARM" "$BUILD_DIR_X64"
    rm -rf "$BUILD_DIR_ARM/$APP_NAME.app" "$BUILD_DIR_X64/$APP_NAME.app"

    JPKG_ARGS_ARM=("${JPKG_BASE_ARGS[@]}" --dest "$BUILD_DIR_ARM" --runtime-image "$RUNTIME_DIR_ARM")
    JPKG_ARGS_X64=("${JPKG_BASE_ARGS[@]}" --dest "$BUILD_DIR_X64" --runtime-image "$RUNTIME_DIR_X64")

    "$JDK_ARM64_HOME/bin/jpackage" "${JPKG_ARGS_ARM[@]}"
    arch -x86_64 "$JDK_X86_64_HOME/bin/jpackage" "${JPKG_ARGS_X64[@]}"

    cp -R "$BUILD_DIR_ARM/$APP_NAME.app" "$BUILD_DIR/$APP_NAME.app"
    rm -rf "$BUILD_DIR/$APP_NAME.app/Contents/runtime"
    cp -R "$RUNTIME_DIR" "$BUILD_DIR/$APP_NAME.app/Contents/runtime"

    ARM_LAUNCHER="$BUILD_DIR_ARM/$APP_NAME.app/Contents/MacOS/$APP_NAME"
    X64_LAUNCHER="$BUILD_DIR_X64/$APP_NAME.app/Contents/MacOS/$APP_NAME"
    UNIVERSAL_LAUNCHER="$BUILD_DIR/$APP_NAME.app/Contents/MacOS/$APP_NAME"
    lipo -create "$ARM_LAUNCHER" "$X64_LAUNCHER" -output "$UNIVERSAL_LAUNCHER"
    chmod "$(stat -f %Lp "$ARM_LAUNCHER")" "$UNIVERSAL_LAUNCHER"
else
    JPKG_ARGS=("${JPKG_BASE_ARGS[@]}" --dest "$BUILD_DIR" --runtime-image "$RUNTIME_DIR")
    jpackage "${JPKG_ARGS[@]}"
fi

log_ok "App bundle created: $APP_NAME.app"
if [[ "$BUILD_UNIVERSAL" == true ]]; then
    require_universal_binary "$BUILD_DIR/$APP_NAME.app/Contents/MacOS/$APP_NAME"
    log_ok "Universal launcher verified"
fi

# ============================================================================
# Step 4b: Bundle Native Library
# ============================================================================
NATIVE_DIR="$ROOT_DIR/src/main/native"
NATIVE_LIB="$NATIVE_DIR/build/libsimjot_native.dylib"
APP_MACOS_DIR="$BUILD_DIR/$APP_NAME.app/Contents/app"

if [[ -d "$NATIVE_DIR" ]]; then
    # Build native library if not already built
    NEED_NATIVE_BUILD=false
    if [[ ! -f "$NATIVE_LIB" ]]; then
        NEED_NATIVE_BUILD=true
    elif [[ "$BUILD_UNIVERSAL" == true ]] && ! is_universal_binary "$NATIVE_LIB"; then
        log_warn "Native library is not universal; rebuilding..."
        NEED_NATIVE_BUILD=true
    fi

    if [[ "$NEED_NATIVE_BUILD" == true ]]; then
        log_info "Building native library..."
        if [[ -f "$ROOT_DIR/compile-native.sh" ]]; then
            NATIVE_BUILD_ARGS=(--clean --release)
            if [[ "$BUILD_UNIVERSAL" == true ]]; then
                NATIVE_BUILD_ARGS+=(--arch universal)
            fi
            SIMJOT_MACOS_MIN_VERSION="$MACOS_MIN_VERSION" "$ROOT_DIR/compile-native.sh" "${NATIVE_BUILD_ARGS[@]}"
        else
            mkdir -p "$NATIVE_DIR/build"
            cd "$NATIVE_DIR/build"
            if [[ "$BUILD_UNIVERSAL" == true ]]; then
                MACOSX_DEPLOYMENT_TARGET="$MACOS_MIN_VERSION" cmake .. \
                    -DCMAKE_BUILD_TYPE=Release \
                    -DCMAKE_OSX_DEPLOYMENT_TARGET="$MACOS_MIN_VERSION" \
                    -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"
            else
                MACOSX_DEPLOYMENT_TARGET="$MACOS_MIN_VERSION" cmake .. \
                    -DCMAKE_BUILD_TYPE=Release \
                    -DCMAKE_OSX_DEPLOYMENT_TARGET="$MACOS_MIN_VERSION"
            fi
            make -j$(sysctl -n hw.ncpu)
            cd "$ROOT_DIR"
        fi
    fi
    
    # Bundle into app
    if [[ -f "$NATIVE_LIB" ]]; then
        if [[ "$BUILD_UNIVERSAL" == true ]]; then
            require_universal_binary "$NATIVE_LIB"
            log_ok "Universal native library verified"
        fi
        log_info "Bundling native library into app..."
        mkdir -p "$APP_MACOS_DIR"
        cp "$NATIVE_LIB" "$APP_MACOS_DIR/"
        log_ok "Native library bundled: libsimjot_native.dylib"
    else
        log_warn "Native library not found at $NATIVE_LIB"
    fi
fi

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
    /usr/libexec/PlistBuddy -c "Set :LSMinimumSystemVersion $MACOS_MIN_VERSION" "$PLIST_PATH" 2>/dev/null || \
        /usr/libexec/PlistBuddy -c "Add :LSMinimumSystemVersion string $MACOS_MIN_VERSION" "$PLIST_PATH" 2>/dev/null || true
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

# Create resources directory early for background image check
RESOURCES_DIR="$BUILD_DIR/resources"
mkdir -p "$RESOURCES_DIR"

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
    <options customize="never" require-scripts="false" hostArchitectures="$PKG_ARCHS"/>
    
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
    <p>$APP_NAME is a personalizable journaling and poetry studio with rich editing tools, mood tracking, and native-accelerated performance.</p>
    <p>This installer will guide you through the installation process.</p>
    <p><strong>Features:</strong></p>
    <ul>
        <li>Journal and poetry editors with templates, formatting, and images</li>
        <li>Poetry analysis tools: syllables, rhyme, meter, and forms</li>
        <li>Mood tracking with charts and trends</li>
        <li>Notebook organization, search, and smart autosave</li>
        <li>Custom fonts, themes, and background personalization</li>
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
    <p>You can also search for "$APP_NAME" in Spotlight (⌘ + Space).</p>
    <p><strong>Getting Started:</strong></p>
    <ol>
        <li>Launch $APP_NAME from Applications</li>
        <li>Choose a workspace folder and create your first notebook</li>
        <li>Pick a template or start a blank entry</li>
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
if [[ "$BUILD_UNIVERSAL" == true ]]; then
    echo "  Universal build:  arm64 + x86_64"
fi
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
