/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.quicksettings;

import java.util.function.Supplier;
import javax.swing.*;

public class QuickSettingsCategory {
    private final String name;
    private final Icon icon; // optional, can be null
    private final Supplier<JComponent> panelSupplier;

    public QuickSettingsCategory(String name, Icon icon, Supplier<JComponent> panelSupplier) {
        this.name = name;
        this.icon = icon;
        this.panelSupplier = panelSupplier;
    }

    public String getName() { return name; }
    public Icon getIcon() { return icon; }
    public JComponent createPanel() { return panelSupplier.get(); }
}
