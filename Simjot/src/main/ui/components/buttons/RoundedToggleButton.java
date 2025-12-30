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

import java.awt.*;
import javax.swing.*;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.theme.Theme;

public class RoundedToggleButton extends JToggleButton {

    public RoundedToggleButton(String text) {
        super(text);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(AeroTheme.defaultBoldFont(12f));
        setPreferredSize(new Dimension(140, 32));
        setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        ButtonModel m = getModel();
        boolean selected = m.isSelected();
        boolean pressed = m.isPressed();
        boolean hover = m.isRollover();

        int arc = 12;
        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());

        if (Theme.isPlainWhite()) {
            Color fill = pressed ? new Color(220, 220, 220)
                    : (hover || selected ? new Color(235, 235, 235) : new Color(245, 245, 245));
            g2.setColor(fill);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        } else {
            Color top;
            Color bottom;
            if (pressed) {
                top = AeroTheme.BUTTON_PRESS_TOP;
                bottom = AeroTheme.BUTTON_PRESS_BOTTOM;
            } else if (selected) {
                top = AeroTheme.BUTTON_HOVER_TOP;
                bottom = AeroTheme.BUTTON_HOVER_BOTTOM;
            } else if (hover) {
                top = AeroTheme.BUTTON_HOVER_TOP;
                bottom = AeroTheme.BUTTON_HOVER_BOTTOM;
            } else {
                top = AeroTheme.BUTTON_BG_TOP;
                bottom = AeroTheme.BUTTON_BG_BOTTOM;
            }
            if (hover && !pressed) {
                Color glow = new Color(90, 150, 220, 180);
                AeroPainters.paintOuterGlow(g2, r, arc, glow, 6, 40);
            }
            AeroPainters.paintVerticalGradient(g2, r, top, bottom, arc);
            AeroPainters.paintGlassOverlay(g2, r, arc);
            AeroPainters.paintInnerStroke(g2, r, arc, new Color(255, 255, 255, 70));
            if (pressed) {
                AeroPainters.paintInnerShadow(g2, new Rectangle(r.x + 1, r.y + 1, r.width - 2, r.height - 2), arc - 2,
                        new Color(0, 0, 0), 4, 60);
            }
        }

        // Soft border
        g2.setColor(new Color(0, 0, 0, 60));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

        // Draw centered text
        g2.setColor(getForeground()); // should be AeroTheme.TEXT_PRIMARY
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, ((getHeight() - fm.getHeight()) / 2) + fm.getAscent());
        g2.dispose();
    }
}
