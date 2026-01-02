/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Font;

import javax.swing.JTextPane;

import main.core.font.CustomFont;
import main.infrastructure.font.CustomFontSupport;

/**
 * Applies standard or custom fonts to Swing text components.
 */
public final class CustomFontApplier {
    private CustomFontApplier() {}

    public static void applyToTextPane(JTextPane pane, String fontName, int size) {
        if (pane == null) return;
        String resolved = fontName == null || fontName.isBlank() ? "Serif" : fontName;

        if (CustomFontSupport.isCustomDisplayName(resolved)) {
            CustomFont custom = CustomFontSupport.loadByDisplayName(resolved);
            if (pane instanceof CustomFontTextPane customPane) {
                customPane.setCustomFont(custom);
            }
            if (custom == null) {
                pane.setFont(new Font("Serif", Font.PLAIN, size));
            } else {
                pane.setFont(new Font("SansSerif", Font.PLAIN, size));
            }
        } else {
            if (pane instanceof CustomFontTextPane customPane) {
                customPane.setCustomFont(null);
            }
            pane.setFont(new Font(resolved, Font.PLAIN, size));
        }
    }

    public static Font resolveUiFont(String fontName, int size) {
        String resolved = fontName == null || fontName.isBlank() ? "Serif" : fontName;
        if (CustomFontSupport.isCustomDisplayName(resolved)) {
            return new Font("Serif", Font.PLAIN, size);
        }
        return new Font(resolved, Font.PLAIN, size);
    }
}
