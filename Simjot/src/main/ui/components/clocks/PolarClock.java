/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Modern polar/radial clock with concentric arcs for hours, minutes, and seconds.
 * Each time unit is represented as a growing arc around the center.
 */
public class PolarClock extends JPanel {
    private static final long serialVersionUID = 1L;
    private Color accent;

    public PolarClock() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public PolarClock(Color accent) {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        this.accent = accent != null ? accent : new Color(100, 140, 230);
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
        int r = Math.max(20, size / 2 - 8);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Subtle background circle
        g2.setColor(new Color(245, 245, 248));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour();
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        // Arc properties
        int arcThick = Math.max(6, (int)(r * 0.12));
        int gap = Math.max(3, (int)(r * 0.04));

        // Three concentric rings: outer=seconds, middle=minutes, inner=hours
        int secR = r - arcThick / 2 - 2;
        int minR = secR - arcThick - gap;
        int hourR = minR - arcThick - gap;

        // Background tracks (faded)
        g2.setStroke(new BasicStroke(arcThick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g2.setColor(new Color(220, 220, 225));
        g2.drawArc(cx - secR, cy - secR, secR * 2, secR * 2, 90, -360);
        g2.drawArc(cx - minR, cy - minR, minR * 2, minR * 2, 90, -360);
        g2.drawArc(cx - hourR, cy - hourR, hourR * 2, hourR * 2, 90, -360);

        // Seconds arc (full color)
        Color secColor = accent;
        int secAngle = -(seconds * 6);
        g2.setColor(secColor);
        if (seconds > 0) {
            g2.drawArc(cx - secR, cy - secR, secR * 2, secR * 2, 90, secAngle);
        }

        // Minutes arc (slightly different hue)
        Color minColor = shiftHue(accent, 0.1f);
        int minAngle = -(minutes * 6 + seconds / 10);
        g2.setColor(minColor);
        if (minutes > 0 || seconds > 0) {
            g2.drawArc(cx - minR, cy - minR, minR * 2, minR * 2, 90, minAngle);
        }

        // Hours arc (12-hour with fractional minutes)
        Color hourColor = shiftHue(accent, 0.2f);
        int hour12 = hours % 12;
        int hourAngle = -((hour12 * 30) + (minutes / 2));
        g2.setColor(hourColor);
        if (hour12 > 0 || minutes > 0) {
            g2.drawArc(cx - hourR, cy - hourR, hourR * 2, hourR * 2, 90, hourAngle);
        }

        // Center digital time display
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, (int)(r * 0.22))));
        g2.setColor(new Color(60, 60, 70));
        String timeStr = String.format("%02d:%02d", hours, minutes);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(timeStr, cx - fm.stringWidth(timeStr) / 2, cy + fm.getAscent() / 2 - 2);

        // Small labels for each ring
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, (int)(r * 0.09))));
        g2.setColor(new Color(140, 140, 150));
        FontMetrics fmSmall = g2.getFontMetrics();

        // Label positions at 12 o'clock of each ring
        drawLabel(g2, "SEC", cx, cy - secR + arcThick / 2 + fmSmall.getAscent() + 2, fmSmall);
        drawLabel(g2, "MIN", cx, cy - minR + arcThick / 2 + fmSmall.getAscent() + 2, fmSmall);
        drawLabel(g2, "HR", cx, cy - hourR + arcThick / 2 + fmSmall.getAscent() + 2, fmSmall);

        // Outer thin ring
        g2.setColor(new Color(200, 200, 205));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        g2.dispose();
    }

    private void drawLabel(Graphics2D g2, String text, int cx, int y, FontMetrics fm) {
        g2.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }

    private Color shiftHue(Color c, float shift) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[0] = (hsb[0] + shift) % 1.0f;
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }
}
