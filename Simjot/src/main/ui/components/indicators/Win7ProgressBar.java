/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.indicators;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Windows 7-style animated progress bar with iconic green gradient,
 * glass effect, and sweeping glow animation.
 */
public class Win7ProgressBar extends JComponent {
    
    // Progress (0.0 - 1.0) or -1 for indeterminate
    private float progress = -1f;
    private boolean indeterminate = true;
    
    // Animation state
    private float glowPosition = 0f;
    private final Timer animTimer;
    private int lastGlowX = -1;
    private int lastGlowW = 0;
    
    // Colors matching Windows 7 green progress bar
    private static final Color GREEN_DARK = new Color(0, 160, 0);
    private static final Color GREEN_LIGHT = new Color(50, 205, 50);
    private static final Color GLASS_TOP = new Color(255, 255, 255, 100);
    private static final Color GLASS_BOTTOM = new Color(255, 255, 255, 30);
    private static final Color BORDER_OUTER = new Color(80, 80, 80);
    private static final Color BORDER_INNER = new Color(40, 100, 40);
    private static final Color TRACK_TOP = new Color(200, 200, 200);
    private static final Color TRACK_BOTTOM = new Color(230, 230, 230);
    
    public Win7ProgressBar() {
        setOpaque(false);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(300, 22));
        setMinimumSize(new Dimension(100, 18));
        
        // Animation timer for glow sweep effect (~60fps)
        animTimer = new Timer(16, e -> {
            // Smaller step to avoid jumping (≈1 px/frame on 280px wide bar)
            float step = indeterminate ? 0.0032f : 0.0048f;
            glowPosition += step;
            if (glowPosition > 1.4f) glowPosition = -0.4f;

            // Repaint only the area affected by the moving glow to reduce flicker
            int w = Math.max(1, getWidth() - 4);
            int innerW = Math.max(1, w);
            int glowWidth = (int) (innerW * 0.35f);
            int glowX = 2 + (int) ((innerW + glowWidth) * glowPosition) - glowWidth;
            int repaintX = Math.min(glowX, lastGlowX);
            int repaintW = (lastGlowX < 0) ? (glowWidth + 6) : (Math.max(glowX + glowWidth, lastGlowX + lastGlowW) - repaintX) + 6;
            lastGlowX = glowX;
            lastGlowW = glowWidth;
            repaint(Math.max(0, repaintX), 0, Math.min(getWidth(), repaintW), getHeight());
        });
    }
    
    @Override
    public void addNotify() {
        super.addNotify();
        if (!animTimer.isRunning()) animTimer.start();
    }
    
    @Override
    public void removeNotify() {
        if (animTimer.isRunning()) animTimer.stop();
        super.removeNotify();
    }
    
    public void setProgress(float value) {
        this.progress = Math.max(0f, Math.min(1f, value));
        this.indeterminate = false;
        repaint();
    }
    
    public void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        if (indeterminate) {
            glowPosition = -0.4f;
        }
        repaint();
    }
    
    public float getProgress() {
        return progress;
    }
    
    public boolean isIndeterminate() {
        return indeterminate;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        
        int w = getWidth();
        int h = getHeight();
        int arc = 6;
        int inset = 2;
        
        // Track background with subtle gradient
        RoundRectangle2D track = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc);
        GradientPaint trackGrad = new GradientPaint(0, 0, TRACK_TOP, 0, h, TRACK_BOTTOM);
        g2.setPaint(trackGrad);
        g2.fill(track);
        
        // Outer border
        g2.setColor(BORDER_OUTER);
        g2.draw(track);
        
        // Inner track area
        int innerX = inset;
        int innerY = inset;
        int innerW = w - inset * 2;
        int innerH = h - inset * 2;
        
        // Calculate fill width
        float fillRatio = indeterminate ? 1f : progress;
        int fillW = (int) (innerW * fillRatio);
        if (fillW < 1 && fillRatio > 0) fillW = 1;
        
        if (fillW > 0) {
            // Clip to inner rounded rect
            RoundRectangle2D innerClip = new RoundRectangle2D.Float(innerX, innerY, fillW, innerH, arc - 2, arc - 2);
            Shape oldClip = g2.getClip();
            g2.clip(innerClip);
            
            // Main green gradient (darker at edges, lighter in center)
            GradientPaint mainGrad = new GradientPaint(
                0, innerY, GREEN_DARK,
                0, innerY + innerH, GREEN_LIGHT
            );
            g2.setPaint(mainGrad);
            g2.fillRect(innerX, innerY, fillW, innerH);
            
            // Add horizontal gradient for depth
            GradientPaint depthGrad = new GradientPaint(
                innerX, 0, new Color(0, 0, 0, 40),
                innerX + fillW, 0, new Color(0, 0, 0, 0)
            );
            g2.setPaint(depthGrad);
            g2.fillRect(innerX, innerY, fillW, innerH);
            
            // Glass highlight on top half
            int glassH = innerH / 2;
            GradientPaint glassGrad = new GradientPaint(
                0, innerY, GLASS_TOP,
                0, innerY + glassH, GLASS_BOTTOM
            );
            g2.setPaint(glassGrad);
            g2.fillRect(innerX, innerY, fillW, glassH);
            
            // Animated sweeping glow
            paintGlowEffect(g2, innerX, innerY, fillW, innerH);
            
            // Segmented blocks (subtle vertical lines like Win7)
            paintSegments(g2, innerX, innerY, fillW, innerH);
            
            g2.setClip(oldClip);
            
            // Inner border around fill
            g2.setColor(BORDER_INNER);
            g2.draw(innerClip);
        }
        
        g2.dispose();
    }
    
    private void paintGlowEffect(Graphics2D g2, int x, int y, int w, int h) {
        // Calculate glow position
        int glowWidth = (int) (w * 0.35f);
        int glowX = x + (int) ((w + glowWidth) * glowPosition) - glowWidth;

        // Only paint if glow is within visible area
        if (glowX + glowWidth < x || glowX > x + w) return;

        // Soften glow with gradient band
        int gx = Math.max(x, glowX);
        int gw = Math.min(glowX + glowWidth, x + w) - gx;
        if (gw <= 0) return;

        float[] fractions = {0f, 0.25f, 0.5f, 0.75f, 1f};
        Color[] colors = {
            new Color(255, 255, 255, 0),
            new Color(230, 255, 230, 70),
            new Color(255, 255, 255, 130),
            new Color(230, 255, 230, 70),
            new Color(255, 255, 255, 0)
        };
        LinearGradientPaint lg = new LinearGradientPaint(gx, 0, gx + gw, 0, fractions, colors);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setPaint(lg);
        g2.fillRect(gx, y + 1, gw, h - 2);
    }
    
    private void paintSegments(Graphics2D g2, int x, int y, int w, int h) {
        // Subtle vertical segment lines (every ~8 pixels)
        g2.setColor(new Color(0, 100, 0, 30));
        int segmentWidth = 8;
        for (int sx = x + segmentWidth; sx < x + w; sx += segmentWidth) {
            g2.drawLine(sx, y, sx, y + h);
        }
        
        // Brighter highlight lines next to segments
        g2.setColor(new Color(150, 255, 150, 20));
        for (int sx = x + segmentWidth + 1; sx < x + w; sx += segmentWidth) {
            g2.drawLine(sx, y + 1, sx, y + h - 2);
        }
    }
    
    /** Create a compact version suitable for splash screens */
    public static Win7ProgressBar createCompact() {
        Win7ProgressBar bar = new Win7ProgressBar();
        bar.setPreferredSize(new Dimension(280, 18));
        return bar;
    }
}
