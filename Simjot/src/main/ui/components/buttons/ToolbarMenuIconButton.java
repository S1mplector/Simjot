/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.buttons;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import main.ui.components.icons.IconTransforms;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.aero.AeroTheme;

/**
 * Compact icon-first button styled like the main menu tiles:
 * shows only the icon at rest, then reveals a soft overlay + caption on hover/press.
 */
public class ToolbarMenuIconButton extends ToolbarIconButton {
    private final String iconResourcePath;
    private final String caption;
    private boolean hovering = false;
    private double iconRotationRadians = 0.0;

    public ToolbarMenuIconButton(String caption, String iconId) {
        super(iconId);
        this.caption = caption == null ? "" : caption;
        String normalizedIconId = iconId == null ? "" : iconId.toLowerCase();
        this.iconResourcePath = ImageIconRenderer.mapIdToResource(normalizedIconId);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(new Font("SansSerif", Font.BOLD, 13));
        Dimension d = new Dimension(46, 46);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(new Dimension(70, 46));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
        });
    }

    /** Rotate the icon around its center. Use Math.PI to flip 180 degrees. */
    public void setIconRotationRadians(double radians) {
        if (Double.isNaN(radians) || Double.isInfinite(radians)) return;
        if (this.iconRotationRadians != radians) {
            this.iconRotationRadians = radians;
            repaint();
        }
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        boolean pressed = getModel().isArmed() && getModel().isPressed();
        if (hovering || pressed) {
            Shape plate = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10);
            Color top = pressed ? new Color(235, 238, 243, 220) : new Color(245, 248, 252, 210);
            Color bot = pressed ? new Color(215, 220, 230, 220) : new Color(225, 230, 238, 210);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
            g2.fill(plate);
            g2.setColor(new Color(170, 180, 195, 200));
            g2.draw(plate);
        }

        // Icon
        int iconSize = Math.min(w, h) - 12;
        int ix = (w - iconSize) / 2;
        int iy = (h - iconSize) / 2;
        String res = iconResourcePath;
        if (res != null) {
            if (Math.abs(iconRotationRadians) > 0.0001) {
                IconTransforms.drawRotated(g2, res, ix, iy, iconSize, this, true, iconRotationRadians);
            } else {
                ImageIconRenderer.draw(g2, res, ix, iy, iconSize, this, true);
            }
        }

        // Caption appears on hover/press as inline overlay
        if (hovering || pressed) {
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(caption);
            int textX = (w - textW) / 2;
            int textY = h - 6;
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawString(caption, textX + 1, textY + 1);
            g2.setColor(new Color(30, 30, 30, 190));
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }
}
