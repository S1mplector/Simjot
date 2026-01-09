/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import main.core.font.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Renders custom fonts to Graphics2D or BufferedImage.
 * Uses antialiased stroke rendering with pressure sensitivity.
 */
public final class CustomFontRenderer {
    
    private static final int DEFAULT_OVERSAMPLE = 2;
    
    private final CustomFontCache cache;
    private int oversample = DEFAULT_OVERSAMPLE;
    private boolean antialiasing = true;
    
    public CustomFontRenderer() {
        this.cache = new CustomFontCache();
    }
    
    public CustomFontRenderer(CustomFontCache cache) {
        this.cache = cache != null ? cache : new CustomFontCache();
    }
    
    public void setOversample(int oversample) {
        this.oversample = Math.max(1, Math.min(8, oversample));
    }
    
    public void setAntialiasing(boolean enabled) {
        this.antialiasing = enabled;
    }
    
    public CustomFontCache getCache() {
        return cache;
    }
    
    public BufferedImage renderGlyph(CustomFont font, CustomGlyph glyph, int size, Color color) {
        if (font == null || glyph == null || !glyph.isDefined() || size <= 0) {
            return null;
        }
        
        // Check cache
        String cacheKey = font.getName() + "_" + font.getModifiedTimestamp() + "_" + glyph.getCodepoint() + "_" + size;
        BufferedImage cached = cache.getGlyphImage(cacheKey);
        if (cached != null) {
            return colorize(cached, color);
        }

        BufferedImage nativeImg = NativeFontSupport.renderGlyph(font, glyph, size, oversample, antialiasing);
        if (nativeImg != null) {
            cache.putGlyphImage(cacheKey, nativeImg);
            return colorize(nativeImg, color);
        }
        
        float emSize = font.getEmSize();
        float scale = (float) size / emSize;
        
        // Calculate bounds
        float[] bounds = glyph.getBounds();
        float padding = glyph.getStrokes().isEmpty() ? 0 : 
            glyph.getStrokes().get(0).getThickness() * scale;
        
        int imgWidth = Math.max(1, (int) Math.ceil((bounds[2] + padding * 2) * scale * oversample));
        int imgHeight = Math.max(1, (int) Math.ceil((bounds[3] + padding * 2) * scale * oversample));
        
        if (imgWidth > 512 || imgHeight > 512) {
            imgWidth = Math.min(imgWidth, 512);
            imgHeight = Math.min(imgHeight, 512);
        }
        
        // Create oversampled image
        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        
        if (antialiasing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        }
        
        g2.setColor(Color.BLACK);
        
        float offsetX = (-bounds[0] + padding) * scale * oversample;
        float offsetY = (-bounds[1] + padding) * scale * oversample;
        
        // Render each stroke
        for (CustomStroke stroke : glyph.getStrokes()) {
            renderStroke(g2, stroke, scale * oversample, offsetX, offsetY);
        }
        
        g2.dispose();
        
        // Downsample if needed
        if (oversample > 1) {
            int finalWidth = (imgWidth + oversample - 1) / oversample;
            int finalHeight = (imgHeight + oversample - 1) / oversample;
            
            BufferedImage downsampled = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = downsampled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(img, 0, 0, finalWidth, finalHeight, null);
            g2d.dispose();
            img = downsampled;
        }
        
        // Cache the grayscale version
        cache.putGlyphImage(cacheKey, img);
        
        return colorize(img, color);
    }
    
    private void renderStroke(Graphics2D g2, CustomStroke stroke, float scale, float offsetX, float offsetY) {
        java.util.List<StrokePoint> points = stroke.getPoints();
        if (points.size() < 2) {
            if (points.size() == 1) {
                // Single point - draw dot
                StrokePoint p = points.get(0);
                float x = p.getX() * scale + offsetX;
                float y = p.getY() * scale + offsetY;
                float r = stroke.getThickness() * p.getPressure() * scale * 0.5f;
                g2.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
            }
            return;
        }

        // Draw variable-width stroke
        float baseThickness = stroke.getThickness() * scale;
        float minP = 1.0f;
        float maxP = 0.0f;
        for (StrokePoint p : points) {
            float pr = p.getPressure();
            minP = Math.min(minP, pr);
            maxP = Math.max(maxP, pr);
        }
        if (maxP - minP < 0.05f) {
            float avgP = (minP + maxP) * 0.5f;
            Path2D.Float path = new Path2D.Float();
            StrokePoint first = points.get(0);
            path.moveTo(first.getX() * scale + offsetX, first.getY() * scale + offsetY);
            for (int i = 1; i < points.size(); i++) {
                StrokePoint p = points.get(i);
                path.lineTo(p.getX() * scale + offsetX, p.getY() * scale + offsetY);
            }
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(Math.max(1.0f, baseThickness * avgP),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
            g2.setStroke(old);
            return;
        }
        
        for (int i = 0; i < points.size() - 1; i++) {
            StrokePoint p0 = points.get(i);
            StrokePoint p1 = points.get(i + 1);
            
            float x0 = p0.getX() * scale + offsetX;
            float y0 = p0.getY() * scale + offsetY;
            float x1 = p1.getX() * scale + offsetX;
            float y1 = p1.getY() * scale + offsetY;
            
            float thick0 = baseThickness * p0.getPressure();
            float thick1 = baseThickness * p1.getPressure();

            drawVariableWidthSegment(g2, x0, y0, x1, y1, thick0, thick1);
        }

        // Draw round caps
        StrokePoint first = points.get(0);
        StrokePoint last = points.get(points.size() - 1);
        
        float fx = first.getX() * scale + offsetX;
        float fy = first.getY() * scale + offsetY;
        float fr = baseThickness * first.getPressure() * 0.5f;
        g2.fill(new Ellipse2D.Float(fx - fr, fy - fr, fr * 2, fr * 2));
        
        float lx = last.getX() * scale + offsetX;
        float ly = last.getY() * scale + offsetY;
        float lr = baseThickness * last.getPressure() * 0.5f;
        g2.fill(new Ellipse2D.Float(lx - lr, ly - lr, lr * 2, lr * 2));
    }
    
    private void drawVariableWidthSegment(Graphics2D g2, float x0, float y0, float x1, float y1, 
                                           float thick0, float thick1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (len < 0.001f) return;
        
        // Perpendicular vector
        float nx = -dy / len;
        float ny = dx / len;
        
        float r0 = thick0 * 0.5f;
        float r1 = thick1 * 0.5f;
        
        // Create quad
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x0 + nx * r0, y0 + ny * r0);
        path.lineTo(x1 + nx * r1, y1 + ny * r1);
        path.lineTo(x1 - nx * r1, y1 - ny * r1);
        path.lineTo(x0 - nx * r0, y0 - ny * r0);
        path.closePath();
        
        g2.fill(path);
    }
    
    private BufferedImage colorize(BufferedImage img, Color color) {
        if (color == null || color.equals(Color.BLACK)) return img;
        
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha > 0) {
                    result.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        
        return result;
    }
    
    public void drawText(Graphics2D g2, CustomFont font, String text, int x, int y, int size, Color color) {
        if (font == null || text == null || text.isEmpty()) return;
        
        float scale = (float) size / font.getEmSize();
        float cursorX = x;
        boolean nativeAvailable = NativeFontSupport.isAvailable();
        Float nativeAscender = nativeAvailable ? NativeFontSupport.getAscender(font, size) : null;
        float ascender = nativeAscender != null ? nativeAscender : font.getAscender(size);
        
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if (Character.isSupplementaryCodePoint(cp)) i++;
            
            CustomGlyph glyph = font.getGlyph(cp);
            
            if (glyph != null && glyph.isDefined()) {
                BufferedImage img = renderGlyph(font, glyph, size, color);
                if (img != null) {
                    float[] bounds = nativeAvailable ? NativeFontSupport.getGlyphBounds(font, cp) : null;
                    if (bounds == null) {
                        bounds = glyph.getBounds();
                    }
                    int drawX = (int) (cursorX + bounds[0] * scale);
                    int drawY = (int) (y - ascender + bounds[1] * scale);
                    g2.drawImage(img, drawX, drawY, null);
                }
                Float advance = nativeAvailable ? NativeFontSupport.getGlyphAdvance(font, cp) : null;
                float advanceValue = (advance != null && advance > 0.0f) ? advance : glyph.getAdvanceWidth();
                cursorX += advanceValue * scale;
            } else {
                // Skip undefined glyphs, advance by default width
                cursorX += font.getEmSize() * 0.6f * scale;
            }
        }
    }
    
    public BufferedImage renderText(CustomFont font, String text, int size, Color color, Color background) {
        if (font == null || text == null || text.isEmpty()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        
        Float nativeWidth = NativeFontSupport.measureText(font, text, size);
        float width = nativeWidth != null ? nativeWidth : font.measureText(text, size);
        Float nativeHeight = NativeFontSupport.getLineHeight(font, size);
        float height = nativeHeight != null ? nativeHeight : font.getLineHeight(size);
        
        int imgWidth = Math.max(1, (int) Math.ceil(width) + 4);
        int imgHeight = Math.max(1, (int) Math.ceil(height) + 4);
        
        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        
        if (background != null) {
            g2.setColor(background);
            g2.fillRect(0, 0, imgWidth, imgHeight);
        }
        
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        Float nativeAscender = NativeFontSupport.getAscender(font, size);
        float ascender = nativeAscender != null ? nativeAscender : font.getAscender(size);
        int baseline = (int) ascender + 2;
        drawText(g2, font, text, 2, baseline, size, color);
        
        g2.dispose();
        return img;
    }
    
    public void clearCache() {
        cache.clear();
        NativeFontSupport.clearCache();
    }
}
