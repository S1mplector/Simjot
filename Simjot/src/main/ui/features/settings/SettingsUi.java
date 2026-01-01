/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.settings;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import java.awt.Font;
import java.awt.Color;
import main.ui.theme.aero.AeroTheme;

final class SettingsUi {
    private SettingsUi() {}

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        return l;
    }

    public static JPanel header(String title, String subtitle) {
        JLabel t = label(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD));
        JLabel s = label(subtitle);
        s.setFont(s.getFont().deriveFont(11f));
        s.setForeground(new Color(0, 0, 0, 120));
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(t);
        p.add(s);
        return p;
    }
}
