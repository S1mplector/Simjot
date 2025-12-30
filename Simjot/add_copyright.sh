#!/bin/bash

# Script to add copyright headers to Java files
# Usage: ./add_copyright.sh

# Define copyright headers
MAIN_HEADER="/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */"

CRYPTO_HEADER="/*
 * SIMJOT CRYPTOGRAPHIC ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024 Simjot / S1mplector. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Cryptographic Engine License.
 * You may inspect this code for educational and security research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */"

POETRY_HEADER="/*
 * SIMJOT POETRY ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Poetry Engine Proprietary License.
 * You may inspect this code for educational and research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */"

# Find all Java files
find src -name "*.java" | while read file; do
    # Skip if already has copyright header
    if head -n 5 "$file" | grep -q "Copyright"; then
        echo "Skipping $file (already has copyright)"
        continue
    fi
    
    # Determine which header to use based on path
    if [[ "$file" == *"/crypto/"* ]]; then
        HEADER="$CRYPTO_HEADER"
    elif [[ "$file" == *"/poetry/"* ]]; then
        HEADER="$POETRY_HEADER"
    else
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
