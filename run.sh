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
    find src -name "*.java" > sources.txt
    javac -d bin @sources.txt
    if [ $? -ne 0 ]; then
        echo "Compilation failed"
        exit 1
    fi
    cd - > /dev/null
fi

# Function to read UI scale from preferences
get_ui_scale() {
    # First, find the Simjot root folder from the config file
    local config_file="$HOME/.simjournal_config.txt"
    if [ ! -f "$config_file" ]; then
        echo "1.0"
        return 0
    fi

    # Read the root folder path
    local root_folder=$(cat "$config_file")
    if [ -z "$root_folder" ]; then
        echo "1.0"
        return 0
    fi

    # Look for the preferences file in the settings subdirectory
    local prefs_file="$root_folder/settings/preferences.properties"
    if [ -f "$prefs_file" ]; then
        local scale=$(grep "^uiScale=" "$prefs_file" | cut -d'=' -f2)
        if [ ! -z "$scale" ]; then
            echo "$scale"
            return 0
        fi
    fi
    echo "1.0"
}

# Run the application
echo "Starting Simjot..."

# Debug information
config_file="$HOME/.simjournal_config.txt"
if [ -f "$config_file" ]; then
    echo "Found config file at: $config_file"
    root_folder=$(cat "$config_file")
    echo "Root folder: $root_folder"
    if [ -d "$root_folder/settings" ]; then
        echo "Settings folder exists"
        if [ -f "$root_folder/settings/preferences.properties" ]; then
            echo "Found preferences file"
            echo "Contents of preferences file:"
            cat "$root_folder/settings/preferences.properties"
        else
            echo "No preferences file found"
        fi
    else
        echo "No settings folder found"
    fi
else
    echo "No config file found at: $config_file"
fi

ui_scale=$(get_ui_scale)
echo "Using UI scale: $ui_scale"
java -Dsun.java2d.uiScale=$ui_scale --module-path "$BUILD_DIR" -m Simjot/main.ui.JournalApp

# Check if the command was successful
if [ $? -ne 0 ]; then
    echo "\nIf you see module-related errors, try running with --compile to rebuild the project:"
    echo "  ./run.sh --compile"
fi
