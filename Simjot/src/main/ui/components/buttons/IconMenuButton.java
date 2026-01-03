/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.buttons;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.Timer;

import main.core.service.SettingsStore;
import main.infrastructure.monitoring.AppPerf;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

/**
 * Minimal icon-first menu button.
 * Draws a centered SVG/PNG/vector icon; on hover/press shows a soft overlay and reveals the caption under the icon.
 */
public class IconMenuButton extends JButton {
    private final String iconId;
    private final String caption;
    private boolean hoverTarget = false;
    private float hoverT = 0f;
    private Timer hoverTimer;

    public IconMenuButton(String text, String iconId) {
        super(""); // we render text manually
        this.caption = text == null ? "" : text;
        this.iconId = iconId == null ? "" : iconId.toLowerCase();
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(new Font("SansSerif", Font.BOLD, 16));

        // Size tuned for icon + hover caption
        int w = 80;
        int h = 96;
        Dimension pref = new Dimension(w, h);
        setPreferredSize(pref);
        setMinimumSize(pref);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 10));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { setHoverTarget(true); } }
            @Override public void mouseExited(MouseEvent e) { setHoverTarget(false); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        boolean enabled = isEnabled();
        
        // When disabled, apply transparency to the whole component
        if (!enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        }

        // Background plate (subtle only on hover/press, and only when enabled)
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        float t = hoverT;
        if (pressed) t = Math.min(1f, t + 0.2f);
        if (enabled && (t > 0.01f || pressed)) {
            int arc = 16;
            Shape plate = new RoundRectangle2D.Float(6, 6, w - 12, h - 26, arc, arc);
            Rectangle r = new Rectangle(6, 6, w - 12, h - 26);
            Color accent = resolveAccent();

            if (Theme.isPlainWhite()) {
                Color fill = new Color(240, 244, 249, Math.round(60 + 140 * t));
                g2.setColor(fill);
                g2.fill(plate);
                g2.setColor(new Color(180, 190, 205, Math.round(80 + 120 * t)));
                g2.draw(plate);
            } else {
                Color top = new Color(255, 255, 255, Math.round(110 + 120 * t));
                Color bottom = new Color(215, 228, 245, Math.round(110 + 130 * t));
                AeroPainters.paintVerticalGradient(g2, r, top, bottom, arc);
                g2.setComposite(AlphaComposite.SrcOver.derive(0.55f + 0.35f * t));
                AeroPainters.paintGlassOverlay(g2, r, arc);
                g2.setComposite(AlphaComposite.SrcOver);
                if (t > 0.01f) {
                    RadialGradientPaint wash = new RadialGradientPaint(
                            new Point(w / 2, h / 2),
                            Math.max(w, h) * 0.7f,
                            new float[]{0f, 1f},
                            new Color[]{withAlpha(accent, Math.round(90 * t)), withAlpha(accent, 0)}
                    );
                    g2.setPaint(wash);
                    g2.fill(plate);
                }
                AeroPainters.paintInnerStroke(g2, r, arc, new Color(255, 255, 255, Math.round(70 + 80 * t)));
                g2.setColor(mix(new Color(180, 190, 205, 140), withAlpha(accent, 150), t * 0.6f));
                g2.draw(plate);
            }
        }

        // Icon
        int iconSize = Math.min(w - 18, 48);
        int iconX = (w - iconSize) / 2;
        int lift = (int) Math.round(4 * (1f - t));
        int iconY = 12 + lift; // nudge down a bit when idle
        String resPath = ImageIconRenderer.mapIdToResource(iconId);
        if (resPath != null) {
            ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconSize, this, true);
        }

        // Caption (only when hovered and enabled)
        if (enabled && t > 0.01f) {
            Color captionColor = AccentColorUtil.blend(getForeground(), resolveAccent(), 0.25f * t);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.2f + 0.8f * t));
            g2.setColor(captionColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(caption);
            int textX = (w - textW) / 2;
            int textY = iconY + iconSize + fm.getAscent() + 4;
            g2.drawString(caption, textX, textY);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        g2.dispose();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            setHoverTarget(false);
        }
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    private void setHoverTarget(boolean hover) {
        if (isHoverAnimationDisabled()) {
            hoverT = hover ? 1f : 0f;
            stopHoverTimer();
            repaint();
            return;
        }
        hoverTarget = hover;
        if (hoverTimer == null) {
            hoverTimer = new Timer(AppPerf.getAnimationDelay(), e -> animateHover());
            hoverTimer.start();
        }
    }

    private void animateHover() {
        float target = hoverTarget ? 1f : 0f;
        float step = Math.max(0.04f, Math.min(0.18f, AppPerf.getAnimationDelay() / 220f));
        if (hoverT < target) {
            hoverT = Math.min(target, hoverT + step);
        } else if (hoverT > target) {
            hoverT = Math.max(target, hoverT - step);
        }
        if (Math.abs(hoverT - target) < 0.001f) {
            hoverT = target;
            stopHoverTimer();
        }
        repaint();
    }

    private void stopHoverTimer() {
        if (hoverTimer != null) {
            hoverTimer.stop();
            hoverTimer = null;
        }
    }

    private boolean isHoverAnimationDisabled() {
        try {
            return SettingsStore.get().isMainMenuAnimationsDisabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Color resolveAccent() {
        try {
            int rgb = SettingsStore.get().getMainMenuAccentRGB();
            if (rgb != Integer.MIN_VALUE) return new Color(rgb);
        } catch (Throwable ignored) {
        }
        return AccentColorUtil.defaultAccent();
    }

    private static Color mix(Color a, Color b, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped);
        return new Color(r, g, bl, al);
    }

    private static Color withAlpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
