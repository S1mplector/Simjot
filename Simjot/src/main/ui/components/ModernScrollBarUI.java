package main.ui.components;

import java.awt.*;
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
    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = new Color(210, 225, 245);
        this.thumbDarkShadowColor = new Color(180, 200, 230);
        this.thumbHighlightColor = new Color(230, 240, 255);
        this.thumbLightShadowColor = new Color(230, 240, 255);
        this.trackColor = new Color(245, 245, 245);
        this.trackHighlightColor = new Color(230, 240, 255);
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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(210, 225, 245));
        g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 8, 8);
        g2.setColor(new Color(180, 200, 230));
        g2.drawRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width-1, thumbBounds.height-1, 8, 8);
        g2.dispose();
    }
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(245, 245, 245));
        g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, 8, 8);
        g2.dispose();
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
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(120, 120, 120));
            int w = getWidth(), h = getHeight();
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
