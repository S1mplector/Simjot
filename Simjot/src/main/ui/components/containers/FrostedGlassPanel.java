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

/**
 * Frosted glass container for toolbar rows. It lets the wallpaper show through gently
 * while adding a soft glass sheen and a subtle outline so controls stay legible. 
 * The rendering is meant to be lightweight, using only basic Java 2D operations, 
 * such as gradients and rounded rectangles, to achieve the frosted glass effect. 
 * 
 * Other ways of doing this could be using a frosted glass image as a background,
 * but this would require a separate image for each resolution and size, and would
 * also require the image to be updated when the wallpaper changes.
 * 
 * Due to that I've decided to not use the latter approach and instead use the first one. 
 * 
 * @author S1mplector
 */
public class FrostedGlassPanel extends JPanel {
    private int arc;
    private float opacityScale = 1.0f;

    public FrostedGlassPanel() {
        this(new BorderLayout(), 14);
    }

    public FrostedGlassPanel(int arc) {
        this(new BorderLayout(), arc);
    }

    public FrostedGlassPanel(LayoutManager layout, int arc) {
        super(layout);
        this.arc = arc;
        setOpaque(false);
        // Expose a representative base colour so children (e.g., sliders) can align their clears.
        setBackground(new Color(245, 245, 245, 170));
    }

    public void setArc(int arc) {
        this.arc = arc;
        repaint();
    }

    public void setOpacityScale(float opacityScale) {
        if (Float.isNaN(opacityScale)) return;
        this.opacityScale = Math.max(0f, Math.min(1f, opacityScale));
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

        int rArc = Math.max(arc, 6);
        RoundRectangle2D fillShape = new RoundRectangle2D.Float(0, 0, w, h, rArc, rArc);

        // Soft translucent gradient that lets the wallpaper peek through.
        GradientPaint base = new GradientPaint(
                0, 0, scaleAlpha(new Color(255, 255, 255, 205), opacity),
                0, h, scaleAlpha(new Color(235, 235, 235, 150), opacity)
        );
        g2.setPaint(base);
        g2.fill(fillShape);

        // Glass sheen: bright top fade plus a gentle lower shadow to keep the bar distinct.
        GradientPaint sheen = new GradientPaint(
                0, 0, scaleAlpha(new Color(255, 255, 255, 110), opacity),
                0, h * 0.55f, scaleAlpha(new Color(255, 255, 255, 25), opacity)
        );
        g2.setPaint(sheen);
        g2.fill(fillShape);
        GradientPaint shadow = new GradientPaint(
                0, h * 0.45f, scaleAlpha(new Color(0, 0, 0, 12), opacity),
                0, h, scaleAlpha(new Color(0, 0, 0, 40), opacity)
        );
        g2.setPaint(shadow);
        g2.fill(fillShape);

        int innerArc = Math.max(rArc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 90), opacity));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, innerArc, innerArc));
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 35), opacity));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, rArc, rArc));

        g2.dispose();
    }
}
