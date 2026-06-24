/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.settings;

import javax.swing.JComponent;

public interface SettingsPage {
    JComponent getComponent();
    void apply();
}
