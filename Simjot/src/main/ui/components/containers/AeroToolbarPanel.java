/*
 * SIMJOT - No Derivatives License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE for full terms.
 */

package main.ui.components.containers;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.features.gallery.GeneratedWallpapers;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;

/**
 * Toolbar glass that paints the current app wallpaper underneath itself first.
 * This keeps north bars translucent even when the owning screen has a white body.
 */
public class AeroToolbarPanel extends FrostedGlassPanel {
    private static final Object SOURCE_LOCK = new Object();
    private static final int GENERATED_W = 2560;
    private static final int GENERATED_H = 1440;

    private static String sharedSourceKey;
    private static Image sharedSourceImage;

    private transient BufferedImage cachedBackdrop;
    private transient String cachedBackdropKey;
    private transient int cachedW = -1;
    private transient int cachedH = -1;
    private transient int cachedRootW = -1;
    private transient int cachedRootH = -1;
    private transient int cachedOffsetX = Integer.MIN_VALUE;
    private transient int cachedOffsetY = Integer.MIN_VALUE;
    private transient int cachedAccentRgb = 0;
    private transient boolean cachedMinimal;

    public AeroToolbarPanel(LayoutManager layout, int arc) {
        super(layout, arc);
        setOpacityScale(0.78f);
    }

    public AeroToolbarPanel(int arc) {
        super(arc);
        setOpacityScale(0.78f);
    }

    public AeroToolbarPanel() {
        super();
        setOpacityScale(0.78f);
    }

    @Override
    protected boolean shouldForceSolidWhenTransparentWindowsDisabled() {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintAppBackdrop(g);
        super.paintComponent(g);
    }

    private void paintAppBackdrop(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean minimal = Theme.isMinimalLook();
        String key = minimal ? "" : safeBackgroundKey();
        Component root = backdropRoot();
        Dimension rootSize = root == null ? new Dimension(w, h) : root.getSize();
        int rootW = Math.max(w, rootSize.width);
        int rootH = Math.max(h, rootSize.height);
        Point offset = componentOffset(root);
        int accentRgb = AeroTheme.resolveChromeAccent().getRGB();

        if (!isBackdropCacheValid(w, h, rootW, rootH, offset.x, offset.y, key, accentRgb, minimal)) {
            rebuildBackdrop(w, h, rootW, rootH, offset.x, offset.y, key, accentRgb, minimal);
        }

        if (cachedBackdrop != null) {
            g.drawImage(cachedBackdrop, 0, 0, this);
        }
    }

    private boolean isBackdropCacheValid(int w, int h, int rootW, int rootH, int offsetX, int offsetY,
                                         String key, int accentRgb, boolean minimal) {
        return cachedBackdrop != null
                && cachedW == w
                && cachedH == h
                && cachedRootW == rootW
                && cachedRootH == rootH
                && cachedOffsetX == offsetX
                && cachedOffsetY == offsetY
                && cachedAccentRgb == accentRgb
                && cachedMinimal == minimal
                && java.util.Objects.equals(cachedBackdropKey, key);
    }

    private void rebuildBackdrop(int w, int h, int rootW, int rootH, int offsetX, int offsetY,
                                 String key, int accentRgb, boolean minimal) {
        if (cachedBackdrop != null) cachedBackdrop.flush();
        cachedBackdrop = createBackdropImage(w, h);
        Graphics2D g2 = cachedBackdrop.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.translate(-offsetX, -offsetY);
            if (minimal) {
                g2.setColor(Color.WHITE);
                g2.fillRect(offsetX, offsetY, w, h);
            } else {
                Image source = resolveSourceImage(key);
                if (source != null && source.getWidth(this) > 0 && source.getHeight(this) > 0) {
                    paintSource(g2, source, rootW, rootH);
                } else {
                    paintFallback(g2, rootW, rootH);
                }
                paintSoftVeil(g2, rootW, rootH);
            }
        } finally {
            g2.dispose();
        }

        cachedW = w;
        cachedH = h;
        cachedRootW = rootW;
        cachedRootH = rootH;
        cachedOffsetX = offsetX;
        cachedOffsetY = offsetY;
        cachedBackdropKey = key;
        cachedAccentRgb = accentRgb;
        cachedMinimal = minimal;
    }

    private void paintSource(Graphics2D g2, Image source, int rootW, int rootH) {
        int imgW = source.getWidth(this);
        int imgH = source.getHeight(this);
        double scale = Math.max((double) rootW / imgW, (double) rootH / imgH);
        int drawW = Math.max(1, (int) Math.ceil(imgW * scale));
        int drawH = Math.max(1, (int) Math.ceil(imgH * scale));
        int x = (rootW - drawW) / 2;
        int y = (rootH - drawH) / 2;
        g2.drawImage(source, x, y, drawW, drawH, this);
    }

    private void paintFallback(Graphics2D g2, int rootW, int rootH) {
        Color accent = AeroTheme.resolveChromeAccent();
        GradientPaint base = new GradientPaint(
                0, 0, AeroTheme.withAlpha(AeroTheme.lift(accent, 0.88f), 220),
                0, rootH, AeroTheme.withAlpha(AeroTheme.lift(accent, 0.72f), 176)
        );
        g2.setPaint(base);
        g2.fillRect(0, 0, rootW, rootH);
    }

    private void paintSoftVeil(Graphics2D g2, int rootW, int rootH) {
        if (Theme.isPlainWhite()) return;
        Color accent = AeroTheme.resolveChromeAccent();
        g2.setComposite(AlphaComposite.SrcOver.derive(0.36f));
        java.awt.RadialGradientPaint bloom = new java.awt.RadialGradientPaint(
                new Point2D.Float(rootW * 0.22f, rootH * 0.08f),
                Math.max(rootW, rootH) * 0.72f,
                new float[]{0f, 0.48f, 1f},
                new Color[]{
                        AeroTheme.withAlpha(AeroTheme.lift(accent, 0.62f), 112),
                        new Color(255, 255, 255, 58),
                        new Color(255, 255, 255, 0)
                }
        );
        g2.setPaint(bloom);
        g2.fillRect(0, 0, rootW, rootH);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private String safeBackgroundKey() {
        try {
            String bg = SettingsStore.get().getBackgroundImage();
            return bg == null ? "" : bg.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Image resolveSourceImage(String key) {
        if (key == null || key.isEmpty()) return null;
        synchronized (SOURCE_LOCK) {
            if (key.equals(sharedSourceKey) && sharedSourceImage != null
                    && sharedSourceImage.getWidth(null) > 0 && sharedSourceImage.getHeight(null) > 0) {
                return sharedSourceImage;
            }
            Image next = loadSourceImage(key);
            sharedSourceKey = key;
            sharedSourceImage = next;
            return next;
        }
    }

    private static Image loadSourceImage(String key) {
        try {
            Image img;
            if (key.startsWith("gen:")) {
                img = GeneratedWallpapers.render(key, GENERATED_W, GENERATED_H);
            } else if (key.startsWith("res:")) {
                img = ResourceLoader.createImage("Simjot/" + key.substring(4));
            } else {
                File file = new File(key);
                if (!file.exists()) return null;
                img = new ImageIcon(key).getImage();
            }
            return img != null && img.getWidth(null) > 0 && img.getHeight(null) > 0 ? img : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Component backdropRoot() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && window.getWidth() > 0 && window.getHeight() > 0) return window;
        return this;
    }

    private Point componentOffset(Component root) {
        if (root == null || root == this) return new Point(0, 0);
        try {
            return SwingUtilities.convertPoint(this, 0, 0, root);
        } catch (Throwable ignored) {
            return new Point(0, 0);
        }
    }

    private static BufferedImage createBackdropImage(int w, int h) {
        return new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
    }
}
