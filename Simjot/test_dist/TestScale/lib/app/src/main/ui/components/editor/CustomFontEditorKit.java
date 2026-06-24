/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;

/**
 * Styled editor kit that swaps in custom font glyph rendering when enabled.
 */
public class CustomFontEditorKit extends StyledEditorKit {
    private final ViewFactory viewFactory = new CustomFontViewFactory();

    @Override
    public ViewFactory getViewFactory() {
        return viewFactory;
    }
}
