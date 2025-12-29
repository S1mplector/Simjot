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

# Prefer bundled .icns icon; fallback to generating from PNG if needed.
ICON_ICNS_SRC="src/main/resources/img/icons/original/simjot.icns"
ICON_PNG="src/main/resources/img/icons/original/simjot.png"
ICON_ICNS="packaging/app.icns"
ICON_FLAG=()

if [[ -f "$ICON_ICNS_SRC" ]]; then
  ICON_FLAG=(--icon "$ICON_ICNS_SRC")
elif [[ -f "$ICON_PNG" ]]; then
  echo "Converting $ICON_PNG to $ICON_ICNS..."
  ICONSET_DIR="packaging/app.iconset"
  rm -rf "$ICONSET_DIR"
  mkdir -p "$ICONSET_DIR"

  # Generate all required icon sizes for macOS
  sips -z 16 16     "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
  sips -z 32 32     "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
  sips -z 32 32     "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
  sips -z 64 64     "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
  sips -z 128 128   "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
  sips -z 256 256   "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
  sips -z 256 256   "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
  sips -z 512 512   "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
  sips -z 512 512   "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
  sips -z 1024 1024 "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null

  # Convert iconset to icns
  iconutil -c icns "$ICONSET_DIR" -o "$ICON_ICNS"
  rm -rf "$ICONSET_DIR"
  echo "Generated $ICON_ICNS"

  if [[ -f "$ICON_ICNS" ]]; then
    ICON_FLAG=(--icon "$ICON_ICNS")
  fi
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
