package main.ui.features.settings;

import javax.swing.JLabel;
import main.ui.theme.aero.AeroTheme;

final class SettingsUi {
    private SettingsUi() {}

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        return l;
    }
}
