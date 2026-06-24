/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.calendars;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Calendar styled like a sticky note / post-it.
 * Features slight rotation, folded corner, and handwritten-style text.
 */
public class PostItCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent;

    public PostItCalendar() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public PostItCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        this.accent = accent != null ? accent : new Color(255, 240, 100);
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

        int noteW = (int)(w * 0.8);
        int noteH = (int)(h * 0.8);
        int noteX = (w - noteW) / 2;
        int noteY = (h - noteH) / 2;

        // Slight rotation for natural look
        AffineTransform oldTransform = g2.getTransform();
        g2.rotate(Math.toRadians(-2), cx, cy);

        // Shadow
        g2.setColor(new Color(0, 0, 0, 25));
        g2.fillRect(noteX + 4, noteY + 5, noteW, noteH);

        // Main sticky note body
        g2.setColor(accent);
        g2.fillRect(noteX, noteY, noteW, noteH);

        // Folded corner
        int foldSize = Math.max(12, noteW / 8);
        Path2D fold = new Path2D.Double();
        fold.moveTo(noteX + noteW - foldSize, noteY);
        fold.lineTo(noteX + noteW, noteY);
        fold.lineTo(noteX + noteW, noteY + foldSize);
        fold.closePath();

        // Darken the fold area
        g2.setColor(darken(accent, 0.15f));
        g2.fill(fold);

        // Fold shadow/crease
        Path2D foldBack = new Path2D.Double();
        foldBack.moveTo(noteX + noteW - foldSize, noteY);
        foldBack.lineTo(noteX + noteW - foldSize, noteY + foldSize);
        foldBack.lineTo(noteX + noteW, noteY + foldSize);
        foldBack.closePath();
        g2.setColor(darken(accent, 0.08f));
        g2.fill(foldBack);

        // Subtle gradient overlay for depth
        Paint overlay = new GradientPaint(noteX, noteY, new Color(255, 255, 255, 40),
                noteX, noteY + noteH, new Color(0, 0, 0, 20));
        g2.setPaint(overlay);
        g2.fillRect(noteX, noteY, noteW, noteH);

        // Text color - dark for readability
        Color textColor = getContrastColor(accent);

        // Month at top
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(11, noteH / 10)));
        g2.setColor(new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 180));
        FontMetrics fmMonth = g2.getFontMetrics();
        g2.drawString(month, noteX + (noteW - fmMonth.stringWidth(month)) / 2, noteY + 20);

        // Large day number
        String dayStr = Integer.toString(today.getDayOfMonth());
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(36, (int)(noteH * 0.4))));
        g2.setColor(textColor);
        FontMetrics fmDay = g2.getFontMetrics();
        g2.drawString(dayStr, noteX + (noteW - fmDay.stringWidth(dayStr)) / 2,
                noteY + noteH / 2 + fmDay.getAscent() / 3);

        // Weekday at bottom
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, noteH / 11)));
        g2.setColor(new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 200));
        FontMetrics fmWeek = g2.getFontMetrics();
        g2.drawString(weekday, noteX + (noteW - fmWeek.stringWidth(weekday)) / 2,
                noteY + noteH - 12);

        g2.setTransform(oldTransform);
        g2.dispose();
    }

    private Color darken(Color c, float amount) {
        return new Color(
                Math.max(0, (int)(c.getRed() * (1 - amount))),
                Math.max(0, (int)(c.getGreen() * (1 - amount))),
                Math.max(0, (int)(c.getBlue() * (1 - amount)))
        );
    }

    private Color getContrastColor(Color bg) {
        // Calculate luminance
        double lum = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return lum > 0.5 ? new Color(50, 50, 55) : new Color(250, 250, 252);
    }
}
