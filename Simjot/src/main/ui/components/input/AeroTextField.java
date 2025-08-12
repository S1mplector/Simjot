package main.ui.components.input;

import main.ui.theme.aero.AeroTheme;

import javax.swing.*;
import java.awt.*;

/**
 * Aero-styled rounded text field suitable for search bars and inputs in toolbars.
 */
public class AeroTextField extends JTextField {
    public AeroTextField(int columns) {
        super(columns);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setFont(AeroTheme.defaultFont());
        setForeground(AeroTheme.TEXT_PRIMARY);
        setCaretColor(AeroTheme.AERO_BLUE_DARK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background: subtle Aero glass-like gradient
        int w = getWidth();
        int h = getHeight();
        GradientPaint gp = new GradientPaint(0, 0, new Color(252, 252, 252), 0, h, new Color(235, 235, 235));
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, w, h, 10, 10);

        // Top gloss highlight
        g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 180), 0, h / 2f, new Color(255, 255, 255, 0)));
        g2.fillRoundRect(1, 1, w - 2, h / 2, 9, 9);

        super.paintComponent(g2);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // Outer border
        g2.setColor(new Color(190, 190, 190));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

        // Focus glow
        if (isFocusOwner()) {
            g2.setColor(new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), 120));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        }
        g2.dispose();
    }
}
