package main.ui.icons;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared vector icon painter so multiple components render exactly the same icons. */
public final class VectorIconPainter {
    private VectorIconPainter() {}

    // --- Lightweight LRU cache for rasterized icons (name@size) -> ARGB image ---
    private static final int MAX_CACHE = 128;
    private static final Map<String, BufferedImage> IMG_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_CACHE;
        }
    };

    private static String key(String id, int s) { return (id == null ? "" : id.toLowerCase()) + "@" + s; }

    /** Return a cached ARGB image for the given icon id and size. */
    public static BufferedImage getImage(String id, int s) {
        String k = key(id, s);
        BufferedImage img = IMG_CACHE.get(k);
        if (img != null) return img;
        img = renderToImage(id, s);
        if (img != null) IMG_CACHE.put(k, img);
        return img;
    }

    /** Render vector icon to a transparent BufferedImage (no cache). */
    public static BufferedImage renderToImage(String id, int s) {
        if (id == null || s <= 0) return null;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Paint at origin; consumers can offset when drawing the image
            boolean ok = paint(g2, id, 0, 0, s);
            if (!ok) {
                // Not a known id; leave transparent image so callers can fallback
            }
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** Paint a named icon at x,y with size s. Returns true if handled. */
    public static boolean paint(Graphics2D g2, String id, int x, int y, int s){
        if(id==null) return false;
        switch(id.toLowerCase()){
            case "wrench":
            case "settings":
                paintWrench(g2, x, y, s); return true;
            case "delete":
                paintDeleteX(g2, x, y, s); return true;
            case "+":
            case "plus":
                paintPlusAero(g2, x, y, s); return true;
            case "save":
                paintSaveAero(g2, x, y, s); return true;
            case "list":
            case "notes":
                paintListAero(g2, x, y, s); return true;
            default:
                return false;
        }
    }

    // Copied from MainMenuButton 'wrench' case (with parameterization)
    private static void paintWrench(Graphics2D g2, int x, int y, int s){
        int cx = x + s/2; int cy = y + s/2;
        int R = Math.max(10, (int)(s*0.45));   // outer radius
        int r = Math.max(6,  (int)(s*0.30));   // inner radius (between teeth)
        int hole = Math.max(4, (int)(s*0.18)); // centre hole
        int teeth = 8;

        // Shadow
        Graphics2D sh2 = (Graphics2D) g2.create();
        sh2.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        sh2.setPaint(new RadialGradientPaint(new Point(cx, y + s), s/1.6f,
                new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
        sh2.fillOval(cx - R, y + s - 8, 2*R, 12);
        sh2.dispose();

        // Build gear path by alternating inner/outer radii
        java.awt.geom.GeneralPath gear = new java.awt.geom.GeneralPath();
        double start = -Math.PI/9; // slight tilt
        for(int i=0;i<teeth*2;i++){
            double a = start + i * Math.PI/teeth;
            double rad = (i%2==0 ? R : r);
            int px = (int)(cx + rad*Math.cos(a));
            int py = (int)(cy + rad*Math.sin(a));
            if(i==0) gear.moveTo(px, py); else gear.lineTo(px, py);
        }
        gear.closePath();

        // Metallic gradient fill
        Paint metal = new LinearGradientPaint(cx, y, cx, y+s,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(235,235,235), new Color(210,210,210), new Color(185,185,185)});
        g2.setPaint(metal);
        g2.fill(gear);

        // Inner hole
        g2.setColor(new Color(160,160,160));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new java.awt.geom.Ellipse2D.Float(cx - hole, cy - hole, hole*2, hole*2));

        // Gloss highlight arc on upper left
        g2.setColor(new Color(255,255,255,160));
        g2.setStroke(new BasicStroke(Math.max(2f, s/14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(cx - R + 4, cy - R + 4, 2*R - 8, 2*R - 8, 130, 70);

        // Outline
        g2.setColor(new Color(130,130,130));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(gear);
    }

    // Copied from ToolbarIconButton 'delete' case (parameterized & scaled by s)
    private static void paintDeleteX(Graphics2D g2, int x, int y, int s){
        int cx = x + s/2, cy = y + s/2;

        // Shadow under X
        Graphics2D sG = (Graphics2D) g2.create();
        sG.setComposite(AlphaComposite.SrcOver.derive(0.2f));
        sG.setPaint(new RadialGradientPaint(new Point(cx, cy + s/2), s/1.8f,
                new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
        sG.fillOval(cx - s/2, cy + s/2 - 6, s, 10);
        sG.dispose();

        // Red gradient for strokes
        Paint red = new LinearGradientPaint(cx, cy - s/2.5f, cx, cy + s/2.5f,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 110, 110), new Color(235, 40, 40), new Color(200, 0, 0)});
        g2.setPaint(red);
        g2.setStroke(new BasicStroke(Math.max(3.2f, s/5.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int delta = Math.max(7, s/3);
        g2.drawLine(cx - delta, cy - delta, cx + delta, cy + delta);
        g2.drawLine(cx - delta, cy + delta, cx + delta, cy - delta);

        // Top gloss highlight
        g2.setStroke(new BasicStroke(Math.max(2f, s/10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255,255,255,130));
        g2.drawLine(cx - delta + 1, cy - delta + 1, cx - 1, cy - 1);
        g2.drawLine(cx - delta + 1, cy + delta - 1, cx - 1, cy + 1);
    }

    // Aero-styled sky blue + silvery plus
    private static void paintPlusAero(Graphics2D g2, int x, int y, int s){
        int cx = x + s/2, cy = y + s/2;
        int arm = Math.max(5, s/2 - 3);

        // Soft drop shadow
        Graphics2D sh = (Graphics2D) g2.create();
        sh.setComposite(AlphaComposite.SrcOver.derive(0.25f));
        sh.setPaint(new RadialGradientPaint(new Point(cx, cy + s/3), s/1.6f,
                new float[]{0f,1f}, new Color[]{new Color(0,0,0,80), new Color(0,0,0,0)}));
        sh.fillOval(cx - arm, cy + arm - 2, arm*2, Math.max(6, s/6));
        sh.dispose();

        // Blue glossy stroke
        Paint blue = new LinearGradientPaint(cx, cy - arm, cx, cy + arm,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(120, 190, 255), new Color(80, 150, 235), new Color(50, 110, 210)});
        g2.setPaint(blue);
        g2.setStroke(new BasicStroke(Math.max(3f, s/5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - arm, cy, cx + arm, cy);
        g2.drawLine(cx, cy - arm, cx, cy + arm);

        // Silvery edge highlight
        g2.setColor(new Color(255,255,255,170));
        g2.setStroke(new BasicStroke(Math.max(2f, s/9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - arm + 1, cy - 1, cx, cy - 1);
        g2.drawLine(cx + 1, cy - arm + 1, cx + 1, cy);
    }

    // Aero-styled save (floppy) with silvery body and blue label
    private static void paintSaveAero(Graphics2D g2, int x, int y, int s){
        int pad = Math.max(2, s/8);
        int w = s, h = s;
        int rx = x + pad, ry = y + pad;
        int rw = w - pad*2, rh = h - pad*2;

        // Body with metallic gradient
        Paint metal = new LinearGradientPaint(rx, ry, rx, ry + rh,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(235,235,235), new Color(205,205,205), new Color(180,180,180)});
        g2.setPaint(metal);
        g2.fillRoundRect(rx, ry, rw, rh, Math.max(4, s/4), Math.max(4, s/4));

        // Top blue label band
        int bandH = Math.max(6, rh/3);
        Paint blue = new LinearGradientPaint(rx, ry, rx, ry + bandH,
                new float[]{0f, 1f},
                new Color[]{new Color(140,200,255), new Color(70,140,230)});
        g2.setPaint(blue);
        g2.fillRoundRect(rx+1, ry+1, rw-2, bandH, Math.max(4, s/5), Math.max(4, s/5));

        // Disk notch (right side)
        g2.setColor(new Color(160,160,160));
        int notch = Math.max(3, s/6);
        g2.fillRect(rx + rw - notch - 2, ry + bandH + 2, notch, notch);

        // Label lines
        g2.setColor(new Color(255,255,255,180));
        g2.drawLine(rx + 3, ry + bandH/2, rx + rw - 4, ry + bandH/2);
        g2.setColor(new Color(120,120,120,160));
        g2.drawLine(rx + 3, ry + bandH/2 + 2, rx + rw - 4, ry + bandH/2 + 2);

        // Gloss and outline
        g2.setColor(new Color(255,255,255,120));
        g2.drawArc(rx+2, ry+2, rw-4, rh-4, 140, 60);
        g2.setColor(new Color(130,130,130));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(rx, ry, rw, rh, Math.max(4, s/4), Math.max(4, s/4));
    }

    // Aero-styled list (view all notes)
    private static void paintListAero(Graphics2D g2, int x, int y, int s){
        int pad = Math.max(2, s/8);
        int w = s;
        int left = x + pad;
        int right = x + w - pad;
        int y1 = y + pad + 2;
        int gap = Math.max(3, s/6);

        // Soft shadow under lines
        Graphics2D sh = (Graphics2D) g2.create();
        sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        sh.setColor(new Color(0,0,0,80));
        sh.drawLine(left, y1 + 1, right, y1 + 1);
        sh.drawLine(left, y1 + gap + 1, right, y1 + gap + 1);
        sh.drawLine(left, y1 + gap*2 + 1, right, y1 + gap*2 + 1);
        sh.dispose();

        // Silvery bars with slight blue tint on the first line
        Stroke st = new BasicStroke(Math.max(3f, s/9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setStroke(st);

        Paint silver = new LinearGradientPaint(left, y1 - 2, left, y1 + 2,
                new float[]{0f,1f}, new Color[]{new Color(245,245,245), new Color(170,170,170)});
        g2.setPaint(silver);
        g2.drawLine(left, y1, right, y1);

        Paint silver2 = new LinearGradientPaint(left, y1 + gap - 2, left, y1 + gap + 2,
                new float[]{0f,1f}, new Color[]{new Color(235,235,235), new Color(160,160,160)});
        g2.setPaint(silver2);
        g2.drawLine(left, y1 + gap, right, y1 + gap);

        Paint silver3 = new LinearGradientPaint(left, y1 + gap*2 - 2, left, y1 + gap*2 + 2,
                new float[]{0f,1f}, new Color[]{new Color(230,230,230), new Color(155,155,155)});
        g2.setPaint(silver3);
        g2.drawLine(left, y1 + gap*2, right, y1 + gap*2);

        // Blue accent bullet on first line
        int bulletR = Math.max(2, s/10);
        g2.setPaint(new RadialGradientPaint(new Point(left - bulletR - 2, y1), bulletR*2,
                new float[]{0f,1f}, new Color[]{new Color(120,190,255), new Color(50,110,210)}));
        g2.fillOval(left - bulletR*2 - 2, y1 - bulletR, bulletR*2, bulletR*2);
    }
}
