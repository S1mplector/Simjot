package main.ui.components;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * A horizontal slider that lets the user pick their mood on a 0-100 scale.
 * The track shows a cool→neutral→warm gradient and the thumb displays a
 * simple emoticon that changes expression according to the value.
 */
public class MoodSlider extends JSlider {
    public MoodSlider() {
        super(0, 100, 50);
        setOpaque(false);
        setFocusable(false);
        setPreferredSize(new Dimension(220, 40));
        // Help prevent painting artifacts by leveraging Swing's double buffering
        setDoubleBuffered(true);
        // Ensure background matches toolbar exact color when needed (#e7e7e7)
        setBackground(new Color(0xE7, 0xE7, 0xE7));
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

            // Clear the repaint region using the exact toolbar color (#e7e7e7)
            // instead of relying on parent background, for visual consistency.
            Shape clip = g2.getClip();
            if (clip != null) {
                Rectangle cb = clip.getBounds();
                Color bg = new Color(0xE7, 0xE7, 0xE7);
                Color old = g2.getColor();
                g2.setColor(bg);
                g2.fillRect(cb.x, cb.y, cb.width, cb.height);
                g2.setColor(old);
            }

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
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Thumb circle
            g2.setColor(Color.WHITE);
            g2.fillOval(thumbRect.x, thumbRect.y, THUMB_SIZE, THUMB_SIZE);
            g2.setColor(new Color(130, 130, 130, 140));
            g2.drawOval(thumbRect.x, thumbRect.y, THUMB_SIZE - 1, THUMB_SIZE - 1);

            // Draw simplistic face
            int val = slider.getValue();
            int cx = thumbRect.x + THUMB_SIZE / 2;
            int cy = thumbRect.y + THUMB_SIZE / 2;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.DARK_GRAY);

            // Eyes
            g2.fillOval(cx - 6, cy - 3, 3, 3);
            g2.fillOval(cx + 3, cy - 3, 3, 3);

            // Mouth expression based on value
            if (val < 33) { // Sad
                g2.drawArc(cx - 6, cy - 1, 12, 8, 20, 140);
            } else if (val < 66) { // Neutral
                g2.drawLine(cx - 6, cy + 3, cx + 6, cy + 3);
            } else { // Happy
                g2.drawArc(cx - 6, cy - 5, 12, 10, 200, 140);
            }
            g2.dispose();
        }

        // Remove tick marks & labels
        @Override
        public void paintTicks(Graphics g) {}
        @Override
        public void paintLabels(Graphics g) {}
    }
} 