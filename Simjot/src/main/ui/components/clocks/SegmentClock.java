/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Modern digital-segment style clock rendered as vectors.
 * Features LCD-style seven-segment display aesthetic in a circular frame.
 */
public class SegmentClock extends JPanel {
    private static final long serialVersionUID = 1L;
    private Color accent;

    public SegmentClock() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public SegmentClock(Color accent) {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        this.accent = accent != null ? accent : new Color(0, 180, 120);
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

        // Dark LCD background
        g2.setColor(new Color(20, 25, 30));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Subtle inner bevel
        g2.setColor(new Color(40, 45, 50));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - r + 3, cy - r + 3, (r - 3) * 2, (r - 3) * 2);

        // Outer metallic ring
        Paint bezel = new LinearGradientPaint(cx, cy - r, cx, cy + r,
                new float[]{0f, 0.3f, 0.5f, 0.7f, 1f},
                new Color[]{new Color(80, 80, 85), new Color(60, 60, 65), new Color(90, 90, 95),
                        new Color(60, 60, 65), new Color(80, 80, 85)});
        g2.setPaint(bezel);
        g2.setStroke(new BasicStroke(Math.max(3f, r * 0.04f)));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour();
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        // Calculate segment dimensions
        int digitH = (int)(r * 0.5);
        int digitW = (int)(digitH * 0.55);
        int segThick = Math.max(2, (int)(digitH * 0.12));
        int gap = (int)(digitW * 0.15);
        int colonW = (int)(digitW * 0.3);

        // Total width: 4 digits + 1 colon
        int totalW = digitW * 4 + gap * 3 + colonW;
        int startX = cx - totalW / 2;
        int digitY = cy - digitH / 2;

        // Draw hour digits
        String hourStr = String.format("%02d", hours);
        drawSegmentDigit(g2, startX, digitY, digitW, digitH, segThick, hourStr.charAt(0) - '0', accent);
        drawSegmentDigit(g2, startX + digitW + gap, digitY, digitW, digitH, segThick, hourStr.charAt(1) - '0', accent);

        // Draw blinking colon
        int colonX = startX + digitW * 2 + gap * 2;
        boolean colonOn = seconds % 2 == 0;
        drawColon(g2, colonX, digitY, colonW, digitH, segThick, colonOn ? accent : new Color(40, 45, 50));

        // Draw minute digits
        String minStr = String.format("%02d", minutes);
        int minStartX = colonX + colonW + gap;
        drawSegmentDigit(g2, minStartX, digitY, digitW, digitH, segThick, minStr.charAt(0) - '0', accent);
        drawSegmentDigit(g2, minStartX + digitW + gap, digitY, digitW, digitH, segThick, minStr.charAt(1) - '0', accent);

        // Seconds arc around the edge
        g2.setStroke(new BasicStroke(Math.max(3f, r * 0.035f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int arcR = r - (int)(r * 0.08);
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
        g2.drawArc(cx - arcR, cy - arcR, arcR * 2, arcR * 2, 90, -360);
        g2.setColor(accent);
        g2.drawArc(cx - arcR, cy - arcR, arcR * 2, arcR * 2, 90, -(seconds * 6));

        // Small seconds display at bottom
        g2.setFont(new Font("Monospaced", Font.BOLD, Math.max(10, (int)(r * 0.14))));
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
        String secStr = String.format(":%02d", seconds);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(secStr, cx - fm.stringWidth(secStr) / 2, cy + digitH / 2 + fm.getHeight());

        g2.dispose();
    }

    // Seven segment display: segments numbered 0-6 (top, top-right, bottom-right, bottom, bottom-left, top-left, middle)
    private static final boolean[][] SEGMENTS = {
            {true, true, true, true, true, true, false},    // 0
            {false, true, true, false, false, false, false}, // 1
            {true, true, false, true, true, false, true},   // 2
            {true, true, true, true, false, false, true},   // 3
            {false, true, true, false, false, true, true},  // 4
            {true, false, true, true, false, true, true},   // 5
            {true, false, true, true, true, true, true},    // 6
            {true, true, true, false, false, false, false}, // 7
            {true, true, true, true, true, true, true},     // 8
            {true, true, true, true, false, true, true}     // 9
    };

    private void drawSegmentDigit(Graphics2D g2, int x, int y, int w, int h, int thick, int digit, Color onColor) {
        Color offColor = new Color(40, 45, 50);
        boolean[] segs = SEGMENTS[digit % 10];

        int hSeg = w - thick;
        int vSeg = (h - thick) / 2 - thick / 2;

        // Segment 0 - top horizontal
        drawHSegment(g2, x + thick / 2, y, hSeg, thick, segs[0] ? onColor : offColor);
        // Segment 1 - top right vertical
        drawVSegment(g2, x + w - thick, y + thick / 2, vSeg, thick, segs[1] ? onColor : offColor);
        // Segment 2 - bottom right vertical
        drawVSegment(g2, x + w - thick, y + h / 2 + thick / 2, vSeg, thick, segs[2] ? onColor : offColor);
        // Segment 3 - bottom horizontal
        drawHSegment(g2, x + thick / 2, y + h - thick, hSeg, thick, segs[3] ? onColor : offColor);
        // Segment 4 - bottom left vertical
        drawVSegment(g2, x, y + h / 2 + thick / 2, vSeg, thick, segs[4] ? onColor : offColor);
        // Segment 5 - top left vertical
        drawVSegment(g2, x, y + thick / 2, vSeg, thick, segs[5] ? onColor : offColor);
        // Segment 6 - middle horizontal
        drawHSegment(g2, x + thick / 2, y + h / 2 - thick / 2, hSeg, thick, segs[6] ? onColor : offColor);
    }

    private void drawHSegment(Graphics2D g2, int x, int y, int len, int thick, Color color) {
        Path2D seg = new Path2D.Double();
        int inset = thick / 3;
        seg.moveTo(x + inset, y + thick / 2.0);
        seg.lineTo(x + thick / 2.0, y);
        seg.lineTo(x + len - thick / 2.0, y);
        seg.lineTo(x + len - inset, y + thick / 2.0);
        seg.lineTo(x + len - thick / 2.0, y + thick);
        seg.lineTo(x + thick / 2.0, y + thick);
        seg.closePath();
        g2.setColor(color);
        g2.fill(seg);
    }

    private void drawVSegment(Graphics2D g2, int x, int y, int len, int thick, Color color) {
        Path2D seg = new Path2D.Double();
        int inset = thick / 3;
        seg.moveTo(x + thick / 2.0, y + inset);
        seg.lineTo(x + thick, y + thick / 2.0);
        seg.lineTo(x + thick, y + len - thick / 2.0);
        seg.lineTo(x + thick / 2.0, y + len - inset);
        seg.lineTo(x, y + len - thick / 2.0);
        seg.lineTo(x, y + thick / 2.0);
        seg.closePath();
        g2.setColor(color);
        g2.fill(seg);
    }

    private void drawColon(Graphics2D g2, int x, int y, int w, int h, int thick, Color color) {
        int dotR = thick;
        int dotX = x + w / 2;
        g2.setColor(color);
        g2.fillOval(dotX - dotR / 2, y + h / 3 - dotR / 2, dotR, dotR);
        g2.fillOval(dotX - dotR / 2, y + h * 2 / 3 - dotR / 2, dotR, dotR);
    }
}
