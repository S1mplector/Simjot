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

package main.ui.components.icons;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for generating vector-rendered application icons.
 * No raster assets required if desired. Produces multiple sizes for best OS integration.
 * @author S1mplector
 */
public final class AppIcon {
    private AppIcon() {}

    /**
     * Create a list of images at multiple sizes suitable for JFrame.setIconImages().
     * Tries to load simjot.png first, then Streamline icons, then falls back to programmatic rendering.
     */
    public static List<Image> generateIconImages() {
        int[] sizes = new int[] {16, 24, 32, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>(sizes.length);
        
        // Try to load the PNG icon first
        Image pngIcon = loadPngIcon();
        if (pngIcon != null) {
            for (int sz : sizes) {
                images.add(pngIcon.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
            }
            return images;
        }

        String res = ImageIconRenderer.mapIdToResource("sticky_widget");
        if (res != null) {
            boolean loaded = false;
            for (int sz : sizes) {
                BufferedImage img = ImageIconRenderer.get(res, sz, false);
                if (img != null) {
                    images.add(img);
                    loaded = true;
                }
            }
            if (loaded) return images;
        }
        
        // Fallback to programmatic rendering
        for (int sz : sizes) {
            images.add(render(sz));
        }
        return images;
    }
    
    /**
     * Load the simjot.png icon from resources.
     */
    private static Image loadPngIcon() {
        try {
            // Try multiple paths for the icon
            String[] paths = {
                "Simjot/img/icons/original/simjot.png",
                "img/icons/original/simjot.png",
                "/img/icons/original/simjot.png",
                "Simjot/img/icons/simjot.png",
                "img/icons/simjot.png",
                "/img/icons/simjot.png"
            };
            for (String path : paths) {
                java.net.URL url = AppIcon.class.getClassLoader().getResource(path);
                if (url != null) {
                    return javax.imageio.ImageIO.read(url);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Render a single icon image with vector drawing at the given square size.
     */
    public static BufferedImage render(int size) {
        int s = Math.max(16, size);
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Drop shadow
            float shadowInset = s * 0.06f;
            float arc = s * 0.22f;
            Shape shadow = new RoundRectangle2D.Float(shadowInset, shadowInset + s * 0.04f,
                    s - shadowInset * 2, s - shadowInset * 2, arc, arc);
            g.setColor(new Color(0, 0, 0, 35));
            g.fill(shadow);

            // Base rounded rectangle
            float inset = s * 0.08f;
            Shape base = new RoundRectangle2D.Float(inset, inset, s - inset * 2, s - inset * 2, arc, arc);

            // Windows 7 style blue gradient
            GradientPaint gp = new GradientPaint(0, inset,
                    new Color(0x5A, 0xA0, 0xE6), // light top
                    0, s - inset,
                    new Color(0x1F, 0x5F, 0xA9)); // deep bottom
            g.setPaint(gp);
            g.fill(base);

            // Inner border (glossy)
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.03f)));
            g.setColor(new Color(255, 255, 255, 110));
            g.draw(base);

            // Outer subtle stroke
            float outInset = inset - s * 0.01f;
            Shape outer = new RoundRectangle2D.Float(outInset, outInset, s - outInset * 2, s - outInset * 2, arc, arc);
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.02f)));
            g.draw(outer);

            // Top gloss highlight
            Shape gloss = new RoundRectangle2D.Float(inset + s * 0.02f, inset + s * 0.02f,
                    s - (inset + s * 0.02f) * 2, (s - inset * 2) * 0.55f, arc * 0.9f, arc * 0.9f);
            Paint glossPaint = new GradientPaint(0, (float) ((RoundRectangle2D) gloss).getMinY(),
                    new Color(255, 255, 255, 140),
                    0, (float) ((RoundRectangle2D) gloss).getMaxY(),
                    new Color(255, 255, 255, 0));
            Composite oldComp = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver);
            g.setPaint(glossPaint);
            g.fill(gloss);
            g.setComposite(oldComp);

            // Central glyph: a clean "S" lettermark (for Simjot) with soft shadow
            String glyph = "S";
            float glyphSize = s * 0.60f;
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(glyphSize));
            GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), glyph);
            Shape text = gv.getOutline();
            Rectangle bounds = text.getBounds();
            AffineTransform at = new AffineTransform();
            double cx = s / 2.0;
            double cy = s / 2.0 + s * 0.02; // slight optical shift
            at.translate(cx - bounds.getWidth() / 2.0 - bounds.getX(),
                         cy - bounds.getHeight() / 2.0 - bounds.getY());
            Shape centered = at.createTransformedShape(text);

            // Soft shadow for glyph
            g.setColor(new Color(0, 0, 0, 70));
            g.translate(s * 0.01, s * 0.015);
            g.fill(centered);
            g.translate(-s * 0.01, -s * 0.015);

            // Glyph fill with subtle vertical gradient
            Paint glyphPaint = new GradientPaint(0, (float) (cy - glyphSize * 0.5),
                    new Color(250, 250, 250),
                    0, (float) (cy + glyphSize * 0.5),
                    new Color(230, 230, 230));
            g.setPaint(glyphPaint);
            g.fill(centered);

            // Glyph stroke
            g.setColor(new Color(0, 0, 0, 50));
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.04f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(centered);
        } finally {
            g.dispose();
        }
        return img;
    }
}
