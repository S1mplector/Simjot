/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.buttons;

import java.awt.*;
import javax.swing.*;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;

public class RoundedButton extends JButton {
    private boolean flat = false; // if true, paint solid fill without gradients

    public RoundedButton(String text){
        super(text);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false); // prevent background clears that cause flicker with custom painting
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(AeroTheme.defaultBoldFont(12f));
        setPreferredSize(new Dimension(140,32));
    }

    /** Enable or disable flat painting (no gradients/glass overlay). */
    public void setFlat(boolean flat) {
        this.flat = flat;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Determine gradient by state
        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        if (flat || Theme.isPlainWhite()) {
            // Solid fill based on state
            Color fill = pressed
                    ? new Color(220, 220, 220)
                    : (hover ? new Color(235, 235, 235) : new Color(245, 245, 245));
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        } else {
            Color top = pressed ? AeroTheme.BUTTON_PRESS_TOP : (hover ? AeroTheme.BUTTON_HOVER_TOP : AeroTheme.BUTTON_BG_TOP);
            Color bottom = pressed ? AeroTheme.BUTTON_PRESS_BOTTOM : (hover ? AeroTheme.BUTTON_HOVER_BOTTOM : AeroTheme.BUTTON_BG_BOTTOM);
            Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
            AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
            AeroPainters.paintGlassOverlay(g2, r, 10);
        }

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);

        // Draw icon (optional) and text
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();

        String text = getText() == null ? "" : getText();
        String iconId = (String) getClientProperty("iconId");
        int gap = 6;
        int paddingX = 10;
        int contentW = fm.stringWidth(text);
        int iconW = 0;
        int iconH = 0;
        String iconPath = null;
        int iconSize = 0;
        Icon swingIcon = getIcon();

        // Prefer a standard Swing icon if one is set via setIcon(...)
        if (swingIcon != null) {
            iconW = swingIcon.getIconWidth();
            iconH = swingIcon.getIconHeight();
        } else if (iconId != null && !iconId.isBlank()) {
            int target = Math.max(14, getHeight() - 12);
            iconPath = ImageIconRenderer.mapIdToResource(iconId);
            if (iconPath != null) {
                iconSize = target;
                iconW = target;
                iconH = target;
            }
        }
        if (iconW > 0 && iconH > 0) {
            contentW += (gap + iconW);
        }
        int xStart = (getWidth() - contentW) / 2;
        // Ensure minimum left padding so icon/text don't hug the border
        if (xStart < paddingX) xStart = paddingX;

        int centerY = (getHeight() - fm.getHeight())/2 + fm.getAscent();
        int drawX = xStart;
        if (iconW > 0 && iconH > 0) {
            int iy = (getHeight() - iconH) / 2;
            if (swingIcon != null) {
                swingIcon.paintIcon(this, g2, drawX, iy);
            } else if (iconPath != null) {
                ImageIconRenderer.draw(g2, iconPath, drawX, iy, iconSize, this, true);
            }
            drawX += iconW + gap;
        }
        g2.drawString(text, drawX, centerY);
        g2.dispose();
    }
}
