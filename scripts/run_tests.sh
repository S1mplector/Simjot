#!/usr/bin/env bash
set -euo pipefail

# Lightweight test runner for Simjot using JUnit 5 ConsoleLauncher
# Requirements:
# - JDK 17+
# - curl
#
# It compiles main sources (excluding module-info.java) and tests onto the classpath,
# then invokes the JUnit ConsoleLauncher.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MAIN_SRC="$ROOT_DIR/Simjot/src"
TEST_SRC="$ROOT_DIR/Simjot/tests"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
TEST_CLASSES_DIR="$BUILD_DIR/test-classes"
LIB_DIR="$BUILD_DIR/libs"
JUNIT_VERSION="1.10.2"
JUNIT_JAR="$LIB_DIR/junit-platform-console-standalone-$JUNIT_VERSION.jar"

# Ensure folders
mkdir -p "$CLASSES_DIR" "$TEST_CLASSES_DIR" "$LIB_DIR"

# Fetch JUnit ConsoleLauncher if missing
if [[ ! -f "$JUNIT_JAR" ]]; then
  echo "Downloading JUnit ConsoleLauncher $JUNIT_VERSION..."
  curl -L -o "$JUNIT_JAR" "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
fi

# Collect main sources (exclude module-info.java to keep classpath compilation simple)
MAIN_LIST_FILE="$BUILD_DIR/main-sources.txt"
find "$MAIN_SRC" -type f -name "*.java" ! -name "module-info.java" > "$MAIN_LIST_FILE"

if [[ ! -s "$MAIN_LIST_FILE" ]]; then
  echo "No main sources found under $MAIN_SRC" >&2
  exit 1
fi

# Compile main sources to classes dir
javac \
  --release 17 \
  -d "$CLASSES_DIR" \
  @"$MAIN_LIST_FILE"

# Collect test sources
if [[ ! -d "$TEST_SRC" ]]; then
  echo "No tests found (missing $TEST_SRC)." >&2
  exit 1
fi
TEST_LIST_FILE="$BUILD_DIR/test-sources.txt"
find "$TEST_SRC" -type f -name "*.java" > "$TEST_LIST_FILE"

if [[ ! -s "$TEST_LIST_FILE" ]]; then
  echo "No test sources found under $TEST_SRC" >&2
  exit 1
fi

# Compile tests against main classes and JUnit
javac \
  --release 17 \
  -cp "$CLASSES_DIR:$JUNIT_JAR" \
  -d "$TEST_CLASSES_DIR" \
  @"$TEST_LIST_FILE"

# Run tests
java \
  -jar "$JUNIT_JAR" \
  --classpath "$CLASSES_DIR:$TEST_CLASSES_DIR" \
  --scan-class-path
