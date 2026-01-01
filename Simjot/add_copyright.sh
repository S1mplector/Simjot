#!/bin/bash

# Script to add copyright headers to Java files
# Usage: ./add_copyright.sh

# Define copyright headers
MAIN_HEADER="/*
 * SIMJOT 
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the MIT License.
 * 
 * See LICENSE.md for full terms.
 */"

# Find all Java files
find src -name "*.java" | while read file; do
    # Skip if already has copyright header
    if head -n 5 "$file" | grep -q "Copyright"; then
        echo "Skipping $file (already has copyright)"
        continue
    fi
    
    if
        HEADER="$MAIN_HEADER"
    fi
    
    # Create temporary file with header
    temp_file=$(mktemp)
    echo "$HEADER" > "$temp_file"
    echo "" >> "$temp_file"
    cat "$file" >> "$temp_file"
    
    # Replace original file
    mv "$temp_file" "$file"
    echo "Added header to $file"
done

echo "Done!"
