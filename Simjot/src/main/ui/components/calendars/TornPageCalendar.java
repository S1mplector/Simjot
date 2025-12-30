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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Calendar styled like a torn page from a day calendar.
 * Features paper texture effect and torn edge at top.
 */
public class TornPageCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent;

    public TornPageCalendar() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public TornPageCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        this.accent = accent != null ? accent : new Color(200, 60, 60);
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

        int rectX = pad;
        int rectY = pad + 8;
        int rectW = w - pad * 2;
        int rectH = h - pad * 2 - 8;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fillRoundRect(rectX + 3, rectY + 4, rectW, rectH, 4, 4);

        // Paper background
        g2.setColor(new Color(255, 253, 250));
        g2.fillRoundRect(rectX, rectY, rectW, rectH, 4, 4);

        // Torn edge at top - jagged path
        Path2D tornEdge = new Path2D.Double();
        tornEdge.moveTo(rectX, rectY + 6);
        int segments = 12;
        double segW = rectW / (double) segments;
        for (int i = 0; i <= segments; i++) {
            double x = rectX + i * segW;
            double yOffset = (i % 2 == 0) ? 0 : 3 + Math.random() * 3;
            if (i == 0) {
                tornEdge.lineTo(x, rectY + yOffset);
            } else {
                tornEdge.lineTo(x, rectY + yOffset);
            }
        }
        tornEdge.lineTo(rectX + rectW, rectY + 6);
        tornEdge.closePath();

        g2.setColor(new Color(255, 253, 250));
        g2.fill(tornEdge);

        // Red header stripe
        int headerH = Math.max(24, rectH / 5);
        g2.setColor(accent);
        g2.fillRect(rectX, rectY + 6, rectW, headerH);

        // Month in header
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, rectH / 8)));
        g2.setColor(Color.WHITE);
        FontMetrics fmMonth = g2.getFontMetrics();
        int monthX = rectX + (rectW - fmMonth.stringWidth(month)) / 2;
        int monthY = rectY + 6 + (headerH + fmMonth.getAscent()) / 2 - 2;
        g2.drawString(month, monthX, monthY);

        // Large day number
        String dayStr = Integer.toString(today.getDayOfMonth());
        g2.setFont(new Font("Serif", Font.BOLD, Math.max(40, (int)(rectH * 0.42))));
        g2.setColor(new Color(40, 40, 40));
        FontMetrics fmDay = g2.getFontMetrics();
        int dayX = rectX + (rectW - fmDay.stringWidth(dayStr)) / 2;
        int dayY = rectY + headerH + 6 + (rectH - headerH - 30) / 2 + fmDay.getAscent() / 2;
        g2.drawString(dayStr, dayX, dayY);

        // Weekday at bottom
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, rectH / 10)));
        g2.setColor(new Color(100, 100, 100));
        FontMetrics fmWeek = g2.getFontMetrics();
        int weekX = rectX + (rectW - fmWeek.stringWidth(weekday)) / 2;
        int weekY = rectY + rectH - 10;
        g2.drawString(weekday, weekX, weekY);

        // Subtle paper texture lines
        g2.setColor(new Color(0, 0, 0, 8));
        g2.setStroke(new BasicStroke(0.5f));
        for (int y = rectY + headerH + 20; y < rectY + rectH - 15; y += 8) {
            g2.drawLine(rectX + 10, y, rectX + rectW - 10, y);
        }

        // Border
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(rectX, rectY + 6, rectW, rectH - 6, 4, 4);

        g2.dispose();
    }
}
