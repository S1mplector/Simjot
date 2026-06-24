#!/usr/bin/env bash
set -euo pipefail

# Resolve project root (this script lives in packaging/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# Ensure Maven is available
if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: Maven (mvn) is required on PATH" >&2
  exit 1
fi

# Determine project version from pom.xml
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR_NAME="Simjot-${VERSION}.jar"
JAR_PATH="target/${JAR_NAME}"
DIST_DIR="dist/Simjot-${VERSION}"

# Build shaded JAR
mvn -DskipTests clean package

# Assemble distribution folder
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"
cp "$JAR_PATH" "$DIST_DIR/"

# Create launchers
cat >"$DIST_DIR/simjot" <<EOF
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
JAVA_OPTS="${JAVA_OPTS:-}"
exec java $JAVA_OPTS -Xmx1G -jar "$HERE/${JAR_NAME}" "$@"
EOF
chmod +x "$DIST_DIR/simjot"

cat >"$DIST_DIR/simjot.bat" <<EOF
@echo off
setlocal enabledelayedexpansion
set HERE=%~dp0
set JAVA_OPTS=%JAVA_OPTS%
java %JAVA_OPTS% -Xmx1G -jar "%HERE%${JAR_NAME}" %*
endlocal
EOF

# Summary
cat <<MSG
Built distribution:
  $DIST_DIR/
Run:
  On macOS/Linux:  $DIST_DIR/simjot
  On Windows:      %CD%\\$DIST_DIR\\simjot.bat
MSG
