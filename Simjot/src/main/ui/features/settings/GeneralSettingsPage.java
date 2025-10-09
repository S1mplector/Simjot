package main.ui.features.settings;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.scaling.UIScalingManager;

class GeneralSettingsPage extends JPanel implements SettingsPage {
    private final JSpinner journalFont;
    private final JSpinner poemFont;
    private final JSpinner autosaveSpin;
    private final JSpinner uiScaleSpinner;
    private javax.swing.Timer liveScaleDebounce;
    private final JComboBox<String> dateFormatBox;
    private final JCheckBox openLastChk;
    private final JCheckBox spellChk;
    private final JCheckBox autosaveOnBlurChk;
    

    GeneralSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        SettingsStore store = SettingsStore.get();
        journalFont = new JSpinner(new SpinnerNumberModel(store.getJournalFontSize(), 8, 72, 1));
        journalFont.setUI(new ModernSpinnerUI());

        poemFont = new JSpinner(new SpinnerNumberModel(store.getPoemFontSize(), 8, 72, 1));
        poemFont.setUI(new ModernSpinnerUI());

        gc.gridx = 0; gc.gridy = 0; add(SettingsUi.label("Journal font size:"), gc);
        gc.gridx = 1; add(journalFont, gc);

        gc.gridx = 0; gc.gridy = 1; add(SettingsUi.label("Poem font size:"), gc);
        gc.gridx = 1; add(poemFont, gc);

        double delaySeconds = SettingsStore.get().getAutosaveDelayMs() / 1000.0;
        // Clamp within model bounds to avoid SpinnerNumberModel IAE on init
        double minS = 0.0;
        double maxS = 600.0; // up to 10 minutes
        double initS = Math.max(minS, Math.min(maxS, delaySeconds));
        autosaveSpin = new JSpinner(new SpinnerNumberModel(initS, minS, maxS, 0.5));
        autosaveSpin.setUI(new ModernSpinnerUI());
        ((JSpinner.NumberEditor) autosaveSpin.getEditor()).getTextField().setColumns(4);
        gc.gridx = 0; gc.gridy = 2; add(SettingsUi.label("Autosave delay (s):"), gc);
        gc.gridx = 1; add(autosaveSpin, gc);

        SpinnerNumberModel uiScaleModel = new SpinnerNumberModel(store.getUIScale(), 0.5, 3.0, 0.25);
        uiScaleSpinner = new JSpinner(uiScaleModel);
        uiScaleSpinner.setUI(new ModernSpinnerUI());
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(uiScaleSpinner, "0.00");
        uiScaleSpinner.setEditor(editor);
        // Ensure the NumberEditor matches our ModernSpinnerUI styling (avoid inner beveled frame look)
        JFormattedTextField uiScaleTf = editor.getTextField();
        uiScaleTf.setOpaque(false);
        uiScaleTf.setBackground(Color.WHITE);
        uiScaleTf.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        uiScaleTf.setForeground(Color.DARK_GRAY);
        gc.gridx = 0; gc.gridy = 3; add(SettingsUi.label("UI Scale:"), gc);
        gc.gridx = 1; add(uiScaleSpinner, gc);

        // Live preview of scaling (debounced) without persisting until Save
        liveScaleDebounce = new javax.swing.Timer(150, e -> {
            float liveScale = ((Number) uiScaleSpinner.getValue()).floatValue();
            try {
                UIScalingManager.updateScale(liveScale);
                JournalApp.globalJournalFontSize = Math.round(14 * liveScale);
            } catch (Throwable ignored) {}
        });
        liveScaleDebounce.setRepeats(false);
        uiScaleSpinner.addChangeListener(ev -> {
            liveScaleDebounce.restart();
        });

        JLabel noteLabel = new JLabel("<html><i>UI scale changes apply immediately. Some screens may briefly reflow.<br>This setting helps with high-DPI displays (e.g., use 2.0 for 200% scaling).</i></html>");
        noteLabel.setForeground(Color.GRAY);
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 2;
        add(noteLabel, gc);

        gc.gridwidth = 1;
        String[] datePatterns = main.infrastructure.io.DateFormatUtil.getCommonPatterns();
        dateFormatBox = new JComboBox<>(datePatterns);
        dateFormatBox.setUI(new ModernComboBoxUI());
        dateFormatBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        dateFormatBox.setSelectedItem(store.getDateFormat());
        gc.gridx = 0; gc.gridy = 5; add(SettingsUi.label("Date format:"), gc);
        gc.gridx = 1; add(dateFormatBox, gc);

        openLastChk = new JCheckBox("Open last note on startup", store.isOpenLastOnStartup());
        openLastChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        openLastChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2; add(openLastChk, gc);

        spellChk = new JCheckBox("Enable spell check", store.isSpellCheckEnabled());
        spellChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        spellChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2; add(spellChk, gc);

        autosaveOnBlurChk = new JCheckBox("Autosave on focus loss", store.isAutosaveOnFocusLoss());
        autosaveOnBlurChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        autosaveOnBlurChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 8; gc.gridwidth = 2; add(autosaveOnBlurChk, gc);

        // Backup settings moved to Storage section
    }

    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore store = SettingsStore.get();
        int jf = (Integer) journalFont.getValue();
        store.setJournalFontSize(jf);
        JournalApp.globalJournalFontSize = jf;

        int pf = (Integer) poemFont.getValue();
        store.setPoemFontSize(pf);

        double autosaveSec = ((Number) autosaveSpin.getValue()).doubleValue();
        store.setAutosaveDelayMs((int)Math.round(autosaveSec * 1000));

        float uiScale = ((Number) uiScaleSpinner.getValue()).floatValue();
        store.setUIScale(uiScale);
        try {
            // Apply live scaling without restart
            UIScalingManager.updateScale(uiScale);
            // Keep app-wide journal font in sync with the effective scale
            JournalApp.globalJournalFontSize = Math.round(14 * uiScale);
        } catch (Throwable t) {
            // Non-fatal; user scale will still be used on next launch
            System.err.println("[Settings] Live UI scale update failed: " + t.getMessage());
        }

        store.setDateFormat((String) dateFormatBox.getSelectedItem());
        store.setOpenLastOnStartup(openLastChk.isSelected());
        store.setSpellCheckEnabled(spellChk.isSelected());
        store.setAutosaveOnFocusLoss(autosaveOnBlurChk.isSelected());
    }
}
