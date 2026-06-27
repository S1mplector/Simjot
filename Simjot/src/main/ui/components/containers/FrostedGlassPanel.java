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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.RoundRectangle2D;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;

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
    public static final String MINIMAL_LOOK_KEEP_GLASS = "minimalLook.keepGlass";

    private int arc;
    private float opacityScale = 1.0f;
    private transient BufferedImage cachedGlassImage;
    private transient int cachedGlassW = -1;
    private transient int cachedGlassH = -1;
    private transient int cachedGlassArc = -1;
    private transient int cachedGlassAccentRgb = 0;
    private transient float cachedGlassOpacity = -1f;
    private transient boolean cachedGlassPlain;
    private transient boolean cachedGlassTrueAero;

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
        invalidateGlassCache();
        repaint();
    }

    public void setOpacityScale(float opacityScale) {
        if (Float.isNaN(opacityScale)) return;
        this.opacityScale = Math.max(0f, Math.min(1f, opacityScale));
        invalidateGlassCache();
        repaint();
    }

    protected float getOpacityScale() {
        return opacityScale;
    }

    public void setMinimalLookKeepGlass(boolean keepGlass) {
        putClientProperty(MINIMAL_LOOK_KEEP_GLASS, Boolean.valueOf(keepGlass));
        invalidateGlassCache();
        repaint();
    }

    private boolean shouldUseSolidMinimalLook() {
        return Theme.isMinimalLook() && !Boolean.TRUE.equals(getClientProperty(MINIMAL_LOOK_KEEP_GLASS));
    }

    protected boolean shouldForceSolidWhenTransparentWindowsDisabled() {
        return true;
    }

    private static Color scaleAlpha(Color color, float scale) {
        int alpha = Math.round(color.getAlpha() * scale);
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void invalidateGlassCache() {
        cachedGlassImage = null;
        cachedGlassW = -1;
        cachedGlassH = -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean trueAero = main.core.service.SettingsStore.get().isTrueAeroEnabled();

        if (shouldForceSolidWhenTransparentWindowsDisabled()
                && main.core.service.SettingsStore.get().isTransparentWindowsDisabled()
                && !trueAero) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.WHITE); // FrostedGlassPanel usually resolves to near white
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (win != null && win.getBackground().getAlpha() == 0) win.setBackground(Color.WHITE);
            return;
        }

        if (shouldUseSolidMinimalLook()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(218, 226, 234));
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.dispose();
            return;
        }

        float opacity = Math.max(0f, Math.min(1f, getOpacityScale()));
        boolean plain = Theme.isPlainWhite();
        Color accent = AeroTheme.resolveChromeAccent();
        int accentRgb = accent.getRGB();

        if (!isGlassCacheValid(w, h, trueAero, plain, accentRgb, opacity)) {
            if (cachedGlassImage != null) cachedGlassImage.flush();
            cachedGlassImage = createTranslucentImage(w, h);
            Graphics2D imageG = cachedGlassImage.createGraphics();
            try {
                paintGlass(imageG, w, h, trueAero, plain, accent, opacity);
            } finally {
                imageG.dispose();
            }
            cachedGlassW = w;
            cachedGlassH = h;
            cachedGlassArc = arc;
            cachedGlassAccentRgb = accentRgb;
            cachedGlassOpacity = opacity;
            cachedGlassPlain = plain;
            cachedGlassTrueAero = trueAero;
        }

        g.drawImage(cachedGlassImage, 0, 0, this);
    }

    private boolean isGlassCacheValid(int w, int h, boolean trueAero, boolean plain, int accentRgb, float opacity) {
        return cachedGlassImage != null
                && cachedGlassW == w
                && cachedGlassH == h
                && cachedGlassArc == arc
                && cachedGlassAccentRgb == accentRgb
                && Float.compare(cachedGlassOpacity, opacity) == 0
                && cachedGlassPlain == plain
                && cachedGlassTrueAero == trueAero;
    }

    private static BufferedImage createTranslucentImage(int w, int h) {
        try {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
            return gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        } catch (Throwable ignored) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private void paintGlass(Graphics2D g2, int w, int h, boolean trueAero, boolean plain, Color accent, float opacity) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!trueAero) {
            // Explicitly clear background to transparent to fix uninitialized VRAM artifacts on some Linux compositors
            g2.setComposite(java.awt.AlphaComposite.Clear);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
        }

        int rArc = Math.max(arc, 6);
        RoundRectangle2D fillShape = new RoundRectangle2D.Float(0, 0, w, h, rArc, rArc);

        // Accent-tinted glass base that feels more like Aero and less like neutral white acrylic.
        Color topTone = plain
                ? new Color(255, 255, 255, 214)
                : AeroTheme.withAlpha(AeroTheme.lift(accent, 0.88f), 198);
        Color bottomTone = plain
                ? new Color(244, 246, 250, 168)
                : AeroTheme.withAlpha(AeroTheme.blend(AeroTheme.lift(accent, 0.74f), new Color(229, 238, 248), 0.42f), 160);
        GradientPaint base = new GradientPaint(
                0, 0, scaleAlpha(topTone, opacity),
                0, h, scaleAlpha(bottomTone, opacity)
        );
        g2.setPaint(base);
        g2.fill(fillShape);

        // Upper-left bloom gives the panel that dewy, luminous desktop feel.
        Shape oldClip = g2.getClip();
        g2.setClip(fillShape);
        RadialGradientPaint bloom = new RadialGradientPaint(
                new Point2D.Float(w * 0.22f, h * 0.08f),
                Math.max(42f, Math.max(w, h) * 0.78f),
                new float[]{0f, 0.35f, 1f},
                new Color[]{
                        scaleAlpha(AeroTheme.withAlpha(AeroTheme.lift(accent, 0.62f), plain ? 38 : 88), opacity),
                        scaleAlpha(new Color(255, 255, 255, plain ? 26 : 44), opacity),
                        new Color(255, 255, 255, 0)
                }
        );
        g2.setPaint(bloom);
        g2.fillRect(0, 0, w, h);
        g2.setClip(oldClip);

        // Glass sheen: bright top fade plus a gentle lower shadow to keep the bar distinct.
        GradientPaint sheen = new GradientPaint(
                0, 0, scaleAlpha(new Color(255, 255, 255, plain ? 122 : 152), opacity),
                0, h * 0.58f, scaleAlpha(new Color(255, 255, 255, plain ? 22 : 34), opacity)
        );
        g2.setPaint(sheen);
        g2.fill(fillShape);
        GradientPaint aquaRim = new GradientPaint(
                0, h * 0.48f, scaleAlpha(AeroTheme.withAlpha(accent, 0), opacity),
                0, h, scaleAlpha(AeroTheme.withAlpha(AeroTheme.sink(accent, 0.22f), plain ? 18 : 54), opacity)
        );
        g2.setPaint(aquaRim);
        g2.fill(fillShape);
        GradientPaint shadow = new GradientPaint(
                0, h * 0.45f, scaleAlpha(new Color(0, 0, 0, plain ? 8 : 12), opacity),
                0, h, scaleAlpha(new Color(0, 0, 0, plain ? 24 : 42), opacity)
        );
        g2.setPaint(shadow);
        g2.fill(fillShape);

        int innerArc = Math.max(rArc - 2, 2);
        g2.setColor(scaleAlpha(new Color(255, 255, 255, plain ? 96 : 132), opacity));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, innerArc, innerArc));
        g2.setColor(scaleAlpha(AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.52f), plain ? 40 : 72), opacity));
        g2.draw(new RoundRectangle2D.Float(0.9f, 0.9f, w - 1.8f, h - 1.8f, Math.max(rArc - 1, 4), Math.max(rArc - 1, 4)));
        g2.setColor(scaleAlpha(new Color(0, 0, 0, plain ? 30 : 38), opacity));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, rArc, rArc));
    }
}
