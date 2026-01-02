/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Container;

import javax.swing.JViewport;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * View factory that replaces GlyphView with custom font rendering when enabled.
 */
public class CustomFontViewFactory implements ViewFactory {
    @Override
    public View create(Element elem) {
        String kind = elem.getName();
        if (kind != null) {
            if (kind.equals(AbstractDocument.ContentElementName)) {
                return new CustomFontGlyphView(elem);
            }
            if (kind.equals(AbstractDocument.ParagraphElementName)) {
                return new ParagraphView(elem);
            }
            if (kind.equals(AbstractDocument.SectionElementName)) {
                return new WrappingBoxView(elem, View.Y_AXIS);
            }
            if (kind.equals(StyleConstants.ComponentElementName)) {
                return new ComponentView(elem);
            }
            if (kind.equals(StyleConstants.IconElementName)) {
                return new IconView(elem);
            }
        }
        return new LabelView(elem);
    }

    /**
     * BoxView that constrains width to the viewport, enabling proper text wrapping
     * when the text pane is placed inside a JScrollPane.
     */
    private static class WrappingBoxView extends BoxView {
        WrappingBoxView(Element elem, int axis) {
            super(elem, axis);
        }

        @Override
        protected void layoutMinorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
            for (int i = 0; i < getViewCount(); i++) {
                offsets[i] = 0;
                spans[i] = targetSpan;
            }
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
                Container container = getContainer();
                if (container != null) {
                    Container parent = container.getParent();
                    if (parent instanceof JViewport) {
                        return parent.getWidth();
                    }
                }
            }
            return super.getMinimumSpan(axis);
        }

        @Override
        public float getMaximumSpan(int axis) {
            if (axis == View.X_AXIS) {
                Container container = getContainer();
                if (container != null) {
                    Container parent = container.getParent();
                    if (parent instanceof JViewport) {
                        return parent.getWidth();
                    }
                }
            }
            return super.getMaximumSpan(axis);
        }
    }
}
