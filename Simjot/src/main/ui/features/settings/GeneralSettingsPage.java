package main.ui.features.settings;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;

class GeneralSettingsPage extends JPanel implements SettingsPage {
    private final JSpinner journalFont;
    private final JSpinner poemFont;
    private final JSpinner autosaveSpin;
    private final JComboBox<String> dateFormatBox;
    private final JCheckBox openLastChk;
    private final JCheckBox spellChk;
    private final JCheckBox journalAutocorrectChk;
    private final JCheckBox poetryAutocorrectChk;
    private final JCheckBox autosaveOnBlurChk;
    

    GeneralSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Header
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; add(SettingsUi.header("General", "Core preferences"), gc);
        gc.gridwidth = 1;

        SettingsStore store = SettingsStore.get();
        journalFont = new JSpinner(new SpinnerNumberModel(store.getJournalFontSize(), 8, 72, 1));
        journalFont.setUI(new ModernSpinnerUI());

        poemFont = new JSpinner(new SpinnerNumberModel(store.getPoemFontSize(), 8, 72, 1));
        poemFont.setUI(new ModernSpinnerUI());

        gc.gridx = 0; gc.gridy = 1; add(SettingsUi.label("Journal font size:"), gc);
        gc.gridx = 1; add(journalFont, gc);

        gc.gridx = 0; gc.gridy = 2; add(SettingsUi.label("Poem font size:"), gc);
        gc.gridx = 1; add(poemFont, gc);

        double delaySeconds = SettingsStore.get().getAutosaveDelayMs() / 1000.0;
        // Clamp within model bounds to avoid SpinnerNumberModel IAE on init
        double minS = 0.0;
        double maxS = 600.0; // up to 10 minutes
        double initS = Math.max(minS, Math.min(maxS, delaySeconds));
        autosaveSpin = new JSpinner(new SpinnerNumberModel(initS, minS, maxS, 0.5));
        autosaveSpin.setUI(new ModernSpinnerUI());
        ((JSpinner.NumberEditor) autosaveSpin.getEditor()).getTextField().setColumns(4);
        gc.gridx = 0; gc.gridy = 3; add(SettingsUi.label("Autosave delay (s):"), gc);
        gc.gridx = 1; add(autosaveSpin, gc);

        gc.gridwidth = 1;
        String[] datePatterns = main.infrastructure.io.DateFormatUtil.getCommonPatterns();
        dateFormatBox = new JComboBox<>(datePatterns);
        dateFormatBox.setUI(new ModernComboBoxUI());
        dateFormatBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        dateFormatBox.setSelectedItem(store.getDateFormat());
        gc.gridx = 0; gc.gridy = 4; add(SettingsUi.label("Date format:"), gc);
        gc.gridx = 1; add(dateFormatBox, gc);

        openLastChk = new JCheckBox("Open last note on startup", store.isOpenLastOnStartup());
        openLastChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        openLastChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2; add(openLastChk, gc);

        spellChk = new JCheckBox("Enable spell check", store.isSpellCheckEnabled());
        spellChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        spellChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2; add(spellChk, gc);

        journalAutocorrectChk = new JCheckBox("Enable autocorrect in journal editor", store.isJournalAutocorrectEnabled());
        journalAutocorrectChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        journalAutocorrectChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2; add(journalAutocorrectChk, gc);

        poetryAutocorrectChk = new JCheckBox("Enable autocorrect in poetry editor", store.isPoetryAutocorrectEnabled());
        poetryAutocorrectChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        poetryAutocorrectChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 8; gc.gridwidth = 2; add(poetryAutocorrectChk, gc);

        autosaveOnBlurChk = new JCheckBox("Autosave on focus loss", store.isAutosaveOnFocusLoss());
        autosaveOnBlurChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        autosaveOnBlurChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = 9; gc.gridwidth = 2; add(autosaveOnBlurChk, gc);

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

        store.setDateFormat((String) dateFormatBox.getSelectedItem());
        store.setOpenLastOnStartup(openLastChk.isSelected());
        store.setSpellCheckEnabled(spellChk.isSelected());
        store.setJournalAutocorrectEnabled(journalAutocorrectChk.isSelected());
        store.setPoetryAutocorrectEnabled(poetryAutocorrectChk.isSelected());
        store.setAutosaveOnFocusLoss(autosaveOnBlurChk.isSelected());
    }
}
