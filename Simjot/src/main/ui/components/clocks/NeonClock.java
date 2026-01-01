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

import main.ui.theme.Theme;

/**
 * Neon-glow style clock with vibrant glowing hands and markers.
 * Features soft glow effects simulating neon lighting.
 */
public class NeonClock extends JPanel {
    private static final long serialVersionUID = 1L;
    private Color accent;

    public NeonClock() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public NeonClock(Color accent) {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        this.accent = accent != null ? accent : new Color(0, 200, 255);
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
        int r = Math.max(20, size / 2 - 10);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Dark background circle
        g2.setColor(new Color(15, 15, 20, 200));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Outer glow ring
        for (int i = 4; i >= 0; i--) {
            float alpha = 0.08f - i * 0.015f;
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(alpha * 255)));
            g2.setStroke(new BasicStroke(3 + i * 2f));
            g2.drawOval(cx - r + 2, cy - r + 2, (r - 2) * 2, (r - 2) * 2);
        }

        // Main ring
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - r + 4, cy - r + 4, (r - 4) * 2, (r - 4) * 2);

        // Hour markers with glow
        int markerDist = (int)(r * 0.82);
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90);
            int mx = cx + (int)(markerDist * Math.cos(angle));
            int my = cy + (int)(markerDist * Math.sin(angle));

            boolean isMain = i % 3 == 0;
            int dotR = isMain ? Math.max(4, (int)(r * 0.045)) : Math.max(2, (int)(r * 0.025));

            // Glow
            for (int j = 3; j >= 0; j--) {
                float a = 0.15f - j * 0.04f;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(a * 255)));
                g2.fillOval(mx - dotR - j * 2, my - dotR - j * 2, (dotR + j * 2) * 2, (dotR + j * 2) * 2);
            }
            g2.setColor(isMain ? Color.WHITE : new Color(200, 200, 200));
            g2.fillOval(mx - dotR, my - dotR, dotR * 2, dotR * 2);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        double hourAngle = Math.toRadians((hours + minutes / 60.0) * 30 - 90);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 90);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        int hourLen = (int)(r * 0.48);
        int minuteLen = (int)(r * 0.68);
        int secondLen = (int)(r * 0.72);

        // Draw glowing hands
        drawGlowingHand(g2, cx, cy, hourAngle, hourLen, accent, 4f);
        drawGlowingHand(g2, cx, cy, minuteAngle, minuteLen, accent, 3f);

        // Second hand - different color (pink/magenta neon)
        Color secColor = new Color(255, 80, 150);
        drawGlowingHand(g2, cx, cy, secondAngle, secondLen, secColor, 1.5f);

        // Center glow dot
        int centerR = Math.max(4, (int)(r * 0.06));
        for (int i = 4; i >= 0; i--) {
            float a = 0.2f - i * 0.04f;
            g2.setColor(new Color(255, 255, 255, (int)(a * 255)));
            g2.fillOval(cx - centerR - i * 3, cy - centerR - i * 3, (centerR + i * 3) * 2, (centerR + i * 3) * 2);
        }
        g2.setColor(Color.WHITE);
        g2.fillOval(cx - centerR, cy - centerR, centerR * 2, centerR * 2);

        g2.dispose();
    }

    private void drawGlowingHand(Graphics2D g2, int cx, int cy, double angle, int len, Color color, float thickness) {
        int ex = cx + (int)(len * Math.cos(angle));
        int ey = cy + (int)(len * Math.sin(angle));

        // Glow layers
        for (int i = 4; i >= 0; i--) {
            float a = 0.12f - i * 0.025f;
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(a * 255)));
            g2.setStroke(new BasicStroke(thickness + i * 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx, cy, ex, ey);
        }

        // Core
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, ex, ey);
    }
}
