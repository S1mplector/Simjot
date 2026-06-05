/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.slider;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * A horizontal slider that lets the user pick their mood on a 0-100 scale.
 * By default the track shows a cool→neutral→warm gradient, and the thumb is
 * a plain circular knob.
 */
public class MoodSlider extends JSlider {
    private float gradientAlpha = 1f;
    private boolean gradientVisible = true;
    private boolean hovered = false;
    private boolean hoverFadeEnabled = false;
    private Timer fadeTimer;
    private static final float FADE_STEP = 0.08f;
    private static final int FADE_DELAY = 16; // ~60fps

    public MoodSlider() {
        super(0, 100, 50);
        setOpaque(false);
        setFocusable(false);
        setPreferredSize(new Dimension(220, 40));
        // Help prevent painting artifacts by leveraging Swing's double buffering
        setDoubleBuffered(true);
        // Install custom UI
        setUI(new MoodSliderUI(this));
    }

    /**
     * Enable or disable the hover fade animation for the gradient.
     * When enabled, the gradient is hidden by default and fades in on hover.
     */
    public void setHoverFadeEnabled(boolean enabled) {
        if (this.hoverFadeEnabled == enabled) return;
        this.hoverFadeEnabled = enabled;
        if (enabled) {
            gradientAlpha = 0f;
            setupHoverAnimation();
        } else {
            gradientAlpha = 1f;
            if (fadeTimer != null) fadeTimer.stop();
        }
        repaint();
    }

    /**
     * Toggle the coloured gradient fill of the track.
     * When disabled, the slider keeps the same shape and thumb
     * but uses a neutral track fill.
     */
    public void setGradientVisible(boolean visible) {
        if (this.gradientVisible == visible) return;
        this.gradientVisible = visible;
        repaint();
    }

    private void setupHoverAnimation() {
        if (fadeTimer != null) return; // already set up
        fadeTimer = new Timer(FADE_DELAY, e -> {
            if (hovered && gradientAlpha < 1f) {
                gradientAlpha = Math.min(1f, gradientAlpha + FADE_STEP);
                repaint();
            } else if (!hovered && gradientAlpha > 0f) {
                gradientAlpha = Math.max(0f, gradientAlpha - FADE_STEP);
                repaint();
            } else {
                fadeTimer.stop();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!hoverFadeEnabled) return;
                hovered = true;
                if (!fadeTimer.isRunning()) fadeTimer.start();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!hoverFadeEnabled) return;
                hovered = false;
                if (!fadeTimer.isRunning()) fadeTimer.start();
            }
        });
    }

    float getGradientAlpha() {
        return gradientAlpha;
    }

    boolean isGradientVisible() {
        return gradientVisible;
    }

    /**
     * Slider UI that paints a colourful track and a circular thumb with a face.
     */
    private static class MoodSliderUI extends BasicSliderUI {
        private static final int TRACK_HEIGHT = 8;
        private static final int THUMB_SIZE = 24;

        public MoodSliderUI(JSlider b) { super(b); }

        @Override
        protected TrackListener createTrackListener(JSlider slider) {
            // Use default to get drag behaviour.
            return super.createTrackListener(slider);
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                d.height = Math.max(d.height, 40);
            }
            return d;
        }

        @Override
        protected void calculateThumbSize() {
            thumbRect.setSize(THUMB_SIZE, THUMB_SIZE);
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

            // Glassy backdrop behind the track
            int plateH = TRACK_HEIGHT + 10;
            int plateTop = trackTop - 5;
            g2.setPaint(new Color(255, 255, 255, 110));
            g2.fillRoundRect(trackLeft, plateTop, trackWidth, plateH, plateH, plateH);
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawRoundRect(trackLeft, plateTop, trackWidth, plateH, plateH, plateH);

            MoodSlider moodSlider = (MoodSlider) slider;
            if (moodSlider.isGradientVisible()) {
                // Build gradient: blue (0) → grey (50) → orange (100) with fade animation
                float alpha = moodSlider.getGradientAlpha();
                if (alpha > 0f) {
                    LinearGradientPaint paint = new LinearGradientPaint(
                            trackLeft, 0, trackLeft + trackWidth, 0,
                            new float[] { 0f, 0.5f, 1f },
                            new Color[] {
                                    new Color(0, 122, 204),       // cool blue
                                    new Color(200, 200, 200),      // neutral grey
                                    new Color(255, 120, 50)        // warm orange
                            });
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.setPaint(paint);
                    g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
                    g2.setComposite(AlphaComposite.SrcOver); // reset
                }
            } else {
                // Same slider style, but neutral track without mood gradient.
                g2.setColor(new Color(186, 193, 205, 185));
                g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            }
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = thumbRect.x;
            int cy = thumbRect.y;
            // Soft shadow
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillOval(cx + 1, cy + 2, THUMB_SIZE - 2, THUMB_SIZE - 2);
            // Glass thumb
            g2.setPaint(new GradientPaint(0, cy, new Color(255, 255, 255, 245),
                    0, cy + THUMB_SIZE, new Color(235, 235, 235, 215)));
            g2.fillOval(cx, cy, THUMB_SIZE, THUMB_SIZE);
            g2.setColor(new Color(160, 160, 160, 160));
            g2.drawOval(cx, cy, THUMB_SIZE - 1, THUMB_SIZE - 1);
            g2.setColor(new Color(255, 255, 255, 180));
            g2.drawOval(cx + 1, cy + 1, THUMB_SIZE - 3, THUMB_SIZE - 3);
            g2.dispose();
        }

        // Remove tick marks & labels
        @Override
        public void paintTicks(Graphics g) {}
        @Override
        public void paintLabels(Graphics g) {}
    }
} 
