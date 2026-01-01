/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.calendars;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.JPanel;

/**
 * Retro 70s style calendar with warm colors and rounded shapes.
 */
public class RetroCalendar extends JPanel {
    private Color accent;

    public RetroCalendar() {
        this(new Color(210, 105, 30));
    }

    public RetroCalendar(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(150, 150));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;

        LocalDate today = LocalDate.now();
        String day = String.valueOf(today.getDayOfMonth());
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());

        // Background with retro gradient
        GradientPaint bg = new GradientPaint(0, 0, new Color(255, 245, 230), 0, h, new Color(255, 235, 210));
        g2.setPaint(bg);
        g2.fillRoundRect(10, 10, w - 20, h - 20, 20, 20);

        // Top decorative band
        g2.setColor(accent);
        g2.fillRoundRect(10, 10, w - 20, 28, 20, 20);
        g2.fillRect(10, 28, w - 20, 10);

        // Month in band
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(month.toUpperCase(), cx - fm.stringWidth(month.toUpperCase()) / 2, 28);

        // Large day number with circle background
        int circleR = 32;
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
        g2.fillOval(cx - circleR, 50, circleR * 2, circleR * 2);

        g2.setFont(new Font("SansSerif", Font.BOLD, 34));
        g2.setColor(new Color(80, 60, 40));
        fm = g2.getFontMetrics();
        g2.drawString(day, cx - fm.stringWidth(day) / 2, 85);

        // Weekday below
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(accent);
        fm = g2.getFontMetrics();
        g2.drawString(weekday, cx - fm.stringWidth(weekday) / 2, h - 22);

        // Border
        g2.setColor(new Color(200, 180, 160));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(10, 10, w - 20, h - 20, 20, 20);

        g2.dispose();
    }
}
