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

package main.ui.components.input;

import main.ui.theme.aero.AeroTheme;
import main.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Aero-styled rounded password field matching AeroTextField visuals.
 */
public class AeroPasswordField extends JPasswordField {
    public AeroPasswordField(int columns) {
        super(columns);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setFont(AeroTheme.defaultFont());
        setForeground(AeroTheme.TEXT_PRIMARY);
        setCaretColor(AeroTheme.AERO_BLUE_DARK);
        setEchoChar('•');
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        if (Theme.isPlainWhite()) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, new Color(252, 252, 252), 0, h, new Color(235, 235, 235));
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 180), 0, h / 2f, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(1, 1, w - 2, h / 2, 9, 9);
        }

        super.paintComponent(g2);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(190, 190, 190));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

        if (!Theme.isPlainWhite() && isFocusOwner()) {
            g2.setColor(new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), 120));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        }
        g2.dispose();
    }
}
