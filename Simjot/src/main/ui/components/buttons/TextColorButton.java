/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.buttons;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;

/**
 * Text color button showing an "A" in Bradley Hand font,
 * colored with the currently selected text color.
 * 
 * @author S1mplector
 */
public class TextColorButton extends JButton {
    private static final Font HAND_FONT = main.ui.theme.Theme.resolveHandFont(Font.BOLD, 20);
    private Color textColor = Color.BLACK;

    public TextColorButton() {
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setFont(resolveFont());
        Dimension d = new Dimension(48, 36);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(new Dimension(70, 40));
        setToolTipText("Text Color");
    }

    private Font resolveFont() {
        if (HAND_FONT.getFamily().equalsIgnoreCase("dialog")) {
            return getFont().deriveFont(Font.BOLD, 20f);
        }
        return HAND_FONT;
    }

    public void setTextColor(Color color) {
        this.textColor = color != null ? color : Color.BLACK;
        repaint();
    }

    public Color getTextColor() {
        return textColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int w = getWidth(), h = getHeight();
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        boolean hover = getModel().isRollover();

        // Background plate
        Color top = new Color(247, 248, 250, 225);
        Color bot = new Color(229, 232, 238, 220);
        if (pressed) {
            top = top.darker();
            bot = bot.darker();
        }
        RoundRectangle2D plate = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10);
        g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
        g2.fill(plate);

        // Glass highlight
        g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 140), 0, h / 2f, new Color(255, 255, 255, 20)));
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f + 4, 9, 9));

        // Border
        g2.setColor(new Color(170, 175, 185, 200));
        g2.draw(plate);

        // Hover overlay
        if (hover) {
            g2.setColor(new Color(255, 180, 90, 70));
            g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, 9, 9));
        }

        // Draw "A" in the current text color
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String label = "A";
        int textW = fm.stringWidth(label);
        int textX = (w - textW) / 2;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2 - 2;
        
        // Draw colored "A"
        g2.setColor(textColor);
        g2.drawString(label, textX, textY);
        
        // Draw color bar underneath the "A"
        int barY = textY + 2;
        int barH = 3;
        g2.setColor(textColor);
        g2.fillRoundRect(textX - 2, barY, textW + 4, barH, 2, 2);

        g2.dispose();
    }
}
