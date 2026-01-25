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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;

import main.core.font.CustomFont;
import main.core.service.SettingsStore;
import main.infrastructure.font.CustomFontRenderer;
import main.infrastructure.font.CustomFontSupport;
import main.infrastructure.startup.MacLoginItem;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.features.font.CustomFontStudioDialog;

class GeneralSettingsPage extends JPanel implements SettingsPage {
    private static final String[] BUILTIN_FONTS = {
        "Serif", "Georgia", "Garamond", "Baskerville",
        "Lucida Handwriting", "Segoe Script", "Comic Sans MS", "Bradley Hand",
        "Segoe Print", "Marker Felt", "Noteworthy", "Chalkboard", "Chalkboard SE",
        "Apple Chancery", "Snell Roundhand", "Zapfino", "Brush Script MT",
        "Lucida Calligraphy", "Papyrus", "Cursive"
    };
    private final JSpinner journalFont;
    private final JSpinner poemFont;
    private final JSpinner autosaveSpin;
    private final JComboBox<String> fontFamilyBox;
    private final JComboBox<Integer> fontSizeBox;
    private final JComboBox<String> lineSpacingBox;
    private final JPanel fontPreview;
    private final CustomFontRenderer customFontRenderer = new CustomFontRenderer();
    private final JComboBox<String> dateFormatBox;
    private final JCheckBox openLastChk;
    private final JCheckBox spellChk;
    private final JCheckBox journalAutocorrectChk;
    private final JCheckBox poetryAutocorrectChk;
    private final JCheckBox menuBarChk;
    private final JCheckBox launchOnLoginChk;
    

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

        fontFamilyBox = new JComboBox<>();
        fontFamilyBox.setUI(new ModernComboBoxUI());
        fontFamilyBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        reloadFontFamilyOptions(store.getEditorFontFamily());
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

        RoundedButton customFontsButton = new RoundedButton("Custom Fonts...");
        customFontsButton.setPreferredSize(new Dimension(160, 28));
        customFontsButton.addActionListener(e -> openCustomFontsDialog());
        gc.gridx = 0; gc.gridy = row; add(SettingsUi.label("Custom fonts:"), gc);
        gc.gridx = 1; add(customFontsButton, gc);
        row++;

        fontPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintFontPreview((Graphics2D) g);
            }
        };
        fontPreview.setBackground(new Color(252, 252, 250));
        fontPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        fontPreview.setPreferredSize(new Dimension(300, 80));

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
        row++;

        // Menu Bar Service section (macOS only)
        if (isMacOS()) {
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
            gc.insets = new Insets(16, 5, 5, 5);
            add(SettingsUi.header("Menu Bar Quick Entry", "Quick access from the macOS menu bar"), gc);
            row++;

            gc.insets = new Insets(5, 5, 5, 5);
            gc.gridwidth = 2;

            menuBarChk = new JCheckBox("Enable menu bar quick entry", store.isMenuBarServiceEnabled());
            menuBarChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
            menuBarChk.setBackground(new Color(0, 0, 0, 0));
            menuBarChk.setToolTipText("Add a Simjot icon to the menu bar for quick journal entries");
            gc.gridx = 0; gc.gridy = row; add(menuBarChk, gc);
            row++;

            JLabel menuBarDesc = new JLabel("<html><div style='width:280px;color:#666;font-size:11px;'>" +
                "When enabled, a Simjot icon appears in the menu bar. Click it to open a quick entry panel " +
                "with a minimal formatting toolbar. Entries are saved to the 'quick' folder.</div></html>");
            menuBarDesc.setFont(menuBarDesc.getFont().deriveFont(11f));
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; add(menuBarDesc, gc);
            row++;

            launchOnLoginChk = new JCheckBox("Start Simjot at login", store.isLaunchOnLoginEnabled());
            launchOnLoginChk.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
            launchOnLoginChk.setBackground(new Color(0, 0, 0, 0));
            launchOnLoginChk.setToolTipText("Launches Simjot automatically when you sign in");
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; add(launchOnLoginChk, gc);
            row++;
        } else {
            menuBarChk = null;
            launchOnLoginChk = null;
        }

        // Backup settings moved to Storage section
    }
    
    private static boolean isMacOS() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
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
        
        // Menu bar service (macOS only)
        boolean menuBarEnabled = store.isMenuBarServiceEnabled();
        if (menuBarChk != null) {
            boolean wasEnabled = store.isMenuBarServiceEnabled();
            boolean nowEnabled = menuBarChk.isSelected();
            store.setMenuBarServiceEnabled(nowEnabled);
            menuBarEnabled = nowEnabled;
            
            // Initialize or shutdown based on change
            if (nowEnabled && !wasEnabled) {
                main.ui.app.AppConfig.initMenuBarService();
            } else if (!nowEnabled && wasEnabled) {
                try {
                    main.infrastructure.menubar.MenuBarService.getInstance().shutdown();
                } catch (Throwable ignored) {}
            }
            updateMacQuitStrategy(nowEnabled);
        }

        if (launchOnLoginChk != null) {
            store.setLaunchOnLoginEnabled(launchOnLoginChk.isSelected());
            MacLoginItem.sync(store.isLaunchOnLoginEnabled(), menuBarEnabled);
        }
    }

    private void updateFontPreview() {
        customFontRenderer.clearCache();
        fontPreview.repaint();
    }

    private void paintFontPreview(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String family = (String) fontFamilyBox.getSelectedItem();
        Integer size = (Integer) fontSizeBox.getSelectedItem();
        if (family == null) family = "Serif";
        if (size == null) size = 16;

        String sampleText = "The quick brown fox jumps over the lazy dog.";
        int x = 12;
        int y = 28;

        if (CustomFontSupport.isCustomDisplayName(family)) {
            // Render with custom font
            try {
                CustomFont customFont = CustomFontSupport.loadByDisplayName(family);
                if (customFont != null) {
                    customFontRenderer.drawText(g2, customFont, sampleText, x, y, size, Color.BLACK);
                    return;
                }
            } catch (Exception ignored) {}
            // Fallback if custom font fails
            family = "Serif";
        }

        // Render with system font
        g2.setFont(new Font(family, Font.PLAIN, size));
        g2.setColor(Color.BLACK);
        g2.drawString(sampleText, x, y);
    }

    private static void updateMacQuitStrategy(boolean keepRunningInTray) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return;
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            java.awt.desktop.QuitStrategy qs = keepRunningInTray
                ? java.awt.desktop.QuitStrategy.NORMAL_EXIT
                : java.awt.desktop.QuitStrategy.CLOSE_ALL_WINDOWS;
            d.setQuitStrategy(qs);
        } catch (Throwable ignored) {}
    }

    private void reloadFontFamilyOptions(String preferredSelection) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String font : BUILTIN_FONTS) {
            model.addElement(font);
        }
        for (String custom : CustomFontSupport.listDisplayNames()) {
            model.addElement(custom);
        }
        fontFamilyBox.setModel(model);
        if (preferredSelection != null && model.getIndexOf(preferredSelection) >= 0) {
            fontFamilyBox.setSelectedItem(preferredSelection);
        } else if (model.getSize() > 0) {
            fontFamilyBox.setSelectedIndex(0);
        }
    }

    private void openCustomFontsDialog() {
        try {
            Window owner = SwingUtilities.getWindowAncestor(this);
            java.nio.file.Path dir = CustomFontSupport.fontsDirectory();
            if (dir == null) return;
            String selected = CustomFontStudioDialog.showDialog(owner, dir);
            String current = (String) fontFamilyBox.getSelectedItem();
            reloadFontFamilyOptions(current);
            if (selected != null && !selected.isBlank()) {
                fontFamilyBox.setSelectedItem(CustomFontSupport.toDisplayName(selected));
            }
            updateFontPreview();
        } catch (Throwable ignored) {}
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
