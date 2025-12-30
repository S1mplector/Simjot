/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.theme.aero;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import main.ui.theme.Theme;

public final class AeroPainters {
    private AeroPainters() {}

    public static void paintVerticalGradient(Graphics2D g2, Rectangle r, Color top, Color bottom, int arc) {
        Paint old = g2.getPaint();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (Theme.isPlainWhite()) {
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
        } else {
            GradientPaint gp = new GradientPaint(0, r.y, top, 0, r.y + r.height, bottom);
            g2.setPaint(gp);
            g2.fill(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
        }
        g2.setPaint(old);
    }

    public static void paintGlassOverlay(Graphics2D g2, Rectangle r, int arc) {
        if (Theme.isPlainWhite()) return;
        int half = r.height / 2;
        paintVerticalGradient(g2, new Rectangle(r.x, r.y, r.width, half),
                new Color(255,255,255,110), new Color(255,255,255,20), arc);
        paintVerticalGradient(g2, new Rectangle(r.x, r.y + half, r.width, r.height - half),
                new Color(0,0,0,10), new Color(0,0,0,35), arc);
    }

    public static void paintGlowText(Graphics2D g2, String text, int x, int y, Font font, Color glow, Color fg) {
        g2.setFont(font);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (Theme.isPlainWhite()) {
            g2.setColor(fg);
            g2.drawString(text, x, y);
            return;
        }
        g2.setColor(glow);
        for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) {
            if (dx==0 && dy==0) continue;
            g2.drawString(text, x+dx, y+dy);
        }
        g2.setColor(fg);
        g2.drawString(text, x, y);
    }

    // Subtle outer glow halo around a rounded rectangle. Draw before the fill to appear behind.
    public static void paintOuterGlow(Graphics2D g2, Rectangle r, int arc, Color color, int size, int maxAlpha) {
        if (Theme.isPlainWhite()) return;
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = size; i >= 1; i--) {
            float a = (float) i / (float) size;
            int alpha = Math.min(255, Math.max(0, Math.round(maxAlpha * a * a))); // ease-out
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g2.setStroke(new BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            RoundRectangle2D rr = new RoundRectangle2D.Float(r.x + 1, r.y + 1, r.width - 2, r.height - 2, arc, arc);
            g2.draw(rr);
        }
        g2.setStroke(oldStroke);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    // 1px inner stroke highlight to sell the glass edge.
    public static void paintInnerStroke(Graphics2D g2, Rectangle r, int arc, Color color) {
        if (Theme.isPlainWhite()) return;
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(color);
        g2.setStroke(new BasicStroke(1f));
        RoundRectangle2D rr = new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1f, r.height - 1f, arc, arc);
        g2.draw(rr);

        g2.setStroke(oldStroke);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    // Inner shadow to simulate a pressed/inset button state.
    public static void paintInnerShadow(Graphics2D g2, Rectangle r, int arc, Color color, int size, int maxAlpha) {
        if (Theme.isPlainWhite()) return;
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 1; i <= size; i++) {
            float t = (float) i / (float) size;
            int alpha = Math.min(255, Math.max(0, Math.round(maxAlpha * (1f - t))));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g2.setStroke(new BasicStroke(1f));
            RoundRectangle2D rr = new RoundRectangle2D.Float(r.x + i, r.y + i, r.width - 2 * i, r.height - 2 * i, arc, arc);
            g2.draw(rr);
        }

        g2.setStroke(oldStroke);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }
}
