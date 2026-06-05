/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Calendar;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Word clock that displays time as written text.
 */
public class WordClock extends JPanel {
    private Color accent;
    private final Timer timer;

    private static final String[] HOURS = {
        "twelve", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten", "eleven"
    };

    public WordClock() {
        this(new Color(80, 80, 80));
    }

    public WordClock(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(200, 200));
        timer = new Timer(1000, e -> repaint());
        timer.start();
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

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR);
        int min = cal.get(Calendar.MINUTE);

        String[] lines = getTimeWords(hour, min);

        // Background card
        int cardW = w - 20;
        int cardH = h - 20;
        g2.setColor(new Color(250, 250, 248));
        g2.fillRoundRect(10, 10, cardW, cardH, 12, 12);
        g2.setColor(new Color(220, 220, 215));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(10, 10, cardW, cardH, 12, 12);

        // Draw text lines
        g2.setFont(resolveWordFont(16f));
        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight() + 4;
        int startY = cy - (lines.length * lineHeight) / 2 + fm.getAscent();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int x = cx - fm.stringWidth(line) / 2;
            int y = startY + i * lineHeight;

            // Shadow
            g2.setColor(new Color(0, 0, 0, 20));
            g2.drawString(line, x + 1, y + 1);

            // Text
            g2.setColor(accent);
            g2.drawString(line, x, y);
        }

        g2.dispose();
    }

    private String[] getTimeWords(int hour, int min) {
        String hourWord = HOURS[hour % 12];

        if (min == 0) {
            return new String[]{ hourWord, "o'clock" };
        } else if (min == 15) {
            return new String[]{ "quarter", "past " + hourWord };
        } else if (min == 30) {
            return new String[]{ "half past", hourWord };
        } else if (min == 45) {
            String nextHour = HOURS[(hour + 1) % 12];
            return new String[]{ "quarter", "to " + nextHour };
        } else if (min < 30) {
            return new String[]{ min + " past", hourWord };
        } else {
            String nextHour = HOURS[(hour + 1) % 12];
            return new String[]{ (60 - min) + " to", nextHour };
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }

    private static Font resolveWordFont(float size) {
        String family = "Zapfino";
        Font f = new Font(family, Font.PLAIN, Math.round(size));
        if (!family.equalsIgnoreCase(f.getFamily())) {
            f = new Font("Serif", Font.ITALIC, Math.round(size));
        }
        return f;
    }
}
