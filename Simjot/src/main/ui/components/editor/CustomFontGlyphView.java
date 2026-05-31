/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.GlyphView;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;

import main.core.font.CustomFont;
import main.infrastructure.font.CustomFontRenderer;
import main.infrastructure.font.NativeFontSupport;

/**
 * GlyphView that renders using the active custom font when present.
 */
class CustomFontGlyphView extends GlyphView {
    private static final CustomFontRenderer RENDERER = new CustomFontRenderer();
    private static final float DIMMED_TEXT_ALPHA = 0.38f;

    CustomFontGlyphView(Element elem) {
        super(elem);
    }

    @Override
    public float getPreferredSpan(int axis) {
        CustomFont font = resolveFont();
        if (font == null) return super.getPreferredSpan(axis);
        int size = resolveSize();
        if (axis == View.X_AXIS) {
            return measureTextWidth(font, getViewText(), size);
        }
        return resolveLineHeight(font, size);
    }

    @Override
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public void paint(Graphics g, Shape a) {
        CustomFont font = resolveFont();
        if (font == null) {
            List<SentenceFocusHighlighter.DimRange> dimRanges = resolveDimRanges();
            if (!dimRanges.isEmpty() && viewFullyDimmed(dimRanges)) {
                Graphics2D g2 = (Graphics2D) g.create();
                Composite oldComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA));
                super.paint(g2, a);
                g2.setComposite(oldComposite);
                g2.dispose();
                return;
            }
            super.paint(g, a);
            return;
        }
        String text = getViewText();
        if (text.isEmpty()) return;
        Rectangle alloc = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
        int size = resolveSize();
        int baseline = alloc.y + Math.round(resolveAscender(font, size));

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Color color = resolveColor();
            g2.setColor(color);
            List<SentenceFocusHighlighter.DimRange> dimRanges = resolveDimRanges();
            if (dimRanges.isEmpty()) {
                RENDERER.drawText(g2, font, text, alloc.x, baseline, size, color);
            } else {
                drawCustomTextWithDimmedRanges(g2, font, text, alloc.x, baseline, size, color, dimRanges);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
        CustomFont font = resolveFont();
        if (font == null) return super.modelToView(pos, a, b);

        int start = getStartOffset();
        int end = getEndOffset();
        int clamped = Math.max(start, Math.min(pos, end));
        String text = getDocument().getText(start, clamped - start);
        float width = measureTextWidth(font, text, resolveSize());
        Rectangle alloc = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
        int x = alloc.x + Math.round(width);
        return new Rectangle(x, alloc.y, 1, alloc.height);
    }

    @Override
    public int viewToModel(float x, float y, Shape a, Position.Bias[] biasReturn) {
        CustomFont font = resolveFont();
        if (font == null) return super.viewToModel(x, y, a, biasReturn);

        Rectangle alloc = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
        float target = x - alloc.x;
        int start = getStartOffset();
        int end = getEndOffset();
        if (target <= 0) {
            if (biasReturn != null && biasReturn.length > 0) biasReturn[0] = Position.Bias.Forward;
            return start;
        }
        try {
            String text = getDocument().getText(start, end - start);
            int offset = start;
            float width = 0f;
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                int chars = Character.charCount(cp);
                float advance = advanceForCodepoint(font, cp, resolveSize());
                if (width + advance * 0.5f >= target) {
                    if (biasReturn != null && biasReturn.length > 0) biasReturn[0] = Position.Bias.Forward;
                    return offset;
                }
                width += advance;
                offset += chars;
                i += chars;
            }
        } catch (BadLocationException ignored) {}
        if (biasReturn != null && biasReturn.length > 0) biasReturn[0] = Position.Bias.Forward;
        return end;
    }

    @Override
    public int getBreakWeight(int axis, float pos, float len) {
        CustomFont font = resolveFont();
        if (font == null) return super.getBreakWeight(axis, pos, len);
        if (axis != View.X_AXIS) return super.getBreakWeight(axis, pos, len);
        float width = measureTextWidth(font, getViewText(), resolveSize());
        return width <= len ? View.BadBreakWeight : View.GoodBreakWeight;
    }

    @Override
    public View breakView(int axis, int p0, float pos, float len) {
        CustomFont font = resolveFont();
        if (font == null) return super.breakView(axis, p0, pos, len);
        if (axis != View.X_AXIS) return super.breakView(axis, p0, pos, len);

        int end = getEndOffset();
        if (p0 >= end) return this;

        try {
            String text = getDocument().getText(p0, end - p0);
            float width = 0f;
            int breakOffset = -1;
            int lastSpace = -1;
            int offset = p0;
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                int chars = Character.charCount(cp);
                float advance = advanceForCodepoint(font, cp, resolveSize());
                if (Character.isWhitespace(cp)) {
                    lastSpace = offset + chars;
                }
                if (width + advance > len) {
                    breakOffset = (lastSpace > p0) ? lastSpace : offset;
                    break;
                }
                width += advance;
                offset += chars;
                i += chars;
            }
            if (breakOffset <= p0) {
                breakOffset = Math.min(p0 + 1, end);
            }
            if (breakOffset >= end) return this;
            return createFragment(p0, breakOffset);
        } catch (BadLocationException ignored) {
            return this;
        }
    }

    private CustomFont resolveFont() {
        Container container = getContainer();
        if (container instanceof CustomFontTextPane pane) {
            return pane.getCustomFont();
        }
        return null;
    }

    private int resolveSize() {
        Container container = getContainer();
        if (container instanceof JComponent jc && jc.getFont() != null) {
            return jc.getFont().getSize();
        }
        return 14;
    }

    private Color resolveColor() {
        Color color = StyleConstants.getForeground(getAttributes());
        if (color == null) {
            Container container = getContainer();
            if (container instanceof JComponent jc) {
                color = jc.getForeground();
            }
        }
        return color != null ? color : Color.BLACK;
    }

    private List<SentenceFocusHighlighter.DimRange> resolveDimRanges() {
        Container container = getContainer();
        if (container instanceof JComponent jc) {
            Object value = jc.getClientProperty(SentenceFocusHighlighter.DIM_RANGES_PROPERTY);
            if (value instanceof List<?> raw && !raw.isEmpty()
                    && raw.get(0) instanceof SentenceFocusHighlighter.DimRange) {
                @SuppressWarnings("unchecked")
                List<SentenceFocusHighlighter.DimRange> ranges = (List<SentenceFocusHighlighter.DimRange>) raw;
                return ranges;
            }
        }
        return List.of();
    }

    private boolean viewFullyDimmed(List<SentenceFocusHighlighter.DimRange> dimRanges) {
        int start = getStartOffset();
        int end = getEndOffset();
        if (end <= start) return false;
        for (SentenceFocusHighlighter.DimRange range : dimRanges) {
            if (range.start() <= start && range.end() >= end) return true;
        }
        return false;
    }

    private boolean isOffsetDimmed(int offset, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        for (SentenceFocusHighlighter.DimRange range : dimRanges) {
            if (range.contains(offset)) return true;
            if (range.start() > offset) return false;
        }
        return false;
    }

    private void drawCustomTextWithDimmedRanges(Graphics2D g2, CustomFont font, String text, int x, int baseline,
                                                int size, Color color, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        float cursorX = x;
        int offset = getStartOffset();
        Composite baseComposite = g2.getComposite();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            int chars = Character.charCount(cp);
            String glyphText = text.substring(i, i + chars);
            boolean dimmed = isOffsetDimmed(offset, dimRanges);
            if (dimmed) {
                g2.setComposite(AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA));
            } else {
                g2.setComposite(baseComposite);
            }
            RENDERER.drawText(g2, font, glyphText, Math.round(cursorX), baseline, size, color);
            cursorX += advanceForCodepoint(font, cp, size);
            offset += chars;
            i += chars;
        }
        g2.setComposite(baseComposite);
    }

    private String getViewText() {
        try {
            int start = getStartOffset();
            int end = getEndOffset();
            return getDocument().getText(start, Math.max(0, end - start));
        } catch (BadLocationException ignored) {
            return "";
        }
    }

    private float measureTextWidth(CustomFont font, String text, int size) {
        if (text == null || text.isEmpty()) return 0f;
        float width = 0f;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            int chars = Character.charCount(cp);
            width += advanceForCodepoint(font, cp, size);
            i += chars;
        }
        return width;
    }

    private float advanceForCodepoint(CustomFont font, int cp, int size) {
        if (cp == '\n' || cp == '\r') return 0f;
        if (cp == '\t') return resolveSpaceAdvance(font, size) * 4f;
        Float nativeAdvance = NativeFontSupport.measureChar(font, cp, size);
        return nativeAdvance != null ? nativeAdvance : font.measureChar(cp, size);
    }

    private float resolveSpaceAdvance(CustomFont font, int size) {
        Float nativeAdvance = NativeFontSupport.measureChar(font, ' ', size);
        return nativeAdvance != null ? nativeAdvance : font.measureChar(' ', size);
    }

    private float resolveLineHeight(CustomFont font, int size) {
        Float nativeHeight = NativeFontSupport.getLineHeight(font, size);
        return nativeHeight != null ? nativeHeight : font.getLineHeight(size);
    }

    private float resolveAscender(CustomFont font, int size) {
        Float nativeAscender = NativeFontSupport.getAscender(font, size);
        return nativeAscender != null ? nativeAscender : font.getAscender(size);
    }
}
