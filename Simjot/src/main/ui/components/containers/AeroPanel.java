/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.containers;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;

public class AeroPanel extends JPanel {
    private int arc = 10;

    public AeroPanel() {
        setOpaque(false);
    }

    public AeroPanel(int arc) {
        setOpaque(false);
        this.arc = arc;
    }

    public void setArc(int arc) { this.arc = arc; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (main.core.service.SettingsStore.get().isTransparentWindowsDisabled()) {
            g2.setColor(new Color(245, 245, 245));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null && win.getBackground().getAlpha() == 0) win.setBackground(new Color(245, 245, 245));
            return;
        }

        if (Theme.isMinimalLook()) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(218, 226, 234));
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
            return;
        }

        // Explicitly clear background to transparent to fix uninitialized VRAM artifacts on some Linux compositors
        g2.setComposite(java.awt.AlphaComposite.Clear);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(java.awt.AlphaComposite.SrcOver);

        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
        AeroPainters.paintVerticalGradient(g2, r, new Color(255,255,255,200), new Color(235,235,235,200), arc);
        AeroPainters.paintGlassOverlay(g2, r, arc);
        // Inner shadow
        g2.setColor(new Color(0,0,0,30));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1f, getHeight()-1f, arc, arc));
        g2.dispose();
    }
}
