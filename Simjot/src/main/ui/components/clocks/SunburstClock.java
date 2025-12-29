package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Art deco inspired clock with radiating sunburst lines
 * and elegant geometric design.
 */
public class SunburstClock extends JPanel {
    private static final long serialVersionUID = 1L;
    private Color accent;

    public SunburstClock() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public SunburstClock(Color accent) {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        this.accent = accent != null ? accent : new Color(200, 160, 80);
        Timer timer = new Timer(1000, e -> repaint());
        timer.start();
    }

    public void setAccent(Color c) {
        if (c != null) {
            this.accent = c;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int size = Math.min(getWidth(), getHeight());
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int r = Math.max(20, size / 2 - 6);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Cream/ivory dial
        Color dialColor = new Color(255, 252, 245);
        g2.setColor(dialColor);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Sunburst rays emanating from center
        int innerR = (int)(r * 0.25);
        int outerR = (int)(r * 0.75);
        g2.setStroke(new BasicStroke(Math.max(1f, r * 0.012f)));

        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float intensity = (i % 5 == 0) ? 0.6f : 0.25f;
            g2.setColor(new Color(
                (int)(accent.getRed() * intensity),
                (int)(accent.getGreen() * intensity),
                (int)(accent.getBlue() * intensity),
                (int)(100 + intensity * 80)
            ));

            int x1 = cx + (int)(innerR * Math.cos(angle));
            int y1 = cy + (int)(innerR * Math.sin(angle));
            int rayLen = (i % 5 == 0) ? outerR : (int)(r * 0.55);
            int x2 = cx + (int)(rayLen * Math.cos(angle));
            int y2 = cy + (int)(rayLen * Math.sin(angle));
            g2.drawLine(x1, y1, x2, y2);
        }

        // Decorative outer ring with accent
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(Math.max(2f, r * 0.025f)));
        g2.drawOval(cx - r + 3, cy - r + 3, (r - 3) * 2, (r - 3) * 2);

        // Inner decorative circle
        int innerCircleR = (int)(r * 0.82);
        g2.setStroke(new BasicStroke(Math.max(1f, r * 0.012f)));
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
        g2.drawOval(cx - innerCircleR, cy - innerCircleR, innerCircleR * 2, innerCircleR * 2);

        // Hour numerals - art deco style (just 12, 3, 6, 9)
        g2.setColor(new Color(60, 50, 40));
        Font artDecoFont = new Font("SansSerif", Font.BOLD, Math.max(12, (int)(r * 0.16)));
        g2.setFont(artDecoFont);
        FontMetrics fm = g2.getFontMetrics();

        String[] nums = {"12", "3", "6", "9"};
        int[] angles = {-90, 0, 90, 180};
        int numDist = (int)(r * 0.68);

        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(angles[i]);
            int nx = cx + (int)(numDist * Math.cos(angle));
            int ny = cy + (int)(numDist * Math.sin(angle));
            int sw = fm.stringWidth(nums[i]);
            g2.drawString(nums[i], nx - sw / 2, ny + fm.getAscent() / 2 - 2);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        double hourAngle = Math.toRadians((hours + minutes / 60.0) * 30 - 90);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 90);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        int hourLen = (int)(r * 0.42);
        int minuteLen = (int)(r * 0.58);
        int secondLen = (int)(r * 0.52);

        // Art deco hands - diamond/arrow shape
        drawArtDecoHand(g2, cx, cy, hourAngle, hourLen, (int)(r * 0.08), new Color(50, 40, 30));
        drawArtDecoHand(g2, cx, cy, minuteAngle, minuteLen, (int)(r * 0.06), new Color(50, 40, 30));

        // Second hand - thin with accent color
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(Math.max(1.2f, r * 0.015f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int sx = cx + (int)(secondLen * Math.cos(secondAngle));
        int sy = cy + (int)(secondLen * Math.sin(secondAngle));
        g2.drawLine(cx, cy, sx, sy);

        // Center ornament
        int centerR = Math.max(4, (int)(r * 0.07));
        g2.setColor(accent);
        g2.fillOval(cx - centerR, cy - centerR, centerR * 2, centerR * 2);
        g2.setColor(new Color(60, 50, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - centerR, cy - centerR, centerR * 2, centerR * 2);

        g2.dispose();
    }

    private void drawArtDecoHand(Graphics2D g2, int cx, int cy, double angle, int len, int width, Color color) {
        // Diamond-shaped hand
        Path2D p = new Path2D.Double();
        p.moveTo(0, -width / 2.0);
        p.lineTo(len * 0.7, 0);
        p.lineTo(len, 0);
        p.lineTo(len * 0.7, 0);
        p.lineTo(0, width / 2.0);
        p.lineTo(-width * 0.6, 0);
        p.closePath();

        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(angle);
        Shape shp = at.createTransformedShape(p);

        g2.setColor(color);
        g2.fill(shp);
    }
}
