/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.settings;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import main.core.service.SettingsStore;
import main.ui.components.spinner.ModernSpinnerUI;

class DrawingSettingsPage extends JPanel implements SettingsPage {
    private final JSpinner brushSize;
    private final JCheckBox smoothing;
    private final JCheckBox thumbnails;

    DrawingSettingsPage() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Header
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; add(SettingsUi.header("Drawing", "Canvas and rendering"), gc);
        gc.gridwidth = 1;

        SettingsStore st = SettingsStore.get();
        brushSize = new JSpinner(new SpinnerNumberModel(st.getDefaultBrushSize(), 1, 50, 1));
        brushSize.setUI(new ModernSpinnerUI());
        smoothing = new JCheckBox("Enable stroke smoothing", st.isSmoothingEnabled());
        smoothing.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        thumbnails = new JCheckBox("Generate thumbnails on save", st.isThumbnailGeneration());
        thumbnails.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        smoothing.setBackground(new Color(0, 0, 0, 0));
        thumbnails.setBackground(new Color(0, 0, 0, 0));

        gc.gridx = 0; gc.gridy = 1; add(SettingsUi.label("Default brush size:"), gc);
        gc.gridx = 1; add(brushSize, gc);
        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; add(smoothing, gc);
        gc.gridy = 3; add(thumbnails, gc);
    }

    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore st = SettingsStore.get();
        st.setDefaultBrushSize((Integer) brushSize.getValue());
        st.setSmoothingEnabled(smoothing.isSelected());
        st.setThumbnailGeneration(thumbnails.isSelected());
    }
}
