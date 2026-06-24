/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import javax.swing.JTextPane;

import main.core.font.CustomFont;

/**
 * JTextPane with optional custom font rendering support.
 */
public class CustomFontTextPane extends JTextPane {
    private CustomFont customFont;

    public CustomFontTextPane() {
        super();
        setEditorKit(new CustomFontEditorKit());
    }

    public void setCustomFont(CustomFont font) {
        this.customFont = font;
        revalidate();
        repaint();
    }

    public CustomFont getCustomFont() {
        return customFont;
    }

    public boolean isCustomFontActive() {
        return customFont != null;
    }
}
