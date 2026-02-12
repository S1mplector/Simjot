/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
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
import main.core.poetry.PoetryUtils;
import main.core.poetry.ScansionEngine;
import main.core.poetry.SoundDevicesEngine;
import main.core.poetry.ThematicAnalyzer;
import main.core.poetry.VocabularyAnalyzer;
import main.core.security.EncryptionManager;
import main.core.security.crypto.ContentType;
import main.core.security.crypto.CryptoConfig;
import main.core.security.crypto.CryptoException;
import main.core.security.crypto.EncryptedMetadata;
import main.core.service.LastSaveTracker;
import main.core.service.SettingsStore;
import main.core.spelling.AutocorrectDocumentFilter;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.FileIO;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.QuestionMarkButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.editor.CustomFontApplier;
import main.ui.components.editor.CustomFontTextPane;
import main.ui.components.editor.CurrentLineGlowHighlighter;
import main.ui.components.editor.FormattingHotkeyHandler;
import main.ui.components.editor.HandwrittenHeaderStrip;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.LinkManager;
import main.ui.components.editor.PaperFeelViewport;
import main.ui.components.editor.RichTextStyler;
import main.ui.components.fields.TitleDividerField;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.util.EditorUIUtils;
import main.ui.dialog.export.PoemExportDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.PoemBackgroundDialog;
import main.ui.features.editing.UndoRedoManager;
import main.ui.features.poetry.PoetryAnalysisPanel;
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
    protected TitleDividerField poemTitleField;
    protected CustomFontTextPane poemEditor;
    private Insets baseTextMargin;
    private PaperFeelViewport paperViewport;

    private final String[] INSPIRATIONAL_WORDS = {
        "Ethereal", "Ephemeral", "Sonder", "Solitude", "Cascade", "Labyrinthine",
        "Mellifluous", "Nostalgia", "Petrichor", "Ineffable", "Serendipity", "Halcyon",
        "Luminescence", "Redolent", "Somnambulist", "Susurrus", "Opalescent", "Reverie"
    };

    // Helpers
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private SaveIndicatorPanel saveIndicator;
    private volatile boolean isAutosaving = false;
    private NativeAutosaveCoordinator autosaveCoordinator;
    // 'currentFile' is inherited from AbstractEditorPanel

    // UI refs for toggling
    private JPanel toolbarContainer;
    private JPanel toolbarGroup;
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
    // Guided writing overlays
    private boolean guidedModeEnabled = false;
    private GuidedFormConfig guidedFormConfig;
    private Object guidedLineHighlight;
    private final List<Object> guidedRhymeHighlights = new ArrayList<>();
    private final Color guidedLineBaseColor = new Color(255, 196, 120);
    private String baseToolkitHint = "";
    private Font baseToolkitHintFont;
    private Color baseToolkitHintColor;
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
    private javax.swing.Timer analysisSplashTimer;
    private String analysisSplashOriginalStatus;
    private String analysisPendingText = "";
    private javax.swing.Timer statusDebounceTimer;
    private static final int STATUS_DEBOUNCE_MS = 160;
    private String statusPendingText = "";
    private int statusPendingStanzas = 0;
    private int textChangeId = 0;
    private int lastRhymesTextChangeId = -1;
    private String lastRhymesWordLower = null;
    private int lastWordHighlightStart = -1;
    private int lastWordHighlightEnd = -1;
    private int lastWordHighlightTextChangeId = -1;
    private UndoRedoManager poemTitleUndoManager;
    private UndoRedoManager poemContentUndoManager;
    // Poetry toolkit controls
    private javax.swing.JComboBox<String> formPresetBox;
    private JLabel toolkitStatusLabel;
    private JLabel toolkitHintLabel;
    private HandwrittenHeaderStrip headerStamp;

    public PoemPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
    }

    // Helper: determine 0-based line index at caret (by counting newlines before caret)
    private int getCaretLineIndex() {
        return getCaretLineIndex(poemEditor != null ? poemEditor.getText() : null);
    }

    private int getCaretLineIndex(String text) {
        try {
            if (poemEditor == null) return -1;
            int pos = poemEditor.getCaretPosition();
            if (text == null || text.isEmpty()) return -1;
            if (pos < 0) pos = 0;
            if (pos > text.length()) pos = text.length();
            int line = 0;
            for (int i = 0; i < pos; i++) if (text.charAt(i) == '\n') line++;
            return line;
        } catch (Throwable ignored) { return -1; }
    }

    // Helper: determine word at current caret position
    private String getWordAtCaret() {
        return getWordAtCaret(poemEditor != null ? poemEditor.getText() : null);
    }

    private String getWordAtCaret(String text) {
        try {
            if (poemEditor == null) return null;
            int pos = poemEditor.getCaretPosition();
            if (text == null || text.isEmpty()) return null;
            if (pos < 0) pos = 0;
            if (pos > text.length()) pos = text.length();
            int start = pos;
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            int end = pos;
            while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
            if (end > start) return text.substring(start, end);
        } catch (Throwable ignored) {}
        return null;
    }
    
    private static class GuidedFormConfig {
        final String label;
        final int[] syllableTargets;
        final String rhymeScheme;
        final int expectedLines;
        
        GuidedFormConfig(String label, int[] syllableTargets, String rhymeScheme, int expectedLines) {
            this.label = label;
            this.syllableTargets = syllableTargets;
            this.rhymeScheme = rhymeScheme;
            this.expectedLines = expectedLines;
        }
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
        ToolbarMenuIconButton statsToggle = new ToolbarMenuIconButton("", "stats");
        statsToggle.setToolTipText("Toggle Stats Sidebar");
        statsToggle.addActionListener(e -> {
            if (statsPanel != null) {
                boolean vis = !statsPanel.isVisible();
                statsPanel.setVisible(vis);
                statsToggle.setSelected(vis);
                revalidate(); repaint();
            }
        });
        ToolbarMenuIconButton rhymesToggle = new ToolbarMenuIconButton("", "rhyme");
        rhymesToggle.setToolTipText("Toggle Rhymes & Thesaurus Dock");
        rhymesToggle.addActionListener(e -> {
            if (rhymesDock != null) {
                boolean vis = !rhymesDock.isVisible();
                rhymesDock.setVisible(vis);
                rhymesToggle.setSelected(vis);
                if (vis) {
                    String text = poemEditor.getText();
                    String w = getWordAtCaret(text);
                    updateRhymesDock(w, text, true);
                    updateWordHighlight(text);
                } else {
                    clearWordHighlight();
                }
                revalidate(); repaint();
            }
        });
        ToolbarMenuIconButton settingsBtn = new ToolbarMenuIconButton("", "backgroundoptions");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            PoemBackgroundDialog dialog = new PoemBackgroundDialog((java.awt.Frame)SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        // Distraction-free toggle
        ToolbarMenuIconButton dfBtn = new ToolbarMenuIconButton("", "fullscreen");
        dfBtn.setToolTipText("Distraction-Free Mode");
        dfBtn.addActionListener(e -> toggleDistractionFree());
        // Export button (advanced)
        ToolbarMenuIconButton exportBtn = new ToolbarMenuIconButton("", "export");
        exportBtn.setToolTipText("Export poem (Markdown/HTML/TXT/PNG)");
        exportBtn.addActionListener(e -> exportPoem());
        // Analysis button - opens detailed poetry analysis panel
        ToolbarMenuIconButton analyzeBtn = new ToolbarMenuIconButton("", "analyze");
        analyzeBtn.setToolTipText("Open Detailed Poetry Analysis");
        analyzeBtn.addActionListener(e -> showPoemAnalysis());
        rightToolbar.add(statsToggle);
        rightToolbar.add(rhymesToggle);
        rightToolbar.add(analyzeBtn);
        rightToolbar.add(exportBtn);
        rightToolbar.add(dfBtn);
        rightToolbar.add(settingsBtn);
        QuestionMarkButton helpBtn = new QuestionMarkButton();
        helpBtn.setToolTipText("Poem editor tips");
        helpBtn.addActionListener(e -> showHelpDialog());
        rightToolbar.add(Box.createHorizontalStrut(6));
        rightToolbar.add(helpBtn);
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
                    int size = poemEditor.getFont() != null ? poemEditor.getFont().getSize() : SettingsStore.get().getPoemFontSize();
                    CustomFontApplier.applyToTextPane(poemEditor, fontName, size);
                    applyParagraphFontToAll();
                },
                (size) -> {
                    poemEditor.setFont(poemEditor.getFont().deriveFont(size.floatValue()));
                    applyParagraphFontToAll();
                    SettingsStore.get().setPoemFontSize(size);
                    SettingsStore.get().save();
                },
                this::applyLineSpacing,
                rightToolbar,
                () -> main.ui.components.editor.RichTextStyler.toggleBulletList(poemEditor),
                () -> main.ui.components.editor.RichTextStyler.toggleNumberedList(poemEditor),
                () -> RichTextStyler.applyHeaderToSelection(poemEditor),
                null
        );
        toolbarContainer = sharedToolbar.getContainer();
        poemTitleField = sharedToolbar.getTitleField();
        if (SettingsStore.get().isEditorHeaderStampEnabled()) {
            String stamp = buildHeaderStampText();
            if (stamp != null && !stamp.isBlank()) {
                headerStamp = new HandwrittenHeaderStrip();
                headerStamp.setStampText(stamp);
                headerStamp.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        toolbarGroup = new JPanel();
        toolbarGroup.setOpaque(false);
        toolbarGroup.setLayout(new BoxLayout(toolbarGroup, BoxLayout.Y_AXIS));
        if (headerStamp != null) {
            JPanel stampRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stampRow.setOpaque(false);
            stampRow.setBorder(BorderFactory.createEmptyBorder(4, 52, 2, 12));
            stampRow.add(headerStamp);
            toolbarGroup.add(stampRow);
            toolbarGroup.add(Box.createVerticalStrut(4));
        }
        toolbarGroup.add(toolbarContainer);
        add(toolbarGroup, BorderLayout.NORTH);

        // Distraction-free header: only Back button, no other controls
        dfHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dfHeader.setOpaque(false);
        dfHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        // Use the same back icon, but only exit fullscreen (do not navigate away)
        ToolbarMenuIconButton dfBack = new ToolbarMenuIconButton("", "back");
        dfBack.setToolTipText("Exit Fullscreen");
        dfBack.addActionListener(e -> toggleDistractionFree());
        dfHeader.add(dfBack);

        // --- Center Panel: Poem Text Area with a cursive feel ---
        // Glass effect with adjustable opacity from settings
        JPanel textWrapper = new FrostedGlassPanel(new BorderLayout(), 16) {
            @Override
            protected float getOpacityScale() {
                return SettingsStore.get().getPoemGlassOpacity();
            }
        };
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        poemEditor = new CustomFontTextPane();
        poemEditor.setOpaque(false);
        poemEditor.setForeground(new Color(40, 40, 40));
        baseTextMargin = poemEditor.getMargin();
        applyPaperFeelInsets();

        // Load font settings from Appearance settings
        String fontFamily = SettingsStore.get().getEditorFontFamily();
        int savedFontSize = SettingsStore.get().getPoemFontSize();
        String lineSpacingStr = SettingsStore.get().getEditorLineSpacing();
        CustomFontApplier.applyToTextPane(poemEditor, fontFamily, savedFontSize);
        if (poemTitleField != null) {
            poemTitleField.setFont(CustomFontApplier.resolveUiFont(fontFamily, savedFontSize));
            poemTitleField.setPlaceholder(null);
        }
        // Apply line spacing from settings
        float spacing = resolveLineSpacing(lineSpacingStr);
        javax.swing.SwingUtilities.invokeLater(() -> {
            StyledDocument doc = poemEditor.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            applyParagraphRhythm(attrs);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        });

        // Enable rich image paste & drag-and-drop into the poem editor
        ImagePasteManager.install(
                poemEditor,
                () -> new File(journalFolder, "attachments"),
                800 // max width in pixels for inserted images
        );

        // Install native-accelerated formatting hotkeys (Cmd/Ctrl + B/I/U, Cmd/Ctrl+Shift+S)
        FormattingHotkeyHandler.install(poemEditor,
                () -> RichTextStyler.toggleBold(poemEditor),
                () -> RichTextStyler.toggleItalic(poemEditor),
                () -> RichTextStyler.toggleUnderline(poemEditor),
                () -> RichTextStyler.toggleStrike(poemEditor));
        installHeaderShortcut(poemEditor);

        // Enable link detection and styling on paste (deferred until displayable)
        LinkManager.installWhenReady(poemEditor);
        CurrentLineGlowHighlighter.install(poemEditor, () -> SettingsStore.get().isEditorTypographyPolishEnabled());

        JScrollPane scrollPane = new JScrollPane(poemEditor);
        scrollPane.setOpaque(false);
        paperViewport = new PaperFeelViewport(poemEditor);
        paperViewport.setPaperFeelEnabled(SettingsStore.get().isEditorPaperFeelEnabled());
        paperViewport.setView(poemEditor);
        scrollPane.setViewport(paperViewport);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
                handlePoemDocumentChange(stanzaLabel);
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                handlePoemDocumentChange(stanzaLabel);
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                handlePoemDocumentChange(stanzaLabel);
            }
        });
        // Caret listener to update rhyme/synonyms for current word
        poemEditor.addCaretListener(e -> handlePoemCaretUpdate());
        // Autosave on title change as well
        poemTitleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
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

        // --- Autosave wiring (native C++ coordinator) ---
        int delayMs = SettingsStore.get().getAutosaveDelayMs();
        if (delayMs > 0) {
            String filePath = (currentFile != null) ? currentFile.getAbsolutePath() : null;
            autosaveCoordinator = new NativeAutosaveCoordinator(filePath, delayMs,
                    this::savePoem,
                    () -> { isAutosaving = true; if (saveIndicator != null) saveIndicator.setSaving(); },
                    () -> { 
                        updateSaveIndicatorFromCurrentFile();
                        isAutosaving = false; 
                    });
        } else {
            autosaveCoordinator = null; // autosave disabled
        }

        // Initial metrics
        updateMetrics(stanzaLabel);
        refreshToolkitStatus();
        // Run initial poetry analysis
        schedulePoetryAnalysis();
        guidedFormConfig = guidedConfigForPreset((String) formPresetBox.getSelectedItem());
    }

    private void updateStanzaCount(JLabel label, String text) {
        int stanzas = countStanzas(text);
        label.setText("Stanzas: " + stanzas);
        scheduleStatusUpdate(text, stanzas);
    }

    private void updateMetrics(JLabel stanzaLabel) {
        String text = poemEditor.getText();
        updateStanzaCount(stanzaLabel, text);
    }

    private void updateRhymesDock(String word, String text, boolean force) {
        if (rhymesDock == null || !rhymesDock.isVisible()) return;
        String normalized = (word == null) ? null : word.toLowerCase(Locale.ROOT);
        if (!force) {
            if (normalized == null) {
                if (lastRhymesWordLower == null && lastRhymesTextChangeId == textChangeId) return;
            } else if (normalized.equals(lastRhymesWordLower) && lastRhymesTextChangeId == textChangeId) {
                return;
            }
        }
        rhymesDock.update(word, text);
        lastRhymesWordLower = normalized;
        lastRhymesTextChangeId = textChangeId;
    }

    private void handlePoemDocumentChange(JLabel stanzaLabel) {
        if (poemEditor == null) return;
        String text = poemEditor.getText();
        textChangeId++;
        updateStanzaCount(stanzaLabel, text);
        if (statsPanel != null) statsPanel.updateFromText(text);
        updateRhymesDock(getWordAtCaret(text), text, true);
        int caretLine = getCaretLineIndex(text);
        if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(caretLine);
        if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
        updateGuidedWriting(text, caretLine);
        schedulePoetryAnalysis(text);
    }

    private void handlePoemCaretUpdate() {
        if (poemEditor == null) return;
        String text = poemEditor.getText();
        int caretLine = getCaretLineIndex(text);
        updateRhymesDock(getWordAtCaret(text), text, false);
        updateWordHighlight(text);
        if (statsPanel != null && statsPanel.isVisible()) {
            statsPanel.setHighlightedLine(caretLine);
        }
        updateGuidedWriting(text, caretLine);
    }

    private int countStanzas(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        boolean inStanza = false;
        boolean lineHasContent = false;
        int len = text.length();
        for (int i = 0; i <= len; i++) {
            char c = (i < len) ? text.charAt(i) : '\n';
            if (c == '\r') continue;
            if (c == '\n') {
                if (lineHasContent) {
                    if (!inStanza) {
                        count++;
                        inStanza = true;
                    }
                } else {
                    inStanza = false;
                }
                lineHasContent = false;
            } else if (!Character.isWhitespace(c)) {
                lineHasContent = true;
            }
        }
        return count;
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

        RoundedToggleButton guidedToggle = new RoundedToggleButton("Guided");
        guidedToggle.setPreferredSize(new Dimension(100, 28));
        guidedToggle.setToolTipText("Highlight rhyme targets and form cues");
        guidedToggle.addActionListener(e -> setGuidedMode(guidedToggle.isSelected()));
        row.add(guidedToggle);

        row.add(Box.createHorizontalStrut(10));
        toolkitStatusLabel = new JLabel("Active form: None • Detected: —");
        toolkitStatusLabel.setForeground(new Color(60, 60, 60));
        toolkitStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        row.add(toolkitStatusLabel);

        toolkitHintLabel = new JLabel("");
        toolkitHintLabel.setForeground(new Color(90, 90, 90));
        toolkitHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        toolkitHintLabel.setVisible(false);
        baseToolkitHint = "";
        baseToolkitHintFont = toolkitHintLabel.getFont();
        baseToolkitHintColor = toolkitHintLabel.getForeground();

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
        guidedFormConfig = guidedConfigForPreset(preset);
        updateGuidedWriting();
    }

    private void setToolkitHint(String text) {
        baseToolkitHint = text == null ? "" : text;
        updateGuidedHint(null);
    }
    
    private void updateGuidedHint(String guidedText) {
        if (toolkitHintLabel == null) return;
        String text;
        Font font = baseToolkitHintFont;
        Color color = baseToolkitHintColor;
        if (!guidedModeEnabled || guidedText == null || guidedText.isBlank()) {
            text = baseToolkitHint;
        } else {
            if (baseToolkitHint == null || baseToolkitHint.isBlank()) {
                text = guidedText;
            } else {
                text = baseToolkitHint + "  |  " + guidedText;
            }
            if (baseToolkitHintFont != null) {
                int style = baseToolkitHintFont.getStyle() | Font.BOLD;
                font = baseToolkitHintFont.deriveFont(style);
            }
            color = new Color(80, 70, 60);
        }
        if (text == null || text.isBlank()) {
            toolkitHintLabel.setText("");
            toolkitHintLabel.setVisible(false);
            return;
        }
        toolkitHintLabel.setVisible(true);
        toolkitHintLabel.setText(text);
        if (font != null) toolkitHintLabel.setFont(font);
        if (color != null) toolkitHintLabel.setForeground(color);
    }
    
    private GuidedFormConfig guidedConfigForPreset(String preset) {
        if (preset == null) return null;
        switch (preset) {
            case "Haiku (5/7/5)":
                return new GuidedFormConfig("Haiku", new int[]{5, 7, 5}, null, 3);
            case "Limerick (9/9/6/6/9)":
                return new GuidedFormConfig("Limerick", new int[]{9, 9, 6, 6, 9}, "AABBA", 5);
            case "Sonnet (14x10)": {
                int[] tenBeat = new int[14];
                for (int i = 0; i < tenBeat.length; i++) tenBeat[i] = 10;
                return new GuidedFormConfig("Sonnet", tenBeat, "ABABCDCDEFEFGG", 14);
            }
            case "Octosyllabic quatrain":
                return new GuidedFormConfig("Octosyllabic", new int[]{8, 8, 8, 8}, "ABAB", 4);
            default:
                return null;
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
    
    private void setGuidedMode(boolean enabled) {
        guidedModeEnabled = enabled;
        updateGuidedWriting();
    }
    
    private static class GuidedLineState {
        final int contentLineOrdinal;
        final int totalContentLines;
        final List<Integer> contentLineToTextLine;
        final boolean caretLineBlank;
        
        GuidedLineState(int contentLineOrdinal, int totalContentLines,
                        List<Integer> contentLineToTextLine, boolean caretLineBlank) {
            this.contentLineOrdinal = contentLineOrdinal;
            this.totalContentLines = totalContentLines;
            this.contentLineToTextLine = contentLineToTextLine;
            this.caretLineBlank = caretLineBlank;
        }
    }
    
    private GuidedLineState buildGuidedLineState(String text, int caretLineIndex) {
        String[] lines = text.split("\n", -1);
        List<Integer> contentLineToTextLine = new ArrayList<>();
        int contentOrdinal = 0;
        int caretOrdinal = -1;
        boolean caretBlank = true;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean blank = line == null || line.trim().isEmpty();
            if (!blank) {
                contentLineToTextLine.add(i);
                if (i == caretLineIndex) {
                    caretOrdinal = contentOrdinal;
                    caretBlank = false;
                }
                contentOrdinal++;
            } else if (i == caretLineIndex) {
                caretOrdinal = contentOrdinal;
                caretBlank = true;
            }
        }
        
        if (caretLineIndex >= lines.length) {
            caretOrdinal = contentOrdinal;
            caretBlank = true;
        }
        
        return new GuidedLineState(caretOrdinal, contentOrdinal, contentLineToTextLine, caretBlank);
    }
    
    private void updateGuidedWriting() {
        updateGuidedWriting(poemEditor != null ? poemEditor.getText() : null, -1);
    }

    private void updateGuidedWriting(String text, int caretLineIndex) {
        clearGuidedHighlights();
        if (!guidedModeEnabled) {
            updateGuidedHint(null);
            return;
        }
        if (guidedFormConfig == null) {
            updateGuidedHint("Guided mode needs a form preset.");
            return;
        }
        if (poemEditor == null) return;

        if (text == null) {
            updateGuidedHint(null);
            return;
        }

        int caretLine = (caretLineIndex >= 0) ? caretLineIndex : getCaretLineIndex(text);
        if (caretLine < 0) {
            updateGuidedHint(null);
            return;
        }

        GuidedLineState state = buildGuidedLineState(text, caretLine);
        if (guidedFormConfig.expectedLines > 0 && state.contentLineOrdinal >= guidedFormConfig.expectedLines) {
            updateGuidedHint("Guided: form complete (" + guidedFormConfig.expectedLines + " lines).");
            return;
        }
        if (state.contentLineOrdinal < 0) {
            updateGuidedHint(null);
            return;
        }
        
        int lineOrdinal = Math.max(0, state.contentLineOrdinal);
        int targetSyllables = targetSyllablesFor(guidedFormConfig, lineOrdinal);
        char rhymeLetter = rhymeLetterFor(guidedFormConfig, lineOrdinal);
        List<Integer> rhymeLines = new ArrayList<>();
        String lineText = getLineText(text, caretLine);
        int currentSyllables = 0;
        try {
            if (lineText != null && !lineText.isBlank()) {
                currentSyllables = PoetryUtils.countSyllablesInLine(lineText);
            }
        } catch (Throwable ignored) {}
        
        try {
            int[] lineBounds = getLineBounds(text, caretLine);
            if (lineBounds != null) {
                int start = lineBounds[0];
                int end = lineBounds[1];
                if (start == end && start < text.length()) end = start + 1;
                if (end > start) {
                    String badge = buildGuideBadge(targetSyllables, rhymeLetter, currentSyllables);
                    Color accent = rhymeLetter != 0 ? colorForRhymeLetter(rhymeLetter) : guidedLineBaseColor;
                    guidedLineHighlight = poemEditor.getHighlighter()
                            .addHighlight(start, end,
                                    new GuidedLineHighlightPainter(guidedLineBaseColor, accent, badge));
                }
            }
        } catch (BadLocationException ignored) {}
        
        if (rhymeLetter != 0) {
            for (int i = 0; i < lineOrdinal && i < state.contentLineToTextLine.size(); i++) {
                if (rhymeLetterFor(guidedFormConfig, i) != rhymeLetter) continue;
                int textLine = state.contentLineToTextLine.get(i);
                int[] bounds = getLastWordBounds(text, textLine);
                if (bounds != null && bounds[1] > bounds[0]) {
                    try {
                        Object h = poemEditor.getHighlighter()
                                .addHighlight(bounds[0], bounds[1],
                                        new GuidedAnchorHighlightPainter(colorForRhymeLetter(rhymeLetter),
                                                String.valueOf(rhymeLetter)));
                        guidedRhymeHighlights.add(h);
                        rhymeLines.add(i + 1);
                    } catch (BadLocationException ignored) {}
                }
            }
        }
        
        String guidedHint = buildGuidedHint(guidedFormConfig, state, lineOrdinal, targetSyllables,
                currentSyllables, rhymeLetter, rhymeLines);
        updateGuidedHint(guidedHint);
    }
    
    private void clearGuidedHighlights() {
        if (poemEditor == null) return;
        Highlighter hl = poemEditor.getHighlighter();
        if (guidedLineHighlight != null) {
            hl.removeHighlight(guidedLineHighlight);
            guidedLineHighlight = null;
        }
        for (Object h : guidedRhymeHighlights) {
            hl.removeHighlight(h);
        }
        guidedRhymeHighlights.clear();
    }
    
    private String buildGuidedHint(GuidedFormConfig config, GuidedLineState state, int lineOrdinal,
                                  int targetSyllables, int currentSyllables,
                                  char rhymeLetter, List<Integer> rhymeLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Guided: ");
        if (state.caretLineBlank) sb.append("next ");
        sb.append("line ").append(lineOrdinal + 1);
        if (config.expectedLines > 0) sb.append("/").append(config.expectedLines);
        if (targetSyllables > 0) {
            sb.append(", target ").append(targetSyllables).append(" syllables");
            int diff = targetSyllables - Math.max(0, currentSyllables);
            sb.append(" (current ").append(Math.max(0, currentSyllables));
            if (diff > 0) {
                sb.append(", ").append(diff).append(" to go");
            } else if (diff < 0) {
                sb.append(", ").append(-diff).append(" over");
            }
            sb.append(")");
        }
        if (rhymeLetter != 0) {
            sb.append(", rhyme ").append(rhymeLetter);
            if (rhymeLines.isEmpty()) {
                sb.append(" (establish)");
            } else {
                sb.append(" with line");
                if (rhymeLines.size() > 1) sb.append("s ");
                else sb.append(" ");
                sb.append(joinLineNumbers(rhymeLines));
            }
        }
        return sb.toString();
    }
    
    private String buildGuideBadge(int targetSyllables, char rhymeLetter, int currentSyllables) {
        if (targetSyllables <= 0 && rhymeLetter == 0) return null;
        String syllablePart = null;
        if (targetSyllables > 0) {
            int current = Math.max(0, currentSyllables);
            syllablePart = current + "/" + targetSyllables;
        } else if (currentSyllables > 0) {
            syllablePart = String.valueOf(currentSyllables);
        }
        String rhymePart = rhymeLetter != 0 ? String.valueOf(rhymeLetter) : null;
        if (syllablePart != null && rhymePart != null) return syllablePart + " " + rhymePart;
        if (syllablePart != null) return syllablePart;
        return rhymePart;
    }
    
    private Color colorForRhymeLetter(char letter) {
        return switch (Character.toUpperCase(letter)) {
            case 'A' -> new Color(90, 140, 220);
            case 'B' -> new Color(90, 170, 120);
            case 'C' -> new Color(190, 130, 90);
            case 'D' -> new Color(150, 110, 200);
            case 'E' -> new Color(200, 120, 140);
            default -> new Color(140, 140, 140);
        };
    }
    
    private String joinLineNumbers(List<Integer> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
    
    private int targetSyllablesFor(GuidedFormConfig config, int lineOrdinal) {
        if (config == null || config.syllableTargets == null || config.syllableTargets.length == 0) return 0;
        return config.syllableTargets[lineOrdinal % config.syllableTargets.length];
    }
    
    private char rhymeLetterFor(GuidedFormConfig config, int lineOrdinal) {
        if (config == null || config.rhymeScheme == null || config.rhymeScheme.isBlank()) return 0;
        if (lineOrdinal < 0 || lineOrdinal >= config.rhymeScheme.length()) return 0;
        return config.rhymeScheme.charAt(lineOrdinal);
    }
    
    private int[] getLineBounds(String text, int lineIndex) {
        if (text == null || lineIndex < 0) return null;
        int line = 0;
        int start = 0;
        int len = text.length();
        for (int i = 0; i < len && line < lineIndex; i++) {
            if (text.charAt(i) == '\n') {
                start = i + 1;
                line++;
            }
        }
        if (line != lineIndex) return null;
        int end = text.indexOf('\n', start);
        if (end < 0) end = len;
        return new int[]{start, end};
    }

    private String getLineText(String text, int lineIndex) {
        if (text == null || text.isEmpty()) return "";
        int[] bounds = getLineBounds(text, lineIndex);
        if (bounds == null) return "";
        int start = Math.max(0, bounds[0]);
        int end = Math.min(text.length(), bounds[1]);
        if (end <= start) return "";
        return text.substring(start, end);
    }
    
    private int[] getLastWordBounds(String text, int lineIndex) {
        int[] lineBounds = getLineBounds(text, lineIndex);
        if (lineBounds == null) return null;
        int start = lineBounds[0];
        int end = lineBounds[1];
        int i = end - 1;
        while (i >= start && !isWordChar(text.charAt(i))) i--;
        if (i < start) return null;
        int wordEnd = i + 1;
        while (i >= start && isWordChar(text.charAt(i))) i--;
        int wordStart = i + 1;
        return new int[]{wordStart, wordEnd};
    }
    
    private boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'';
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
            
            // Lightweight splash: show staged analysis steps while work begins
            showAnalysisSplash();

            try {
                // Try unified native analysis first (much faster - single call instead of 4+)
                NativeAccess.PoetryAnalysisResult result = NativeAccess.poetryAnalyzeAll(text);
                if (result != null) {
                    // Use native results
                    vocabText = String.format("TTR: %.0f%%", Math.max(0.0, result.ttr));
                    themeText = "Theme: " + (result.dominantTheme.isEmpty() ? "General" : 
                                     Character.toUpperCase(result.dominantTheme.charAt(0)) + result.dominantTheme.substring(1));
                    soundText = "Sound: " + result.soundDeviceCount;
                    meterText = "Meter: " + (result.dominantMeter.isEmpty() ? "Free" : 
                                     (result.dominantMeter.length() > 12 ? result.dominantMeter.substring(0, 12) : result.dominantMeter));
                } else {
                    // Fallback to individual native methods (using original logic)
                    vocabText = "TTR: ...";
                    themeText = "Theme: ...";
                    soundText = "Sound: ...";
                    meterText = "Meter: ...";
                    
                    // Run individual analysis (original code logic)
                    double ttr = -1.0;
                    if (NativeAccess.hasHaskellPoetrySupport()) {
                        String stats = NativeAccess.hsGetVocabStats(text);
                        if (stats != null && !stats.isBlank()) {
                            String[] parts = stats.split(",");
                            if (parts.length >= 2) {
                                try {
                                    int total = Integer.parseInt(parts[0].trim());
                                    int unique = Integer.parseInt(parts[1].trim());
                                    if (total > 0) ttr = (double) unique / total * 100;
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    if (ttr < 0) {
                        int total = NativeAccess.poetryAnalyzeVocab(text);
                        NativeAccess.PoetryVocabStats stats = NativeAccess.poetryGetVocabStats();
                        if (total > 0 && stats != null && stats.totalWords() > 0) {
                            ttr = (double) stats.uniqueWords() / stats.totalWords() * 100;
                        }
                    }
                    if (ttr < 0) {
                        List<String> words = NativeAccess.textExtractWords(text);
                        if (words == null || words.isEmpty()) {
                            String trimmed = text.trim();
                            if (!trimmed.isEmpty()) {
                                String[] split = trimmed.split("\\s+");
                                words = new ArrayList<>(split.length);
                                Collections.addAll(words, split);
                            }
                        }
                        if (words == null || words.isEmpty()) {
                            ttr = 0.0;
                        } else {
                            java.util.Set<String> unique = new java.util.HashSet<>();
                            for (String w : words) unique.add(w.toLowerCase(java.util.Locale.ROOT));
                            ttr = (double) unique.size() / words.size() * 100;
                        }
                    }
                    vocabText = String.format("TTR: %.0f%%", Math.max(0.0, ttr));
                    
                    // Simplified theme analysis
                    String dominantTheme = null;
                    int themeCount = NativeAccess.poetryAnalyzeThemes(text);
                    if (themeCount > 0) {
                        String themes = NativeAccess.poetryGetThemes();
                        if (themes != null && !themes.isBlank()) {
                            double bestScore = Double.NEGATIVE_INFINITY;
                            for (String theme : themes.split("\n")) {
                                if (theme.isBlank()) continue;
                                double score = NativeAccess.poetryGetThemeScore(theme);
                                if (score > bestScore) {
                                    bestScore = score;
                                    dominantTheme = theme;
                                }
                            }
                        }
                    }
                    if (dominantTheme == null || dominantTheme.isBlank()) {
                        dominantTheme = "General";
                    }
                    themeText = "Theme: " + (Character.toUpperCase(dominantTheme.charAt(0)) + dominantTheme.substring(1));
                    
                    // Simplified sound analysis
                    int devices = 0;
                    if (NativeAccess.hasHaskellPoetrySupport()) {
                        devices = NativeAccess.hsAnalyzeSoundDevices(text);
                    }
                    if (devices <= 0) {
                        devices = NativeAccess.poetryAnalyzeSounds(text);
                    }
                    soundText = "Sound: " + devices;
                    
                    // Simplified meter analysis
                    String meter = null;
                    if (NativeAccess.hasHaskellPoetrySupport()) {
                        meter = NativeAccess.hsGetMeterName(text);
                    }
                    if (meter == null || meter.isBlank()) {
                        int lines = NativeAccess.poetryAnalyzeMeter(text);
                        if (lines > 0) {
                            meter = NativeAccess.poetryDetectMeter();
                        }
                    }
                    if (meter == null || meter.isEmpty()) meter = "Free";
                    else if (meter.length() > 12) meter = meter.substring(0, 12);
                    meterText = "Meter: " + meter;
                }
            } catch (Throwable ex) {
                System.err.println("Poetry analysis error: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                stopAnalysisSplash();
            }
            
            // Update UI on EDT
            final String finalVocabText = vocabText;
            final String finalThemeText = themeText;
            final String finalSoundText = soundText;
            final String finalMeterText = meterText;
            
            SwingUtilities.invokeLater(() -> {
                if (vocabLabel != null) vocabLabel.setText(finalVocabText);
                if (themeLabel != null) themeLabel.setText(finalThemeText);
                if (soundLabel != null) soundLabel.setText(finalSoundText);
                if (meterLabel != null) meterLabel.setText(finalMeterText);
            });
        }).start();
    }

    private void showAnalysisSplash() {
        SwingUtilities.invokeLater(() -> {
            analysisSplashOriginalStatus = statusLabel != null ? statusLabel.getText() : null;
            if (statusLabel != null) statusLabel.setText("Starting analysis…");
            String[] steps = new String[] {
                "Analyzing prosody…",
                "Analyzing phonetics…",
                "Analyzing vocabulary…",
                "Analyzing themes…",
                "Analyzing meter…",
                "Finishing up…"
            };
            if (analysisSplashTimer != null && analysisSplashTimer.isRunning()) {
                analysisSplashTimer.stop();
            }
            analysisSplashTimer = new javax.swing.Timer(500, new java.awt.event.ActionListener() {
                int idx = 0;
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (statusLabel != null && idx < steps.length) {
                        statusLabel.setText(steps[idx]);
                    }
                    idx++;
                    if (idx >= steps.length) {
                        analysisSplashTimer.stop();
                    }
                }
            });
            analysisSplashTimer.setRepeats(true);
            analysisSplashTimer.start();
        });
    }

    private void stopAnalysisSplash() {
        SwingUtilities.invokeLater(() -> {
            if (analysisSplashTimer != null && analysisSplashTimer.isRunning()) {
                analysisSplashTimer.stop();
            }
            if (analysisSplashOriginalStatus != null && statusLabel != null) {
                statusLabel.setText(analysisSplashOriginalStatus);
            }
        });
    }

    private void schedulePoetryAnalysis() {
        schedulePoetryAnalysis(poemEditor != null ? poemEditor.getText() : "");
    }

    private void schedulePoetryAnalysis(String text) {
        analysisPendingText = (text == null) ? "" : text;
        if (analysisDebounceTimer == null) {
            analysisDebounceTimer = new javax.swing.Timer(500, e -> {
                analysisDebounceTimer.stop();
                runPoetryAnalysis(analysisPendingText);
            });
            analysisDebounceTimer.setRepeats(false);
        }
        analysisDebounceTimer.restart();
    }

    private void scheduleStatusUpdate(String text, int stanzas) {
        statusPendingText = (text == null) ? "" : text;
        statusPendingStanzas = stanzas;
        if (statusDebounceTimer == null) {
            statusDebounceTimer = new javax.swing.Timer(STATUS_DEBOUNCE_MS, e -> {
                statusDebounceTimer.stop();
                updateStatus(statusPendingText, statusPendingStanzas);
            });
            statusDebounceTimer.setRepeats(false);
        }
        statusDebounceTimer.restart();
    }

    private void updateStatus(String text, int stanzas) {
        String safeText = (text == null) ? "" : text;
        int words = 0;
        int chars = safeText.length();
        Integer nativeWords = NativeAccess.textWordCount(safeText);
        if (nativeWords != null) {
            words = nativeWords;
        } else {
            words = countWordsFast(safeText);
        }
        Integer nativeChars = NativeAccess.textCharCount(safeText, true);
        if (nativeChars != null) chars = nativeChars;
        int minutes = Math.max(0, (int)Math.ceil(words / 200.0));
        if (statusLabel != null) {
            statusLabel.setText(String.format("Words: %d • Chars: %d • Stanzas: %d • ~%d min read", words, chars, stanzas, minutes));
        }
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
            int wordCount = countWords(title) + countWords(content);
            if (EncryptionManager.isEncryptionEnabled()) {
                String password = EncryptionManager.getPasswordForUse(this, !isAutosaving);
                if (password == null || password.isBlank()) {
                    if (saveIndicator != null) saveIndicator.setError("Encryption locked");
                    return;
                }
                CryptoConfig config = CryptoConfig.forPoems()
                        .withIdentifier(EncryptedMetadata.encodePoem(title, System.currentTimeMillis(), wordCount));
                try {
                    data = EncryptionManager.encrypt(data, password, ContentType.POEM, config);
                } catch (CryptoException ex) {
                    if (saveIndicator != null) saveIndicator.setError("Encrypt failed");
                    SwingUtilities.invokeLater(() -> CustomMessageDialog.display(this, "Encryption", ex.getUserMessage(), true));
                    return;
                }
            }
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

            updateSaveIndicatorFromCurrentFile();
            
            // Toast notification for manual saves only
            if (!isAutosaving) {
                main.ui.components.toast.ToastOverlay.success("Poem saved");
            }
            
            // Don't clear fields - keep content like NewEntryPanel does
            // This allows continuous editing of the same poem
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving poem.", true).showDialog();
            if (saveIndicator != null) saveIndicator.setError("Error saving");
            main.ui.components.toast.ToastOverlay.error("Failed to save poem");
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
        float spacing = resolveLineSpacing(val);
        StyledDocument doc = poemEditor.getStyledDocument();
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attrs, spacing);
        applyParagraphRhythm(attrs);
        doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
    }

    private void showHelpDialog() {
        String message = "<html><body style='text-align:left;'>"
                + "<b>Poem editor features</b><br>"
                + "• Metering, vocabulary, and theme analysis panels.<br>"
                + "• Rhymes dock with quick lookup and suggestions.<br>"
                + "• Export to Markdown, HTML, TXT, or PNG.<br>"
                + "• Background settings and distraction-free mode in the toolbar."
                + "</body></html>";
        CustomMessageDialog.display(this, "Poem Editor", message, false);
    }

    private void installHeaderShortcut(JComponent editor) {
        try {
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            editor.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_H, meta | InputEvent.SHIFT_DOWN_MASK), "header-selection");
            editor.getActionMap().put("header-selection", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (poemEditor.getSelectionStart() != poemEditor.getSelectionEnd()) {
                        RichTextStyler.applyHeaderToSelection(poemEditor);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private float resolveLineSpacing(String val) {
        float spacing = switch (val) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };
        if (SettingsStore.get().isEditorTypographyPolishEnabled()) {
            spacing = Math.min(0.6f, spacing + 0.08f);
        }
        return spacing;
    }

    private void applyParagraphRhythm(MutableAttributeSet attrs) {
        if (SettingsStore.get().isEditorTypographyPolishEnabled()) {
            StyleConstants.setSpaceAbove(attrs, 2f);
            StyleConstants.setSpaceBelow(attrs, 6f);
        } else {
            StyleConstants.setSpaceAbove(attrs, 0f);
            StyleConstants.setSpaceBelow(attrs, 0f);
        }
    }

    private void applyPaperFeelInsets() {
        if (poemEditor == null) return;
        if (SettingsStore.get().isEditorPaperFeelEnabled()) {
            poemEditor.setMargin(new Insets(18, 64, 18, 32));
        } else if (baseTextMargin != null) {
            poemEditor.setMargin(baseTextMargin);
        }
    }

    private String buildHeaderStampText() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"));
        String location = SettingsStore.get().getEditorHeaderStampLocation();
        if (location != null && !location.isBlank()) {
            return date + " - " + location.trim();
        }
        return date;
    }

    private void toggleDistractionFree() {
        distractionFree = !distractionFree;
        // Swap toolbar with minimal df header in NORTH
        if (distractionFree) {
            // Entering distraction-free: remove main toolbar, add dfHeader
            try { remove(toolbarGroup != null ? toolbarGroup : toolbarContainer); } catch (Throwable ignored) {}
            add(dfHeader, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(false);
        } else {
            // Exiting distraction-free: remove dfHeader, restore toolbar
            try { remove(dfHeader); } catch (Throwable ignored) {}
            add(toolbarGroup != null ? toolbarGroup : toolbarContainer, BorderLayout.NORTH);
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
        try (BufferedReader reader = openPoemReader(poemFile)) {
            if (reader == null) return;
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
        } catch (CryptoException ex) {
            CustomMessageDialog.display(this, "Encryption", ex.getUserMessage(), true);
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

    private BufferedReader openPoemReader(File file) throws IOException, CryptoException {
        byte[] data = EncryptionManager.readFileMaybeDecrypt(file, this, true);
        if (data == null) return null;
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        int nativeCount = NativeAccess.countWords(text);
        if (nativeCount >= 0) return nativeCount;
        return countWordsFast(text);
    }

    private static int countWordsFast(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        boolean inWord = false;
        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }

    private void updateSaveIndicatorFromCurrentFile() {
        if (saveIndicator == null) return;
        long ts = (currentFile != null && currentFile.exists()) ? currentFile.lastModified() : System.currentTimeMillis();
        saveIndicator.setSaved(new Date(ts));
    }

    @Override
    public void removeNotify() {
        try { if (autosaveCoordinator != null) autosaveCoordinator.shutdown(); } catch (Throwable ignored) {}
        super.removeNotify();
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
        return getWordBoundsAtCaret(poemEditor != null ? poemEditor.getText() : null);
    }

    private int[] getWordBoundsAtCaret(String text) {
        try {
            if (poemEditor == null) return null;
            int pos = poemEditor.getCaretPosition();
            if (text == null || text.isEmpty()) return null;
            if (pos < 0) pos = 0;
            if (pos > text.length()) pos = text.length();
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
        updateWordHighlight(poemEditor != null ? poemEditor.getText() : null);
    }

    private void updateWordHighlight(String text) {
        if (rhymesDock == null || !rhymesDock.isVisible()) {
            clearWordHighlight();
            return;
        }

        int[] bounds = getWordBoundsAtCaret(text);
        if (bounds == null) {
            clearWordHighlight();
            return;
        }
        if (currentWordHighlight != null
                && bounds[0] == lastWordHighlightStart
                && bounds[1] == lastWordHighlightEnd
                && lastWordHighlightTextChangeId == textChangeId) {
            return;
        }

        clearWordHighlight();
        try {
            Highlighter hl = poemEditor.getHighlighter();
            currentWordHighlight = hl.addHighlight(bounds[0], bounds[1], wordHighlightPainter);
            lastWordHighlightStart = bounds[0];
            lastWordHighlightEnd = bounds[1];
            lastWordHighlightTextChangeId = textChangeId;
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
        lastWordHighlightStart = -1;
        lastWordHighlightEnd = -1;
        lastWordHighlightTextChangeId = -1;
    }
    
    /**
     * Opens the detailed poetry analysis dialog.
     */
    private void showPoemAnalysis() {
        String title = poemTitleField != null ? poemTitleField.getText() : "";
        String text = poemEditor != null ? poemEditor.getText() : "";
        
        if (text == null || text.isBlank()) {
            javax.swing.JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "Please write some poetry first to analyze.",
                "No Content",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        
        // Create analysis dialog
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Poetry Analysis", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(700, 600);
        dialog.setLocationRelativeTo(this);
        
        // Create and populate the analysis panel
        PoetryAnalysisPanel analysisPanel = new PoetryAnalysisPanel();
        analysisPanel.analyzePoem(title, text);
        
        dialog.add(analysisPanel);
        dialog.setVisible(true);
    }
    
    /**
     * Custom highlight painter that draws a gentle rounded outline around text.
     */
    private static class WordOutlineHighlightPainter implements Highlighter.HighlightPainter {
        private final Color color;
        private final int fillAlpha;
        private final int strokeAlpha;
        private final float strokeWidth;
        
        public WordOutlineHighlightPainter(Color color) {
            this.color = color;
            this.fillAlpha = 40;
            this.strokeAlpha = color.getAlpha();
            this.strokeWidth = 1.5f;
        }
        
        public WordOutlineHighlightPainter(Color color, int fillAlpha, int strokeAlpha, float strokeWidth) {
            this.color = color;
            this.fillAlpha = fillAlpha;
            this.strokeAlpha = strokeAlpha;
            this.strokeWidth = strokeWidth;
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
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), fillAlpha));
                g2.fillRoundRect(x, y, w, h, 6, 6);
                
                // Draw outline
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), strokeAlpha));
                g2.setStroke(new java.awt.BasicStroke(strokeWidth));
                g2.drawRoundRect(x, y, w, h, 6, 6);
                
                g2.dispose();
            } catch (BadLocationException ignored) {}
        }
    }
    
    private static class GuidedLineHighlightPainter implements Highlighter.HighlightPainter {
        private final Color fillColor;
        private final Color accentColor;
        private final String badge;
        
        GuidedLineHighlightPainter(Color fillColor, Color accentColor, String badge) {
            this.fillColor = fillColor;
            this.accentColor = accentColor;
            this.badge = badge;
        }
        
        @Override
        public void paint(Graphics g, int offs0, int offs1, java.awt.Shape bounds, javax.swing.text.JTextComponent c) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                java.awt.Rectangle r0 = c.modelToView(offs0);
                if (r0 == null) { g2.dispose(); return; }
                
                Insets insets = c.getInsets();
                int x = Math.max(insets.left, r0.x - 8);
                int y = r0.y - 1;
                int h = r0.height + 2;
                int w = Math.max(0, c.getWidth() - x - insets.right - 6);
                
                g2.setColor(new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 80));
                g2.fillRoundRect(x, y, w, h, 10, 10);
                
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 210));
                g2.fillRoundRect(x, y, 8, h, 8, 8);
                
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 150));
                g2.setStroke(new java.awt.BasicStroke(1.3f));
                g2.drawRoundRect(x, y, w, h, 10, 10);
                
                if (badge != null && !badge.isBlank()) {
                    Font font = c.getFont().deriveFont(Font.BOLD, 12f);
                    g2.setFont(font);
                    FontMetrics fm = g2.getFontMetrics();
                    int padX = 7;
                    int badgeW = fm.stringWidth(badge) + padX * 2;
                    int badgeH = fm.getHeight() + 2;
                    int bx = Math.max(x + 10, x + w - badgeW - 8);
                    int by = y + (h - badgeH) / 2;
                    
                    g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 220));
                    g2.fillRoundRect(bx, by, badgeW, badgeH, 10, 10);
                    g2.setColor(new Color(255, 255, 255, 220));
                    g2.drawString(badge, bx + padX, by + fm.getAscent());
                }
                
                g2.dispose();
            } catch (BadLocationException ignored) {}
        }
    }
    
    private static class GuidedAnchorHighlightPainter implements Highlighter.HighlightPainter {
        private final Color color;
        private final String badge;
        
        GuidedAnchorHighlightPainter(Color color, String badge) {
            this.color = color;
            this.badge = badge;
        }
        
        @Override
        public void paint(Graphics g, int offs0, int offs1, java.awt.Shape bounds, javax.swing.text.JTextComponent c) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                java.awt.Rectangle r0 = c.modelToView(offs0);
                java.awt.Rectangle r1 = c.modelToView(offs1);
                if (r0 == null || r1 == null) { g2.dispose(); return; }
                
                int x = r0.x - 2;
                int y = r0.y - 1;
                int w = (r1.x + r1.width) - r0.x + 4;
                int h = r0.height + 2;
                
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
                g2.fillRoundRect(x, y, w, h, 8, 8);
                
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
                g2.setStroke(new java.awt.BasicStroke(1.4f));
                g2.drawRoundRect(x, y, w, h, 8, 8);
                
                if (badge != null && !badge.isBlank()) {
                    Font font = c.getFont().deriveFont(Font.BOLD, 11f);
                    g2.setFont(font);
                    FontMetrics fm = g2.getFontMetrics();
                    int badgeW = fm.stringWidth(badge) + 10;
                    int badgeH = fm.getHeight() + 2;
                    int bx = Math.min(c.getWidth() - badgeW - 6, x + w + 4);
                    int by = y + (h - badgeH) / 2;
                    g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 230));
                    g2.fillRoundRect(bx, by, badgeW, badgeH, 8, 8);
                    g2.setColor(new Color(255, 255, 255, 220));
                    g2.drawString(badge, bx + 4, by + fm.getAscent());
                }
                
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
