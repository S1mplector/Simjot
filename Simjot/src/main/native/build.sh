#!/bin/bash
# Build native library for Panama FFM testing
# Run from this directory: ./build.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building libsimjot_native.dylib..."

# Compile as shared library for macOS
clang -shared -O2 -fPIC -o libsimjot_native.dylib simjot_native.c

echo "Built: $SCRIPT_DIR/libsimjot_native.dylib"
echo ""
echo "To use in Java, set library path:"
echo "  -Djava.library.path=$SCRIPT_DIR"
echo "Or copy to /usr/local/lib"
