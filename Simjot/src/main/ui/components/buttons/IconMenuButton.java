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
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();

        boolean enabled = isEnabled();
        
        // When disabled, apply transparency to the whole component
        if (!enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        }

        float t = hoverT;

        // Icon
        int iconSize = Math.min(w - 18, 56);
        int iconX = (w - iconSize) / 2;
        int lift = (int) Math.round(5 * (1f - t));
        int iconY = 14 + lift; // nudge down a bit when idle
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

        // Asymmetric timing: faster fade-in, gentler fade-out
        float smoothTime = hoverTarget ? 0.18f : 0.32f;
        float omega = 2f / smoothTime;
        float x = omega * dt;
        float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = hoverT - target;
        float temp = (hoverV + omega * change) * dt;
        hoverV = (hoverV - omega * temp) * exp;
        hoverT = target + (change + temp) * exp;
        hoverT = Math.max(0f, Math.min(1f, hoverT));

        if (Math.abs(hoverT - target) < 0.0008f && Math.abs(hoverV) < 0.0008f) {
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

}
