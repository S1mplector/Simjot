#!/usr/bin/env bash
set -euo pipefail

# Minimal JUnit 5 test runner for Simjot without changing the custom build.
# - Compiles main sources into build/classes
# - Compiles tests from Simjot/tests into build/test-classes
# - Downloads junit-platform-console-standalone if missing
# - Executes tests discovered on the classpath

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build"
LIB_DIR="$BUILD_DIR/libs"
MAIN_CLASSES="$BUILD_DIR/classes"
TEST_CLASSES="$BUILD_DIR/test-classes"
MAIN_SOURCES_LIST="$BUILD_DIR/main-sources.txt"
TEST_SOURCES_LIST="$BUILD_DIR/test-sources.txt"

JUNIT_VERSION="1.10.2"
JUNIT_JAR="$LIB_DIR/junit-platform-console-standalone-$JUNIT_VERSION.jar"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"

mkdir -p "$LIB_DIR" "$MAIN_CLASSES" "$TEST_CLASSES"

# 1) Ensure JUnit ConsoleLauncher jar is present
if [[ ! -f "$JUNIT_JAR" ]]; then
  echo "Downloading JUnit ConsoleLauncher $JUNIT_VERSION ..."
  curl -fsSL -o "$JUNIT_JAR" "$JUNIT_URL"
fi

# 2) Build list of main sources (exclude module-info.java)
# We respect the project layout: Simjot/src/main/**/*.java
# Also support any additional sources if present.
: > "$MAIN_SOURCES_LIST"
while IFS= read -r -d '' f; do
  [[ "$(basename "$f")" == "module-info.java" ]] && continue
  echo "$f" >> "$MAIN_SOURCES_LIST"
done < <(find "$ROOT_DIR/Simjot/src/main" -type f -name '*.java' -print0)

# 3) Compile main sources
if [[ -s "$MAIN_SOURCES_LIST" ]]; then
  echo "Compiling main sources ..."
  javac -encoding UTF-8 -g -d "$MAIN_CLASSES" @"$MAIN_SOURCES_LIST"
else
  echo "No main sources found under Simjot/src/main"
fi

# 4) Build list of test sources: Simjot/tests/**/*.java
: > "$TEST_SOURCES_LIST"
if [[ -d "$ROOT_DIR/Simjot/tests" ]]; then
  while IFS= read -r -d '' f; do
    echo "$f" >> "$TEST_SOURCES_LIST"
  done < <(find "$ROOT_DIR/Simjot/tests" -type f -name '*Test.java' -print0)
fi

# 5) Compile tests
if [[ -s "$TEST_SOURCES_LIST" ]]; then
  echo "Compiling test sources ..."
  javac -encoding UTF-8 -g \
    -cp "$JUNIT_JAR:$MAIN_CLASSES" \
    -d "$TEST_CLASSES" @"$TEST_SOURCES_LIST"
else
  echo "No tests found under Simjot/tests"
fi

# 6) Run tests via ConsoleLauncher
echo "Running tests ..."
java -jar "$JUNIT_JAR" \
  --class-path "$MAIN_CLASSES:$TEST_CLASSES" \
  --scan-class-path \
  --fail-if-no-tests
