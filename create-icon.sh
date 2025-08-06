#!/bin/bash

# Create a simple SVG icon for Simjot
cat > simjot-icon.svg << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<svg width="128" height="128" viewBox="0 0 128 128" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#4a90e2;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#357abd;stop-opacity:1" />
    </linearGradient>
  </defs>
  
  <!-- Background circle -->
  <circle cx="64" cy="64" r="60" fill="url(#grad1)" stroke="#2c5aa0" stroke-width="2"/>
  
  <!-- Feather/pen icon -->
  <path d="M35 85 L45 75 L75 45 L85 35 L90 40 L80 50 L50 80 L40 90 Z" fill="white" opacity="0.9"/>
  <path d="M75 45 L85 35 L90 40 L80 50 Z" fill="#f0f0f0"/>
  
  <!-- Paper lines -->
  <line x1="25" y1="95" x2="70" y2="95" stroke="white" stroke-width="2" opacity="0.7"/>
  <line x1="25" y1="100" x2="65" y2="100" stroke="white" stroke-width="2" opacity="0.7"/>
  <line x1="25" y1="105" x2="60" y2="105" stroke="white" stroke-width="2" opacity="0.7"/>
  
  <!-- Decorative dots -->
  <circle cx="95" cy="25" r="3" fill="white" opacity="0.6"/>
  <circle cx="105" cy="35" r="2" fill="white" opacity="0.4"/>
  <circle cx="25" cy="25" r="2" fill="white" opacity="0.5"/>
</svg>
EOF

# Convert SVG to PNG using ImageMagick (if available) or rsvg-convert
if command -v convert >/dev/null 2>&1; then
    convert simjot-icon.svg -resize 128x128 simjot-icon.png
    echo "Icon created using ImageMagick"
elif command -v rsvg-convert >/dev/null 2>&1; then
    rsvg-convert -w 128 -h 128 simjot-icon.svg -o simjot-icon.png
    echo "Icon created using rsvg-convert"
else
    echo "Neither ImageMagick nor rsvg-convert found. Please install one of them to create the PNG icon."
    echo "You can install ImageMagick with: sudo apt install imagemagick"
    echo "Or install rsvg-convert with: sudo apt install librsvg2-bin"
    exit 1
fi

# Clean up SVG file
rm simjot-icon.svg
echo "Icon saved as simjot-icon.png"