/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.containers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A container panel for undecorated dialogs that provides:
 * - Soft drop shadow around the content
 * - Proper margins to prevent rounded corner clipping
 * - Frosted glass background effect
 * 
 * Use this as the root container in undecorated dialogs to ensure
 * rounded corners render correctly without clipping.
 */
public class ShadowedDialogPanel extends JPanel {
    
    private static final int DEFAULT_SHADOW_SIZE = 12;
    private static final int DEFAULT_ARC = 18;
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 40);
    
    private final int shadowSize;
    private final int arc;
    private float opacityScale = 1.0f;
    
    public ShadowedDialogPanel() {
        this(new BorderLayout(), DEFAULT_ARC, DEFAULT_SHADOW_SIZE);
    }
    
    public ShadowedDialogPanel(int arc) {
        this(new BorderLayout(), arc, DEFAULT_SHADOW_SIZE);
    }
    
    public ShadowedDialogPanel(LayoutManager layout, int arc) {
        this(layout, arc, DEFAULT_SHADOW_SIZE);
    }
    
    public ShadowedDialogPanel(LayoutManager layout, int arc, int shadowSize) {
        super(layout);
        this.arc = arc;
        this.shadowSize = shadowSize;
        setOpaque(false);
        setBorder(new EmptyBorder(shadowSize, shadowSize, shadowSize, shadowSize));
        setBackground(new Color(245, 245, 245, 170));
    }
    
    public void setOpacityScale(float scale) {
        if (Float.isNaN(scale)) return;
        this.opacityScale = Math.max(0f, Math.min(1f, scale));
        repaint();
    }
    
    protected float getOpacityScale() {
        return opacityScale;
    }
    
    private static Color scaleAlpha(Color color, float scale) {
        int alpha = Math.round(color.getAlpha() * scale);
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        float opacity = Math.max(0f, Math.min(1f, getOpacityScale()));
        
        // Content area (excluding shadow margins)
        int contentX = shadowSize;
        int contentY = shadowSize;
        int contentW = w - shadowSize * 2;
        int contentH = h - shadowSize * 2;
        
        // Paint multi-layer drop shadow
        paintShadow(g2, contentX, contentY, contentW, contentH, opacity);
        
        // Paint frosted glass background
        paintFrostedBackground(g2, contentX, contentY, contentW, contentH, opacity);
        
        g2.dispose();
    }
    
    private void paintShadow(Graphics2D g2, int x, int y, int w, int h, float opacity) {
        // Multi-layer shadow for soft effect
        for (int i = shadowSize; i > 0; i--) {
            float alpha = (float) i / (shadowSize * 2.5f);
            Color shadowLayer = new Color(
                SHADOW_COLOR.getRed(),
                SHADOW_COLOR.getGreen(),
                SHADOW_COLOR.getBlue(),
                (int) (SHADOW_COLOR.getAlpha() * alpha * opacity)
            );
            g2.setColor(shadowLayer);
            
            int offset = shadowSize - i;
            int layerArc = Math.max(arc - offset / 2, 4);
            g2.fill(new RoundRectangle2D.Float(
                x - offset, y - offset + offset / 3f,
                w + offset * 2, h + offset * 2 - offset / 3f,
                layerArc, layerArc
            ));
        }
    }
    
    private void paintFrostedBackground(Graphics2D g2, int x, int y, int w, int h, float opacity) {
        int rArc = Math.max(arc, 6);
        RoundRectangle2D fillShape = new RoundRectangle2D.Float(x, y, w, h, rArc, rArc);
        
        // Soft translucent gradient base
        GradientPaint base = new GradientPaint(
            x, y, scaleAlpha(new Color(255, 255, 255, 230), opacity),
            x, y + h, scaleAlpha(new Color(240, 240, 240, 200), opacity)
        );
        g2.setPaint(base);
        g2.fill(fillShape);
        
        // Glass sheen overlay
        GradientPaint sheen = new GradientPaint(
            x, y, scaleAlpha(new Color(255, 255, 255, 120), opacity),
            x, y + h * 0.5f, scaleAlpha(new Color(255, 255, 255, 20), opacity)
        );
        g2.setPaint(sheen);
        g2.fill(fillShape);
        
        // Subtle bottom shadow
        GradientPaint shadow = new GradientPaint(
            x, y + h * 0.5f, scaleAlpha(new Color(0, 0, 0, 8), opacity),
            x, y + h, scaleAlpha(new Color(0, 0, 0, 25), opacity)
        );
        g2.setPaint(shadow);
        g2.fill(fillShape);
        
        // Inner highlight
        int innerArc = Math.max(rArc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 100), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, innerArc, innerArc));
        
        // Outer border
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 30), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, rArc, rArc));
    }
}
