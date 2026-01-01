/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.settings;

import javax.swing.JComponent;

public interface SettingsPage {
    JComponent getComponent();
    void apply();
}
