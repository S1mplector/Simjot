/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * Subtle handwritten-style header strip for date/location stamps.
 */
public class HandwrittenHeaderStrip extends JComponent {
    private static final String[] FONT_CANDIDATES = new String[] {
            "Bradley Hand",
            "Bradley Hand ITC",
            "Noteworthy",
            "Segoe Script",
            "Segoe Print",
            "Snell Roundhand",
            "Lucida Handwriting",
            "Zapfino",
            "Comic Sans MS",
            "Serif"
    };

    private String text = "";
    private Font cachedFont;
    private final Color ink = new Color(90, 78, 68, 170);
    private final Color underline = new Color(90, 78, 68, 60);

    public HandwrittenHeaderStrip() {
        setOpaque(false);
    }

    public void setStampText(String text) {
        this.text = text == null ? "" : text.trim();
        setVisible(!this.text.isEmpty());
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(240, 28);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (text == null || text.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Insets insets = getInsets();
        int x = insets.left;
        int y = insets.top;

        Font font = resolveFont();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int textY = y + fm.getAscent();

        g2.setColor(ink);
        g2.drawString(text, x, textY);

        int underlineY = textY + 3;
        int textWidth = fm.stringWidth(text);
        g2.setColor(underline);
        g2.drawLine(x, underlineY, x + textWidth, underlineY);
        g2.dispose();
    }

    private Font resolveFont() {
        if (cachedFont != null) return cachedFont;
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        String chosen = null;
        outer:
        for (String candidate : FONT_CANDIDATES) {
            for (String family : available) {
                if (family.equalsIgnoreCase(candidate)) {
                    chosen = family;
                    break outer;
                }
            }
        }
        if (chosen == null) {
            cachedFont = getFont() != null ? getFont().deriveFont(Font.ITALIC, 15f) : new Font("Serif", Font.ITALIC, 15);
        } else {
            cachedFont = new Font(chosen, Font.PLAIN, 15);
        }
        return cachedFont;
    }
}
