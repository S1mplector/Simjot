package main.ui.features.quicksettings;

import javax.swing.*;
import java.awt.*;

/**
 * App-specific vector icons for Quick Settings orbs to avoid OS-dependent emoji rendering.
 * Icons are simple, crisp, and neutral to fit within a 16-20px area.
 */
public final class QuickSettingsIcons {
    private QuickSettingsIcons() {}

    public static Icon appearance(int size) { return new PaletteIcon(size); }
    public static Icon editor(int size) { return new PenIcon(size); }
    public static Icon widgets(int size) { return new PuzzleIcon(size); }
    public static Icon backup(int size) { return new SaveIcon(size); }

    private static abstract class BaseIcon implements Icon {
        protected final int size;
        protected BaseIcon(int size) { this.size = Math.max(12, size); }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            paint(g2);
            g2.dispose();
        }
        protected abstract void paint(Graphics2D g2);
    }

    // 🎨 Palette
    private static class PaletteIcon extends BaseIcon {
        PaletteIcon(int size) { super(size); }
        @Override protected void paint(Graphics2D g2) {
            int s = size;
            // base palette
            g2.setColor(new Color(245, 237, 209));
            g2.fillOval(0, 0, s, s);
            g2.setColor(new Color(190, 180, 155));
            g2.drawOval(0, 0, s-1, s-1);
            // thumb hole
            g2.setColor(new Color(230, 222, 195));
            g2.fillOval((int)(s*0.58), (int)(s*0.58), (int)(s*0.28), (int)(s*0.28));
            // paint dots
            int r = Math.max(2, s/7);
            g2.setColor(new Color(229, 71, 71));
            g2.fillOval(s/6 - r/2, s/3 - r/2, r, r);
            g2.setColor(new Color(70, 146, 246));
            g2.fillOval(s/2 - r/2, s/4 - r/2, r, r);
            g2.setColor(new Color(78, 186, 97));
            g2.fillOval(s/3*2 - r/2, s/2 - r/2, r, r);
            g2.setColor(new Color(241, 195, 67));
            g2.fillOval(s/3 - r/2, s/2 - r/2, r, r);
        }
    }

    // ✍ Pen/quill
    private static class PenIcon extends BaseIcon {
        PenIcon(int size) { super(size); }
        @Override protected void paint(Graphics2D g2) {
            int s = size;
            // Draw a small diagonal pencil for clarity at tiny sizes
            double angle = Math.toRadians(-35);
            Stroke outline = new BasicStroke(Math.max(1f, s/18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            Color body1 = new Color(250, 210, 80);  // yellow body
            Color body2 = new Color(230, 180, 60);  // shade
            Color outlineC = new Color(30, 70, 140);
            Color eraser = new Color(248, 154, 164);
            Color ferrule = new Color(210, 210, 210);
            Color wood = new Color(226, 195, 150);
            Color graphite = new Color(60, 60, 60);

            int L = (int) Math.round(s * 0.82);         // length
            int T = Math.max(3, (int) Math.round(s * 0.22)); // thickness

            // Centered coordinate system and rotation
            g2 = (Graphics2D) g2.create();
            g2.translate(s/2.0, s/2.0);
            g2.rotate(angle);

            int x0 = -L/2;         // start of pencil body
            int x1 = L/2;          // tip end
            int y0 = -T/2;
            int y1 = T/2;

            // Body gradient
            Paint body = new GradientPaint(0, y0, body1, 0, y1, body2);
            g2.setPaint(body);
            g2.fillRoundRect(x0, y0, L - (int)(T*0.9), T, T/2, T/2);

            // Ferrule (metal band near eraser)
            int ferrW = (int) Math.round(T * 0.6);
            g2.setColor(ferrule);
            g2.fillRoundRect(x0, y0, ferrW, T, T/4, T/4);
            g2.setColor(new Color(170,170,170));
            g2.drawRoundRect(x0, y0, ferrW, T, T/4, T/4);

            // Eraser
            int eraW = (int) Math.round(T * 0.7);
            g2.setColor(eraser);
            g2.fillRoundRect(x0 - eraW + 1, y0 + 1, eraW, T - 2, T/3, T/3);

            // Wood tip
            int tipW = (int) Math.round(T * 0.9);
            Polygon woodTip = new Polygon();
            woodTip.addPoint(x1 - tipW, y0);
            woodTip.addPoint(x1 - tipW, y1);
            woodTip.addPoint(x1, 0);
            g2.setColor(wood);
            g2.fillPolygon(woodTip);

            // Graphite tip
            Polygon lead = new Polygon();
            lead.addPoint(x1 - (int)(tipW*0.35), (int)(-T*0.18));
            lead.addPoint(x1 - (int)(tipW*0.35), (int)(T*0.18));
            lead.addPoint(x1, 0);
            g2.setColor(graphite);
            g2.fillPolygon(lead);

            // Outline
            g2.setColor(outlineC);
            g2.setStroke(outline);
            g2.drawRoundRect(x0, y0, L - (int)(T*0.9), T, T/2, T/2);
            g2.drawPolygon(woodTip);
            g2.drawPolygon(lead);

            g2.dispose();
        }
    }

    // 🧩 Puzzle piece
    private static class PuzzleIcon extends BaseIcon {
        PuzzleIcon(int size) { super(size); }
        @Override protected void paint(Graphics2D g2) {
            int s = size;
            g2.setColor(new Color(120, 200, 90));
            int r = s/5;
            int w = s - 2;
            int h = s - 2;
            int x = 1, y = 1;
            g2.fillRoundRect(x, y, w, h, s/4, s/4);
            g2.setColor(new Color(40, 120, 60));
            g2.drawRoundRect(x, y, w, h, s/4, s/4);
            // simple knobs
            g2.setColor(new Color(120, 200, 90));
            g2.fillOval(s/2 - r/2, y - r/2, r, r);
            g2.fillOval(s - r/2 - 2, s/2 - r/2, r, r);
        }
    }

    // 💾 Save
    private static class SaveIcon extends BaseIcon {
        SaveIcon(int size) { super(size); }
        @Override protected void paint(Graphics2D g2) {
            int s = size;
            g2.setColor(new Color(240, 240, 240));
            g2.fillRoundRect(1, 1, s-2, s-2, 3, 3);
            g2.setColor(new Color(40, 90, 160));
            g2.drawRoundRect(1, 1, s-2, s-2, 3, 3);
            // top band
            g2.setColor(new Color(70, 130, 200));
            g2.fillRect(2, 2, s-4, s/3);
            // shutter slot
            g2.setColor(new Color(230,230,230));
            g2.fillRect(s/3, s/2, s/3, s/4);
            g2.setColor(new Color(120,120,120));
            g2.drawRect(s/3, s/2, s/3, s/4);
        }
    }
}
