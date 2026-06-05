/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.theme.aero;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;
import main.core.service.SettingsStore;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;

public final class AeroLookAndFeel {
    private AeroLookAndFeel() {}

    public static void apply() {
        Theme.Variant variant = Theme.getVariant();
        AeroTheme.applyVariant(variant);
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
        SettingsStore store = SettingsStore.get();
        float densityScale = Theme.densityToScale(store.getLayoutDensity());
        float scale = Math.max(0.8f, Math.min(1.6f, densityScale));
        Font base = AeroTheme.defaultFont().deriveFont(Math.round(AeroTheme.defaultFont().getSize2D() * scale));
        setDefaultFont(base);
        try { ImageIconRenderer.setAccentTint(Theme.getWidgetAccent()); } catch (Throwable ignored) {}

        // Basic spacing tweaks closer to Win7
        UIManager.put("Button.margin", scaledInsets(4, 12, 4, 12, scale));
        UIManager.put("ScrollBar.width", Math.max(10, Math.round(12 * scale)));

        // Optional: tooltips
        UIManager.put("ToolTip.font", base);

        // Colors for common elements (used where L&F reads UIManager)
        switch (variant) {
            case SEPIA -> {
                UIManager.put("Panel.background", new Color(245, 236, 223));
                UIManager.put("Button.foreground", AeroTheme.TEXT_PRIMARY);
                UIManager.put("Label.foreground", AeroTheme.TEXT_PRIMARY);
                UIManager.put("CheckBox.background", new Color(245, 236, 223));
                UIManager.put("CheckBox.foreground", AeroTheme.TEXT_PRIMARY);
                UIManager.put("ComboBox.background", new Color(252, 245, 234));
                UIManager.put("ComboBox.foreground", AeroTheme.TEXT_PRIMARY);
                UIManager.put("TextField.background", Color.WHITE);
                UIManager.put("TextField.foreground", AeroTheme.TEXT_PRIMARY);
            }
            case LIGHT -> {
                UIManager.put("Panel.background", Color.WHITE);
                UIManager.put("Button.foreground", new Color(40, 40, 40));
                UIManager.put("Label.foreground", new Color(40, 40, 40));
                UIManager.put("CheckBox.background", Color.WHITE);
                UIManager.put("CheckBox.foreground", new Color(40, 40, 40));
                UIManager.put("ComboBox.background", Color.WHITE);
                UIManager.put("ComboBox.foreground", new Color(40, 40, 40));
                UIManager.put("TextField.background", Color.WHITE);
                UIManager.put("TextField.foreground", new Color(40, 40, 40));
            }
            default -> {
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
        }

        // Note: We don't register custom UI classes via UIManager.put() because:
        // 1. Java modules block reflection access to our custom UI classes
        // 2. Components explicitly call setUI() in their constructors anyway
        // Custom UIs are applied directly via component.setUI(new CustomUI()) where needed
    }

    private static Insets scaledInsets(int top, int left, int bottom, int right, float scale){
        return new Insets(Math.max(1, Math.round(top * scale)),
                Math.max(2, Math.round(left * scale)),
                Math.max(1, Math.round(bottom * scale)),
                Math.max(2, Math.round(right * scale)));
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
