/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.slider;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * A neutral slider used for image sizing controls in floating overlays.
 * Its visuals intentionally avoid mood-specific gradients.
 */
public class ImageSizeSlider extends JSlider {
    public ImageSizeSlider() {
        super(0, 100, 50);
        setOpaque(false);
        setFocusable(false);
        setPreferredSize(new Dimension(190, 28));
        setDoubleBuffered(true);
        setUI(new ImageSizeSliderUI(this));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Replace previous pixels in the repaint region to avoid thumb trails while dragging.
        g2.setComposite(AlphaComposite.Src);
        g2.setColor(resolveClearColor());
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    private Color resolveClearColor() {
        Color self = getBackground();
        if (self != null && self.getAlpha() > 0) {
            return self;
        }
        Container p = getParent();
        while (p != null) {
            Color bg = p.getBackground();
            if (bg != null && bg.getAlpha() > 0) {
                return bg;
            }
            p = p.getParent();
        }
        return new Color(245, 245, 245, 170);
    }

    private static class ImageSizeSliderUI extends BasicSliderUI {
        private static final int TRACK_HEIGHT = 7;
        private static final int THUMB_WIDTH = 15;
        private static final int THUMB_HEIGHT = 23;

        ImageSizeSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                d.height = Math.max(d.height, 28);
            }
            return d;
        }

        @Override
        protected void calculateThumbSize() {
            thumbRect.setSize(THUMB_WIDTH, THUMB_HEIGHT);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int trackLeft = trackRect.x;
            int trackTop = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2;
            int trackWidth = trackRect.width;
            if (trackWidth <= 0) {
                g2.dispose();
                return;
            }

            int arc = TRACK_HEIGHT;
            g2.setColor(new Color(35, 44, 60, 70));
            g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, arc, arc);

            int valuePos = xPositionForValue(slider.getValue());
            int fillWidth = Math.max(0, Math.min(trackWidth, valuePos - trackLeft));
            if (fillWidth > 0) {
                LinearGradientPaint fill = new LinearGradientPaint(
                        trackLeft, 0, trackLeft + fillWidth, 0,
                        new float[] { 0f, 1f },
                        new Color[] {
                                new Color(96, 186, 255, 220),
                                new Color(34, 125, 226, 230)
                        });
                g2.setPaint(fill);
                g2.fillRoundRect(trackLeft, trackTop, fillWidth, TRACK_HEIGHT, arc, arc);
            }

            g2.setColor(new Color(255, 255, 255, 105));
            g2.drawRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, arc, arc);

            // Subtle quarter markers for orientation while dragging.
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(255, 255, 255, 95));
            for (int i = 1; i <= 3; i++) {
                int markerX = trackLeft + (trackWidth * i) / 4;
                g2.drawLine(markerX, trackTop - 3, markerX, trackTop + TRACK_HEIGHT + 3);
            }

            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = thumbRect.x;
            int y = thumbRect.y;
            int w = THUMB_WIDTH;
            int h = THUMB_HEIGHT;
            RoundRectangle2D.Float thumb = new RoundRectangle2D.Float(x, y, w, h, 8f, 8f);

            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRoundRect(x + 1, y + 2, w - 1, h - 1, 8, 8);

            g2.setPaint(new GradientPaint(0, y, new Color(255, 255, 255, 250),
                    0, y + h, new Color(224, 231, 241, 240)));
            g2.fill(thumb);

            g2.setColor(new Color(120, 132, 150, 180));
            g2.draw(thumb);

            int gripX = x + (w / 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(88, 100, 120, 150));
            g2.drawLine(gripX - 3, y + 7, gripX - 3, y + h - 7);
            g2.drawLine(gripX, y + 7, gripX, y + h - 7);
            g2.drawLine(gripX + 3, y + 7, gripX + 3, y + h - 7);

            g2.dispose();
        }

        @Override
        public void paintTicks(Graphics g) {}

        @Override
        public void paintLabels(Graphics g) {}
    }
}
