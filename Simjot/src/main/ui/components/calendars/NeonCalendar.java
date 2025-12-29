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
 * Neon glowing calendar with dark background.
 */
public class NeonCalendar extends JPanel {
    private Color accent;

    public NeonCalendar() {
        this(new Color(255, 0, 150));
    }

    public NeonCalendar(Color accent) {
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
        int cy = h / 2;

        LocalDate today = LocalDate.now();
        String day = String.valueOf(today.getDayOfMonth());
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());

        // Dark background
        g2.setColor(new Color(20, 20, 30));
        g2.fillRoundRect(10, 10, w - 20, h - 20, 16, 16);

        // Outer glow border
        for (int i = 3; i >= 1; i--) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20 * i));
            g2.setStroke(new BasicStroke(i * 2f));
            g2.drawRoundRect(10, 10, w - 20, h - 20, 16, 16);
        }

        // Neon border
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(12, 12, w - 24, h - 24, 14, 14);

        // Month with glow
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int monthX = cx - fm.stringWidth(month) / 2;
        // Glow
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        g2.drawString(month, monthX, 32);
        g2.drawString(month, monthX + 1, 32);
        g2.drawString(month, monthX - 1, 32);
        // Text
        g2.setColor(accent);
        g2.drawString(month, monthX, 32);

        // Day number with strong glow
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        fm = g2.getFontMetrics();
        int dayX = cx - fm.stringWidth(day) / 2;
        int dayY = cy + 12;
        // Glow layers
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 15 * i));
            g2.drawString(day, dayX - i, dayY);
            g2.drawString(day, dayX + i, dayY);
            g2.drawString(day, dayX, dayY - i);
            g2.drawString(day, dayX, dayY + i);
        }
        // Core text
        g2.setColor(Color.WHITE);
        g2.drawString(day, dayX, dayY);

        // Weekday
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200));
        fm = g2.getFontMetrics();
        g2.drawString(weekday, cx - fm.stringWidth(weekday) / 2, h - 22);

        g2.dispose();
    }
}
