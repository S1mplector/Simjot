package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;

import main.core.export.PoemExporter;
import main.core.poetry.ScansionEngine;
import main.core.poetry.SoundDevicesEngine;
import main.core.poetry.ThematicAnalyzer;
import main.core.poetry.VocabularyAnalyzer;
import main.core.service.LastSaveTracker;
import main.core.service.SettingsStore;
import main.core.spelling.AutocorrectDocumentFilter;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.FileIO;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.containers.TranslucentPanel;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.util.EditorUIUtils;
import main.ui.dialog.export.PoemExportDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.PoemBackgroundDialog;
import main.ui.features.editing.UndoRedoManager;
import main.ui.features.poetry.RhymesDockPanel;
import main.ui.features.poetry.StatsSidebarPanel;

public class PoemPanel extends AbstractEditorPanel {

    /**
     * This class is the main poem writing interface with real-time poetry analysis.
     * It provides a rich text editor with live statistics, poetry metrics, and interactive tools.
     * Features include:
     * - Real-time word count and reading time estimation
     * - Poetry analysis metrics (vocabulary, theme, sound devices, meter)
     * - Interactive rhyming dictionary and synonym finder
     * - Customizable background themes
     * - Undo/redo functionality with history tracking
     * 
     * It is a child class of AbstractEditorPanel.
     * Inherited: app, journalFolder, cardLayout, cardPanel

     */

    // Components for poem writing
    protected JTextField poemTitleField;
    protected JTextPane poemEditor;

    private final String[] INSPIRATIONAL_WORDS = {
        "Ethereal", "Ephemeral", "Sonder", "Solitude", "Cascade", "Labyrinthine",
        "Mellifluous", "Nostalgia", "Petrichor", "Ineffable", "Serendipity", "Halcyon",
        "Luminescence", "Redolent", "Somnambulist", "Susurrus", "Opalescent", "Reverie"
    };

    // Helpers
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private SaveIndicatorPanel saveIndicator;
    private volatile boolean isAutosaving = false;
    private AutosaveManager autosaveManager;
    // 'currentFile' is inherited from AbstractEditorPanel

    // UI refs for toggling
    private JPanel toolbarContainer;
    private JPanel bottomPanel;
    private boolean distractionFree = false;
    private JLabel statusLabel;
    // Minimal header shown in distraction-free mode (contains only a back button)
    private JPanel dfHeader;
    // Optional poetry helpers
    private StatsSidebarPanel statsPanel;
    private RhymesDockPanel rhymesDock;
    // Word highlight for rhymes dock
    private Object currentWordHighlight;
    private final WordOutlineHighlightPainter wordHighlightPainter = new WordOutlineHighlightPainter(new Color(100, 149, 237, 120));
    // Poetry analysis engines
    private final VocabularyAnalyzer vocabularyAnalyzer = new VocabularyAnalyzer();
    private final ThematicAnalyzer thematicAnalyzer = new ThematicAnalyzer();
    private final SoundDevicesEngine soundDevicesEngine = new SoundDevicesEngine();
    private final ScansionEngine scansionEngine = new ScansionEngine();
    // Poetry metrics labels for bottom bar
    private JLabel vocabLabel;
    private JLabel themeLabel;
    private JLabel soundLabel;
    private JLabel meterLabel;
    private javax.swing.Timer analysisDebounceTimer;
    private UndoRedoManager poemTitleUndoManager;
    private UndoRedoManager poemContentUndoManager;
    // Poetry toolkit controls
    private javax.swing.JComboBox<String> formPresetBox;
    private JLabel toolkitStatusLabel;
    private JLabel toolkitHintLabel;

    public PoemPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
    }

    // Helper: determine 0-based line index at caret (by counting newlines before caret)
    private int getCaretLineIndex() {
        try {
            int pos = poemEditor.getCaretPosition();
            String text = poemEditor.getText();
            if (text == null || text.isEmpty()) return -1;
            if (pos < 0) pos = 0; if (pos > text.length()) pos = text.length();
            int line = 0;
            for (int i = 0; i < pos; i++) if (text.charAt(i) == '\n') line++;
            return line;
        } catch (Throwable ignored) { return -1; }
    }

    // Helper: determine word at current caret position
    private String getWordAtCaret() {
        try {
            int pos = poemEditor.getCaretPosition();
            String text = poemEditor.getText();
            if (text == null || text.isEmpty()) return null;
            if (pos < 0) pos = 0; if (pos > text.length()) pos = text.length();
            int start = pos;
            while (start > 0 && Character.isLetter(text.charAt(start-1))) start--;
            int end = pos;
            while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
            if (end > start) return text.substring(start, end);
        } catch (Throwable ignored) {}
        return null;
    }

    // Unified constructor: if poemFileToEdit is non-null, load it and save updates to same file
    public PoemPanel(JournalApp app, File poemFileToEdit, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this(app, journalFolder, cardLayout, cardPanel);
        if (poemFileToEdit != null) {
            this.currentFile = poemFileToEdit;
            loadExistingPoem(poemFileToEdit);
        }
    }

    // Paint the background image via helper (with white base)
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        String bgPath = SettingsStore.get().getPoemBackgroundImage();
        float opacity = SettingsStore.get().getPoemBackgroundOpacity();
        backgroundPainter.paint(g, this, bgPath, opacity, true);
    }

    private void initUI() {
        // Build right-side controls to pass into the shared toolbar
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.setOpaque(false);
        // Toggles for poetry helpers
        ToolbarMenuIconButton statsToggle = new ToolbarMenuIconButton("Stats", "stats");
        statsToggle.setToolTipText("Toggle Stats Sidebar");
        statsToggle.addActionListener(e -> {
            if (statsPanel != null) {
                boolean vis = !statsPanel.isVisible();
                statsPanel.setVisible(vis);
                statsToggle.setSelected(vis);
                revalidate(); repaint();
            }
        });
        ToolbarMenuIconButton rhymesToggle = new ToolbarMenuIconButton("Rhymes", "rhyme");
        rhymesToggle.setToolTipText("Toggle Rhymes & Thesaurus Dock");
        rhymesToggle.addActionListener(e -> {
            if (rhymesDock != null) {
                boolean vis = !rhymesDock.isVisible();
                rhymesDock.setVisible(vis);
                rhymesToggle.setSelected(vis);
                if (vis) {
                    String w = getWordAtCaret();
                    rhymesDock.update(w, poemEditor.getText());
                    updateWordHighlight();
                } else {
                    clearWordHighlight();
                }
                revalidate(); repaint();
            }
        });
        ToolbarMenuIconButton settingsBtn = new ToolbarMenuIconButton("Background", "backgroundoptions");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            PoemBackgroundDialog dialog = new PoemBackgroundDialog((java.awt.Frame)SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        // Distraction-free toggle
        ToolbarMenuIconButton dfBtn = new ToolbarMenuIconButton("Fullscreen", "fullscreen");
        dfBtn.setToolTipText("Distraction-Free Mode");
        dfBtn.addActionListener(e -> toggleDistractionFree());
        // Export button (advanced)
        ToolbarMenuIconButton exportBtn = new ToolbarMenuIconButton("Export", "export");
        exportBtn.setToolTipText("Export poem (Markdown/HTML/TXT/PNG)");
        exportBtn.addActionListener(e -> exportPoem());
        rightToolbar.add(statsToggle);
        rightToolbar.add(rhymesToggle);
        rightToolbar.add(exportBtn);
        rightToolbar.add(dfBtn);
        rightToolbar.add(settingsBtn);
        // Create shared toolbar and wire callbacks
        NotebookInfo nbInfo = new NotebookInfo(
                journalFolder.getName(),
                NotebookInfo.Type.POETRY,
                journalFolder,
                journalFolder.lastModified(),
                null
        );
        main.ui.components.toolbars.PoetryStyleToolbar sharedToolbar = new main.ui.components.toolbars.PoetryStyleToolbar(
                app,
                nbInfo,
                "",
                null,
                (selected) -> setTypingStyleBold(selected),
                (selected) -> setTypingStyleItalic(selected),
                (selected) -> setTypingStyleUnderline(selected),
                (selected) -> setTypingStyleStrike(selected),
                (fontName) -> {
                    Font currentFont = poemEditor.getFont();
                    poemEditor.setFont(new Font(fontName, currentFont.getStyle(), currentFont.getSize()));
                    applyParagraphFontToAll();
                },
                (size) -> {
                    poemEditor.setFont(poemEditor.getFont().deriveFont(size.floatValue()));
                    applyParagraphFontToAll();
                    SettingsStore.get().setPoemFontSize(size);
                    SettingsStore.get().save();
                },
                this::applyLineSpacing,
                rightToolbar
        );
        toolbarContainer = sharedToolbar.getContainer();
        poemTitleField = sharedToolbar.getTitleField();
        add(toolbarContainer, BorderLayout.NORTH);

        // Distraction-free header: only Back button, no other controls
        dfHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dfHeader.setOpaque(false);
        dfHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        // Use the same back icon, but only exit fullscreen (do not navigate away)
        ToolbarMenuIconButton dfBack = new ToolbarMenuIconButton("Exit", "back");
        dfBack.setToolTipText("Exit Fullscreen");
        dfBack.addActionListener(e -> toggleDistractionFree());
        dfHeader.add(dfBack);

        // --- Center Panel: Poem Text Area with a cursive feel ---
        JPanel textWrapper = new TranslucentPanel() { // Paper-like card
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new Color(255,255,250,230));
                g2.fillRoundRect(6, 6, w-12, h-12, 16, 16);
                g2.setColor(new Color(0,0,0,25));
                g2.drawRoundRect(6, 6, w-12, h-12, 16, 16);
                g2.dispose();
            }
        }; // Use the new panel
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        poemEditor = new JTextPane();
        poemEditor.setOpaque(false);
        poemEditor.setForeground(new Color(40, 40, 40));

        // Load font settings from Appearance settings
        String fontFamily = SettingsStore.get().getEditorFontFamily();
        int savedFontSize = SettingsStore.get().getPoemFontSize();
        String lineSpacingStr = SettingsStore.get().getEditorLineSpacing();
        poemEditor.setFont(new Font(fontFamily, Font.PLAIN, savedFontSize));
        if (poemTitleField != null) {
            poemTitleField.setFont(new Font(fontFamily, Font.PLAIN, savedFontSize));
            poemTitleField.setPlaceholder(null);
        }
        // Apply line spacing from settings
        float spacing = switch (lineSpacingStr) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };
        javax.swing.SwingUtilities.invokeLater(() -> {
            StyledDocument doc = poemEditor.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        });

        // Enable rich image paste & drag-and-drop into the poem editor
        ImagePasteManager.install(
                poemEditor,
                () -> new File(journalFolder, "attachments"),
                800 // max width in pixels for inserted images
        );

        JScrollPane scrollPane = new JScrollPane(poemEditor);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        // Apply modern, slim scrollbars
        JScrollBar vbar = scrollPane.getVerticalScrollBar();
        vbar.setUI(new ModernScrollBarUI());
        vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        vbar.setOpaque(false);
        vbar.setUnitIncrement(16);
        JScrollBar hbar = scrollPane.getHorizontalScrollBar();
        hbar.setUI(new ModernScrollBarUI());
        hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
        hbar.setOpaque(false);
        hbar.setUnitIncrement(16);

        // Add undo/redo support (after components are created)
        this.poemContentUndoManager = new UndoRedoManager(poemEditor);
        this.poemTitleUndoManager = new UndoRedoManager(poemTitleField);

        try {
            if (SettingsStore.get().isPoetryAutocorrectEnabled()) {
                AutocorrectDocumentFilter.install(poemEditor);
            }
        } catch (Throwable ignored) {}

        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Initialize optional side panels (hidden by default)
        statsPanel = new StatsSidebarPanel();
        statsPanel.setVisible(false);
        statsPanel.setAnalysisFinishedCallback(this::refreshToolkitStatus);
        // Wire: clicking a row in stats sidebar moves caret to that text line and highlights it
        statsPanel.setRowClickListener(lineIndex -> {
            if (lineIndex < 0) return;
            try {
                String text = poemEditor.getText();
                if (text == null) return;
                int idx = 0;
                int line = 0;
                int len = text.length();
                // Find start offset of the requested line
                for (int i = 0; i < len && line < lineIndex; i++) {
                    if (text.charAt(i) == '\n') {
                        idx = i + 1;
                        line++;
                    }
                }
                final int pos = Math.min(Math.max(0, idx), len);
                poemEditor.requestFocusInWindow();
                poemEditor.setCaretPosition(pos);
                // Update sidebar highlight immediately
                statsPanel.setHighlightedLine(lineIndex);
            } catch (Throwable ignored) { }
        });
        rhymesDock = new RhymesDockPanel();
        rhymesDock.setVisible(false);

        // Add poetry toolkit above the editor
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        JPanel northStack = new JPanel();
        northStack.setOpaque(false);
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.add(Box.createRigidArea(new Dimension(0, 10)));
        northStack.add(buildToolkitBar());
        northStack.add(Box.createRigidArea(new Dimension(0, 8)));
        centerContainer.add(northStack, BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);

        add(statsPanel, BorderLayout.WEST);
        add(centerContainer, BorderLayout.CENTER);
        add(rhymesDock, BorderLayout.EAST);

        // --- Bottom Panel: Two rows - status and poetry analysis ---
        bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(3, 10, 5, 10));

        // Row 1: Status info
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel stanzaLabel = new JLabel("Stanzas: 1");
        stanzaLabel.setForeground(Color.DARK_GRAY);
        stanzaLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        row1.add(stanzaLabel, BorderLayout.WEST);
        
        // Listener to update the stanza count
        poemEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
                schedulePoetryAnalysis();
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
                schedulePoetryAnalysis();
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
                schedulePoetryAnalysis();
            }
        });
        // Caret listener to update rhyme/synonyms for current word
        poemEditor.addCaretListener(e -> {
            if (rhymesDock != null && rhymesDock.isVisible()) {
                String w = getWordAtCaret();
                rhymesDock.update(w, poemEditor.getText());
                updateWordHighlight();
            }
            if (statsPanel != null && statsPanel.isVisible()) {
                statsPanel.setHighlightedLine(getCaretLineIndex());
            }
        });
        // Autosave on title change as well
        poemTitleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
        });

        // Status in center of row1
        JPanel centerFlow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        centerFlow.setOpaque(false);
        statusLabel = new JLabel("Words: 0 • Chars: 0 • Stanzas: 0 • ~0 min read");
        statusLabel.setForeground(Color.DARK_GRAY);
        centerFlow.add(statusLabel);
        row1.add(centerFlow, BorderLayout.CENTER);

        // Save button (via EditorUIUtils)
        ToolbarIconButton saveButton = EditorUIUtils.createSaveButton("Save Poem", this::savePoem);
        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        eastPanel.setOpaque(false);
        saveIndicator = new SaveIndicatorPanel();
        eastPanel.add(saveIndicator);
        eastPanel.add(saveButton);
        row1.add(eastPanel, BorderLayout.EAST);
        
        bottomPanel.add(row1);

        // Row 2: Poetry analysis metrics
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 2));
        row2.setOpaque(false);
        
        vocabLabel = new JLabel("Vocab: —");
        vocabLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        vocabLabel.setForeground(new Color(80, 80, 140));
        
        themeLabel = new JLabel("Theme: —");
        themeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        themeLabel.setForeground(new Color(120, 80, 80));
        
        soundLabel = new JLabel("Sound: —");
        soundLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        soundLabel.setForeground(new Color(80, 120, 80));
        
        meterLabel = new JLabel("Meter: —");
        meterLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        meterLabel.setForeground(new Color(100, 100, 100));
        
        row2.add(vocabLabel);
        row2.add(new JLabel("•"));
        row2.add(themeLabel);
        row2.add(new JLabel("•"));
        row2.add(soundLabel);
        row2.add(new JLabel("•"));
        row2.add(meterLabel);
        
        bottomPanel.add(row2);

        add(bottomPanel, BorderLayout.SOUTH);

        // --- Autosave wiring ---
        int delayMs = SettingsStore.get().getAutosaveDelayMs();
        if (delayMs > 0) {
            autosaveManager = new AutosaveManager(delayMs,
                    this::savePoem,
                    () -> { isAutosaving = true; if (saveIndicator != null) saveIndicator.setSaving(); },
                    () -> { 
                        updateSaveIndicatorFromCurrentFile();
                        isAutosaving = false; 
                    });
        } else {
            autosaveManager = null; // autosave disabled
        }

        // Initial metrics
        updateMetrics(stanzaLabel);
        refreshToolkitStatus();
        // Run initial poetry analysis
        schedulePoetryAnalysis();
    }

    private void updateStanzaCount(JLabel label) {
        String text = poemEditor.getText();
        if (text.trim().isEmpty()) {
            label.setText("Stanzas: 0");
            updateStatus(text, 0);
            return;
        }
        // A stanza is a block of text separated by one or more newlines
        int stanzas = text.split("\\n\\s*\\n").length;
        label.setText("Stanzas: " + stanzas);
        updateStatus(text, stanzas);
    }

    private void updateMetrics(JLabel stanzaLabel) {
        String text = poemEditor.getText();
        int stanzas = text.trim().isEmpty() ? 0 : text.split("\\n\\s*\\n").length;
        stanzaLabel.setText("Stanzas: " + stanzas);
        updateStatus(text, stanzas);
    }

    private JPanel buildToolkitBar() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);

        JLabel title = new JLabel("Poetry Toolkit");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        row.add(title);

        formPresetBox = new javax.swing.JComboBox<>(new String[]{
                "Free verse (no targets)",
                "Haiku (5/7/5)",
                "Limerick (9/9/6/6/9)",
                "Sonnet (14x10)",
                "Octosyllabic quatrain"
        });
        formPresetBox.setUI(new ModernComboBoxUI());
        formPresetBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        formPresetBox.setFocusable(false);
        formPresetBox.setToolTipText("Apply gentle syllable targets for common forms");
        formPresetBox.addActionListener(e -> applyFormPreset((String) formPresetBox.getSelectedItem()));
        row.add(formPresetBox);

        JButton rescanBtn = new RoundedButton("Rescan");
        rescanBtn.setFocusable(false);
        rescanBtn.setToolTipText("Refresh meter detection");
        rescanBtn.addActionListener(e -> {
            if (statsPanel != null) statsPanel.updateFromText(poemEditor.getText());
            refreshToolkitStatus();
        });
        row.add(rescanBtn);

        row.add(Box.createHorizontalStrut(10));
        toolkitStatusLabel = new JLabel("Active form: None • Detected: —");
        toolkitStatusLabel.setForeground(new Color(60, 60, 60));
        toolkitStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        row.add(toolkitStatusLabel);

        toolkitHintLabel = new JLabel("Choose a form to set gentle line targets.");
        toolkitHintLabel.setForeground(new Color(90, 90, 90));
        toolkitHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.add(row);
        wrapper.add(toolkitHintLabel);
        return wrapper;
    }

    private void applyFormPreset(String preset) {
        if (statsPanel == null || preset == null) return;
        switch (preset) {
            case "Haiku (5/7/5)" -> {
                statsPanel.setTargetPattern(new int[]{5, 7, 5}, "Haiku 5/7/5", 3);
                statsPanel.setPerStanza(false);
                setToolkitHint("Three lines: 5-7-5 syllables and one vivid image.");
            }
            case "Limerick (9/9/6/6/9)" -> {
                statsPanel.setTargetPattern(new int[]{9, 9, 6, 6, 9}, "Limerick beat", 5);
                statsPanel.setPerStanza(true);
                setToolkitHint("Five lines with playful bounce and AABBA rhyme.");
            }
            case "Sonnet (14x10)" -> {
                int[] tenBeat = new int[14];
                java.util.Arrays.fill(tenBeat, 10);
                statsPanel.setTargetPattern(tenBeat, "Sonnet line targets", 14);
                statsPanel.setPerStanza(false);
                setToolkitHint("Fourteen 10-syllable lines; aim for clear volta.");
            }
            case "Octosyllabic quatrain" -> {
                statsPanel.clearTargetPattern();
                statsPanel.setTargetSyllables(8);
                statsPanel.setPerStanza(true);
                setToolkitHint("Quatrains built from 8-syllable lines; steady and songlike.");
            }
            default -> {
                statsPanel.clearTargetPattern();
                statsPanel.setTargetSyllables(0);
                statsPanel.setPerStanza(true);
                setToolkitHint("Free verse—no fixed targets. Listen for your own rhythm.");
            }
        }
        statsPanel.updateFromText(poemEditor.getText());
        refreshToolkitStatus();
    }

    private void setToolkitHint(String text) {
        if (toolkitHintLabel != null && text != null) {
            toolkitHintLabel.setText(text);
        }
    }

    private void refreshToolkitStatus() {
        if (toolkitStatusLabel == null) return;
        String active = (statsPanel != null) ? statsPanel.getTargetPatternLabel() : null;
        String detected = (statsPanel != null) ? statsPanel.getDetectedForm() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Active form: ").append(active != null && !active.isBlank() ? active : "None");
        sb.append(" • Detected: ").append(detected != null && !detected.isBlank() ? detected : "—");
        toolkitStatusLabel.setText(sb.toString());
    }

    private void runPoetryAnalysis(String text) {
        if (text == null || text.isBlank()) {
            SwingUtilities.invokeLater(() -> {
                if (vocabLabel != null) vocabLabel.setText("Vocab: n/a");
                if (themeLabel != null) themeLabel.setText("Theme: n/a");
                if (soundLabel != null) soundLabel.setText("Sound: n/a");
                if (meterLabel != null) meterLabel.setText("Meter: n/a");
            });
            return;
        }
        
        // Run analysis in background to avoid blocking UI
        new Thread(() -> {
            String vocabText = "Vocab: ...";
            String themeText = "Theme: ...";
            String soundText = "Sound: ...";
            String meterText = "Meter: ...";
            
            try {
                // Simple vocabulary stats (always works)
                String[] words = text.trim().split("\\s+");
                int wordCount = words.length;
                java.util.Set<String> unique = new java.util.HashSet<>();
                for (String w : words) unique.add(w.toLowerCase(java.util.Locale.ROOT));
                int uniqueCount = unique.size();
                double ttr = wordCount > 0 ? (double) uniqueCount / wordCount * 100 : 0;
                vocabText = String.format("TTR: %.0f%%", ttr);
                
                // Thematic analysis
                ThematicAnalyzer.ThematicAnalysis theme = thematicAnalyzer.analyze(text);
                themeText = "Theme: " + (theme.dominantTheme.equals("Unknown") ? "General" : theme.dominantTheme);
                
                // Sound devices
                SoundDevicesEngine.SoundAnalysis sound = soundDevicesEngine.analyzePoem(text);
                int devices = sound.devices.size();
                soundText = "Sound: " + devices;
                
                // Scansion/meter
                ScansionEngine.PoemScansion scansion = scansionEngine.analyzePoem(text);
                String meter = scansion.dominantMeter;
                if (meter == null || meter.isEmpty()) meter = "Free";
                else if (meter.length() > 12) meter = meter.substring(0, 12);
                meterText = "Meter: " + meter;
                
            } catch (Throwable ex) {
                System.err.println("Poetry analysis error: " + ex.getMessage());
                ex.printStackTrace();
            }
            
            // Update UI on EDT
            final String v = vocabText, t = themeText, s = soundText, m = meterText;
            SwingUtilities.invokeLater(() -> {
                if (vocabLabel != null) vocabLabel.setText(v);
                if (themeLabel != null) themeLabel.setText(t);
                if (soundLabel != null) soundLabel.setText(s);
                if (meterLabel != null) meterLabel.setText(m);
            });
        }).start();
    }
    
    private void schedulePoetryAnalysis() {
        String text = poemEditor.getText();
        if (analysisDebounceTimer != null && analysisDebounceTimer.isRunning()) {
            analysisDebounceTimer.stop();
        }
        // Debounce: wait 500ms after last keystroke before running analysis
        analysisDebounceTimer = new javax.swing.Timer(500, e -> {
            analysisDebounceTimer.stop();
            runPoetryAnalysis(text);
        });
        analysisDebounceTimer.setRepeats(false);
        analysisDebounceTimer.start();
    }

    private void updateStatus(String text, int stanzas) {
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        int chars = text.length();
        int minutes = Math.max(0, (int)Math.ceil(words / 200.0));
        if (statusLabel != null) {
            statusLabel.setText(String.format("Words: %d • Chars: %d • Stanzas: %d • ~%d min read", words, chars, stanzas, minutes));
        }
        if (statsPanel != null && statsPanel.isVisible()) statsPanel.updateFromText(text);
    }

    private void showInspirationalWord() {
        Random rand = new Random();
        String word = INSPIRATIONAL_WORDS[rand.nextInt(INSPIRATIONAL_WORDS.length)];
        
        // Use the new custom dialog
        CustomInspirationDialog dialog = new CustomInspirationDialog((JFrame) SwingUtilities.getWindowAncestor(this), word);
        dialog.setVisible(true);
    }

    // "Save Poem" logic for a new poem
    protected void savePoem() {
        // Snapshot UI state on EDT to avoid touching Swing components from autosave worker threads
        final String[] titleHolder = new String[1];
        final String[] contentHolder = new String[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                titleHolder[0] = poemTitleField.getText().trim();
                contentHolder[0] = poemEditor.getText();
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    titleHolder[0] = poemTitleField.getText().trim();
                    contentHolder[0] = poemEditor.getText();
                });
            }
        } catch (Exception ignored) { return; }
        String title = titleHolder[0];
        String content = contentHolder[0];
        if (saveIndicator != null) saveIndicator.setSaving();
        // 'journalFolder' already points to the poems directory provided by AppDirectories
        if (!journalFolder.exists()) {
            journalFolder.mkdirs();
        }
        
        try {
            File poemFile;
            
            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + ".poem";
                poemFile = new File(journalFolder, filename);
                currentFile = poemFile;
            } else {
                // Subsequent saves - use existing file
                poemFile = currentFile;
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, java.nio.charset.StandardCharsets.UTF_8));
            writer.println(title);
            writer.println();
            writer.println(content);
            writer.flush();
            byte[] data = baos.toByteArray();
            FileIO.ensureSpace(poemFile.toPath(), data.length + 4096L, "poem save");
            FileIO.atomicWrite(poemFile.toPath(), data, true, true);

            try {
                int keep = SettingsStore.get().getBackupKeepCount();
                EntryHistoryManager.recordSnapshot(poemFile, keep);
            } catch (Throwable ignored) {}
            
            // Remember as last opened file for startup restore
            try {
                SettingsStore.get().setLastOpenedFilePath(poemFile.getAbsolutePath());
                SettingsStore.get().save();
            } catch (Throwable ignored) {}

            // Mark last successful save for status bar and undo save-point
            LastSaveTracker.markSaved();
            try {
                if (poemContentUndoManager != null) poemContentUndoManager.markSavePoint();
                if (poemTitleUndoManager != null) poemTitleUndoManager.markSavePoint();
            } catch (Throwable ignored) {}

            // Suppress success popups; rely on status indicator only
            updateSaveIndicatorFromCurrentFile();
            
            // Don't clear fields - keep content like NewEntryPanel does
            // This allows continuous editing of the same poem
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving poem.", true).showDialog();
            if (saveIndicator != null) saveIndicator.setError("Error saving");
        }
    }

    private void toggleStyle(Object styleAttr) {
        StyledDocument doc = poemEditor.getStyledDocument();
        int start = poemEditor.getSelectionStart();
        int end = poemEditor.getSelectionEnd();
        if (start == end) return; // nothing selected
        MutableAttributeSet attrs = new SimpleAttributeSet();
        boolean enable;
        AttributeSet selectionAttrs = doc.getCharacterElement(start).getAttributes();
        if (styleAttr == StyleConstants.CharacterConstants.Bold) {
            enable = !StyleConstants.isBold(selectionAttrs);
            StyleConstants.setBold(attrs, enable);
        } else if (styleAttr == StyleConstants.CharacterConstants.Italic) {
            enable = !StyleConstants.isItalic(selectionAttrs);
            StyleConstants.setItalic(attrs, enable);
        } else if (styleAttr == StyleConstants.CharacterConstants.Underline) {
            enable = !StyleConstants.isUnderline(selectionAttrs);
            StyleConstants.setUnderline(attrs, enable);
        } else {
            return;
        }
        doc.setCharacterAttributes(start, end - start, attrs, false);
    }

    private void applyParagraphFontToAll() {
        StyledDocument doc = poemEditor.getStyledDocument();
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, poemEditor.getFont().getFamily());
        StyleConstants.setFontSize(attrs, poemEditor.getFont().getSize());
        doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
    }

    private void toggleStrike() {
        try {
            StyledDocument doc = poemEditor.getStyledDocument();
            int start = poemEditor.getSelectionStart();
            int end = poemEditor.getSelectionEnd();
            if (start == end) return;
            AttributeSet selectionAttrs = doc.getCharacterElement(start).getAttributes();
            boolean enable = !StyleConstants.isStrikeThrough(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setStrikeThrough(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Throwable ignored) {}
    }

    // --- Typing style (affects new text via input attributes) ---
    private void setTypingStyleBold(boolean on) {
        try {
            MutableAttributeSet attrs = new SimpleAttributeSet(((StyledEditorKit) poemEditor.getEditorKit()).getInputAttributes());
            StyleConstants.setBold(attrs, on);
            poemEditor.setCharacterAttributes(attrs, true);
        } catch (Throwable ignored) {}
    }

    private void setTypingStyleItalic(boolean on) {
        try {
            MutableAttributeSet attrs = new SimpleAttributeSet(((StyledEditorKit) poemEditor.getEditorKit()).getInputAttributes());
            StyleConstants.setItalic(attrs, on);
            poemEditor.setCharacterAttributes(attrs, true);
        } catch (Throwable ignored) {}
    }

    private void setTypingStyleUnderline(boolean on) {
        try {
            MutableAttributeSet attrs = new SimpleAttributeSet(((StyledEditorKit) poemEditor.getEditorKit()).getInputAttributes());
            StyleConstants.setUnderline(attrs, on);
            poemEditor.setCharacterAttributes(attrs, true);
        } catch (Throwable ignored) {}
    }

    private void setTypingStyleStrike(boolean on) {
        try {
            MutableAttributeSet attrs = new SimpleAttributeSet(((StyledEditorKit) poemEditor.getEditorKit()).getInputAttributes());
            StyleConstants.setStrikeThrough(attrs, on);
            poemEditor.setCharacterAttributes(attrs, true);
        } catch (Throwable ignored) {}
    }

    private void applyLineSpacing(String val) {
        float spacing = switch (val) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };
        StyledDocument doc = poemEditor.getStyledDocument();
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attrs, spacing);
        doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
    }

    private void toggleDistractionFree() {
        distractionFree = !distractionFree;
        // Swap toolbar with minimal df header in NORTH
        if (distractionFree) {
            // Entering distraction-free: remove main toolbar, add dfHeader
            try { remove(toolbarContainer); } catch (Throwable ignored) {}
            add(dfHeader, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(false);
        } else {
            // Exiting distraction-free: remove dfHeader, restore toolbar
            try { remove(dfHeader); } catch (Throwable ignored) {}
            add(toolbarContainer, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(true);
        }
        revalidate();
        repaint();
    }

    private void exportPoem() {
        String title = poemTitleField.getText().trim();
        String content = poemEditor.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Export", "Nothing to export.", true).showDialog();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        String suggestedBase;
        if (currentFile != null) {
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.') >= 0 ? name.lastIndexOf('.') : name.length();
            suggestedBase = name.substring(0, dot);
        } else if (!title.isEmpty()) {
            suggestedBase = title.replaceAll("[^A-Za-z0-9-_]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
            if (suggestedBase.isBlank()) suggestedBase = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        } else {
            suggestedBase = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        }
        PoemExportDialog dialog = new PoemExportDialog(owner, journalFolder, suggestedBase);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) return;

        PoemExporter.Format fmt = dialog.getSelectedFormat();
        PoemExporter.Options opts = dialog.getOptions();
        // Build basic stats for optional inclusion
        java.util.Map<String, String> stats = new java.util.LinkedHashMap<>();
        int words = content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length;
        int chars = content.length();
        int stanzas = content.trim().isEmpty() ? 0 : content.split("\\n\\s*\\n").length;
        stats.put("Words", String.valueOf(words));
        stats.put("Chars", String.valueOf(chars));
        stats.put("Stanzas", String.valueOf(stanzas));

        // Mirror current editor styling into export options (applies to HTML)
        try {
            Font f = poemEditor.getFont();
            if (f != null) {
                opts.fontFamily = f.getFamily();
                opts.fontSizePx = f.getSize();
            }
            // Read paragraph line spacing from the first paragraph's attributes
            StyledDocument doc = poemEditor.getStyledDocument();
            javax.swing.text.Element para = doc.getParagraphElement(0);
            if (para != null) {
                float ls = StyleConstants.getLineSpacing(para.getAttributes()); // additive: 0.0, 0.2, 0.5, ...
                opts.lineHeight = 1.0f + Math.max(0f, ls);
            }
        } catch (Throwable ignored) {}

        try {
            File out = PoemExporter.buildTargetFile(journalFolder, currentFile, fmt);
            out = dialog.getOutputFile(out, fmt);
            switch (fmt) {
                case MARKDOWN, HTML, TXT -> {
                    PoemExporter.exportTextual(out, fmt, title, content, opts, stats);
                }
                case PNG -> {
                    // Render the main text area card if possible (centerContainer's textWrapper)
                    // We can render the parent of poemEditor to capture styling
                    JComponent toRender = (JComponent) poemEditor.getParent().getParent();
                    PoemExporter.exportPng(out, title, content, opts, toRender);
                }
            }
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Export", "Exported to: " + out.getName(), false).showDialog();
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error exporting.", true).showDialog();
        }
    }

    // Load an existing poem file into the editor fields
    private void loadExistingPoem(File poemFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(poemFile))) {
            String title = reader.readLine();
            if (title == null) title = "";
            poemTitleField.setText(title);
            reader.readLine(); // skip blank line

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            poemEditor.setText(content.toString());
            // Ensure the editor starts at the top (caret and viewport)
            try {
                poemEditor.setCaretPosition(0);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        poemEditor.setCaretPosition(0);
                        // Nudge viewport to top-left
                        poemEditor.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1));
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading poem.", true).showDialog();
        }
    }

    // --- AbstractEditorPanel hooks and remaining interface bits ---
    @Override
    protected void safeLoadFile(File f) {
        loadExistingPoem(f);
        if (saveIndicator != null && f != null) {
            saveIndicator.setSavedFromTimestamp(f.lastModified());
        }
        try {
            if (poemContentUndoManager != null) poemContentUndoManager.clearHistory();
            if (poemTitleUndoManager != null) poemTitleUndoManager.clearHistory();
            if (poemContentUndoManager != null) poemContentUndoManager.markSavePoint();
            if (poemTitleUndoManager != null) poemTitleUndoManager.markSavePoint();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void clearEditor() {
        poemTitleField.setText("");
        poemEditor.setText("");
        if (saveIndicator != null) saveIndicator.clear();
    }

    private void updateSaveIndicatorFromCurrentFile() {
        if (saveIndicator == null) return;
        long ts = (currentFile != null && currentFile.exists()) ? currentFile.lastModified() : System.currentTimeMillis();
        saveIndicator.setSaved(new Date(ts));
    }

    @Override
    protected void performSave() {
        savePoem();
    }

    @Override
    public String fileExtension() {
        return ".poem";
    }

    @Override
    public void requestInitialFocus() {
        if (poemTitleField.getText() == null || poemTitleField.getText().isEmpty()) {
            poemTitleField.requestFocusInWindow();
        } else {
            poemEditor.requestFocusInWindow();
        }
    }

    @Override
    public void setInitialContent(String content) {
        if (content != null && !content.isEmpty()) {
            poemEditor.setText(content);
            poemEditor.setCaretPosition(content.length());
        }
    }

    @Override
    public void setGuidedQuestions(String[] questions) {
        // Poems don't use guided questions - no-op
    }

    // --- Word highlight for rhymes dock ---
    
    /**
     * Get word boundaries at caret position.
     * @return int[2] with {start, end} or null if no word
     */
    private int[] getWordBoundsAtCaret() {
        try {
            int pos = poemEditor.getCaretPosition();
            String text = poemEditor.getText();
            if (text == null || text.isEmpty()) return null;
            if (pos < 0) pos = 0; if (pos > text.length()) pos = text.length();
            int start = pos;
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            int end = pos;
            while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
            if (end > start) return new int[]{start, end};
        } catch (Throwable ignored) {}
        return null;
    }
    
    /**
     * Update the word highlight overlay when rhymes dock is visible.
     */
    private void updateWordHighlight() {
        clearWordHighlight();
        if (rhymesDock == null || !rhymesDock.isVisible()) return;
        
        int[] bounds = getWordBoundsAtCaret();
        if (bounds == null) return;
        
        try {
            Highlighter hl = poemEditor.getHighlighter();
            currentWordHighlight = hl.addHighlight(bounds[0], bounds[1], wordHighlightPainter);
        } catch (BadLocationException ignored) {}
    }
    
    /**
     * Clear the word highlight overlay.
     */
    private void clearWordHighlight() {
        if (currentWordHighlight != null) {
            poemEditor.getHighlighter().removeHighlight(currentWordHighlight);
            currentWordHighlight = null;
        }
    }
    
    /**
     * Custom highlight painter that draws a gentle rounded outline around text.
     */
    private static class WordOutlineHighlightPainter implements Highlighter.HighlightPainter {
        private final Color color;
        
        public WordOutlineHighlightPainter(Color color) {
            this.color = color;
        }
        
        @Override
        public void paint(Graphics g, int offs0, int offs1, java.awt.Shape bounds, javax.swing.text.JTextComponent c) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                java.awt.Rectangle r0 = c.modelToView(offs0);
                java.awt.Rectangle r1 = c.modelToView(offs1);
                if (r0 == null || r1 == null) { g2.dispose(); return; }
                
                // Calculate bounding rectangle with padding
                int x = r0.x - 2;
                int y = r0.y - 1;
                int w = (r1.x + r1.width) - r0.x + 4;
                int h = r0.height + 2;
                
                // Fill with semi-transparent background
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRoundRect(x, y, w, h, 6, 6);
                
                // Draw outline
                g2.setColor(color);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawRoundRect(x, y, w, h, 6, 6);
                
                g2.dispose();
            } catch (BadLocationException ignored) {}
        }
    }
}

class CustomInspirationDialog extends JDialog {
    private float opacity = 1.0f;
    private Timer fadeOutTimer;

    public CustomInspirationDialog(JFrame parent, String word) {
        super(parent, true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0)); // Transparent background
        setLayout(new BorderLayout());
        
        JLabel label = new JLabel(word, SwingConstants.CENTER);
        label.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        label.setForeground(new Color(40, 40, 40));
        label.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        FrostedGlassPanel contentPanel = new FrostedGlassPanel(new BorderLayout(), 30);
        contentPanel.add(label);
        add(contentPanel);

        pack();
        setLocationRelativeTo(parent);
        
        // Fade out after a delay
        startFadeOut();
    }

    private void startFadeOut() {
        fadeOutTimer = new Timer(50, e -> {
            opacity -= 0.05f;
            if (opacity <= 0) {
                opacity = 0;
                ((Timer)e.getSource()).stop();
                setVisible(false);
                dispose();
            }
            setOpacity(opacity);
        });
        fadeOutTimer.setInitialDelay(1500); // Wait 1.5s before starting to fade
        fadeOutTimer.start();
    }
}
