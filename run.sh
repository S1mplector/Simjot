#!/bin/bash

# Set the base directory to the script's location
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$BASE_DIR/Simjot/bin"

# Create build directory if it doesn't exist
mkdir -p "$BUILD_DIR"

# Compile the project if needed
if [ "$1" == "--compile" ] || [ "$1" == "-c" ]; then
    echo "Compiling the project..."
    cd "$BASE_DIR/Simjot"
    javac -d bin -sourcepath src src/module-info.java src/main/ui/JournalApp.java
    if [ $? -ne 0 ]; then
        echo "Compilation failed"
        exit 1
    fi
    cd - > /dev/null
fi

# Run the application
echo "Starting Simjot..."
java --module-path "$BUILD_DIR" -m Simjot/main.ui.JournalApp

# Check if the command was successful
if [ $? -ne 0 ]; then
    echo "\nIf you see module-related errors, try running with --compile to rebuild the project:"
    echo "  ./run.sh --compile"
fi
