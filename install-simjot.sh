#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Create icon
echo "Creating application icon..."
chmod +x "$SCRIPT_DIR/create-icon.sh"
"$SCRIPT_DIR/create-icon.sh"

# Update the desktop entry with the correct paths
sed -i "s|/home/sim/Desktop/Files/Projects/Simjot|$SCRIPT_DIR|g" "$SCRIPT_DIR/simjot.desktop"

# Create applications directory if it doesn't exist
mkdir -p ~/.local/share/applications

# Copy the desktop entry to the applications directory
echo "Installing desktop entry..."
cp "$SCRIPT_DIR/simjot.desktop" ~/.local/share/applications/

# Make the run script executable
chmod +x "$SCRIPT_DIR/run.sh"

echo "Installation complete!"
echo "Simjot should now appear in your applications menu."
echo "You may need to log out and back in for the icon to appear."