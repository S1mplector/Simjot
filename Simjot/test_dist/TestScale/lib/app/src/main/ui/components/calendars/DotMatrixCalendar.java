/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.calendars;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.JPanel;

/**
 * LED dot matrix style calendar display.
 */
public class DotMatrixCalendar extends JPanel {
    private Color accent;

    // Simple 3x5 digit patterns
    private static final int[][] DIGITS = {
        {7,5,5,5,7}, // 0
        {2,2,2,2,2}, // 1
        {7,1,7,4,7}, // 2
        {7,1,7,1,7}, // 3
        {5,5,7,1,1}, // 4
        {7,4,7,1,7}, // 5
        {7,4,7,5,7}, // 6
        {7,1,1,1,1}, // 7
        {7,5,7,5,7}, // 8
        {7,5,7,1,7}  // 9
    };

    public DotMatrixCalendar() {
        this(new Color(255, 80, 80));
    }

    public DotMatrixCalendar(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(150, 150));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        LocalDate today = LocalDate.now();
        int day = today.getDayOfMonth();
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();

        // Background panel
        g2.setColor(new Color(30, 30, 35));
        g2.fillRoundRect(10, 10, w - 20, h - 20, 12, 12);

        // Draw month in small text
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        g2.setColor(accent);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(month, (w - fm.stringWidth(month)) / 2, 32);

        // Draw day number as dot matrix
        int dotSize = 6;
        int gap = 2;
        int digitW = 3 * (dotSize + gap);
        int d1 = day / 10;
        int d2 = day % 10;

        int startX = (w - digitW * 2 - 8) / 2;
        int startY = 45;

        Color dimColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30);

        if (d1 > 0) {
            drawDigit(g2, d1, startX, startY, dotSize, gap, accent, dimColor);
        }
        drawDigit(g2, d2, startX + digitW + 8, startY, dotSize, gap, accent, dimColor);

        // Weekday
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(150, 150, 150));
        fm = g2.getFontMetrics();
        g2.drawString(weekday, (w - fm.stringWidth(weekday)) / 2, h - 20);

        // Border glow
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(10, 10, w - 20, h - 20, 12, 12);

        g2.dispose();
    }

    private void drawDigit(Graphics2D g2, int digit, int x, int y, int dotSize, int gap, Color on, Color off) {
        int[] pattern = DIGITS[digit];
        for (int row = 0; row < 5; row++) {
            int bits = pattern[row];
            for (int col = 0; col < 3; col++) {
                boolean lit = ((bits >> (2 - col)) & 1) == 1;
                g2.setColor(lit ? on : off);
                int dx = x + col * (dotSize + gap);
                int dy = y + row * (dotSize + gap);
                g2.fillOval(dx, dy, dotSize, dotSize);
            }
        }
    }
}
