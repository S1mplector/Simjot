/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.home;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

/**
 * Vector-rendered compact calendar showing today's date.
 * Designed to sit next to the analog clock on the main menu.
 */
public class TodayCalendarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private LocalDate today = LocalDate.now();
    private final Timer timer;
    private Color accent = AeroTheme.AERO_BLUE;

    public TodayCalendarPanel() {
        this(Theme.getWidgetAccent());
    }

    public TodayCalendarPanel(Color accent) {
        setOpaque(false);
        setPreferredSize(new Dimension(140, 150));
        setMinimumSize(new Dimension(110, 130));
        if (accent != null) this.accent = accent;
        // Update once per second to detect minute/day boundary; cheap repaint
        timer = new Timer(1000, e -> tick());
        timer.start();
    }

    public void setAccent(Color c){ if (c!=null){ this.accent = c; repaint(); } }

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
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();
        int pad = Math.max(8, Math.min(w, h) / 18);
        int arc = Math.max(14, Math.min(w, h) / 7);

        Rectangle rect = new Rectangle(pad, pad, w - pad * 2, h - pad * 2);

        // Soft shadow
        Graphics2D sh = (Graphics2D) g2.create();
        sh.setComposite(AlphaComposite.SrcOver.derive(0.20f));
        sh.setPaint(new RadialGradientPaint(new Point(rect.x + rect.width/2, rect.y + rect.height), rect.width,
                new float[]{0f,1f}, new Color[]{new Color(0,0,0,80), new Color(0,0,0,0)}));
        sh.fillRoundRect(rect.x - 2, rect.y + 6, rect.width + 4, rect.height - 4, arc, arc);
        sh.dispose();

        // Card background (subtle vertical gradient)
        Paint card = new LinearGradientPaint(0, rect.y, 0, rect.y + rect.height,
                new float[]{0f, 1f}, new Color[]{new Color(252,252,252), new Color(236,236,236)});
        g2.setPaint(card);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc);

        // Inner stroke
        g2.setColor(new Color(180,180,180));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc);

        // Header bar for month name (use accent color gradient)
        int headerH = Math.max(28, rect.height / 5);
        RoundRectangle2D header = new RoundRectangle2D.Double(rect.x, rect.y, rect.width, headerH, arc, arc);
        // Clip to top to avoid rounded bottom in header fill
        Area headerClip = new Area(header);
        Area bottomCut = new Area(new Rectangle(rect.x, rect.y + headerH - arc, rect.width, arc));
        headerClip.subtract(bottomCut);
        Graphics2D h2 = (Graphics2D) g2.create();
        h2.setClip(headerClip);
        Color top = AccentColorUtil.lighten(accent, 0.15f);
        Color bot = AccentColorUtil.darken(accent, 0.18f);
        Paint headerPaint = new LinearGradientPaint(rect.x, rect.y, rect.x, rect.y + headerH,
                new float[]{0f, 1f}, new Color[]{top, bot});
        h2.setPaint(headerPaint);
        h2.fillRect(rect.x, rect.y, rect.width, headerH);
        // Header gloss
        h2.setPaint(new GradientPaint(0, rect.y, new Color(255,255,255,160), 0, rect.y + headerH/2f, new Color(255,255,255,0)));
        h2.fillRect(rect.x + 1, rect.y + 1, rect.width - 2, headerH/2);
        h2.dispose();

        // Month text (black, per request)
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, Math.max(14f, rect.height * 0.13f)));
        g2.setColor(Color.BLACK);
        FontMetrics fmM = g2.getFontMetrics();
        int monthX = rect.x + (rect.width - fmM.stringWidth(month)) / 2;
        int monthY = rect.y + (headerH + fmM.getAscent() - fmM.getDescent()) / 2 + 2; // nudge lower
        g2.drawString(month, monthX, monthY);

        // Large day number
        String dayStr = Integer.toString(today.getDayOfMonth());
        float daySize = Math.max(40f, rect.height * 0.42f);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, daySize));
        g2.setColor(new Color(50,50,50));
        FontMetrics fmD = g2.getFontMetrics();
        int dayX = rect.x + (rect.width - fmD.stringWidth(dayStr)) / 2;
        // Position day so there's guaranteed room for weekday below
        int contentTop = rect.y + headerH + 8;
        // Compute provisional weekday metrics for spacing
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Math.max(12f, rect.height * 0.11f)));
        FontMetrics fmWtmp = g2.getFontMetrics();
        int weekdayBaseline = rect.y + rect.height - pad - 6 - fmWtmp.getDescent();
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, daySize));
        int availableHeight = weekdayBaseline - contentTop;
        int dayY = contentTop + availableHeight/2 + fmD.getAscent()/2 - 2;
        // Subtle emboss effect
        g2.setColor(new Color(255,255,255,160));
        g2.drawString(dayStr, dayX, dayY - 1);
        g2.setColor(new Color(40,40,40));
        g2.drawString(dayStr, dayX, dayY);

        // Weekday label
        DayOfWeek dow = today.getDayOfWeek();
        String weekday = dow.getDisplayName(TextStyle.FULL, Locale.getDefault());
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Math.max(12f, rect.height * 0.11f)));
        FontMetrics fmW = g2.getFontMetrics();
        int wX = rect.x + (rect.width - fmW.stringWidth(weekday)) / 2;
        // Keep fully inside bottom area
        int wY = rect.y + rect.height - pad - 6 - fmW.getDescent();
        g2.setColor(new Color(90,90,90));
        g2.drawString(weekday, wX, wY);

        // Bottom inner highlight line
        g2.setColor(new Color(255,255,255,140));
        g2.drawRoundRect(rect.x + 1, rect.y + 1, rect.width - 2, rect.height - 2, arc - 2, arc - 2);

        g2.dispose();
    }
}
