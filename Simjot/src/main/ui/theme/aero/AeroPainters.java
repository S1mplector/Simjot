package main.ui.theme.aero;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public final class AeroPainters {
    private AeroPainters() {}

    public static void paintVerticalGradient(Graphics2D g2, Rectangle r, Color top, Color bottom, int arc) {
        Paint old = g2.getPaint();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, r.y, top, 0, r.y + r.height, bottom);
        g2.setPaint(gp);
        g2.fill(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
        g2.setPaint(old);
    }

    public static void paintGlassOverlay(Graphics2D g2, Rectangle r, int arc) {
        // Subtle white highlight on top and shade at bottom
        int half = r.height / 2;
        paintVerticalGradient(g2, new Rectangle(r.x, r.y, r.width, half),
                new Color(255,255,255,110), new Color(255,255,255,20), arc);
        paintVerticalGradient(g2, new Rectangle(r.x, r.y + half, r.width, r.height - half),
                new Color(0,0,0,10), new Color(0,0,0,35), arc);
    }

    public static void paintGlowText(Graphics2D g2, String text, int x, int y, Font font, Color glow, Color fg) {
        g2.setFont(font);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(glow);
        for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) {
            if (dx==0 && dy==0) continue;
            g2.drawString(text, x+dx, y+dy);
        }
        g2.setColor(fg);
        g2.drawString(text, x, y);
    }
}
