/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.calendars;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

import main.ui.theme.Theme;

/**
 * Calendar styled after the Clannad visual novel date display.
 * Features an image template with overlaid text for month, day, and weekday.
 */
public class ClannadCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private BufferedImage templateImage;
    @SuppressWarnings("unused")
    private Color accent;

    // Text colors matching the Clannad style
    private static final Color MONTH_TEXT_COLOR = new Color(95, 55, 35);
    private static final Color DAY_TEXT_COLOR = new Color(180, 155, 80);
    private static final Color DAY_STROKE_COLOR = new Color(100, 60, 40);
    private static final Color WEEKDAY_TEXT_COLOR = new Color(85, 50, 35);

    public ClannadCalendar() {
        this(Theme.getWidgetAccent());
    }

    public ClannadCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(255, 278));
        this.accent = accent;
        loadTemplateImage();
        timer = new Timer(60000, e -> tick());
        timer.start();
    }

    private void loadTemplateImage() {
        try {
            templateImage = ImageIO.read(getClass().getResourceAsStream("/img/misc/clannad_date.png"));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("ClannadCalendar: Could not load template image: " + e.getMessage());
        }
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
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();

        if (templateImage != null) {
            // Scale image to fit panel while maintaining aspect ratio
            double imgAspect = (double) templateImage.getWidth() / templateImage.getHeight();
            double panelAspect = (double) w / h;
            
            int drawW, drawH, drawX, drawY;
            if (imgAspect > panelAspect) {
                drawW = w;
                drawH = (int) (w / imgAspect);
                drawX = 0;
                drawY = (h - drawH) / 2;
            } else {
                drawH = h;
                drawW = (int) (h * imgAspect);
                drawX = (w - drawW) / 2;
                drawY = 0;
            }
            
            g2.drawImage(templateImage, drawX, drawY, drawW, drawH, null);
            
            // Calculate positions relative to drawn image
            // These percentages are based on analyzing the template image
            double scaleX = (double) drawW / templateImage.getWidth();
            double scaleY = (double) drawH / templateImage.getHeight();
            
            // Month text position (orange oval area at top) 
            // Oval center is approximately at 50% x, 13% y of the image
            int monthCenterX = drawX + (int)(templateImage.getWidth() * 0.50 * scaleX);
            int monthCenterY = drawY + (int)(templateImage.getHeight() * 0.27 * scaleY);
            
            // Day number position (center of main circle)
            // Center is approximately at 48% x, 48% y
            int dayCenterX = drawX + (int)(templateImage.getWidth() * 0.48 * scaleX);
            int dayCenterY = drawY + (int)(templateImage.getHeight() * 0.50 * scaleY);
            
            // Weekday position (small circle at bottom right)
            // Center is approximately at 80% x, 82% y
            int weekdayCenterX = drawX + (int)(templateImage.getWidth() * 0.805 * scaleX);
            int weekdayCenterY = drawY + (int)(templateImage.getHeight() * 0.825 * scaleY);
            
            // Draw month name
            String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            int monthFontSize = Math.max(12, (int)(drawH * 0.11));
            Font monthFont = new Font("Serif", Font.BOLD, monthFontSize);
            g2.setFont(monthFont);
            FontMetrics fmMonth = g2.getFontMetrics();
            int monthX = monthCenterX - fmMonth.stringWidth(month) / 2;
            int monthY = monthCenterY + fmMonth.getAscent() / 3;
            g2.setColor(MONTH_TEXT_COLOR);
            g2.drawString(month, monthX, monthY);
            
            // Draw day number with outline effect for visibility
            String dayStr = Integer.toString(today.getDayOfMonth());
            int dayFontSize = Math.max(36, (int)(drawH * 0.38));
            Font dayFont = new Font("Serif", Font.BOLD, dayFontSize);
            g2.setFont(dayFont);
            FontMetrics fmDay = g2.getFontMetrics();
            int dayX = dayCenterX - fmDay.stringWidth(dayStr) / 2;
            int dayY = dayCenterY + fmDay.getAscent() / 3;
            
            // Draw stroke/outline
            g2.setColor(DAY_STROKE_COLOR);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        g2.drawString(dayStr, dayX + dx, dayY + dy);
                    }
                }
            }
            // Draw main text
            g2.setColor(DAY_TEXT_COLOR);
            g2.drawString(dayStr, dayX, dayY);
            
            // Draw weekday abbreviation (e.g., "Tue.")
            String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ".";
            int weekdayFontSize = Math.max(9, (int)(drawH * 0.085));
            Font weekdayFont = new Font("Serif", Font.BOLD, weekdayFontSize);
            g2.setFont(weekdayFont);
            FontMetrics fmWeekday = g2.getFontMetrics();
            int weekdayX = weekdayCenterX - fmWeekday.stringWidth(weekday) / 2;
            int weekdayY = weekdayCenterY + fmWeekday.getAscent() / 3;
            g2.setColor(WEEKDAY_TEXT_COLOR);
            g2.drawString(weekday, weekdayX, weekdayY);
        } else {
            // Fallback if image not loaded - draw placeholder
            g2.setColor(new Color(200, 150, 150));
            g2.fillRoundRect(10, 10, w - 20, h - 20, 20, 20);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            String msg = "Clannad";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
        }

        g2.dispose();
    }
}
