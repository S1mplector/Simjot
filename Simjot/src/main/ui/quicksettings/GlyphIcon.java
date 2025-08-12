package main.ui.quicksettings;

import javax.swing.*;
import java.awt.*;

/**
 * Simple text-based icon that paints a single glyph (emoji or symbol) centered.
 */
public class GlyphIcon implements Icon {
    private final String glyph;
    private final int size; // square size in px
    private final Color color;
    private final Font baseFont;

    public GlyphIcon(String glyph, int size, Color color) {
        this(glyph, size, color, new Font("Segoe UI Emoji", Font.PLAIN, size));
    }

    public GlyphIcon(String glyph, int size, Color color, Font font) {
        this.glyph = glyph;
        this.size = size;
        this.color = color;
        this.baseFont = font;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Font f = baseFont.deriveFont((float) (size * 0.9));
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(glyph);
        int th = fm.getAscent();
        int cx = x + (getIconWidth() - tw) / 2;
        int cy = y + (getIconHeight() + th) / 2 - Math.max(2, (int)(size*0.08));
        g2.setColor(color != null ? color : Color.WHITE);
        g2.drawString(glyph, cx, cy);
        g2.dispose();
    }

    @Override
    public int getIconWidth() { return size; }

    @Override
    public int getIconHeight() { return size; }
}
