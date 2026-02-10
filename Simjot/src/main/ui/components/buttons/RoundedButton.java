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
import main.ui.theme.aero.AeroTheme;
import main.ui.components.icons.ImageIconRenderer;

public class RoundedButton extends JButton {
    private String iconId = null;
    private String iconResourcePath = null;

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

    /** Configure an icon (via ImageIconRenderer id) to display left of the text. */
    public RoundedButton withIcon(String iconId) {
        this.iconId = iconId == null ? null : iconId.toLowerCase();
        this.iconResourcePath = this.iconId == null ? null : ImageIconRenderer.mapIdToResource(this.iconId);
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Solid fill based on state (flat style)
        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        Color fill = pressed
                ? new Color(220, 220, 220)
                : (hover ? new Color(235, 235, 235) : new Color(245, 245, 245));
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);

        // Draw icon (optional) and text
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();

        String text = getText() == null ? "" : getText();
        String iconKey = iconId;
        if (iconKey == null) {
            Object legacy = getClientProperty("iconId");
            if (legacy instanceof String legacyId) {
                iconKey = legacyId.toLowerCase();
            }
        }
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
        } else if (iconKey != null && !iconKey.isBlank()) {
            int target = Math.max(14, getHeight() - 12);
            if (this.iconId != null && this.iconId.equals(iconKey)) {
                iconPath = iconResourcePath;
            } else {
                iconPath = ImageIconRenderer.mapIdToResource(iconKey);
            }
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
