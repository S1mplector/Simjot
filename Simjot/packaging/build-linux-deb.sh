#!/usr/bin/env bash
#
# Simjot Linux .deb Builder
# Creates a Debian package using jpackage + a custom runtime image.
#
# Usage: ./build-linux-deb.sh [--clean] [--tests] [--verbose]
#
set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================
APP_NAME="Simjot"
PACKAGE_NAME="simjot"
VENDOR="S1mplector"
DESCRIPTION="Simjot Journal Application"

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/linux-deb"
RUNTIME_DIR="$BUILD_DIR/runtime"
DIST_DIR="$ROOT_DIR/dist"
ICON_PNG="$ROOT_DIR/src/main/resources/img/icons/original/simjot.png"

# ============================================================================
# Helper Functions
# ============================================================================
log_info()  { echo "[INFO] $*"; }
log_ok()    { echo "[OK]   $*"; }
log_warn()  { echo "[WARN] $*"; }
log_error() { echo "[ERROR] $*" >&2; }

check_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log_error "Required command not found: $1"
        exit 1
    fi
}

get_java_major() {
    java -version 2>&1 | awk -F\" '/version/ {print $2}' | cut -d. -f1
}

get_app_version() {
    local app_info="$ROOT_DIR/src/main/core/AppInfo.java"
    if [[ -f "$app_info" ]]; then
        sed -n 's/.*VERSION = \"\([^\"]\+\)\".*/\1/p' "$app_info" | head -n 1
    fi
}

# ============================================================================
# Parse Arguments
# ============================================================================
CLEAN_BUILD=false
VERBOSE=false
RUN_TESTS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)   CLEAN_BUILD=true; shift ;;
        --verbose) VERBOSE=true; shift ;;
        --tests)   RUN_TESTS=true; shift ;;
        -h|--help)
            echo "Usage: $0 [--clean] [--tests] [--verbose]"
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
if [[ "$(uname -s)" != "Linux" ]]; then
    log_error "This script must be run on Linux."
    exit 1
fi

check_command java
check_command mvn
check_command jpackage
check_command jdeps
check_command jlink
check_command dpkg-deb
check_command fakeroot

JAVA_VERSION="$(get_java_major)"
if [[ -z "$JAVA_VERSION" || "$JAVA_VERSION" -lt 17 ]]; then
    log_error "Java 17+ is required (found: ${JAVA_VERSION:-unknown})."
    exit 1
fi

# Determine versions
MVN_VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null || true)"
if [[ -z "$MVN_VERSION" ]]; then
    log_error "Could not determine Maven version from pom.xml."
    exit 1
fi

APP_VERSION="$(get_app_version)"
if [[ -z "$APP_VERSION" ]]; then
    APP_VERSION="$MVN_VERSION"
fi

# Maintainer metadata for Debian
if [[ -n "${SIMJOT_DEB_MAINTAINER:-}" ]]; then
    MAINTAINER="$SIMJOT_DEB_MAINTAINER"
else
    NAME="${DEBFULLNAME:-${USER:-Simjot Maintainer}}"
    EMAIL="${DEBEMAIL:-${USER:-simjot}@localhost}"
    MAINTAINER="${NAME} <${EMAIL}>"
fi

log_info "Building $APP_NAME v$APP_VERSION (jar v$MVN_VERSION)"

# ============================================================================
# Setup
# ============================================================================
if [[ "$CLEAN_BUILD" == true ]]; then
    log_info "Cleaning build artifacts..."
    rm -rf "$BUILD_DIR"
    rm -f "$DIST_DIR"/${APP_NAME}-*.deb
fi

mkdir -p "$BUILD_DIR" "$DIST_DIR"

# ============================================================================
# Step 1: Build the shaded JAR
# ============================================================================
log_info "Building application JAR..."
if [[ "$RUN_TESTS" == true ]]; then
    mvn clean package -q
else
    mvn -DskipTests clean package -q
fi

SHADED_JAR="${APP_NAME}-${MVN_VERSION}.jar"
JAR_PATH="$ROOT_DIR/target/$SHADED_JAR"
if [[ ! -f "$JAR_PATH" ]]; then
    log_error "JAR not found: $JAR_PATH"
    exit 1
fi
log_ok "JAR built: $SHADED_JAR"

# ============================================================================
# Step 2: Create Custom Runtime (jlink)
# ============================================================================
log_info "Creating optimized Java runtime..."
rm -rf "$RUNTIME_DIR"

MODULES="$(jdeps --multi-release "$JAVA_VERSION" --ignore-missing-deps --print-module-deps "$JAR_PATH" 2>/dev/null | grep -v "^Warning:" | tail -1 || true)"
if [[ -z "$MODULES" ]]; then
    MODULES="java.base,java.desktop,java.logging,java.prefs,java.sql,java.xml"
fi
EXTRA_MODULES="jdk.unsupported,java.naming,java.management"
MODULES="$MODULES,$EXTRA_MODULES"

log_info "  Required modules: $MODULES"
jlink \
    --add-modules "$MODULES" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output "$RUNTIME_DIR"

log_ok "Runtime created at $RUNTIME_DIR"

# ============================================================================
# Step 3: Build the .deb package (jpackage)
# ============================================================================
log_inrfo "Creating .deb package..."

JPKG_ARGS=(
    --type deb
    --name "$APP_NAME"
    --app-version "$APP_VERSION"
    --vendor "$VENDOR"
    --dest "$DIST_DIR"
    --input "$ROOT_DIR/target"
    --main-jar "$SHADED_JAR"
    --main-class "main.ui.app.JournalApp"
    --runtime-image "$RUNTIME_DIR"
    --description "$DESCRIPTION"
    --linux-package-name "$PACKAGE_NAME"
    --linux-shortcut
    --linux-menu-group "Office"
    --linux-deb-maintainer "$MAINTAINER"
    --java-options "-Xmx1G"
)

if [[ -f "$ICON_PNG" ]]; then
    JPKG_ARGS+=(--icon "$ICON_PNG")
else
    log_warn "Icon not found, using default: $ICON_PNG"
fi

if [[ "$VERBOSE" == true ]]; then
    JPKG_ARGS+=(--verbose)
fi

jpackage "${JPKG_ARGS[@]}"

log_ok "Deb package created in: $DIST_DIR"
