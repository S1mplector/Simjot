/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.scaling;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.Enumeration;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/**
 * Advanced, system-agnostic UI scaling manager that detects and applies
 * DPI/scaling across Windows, Linux, and macOS.
 * 
 * Now uses native C implementation for reliable cross-platform DPI detection.
 */
public final class UIScalingManager {
    
    private static float detectedScale = -1f;
    private static boolean initialized = false;
    private static float lastAppliedScale = 1.0f;
    private static boolean useNativeScaling = true;
    
    private UIScalingManager() {}
    
    /**
     * Initialize scaling early in the application lifecycle.
     * Should be called before any Swing components are created.
     * Attempts to read user preference from config file if available.
     */
    public static void initializeEarly() {
        if (initialized) return;

        // Check if UI scaling is enabled in settings by reading config file directly
        // (SettingsStore can't be used yet as AppDirectories isn't initialized)
        if (!isUIScalingEnabledFromConfig()) {
            // UI scaling is disabled, use 1.0 scale (no scaling)
            detectedScale = 1.0f;
            initialized = true;
            System.out.println("[UIScaling] UI scaling disabled by user setting, using 1.0x scale");
            return;
        }

        // Try to read user preference from config file BEFORE detecting system scale
        float userScale = readUserScaleFromConfig();
        float scale = (userScale > 0 && userScale != 1.0f) ? userScale : detectSystemScale();
        scale = normalizeScale(scale);

        // Set JVM properties that various subsystems might check
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("sun.java2d.uiScale", String.valueOf(scale));
        System.setProperty("sun.java2d.dpiaware", "true");

        // For JavaFX if present
        System.setProperty("glass.gtk.uiScale", String.valueOf(scale));
        System.setProperty("glass.win.uiScale", String.valueOf(scale));

        detectedScale = scale;
        initialized = true;

        System.out.println("[UIScaling] Initialized with scale: " + scale + "x" +
            (userScale > 0 && userScale != 1.0f ? " (from user config)" : " (auto-detected)"));
    }
    
    /**
     * Apply scaling to Swing UIManager defaults.
     * Should be called after Look & Feel is set.
     */
    public static void applyToSwing(float userScaleOverride) {
        float effectiveScale = (userScaleOverride > 0 && userScaleOverride != 1.0f) 
            ? userScaleOverride 
            : getDetectedScale();

        System.out.println("[UIScaling] Applying " + effectiveScale + "x scale to Swing components");
        
        try {
            // Scale all fonts in UIManager
            UIDefaults defaults = UIManager.getDefaults();
            Enumeration<Object> keys = defaults.keys();
            
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object value = defaults.get(key);
                
                if (value instanceof FontUIResource) {
                    FontUIResource font = (FontUIResource) value;
                    int newSize = Math.round(font.getSize() * effectiveScale);
                    FontUIResource scaledFont = new FontUIResource(
                        font.getName(), font.getStyle(), newSize);
                    UIManager.put(key, scaledFont);
                }
            }
            
            // Scale component dimensions
            scaleInsets("Button.margin", 2, 14, 2, 14, effectiveScale);
            scaleInsets("TextField.margin", 2, 6, 2, 6, effectiveScale);
            scaleInsets("ComboBox.padding", 3, 3, 3, 3, effectiveScale);
            scaleInsets("TabbedPane.contentBorderInsets", 2, 2, 3, 3, effectiveScale);
            scaleInsets("TabbedPane.tabInsets", 4, 4, 4, 1, effectiveScale);
            
            // Scale sizes
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("SplitPane.dividerSize", Math.round(10 * effectiveScale));
            UIManager.put("Tree.rowHeight", Math.round(20 * effectiveScale));
            UIManager.put("Table.rowHeight", Math.round(20 * effectiveScale));
            
            // Force component size scaling for better results
            UIManager.put("Component.minimumSize", scaleDimension(10, 10, effectiveScale));
            
        } catch (Exception e) {
            System.err.println("[UIScaling] Error applying scaling: " + e.getMessage());
            e.printStackTrace();
        }
        lastAppliedScale = effectiveScale;
    }

    /**
     * Update the UI scale at runtime. This will:
     * - Update JVM/system properties for downstream consumers
     * - Recompute UIManager defaults for the new scale
     * - Refresh all open Swing windows to pick up new defaults
     */
    public static void updateScale(float newScale) {
        // Clamp and store
        float scale = Math.max(0.5f, Math.min(4.0f, newScale));
        if (scale == detectedScale) return;

        System.out.println("[UIScaling] Updating scale to " + scale + "x");
        detectedScale = scale;

        // Update properties so newly created components respect the new scale
        try {
            System.setProperty("sun.java2d.uiScale", String.valueOf(scale));
            System.setProperty("glass.gtk.uiScale", String.valueOf(scale));
            System.setProperty("glass.win.uiScale", String.valueOf(scale));
        } catch (Throwable ignored) {}

        // Reapply UI defaults for current L&F
        float prev = (lastAppliedScale <= 0f ? 1.0f : lastAppliedScale);
        applyToSwing(scale);

        // Refresh all open windows WITHOUT updateComponentTreeUI to preserve custom UIs
        try {
            for (Window w : Window.getWindows()) {
                if (w == null) continue;
                // Rescale explicitly set fonts and preferred sizes by relative factor
                float factor = (prev == 0f) ? scale : (scale / prev);
                try { rescaleComponentTree(w, factor); } catch (Throwable ignored) {}
                // DON'T call updateComponentTreeUI - it breaks custom UIs with module reflection errors
                // Instead just invalidate and repaint to apply the new UIManager defaults
                try { w.invalidate(); } catch (Throwable ignored) {}
                try { w.validate(); } catch (Throwable ignored) {}
                try { w.repaint(); } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            System.err.println("[UIScaling] Error refreshing windows: " + e.getMessage());
        }
        System.out.println("[UIScaling] Scale update complete");
    }

    /**
     * Walks the component tree and rescales fonts to match new scale.
     * Uses absolute scaling rather than relative to avoid compound scaling errors.
     */
    private static void rescaleComponentTree(Component c, float factor) {
        if (c == null) return;
        try {
            // Update font - use UIManager default if available, otherwise scale existing
            Font f = c.getFont();
            if (f != null) {
                // For components with explicitly set fonts, scale them
                if (!(f instanceof FontUIResource)) {
                    float newSize = Math.max(1f, f.getSize2D() * factor);
                    c.setFont(f.deriveFont(newSize));
                } else {
                    // For UIResource fonts, force update from UIManager to get scaled version
                    try {
                        String uiKey = getUIFontKey(c);
                        if (uiKey != null) {
                            Font uiFont = UIManager.getFont(uiKey);
                            if (uiFont != null) c.setFont(uiFont);
                        }
                    } catch (Throwable ignored) {}
                }
            }
            
            // Recurse into children
            if (c instanceof Container cont) {
                for (Component child : cont.getComponents()) {
                    rescaleComponentTree(child, factor);
                }
            }
        } catch (Throwable ignored) {}
    }
    
    /**
     * Get the UIManager font key for a component type.
     */
    private static String getUIFontKey(Component c) {
        if (c instanceof JButton) return "Button.font";
        if (c instanceof JLabel) return "Label.font";
        if (c instanceof JTextField) return "TextField.font";
        if (c instanceof JTextArea) return "TextArea.font";
        if (c instanceof JList) return "List.font";
        if (c instanceof JTable) return "Table.font";
        if (c instanceof JTree) return "Tree.font";
        if (c instanceof JComboBox) return "ComboBox.font";
        if (c instanceof JCheckBox) return "CheckBox.font";
        if (c instanceof JRadioButton) return "RadioButton.font";
        if (c instanceof JSpinner) return "Spinner.font";
        if (c instanceof JPanel) return "Panel.font";
        return null;
    }
    
    /**
     * Reads UI scaling enabled preference directly from the config file.
     * This allows us to check the setting before SettingsStore is initialized.
     */
    private static boolean isUIScalingEnabledFromConfig() {
        try {
            String home = System.getProperty("user.home");
            java.io.File configFile = new java.io.File(home, ".simjournal_config.txt");

            if (!configFile.exists()) return true; // Default to enabled if no config

            // Read the root folder path from config
            String rootPath = null;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile))) {
                rootPath = reader.readLine();
            }

            if (rootPath == null || rootPath.trim().isEmpty()) return true;

            // Try to read preferences.properties from the settings folder
            java.io.File settingsDir = new java.io.File(rootPath, "settings");
            java.io.File prefsFile = new java.io.File(settingsDir, "preferences.properties");

            if (!prefsFile.exists()) return true; // Default to enabled if no preferences file

            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(prefsFile)) {
                props.load(in);
            }

            String enabledStr = props.getProperty("uiScalingEnabled", "true");
            boolean enabled = Boolean.parseBoolean(enabledStr);
            System.out.println("[UIScaling] Read UI scaling enabled from config: " + enabled);
            return enabled;
        } catch (Exception e) {
            System.err.println("[UIScaling] Could not read UI scaling enabled from config: " + e.getMessage());
        }
        return true; // Default to enabled on error
    }

    /**
     * Reads user scale preference directly from the config file.
     * This allows us to apply the correct scale before SettingsStore is initialized.
     */
    private static float readUserScaleFromConfig() {
        try {
            String home = System.getProperty("user.home");
            java.io.File configFile = new java.io.File(home, ".simjournal_config.txt");
            
            if (!configFile.exists()) return 1.0f;
            
            // Read the root folder path from config
            String rootPath = null;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile))) {
                rootPath = reader.readLine();
            }
            
            if (rootPath == null || rootPath.trim().isEmpty()) return 1.0f;
            
            // Try to read preferences.properties from the settings folder
            java.io.File settingsDir = new java.io.File(rootPath, "settings");
            java.io.File prefsFile = new java.io.File(settingsDir, "preferences.properties");
            
            if (!prefsFile.exists()) return 1.0f;
            
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(prefsFile)) {
                props.load(in);
            }
            
            String scaleStr = props.getProperty("uiScale");
            if (scaleStr != null && !scaleStr.isEmpty()) {
                float scale = Float.parseFloat(scaleStr);
                System.out.println("[UIScaling] Read user scale from config: " + scale);
                return scale;
            }
        } catch (Exception e) {
            System.err.println("[UIScaling] Could not read user scale from config: " + e.getMessage());
        }
        return 1.0f;
    }
    
    /**
     * Get the detected system scale factor.
     */
    public static float getDetectedScale() {
        if (detectedScale < 0) {
            detectedScale = detectSystemScale();
        }
        return detectedScale;
    }
    
    /**
     * Detect system scale across all platforms.
     * Uses native C implementation for reliable cross-platform detection.
     */
    private static float detectSystemScale() {
        // Try native detection first (most reliable)
        if (useNativeScaling) {
            try {
                float nativeScale = main.infrastructure.ffi.NativeAccess.getPrimaryDisplayScale();
                if (nativeScale > 0.5f && nativeScale <= 4.0f) {
                    System.out.println("[UIScaling] Native scale detected: " + nativeScale);
                    return nativeScale;
                }
            } catch (Throwable e) {
                System.err.println("[UIScaling] Native scaling failed, using Java fallback: " + e.getMessage());
            }
        }
        
        // Java fallback methods
        String os = System.getProperty("os.name", "").toLowerCase();
        float scale = 1.0f;
        
        // Method 1: Check JVM properties (might be set by launcher or desktop)
        scale = Math.max(scale, checkJVMProperties());
        
        // Method 2: Check environment variables (Linux/Unix)
        if (os.contains("linux") || os.contains("unix")) {
            scale = Math.max(scale, checkLinuxEnvironment());
        }
        
        // Method 3: Check AWT/Swing toolkit DPI
        scale = Math.max(scale, checkToolkitDPI());
        
        // Method 4: Check GraphicsConfiguration (works on all platforms)
        scale = Math.max(scale, checkGraphicsConfiguration());
        
        // Clamp to reasonable range
        return Math.max(0.5f, Math.min(4.0f, scale));
    }

    /**
     * Use quarter-step scaling so text and one-pixel artwork remain crisp while
     * still supporting common fractional desktop scales such as 125% and 150%.
     */
    private static float normalizeScale(float scale) {
        float clamped = Math.max(0.5f, Math.min(4.0f, scale));
        return Math.round(clamped * 4.0f) / 4.0f;
    }
    
    private static float checkJVMProperties() {
        try {
            String prop = System.getProperty("sun.java2d.uiScale");
            if (prop != null && !prop.isEmpty()) {
                return Float.parseFloat(prop);
            }
        } catch (Exception ignored) {}
        return 1.0f;
    }
    
    private static float checkLinuxEnvironment() {
        float scale = 1.0f;
        
        // GDK_SCALE (integer scale, common on GNOME/GTK)
        try {
            String gdk = System.getenv("GDK_SCALE");
            if (gdk != null && !gdk.isBlank()) {
                scale = Math.max(scale, Float.parseFloat(gdk.trim()));
            }
        } catch (Exception ignored) {}
        
        // GDK_DPI_SCALE (fractional scale)
        try {
            String gdkDpi = System.getenv("GDK_DPI_SCALE");
            if (gdkDpi != null && !gdkDpi.isBlank()) {
                scale = Math.max(scale, Float.parseFloat(gdkDpi.trim()));
            }
        } catch (Exception ignored) {}
        
        // QT_SCALE_FACTOR (for Qt-based desktops)
        try {
            String qt = System.getenv("QT_SCALE_FACTOR");
            if (qt != null && !qt.isBlank()) {
                scale = Math.max(scale, Float.parseFloat(qt.trim()));
            }
        } catch (Exception ignored) {}
        
        return scale;
    }
    
    private static float checkToolkitDPI() {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            int dpi = toolkit.getScreenResolution();
            // Standard DPI is 96, so scale = actualDPI / 96
            if (dpi > 0) {
                float calculatedScale = dpi / 96.0f;
                // Only trust this if it's significantly different from 1.0
                if (Math.abs(calculatedScale - 1.0f) > 0.1f) {
                    System.out.println("[UIScaling] Toolkit DPI: " + dpi + " -> scale " + calculatedScale);
                    return calculatedScale;
                }
            }
        } catch (Exception e) {
            System.err.println("[UIScaling] Error checking toolkit DPI: " + e.getMessage());
        }
        return 1.0f;
    }
    
    private static float checkGraphicsConfiguration() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            
            // Get the transform which includes DPI scaling
            var transform = gc.getDefaultTransform();
            double scaleX = transform.getScaleX();
            double scaleY = transform.getScaleY();
            
            // Use the larger of the two scales
            float scale = (float) Math.max(scaleX, scaleY);
            
            if (scale > 1.0f) {
                System.out.println("[UIScaling] GraphicsConfiguration scale: " + scale);
                return scale;
            }
        } catch (Exception e) {
            System.err.println("[UIScaling] Error checking graphics configuration: " + e.getMessage());
        }
        return 1.0f;
    }
    
    private static void scaleInsets(String key, int top, int left, int bottom, int right, float scale) {
        UIManager.put(key, new Insets(
            Math.round(top * scale),
            Math.round(left * scale),
            Math.round(bottom * scale),
            Math.round(right * scale)
        ));
    }
    
    /**
     * Get a manual scale factor multiplier for components that need to scale themselves
     * when native Java2D scaling (sun.java2d.uiScale) fails to apply correctly (e.g. jpackage Linux builds).
     */
    public static float getManualScaleFactor() {
        float detected = getDetectedScale();
        float nativeTransform = 1.0f;
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            nativeTransform = (float) Math.max(gc.getDefaultTransform().getScaleX(), gc.getDefaultTransform().getScaleY());
        } catch (Throwable ignored) {}
        
        // If native scaling is active and matches detected scale, we don't need manual scaling
        if (Math.abs(nativeTransform - detected) < 0.1f) {
            return 1.0f;
        }
        
        // Otherwise, native scaling failed (nativeTransform is likely 1.0), so we must manually scale components
        return detected / Math.max(1.0f, nativeTransform);
    }
    
    /**
     * Scale a dimension value by the detected scale.
     * Uses native C implementation for consistent results.
     */
    public static int scale(int value) {
        return main.infrastructure.ffi.NativeAccess.scale(value);
    }
    
    /**
     * Scale a dimension value by a specific scale factor.
     * Uses native C implementation for consistent results.
     */
    public static int scale(int value, float scaleFactor) {
        return main.infrastructure.ffi.NativeAccess.scaleDimension(value, scaleFactor);
    }
    
    /**
     * Scale a font size by the detected scale.
     * Uses native C implementation with proper rounding for text clarity.
     */
    public static Font scaleFont(Font font) {
        float scaledSize = main.infrastructure.ffi.NativeAccess.scaleFontSize(font.getSize2D());
        return font.deriveFont(scaledSize);
    }
    
    /**
     * Scale a font size by a specific scale factor.
     * Uses native C implementation with proper rounding for text clarity.
     */
    public static Font scaleFont(Font font, float scaleFactor) {
        float scaledSize = main.infrastructure.ffi.NativeAccess.scaleFontSize(font.getSize2D(), scaleFactor);
        return font.deriveFont(scaledSize);
    }
    
    /**
     * Create a scaled dimension.
     */
    public static Dimension scaleDimension(int width, int height) {
        return new Dimension(scale(width), scale(height));
    }
    
    /**
     * Create a scaled dimension with specific scale.
     */
    public static Dimension scaleDimension(int width, int height, float scaleFactor) {
        return new Dimension(scale(width, scaleFactor), scale(height, scaleFactor));
    }
    
    /**
     * Invalidate cached scale values (call when displays change).
     */
    public static void invalidateCache() {
        detectedScale = -1f;
        main.infrastructure.ffi.NativeAccess.invalidateDisplayCache();
    }
    
    /**
     * Get scale factor for a specific display.
     */
    public static float getDisplayScale(int displayIndex) {
        return main.infrastructure.ffi.NativeAccess.getDisplayScale(displayIndex);
    }
    
    /**
     * Get the number of connected displays.
     */
    public static int getDisplayCount() {
        return main.infrastructure.ffi.NativeAccess.getDisplayCount();
    }
}
