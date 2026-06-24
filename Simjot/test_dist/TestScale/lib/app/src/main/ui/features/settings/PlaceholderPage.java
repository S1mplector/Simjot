/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import main.ui.theme.aero.AeroTheme;

public class PlaceholderPage extends JPanel implements SettingsPage {
    public PlaceholderPage(String msg) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        JLabel lab = new JLabel("<html><div style='text-align:center'>" + msg + "</div></html>", SwingConstants.CENTER);
        lab.setForeground(AeroTheme.TEXT_PRIMARY);
        lab.setFont(AeroTheme.defaultFont());
        add(lab, BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public void apply() {}
}
