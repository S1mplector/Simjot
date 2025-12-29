package main.ui.components.calendars;

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
 * Ultra-clean minimalist calendar with flat design and subtle typography.
 * No borders, just elegant spacing and hierarchy.
 */
public class MinimalistCalendar extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent;

    public MinimalistCalendar() {
        this(Theme.getWidgetAccent());
    }

    @SuppressWarnings("unused")
    public MinimalistCalendar(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        this.accent = accent != null ? accent : new Color(100, 100, 100);
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
        int pad = Math.max(10, Math.min(w, h) / 12);

        // Subtle background
        g2.setColor(new Color(250, 250, 252, 200));
        g2.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, 8, 8);

        int contentX = pad + 10;
        int contentW = w - pad * 2 - 20;

        // Month - small caps style
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()).toUpperCase();
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, h / 14)));
        g2.setColor(new Color(120, 120, 130));
        FontMetrics fmMonth = g2.getFontMetrics();
        int monthY = pad + 24;
        g2.drawString(month, contentX + (contentW - fmMonth.stringWidth(month)) / 2, monthY);

        // Large day number
        String dayStr = Integer.toString(today.getDayOfMonth());
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(36, (int)(h * 0.38))));
        g2.setColor(new Color(40, 40, 45));
        FontMetrics fmDay = g2.getFontMetrics();
        int dayX = contentX + (contentW - fmDay.stringWidth(dayStr)) / 2;
        int dayY = h / 2 + fmDay.getAscent() / 3;
        g2.drawString(dayStr, dayX, dayY);

        // Weekday with accent underline
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(11, h / 12)));
        g2.setColor(new Color(80, 80, 90));
        FontMetrics fmWeek = g2.getFontMetrics();
        int weekX = contentX + (contentW - fmWeek.stringWidth(weekday)) / 2;
        int weekY = h - pad - 20;
        g2.drawString(weekday, weekX, weekY);

        // Accent underline
        g2.setColor(accent);
        int lineW = Math.min(40, fmWeek.stringWidth(weekday));
        g2.fillRect(contentX + (contentW - lineW) / 2, weekY + 4, lineW, 2);

        g2.dispose();
    }
}
