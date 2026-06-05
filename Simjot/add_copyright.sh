#!/bin/bash

# Script to add/update copyright headers to source files
# Supports: Java, C, C++, Objective-C++, Haskell
# Usage: ./add_copyright.sh
#
# This script will:
# - Replace old copyright headers with new No Derivatives header
# - Add header to files without one

# No Derivatives License header for C-style comments (Java, C, C++, Objective-C++)
C_HEADER="/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */"

# No Derivatives License header for Haskell
HS_HEADER="{-
 - SIMJOT - No Derivatives License
 - 
 - Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 - 
 - See LICENSE for full terms.
 -}"

# Check if file already has the new No Derivatives header
has_new_header() {
    local file="$1"
    head -n 10 "$file" | grep -q "SIMJOT - No Derivatives License"
}

# Remove old C-style copyright block (/* ... */ at start of file)
strip_old_c_header() {
    local file="$1"
    local temp_file=$(mktemp)
    
    # Use awk to skip the first comment block if it contains Copyright
    awk '
    BEGIN { in_header = 0; found_copyright = 0; done = 0 }
    {
        if (done) { print; next }
        
        # First line - check if it starts a comment
        if (NR == 1 && /^\/\*/) {
            in_header = 1
            buffer = $0 "\n"
            next
        }
        
        # Inside header comment
        if (in_header) {
            buffer = buffer $0 "\n"
            if (/Copyright/) { found_copyright = 1 }
            if (/\*\//) {
                in_header = 0
                if (found_copyright) {
                    # Skip the header (dont print buffer)
                    # Also skip blank line after header
                    done = 1
                    getline  # consume potential blank line
                    if ($0 != "") print  # if not blank, print it
                } else {
                    # Not a copyright header, print it
                    printf "%s", buffer
                    done = 1
                }
            }
            next
        }
        
        # Not in header
        print
        done = 1
    }
    ' "$file" > "$temp_file"
    
    mv "$temp_file" "$file"
}

# Remove old Haskell-style copyright block ({- ... -} at start of file)
strip_old_hs_header() {
    local file="$1"
    local temp_file=$(mktemp)
    
    awk '
    BEGIN { in_header = 0; found_copyright = 0; done = 0 }
    {
        if (done) { print; next }
        
        if (NR == 1 && /^\{-/) {
            in_header = 1
            buffer = $0 "\n"
            next
        }
        
        if (in_header) {
            buffer = buffer $0 "\n"
            if (/Copyright/) { found_copyright = 1 }
            if (/-\}/) {
                in_header = 0
                if (found_copyright) {
                    done = 1
                    getline
                    if ($0 != "") print
                } else {
                    printf "%s", buffer
                    done = 1
                }
            }
            next
        }
        
        print
        done = 1
    }
    ' "$file" > "$temp_file"
    
    mv "$temp_file" "$file"
}

update_c_header() {
    local file="$1"
    
    # Already has new header - skip
    if has_new_header "$file"; then
        echo "Skipping $file (already has No Derivatives header)"
        return
    fi
    
    # Check if has old copyright to remove
    if head -n 15 "$file" | grep -q "Copyright"; then
        echo "Updating $file (replacing old header)"
        strip_old_c_header "$file"
    else
        echo "Adding header to $file"
    fi
    
    # Add new header
    temp_file=$(mktemp)
    echo "$C_HEADER" > "$temp_file"
    echo "" >> "$temp_file"
    cat "$file" >> "$temp_file"
    mv "$temp_file" "$file"
}

update_hs_header() {
    local file="$1"
    
    if has_new_header "$file"; then
        echo "Skipping $file (already has No Derivatives header)"
        return
    fi
    
    if head -n 15 "$file" | grep -q "Copyright"; then
        echo "Updating $file (replacing old header)"
        strip_old_hs_header "$file"
    else
        echo "Adding header to $file"
    fi
    
    temp_file=$(mktemp)
    echo "$HS_HEADER" > "$temp_file"
    echo "" >> "$temp_file"
    cat "$file" >> "$temp_file"
    mv "$temp_file" "$file"
}

# Find and process Java files
find src -path "src/main/native/build" -prune -o -name "*.java" -print | while read file; do
    update_c_header "$file"
done

# Find and process C files
find src -path "src/main/native/build" -prune -o \( -name "*.c" -o -name "*.h" \) -print | while read file; do
    update_c_header "$file"
done

# Find and process C++ / Objective-C++ files
find src -path "src/main/native/build" -prune -o \( -name "*.cpp" -o -name "*.hpp" -o -name "*.cc" -o -name "*.hh" -o -name "*.mm" \) -print | while read file; do
    update_c_header "$file"
done

# Find and process Haskell files
find src -path "src/main/native/build" -prune -o -name "*.hs" -print | while read file; do
    update_hs_header "$file"
done

echo "Done!"
