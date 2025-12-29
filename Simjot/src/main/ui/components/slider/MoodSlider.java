package main.ui.components.slider;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * A horizontal slider that lets the user pick their mood on a 0-100 scale.
 * The track shows a cool→neutral→warm gradient and the thumb is a plain
 * circular knob.
 */
public class MoodSlider extends JSlider {
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

            // Build gradient: blue (0) → grey (50) → orange (100)
            LinearGradientPaint paint = new LinearGradientPaint(
                    trackLeft, 0, trackLeft + trackWidth, 0,
                    new float[] { 0f, 0.5f, 1f },
                    new Color[] {
                            new Color(0, 122, 204),       // cool blue
                            new Color(200, 200, 200),      // neutral grey
                            new Color(255, 120, 50)        // warm orange
                    });
            g2.setPaint(paint);
            g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
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
