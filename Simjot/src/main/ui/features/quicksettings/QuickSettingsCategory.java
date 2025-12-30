/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
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
