/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.gallery;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Renders simple vector (Java2D) wallpapers on the fly.
 * Supported IDs:
 *  - gen:gray-linear
 *  - gen:gray-diagonal
 *  - gen:gray-radial
 */
public final class GeneratedWallpapers {
    private GeneratedWallpapers() {}

    public static final String ID_LINEAR = "gen:gray-linear";
    public static final String ID_DIAGONAL = "gen:gray-diagonal";
    public static final String ID_RADIAL = "gen:gray-radial";

    /**
     * Render a wallpaper image at the given size.
     */
    public static Image render(String id, int width, int height) {
        int w = Math.max(16, width);
        int h = Math.max(16, height);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        switch (id) {
            case ID_LINEAR -> paintLinear(g2, w, h);
            case ID_DIAGONAL -> paintDiagonal(g2, w, h);
            case ID_RADIAL -> paintRadial(g2, w, h);
            default -> paintLinear(g2, w, h);
        }
        g2.dispose();
        return img;
    }

    private static void paintLinear(Graphics2D g2, int w, int h) {
        // Top to bottom light gray to white
        Color c1 = new Color(235, 235, 235);
        Color c2 = Color.WHITE;
        GradientPaint gp = new GradientPaint(0, 0, c1, 0, h, c2);
        g2.setPaint(gp);
        g2.fillRect(0, 0, w, h);
        // subtle vignette
        vignette(g2, w, h, 0.06f);
    }

    private static void paintDiagonal(Graphics2D g2, int w, int h) {
        // Diagonal gradient from top-left gray to bottom-right white
        Color c1 = new Color(225, 225, 225);
        Color c2 = new Color(250, 250, 250);
        GradientPaint gp = new GradientPaint(0, 0, c1, w, h, c2);
        g2.setPaint(gp);
        g2.fillRect(0, 0, w, h);
        vignette(g2, w, h, 0.08f);
    }

    private static void paintRadial(Graphics2D g2, int w, int h) {
        // Radial from center white outward to gray
        float radius = (float) Math.hypot(w, h) / 1.2f;
        Point center = new Point(w / 2, h / 2);
        Color[] colors = { new Color(255, 255, 255), new Color(230, 230, 230) };
        float[] dist = { 0f, 1f };
        java.awt.RadialGradientPaint rgp = new java.awt.RadialGradientPaint(center, radius, dist, colors);
        g2.setPaint(rgp);
        g2.fillRect(0, 0, w, h);
        vignette(g2, w, h, 0.05f);
    }

    private static void vignette(Graphics2D g2, int w, int h, float maxAlpha) {
        // Soft vignette around edges
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Transparent center to dark edges
        GradientPaint gp1 = new GradientPaint(0, 0, new Color(0,0,0, (int)(255*maxAlpha)), 0, h/3f, new Color(0,0,0,0));
        g.setPaint(gp1); g.fillRect(0, 0, w, h/2);
        GradientPaint gp2 = new GradientPaint(0, h, new Color(0,0,0, (int)(255*maxAlpha)), 0, h-h/3f, new Color(0,0,0,0));
        g.setPaint(gp2); g.fillRect(0, h/2, w, h/2);
        g.dispose();
        g2.drawImage(mask, 0, 0, null);
    }
}
