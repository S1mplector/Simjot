package main.ui.features.entries;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.StyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;
import main.core.service.LastSaveTracker;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.containers.TranslucentPanel;
import main.ui.components.popup.AnimatedGlassPopup;
import main.ui.components.slider.MoodSlider;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.EntryBackgroundDialog;
import main.ui.features.editing.UndoRedoManager;
import main.ui.theme.aero.AeroTheme;
import main.ui.components.fields.ModernTextField;
import main.ui.components.util.EditorUIUtils;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.infrastructure.backup.NotebookInfo;
import main.core.sim.api.SimEventBus;

public class EntryPanel extends AbstractEditorPanel {

    // inherited: app, journalFolder, cardLayout, cardPanel

    // UI components for the entry
    protected JTextField titleField;
    protected JTextPane contentArea;
    protected MoodSlider moodSlider;
    private DetailedMoodPanel detailedMoodPanel; // collapsible detailed mood panel
    private SaveIndicatorPanel saveIndicator;
    private boolean titleFocusedOnce = false;
    private AnimatedGlassPopup formatPopup;
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private AutosaveManager autosaveManager;
    private volatile boolean isAutosaving = false;
    // Track temporary placeholder range for Sim guidance
    private int pendingGuidanceStart = -1;
    private int pendingGuidanceLen = 0;
    // Spinner removed — relying on inline "Thinking…" text only
    // Formatting toggle buttons (to reflect current caret/selection state)
    private JToggleButton boldBtn;
    private JToggleButton italicBtn;
    private JToggleButton underlineBtn;
    private JToggleButton bulletsBtn;
    // 'currentFile' is inherited from AbstractEditorPanel

    public EntryPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
        // Subscribe to guidance produced to insert into editor
        try {
            SimEventBus.get().addListener(new SimEventBus.Listener(){
                @Override public void onGuidanceProduced(String text){
                    if (text == null || text.isBlank()) return;
                    SwingUtilities.invokeLater(() -> insertGuidanceStyled(text));
                }
            });
        } catch (Throwable ignored) {}
    }

    // Load an existing entry file into the editor fields
    private void loadExistingEntry(File fileToEdit) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileToEdit))) {
            String title = reader.readLine();
            if (title == null) title = "";
            titleField.setText(title);
            // Expect a blank separator line
            reader.readLine();
            // Read the remainder into a string
            StringBuilder rest = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                rest.append(line).append("\n");
            }
            String remainder = rest.toString();
            if (remainder.stripLeading().startsWith("{\\rtf")) {
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
                try (ByteArrayInputStream bin = new ByteArrayInputStream(remainder.getBytes())) {
                    kit.read(bin, doc, 0);
                }
                contentArea.setDocument(doc);
                contentArea.setEditorKit(new StyledEditorKit());
                ensureSimStyles();
            } else {
                contentArea.setText(remainder);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading journal entry.", true).showDialog();
        }
    }

    // Unified constructor: if fileToEdit is non-null, load it and switch to edit mode (saving updates same file)
    public EntryPanel(JournalApp app, File fileToEdit, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this(app, journalFolder, cardLayout, cardPanel);
        if (fileToEdit != null) {
            this.currentFile = fileToEdit;
            loadExistingEntry(fileToEdit);
        }
    }

    // Load the paper background image from "img/paper.png"
    /*
    private void loadBackground() {
        try {
            backgroundImage = ImageIO.read(new File("Simjot/img/paper.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
            backgroundImage = null;
        }
    }
     */
    // Paint the background image via helper
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        String bgPath = SettingsStore.get().getEntryBackgroundImage();
        float opacity = SettingsStore.get().getEntryBackgroundOpacity();
        backgroundPainter.paint(g, this, bgPath, opacity, false);
    }

    private void initUI() {
        // --- Extended Toolbar with Mood Slider ---
        JPanel toolbarContainer = new JPanel(new BorderLayout(0, 5));
        // Solid background so the page wallpaper does not seep through the toolbar
        toolbarContainer.setOpaque(true);
        toolbarContainer.setBackground(new Color(0xE7, 0xE7, 0xE7)); // #e7e7e7
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar row (left)
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topToolbar.setOpaque(false);

        // Back button -> return to this notebook's entries manager
        NotebookInfo nbInfo = new NotebookInfo(
                journalFolder.getName(),
                NotebookInfo.Type.JOURNAL,
                journalFolder,
                journalFolder.lastModified(),
                null
        );
        ToolbarIconButton backButton = EditorUIUtils.createBackToEntriesButton(app, nbInfo);
        topToolbar.add(backButton);

        // Right-side settings (cork icon) button
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.setOpaque(false);
        ToolbarIconButton settingsBtn = new ToolbarIconButton("options");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            EntryBackgroundDialog dialog = new EntryBackgroundDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        rightToolbar.add(settingsBtn);

        // Spinner removed per request; keep toolbar compact

        // Sim guidance button (uses simguidance.png)
        ToolbarIconButton guidanceBtn = new ToolbarIconButton("simguidance");
        guidanceBtn.setToolTipText("Ask Sim for guidance on this entry");
        guidanceBtn.addActionListener(e -> {
            try {
                Document doc = contentArea.getDocument();
                String all = doc.getText(0, doc.getLength());
                // Trim excessive length to keep prompt lean
                int cap = 4000;
                String text = all.length() > cap ? all.substring(all.length() - cap) : all;
                showGuidanceThinkingPlaceholder();
                SimEventBus.get().emitGuidanceRequested(text);
            } catch (BadLocationException ex) {
                // no-op
            }
        });
        rightToolbar.add(Box.createHorizontalStrut(6));
        rightToolbar.add(guidanceBtn);

        // Title label & field
        JLabel titleLabel = new JLabel("Title:");
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleLabel.setFont(AeroTheme.defaultBoldFont(16f));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(titleLabel);

        titleField = new ModernTextField(24);
        ((ModernTextField) titleField).setPlaceholder("Untitled entry");
        titleField.setFont(AeroTheme.defaultFont().deriveFont(16f));
        titleField.setForeground(AeroTheme.TEXT_PRIMARY);
        // Auto-select on first focus
        titleField.addFocusListener(new java.awt.event.FocusAdapter(){
            @Override
            public void focusGained(java.awt.event.FocusEvent e){
                if(!titleFocusedOnce){
                    titleField.selectAll();
                    titleFocusedOnce = true;
                }
            }
        });
        topToolbar.add(titleField);

        // Font buttons (A- / A+)
        RoundedButton decFont = new RoundedButton("A-");
        RoundedButton incFont = new RoundedButton("A+");
        decFont.addActionListener(e -> changeFontSize(-1));
        incFont.addActionListener(e -> changeFontSize(1));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(decFont);
        topToolbar.add(incFont);

        // Bottom toolbar row with mood slider
        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomToolbar.setOpaque(false);

        moodSlider = new MoodSlider();
        bottomToolbar.add(moodSlider);
        // Emit Sim mood changes (Phase 2 hook)
        moodSlider.addChangeListener(e -> {
            try { SimEventBus.get().emitMoodChanged((double) moodSlider.getValue()); } catch (Throwable ignored) {}
        });

        // Add both toolbar rows to the container
        // Detailed mood logging expand button
        RoundedButton expandMoodBtn = new RoundedButton("\u203A"); // single-glyph arrow ›
        expandMoodBtn.setToolTipText("Open detailed mood logging");
        expandMoodBtn.setForeground(AeroTheme.TEXT_PRIMARY);
        // Make the button compact
        expandMoodBtn.setPreferredSize(new Dimension(28, 28));
        expandMoodBtn.setMargin(new Insets(0, 0, 0, 0));
        expandMoodBtn.addActionListener(e -> {
            boolean next = detailedMoodPanel == null || !detailedMoodPanel.isExpanded();
            if (detailedMoodPanel != null) detailedMoodPanel.setExpanded(next);
            // flip arrow
            expandMoodBtn.setText(next ? "\u2039" : "\u203A"); // ‹ when open, › when closed
        });
        bottomToolbar.add(expandMoodBtn);

        // Stack bottom toolbar and the collapsible detailed panel vertically
        JPanel bottomStack = new JPanel();
        bottomStack.setOpaque(false);
        bottomStack.setLayout(new BoxLayout(bottomStack, BoxLayout.Y_AXIS));
        bottomStack.add(bottomToolbar);
        // create detailed panel with save handler -> update mood slider and persist
        detailedMoodPanel = new DetailedMoodPanel(composite -> {
            moodSlider.setValue(composite);
            recordMood(composite);
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Mood Logged",
                    "Detailed mood saved (" + composite + ")",
                    false).showDialog();
        });
        bottomStack.add(detailedMoodPanel);

        // Add toolbars to container
        toolbarContainer.add(topToolbar, BorderLayout.NORTH);
        toolbarContainer.add(bottomStack, BorderLayout.CENTER);
        toolbarContainer.add(rightToolbar, BorderLayout.EAST);

        add(toolbarContainer, BorderLayout.NORTH);

        // --- Content Area (match PoemPanel style) ---
        // Use the same translucent rounded rectangle container used in PoemPanel
        JPanel textWrapper = new TranslucentPanel();
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Content Area: Rich text editor (StyledDocument)
        contentArea = new JTextPane();

        // Load font size directly from settings to ensure persistence
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        contentArea.setFont(new Font("Serif", Font.PLAIN, savedFontSize));
        // JTextPane handles wrapping automatically via view; ensure editor kit is styled
        contentArea.setEditorKit(new StyledEditorKit());
        contentArea.setOpaque(false);
        contentArea.setForeground(AeroTheme.TEXT_PRIMARY);
        ensureSimStyles();

        // Keep formatting toggles in sync with caret/selection changes
        contentArea.addCaretListener(e -> updateFormattingToggleState());

        // Add undo/redo support
        @SuppressWarnings("unused")
        UndoRedoManager contentUndoManager = new UndoRedoManager(contentArea);
        @SuppressWarnings("unused")
        UndoRedoManager titleUndoManager = new UndoRedoManager(titleField);

        // Add document listener for word count
        contentArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null) autosaveManager.markDirty();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null) autosaveManager.markDirty();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null) autosaveManager.markDirty();
            }
        });
        // Autosave on title change too
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null) autosaveManager.markDirty(); }
        });

        // Middle-click floating popup
        formatPopup = new AnimatedGlassPopup(SwingUtilities.getWindowAncestor(this));
        contentArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    Point p = e.getPoint();
                    SwingUtilities.convertPointToScreen(p, contentArea);
                    formatPopup.showAt(p.x, p.y, () -> createFormattingToolbar());
                    e.consume();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Add scroll pane to the translucent wrapper (no inline formatting bar)
        textWrapper.setLayout(new BorderLayout());
        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Add some vertical space between toolbar and content (like PoemPanel)
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(Box.createRigidArea(new Dimension(0, 15)), BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);

        // Add to main panel
        add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Panel: Save Button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);

        // Save button (via EditorUIUtils)
        ToolbarIconButton saveButton = EditorUIUtils.createSaveButton("Save Entry", this::saveEntry);

        // Word count label
        JLabel wordCountLabel = new JLabel("Words: 0");
        wordCountLabel.setForeground(Color.GRAY);
        wordCountLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        wordCountLabel.setBorder(new EmptyBorder(0, 10, 5, 0));
        bottomPanel.add(wordCountLabel);

        // Save state indicator (reusable component)
        saveIndicator = new SaveIndicatorPanel();
        bottomPanel.add(saveIndicator);
        bottomPanel.add(saveButton);

        add(bottomPanel, BorderLayout.SOUTH);

        // --- Autosave wiring ---
        int delayMs = SettingsStore.get().getAutosaveDelayMs();
        if (delayMs > 0) {
            autosaveManager = new AutosaveManager(delayMs,
                    this::saveEntry,
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
    }

    // --- Sim helpers ---
    private void emitTypingSnapshot() {
        try {
            Document doc = contentArea.getDocument();
            String all = doc.getText(0, doc.getLength());
            // Truncate to avoid huge payloads
            int max = 500;
            String snapshot = all.length() > max ? all.substring(all.length() - max) : all;
            SimEventBus.get().emitTyping(snapshot);
        } catch (BadLocationException | RuntimeException ignored) {
            // ignore
        }
    }

    private void ensureSimStyles(){
        StyledDocument sd = (StyledDocument) contentArea.getDocument();
        // Silvery sky blue color
        Color simBlue = new Color(176, 196, 222); // LightSteelBlue
        Style body = sd.getStyle("simGuidanceBody");
        if (body == null) {
            body = sd.addStyle("simGuidanceBody", null);
            StyleConstants.setForeground(body, simBlue);
        }
        Style header = sd.getStyle("simGuidanceHeader");
        if (header == null) {
            header = sd.addStyle("simGuidanceHeader", body);
            StyleConstants.setBold(header, true);
        }
        Style thinking = sd.getStyle("simThinking");
        if (thinking == null) {
            thinking = sd.addStyle("simThinking", null);
            StyleConstants.setItalic(thinking, true);
            StyleConstants.setForeground(thinking, Color.GRAY);
        }
    }

    private void showGuidanceThinkingPlaceholder(){
        try {
            ensureSimStyles();
            StyledDocument sd = (StyledDocument) contentArea.getDocument();
            Style thinking = sd.getStyle("simThinking");
            int end = sd.getLength();
            String prefix = (end > 0) ? "\n\n" : "";
            String th = "Thinking…\n";
            // Insert and remember range
            sd.insertString(end, prefix, null);
            int start = sd.getLength();
            sd.insertString(sd.getLength(), th, thinking);
            pendingGuidanceStart = start;
            pendingGuidanceLen = sd.getLength() - start;
            contentArea.setCaretPosition(sd.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void insertGuidanceStyled(String guidance){
        try {
            ensureSimStyles();
            StyledDocument sd = (StyledDocument) contentArea.getDocument();
            Style body = sd.getStyle("simGuidanceBody");
            // Remove placeholder if present
            if (pendingGuidanceStart >= 0 && pendingGuidanceLen > 0 &&
                pendingGuidanceStart + pendingGuidanceLen <= sd.getLength()){
                sd.remove(pendingGuidanceStart, pendingGuidanceLen);
            }
            int end = sd.getLength();
            String prefix = (end > 0) ? "\n\n" : "";
            String bodyText = (guidance == null ? "" : guidance.strip()) + "\n";
            sd.insertString(end, prefix, null);
            sd.insertString(sd.getLength(), bodyText, body);
            contentArea.setCaretPosition(sd.getLength());
            pendingGuidanceStart = -1;
            pendingGuidanceLen = 0;
        } catch (BadLocationException ignored) {}
    }

    private JPanel createFormattingToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setOpaque(false);

        Action bold = new StyledEditorKit.BoldAction();
        bold.putValue(Action.NAME, "B");
        Action italic = new StyledEditorKit.ItalicAction();
        italic.putValue(Action.NAME, "I");
        Action underline = new StyledEditorKit.UnderlineAction();
        underline.putValue(Action.NAME, "U");

        Dimension btnSize = new Dimension(48, 28);
        boldBtn = new RoundedToggleButton("B");
        boldBtn.setPreferredSize(btnSize);
        boldBtn.setFocusPainted(false);
        boldBtn.setToolTipText("Bold (Ctrl+B)");
        italicBtn = new RoundedToggleButton("I");
        italicBtn.setPreferredSize(btnSize);
        italicBtn.setFocusPainted(false);
        italicBtn.setToolTipText("Italic (Ctrl+I)");
        underlineBtn = new RoundedToggleButton("U");
        underlineBtn.setPreferredSize(btnSize);
        underlineBtn.setFocusPainted(false);
        underlineBtn.setToolTipText("Underline (Ctrl+U)");
        bulletsBtn = new RoundedToggleButton("•");
        bulletsBtn.setPreferredSize(btnSize);
        bulletsBtn.setFocusPainted(false);
        bulletsBtn.setToolTipText("Toggle Bullets");

        boldBtn.addActionListener(e -> {
            contentArea.requestFocusInWindow();
            bold.actionPerformed(new java.awt.event.ActionEvent(contentArea, java.awt.event.ActionEvent.ACTION_PERFORMED, "bold"));
            updateFormattingToggleState();
        });
        italicBtn.addActionListener(e -> {
            contentArea.requestFocusInWindow();
            italic.actionPerformed(new java.awt.event.ActionEvent(contentArea, java.awt.event.ActionEvent.ACTION_PERFORMED, "italic"));
            updateFormattingToggleState();
        });
        underlineBtn.addActionListener(e -> {
            contentArea.requestFocusInWindow();
            underline.actionPerformed(new java.awt.event.ActionEvent(contentArea, java.awt.event.ActionEvent.ACTION_PERFORMED, "underline"));
            updateFormattingToggleState();
        });
        bulletsBtn.addActionListener(e -> {
            contentArea.requestFocusInWindow();
            toggleBullets();
            updateFormattingToggleState();
        });

        bar.add(boldBtn);
        bar.add(italicBtn);
        bar.add(underlineBtn);
        bar.add(bulletsBtn);

        // Keyboard shortcuts
        InputMap im = contentArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = contentArea.getActionMap();
        im.put(KeyStroke.getKeyStroke("control B"), "rt-bold");
        am.put("rt-bold", (Action) bold);
        im.put(KeyStroke.getKeyStroke("control I"), "rt-italic");
        am.put("rt-italic", (Action) italic);
        im.put(KeyStroke.getKeyStroke("control U"), "rt-underline");
        am.put("rt-underline", (Action) underline);

        // Initialize toggle state
        SwingUtilities.invokeLater(this::updateFormattingToggleState);

        return bar;
    }

    private void updateFormattingToggleState() {
        try {
            javax.swing.text.AttributeSet attrs = ((StyledEditorKit) contentArea.getEditorKit()).getInputAttributes();
            if (boldBtn != null) boldBtn.setSelected(javax.swing.text.StyleConstants.isBold(attrs));
            if (italicBtn != null) italicBtn.setSelected(javax.swing.text.StyleConstants.isItalic(attrs));
            if (underlineBtn != null) underlineBtn.setSelected(javax.swing.text.StyleConstants.isUnderline(attrs));
            if (bulletsBtn != null) bulletsBtn.setSelected(isSelectionAllBulleted());
        } catch (Exception ignored) {
        }
    }

    private boolean isSelectionAllBulleted() {
        javax.swing.text.Document doc = contentArea.getDocument();
        int start = contentArea.getSelectionStart();
        int end = contentArea.getSelectionEnd();
        try {
            int lineStart = getLineStart(doc, start);
            int lineEnd = getLineEnd(doc, end);
            String text = doc.getText(lineStart, lineEnd - lineStart);
            String[] lines = text.split("\n", -1);
            boolean anyNonEmpty = false;
            for (String ln : lines) {
                if (ln.trim().length() > 0) {
                    anyNonEmpty = true;
                    if (!ln.startsWith("• ")) return false;
                }
            }
            return anyNonEmpty; // true only if all non-empty lines are bulleted
        } catch (BadLocationException ex) {
            return false;
        }
    }

    // Reusable popup extracted to main.ui.components.AnimatedGlassPopup

    private void toggleBullets() {
        Document doc = contentArea.getDocument();
        int start = contentArea.getSelectionStart();
        int end = contentArea.getSelectionEnd();
        try {
            int lineStart = getLineStart(doc, start);
            int lineEnd = getLineEnd(doc, end);
            String text = doc.getText(lineStart, lineEnd - lineStart);
            String[] lines = text.split("\n", -1);
            boolean allBulleted = true;
            for (String ln : lines) {
                if (!ln.startsWith("• ") && ln.trim().length() > 0) { allBulleted = false; break; }
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i];
                if (ln.trim().isEmpty()) {
                    sb.append(ln);
                } else if (allBulleted) {
                    // remove bullet prefix if present
                    sb.append(ln.startsWith("• ") ? ln.substring(2) : ln);
                } else {
                    sb.append("• ").append(ln);
                }
                if (i < lines.length - 1) sb.append('\n');
            }
            doc.remove(lineStart, lineEnd - lineStart);
            doc.insertString(lineStart, sb.toString(), null);
            contentArea.select(lineStart, lineStart + sb.length());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private int getLineStart(Document doc, int pos) throws BadLocationException {
        while (pos > 0) {
            String s = doc.getText(pos - 1, 1);
            if ("\n".equals(s)) break;
            pos--;
        }
        return pos;
    }

    private int getLineEnd(Document doc, int pos) throws BadLocationException {
        int len = doc.getLength();
        while (pos < len) {
            String s = doc.getText(pos, 1);
            if ("\n".equals(s)) break;
            pos++;
        }
        return pos;
    }

    private void updateWordCount() {
        String text = contentArea.getText();
        String[] words = text.trim().split("\\s+");
        int count = text.trim().isEmpty() ? 0 : words.length;

        // Find the word count label and update it
        // This is a bit of a workaround, but effective for this structure
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel) {
                JPanel bottomPanel = (JPanel) comp;
                for (Component innerComp : bottomPanel.getComponents()) {
                    if (innerComp instanceof JLabel && ((JLabel) innerComp).getText().startsWith("Words:")) {
                        ((JLabel) innerComp).setText("Words: " + count);
                        return;
                    }
                }
            }
        }
    }

    // Called by the "Save Entry" button.
    // This is overridden in EditEntryPanel to update an existing file.
    protected void saveEntry() {
        // Snapshot UI state on EDT to avoid touching Swing components from autosave thread
        final String[] titleHolder = new String[1];
        final String[] contentHolder = new String[1];
        final int[] moodHolder = new int[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                titleHolder[0] = titleField.getText().trim();
                contentHolder[0] = contentArea.getText();
                moodHolder[0] = moodSlider.getValue();
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    titleHolder[0] = titleField.getText().trim();
                    contentHolder[0] = contentArea.getText();
                    moodHolder[0] = moodSlider.getValue();
                });
            }
        } catch (Exception invokeErr) {
            // If we cannot read UI state, fail gracefully
            return;
        }
        String title = titleHolder[0];
        String content = contentHolder[0];
        if (title.isEmpty() && content.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Please enter a title or content.", true).showDialog());
            return;
        }
        int moodValue = moodHolder[0]; // 0 - 100
        recordMood(moodValue);

        try {
            // Update status to Saving…
            if (saveIndicator != null) saveIndicator.setSaving();
            // Ensure target folder exists
            if (journalFolder != null && !journalFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                journalFolder.mkdirs();
            }

            File file;
            boolean isNewFile = false;

            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + fileExtension();
                file = new File(journalFolder, filename);
                currentFile = file;
                isNewFile = true;
            } else {
                // Subsequent saves - use existing file
                file = currentFile;
            }

            // Save title + a blank line + RTF body with styles
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println(title);
                writer.println(); // separator
            }
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument sd = (StyledDocument) contentArea.getDocument();
                try {
                    kit.write(fos, sd, 0, sd.getLength());
                } catch (BadLocationException ble) {
                    // fallback to plain text if unexpected
                    fos.write(content.getBytes());
                }
            }

            // Mark last successful save for status bar
            LastSaveTracker.markSaved();

            String message = isNewFile ? "Journal entry saved successfully!" : "Journal entry updated successfully!";
            if (!isAutosaving) {
                SwingUtilities.invokeLater(() -> new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", message, false).showDialog());
            }

            // Update status to Saved · time
            if (saveIndicator != null) saveIndicator.setSaved(new Date());
        } catch (IOException ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving entry.", true).showDialog());
            if (saveIndicator != null) saveIndicator.setError("Error saving");
        }
    }

    private void recordMood(int moodValue) {
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(moodFile, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            writer.println(timestamp + "," + moodValue);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void changeFontSize(int delta) {
        Font f = contentArea.getFont();
        int newSize = Math.max(8, Math.min(72, f.getSize() + delta));
        contentArea.setFont(f.deriveFont((float) newSize));
    }

    // --- AbstractEditorPanel hooks and remaining interface bits ---
    @Override
    protected void safeLoadFile(File f) {
        loadExistingEntry(f);
        if (saveIndicator != null && f != null) {
            saveIndicator.setSavedFromTimestamp(f.lastModified());
        }
    }

    @Override
    protected void clearEditor() {
        titleField.setText("");
        contentArea.setText("");
        if (saveIndicator != null) saveIndicator.clear();
    }

    @Override
    protected void performSave() {
        saveEntry();
    }

    @Override
    public String fileExtension() {
        return ".note";
    }

    @Override
    public void requestInitialFocus() {
        if (titleField.getText() == null || titleField.getText().isEmpty()) {
            titleField.requestFocusInWindow();
        } else {
            contentArea.requestFocusInWindow();
        }
    }
}

enum Mood {
    HAPPY, NEUTRAL, SAD
}

class MoodButton extends JToggleButton {

    private Mood mood;
    private final Color SELECTED_COLOR = new Color(135, 206, 250); // Light Sky Blue

    public MoodButton(Mood mood) {
        this.mood = mood;
        init();
    }

    private void init() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Draw background circle based on selection state
        if (isSelected()) {
            g2.setColor(SELECTED_COLOR);
            g2.fillOval(0, 0, width, height);
        } else {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawOval(0, 0, width - 1, height - 1);
        }

        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2));

        // Draw minimalist face
        switch (mood) {
            case HAPPY:
                g2.drawArc(width / 4, height / 4, width / 2, height / 2, 200, 140); // Smile
                g2.fillOval(width / 4 + 5, height / 3, 4, 4); // Left eye
                g2.fillOval(width * 3 / 4 - 10, height / 3, 4, 4); // Right eye
                break;
            case NEUTRAL:
                g2.drawLine(width / 4, height / 2, width * 3 / 4, height / 2); // Mouth
                g2.fillOval(width / 4 + 5, height / 3, 4, 4); // Left eye
                g2.fillOval(width * 3 / 4 - 10, height / 3, 4, 4); // Right eye
                break;
            case SAD:
                g2.drawArc(width / 4, height / 2, width / 2, height / 2, 20, 140); // Frown
                g2.fillOval(width / 4 + 5, height / 3, 4, 4); // Left eye
                g2.fillOval(width * 3 / 4 - 10, height / 3, 4, 4); // Right eye
                break;
        }
        g2.dispose();
    }
}
