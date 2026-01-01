/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.ResourceLoader;

/**
 * Native-accelerated background image painter.
 * 
 * Uses native C code for heavy operations (resize, opacity) while Java handles:
 * - Image loading from resources/filesystem
 * - Cache coordination
 * - Graphics drawing
 * 
 * This approach keeps memory efficient by:
 * - Using native bilinear resize (faster, lower memory)
 * - Applying opacity in-place on pixel data
 * - Minimal Java object allocation
 */
public class BackgroundPainter {
    private BufferedImage sourceImage;      // Original loaded image
    private BufferedImage cachedScaled;     // Scaled + opacity-applied result
    private int cachedPanelW = -1;
    private int cachedPanelH = -1;
    private int cachedX = 0;
    private int cachedY = 0;
    private float cachedOpacity = -1f;
    private String cachedBgPath = null;

    /**
     * Paints the background based on the given path and opacity.
     * Uses native resize when available for better performance.
     *
     * @param g target graphics
     * @param comp component for sizing
     * @param bgPath background image path or resource key (may be null)
     * @param opacity 0..1 opacity
     * @param drawWhiteFirst if true, fills white before painting
     */
    public void paint(Graphics g, JComponent comp, String bgPath, float opacity, boolean drawWhiteFirst) {
        if (drawWhiteFirst) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
        }

        if (bgPath == null || bgPath.isEmpty()) {
            paintFallback(g, comp);
            return;
        }

        // Invalidate cache if path changed
        if (cachedBgPath == null || !cachedBgPath.equals(bgPath)) {
            disposeImages();
            cachedBgPath = bgPath;
        }

        // Load source image if needed
        if (sourceImage == null) {
            sourceImage = loadImage(bgPath);
            if (sourceImage == null) {
                paintFallback(g, comp);
                return;
            }
        }

        int panelW = comp.getWidth();
        int panelH = comp.getHeight();
        if (panelW <= 0 || panelH <= 0) return;

        // Check if we need to rebuild the cached scaled image
        if (cachedScaled == null || panelW != cachedPanelW || panelH != cachedPanelH || opacity != cachedOpacity) {
            cachedScaled = buildScaledImage(sourceImage, panelW, panelH, opacity);
            cachedPanelW = panelW;
            cachedPanelH = panelH;
            cachedOpacity = opacity;
        }

        if (cachedScaled != null) {
            g.drawImage(cachedScaled, cachedX, cachedY, comp);
        } else {
            paintFallback(g, comp);
        }
    }

    /**
     * Load image from path (resource or filesystem).
     */
    private BufferedImage loadImage(String bgPath) {
        try {
            Image img;
            if (bgPath.startsWith("res:")) {
                img = ResourceLoader.createImage("Simjot/" + bgPath.substring(4));
            } else {
                img = new ImageIcon(bgPath).getImage();
            }
            if (img == null) return null;

            int w = img.getWidth(null);
            int h = img.getHeight(null);
            if (w <= 0 || h <= 0) return null;

            // Convert to BufferedImage with ARGB for pixel access
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = buf.createGraphics();
            g2.drawImage(img, 0, 0, null);
            g2.dispose();
            return buf;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Build scaled image with cover-fit and opacity applied.
     * Uses native resize when available.
     */
    private BufferedImage buildScaledImage(BufferedImage src, int panelW, int panelH, float opacity) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        if (srcW <= 0 || srcH <= 0) return null;

        // Calculate cover-fit dimensions
        double scale = Math.max((double) panelW / srcW, (double) panelH / srcH);
        int drawW = Math.max(1, (int) Math.round(srcW * scale));
        int drawH = Math.max(1, (int) Math.round(srcH * scale));
        cachedX = (panelW - drawW) / 2;
        cachedY = (panelH - drawH) / 2;

        // Try native resize first
        BufferedImage result = tryNativeResize(src, srcW, srcH, drawW, drawH, opacity);
        if (result != null) return result;

        // Fallback to Java resize
        return javaResize(src, drawW, drawH, opacity);
    }

    /**
     * Try native resize with opacity. Returns null if native unavailable.
     */
    private BufferedImage tryNativeResize(BufferedImage src, int srcW, int srcH, int dstW, int dstH, float opacity) {
        if (!NativeAccess.isAvailable()) return null;

        try {
            // Extract source pixels
            int[] srcPixels = src.getRGB(0, 0, srcW, srcH, null, 0, srcW);

            // Native resize (quality=2 = auto-select best algorithm)
            int[] dstPixels = NativeAccess.imageResizeArgb(srcPixels, srcW, srcH, dstW, dstH, 2);
            if (dstPixels == null) return null;

            // Apply opacity in-place
            if (opacity < 1.0f) {
                applyOpacity(dstPixels, opacity);
            }

            // Create result image
            BufferedImage result = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0, 0, dstW, dstH, dstPixels, 0, dstW);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Apply opacity to ARGB pixel array in-place.
     */
    private void applyOpacity(int[] pixels, float opacity) {
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xFF;
            a = Math.min(255, (int) (a * opacity + 0.5f));
            pixels[i] = (a << 24) | (p & 0x00FFFFFF);
        }
    }

    /**
     * Java fallback resize with opacity.
     */
    private BufferedImage javaResize(BufferedImage src, int drawW, int drawH, float opacity) {
        BufferedImage result = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, drawW, drawH, null);
        g2.dispose();

        // Apply opacity to result
        if (opacity < 1.0f) {
            int[] pixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
            applyOpacity(pixels, opacity);
        }

        return result;
    }

    /**
     * Fallback: solid white background.
     */
    private void paintFallback(Graphics g, JComponent comp) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
    }

    /**
     * Dispose internal images only (keeps path for potential reload).
     */
    private void disposeImages() {
        if (cachedScaled != null) {
            cachedScaled.flush();
            cachedScaled = null;
        }
        if (sourceImage != null) {
            sourceImage.flush();
            sourceImage = null;
        }
        cachedPanelW = cachedPanelH = -1;
        cachedOpacity = -1f;
    }

    /**
     * Dispose all cached data to free memory.
     * Call when the component is hidden or disposed.
     */
    public void dispose() {
        disposeImages();
        cachedBgPath = null;
    }
}
