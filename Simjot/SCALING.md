# UI Scaling System

Simjot now includes an advanced, system-agnostic UI scaling system that automatically detects and applies appropriate scaling across all platforms.

## How It Works

The `UIScalingManager` uses multiple detection methods to determine the correct scale factor:

### Detection Methods (in order of priority)

1. **User Preference** - If you've set a custom UI scale in Settings, that takes precedence
2. **JVM Properties** - Checks `sun.java2d.uiScale` if set by launcher
3. **Linux Environment Variables**:
   - `GDK_SCALE` (GNOME/GTK integer scaling)
   - `GDK_DPI_SCALE` (GNOME/GTK fractional scaling)
   - `QT_SCALE_FACTOR` (KDE/Qt scaling)
4. **Toolkit DPI** - Queries AWT Toolkit for screen DPI (DPI/96 = scale)
5. **GraphicsConfiguration** - Reads the default transform matrix (most reliable on modern JDKs)

### What Gets Scaled

- All UI fonts (buttons, labels, menus, etc.)
- Component margins and padding
- Scrollbar width
- Split pane dividers
- Tree and table row heights
- Journal entry font size

## For Users

### Setting Custom Scale

1. Open **Settings** → **General**
2. Adjust **UI Scale** slider (0.5x to 3.0x)
3. Changes apply **immediately** - no restart needed!

### Troubleshooting on Linux/Mabox

If automatic detection doesn't work:

**Option 1: Set environment variable before launching**
```bash
export GDK_SCALE=2  # For 2x scaling
java -jar Simjot.jar
```

**Option 2: Set JVM property**
```bash
java -Dsun.java2d.uiScale=2.0 -jar Simjot.jar
```

**Option 3: Use the in-app setting**
- Settings → General → UI Scale → 2.0
- Restart the app

### Checking What Was Detected

When you launch Simjot, check the console output:
```
[UIScaling] Detected scale: 2.0x
[UIScaling] Applying 2.0x scale to Swing components
```

## For Developers

### Using the Scaling Manager

```java
// Initialize early (before any Swing components)
UIScalingManager.initializeEarly();

// Apply to Swing after setting Look & Feel
UIScalingManager.applyToSwing(userScaleOverride);

// Scale individual values
int scaledWidth = UIScalingManager.scale(100);  // Uses detected scale
int scaledHeight = UIScalingManager.scale(50, 2.0f);  // Uses specific scale

// Scale fonts
Font scaledFont = UIScalingManager.scaleFont(baseFont);

// Create scaled dimensions
Dimension dim = UIScalingManager.scaleDimension(800, 600);
```

### Architecture

- **Early Initialization**: Sets JVM properties before Swing EDT starts
- **Multi-Method Detection**: Combines multiple detection strategies for reliability
- **Platform Agnostic**: Works on Windows, Linux (X11/Wayland), and macOS
- **User Override**: Respects user preferences when set

## Platform-Specific Notes

### Linux (Mabox, GNOME, KDE, etc.)
- Detects `GDK_SCALE`, `GDK_DPI_SCALE`, and `QT_SCALE_FACTOR`
- Falls back to GraphicsConfiguration transform
- Works with both X11 and Wayland

### Windows
- Uses GraphicsConfiguration (DPI-aware)
- Respects Windows display scaling settings

### macOS
- Uses GraphicsConfiguration for Retina displays
- Automatically handles 2x scaling on Retina screens

## Technical Details

The system sets these JVM properties early:
- `sun.java2d.uiScale.enabled=true`
- `sun.java2d.uiScale=<detected_scale>`
- `sun.java2d.dpiaware=true`
- `glass.gtk.uiScale=<detected_scale>` (for JavaFX)
- `glass.win.uiScale=<detected_scale>` (for JavaFX)

This ensures that both Swing and any potential JavaFX components respect the scaling.
