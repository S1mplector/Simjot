/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
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
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;

import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.aero.AeroTheme;

/**
 * Minimal icon-first menu button.
 * Draws a centered SVG/PNG/vector icon; on hover/press shows a soft overlay and reveals the caption under the icon.
 */
public class IconMenuButton extends JButton {
    private final String iconId;
    private final String caption;
    private boolean hovering = false;

    public IconMenuButton(String text, String iconId) {
        super(""); // we render text manually
        this.caption = text == null ? "" : text;
        this.iconId = iconId == null ? "" : iconId.toLowerCase();
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
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
            @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { hovering = true; repaint(); } }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
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
        if (enabled && (hovering || pressed)) {
            Color overlay = pressed ? new Color(240, 244, 249, 120) : new Color(248, 251, 255, 110);
            Shape plate = new RoundRectangle2D.Float(6, 6, w - 12, h - 26, 16, 16);
            g2.setColor(overlay);
            g2.fill(plate);
            g2.setColor(new Color(180, 190, 205, 160));
            g2.draw(plate);
        }

        // Icon
        int iconSize = Math.min(w - 18, 48);
        int iconX = (w - iconSize) / 2;
        int iconY = 12 + ((enabled && hovering) ? 0 : 4); // nudge down a bit when idle
        String resPath = ImageIconRenderer.mapIdToResource(iconId);
        if (resPath != null) {
            ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconSize, this, true);
        }

        // Caption (only when hovered and enabled)
        if (enabled && hovering) {
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(caption);
            int textX = (w - textW) / 2;
            int textY = iconY + iconSize + fm.getAscent() + 4;
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            hovering = false;
        }
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }
}
