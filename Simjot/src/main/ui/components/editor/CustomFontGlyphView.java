/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
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
    private static final int DIM_NONE = 0;
    private static final int DIM_ALL = 1;
    private static final int DIM_MIXED = 2;

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
        if (resolveFont() == null) return super.getMinimumSpan(axis);
        return getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
        if (resolveFont() == null) return super.getMaximumSpan(axis);
        return getPreferredSpan(axis);
    }

    @Override
    public void paint(Graphics g, Shape a) {
        CustomFont font = resolveFont();
        if (font == null) {
            List<SentenceFocusHighlighter.DimRange> dimRanges = resolveDimRanges();
            int dimState = dimState(getStartOffset(), getEndOffset(), dimRanges);
            if (dimState == DIM_NONE) {
                super.paint(g, a);
                return;
            }
            if (dimState == DIM_ALL) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setComposite(AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA));
                    super.paint(g2, a);
                } finally {
                    g2.dispose();
                }
                return;
            }
            drawPlatformTextWithDimmedRanges(g, a, dimRanges);
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
            int dimState = dimState(getStartOffset(), getEndOffset(), dimRanges);
            if (dimState == DIM_NONE) {
                RENDERER.drawText(g2, font, text, alloc.x, baseline, size, color);
            } else if (dimState == DIM_ALL) {
                Composite base = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA));
                RENDERER.drawText(g2, font, text, alloc.x, baseline, size, color);
                g2.setComposite(base);
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

    private boolean isOffsetDimmed(int offset, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        if (dimRanges.size() == 1) {
            return dimRanges.get(0).contains(offset);
        }
        for (SentenceFocusHighlighter.DimRange range : dimRanges) {
            if (range.contains(offset)) return true;
            if (range.start() > offset) return false;
        }
        return false;
    }

    private int dimState(int start, int end, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        if (dimRanges == null || dimRanges.isEmpty() || end <= start) return DIM_NONE;
        for (SentenceFocusHighlighter.DimRange range : dimRanges) {
            if (range.end() <= start) continue;
            if (range.start() >= end) break;
            if (range.start() <= start && range.end() >= end) return DIM_ALL;
            return DIM_MIXED;
        }
        return DIM_NONE;
    }

    private void drawCustomTextWithDimmedRanges(Graphics2D g2, CustomFont font, String text, int x, int baseline,
                                                int size, Color color, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        float cursorX = x;
        int offset = getStartOffset();
        Composite baseComposite = g2.getComposite();
        
        int i = 0;
        while (i < text.length()) {
            boolean currentDimmed = isOffsetDimmed(offset, dimRanges);
            int chunkEndIndex = i;
            int chunkEndOffset = offset;
            
            while (chunkEndIndex < text.length()) {
                int cp = text.codePointAt(chunkEndIndex);
                int chars = Character.charCount(cp);
                if (isOffsetDimmed(chunkEndOffset, dimRanges) != currentDimmed) {
                    break;
                }
                chunkEndIndex += chars;
                chunkEndOffset += chars;
            }
            
            String chunkText = text.substring(i, chunkEndIndex);
            
            if (currentDimmed) {
                g2.setComposite(AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA));
            } else {
                g2.setComposite(baseComposite);
            }
            
            RENDERER.drawText(g2, font, chunkText, Math.round(cursorX), baseline, size, color);
            cursorX += measureTextWidth(font, chunkText, size);
            
            offset = chunkEndOffset;
            i = chunkEndIndex;
        }
        g2.setComposite(baseComposite);
    }

    private void drawPlatformTextWithDimmedRanges(Graphics g, Shape a, List<SentenceFocusHighlighter.DimRange> dimRanges) {
        String text = getViewText();
        if (text.isEmpty()) return;
        Rectangle alloc = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Font font = resolvePlatformFont();
            Color color = resolveColor();
            g2.setFont(font);
            g2.setColor(color);

            FontMetrics metrics = g2.getFontMetrics(font);
            int baseline = alloc.y + metrics.getAscent();
            float cursorX = alloc.x;
            int offset = getStartOffset();
            Composite baseComposite = g2.getComposite();
            boolean underline = StyleConstants.isUnderline(getAttributes());
            boolean strike = StyleConstants.isStrikeThrough(getAttributes());

            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                int chars = Character.charCount(cp);

                if (cp == '\n' || cp == '\r') {
                    offset += chars;
                    i += chars;
                    continue;
                }

                if (cp == '\t') {
                    boolean dimmed = isOffsetDimmed(offset, dimRanges);
                    g2.setComposite(dimmed ? AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA) : baseComposite);
                    cursorX += metrics.charWidth(' ') * 4f;
                    offset += chars;
                    i += chars;
                    continue;
                }

                boolean currentDimmed = isOffsetDimmed(offset, dimRanges);
                int chunkEndIndex = i;
                int chunkEndOffset = offset;
                
                while (chunkEndIndex < text.length()) {
                    int nextCp = text.codePointAt(chunkEndIndex);
                    int nextChars = Character.charCount(nextCp);
                    if (nextCp == '\n' || nextCp == '\r' || nextCp == '\t') break;
                    if (isOffsetDimmed(chunkEndOffset, dimRanges) != currentDimmed) break;
                    
                    chunkEndIndex += nextChars;
                    chunkEndOffset += nextChars;
                }
                
                String chunkText = text.substring(i, chunkEndIndex);
                g2.setComposite(currentDimmed ? AlphaComposite.SrcOver.derive(DIMMED_TEXT_ALPHA) : baseComposite);
                
                g2.drawString(chunkText, cursorX, baseline);
                int width = metrics.stringWidth(chunkText);
                if (underline) {
                    int y = baseline + 1;
                    g2.drawLine(Math.round(cursorX), y, Math.round(cursorX + width), y);
                }
                if (strike) {
                    int y = baseline - Math.max(1, metrics.getAscent() / 3);
                    g2.drawLine(Math.round(cursorX), y, Math.round(cursorX + width), y);
                }
                cursorX += width;
                
                offset = chunkEndOffset;
                i = chunkEndIndex;
            }
            g2.setComposite(baseComposite);
        } finally {
            g2.dispose();
        }
    }

    private Font resolvePlatformFont() {
        Container container = getContainer();
        Font base = container instanceof JComponent jc && jc.getFont() != null
                ? jc.getFont()
                : new Font("Serif", Font.PLAIN, resolveSize());
        String family = StyleConstants.getFontFamily(getAttributes());
        if (family == null || family.isBlank()) family = base.getFamily();
        int size = StyleConstants.getFontSize(getAttributes());
        if (size <= 0) size = base.getSize();
        int style = Font.PLAIN;
        if (StyleConstants.isBold(getAttributes())) style |= Font.BOLD;
        if (StyleConstants.isItalic(getAttributes())) style |= Font.ITALIC;
        return new Font(family, style, size);
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
