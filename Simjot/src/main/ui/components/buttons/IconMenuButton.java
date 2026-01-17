/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.buttons;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.Timer;

import main.core.service.SettingsStore;
import main.infrastructure.monitoring.AppPerf;
import main.ui.components.icons.ImageIconRenderer;
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
    private float hoverV = 0f;
    private long hoverLastNs = 0L;
    private Timer hoverTimer;
    private boolean aeroGlowEnabled = false;
    private Font cachedCaptionFont;
    private int cachedCaptionWidth = -1;
    private int cachedCaptionAscent = 0;

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
        setFont(new Font("SansSerif", Font.BOLD, 17));

        // Size tuned for icon + hover caption
        int w = 92;
        int h = 110;
        Dimension pref = new Dimension(w, h);
        setPreferredSize(pref);
        setMinimumSize(pref);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 12));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { setHoverTarget(true); } }
            @Override public void mouseExited(MouseEvent e) { setHoverTarget(false); }
        });
    }

    public IconMenuButton setAeroGlowEnabled(boolean enabled) {
        this.aeroGlowEnabled = enabled;
        repaint();
        return this;
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

        float t = hoverT;
        float te = smoothStep(t);

        // Icon
        int iconSize = Math.min(w - 18, 56);
        int iconX = (w - iconSize) / 2;
        float lift = 5f * (1f - te);
        int iconY = 14; // base position; lift applied via transform

        if (aeroGlowEnabled && enabled && te > 0.01f) {
            int arc = 16;
            int overlayH = Math.max(1, h - 9);
            RoundRectangle2D overlay = new RoundRectangle2D.Float(1, 1, w - 3f, overlayH, arc, arc);
            float alpha = 0.35f + 0.45f * te;
            Composite oldComposite = g2.getComposite();
            Stroke oldStroke = g2.getStroke();

            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 220), 0, overlayH,
                    new Color(224, 232, 244, 210)));
            g2.fill(overlay);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.25f * te));
            g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 200),
                    0, overlayH * 0.5f, new Color(255, 255, 255, 0)));
            g2.fill(overlay);
            g2.setComposite(oldComposite);

            g2.setColor(new Color(255, 255, 255, Math.round(130 * te)));
            g2.drawRoundRect(2, 2, w - 5, overlayH - 2, arc - 2, arc - 2);
            g2.setColor(new Color(0, 0, 0, Math.round(50 * te)));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(1, 1, w - 3, overlayH, arc, arc);

            g2.setStroke(oldStroke);
        }

        AffineTransform oldTransform = g2.getTransform();
        g2.translate(0, lift);
        String resPath = ImageIconRenderer.mapIdToResource(iconId);
        if (resPath != null) {
            ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconSize, this, true);
        }

        // Caption (only when hovered and enabled)
        if (enabled && te > 0.01f) {
            Color accent = resolveAccent();
            Color captionColor = AccentColorUtil.blend(getForeground(), accent, 0.25f * te);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.2f + 0.8f * te));
            g2.setColor(captionColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW;
            int textAscent;
            if (cachedCaptionFont != getFont() || cachedCaptionWidth < 0) {
                cachedCaptionFont = getFont();
                cachedCaptionWidth = fm.stringWidth(caption);
                cachedCaptionAscent = fm.getAscent();
            }
            textW = cachedCaptionWidth;
            textAscent = cachedCaptionAscent;
            int textX = (w - textW) / 2;
            int textY = iconY + iconSize + textAscent + 4;
            g2.drawString(caption, textX, textY);
            g2.setComposite(AlphaComposite.SrcOver);
        }
        g2.setTransform(oldTransform);

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
            hoverV = 0f;
            hoverLastNs = 0L;
            stopHoverTimer();
            repaint();
            return;
        }
        hoverTarget = hover;
        if (hoverTimer == null) {
            hoverLastNs = 0L;
            hoverTimer = new Timer(AppPerf.getAnimationDelay(), e -> animateHover());
            hoverTimer.start();
        }
    }

    private void animateHover() {
        float target = hoverTarget ? 1f : 0f;
        long now = System.nanoTime();
        if (hoverLastNs == 0L) hoverLastNs = now;
        float dt = (now - hoverLastNs) / 1_000_000_000f;
        hoverLastNs = now;
        dt = Math.max(0f, Math.min(0.05f, dt));

        // Match notebook hover timing for a uniform feel.
        float smoothTime = 0.18f;
        float omega = 2f / smoothTime;
        float x = omega * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = hoverT - target;
        float temp = (hoverV + omega * change) * dt;
        hoverV = (hoverV - omega * temp) * exp;
        hoverT = target + (change + temp) * exp;
        hoverT = Math.max(0f, Math.min(1f, hoverT));

        if (Math.abs(hoverT - target) < 0.001f && Math.abs(hoverV) < 0.001f) {
            hoverT = target;
            hoverV = 0f;
            stopHoverTimer();
        }
        repaint();
    }

    private void stopHoverTimer() {
        if (hoverTimer != null) {
            hoverTimer.stop();
            hoverTimer = null;
        }
        hoverLastNs = 0L;
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

    private static float smoothStep(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * (3f - 2f * clamped);
    }

}
