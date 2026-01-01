/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.animations.transitions;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

public class FadingButton extends JButton {
    private static final long serialVersionUID = 1L;
	// Set alpha to full opacity by default.
    private float alpha = 1f;
    private static boolean glowEnabled = main.core.service.SettingsStore.get().isGlowEnabled();
    private static boolean animationsDisabled = main.core.service.SettingsStore.get().isAnimationsDisabled();

    public static void setGlowEnabled(boolean enabled){ glowEnabled = enabled; }
    public static boolean isGlowEnabled(){ return glowEnabled; }

    public FadingButton(String text) {
        super(text);
        setFocusPainted(false);
        setOpaque(false);            // We'll paint the background manually.
        setContentAreaFilled(false); // Disable default background painting.
        // Set a transparent dark gray color.
        // Here, (64, 64, 64) is dark gray and 128 is roughly 50% transparent.
        setBackground(new Color(64, 64, 64, 128));
        setForeground(Color.WHITE);
        // Remove any default border if desired.
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }
    
    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
        repaint();
    }
    
    public float getAlpha() {
        return alpha;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Enable antialiasing for smooth corners.
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Apply the alpha composite.
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        // Create a rounded rectangle shape. Adjust arc dimensions (15,15) for desired roundness.
        Shape roundRect = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15);
        g2.setColor(getBackground());
        g2.fill(roundRect);
        
        // Hover glow when enabled
        if(glowEnabled && getModel().isRollover() && !animationsDisabled){
            int layers=3;
            for(int i=layers;i>=1;i--){
                float a=0.12f*i;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,a));
                int glow=2+(layers-i)*2;
                Shape glowRect=new RoundRectangle2D.Float(-glow,-glow,getWidth()+glow*2,getHeight()+glow*2,18+glow,18+glow);
                g2.setColor(Color.WHITE);
                g2.fill(glowRect);
            }
            g2.setComposite(AlphaComposite.SrcOver);
        }
        
        // Optionally, draw a border around the button (uncomment if desired)
        // g2.setColor(getForeground());
        // g2.draw(roundRect);
        
        // Let the superclass paint the text and icon
        super.paintComponent(g2);
        g2.dispose();
    }
    
    @Override
    public boolean contains(int x, int y) {
        Shape roundRect = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15);
        return roundRect.contains(x, y);
    }
}
