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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Ultra-clean minimalist clock with thin hands and subtle hour markers.
 * No numbers, just simplicity.
 */
public class MinimalistClock extends JPanel {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    public MinimalistClock() {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        Timer timer = new Timer(1000, e -> repaint());
        timer.start();
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

        // Subtle outer ring
        g2.setColor(new Color(60, 60, 60, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // Minimal hour markers - just small dots at 12, 3, 6, 9
        int dotR = Math.max(2, (int)(r * 0.025));
        int markerDist = (int)(r * 0.85);
        g2.setColor(new Color(80, 80, 80));
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(i * 90 - 90);
            int mx = cx + (int)(markerDist * Math.cos(angle));
            int my = cy + (int)(markerDist * Math.sin(angle));
            g2.fillOval(mx - dotR, my - dotR, dotR * 2, dotR * 2);
        }

        // Smaller dots for other hours
        int smallDotR = Math.max(1, (int)(r * 0.015));
        g2.setColor(new Color(120, 120, 120, 180));
        for (int i = 0; i < 12; i++) {
            if (i % 3 == 0) continue;
            double angle = Math.toRadians(i * 30 - 90);
            int mx = cx + (int)(markerDist * Math.cos(angle));
            int my = cy + (int)(markerDist * Math.sin(angle));
            g2.fillOval(mx - smallDotR, my - smallDotR, smallDotR * 2, smallDotR * 2);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        double hourAngle = Math.toRadians((hours + minutes / 60.0) * 30 - 90);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 90);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        int hourLen = (int)(r * 0.50);
        int minuteLen = (int)(r * 0.72);
        int secondLen = (int)(r * 0.78);

        // Hour hand - thin line
        g2.setColor(new Color(50, 50, 50));
        g2.setStroke(new BasicStroke(Math.max(2.5f, r * 0.03f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int hx = cx + (int)(hourLen * Math.cos(hourAngle));
        int hy = cy + (int)(hourLen * Math.sin(hourAngle));
        g2.drawLine(cx, cy, hx, hy);

        // Minute hand - slightly thinner
        g2.setStroke(new BasicStroke(Math.max(1.8f, r * 0.022f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int mx = cx + (int)(minuteLen * Math.cos(minuteAngle));
        int my = cy + (int)(minuteLen * Math.sin(minuteAngle));
        g2.drawLine(cx, cy, mx, my);

        // Second hand - hairline
        g2.setColor(new Color(200, 80, 80));
        g2.setStroke(new BasicStroke(Math.max(0.8f, r * 0.01f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int sx = cx + (int)(secondLen * Math.cos(secondAngle));
        int sy = cy + (int)(secondLen * Math.sin(secondAngle));
        g2.drawLine(cx, cy, sx, sy);

        // Tiny center dot
        int centerR = Math.max(2, (int)(r * 0.035));
        g2.setColor(new Color(60, 60, 60));
        g2.fillOval(cx - centerR, cy - centerR, centerR * 2, centerR * 2);

        g2.dispose();
    }
}
