/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.settings;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.BorderFactory;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.spinner.ModernSpinnerUI;

class GeneralSettingsPage extends JPanel implements SettingsPage {
    private final JSpinner journalFont;
    private final JSpinner poemFont;
    private final JSpinner autosaveSpin;
    private final JComboBox<String> fontFamilyBox;
    private final JComboBox<Integer> fontSizeBox;
    private final JComboBox<String> lineSpacingBox;
    private final JTextPane fontPreview;
    private final JComboBox<String> dateFormatBox;
    private final JCheckBox openLastChk;
    private final JCheckBox spellChk;
    private final JCheckBox journalAutocorrectChk;
    private final JCheckBox poetryAutocorrectChk;
    

    GeneralSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        SettingsStore store = SettingsStore.get();
        int row = 0;

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; add(SettingsUi.header("General", "Core preferences"), gc);
        row++;

        // Editor font section (moved from Appearance)
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Editor Font", "Font style for journals and poems"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridwidth = 1;

        String[] fonts = {"Serif", "Georgia", "Garamond", "Baskerville",
                "Lucida Handwriting", "Segoe Script", "Comic Sans MS", "Bradley Hand",
                "Segoe Print", "Marker Felt", "Noteworthy", "Chalkboard", "Chalkboard SE",
                "Apple Chancery", "Snell Roundhand", "Zapfino", "Brush Script MT",
                "Lucida Calligraphy", "Papyrus", "Cursive"};
        fontFamilyBox = new JComboBox<>(fonts);
        fontFamilyBox.setUI(new ModernComboBoxUI());
        fontFamilyBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontFamilyBox.setSelectedItem(store.getEditorFontFamily());
        fontFamilyBox.addActionListener(e -> updateFontPreview());
        installPopupScrollbar(fontFamilyBox);

        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 28};
        fontSizeBox = new JComboBox<>(sizes);
        fontSizeBox.setUI(new ModernComboBoxUI());
        fontSizeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontSizeBox.setSelectedItem(store.getJournalFontSize());
        fontSizeBox.addActionListener(e -> updateFontPreview());

        lineSpacingBox = new JComboBox<>(new String[]{"1.0", "1.2", "1.5"});
        lineSpacingBox.setUI(new ModernComboBoxUI());
        lineSpacingBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        lineSpacingBox.setSelectedItem(store.getEditorLineSpacing());
        lineSpacingBox.addActionListener(e -> updateFontPreview());

        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Font:"), gc);
        gc.gridx = 1; add(fontFamilyBox, gc);
        row++;

        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Default size:"), gc);
        gc.gridx = 1; add(fontSizeBox, gc);
        row++;

        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Line spacing:"), gc);
        gc.gridx = 1; add(lineSpacingBox, gc);
        row++;

        fontPreview = new JTextPane();
        fontPreview.setText("The quick brown fox jumps over the lazy dog.\nHow vexingly quick daft zebras jump!");
        fontPreview.setEditable(false);
        fontPreview.setBackground(new Color(252, 252, 250));
        fontPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        fontPreview.setPreferredSize(new Dimension(300, 80));
        updateFontPreview();

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.fill = GridBagConstraints.BOTH;
        add(fontPreview, gc);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 1;
        row++;

        // Notes section
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Notes", "Defaults for journals and poems"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridwidth = 1;

        journalFont = new JSpinner(new SpinnerNumberModel(store.getJournalFontSize(), 8, 72, 1));
        journalFont.setUI(new ModernSpinnerUI());
        poemFont = new JSpinner(new SpinnerNumberModel(store.getPoemFontSize(), 8, 72, 1));
        poemFont.setUI(new ModernSpinnerUI());

        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Journal font size:"), gc);
        gc.gridx = 1; add(journalFont, gc);
        row++;

        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Poem font size:"), gc);
        gc.gridx = 1; add(poemFont, gc);
        row++;

        JLabel overrideNote = SettingsUi.label("To override the editor font size later, adjust the journal and poem sizes here.");
        overrideNote.setFont(overrideNote.getFont().deriveFont(Font.PLAIN, 11f));
        overrideNote.setForeground(new Color(0, 0, 0, 120));
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        add(overrideNote, gc);
        gc.gridwidth = 1;
        row++;

        double delaySeconds = store.getAutosaveDelayMs() / 1000.0;
        double minS = 0.0;
        double maxS = 600.0;
        double initS = Math.max(minS, Math.min(maxS, delaySeconds));
        autosaveSpin = new JSpinner(new SpinnerNumberModel(initS, minS, maxS, 0.5));
        autosaveSpin.setUI(new ModernSpinnerUI());
        ((JSpinner.NumberEditor) autosaveSpin.getEditor()).getTextField().setColumns(4);
        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Autosave delay (s):"), gc);
        gc.gridx = 1; add(autosaveSpin, gc);
        row++;

        // Autocorrect section
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Autocorrect", "Typing assistance"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridwidth = 2;
        spellChk = new JCheckBox("Enable spell check", store.isSpellCheckEnabled());
        spellChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        spellChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = row; add(spellChk, gc);
        row++;

        journalAutocorrectChk = new JCheckBox("Enable autocorrect in journal editor", store.isJournalAutocorrectEnabled());
        journalAutocorrectChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        journalAutocorrectChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = row; add(journalAutocorrectChk, gc);
        row++;

        poetryAutocorrectChk = new JCheckBox("Enable autocorrect in poetry editor", store.isPoetryAutocorrectEnabled());
        poetryAutocorrectChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        poetryAutocorrectChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = row; add(poetryAutocorrectChk, gc);
        row++;

        // Startup & Dates section
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Startup & Dates", "Launch behavior and formats"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridwidth = 1;

        String[] datePatterns = main.infrastructure.io.DateFormatUtil.getCommonPatterns();
        dateFormatBox = new JComboBox<>(datePatterns);
        dateFormatBox.setUI(new ModernComboBoxUI());
        dateFormatBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        dateFormatBox.setSelectedItem(store.getDateFormat());
        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Date format:"), gc);
        gc.gridx = 1; add(dateFormatBox, gc);
        row++;

        openLastChk = new JCheckBox("Open last note on startup", store.isOpenLastOnStartup());
        openLastChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        openLastChk.setBackground(new Color(0, 0, 0, 0));
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; add(openLastChk, gc);

        // Backup settings moved to Storage section
    }

    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore store = SettingsStore.get();
        String fontFamily = (String) fontFamilyBox.getSelectedItem();
        Integer baseSize = (Integer) fontSizeBox.getSelectedItem();
        String lineSpacing = (String) lineSpacingBox.getSelectedItem();
        if (fontFamily != null) store.setEditorFontFamily(fontFamily);
        if (lineSpacing != null) store.setEditorLineSpacing(lineSpacing);
        if (baseSize != null) {
            store.setJournalFontSize(baseSize);
            store.setPoemFontSize(baseSize);
        }

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
    }

    private void updateFontPreview() {
        String family = (String) fontFamilyBox.getSelectedItem();
        Integer size = (Integer) fontSizeBox.getSelectedItem();
        String spacingStr = (String) lineSpacingBox.getSelectedItem();
        if (family == null) family = "Serif";
        if (size == null) size = 16;
        float spacing = switch (spacingStr) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };

        fontPreview.setFont(new Font(family, Font.PLAIN, size));
        try {
            StyledDocument doc = fontPreview.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        } catch (Exception ignored) {}
    }

    private static void installPopupScrollbar(JComboBox<?> comboBox) {
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> applyPopupScrollbar(comboBox));
            }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private static void applyPopupScrollbar(JComboBox<?> comboBox) {
        try {
            Object child = comboBox.getUI().getAccessibleChild(comboBox, 0);
            if (child instanceof BasicComboPopup popup) {
                JList<?> list = popup.getList();
                JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, list);
                if (scroller == null) return;
                JScrollBar vbar = scroller.getVerticalScrollBar();
                vbar.setUI(new ModernScrollBarUI());
                vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
                vbar.setOpaque(false);
                JScrollBar hbar = scroller.getHorizontalScrollBar();
                hbar.setUI(new ModernScrollBarUI());
                hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
                hbar.setOpaque(false);
            }
        } catch (Throwable ignored) {}
    }
}
