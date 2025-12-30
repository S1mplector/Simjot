/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
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
import java.util.Calendar;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Radar sweep style clock with glowing sweep line.
 */
public class RadarClock extends JPanel {
    private Color accent;
    private final Timer timer;

    public RadarClock() {
        this(new Color(0, 200, 100));
    }

    public RadarClock(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(200, 200));
        timer = new Timer(50, e -> repaint());
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        int cx = w / 2;
        int cy = h / 2;
        int radius = size / 2 - 10;

        Calendar cal = Calendar.getInstance();
        int sec = cal.get(Calendar.SECOND);
        int millis = cal.get(Calendar.MILLISECOND);
        double sweepAngle = ((sec + millis / 1000.0) / 60.0) * 2 * Math.PI - Math.PI / 2;

        // Background circle
        g2.setColor(new Color(20, 30, 25));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Grid circles
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
        g2.setStroke(new BasicStroke(1f));
        for (int i = 1; i <= 3; i++) {
            int r = radius * i / 3;
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        // Cross lines
        g2.drawLine(cx - radius, cy, cx + radius, cy);
        g2.drawLine(cx, cy - radius, cx, cy + radius);

        // Sweep trail (fading arc)
        for (int i = 0; i < 30; i++) {
            double angle = sweepAngle - (i * Math.PI / 60);
            int alpha = 150 - i * 5;
            if (alpha < 0) break;
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
            g2.setStroke(new BasicStroke(2f));
            int x2 = (int) (cx + radius * Math.cos(angle));
            int y2 = (int) (cy + radius * Math.sin(angle));
            g2.drawLine(cx, cy, x2, y2);
        }

        // Bright sweep line
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2.5f));
        int sweepX = (int) (cx + radius * Math.cos(sweepAngle));
        int sweepY = (int) (cy + radius * Math.sin(sweepAngle));
        g2.drawLine(cx, cy, sweepX, sweepY);

        // Center dot
        g2.setColor(accent);
        g2.fillOval(cx - 4, cy - 4, 8, 8);

        // Hour markers as "blips"
        int hour = cal.get(Calendar.HOUR);
        int min = cal.get(Calendar.MINUTE);
        double hourAngle = (hour + min / 60.0) / 12.0 * 2 * Math.PI - Math.PI / 2;
        int blipX = (int) (cx + radius * 0.6 * Math.cos(hourAngle));
        int blipY = (int) (cy + radius * 0.6 * Math.sin(hourAngle));
        g2.setColor(new Color(255, 100, 100));
        g2.fillOval(blipX - 4, blipY - 4, 8, 8);

        // Outer ring
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g2.dispose();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }
}
