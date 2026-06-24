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
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Circular calendar with date displayed in a modern radial design.
 * Features progress arc showing position in month.
 */
public class CircularCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent;

    public CircularCalendar() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public CircularCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        this.accent = accent != null ? accent : new Color(80, 140, 200);
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
        int cx = w / 2;
        int cy = h / 2;
        int r = Math.min(w, h) / 2 - 8;

        // Outer circle - subtle
        g2.setColor(new Color(240, 240, 245));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Progress arc showing day of month progress
        int daysInMonth = today.lengthOfMonth();
        int dayOfMonth = today.getDayOfMonth();
        double progress = (double) dayOfMonth / daysInMonth;
        int progressAngle = (int)(progress * 360);

        int arcThick = Math.max(6, (int)(r * 0.1));
        int arcR = r - arcThick / 2 - 2;

        // Background track
        g2.setColor(new Color(220, 220, 228));
        g2.setStroke(new BasicStroke(arcThick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(cx - arcR, cy - arcR, arcR * 2, arcR * 2, 90, -360);

        // Progress arc
        g2.setColor(accent);
        g2.drawArc(cx - arcR, cy - arcR, arcR * 2, arcR * 2, 90, -progressAngle);

        // Inner white circle
        int innerR = r - arcThick - 6;
        g2.setColor(Color.WHITE);
        g2.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Month abbreviation at top
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, r / 8)));
        g2.setColor(accent);
        FontMetrics fmMonth = g2.getFontMetrics();
        g2.drawString(month, cx - fmMonth.stringWidth(month) / 2, cy - innerR / 3);

        // Large day number in center
        String dayStr = Integer.toString(dayOfMonth);
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(28, (int)(innerR * 0.7))));
        g2.setColor(new Color(50, 50, 55));
        FontMetrics fmDay = g2.getFontMetrics();
        g2.drawString(dayStr, cx - fmDay.stringWidth(dayStr) / 2, cy + fmDay.getAscent() / 3);

        // Weekday at bottom
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, r / 9)));
        g2.setColor(new Color(120, 120, 130));
        FontMetrics fmWeek = g2.getFontMetrics();
        g2.drawString(weekday, cx - fmWeek.stringWidth(weekday) / 2, cy + innerR / 2 + 4);

        // Year at very bottom (small)
        String year = Integer.toString(today.getYear());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, r / 10)));
        g2.setColor(new Color(160, 160, 170));
        FontMetrics fmYear = g2.getFontMetrics();
        g2.drawString(year, cx - fmYear.stringWidth(year) / 2, cy + innerR / 2 + fmYear.getHeight() + 2);

        g2.dispose();
    }
}
