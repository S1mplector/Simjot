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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Calendar;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Orbital clock with concentric orbits for hours, minutes, seconds.
 */
public class OrbitalClock extends JPanel {
    private Color accent;
    private final Timer timer;

    public OrbitalClock() {
        this(new Color(100, 140, 230));
    }

    public OrbitalClock(Color accent) {
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

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);
        int millis = cal.get(Calendar.MILLISECOND);

        float secAngle = (float) ((sec + millis / 1000.0) / 60.0 * 2 * Math.PI - Math.PI / 2);
        float minAngle = (float) ((min + sec / 60.0) / 60.0 * 2 * Math.PI - Math.PI / 2);
        float hourAngle = (float) ((hour + min / 60.0) / 12.0 * 2 * Math.PI - Math.PI / 2);

        // Center dot
        g2.setColor(accent);
        g2.fillOval(cx - 4, cy - 4, 8, 8);

        // Hour orbit (innermost)
        int hourR = size / 6;
        drawOrbit(g2, cx, cy, hourR, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
        drawPlanet(g2, cx, cy, hourR, hourAngle, 10, accent);

        // Minute orbit (middle)
        int minR = size / 4;
        drawOrbit(g2, cx, cy, minR, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
        drawPlanet(g2, cx, cy, minR, minAngle, 8, lighter(accent));

        // Second orbit (outer)
        int secR = size / 3;
        drawOrbit(g2, cx, cy, secR, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 25));
        drawPlanet(g2, cx, cy, secR, secAngle, 6, lighter(lighter(accent)));

        // Time label
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(100, 100, 100));
        String time = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), min);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(time, cx - fm.stringWidth(time) / 2, cy + secR + 20);

        g2.dispose();
    }

    private void drawOrbit(Graphics2D g2, int cx, int cy, int r, Color c) {
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
    }

    private void drawPlanet(Graphics2D g2, int cx, int cy, int r, float angle, int planetSize, Color c) {
        int px = (int) (cx + r * Math.cos(angle));
        int py = (int) (cy + r * Math.sin(angle));

        // Glow
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
        g2.fillOval(px - planetSize, py - planetSize, planetSize * 2, planetSize * 2);

        // Planet
        g2.setColor(c);
        g2.fillOval(px - planetSize / 2, py - planetSize / 2, planetSize, planetSize);
    }

    private Color lighter(Color c) {
        return new Color(
            Math.min(255, c.getRed() + 40),
            Math.min(255, c.getGreen() + 40),
            Math.min(255, c.getBlue() + 40)
        );
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }
}
