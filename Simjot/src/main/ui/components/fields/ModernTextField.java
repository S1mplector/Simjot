package main.ui.components.fields;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.ui.theme.aero.AeroTheme;

/**
 * Modern rounded JTextField with placeholder support used across editors.
 */
public class ModernTextField extends JTextField {
    private String placeholder = null;

    public ModernTextField(int columns) {
        super(columns);
        setOpaque(false);
        setBorder(new EmptyBorder(6, 10, 6, 10));
        try {
            setForeground(AeroTheme.TEXT_PRIMARY);
        } catch (Throwable ignore) {
            // Fallback if theme is unavailable during early init
            setForeground(new Color(30, 30, 30));
        }
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        super.paintComponent(g2);

        // Draw placeholder text if empty and unfocused
        if ((getText() == null || getText().isEmpty()) && !isFocusOwner() && placeholder != null) {
            g2.setFont(getFont());
            g2.setColor(new Color(130, 130, 130));
            FontMetrics fm = g2.getFontMetrics();
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, 12, textY);
        }
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        g2.dispose();
    }
}
