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
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;

/**
 * Compact help button that renders a centered question mark emoji.
 */
public class QuestionMarkButton extends JButton {
    private boolean hovering = false;

    public QuestionMarkButton() {
        setText("❓");
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        Dimension d = new Dimension(46, 46);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(new Dimension(70, 46));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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

        String text = getText();
        if (text != null && !text.isEmpty()) {
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(text);
            int textX = (w - textW) / 2;
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, textX, textY);
        }

        g2.dispose();
    }
}
