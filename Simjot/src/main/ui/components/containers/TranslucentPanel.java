/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.containers;

import java.awt.*;
import javax.swing.*;
import main.ui.theme.Theme;
/**
 * A custom JPanel with a translucent background.
 */
public class TranslucentPanel extends JPanel {
    public TranslucentPanel() {
        super(new BorderLayout());
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (Theme.isMinimalLook()) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            return;
        }
        // Soft white translucent background
        g2.setColor(new Color(255, 255, 255, 160));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        g2.dispose();
    }
}
