/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.containers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.Shape;
import java.awt.geom.Point2D;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import main.core.service.SettingsStore;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;

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
    private boolean flat = false;
    private Color flatColor = Color.WHITE;
    
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

    /** Enable flat (solid) background rendering instead of frosted glass. */
    public void setFlat(boolean flat) {
        this.flat = flat;
        repaint();
    }

    /** Set the solid background color used when flat mode is enabled. */
    public void setFlatColor(Color color) {
        if (color == null) return;
        this.flatColor = color;
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

        if (SettingsStore.get().isTransparentWindowsDisabled()) {
            g2.setColor(flatColor != null ? flatColor : Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (win != null && win.getBackground().getAlpha() == 0) win.setBackground(flatColor != null ? flatColor : Color.WHITE);
            return;
        }

        // Explicitly clear background to transparent to fix uninitialized VRAM artifacts on some Linux compositors
        g2.setComposite(java.awt.AlphaComposite.Clear);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(java.awt.AlphaComposite.SrcOver);
        
        float opacity = Math.max(0f, Math.min(1f, getOpacityScale()));
        
        // Content area (excluding shadow margins)
        int contentX = shadowSize;
        int contentY = shadowSize;
        int contentW = w - shadowSize * 2;
        int contentH = h - shadowSize * 2;
        
        // Paint multi-layer drop shadow
        paintShadow(g2, contentX, contentY, contentW, contentH, opacity);
        
        // Paint background
        if (flat) {
            paintFlatBackground(g2, contentX, contentY, contentW, contentH, opacity);
        } else {
            paintFrostedBackground(g2, contentX, contentY, contentW, contentH, opacity);
        }
        
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
        boolean plain = Theme.isPlainWhite();
        Color accent = AeroTheme.resolveChromeAccent();
        
        // Tinted dialog glass with a brighter, more Aero-like atmosphere.
        Color topTone = plain
                ? new Color(255, 255, 255, 228)
                : AeroTheme.withAlpha(AeroTheme.lift(accent, 0.9f), 224);
        Color bottomTone = plain
                ? new Color(242, 245, 250, 198)
                : AeroTheme.withAlpha(AeroTheme.blend(AeroTheme.lift(accent, 0.76f), new Color(232, 238, 248), 0.4f), 196);
        GradientPaint base = new GradientPaint(
            x, y, scaleAlpha(topTone, opacity),
            x, y + h, scaleAlpha(bottomTone, opacity)
        );
        g2.setPaint(base);
        g2.fill(fillShape);

        Shape oldClip = g2.getClip();
        g2.setClip(fillShape);
        RadialGradientPaint bloom = new RadialGradientPaint(
                new Point2D.Float(x + w * 0.22f, y + h * 0.06f),
                Math.max(58f, Math.max(w, h) * 0.82f),
                new float[]{0f, 0.32f, 1f},
                new Color[]{
                        scaleAlpha(AeroTheme.withAlpha(AeroTheme.lift(accent, 0.6f), plain ? 34 : 92), opacity),
                        scaleAlpha(new Color(255, 255, 255, plain ? 22 : 44), opacity),
                        new Color(255, 255, 255, 0)
                }
        );
        g2.setPaint(bloom);
        g2.fillRect(x, y, w, h);
        g2.setClip(oldClip);
        
        // Glass sheen overlay
        GradientPaint sheen = new GradientPaint(
            x, y, scaleAlpha(new Color(255, 255, 255, plain ? 124 : 164), opacity),
            x, y + h * 0.5f, scaleAlpha(new Color(255, 255, 255, plain ? 24 : 38), opacity)
        );
        g2.setPaint(sheen);
        g2.fill(fillShape);

        GradientPaint aquaLift = new GradientPaint(
            x, y + h * 0.58f, scaleAlpha(AeroTheme.withAlpha(accent, 0), opacity),
            x, y + h, scaleAlpha(AeroTheme.withAlpha(AeroTheme.sink(accent, 0.2f), plain ? 16 : 56), opacity)
        );
        g2.setPaint(aquaLift);
        g2.fill(fillShape);
        
        // Subtle bottom shadow
        GradientPaint shadow = new GradientPaint(
            x, y + h * 0.5f, scaleAlpha(new Color(0, 0, 0, plain ? 8 : 10), opacity),
            x, y + h, scaleAlpha(new Color(0, 0, 0, plain ? 26 : 30), opacity)
        );
        g2.setPaint(shadow);
        g2.fill(fillShape);
        
        // Inner highlight
        int innerArc = Math.max(rArc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, plain ? 102 : 142), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, innerArc, innerArc));
        g2.setColor(scaleAlpha(AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.5f), plain ? 34 : 74), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 0.9f, y + 0.9f, w - 1.8f, h - 1.8f, Math.max(rArc - 1, 4), Math.max(rArc - 1, 4)));
        
        // Outer border
        g2.setColor(scaleAlpha(new Color(0, 0, 0, plain ? 30 : 34), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, rArc, rArc));
    }

    private void paintFlatBackground(Graphics2D g2, int x, int y, int w, int h, float opacity) {
        int rArc = Math.max(arc, 6);
        RoundRectangle2D fillShape = new RoundRectangle2D.Float(x, y, w, h, rArc, rArc);
        Color base = flatColor != null ? flatColor : Color.WHITE;
        g2.setColor(scaleAlpha(base, opacity));
        g2.fill(fillShape);

        g2.setColor(scaleAlpha(new Color(255, 255, 255, 200), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, Math.max(rArc - 2, 2), Math.max(rArc - 2, 2)));
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 35), opacity));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, rArc, rArc));
    }
}
