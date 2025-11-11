package main.ui.theme.aero;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;
import main.ui.theme.Theme;

public final class AeroLookAndFeel {
    private AeroLookAndFeel() {}

    public static void apply() {
        // Try Windows L&F first (best base for Aero on Windows)
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Windows".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { /* fallback to current */ }

        // Apply Segoe UI font across defaults
        Font base = AeroTheme.defaultFont();
        setDefaultFont(base);

        // Basic spacing tweaks closer to Win7
        UIManager.put("Button.margin", new Insets(4, 12, 4, 12));
        UIManager.put("ScrollBar.width", 12);

        // Optional: tooltips
        UIManager.put("ToolTip.font", base);

        // Colors for common elements (used where L&F reads UIManager)
        if (Theme.isPlainWhite()) {
            UIManager.put("Panel.background", Color.WHITE);
            UIManager.put("Button.foreground", new Color(40, 40, 40));
            UIManager.put("Label.foreground", new Color(40, 40, 40));
            UIManager.put("CheckBox.background", Color.WHITE);
            UIManager.put("CheckBox.foreground", new Color(40, 40, 40));
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", new Color(40, 40, 40));
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextField.foreground", new Color(40, 40, 40));
        } else {
            UIManager.put("Panel.background", new Color(248, 248, 248));
            UIManager.put("Button.foreground", AeroTheme.TEXT_PRIMARY);
            UIManager.put("Label.foreground", AeroTheme.TEXT_PRIMARY);
            UIManager.put("CheckBox.background", new Color(248, 248, 248));
            UIManager.put("CheckBox.foreground", AeroTheme.TEXT_PRIMARY);
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", AeroTheme.TEXT_PRIMARY);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextField.foreground", AeroTheme.TEXT_PRIMARY);
        }

        // Note: We don't register custom UI classes via UIManager.put() because:
        // 1. Java modules block reflection access to our custom UI classes
        // 2. Components explicitly call setUI() in their constructors anyway
        // Custom UIs are applied directly via component.setUI(new CustomUI()) where needed
    }

    private static void setDefaultFont(Font f) {
        UIDefaults d = UIManager.getDefaults();
        Enumeration<?> keys = d.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object val = d.get(key);
            if (val instanceof Font) {
                d.put(key, f);
            }
        }
    }
}
