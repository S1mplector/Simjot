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

package main.ui.components.containers;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Rectangle;
import main.ui.theme.aero.AeroPainters;

public class RoundedPanel extends JPanel {
    private int arc = 20;
    private boolean flat = false; // if true, paint solid fill without gradients

    public RoundedPanel() {
        setOpaque(false);
    }

    public RoundedPanel(int arc) {
        setOpaque(false);
        this.arc = arc;
    }

    public void setArc(int arc) {
        this.arc = arc;
    }

    /** Enable or disable flat painting (no gradients/glass overlay). */
    public void setFlat(boolean flat) {
        this.flat = flat;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
        if (flat) {
            // Solid background using component background color
            Color bg = getBackground() != null ? getBackground() : new Color(255,255,255);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
        } else {
            // Aero glass: subtle vertical gradient + overlay
            AeroPainters.paintVerticalGradient(g2, r, new Color(255,255,255,210), new Color(235,235,235,200), arc);
            AeroPainters.paintGlassOverlay(g2, r, arc);
        }

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1f, getHeight()-1f, arc, arc));
        g2.dispose();
    }
}
