#!/usr/bin/env bash
set -euo pipefail

# Resolve project root (this script lives in packaging/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Error: $1 is required on PATH" >&2; exit 1; }
}

# Tools required
need mvn
need jdeps
need jlink
need jpackage

# Determine version and shaded jar name
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
SHADED_JAR="Simjot-${VERSION}.jar"
JAR_PATH="target/${SHADED_JAR}"
RUNTIME_DIR="build/runtime-macos"
DEST_DIR="dist"
APP_NAME="Simjot"

# Always build shaded JAR to capture resource changes
echo "Building shaded JAR..."
mvn -DskipTests clean package

# Compute required modules for a minimal runtime
echo "Computing required modules with jdeps..."
MODULES="$(jdeps --multi-release 17 --ignore-missing-deps --print-module-deps "$JAR_PATH")"
# Ensure jdk.unsupported is present for some Swing/AWT internals used by third-party libs
if [[ "$MODULES" != *"jdk.unsupported"* ]]; then
  MODULES+="${MODULES:+,}jdk.unsupported"
fi

# Create trimmed runtime
echo "Creating trimmed runtime with jlink..."
rm -rf "$RUNTIME_DIR"
jlink --add-modules "$MODULES" \
      --strip-debug --no-header-files --no-man-pages --compress=2 \
      --output "$RUNTIME_DIR"

# Pick an icon if available
ICON_FLAG=()
if [[ -f "packaging/app.icns" ]]; then
  ICON_FLAG=(--icon "packaging/app.icns")
elif [[ -f "resources/icons/app.icns" ]]; then
  ICON_FLAG=(--icon "resources/icons/app.icns")
fi

# Create DMG with jpackage
mkdir -p "$DEST_DIR"
echo "Packaging ${APP_NAME} ${VERSION} DMG..."
JPKG_CMD=(
  jpackage
  --type dmg
  --name "$APP_NAME"
  --app-version "$VERSION"
  --dest "$DEST_DIR"
  --input target
  --main-jar "$SHADED_JAR"
  --runtime-image "$RUNTIME_DIR"
  --java-options "-Xmx1G"
)
if (( ${#ICON_FLAG[@]} )); then
  JPKG_CMD+=("${ICON_FLAG[@]}")
fi
"${JPKG_CMD[@]}"

DMG_PATH=$(ls -1t "$DEST_DIR"/*.dmg | head -n 1 || true)
echo "\nDone. DMG: ${DMG_PATH:-not generated}"
echo "Tip: You can codesign/notarize later if you plan to distribute widely."
