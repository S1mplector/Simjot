/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.clocks;

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
 * Binary clock showing time in binary LED columns.
 */
public class BinaryClock extends JPanel {
    private Color accent;
    private final Timer timer;

    public BinaryClock() {
        this(new Color(0, 180, 120));
    }

    public BinaryClock(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(200, 200));
        timer = new Timer(1000, e -> repaint());
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
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);

        // Binary columns: H1 H2 : M1 M2 : S1 S2
        int[] digits = {hour / 10, hour % 10, min / 10, min % 10, sec / 10, sec % 10};
        int[] maxBits = {2, 4, 3, 4, 3, 4}; // max bits needed for each column

        int dotSize = size / 14;
        int colSpacing = size / 7;
        int rowSpacing = dotSize + 4;
        int startX = cx - (colSpacing * 3);
        int startY = cy - (rowSpacing * 2);

        Color dimColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);
        Color brightColor = accent;

        for (int col = 0; col < 6; col++) {
            int digit = digits[col];
            int bits = maxBits[col];
            int x = startX + col * colSpacing + (col >= 2 ? 8 : 0) + (col >= 4 ? 8 : 0);

            for (int row = 0; row < 4; row++) {
                int y = startY + (3 - row) * rowSpacing;
                int bitValue = 1 << row;

                if (row < bits) {
                    boolean on = (digit & bitValue) != 0;
                    g2.setColor(on ? brightColor : dimColor);
                    g2.fillOval(x - dotSize / 2, y - dotSize / 2, dotSize, dotSize);
                    if (on) {
                        // Glow effect
                        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                        g2.fillOval(x - dotSize / 2 - 3, y - dotSize / 2 - 3, dotSize + 6, dotSize + 6);
                        g2.setColor(brightColor);
                        g2.fillOval(x - dotSize / 2, y - dotSize / 2, dotSize, dotSize);
                    }
                }
            }
        }

        // Draw separator dots
        g2.setColor(accent);
        int sepY1 = startY + rowSpacing;
        int sepY2 = startY + rowSpacing * 2;
        int sep1X = startX + 2 * colSpacing + 4;
        int sep2X = startX + 4 * colSpacing + 12;
        int sepSize = dotSize / 2;
        g2.fillOval(sep1X - sepSize / 2, sepY1 - sepSize / 2, sepSize, sepSize);
        g2.fillOval(sep1X - sepSize / 2, sepY2 - sepSize / 2, sepSize, sepSize);
        g2.fillOval(sep2X - sepSize / 2, sepY1 - sepSize / 2, sepSize, sepSize);
        g2.fillOval(sep2X - sepSize / 2, sepY2 - sepSize / 2, sepSize, sepSize);

        // Label
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(120, 120, 120));
        String timeStr = String.format("%02d:%02d:%02d", hour, min, sec);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(timeStr, cx - fm.stringWidth(timeStr) / 2, startY + rowSpacing * 4 + 16);

        g2.dispose();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }
}
