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

package main.ui.components.calendars;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Modern glassmorphism-style calendar with frosted glass effect,
 * blur simulation, and subtle borders.
 */
public class GlassCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent;

    public GlassCalendar() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public GlassCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        this.accent = accent != null ? accent : new Color(100, 120, 200);
        timer = new Timer(60000, e -> tick());
        timer.start();
    }

    public void setAccent(Color c) {
        if (c != null) {
            this.accent = c;
            repaint();
        }
    }

    private void tick() {
        LocalDate now = LocalDate.now();
        if (!now.equals(today)) {
            today = now;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        int pad = Math.max(8, Math.min(w, h) / 14);
        int arc = Math.max(16, Math.min(w, h) / 6);

        int rectX = pad;
        int rectY = pad;
        int rectW = w - pad * 2;
        int rectH = h - pad * 2;

        // Soft shadow
        for (int i = 4; i >= 0; i--) {
            int alpha = 8 + i * 4;
            g2.setColor(new Color(0, 0, 0, alpha));
            g2.fillRoundRect(rectX + i, rectY + i + 2, rectW, rectH, arc, arc);
        }

        // Glass background - semi-transparent with gradient
        Paint glassPaint = new LinearGradientPaint(
                rectX, rectY, rectX + rectW, rectY + rectH,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 160),
                        new Color(255, 255, 255, 120),
                        new Color(255, 255, 255, 140)
                }
        );
        g2.setPaint(glassPaint);
        g2.fillRoundRect(rectX, rectY, rectW, rectH, arc, arc);

        // Frosted noise effect simulation (subtle dots)
        g2.setColor(new Color(255, 255, 255, 30));
        for (int i = 0; i < 60; i++) {
            int nx = rectX + (int)(Math.random() * rectW);
            int ny = rectY + (int)(Math.random() * rectH);
            g2.fillOval(nx, ny, 1, 1);
        }

        // Top highlight (glass reflection)
        Paint highlight = new GradientPaint(
                rectX, rectY, new Color(255, 255, 255, 100),
                rectX, rectY + rectH / 3, new Color(255, 255, 255, 0)
        );
        g2.setPaint(highlight);
        g2.fillRoundRect(rectX + 2, rectY + 2, rectW - 4, rectH / 3, arc - 2, arc - 2);

        // Border with subtle gradient
        g2.setStroke(new BasicStroke(1.5f));
        Paint borderPaint = new LinearGradientPaint(
                rectX, rectY, rectX, rectY + rectH,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 180),
                        new Color(255, 255, 255, 80),
                        new Color(255, 255, 255, 120)
                }
        );
        g2.setPaint(borderPaint);
        g2.drawRoundRect(rectX, rectY, rectW, rectH, arc, arc);

        // Accent line at top
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int lineY = rectY + 18;
        int lineInset = 20;
        g2.drawLine(rectX + lineInset, lineY, rectX + rectW - lineInset, lineY);

        // Month
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, rectH / 9)));
        g2.setColor(new Color(60, 60, 70));
        FontMetrics fmMonth = g2.getFontMetrics();
        g2.drawString(month, rectX + (rectW - fmMonth.stringWidth(month)) / 2, lineY + fmMonth.getHeight() + 4);

        // Large day number with accent tint
        String dayStr = Integer.toString(today.getDayOfMonth());
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(38, (int)(rectH * 0.38))));
        FontMetrics fmDay = g2.getFontMetrics();
        int dayX = rectX + (rectW - fmDay.stringWidth(dayStr)) / 2;
        int dayY = rectY + rectH / 2 + fmDay.getAscent() / 3 + 8;

        // Day shadow
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
        g2.drawString(dayStr, dayX + 1, dayY + 1);
        // Day text
        g2.setColor(new Color(40, 40, 50));
        g2.drawString(dayStr, dayX, dayY);

        // Weekday
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, rectH / 11)));
        g2.setColor(new Color(80, 80, 90));
        FontMetrics fmWeek = g2.getFontMetrics();
        g2.drawString(weekday, rectX + (rectW - fmWeek.stringWidth(weekday)) / 2, rectY + rectH - 14);

        // Year small
        String year = Integer.toString(today.getYear());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, rectH / 13)));
        g2.setColor(new Color(120, 120, 130));
        FontMetrics fmYear = g2.getFontMetrics();
        g2.drawString(year, rectX + rectW - fmYear.stringWidth(year) - 10, rectY + rectH - 14);

        g2.dispose();
    }
}
