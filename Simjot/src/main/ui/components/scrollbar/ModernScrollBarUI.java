/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.scrollbar;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
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

    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = new Color(210, 225, 245, 210);
        this.thumbDarkShadowColor = new Color(150, 175, 210, 160);
        this.thumbHighlightColor = new Color(235, 245, 255, 200);
        this.thumbLightShadowColor = new Color(235, 245, 255, 200);
        this.trackColor = new Color(250, 250, 250, 150);
        this.trackHighlightColor = new Color(235, 245, 255, 140);
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
        boolean vertical = scrollbar == null || scrollbar.getOrientation() == Adjustable.VERTICAL;
        float enabledScale = scrollbar != null && scrollbar.isEnabled() ? 1f : 0.6f;

        int pad = 1;
        int x = thumbBounds.x + pad;
        int y = thumbBounds.y + pad;
        int w = Math.max(1, thumbBounds.width - pad * 2);
        int h = Math.max(1, thumbBounds.height - pad * 2);
        int arc = Math.min(ARC, Math.min(w, h));

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

        Color top = scaleAlpha(new Color(210, 225, 245, 215), enabledScale);
        Color bottom = scaleAlpha(new Color(180, 205, 235, 200), enabledScale);
        GradientPaint base = vertical
            ? new GradientPaint(0, y, top, 0, y + h, bottom)
            : new GradientPaint(x, 0, top, x + w, 0, bottom);
        g2.setPaint(base);
        g2.fill(shape);

        GradientPaint sheen = vertical
            ? new GradientPaint(0, y, scaleAlpha(new Color(255, 255, 255, 120), enabledScale),
                                0, y + h * 0.55f, scaleAlpha(new Color(255, 255, 255, 25), enabledScale))
            : new GradientPaint(x, 0, scaleAlpha(new Color(255, 255, 255, 120), enabledScale),
                                x + w * 0.55f, 0, scaleAlpha(new Color(255, 255, 255, 25), enabledScale));
        g2.setPaint(sheen);
        g2.fill(shape);

        GradientPaint shadow = vertical
            ? new GradientPaint(0, y + h * 0.45f, scaleAlpha(new Color(0, 0, 0, 12), enabledScale),
                                0, y + h, scaleAlpha(new Color(0, 0, 0, 35), enabledScale))
            : new GradientPaint(x + w * 0.45f, 0, scaleAlpha(new Color(0, 0, 0, 12), enabledScale),
                                x + w, 0, scaleAlpha(new Color(0, 0, 0, 35), enabledScale));
        g2.setPaint(shadow);
        g2.fill(shape);

        int innerArc = Math.max(arc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 90), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 1f, y + 1f, w - 2f, h - 2f, innerArc, innerArc));
        g2.setColor(scaleAlpha(new Color(120, 150, 190, 160), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, arc, arc));
        g2.dispose();
    }
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        if (trackBounds == null || trackBounds.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean vertical = scrollbar == null || scrollbar.getOrientation() == Adjustable.VERTICAL;
        float enabledScale = scrollbar != null && scrollbar.isEnabled() ? 1f : 0.7f;

        int pad = 2;
        int x = trackBounds.x + pad;
        int y = trackBounds.y + pad;
        int w = Math.max(1, trackBounds.width - pad * 2);
        int h = Math.max(1, trackBounds.height - pad * 2);
        int arc = Math.min(ARC, Math.min(w, h));

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

        Color top = scaleAlpha(new Color(255, 255, 255, 140), enabledScale);
        Color bottom = scaleAlpha(new Color(235, 240, 245, 110), enabledScale);
        GradientPaint base = vertical
            ? new GradientPaint(0, y, top, 0, y + h, bottom)
            : new GradientPaint(x, 0, top, x + w, 0, bottom);
        g2.setPaint(base);
        g2.fill(shape);

        GradientPaint sheen = vertical
            ? new GradientPaint(0, y, scaleAlpha(new Color(255, 255, 255, 90), enabledScale),
                                0, y + h * 0.55f, scaleAlpha(new Color(255, 255, 255, 20), enabledScale))
            : new GradientPaint(x, 0, scaleAlpha(new Color(255, 255, 255, 90), enabledScale),
                                x + w * 0.55f, 0, scaleAlpha(new Color(255, 255, 255, 20), enabledScale));
        g2.setPaint(sheen);
        g2.fill(shape);

        int innerArc = Math.max(arc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 70), enabledScale));
        g2.draw(new RoundRectangle2D.Float(x + 1f, y + 1f, w - 2f, h - 2f, innerArc, innerArc));
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 25), enabledScale));
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

            GradientPaint base = new GradientPaint(
                0, 0, scaleAlpha(new Color(255, 255, 255, 150), enabledScale),
                0, h, scaleAlpha(new Color(230, 235, 245, 110), enabledScale)
            );
            g2.setPaint(base);
            g2.fill(shape);
            g2.setColor(scaleAlpha(new Color(255, 255, 255, 80), enabledScale));
            g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, innerArc, innerArc));
            g2.setColor(scaleAlpha(new Color(0, 0, 0, 35), enabledScale));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

            g2.setColor(scaleAlpha(new Color(110, 120, 135, 200), enabledScale));
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
