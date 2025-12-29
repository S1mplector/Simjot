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
 * Vertical strip calendar with stacked date elements.
 */
public class VerticalCalendar extends JPanel {
    private Color accent;

    public VerticalCalendar() {
        this(new Color(70, 130, 180));
    }

    public VerticalCalendar(Color accent) {
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
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        String day = String.valueOf(today.getDayOfMonth());
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        String year = String.valueOf(today.getYear());

        // Background strip
        int stripW = w - 30;
        g2.setColor(new Color(250, 250, 248));
        g2.fillRoundRect(15, 5, stripW, h - 10, 10, 10);
        
        // Left accent bar
        g2.setColor(accent);
        g2.fillRoundRect(15, 5, 6, h - 10, 3, 3);

        // Month
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(accent);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(month, cx - fm.stringWidth(month) / 2 + 3, 22);

        // Day number
        g2.setFont(new Font("SansSerif", Font.BOLD, 40));
        g2.setColor(new Color(50, 50, 50));
        fm = g2.getFontMetrics();
        g2.drawString(day, cx - fm.stringWidth(day) / 2 + 3, 62);

        // Weekday
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(100, 100, 100));
        fm = g2.getFontMetrics();
        g2.drawString(weekday, cx - fm.stringWidth(weekday) / 2 + 3, 80);

        // Year
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 140));
        fm = g2.getFontMetrics();
        g2.drawString(year, cx - fm.stringWidth(year) / 2 + 3, h - 12);

        // Border
        g2.setColor(new Color(220, 220, 215));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(15, 5, stripW, h - 10, 10, 10);

        g2.dispose();
    }
}
