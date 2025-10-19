package main.ui.features.settings;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import main.core.service.SettingsStore;
import main.infrastructure.monitoring.AppPerf;
import main.ui.animations.transitions.FadingButton;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.features.gallery.WallpaperGalleryPanel;

class AppearanceSettingsPage extends JPanel implements SettingsPage {
    private final RoundedButton backgroundOptionsBtn;
    private final JComboBox<String> themeBox;
    private final JCheckBox glowChk;
    private final JCheckBox disableAnimationsChk;
    private final JCheckBox disableMainMenuAnimationsChk;
    private final JCheckBox lowPowerChk;

    AppearanceSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Header
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; add(SettingsUi.header("Appearance", "Theme and visuals"), gc);
        gc.gridwidth = 1;

        SettingsStore store = SettingsStore.get();
        String[] themes = {"Light", "Dark"};
        themeBox = new JComboBox<>(themes);
        themeBox.setUI(new ModernComboBoxUI());
        themeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        themeBox.setSelectedItem(store.getTheme());

        glowChk = new JCheckBox("Enable button glow", store.isGlowEnabled());
        glowChk.setUI(new ModernCheckBoxUI());
        glowChk.setBackground(new Color(0, 0, 0, 0));

        disableAnimationsChk = new JCheckBox("Disable transition animations", store.isAnimationsDisabled());
        disableAnimationsChk.setUI(new ModernCheckBoxUI());
        disableAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        disableMainMenuAnimationsChk = new JCheckBox("Disable main menu animations", store.isMainMenuAnimationsDisabled());
        disableMainMenuAnimationsChk.setUI(new ModernCheckBoxUI());
        disableMainMenuAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        lowPowerChk = new JCheckBox("Low Power Mode (battery saver)", store.isLowPowerMode());
        lowPowerChk.setUI(new ModernCheckBoxUI());
        lowPowerChk.setBackground(new Color(0, 0, 0, 0));

        backgroundOptionsBtn = new RoundedButton("Background Options");
        backgroundOptionsBtn.addActionListener(e -> openBackgroundOptions());

        gc.gridx = 0; gc.gridy = 1; add(SettingsUi.label("Background:"), gc);
        gc.gridx = 1; add(backgroundOptionsBtn, gc);
        gc.gridx = 0; gc.gridy = 2; add(SettingsUi.label("Theme:"), gc);
        gc.gridx = 1; add(themeBox, gc);
        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 2; add(glowChk, gc);
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 2; add(disableAnimationsChk, gc);
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2; add(disableMainMenuAnimationsChk, gc);
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2; add(lowPowerChk, gc);
    }

    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore store = SettingsStore.get();
        String theme = (String) themeBox.getSelectedItem();
        store.setTheme(theme);
        // TODO: apply theme live

        boolean glow = glowChk.isSelected();
        store.setGlowEnabled(glow);
        FadingButton.setGlowEnabled(glow);
        main.ui.components.buttons.ToolbarIconButton.setGlowEnabled(glow);

        store.setAnimationsDisabled(disableAnimationsChk.isSelected());

        store.setMainMenuAnimationsDisabled(disableMainMenuAnimationsChk.isSelected());

        boolean lp = lowPowerChk.isSelected();
        store.setLowPowerMode(lp);
        AppPerf.setLowPowerMode(lp);
    }

    private void openBackgroundOptions() {
        WallpaperGalleryPanel.showWallpaperGallery(this);
    }
}
