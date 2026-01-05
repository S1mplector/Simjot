/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.scrollbar;

import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * ModernScrollBarUI provides a custom scrollbar UI
 * that features a modern design with rounded corners,
 * smooth thumb, and custom arrow buttons.
 * This UI is designed to enhance the visual appeal
 * and usability of scrollbars in Java Swing applications.
 */

public class ModernScrollBarUI extends BasicScrollBarUI {
    private static final int ARC = 10;
    private static final int THICKNESS = 18;

    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = new Color(214, 218, 224, 210);
        this.thumbDarkShadowColor = new Color(160, 166, 175, 160);
        this.thumbHighlightColor = new Color(248, 248, 250, 200);
        this.thumbLightShadowColor = new Color(248, 248, 250, 200);
        this.trackColor = new Color(247, 247, 247, 160);
        this.trackHighlightColor = new Color(235, 235, 235, 140);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension base = super.getPreferredSize(c);
        if (scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL) {
            return new Dimension(THICKNESS, base.height);
        }
        return new Dimension(base.width, THICKNESS);
    }
    @Override
    protected JButton createDecreaseButton(int orientation) {
        return new ArrowButton(orientation);
    }
    @Override
    protected JButton createIncreaseButton(int orientation) {
        return new ArrowButton(orientation);
    }
    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds == null || thumbBounds.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float enabledScale = scrollbar != null && scrollbar.isEnabled() ? 1f : 0.6f;

        int pad = 2;
        int x = thumbBounds.x + pad;
        int y = thumbBounds.y + pad;
        int w = Math.max(1, thumbBounds.width - pad * 2);
        int h = Math.max(1, thumbBounds.height - pad * 2);
        int arc = Math.min(ARC, Math.min(w, h));

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

        // Flat frosted glass fill - no gradient
        g2.setColor(scaleAlpha(new Color(245, 245, 245, 200), enabledScale));
        g2.fill(shape);

        // Inner white highlight stroke
        int innerArc = Math.max(arc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 90), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, innerArc, innerArc));

        // Outer dark outline stroke
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 35), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, arc, arc));
        g2.dispose();
    }
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        if (trackBounds == null || trackBounds.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float enabledScale = scrollbar != null && scrollbar.isEnabled() ? 1f : 0.7f;

        int pad = 2;
        int x = trackBounds.x + pad;
        int y = trackBounds.y + pad;
        int w = Math.max(1, trackBounds.width - pad * 2);
        int h = Math.max(1, trackBounds.height - pad * 2);
        int arc = Math.min(ARC, Math.min(w, h));

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

        // Flat frosted glass fill - no gradient
        g2.setColor(scaleAlpha(new Color(250, 250, 250, 140), enabledScale));
        g2.fill(shape);

        // Inner white highlight stroke
        int innerArc = Math.max(arc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 85), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, innerArc, innerArc));

        // Outer dark outline stroke
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 22), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, arc, arc));
        g2.dispose();
    }

    private static Color scaleAlpha(Color color, float scale) {
        int alpha = Math.round(color.getAlpha() * Math.max(0f, Math.min(1f, scale)));
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static class ArrowButton extends JButton {
        private final int direction;
        public ArrowButton(int direction) {
            this.direction = direction;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(16, 16));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            float enabledScale = isEnabled() ? 1f : 0.6f;
            int arc = Math.min(8, Math.min(w, h));
            RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc);
            int innerArc = Math.max(arc - 2, 2);

            // Flat frosted glass fill
            g2.setColor(scaleAlpha(new Color(245, 245, 245, 180), enabledScale));
            g2.fill(shape);
            g2.setColor(scaleAlpha(new Color(255, 255, 255, 90), enabledScale));
            g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, innerArc, innerArc));
            g2.setColor(scaleAlpha(new Color(0, 0, 0, 35), enabledScale));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

            g2.setColor(scaleAlpha(new Color(110, 114, 120, 200), enabledScale));
            int[] x, y;
            if (direction == SwingConstants.NORTH) {
                x = new int[]{w/2-4, w/2, w/2+4};
                y = new int[]{h/2+2, h/2-2, h/2+2};
            } else if (direction == SwingConstants.SOUTH) {
                x = new int[]{w/2-4, w/2, w/2+4};
                y = new int[]{h/2-2, h/2+2, h/2-2};
            } else if (direction == SwingConstants.WEST) {
                x = new int[]{w/2+2, w/2-2, w/2+2};
                y = new int[]{h/2-4, h/2, h/2+4};
            } else { // EAST
                x = new int[]{w/2-2, w/2+2, w/2-2};
                y = new int[]{h/2-4, h/2, h/2+4};
            }
            g2.fillPolygon(x, y, 3);
            g2.dispose();
        }
    }
}
