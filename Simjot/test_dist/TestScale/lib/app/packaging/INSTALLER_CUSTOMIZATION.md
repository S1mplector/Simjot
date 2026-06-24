# macOS Installer Customization Guide

This guide shows you how to customize the appearance and content of the Simjot `.pkg` installer.

## 1. Background Image

Add a custom background image to the installer window.

**Steps:**
1. Create an image file (recommended: 1200x800px PNG or JPG)
2. Save it as `installer-background.png` or `installer-background.jpg` in the `packaging/` directory
3. Run the build script - it will automatically detect and use the image

**Supported formats:** PNG, JPG  
**Recommended size:** 1200x800px  
**Alignment options:** You can edit the script to change `alignment` (topleft, topright, bottomleft, bottomright, center) and `scaling` (tofit, proportional, none)

**Example:**
```bash
# Place your image
cp ~/my-background.png packaging/installer-background.png

# Build with background
cd packaging
./build-macos-pkg.sh --desktop
```

---

## 2. Welcome & Conclusion Pages

The installer shows HTML pages at the start and end. These are auto-generated but you can customize them.

### Welcome Page

Edit the welcome page content in the build script at line ~365:

**Current content:**
- App name and version
- Brief description
- Feature list

**To customize:**
1. Open `build-macos-pkg.sh`
2. Find the `cat > "$RESOURCES_DIR/welcome.html"` section
3. Edit the HTML content
4. You can add:
   - Custom styling (CSS in `<style>` tag)
   - Images (place in `resources/` and reference)
   - Links to documentation
   - System requirements

**Example additions:**
```html
<h2>System Requirements</h2>
<ul>
    <li>macOS 10.14 or later</li>
    <li>100 MB free disk space</li>
    <li>Java 17+ (included)</li>
</ul>
```

### Conclusion Page

Similar to welcome page, found at line ~380 in the script.

**Current content:**
- Success message
- Installation location
- Getting started steps

---

## 3. App Icon Color

Customize the app icon color during build:

```bash
# Preset colors
./build-macos-pkg.sh --color green
./build-macos-pkg.sh --color purple
./build-macos-pkg.sh --color red

# Custom hex color
./build-macos-pkg.sh --color "#FF5500"
```

**Available presets:**
- `blue` (default) - Windows 7 style blue
- `green` - Fresh, natural
- `purple` - Creative, unique
- `red` - Bold, energetic
- `orange` - Warm, friendly
- `teal` - Modern, professional
- `pink` - Playful, distinctive
- `gold` - Premium, elegant

---

## 4. License Agreement

Add a license agreement that users must accept:

**Steps:**
1. Create `LICENSE.txt` or `LICENSE.rtf` in `packaging/`
2. Add this to the distribution XML in the script (after line 343):

```xml
<license file="LICENSE.txt" mime-type="text/plain"/>
```

Or for RTF (with formatting):
```xml
<license file="LICENSE.rtf" mime-type="application/rtf"/>
```

3. Copy the file to resources in the script:
```bash
cp "$SCRIPT_DIR/LICENSE.txt" "$RESOURCES_DIR/"
```

---

## 5. README Display

Show a README before installation:

**Steps:**
1. Create `README.html` in `packaging/`
2. Add to distribution XML:

```xml
<readme file="README.html" mime-type="text/html"/>
```

3. Copy to resources:
```bash
cp "$SCRIPT_DIR/README.html" "$RESOURCES_DIR/"
```

---

## 6. Custom Installation Options

Allow users to choose what to install:

**Example: Optional components**

Edit the distribution XML to add choices:

```xml
<choices-outline>
    <line choice="default">
        <line choice="app"/>
        <line choice="docs"/>
        <line choice="examples"/>
    </line>
</choices-outline>

<choice id="app" title="Simjot Application" description="The main application">
    <pkg-ref id="com.s1mplector.simjot"/>
</choice>

<choice id="docs" title="Documentation" description="User guide and tutorials" start_selected="true">
    <pkg-ref id="com.s1mplector.simjot.docs"/>
</choice>

<choice id="examples" title="Example Journals" description="Sample journal entries" start_selected="false">
    <pkg-ref id="com.s1mplector.simjot.examples"/>
</choice>
```

---

## 7. Installer Window Title

The window title is set in the distribution XML:

```xml
<title>Simjot Installer</title>
```

Change `$APP_NAME` to a custom string in the script if you want a different title.

---

## 8. Advanced: Custom Scripts

Run scripts before/after installation:

**Pre-install script:**
```xml
<installation-check script="preinstall.js"/>
```

**Post-install script:**
```xml
<postinstall file="postinstall.sh"/>
```

Place scripts in `resources/` and reference them in the XML.

---

## 9. Styling Tips

### Modern Dark Theme
Add to welcome.html CSS:
```css
body {
    background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
    color: white;
}
h1 { color: #5AA0E6; }
```

### Gradient Background
```css
body {
    background: linear-gradient(to bottom, #f0f4f8, #d9e2ec);
}
```

### Custom Fonts
```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap');
body { font-family: 'Inter', -apple-system, sans-serif; }
```

---

## 10. Testing Your Customizations

After making changes:

```bash
# Clean build to see all changes
./build-macos-pkg.sh --clean --desktop

# Test the installer
open ~/Desktop/Simjot-1.0.0.pkg
```

**Preview without installing:**
```bash
# Extract and view
pkgutil --expand Simjot-1.0.0.pkg extracted/
open extracted/Distribution
```

---

## Quick Reference

| Customization | File/Location | Difficulty |
|---------------|---------------|------------|
| Background image | `packaging/installer-background.png` | Easy |
| Icon color | `--color` flag | Easy |
| Welcome text | `build-macos-pkg.sh` line ~365 | Easy |
| Conclusion text | `build-macos-pkg.sh` line ~380 | Easy |
| License | `packaging/LICENSE.txt` + XML edit | Medium |
| README | `packaging/README.html` + XML edit | Medium |
| Custom options | Distribution XML | Advanced |
| Scripts | Shell/JS scripts + XML | Advanced |

---

## Resources

- [Apple Installer Documentation](https://developer.apple.com/library/archive/documentation/DeveloperTools/Reference/DistributionDefinitionRef/)
- [pkgbuild man page](https://ss64.com/osx/pkgbuild.html)
- [productbuild man page](https://ss64.com/osx/productbuild.html)
