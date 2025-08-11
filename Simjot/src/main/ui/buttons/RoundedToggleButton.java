package main.ui.buttons;

import java.awt.*;
import javax.swing.*;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

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

        ButtonModel m = getModel();
        boolean selected = m.isSelected();
        boolean pressed = m.isPressed();
        boolean hover = m.isRollover();

        // Choose gradient based on state; use hover/press palette for selected as a distinct look
        Color top;
        Color bottom;
        if (pressed) {
            top = AeroTheme.BUTTON_PRESS_TOP;
            bottom = AeroTheme.BUTTON_PRESS_BOTTOM;
        } else if (selected) {
            // Selected state uses hover gradient to stand out
            top = AeroTheme.BUTTON_HOVER_TOP;
            bottom = AeroTheme.BUTTON_HOVER_BOTTOM;
        } else if (hover) {
            top = AeroTheme.BUTTON_HOVER_TOP;
            bottom = AeroTheme.BUTTON_HOVER_BOTTOM;
        } else {
            top = AeroTheme.BUTTON_BG_TOP;
            bottom = AeroTheme.BUTTON_BG_BOTTOM;
        }

        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
        AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
        AeroPainters.paintGlassOverlay(g2, r, 10);

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

        // Draw centered text
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, ((getHeight() - fm.getHeight()) / 2) + fm.getAscent());
        g2.dispose();
    }
}
