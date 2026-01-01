/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.combobox;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import main.ui.theme.aero.AeroTheme;
import main.ui.theme.Theme;

public class ModernComboBoxUI extends BasicComboBoxUI {
    @Override
    protected JButton createArrowButton() {
        return new JButton() {{ setVisible(false); }};
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        // No default background painting
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = c.getWidth();
        int h = c.getHeight();

        if (Theme.isPlainWhite()) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
        } else {
            Color top = comboBox.isPopupVisible() ? new Color(234, 241, 251) : new Color(252, 252, 252);
            Color bottom = comboBox.isPopupVisible() ? new Color(221, 236, 248) : new Color(235, 235, 235);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 180), 0, h / 2f, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(1, 1, w - 2, h / 2, 9, 9);
        }

        // Border
        g2.setColor(new Color(190, 190, 190));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

        // Focus glow
        if (!Theme.isPlainWhite()) {
            if (c.isFocusOwner() || (comboBox != null && comboBox.isPopupVisible())) {
                g2.setColor(new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), 120));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            }
        }

        // Text
        Object sel = (comboBox != null) ? comboBox.getSelectedItem() : null;
        if (sel != null) {
            g2.setColor(c.getForeground());
            g2.setFont(c.getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textY = fm.getAscent() + (h - fm.getHeight()) / 2;
            g2.drawString(sel.toString(), 8, textY);
        }

        // Arrow
        g2.setColor(new Color(80, 80, 80));
        int x = w - 18, y = h / 2 - 3;
        g2.fillPolygon(new int[]{x, x + 6, x + 12}, new int[]{y, y + 6, y}, 3);

        g2.dispose();
    }

    // Renderer for drop-down list
    public static class ModernComboBoxRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (Theme.isPlainWhite()) {
                setBackground(isSelected ? new Color(235, 235, 235) : Color.WHITE);
                setForeground(new Color(40, 40, 40));
            } else {
                setBackground(isSelected ? new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), 60) : Color.WHITE);
                setForeground(AeroTheme.TEXT_PRIMARY);
            }
            setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
            return this;
        }
    }
}
