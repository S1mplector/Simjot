/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.theme;

import java.awt.Color;
import main.core.service.SettingsStore;
import main.ui.theme.aero.AeroTheme;

public final class Theme {

    public enum Variant {
        AERO,
        LIGHT,
        SEPIA
    }

    private Theme() {}

    public static Variant getVariant() {
        try {
            String t = SettingsStore.get().getTheme();
            if (t == null) return Variant.AERO;
            String v = t.trim();
            if (v.equalsIgnoreCase("Plain White") || v.equalsIgnoreCase("Plain") || v.equalsIgnoreCase("White") || v.equalsIgnoreCase("Light")) {
                return Variant.LIGHT;
            }
            if (v.equalsIgnoreCase("Sepia")) return Variant.SEPIA;
            if (v.equalsIgnoreCase("Dark")) return Variant.LIGHT; // map legacy dark to light
            return Variant.AERO;
        } catch (Throwable ignored) {
            return Variant.AERO;
        }
    }

    public static boolean isPlainWhite() {
        return getVariant() == Variant.LIGHT;
    }

    public static boolean isAero() {
        return getVariant() == Variant.AERO;
    }

    public static Color getBaseAccent() {
        Variant v = getVariant();
        // Reuse AeroTheme palette (it will be updated per variant in look & feel application)
        try {
            // If a specific widget accent is stored, prefer it
            int rgb = SettingsStore.get().getWidgetAccentRGB();
            if (rgb != Integer.MIN_VALUE) return new Color(rgb, false);
        } catch (Throwable ignored) {}
        return switch (v) {
            case SEPIA -> new Color(174, 118, 74);
            case LIGHT -> new Color(0, 120, 215);
            default -> AeroTheme.AERO_BLUE;
        };
    }

    public static Color getWidgetAccent() {
        try {
            int rgb = SettingsStore.get().getWidgetAccentRGB();
            if (rgb != Integer.MIN_VALUE) return new Color(rgb, false);
            int mainMenuRgb = SettingsStore.get().getMainMenuAccentRGB();
            if (mainMenuRgb != Integer.MIN_VALUE) return new Color(mainMenuRgb, true);
        } catch (Throwable ignored) {}
        return getBaseAccent();
    }

    public static Color getMainMenuAccent() {
        try {
            int rgb = SettingsStore.get().getMainMenuAccentRGB();
            if (rgb != Integer.MIN_VALUE) return new Color(rgb, true);
        } catch (Throwable ignored) {}
        try {
            int widgetRgb = SettingsStore.get().getWidgetAccentRGB();
            if (widgetRgb != Integer.MIN_VALUE) return new Color(widgetRgb, false);
        } catch (Throwable ignored) {}
        return getBaseAccent();
    }

    public static Color getChromeAccent() {
        try {
            Color accent = getMainMenuAccent();
            if (accent != null) return accent;
        } catch (Throwable ignored) {}
        return getWidgetAccent();
    }

    public static float densityToScale(String density) {
        if (density == null) return 1.0f;
        String d = density.trim().toLowerCase();
        if (d.startsWith("minimal")) return 1.05f;
        if (d.startsWith("dense") || d.startsWith("information")) return 0.92f;
        return 1.0f; // balanced/default
    }
}
