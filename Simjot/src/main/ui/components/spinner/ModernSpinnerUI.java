/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.spinner;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSpinnerUI;

public class ModernSpinnerUI extends BasicSpinnerUI {

    public static ModernSpinnerUI createUI(JComponent c) {
        return new ModernSpinnerUI();
    }

    @Override
    protected Component createNextButton() {
        JButton button = createArrowButton(SwingConstants.NORTH);
        button.setToolTipText("Increase");
        installNextButtonListeners(button);
        return button;
    }

    @Override
    protected Component createPreviousButton() {
        JButton button = createArrowButton(SwingConstants.SOUTH);
        button.setToolTipText("Decrease");
        installPreviousButtonListeners(button);
        return button;
    }

    private JButton createArrowButton(int direction) {
        JButton button = new JButton(new ArrowIcon(direction));
        button.setFocusable(false);
        button.setOpaque(true);
        button.setBackground(new Color(245, 245, 245));
        button.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        return button;
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createLineBorder(new Color(220,220,220)));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setOpaque(false);
        editor.getTextField().setBackground(Color.WHITE);
        editor.getTextField().setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        editor.getTextField().setForeground(Color.DARK_GRAY);
    }

    private static class ArrowIcon implements Icon {
        private final int direction;

        public ArrowIcon(int direction) {
            this.direction = direction;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.DARK_GRAY);
            int w = getIconWidth();
            int h = getIconHeight();
            int[] xPoints, yPoints;
            if (direction == SwingConstants.NORTH) {
                xPoints = new int[]{x, x + w / 2, x + w};
                yPoints = new int[]{y + h, y, y + h};
            } else { // SOUTH
                xPoints = new int[]{x, x + w / 2, x + w};
                yPoints = new int[]{y, y + h, y};
            }
            g2.fillPolygon(xPoints, yPoints, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 8;
        }

        @Override
        public int getIconHeight() {
            return 5;
        }
    }
}
