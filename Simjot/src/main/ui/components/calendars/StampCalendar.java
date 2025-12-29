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
 * Postage stamp style calendar.
 */
public class StampCalendar extends JPanel {
    private Color accent;

    public StampCalendar() {
        this(new Color(180, 60, 60));
    }

    public StampCalendar(Color accent) {
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
        int margin = 12;
        int stampW = w - margin * 2;
        int stampH = h - margin * 2;

        // Perforated edge effect
        g2.setColor(new Color(250, 248, 245));
        drawPerforatedRect(g2, margin, margin, stampW, stampH, 6);

        // Inner border
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(margin + 8, margin + 8, stampW - 16, stampH - 16);

        // Decorative corner flourishes
        g2.setStroke(new BasicStroke(1.5f));
        int cornerSize = 8;
        // Top-left
        g2.drawLine(margin + 12, margin + 12, margin + 12 + cornerSize, margin + 12);
        g2.drawLine(margin + 12, margin + 12, margin + 12, margin + 12 + cornerSize);
        // Top-right
        g2.drawLine(w - margin - 12, margin + 12, w - margin - 12 - cornerSize, margin + 12);
        g2.drawLine(w - margin - 12, margin + 12, w - margin - 12, margin + 12 + cornerSize);
        // Bottom-left
        g2.drawLine(margin + 12, h - margin - 12, margin + 12 + cornerSize, h - margin - 12);
        g2.drawLine(margin + 12, h - margin - 12, margin + 12, h - margin - 12 - cornerSize);
        // Bottom-right
        g2.drawLine(w - margin - 12, h - margin - 12, w - margin - 12 - cornerSize, h - margin - 12);
        g2.drawLine(w - margin - 12, h - margin - 12, w - margin - 12, h - margin - 12 - cornerSize);

        LocalDate today = LocalDate.now();
        String day = String.valueOf(today.getDayOfMonth());
        String month = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
        String year = String.valueOf(today.getYear());

        int cx = w / 2;
        int cy = h / 2;

        // Day number
        g2.setFont(new Font("Serif", Font.BOLD, 36));
        g2.setColor(accent);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(day, cx - fm.stringWidth(day) / 2, cy + 8);

        // Month above
        g2.setFont(new Font("Serif", Font.PLAIN, 11));
        g2.setColor(new Color(100, 80, 80));
        fm = g2.getFontMetrics();
        g2.drawString(month, cx - fm.stringWidth(month) / 2, cy - 22);

        // Year below
        g2.setFont(new Font("Serif", Font.ITALIC, 10));
        g2.setColor(new Color(130, 110, 110));
        fm = g2.getFontMetrics();
        g2.drawString(year, cx - fm.stringWidth(year) / 2, cy + 28);

        g2.dispose();
    }

    private void drawPerforatedRect(Graphics2D g2, int x, int y, int w, int h, int holeSize) {
        // Fill main area
        g2.fillRect(x + holeSize / 2, y + holeSize / 2, w - holeSize, h - holeSize);

        // Draw semicircles on edges to create perforation effect
        int spacing = holeSize + 4;
        g2.setColor(getBackground() != null ? getBackground() : new Color(0, 0, 0, 0));

        // This simplified version just draws the stamp without actual perforations
        // For a full effect, we'd clip out semicircles
        g2.setColor(new Color(250, 248, 245));
        g2.fillRoundRect(x, y, w, h, 4, 4);
    }
}
