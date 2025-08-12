package main.ui.features.settings;

import javax.swing.JComponent;

public interface SettingsPage {
    JComponent getComponent();
    void apply();
}
