package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Calendar;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Retro flip clock with mechanical flip card style.
 */
public class FlipClock extends JPanel {
    private Color accent;
    private final Timer timer;

    public FlipClock() {
        this(new Color(60, 60, 60));
    }

    public FlipClock(Color accent) {
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
        int size = Math.min(w, h);
        int cx = w / 2;
        int cy = h / 2;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);

        int cardW = size / 4;
        int cardH = (int) (cardW * 1.3);
        int gap = 6;
        int totalW = cardW * 4 + gap * 3 + 16;
        int startX = cx - totalW / 2;
        int cardY = cy - cardH / 2;

        // Draw flip cards
        drawFlipCard(g2, startX, cardY, cardW, cardH, hour / 10);
        drawFlipCard(g2, startX + cardW + gap, cardY, cardW, cardH, hour % 10);

        // Colon
        int colonX = startX + cardW * 2 + gap + 8;
        g2.setColor(accent);
        g2.fillOval(colonX, cy - 12, 6, 6);
        g2.fillOval(colonX, cy + 6, 6, 6);

        drawFlipCard(g2, colonX + 14, cardY, cardW, cardH, min / 10);
        drawFlipCard(g2, colonX + 14 + cardW + gap, cardY, cardW, cardH, min % 10);

        g2.dispose();
    }

    private void drawFlipCard(Graphics2D g2, int x, int y, int w, int h, int digit) {
        // Card background
        g2.setColor(new Color(40, 40, 45));
        g2.fillRoundRect(x, y, w, h, 8, 8);

        // Top half (slightly lighter)
        g2.setColor(new Color(50, 50, 55));
        g2.fillRoundRect(x, y, w, h / 2, 8, 8);
        g2.fillRect(x, y + h / 2 - 8, w, 8);

        // Divider line
        g2.setColor(new Color(25, 25, 30));
        g2.fillRect(x, y + h / 2 - 1, w, 3);

        // Digit
        g2.setFont(new Font("SansSerif", Font.BOLD, h - 12));
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        String s = String.valueOf(digit);
        int tx = x + (w - fm.stringWidth(s)) / 2;
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(s, tx, ty);

        // Shine on top half
        GradientPaint shine = new GradientPaint(x, y, new Color(255, 255, 255, 30), x, y + h / 3, new Color(255, 255, 255, 0));
        g2.setPaint(shine);
        g2.fillRoundRect(x + 2, y + 2, w - 4, h / 3, 6, 6);

        // Border
        g2.setColor(new Color(70, 70, 75));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 8, 8);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }
}
