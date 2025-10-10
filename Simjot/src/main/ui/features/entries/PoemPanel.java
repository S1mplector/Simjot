package main.ui.features.entries;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.containers.TranslucentPanel;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.util.EditorUIUtils;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.PoemBackgroundDialog;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.infrastructure.backup.NotebookInfo;
import main.ui.features.poetry.StatsSidebarPanel;
import main.ui.features.poetry.RhymesDockPanel;
import main.core.export.PoemExporter;
import main.ui.dialog.export.PoemExportDialog;
import main.ui.dialog.message.UIMessage;

public class PoemPanel extends AbstractEditorPanel {
    // inherited: app, journalFolder, cardLayout, cardPanel

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
        ToolbarIconButton statsToggle = new ToolbarIconButton("list");
        statsToggle.setToolTipText("Toggle Stats Sidebar");
        statsToggle.addActionListener(e -> {
            if (statsPanel != null) {
                boolean vis = !statsPanel.isVisible();
                statsPanel.setVisible(vis);
                statsToggle.setSelected(vis);
                revalidate(); repaint();
            }
        });
        ToolbarIconButton rhymesToggle = new ToolbarIconButton("rhyme");
        rhymesToggle.setToolTipText("Toggle Rhymes & Thesaurus Dock");
        rhymesToggle.addActionListener(e -> {
            if (rhymesDock != null) {
                boolean vis = !rhymesDock.isVisible();
                rhymesDock.setVisible(vis);
                rhymesToggle.setSelected(vis);
                if (vis) {
                    String w = getWordAtCaret();
                    rhymesDock.update(w, poemEditor.getText());
                }
                revalidate(); repaint();
            }
        });
        ToolbarIconButton settingsBtn = new ToolbarIconButton("options");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            PoemBackgroundDialog dialog = new PoemBackgroundDialog((java.awt.Frame)SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        // Distraction-free toggle
        ToolbarIconButton dfBtn = new ToolbarIconButton("fullscreen");
        dfBtn.setToolTipText("Distraction-Free Mode");
        dfBtn.addActionListener(e -> toggleDistractionFree());
        // Export button (advanced)
        ToolbarIconButton exportBtn = new ToolbarIconButton("export");
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
                "Poem Title:",
                "Untitled poem",
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
        ToolbarIconButton dfBack = new ToolbarIconButton("back");
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

        // Load font size directly from settings to ensure persistence
        int savedFontSize = SettingsStore.get().getPoemFontSize();
        poemEditor.setFont(new Font("Serif", Font.ITALIC, savedFontSize));
        /*
          NOTE: If you want a truly cursive font, pick one installed on your system, 
          e.g. new Font("Gabriola", Font.PLAIN, 18) or "Lucida Handwriting", etc.
        */

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

        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Add some vertical space between title and text area
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(Box.createRigidArea(new Dimension(0, 15)), BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);
        // Initialize optional side panels (hidden by default)
        statsPanel = new StatsSidebarPanel();
        statsPanel.setVisible(false);
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

        add(statsPanel, BorderLayout.WEST);
        add(centerContainer, BorderLayout.CENTER);
        add(rhymesDock, BorderLayout.EAST);

        // --- Bottom Panel: "Save Poem" button and Stanza Counter ---
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel stanzaLabel = new JLabel("Stanzas: 1");
        stanzaLabel.setForeground(Color.DARK_GRAY);
        stanzaLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        bottomPanel.add(stanzaLabel, BorderLayout.WEST);
        
        // Listener to update the stanza count
        poemEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null) autosaveManager.markDirty();
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null) autosaveManager.markDirty();
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateStanzaCount(stanzaLabel);
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.updateFromText(poemEditor.getText());
                if (rhymesDock != null && rhymesDock.isVisible()) rhymesDock.update(getWordAtCaret(), poemEditor.getText());
                if (statsPanel != null && statsPanel.isVisible()) statsPanel.setHighlightedLine(getCaretLineIndex());
                if (autosaveManager != null) autosaveManager.markDirty();
            }
        });
        // Caret listener to update rhyme/synonyms for current word
        poemEditor.addCaretListener(e -> {
            if (rhymesDock != null && rhymesDock.isVisible()) {
                String w = getWordAtCaret();
                rhymesDock.update(w, poemEditor.getText());
            }
            if (statsPanel != null && statsPanel.isVisible()) {
                statsPanel.setHighlightedLine(getCaretLineIndex());
            }
        });
        // Autosave on title change as well
        poemTitleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
        });

        // Status + Inspire
        JPanel centerFlow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        centerFlow.setOpaque(false);
        statusLabel = new JLabel("Words: 0 • Chars: 0 • Stanzas: 0 • ~0 min read");
        statusLabel.setForeground(Color.DARK_GRAY);
        centerFlow.add(statusLabel);
        bottomPanel.add(centerFlow, BorderLayout.CENTER);

        // Save button (via EditorUIUtils)
        ToolbarIconButton saveButton = EditorUIUtils.createSaveButton("Save Poem", this::savePoem);
        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        eastPanel.setOpaque(false);
        saveIndicator = new SaveIndicatorPanel();
        eastPanel.add(saveIndicator);
        eastPanel.add(saveButton);
        bottomPanel.add(eastPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // --- Autosave wiring ---
        int delayMs = SettingsStore.get().getAutosaveDelayMs();
        if (delayMs > 0) {
            autosaveManager = new AutosaveManager(delayMs,
                    this::savePoem,
                    () -> { isAutosaving = true; if (saveIndicator != null) saveIndicator.setSaving(); },
                    () -> { 
                        if (saveIndicator != null) {
                            long ts = (currentFile != null && currentFile.exists()) ? currentFile.lastModified() : System.currentTimeMillis();
                            saveIndicator.setSavedFromTimestamp(ts);
                        }
                        isAutosaving = false; 
                    });
        } else {
            autosaveManager = null; // autosave disabled
        }

        // Initial metrics
        updateMetrics(stanzaLabel);
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
        String title = poemTitleField.getText().trim();
        String content = poemEditor.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            UIMessage.error(this,
                    "Error",
                    "<b>Error:</b> cannot save poem without content.\n\n\"Your poem is empty.\"",
                    "- Add a title or a few lines\n- Press Save again");
            return;
        }
        if (saveIndicator != null) saveIndicator.setSaving();
        // 'journalFolder' already points to the poems directory provided by AppDirectories
        if (!journalFolder.exists()) {
            journalFolder.mkdirs();
        }
        
        try {
            File poemFile;
            boolean isNewFile = false;
            
            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + ".poem";
                poemFile = new File(journalFolder, filename);
                currentFile = poemFile;
                isNewFile = true;
            } else {
                // Subsequent saves - use existing file
                poemFile = currentFile;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(poemFile))) {
                writer.println(title);
                writer.println();
                writer.println(content);
            }
            
            // Remember as last opened file for startup restore
            try {
                SettingsStore.get().setLastOpenedFilePath(poemFile.getAbsolutePath());
                SettingsStore.get().save();
            } catch (Throwable ignored) {}

            String message = isNewFile ? "Poem saved successfully!" : "Poem updated successfully!";
            if (!isAutosaving) {
                new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", message, false).showDialog();
            }
            if (saveIndicator != null) saveIndicator.setSaved(new Date());
            
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
    }

    @Override
    protected void clearEditor() {
        poemTitleField.setText("");
        poemEditor.setText("");
        if (saveIndicator != null) saveIndicator.clear();
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
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Panel with a dark, translucent background
        JPanel contentPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 30, 30, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        contentPanel.setOpaque(false);
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
