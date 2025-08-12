package main.ui.components.scrollbar;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class AeroScrollBarUI extends BasicScrollBarUI {
    private static final Color TRACK = new Color(245, 245, 245);
    private static final Color THUMB_TOP = new Color(221, 236, 248);
    private static final Color THUMB_BOTTOM = new Color(199, 216, 235);
    private static final Color THUMB_BORDER = new Color(160, 180, 200);

    @Override
    protected void configureScrollBarColors() {
        this.trackColor = TRACK;
        this.thumbColor = THUMB_BOTTOM; // not used directly; we custom paint
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(TRACK);
        g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, 8, 8);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle tb) {
        if (!c.isEnabled() || tb.width > tb.height && tb.height < 2) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gp = new GradientPaint(0, tb.y, THUMB_TOP, 0, tb.y + tb.height, THUMB_BOTTOM);
        g2.setPaint(gp);
        g2.fillRoundRect(tb.x, tb.y, tb.width, tb.height, 8, 8);
        g2.setColor(THUMB_BORDER);
        g2.drawRoundRect(tb.x, tb.y, tb.width - 1, tb.height - 1, 8, 8);
        g2.dispose();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) { return arrow(orientation); }
    @Override
    protected JButton createIncreaseButton(int orientation) { return arrow(orientation); }

    private JButton arrow(int direction) {
        return new ArrowButton(direction);
    }

    private static class ArrowButton extends JButton {
        private final int direction;
        ArrowButton(int direction) {
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
