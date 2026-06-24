/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.theme;

import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.aero.AeroLookAndFeel;
import main.ui.theme.aero.AeroTheme;

/**
 * Small helper to refresh theme palettes and icon caches when theme or accent changes.
 */
public final class ThemePalette {
    private ThemePalette() {}

    public static void refresh() {
        // Re-apply current variant to static palette holders
        AeroTheme.applyVariant(Theme.getVariant());
        // Clear icon cache/tint so next loads recolor with current accent/background
        ImageIconRenderer.clearCaches();
        try {
            // Recompute accent tint lazily on next request by resetting to null,
            // then setting from current widget accent.
            ImageIconRenderer.setAccentTint(Theme.getWidgetAccent());
        } catch (Throwable ignored) {}
        try { AeroLookAndFeel.apply(); } catch (Throwable ignored) {}
    }
}
