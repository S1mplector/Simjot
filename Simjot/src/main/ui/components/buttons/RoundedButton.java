package main.ui.components.buttons;

import java.awt.*;
import javax.swing.*;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.components.icons.ImageIconRenderer;

public class RoundedButton extends JButton {

    public RoundedButton(String text){
        super(text);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(AeroTheme.defaultBoldFont(12f));
        setPreferredSize(new Dimension(140,32));
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Determine gradient by state
        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        Color top = pressed ? AeroTheme.BUTTON_PRESS_TOP : (hover ? AeroTheme.BUTTON_HOVER_TOP : AeroTheme.BUTTON_BG_TOP);
        Color bottom = pressed ? AeroTheme.BUTTON_PRESS_BOTTOM : (hover ? AeroTheme.BUTTON_HOVER_BOTTOM : AeroTheme.BUTTON_BG_BOTTOM);

        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
        AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
        AeroPainters.paintGlassOverlay(g2, r, 10);

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);

        // Draw icon (optional) and text
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();

        String text = getText() == null ? "" : getText();
        String iconId = (String) getClientProperty("iconId");
        int gap = 6;
        int paddingX = 10;
        int contentW = fm.stringWidth(text);
        int iconW = 0;
        int iconH = 0;
        java.awt.image.BufferedImage iconImg = null;
        Icon swingIcon = getIcon();

        // Prefer a standard Swing icon if one is set via setIcon(...)
        if (swingIcon != null) {
            iconW = swingIcon.getIconWidth();
            iconH = swingIcon.getIconHeight();
        } else if (iconId != null && !iconId.isBlank()) {
            int target = Math.max(14, getHeight() - 12);
            String path = ImageIconRenderer.mapIdToResource(iconId);
            if (path != null) {
                iconImg = ImageIconRenderer.get(path, target, true);
                if (iconImg != null) { iconW = iconImg.getWidth(); iconH = iconImg.getHeight(); }
            }
        }
        if (iconW > 0 && iconH > 0) {
            contentW += (gap + iconW);
        }
        int xStart = (getWidth() - contentW) / 2;
        // Ensure minimum left padding so icon/text don't hug the border
        if (xStart < paddingX) xStart = paddingX;

        int centerY = (getHeight() - fm.getHeight())/2 + fm.getAscent();
        int drawX = xStart;
        if (iconW > 0 && iconH > 0) {
            int iy = (getHeight() - iconH) / 2;
            if (swingIcon != null) {
                swingIcon.paintIcon(this, g2, drawX, iy);
            } else if (iconImg != null) {
                g2.drawImage(iconImg, drawX, iy, null);
            }
            drawX += iconW + gap;
        }
        g2.drawString(text, drawX, centerY);
        g2.dispose();
    }
}