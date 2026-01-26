/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.features.entries.DateDividerPainter;

/**
 * Frosted header strip for date/location stamps using the same font styling
 * as the entry manager date separators.
 */
public class HandwrittenHeaderStrip extends FrostedGlassPanel {
    private static final int PAD_X = 16;
    private static final int PAD_Y = 6;

    private String text = "";
    private Font cachedFont;
    private Dimension cachedPreferred = new Dimension(240, 34);
    private final Color ink = new Color(60, 60, 60);

    public HandwrittenHeaderStrip() {
        super(new BorderLayout(), 16);
        setOpaque(false);
        setOpacityScale(0.9f);
    }

    public void setStampText(String text) {
        this.text = text == null ? "" : text.trim();
        setVisible(!this.text.isEmpty());
        updateSizing();
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return cachedPreferred;
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (text == null || text.isEmpty()) return;
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Insets insets = getInsets();
        int w = getWidth();
        int h = getHeight();
        int innerW = Math.max(1, w - insets.left - insets.right);
        int innerH = Math.max(1, h - insets.top - insets.bottom);

        Font font = resolveFont();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int textX = insets.left + Math.max(0, (innerW - textW) / 2);
        int textY = insets.top + Math.max(0, (innerH - fm.getHeight()) / 2) + fm.getAscent();

        g2.setColor(ink);
        g2.drawString(text, textX, textY);
        g2.dispose();
    }

    private Font resolveFont() {
        if (cachedFont != null) return cachedFont;
        cachedFont = DateDividerPainter.resolveFont(16f);
        return cachedFont;
    }

    private void updateSizing() {
        Font font = resolveFont();
        FontMetrics fm = getFontMetrics(font);
        int textW = fm.stringWidth(text);
        int textH = fm.getHeight();
        Insets insets = getInsets();
        int w = textW + PAD_X * 2 + insets.left + insets.right;
        int h = textH + PAD_Y * 2 + insets.top + insets.bottom;
        cachedPreferred = new Dimension(Math.max(160, w), Math.max(32, h));
        setPreferredSize(cachedPreferred);
        setMaximumSize(cachedPreferred);
    }
}
