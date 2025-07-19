package main.ui.components;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;

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
        g2.setColor(comboBox.isPopupVisible() ? new Color(220, 220, 220) : new Color(240, 240, 240));
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 10, 10);
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
        if (comboBox.getSelectedItem() != null) {
            g2.setColor(c.getForeground());
            g2.setFont(c.getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(comboBox.getSelectedItem().toString(), 8, fm.getAscent() + (c.getHeight() - fm.getHeight()) / 2);
        }
        g2.setColor(Color.DARK_GRAY);
        int x = c.getWidth() - 18, y = c.getHeight() / 2 - 3;
        g2.fillPolygon(new int[]{x, x + 6, x + 12}, new int[]{y, y + 6, y}, 3);
        g2.dispose();
    }

    // Renderer for drop-down list
    public static class ModernComboBoxRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setBackground(isSelected ? new Color(0,120,215,60) : Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
            return this;
        }
    }
} 