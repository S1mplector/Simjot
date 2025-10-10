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
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import main.ui.components.editor.RichTextStyler;
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
import main.ui.components.util.EditorUIUtils;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;
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
    // UI refs for toggling (distraction-free / zen mode)
    private JPanel toolbarContainer;
    private JPanel bottomPanel;
    private boolean distractionFree = false;
    private JPanel dfHeader;
    // Guided question mode
    private String[] guidedQuestions;
    private int currentQuestionIndex = 0;
    private JPanel questionBubble;
    private JLabel questionLabel;
    private RoundedButton prevQuestionBtn;
    private RoundedButton nextQuestionBtn;
    private JLabel questionCountLabel;
    // Store text content per question
    private java.util.Map<Integer, String> questionResponses = new java.util.HashMap<>();

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
            
            // Check for guided mode metadata
            String firstContentLine = reader.readLine();
            if (firstContentLine != null && firstContentLine.startsWith("[GUIDED_MODE:")) {
                // Parse guided mode metadata: [GUIDED_MODE:template_name]
                int endIdx = firstContentLine.indexOf(']');
                if (endIdx > 0) {
                    String templateName = firstContentLine.substring(13, endIdx).trim();
                    // Find matching template and restore guided mode
                    restoreGuidedMode(templateName, reader);
                    return;
                }
            }
            
            // Regular mode: Read the remainder into a string
            StringBuilder rest = new StringBuilder();
            if (firstContentLine != null) {
                rest.append(firstContentLine).append("\n");
            }
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
                // Important: set the loaded document and DO NOT replace the editor kit afterwards,
                // or Swing will create a new default document and drop our loaded content.
                contentArea.setDocument(doc);
                ensureSimStyles();
            } else {
                contentArea.setText(remainder);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading journal entry.", true).showDialog();
        }
    }

    // --- Formatting helpers used by shared toolbar ---
    private void applyInlineStyleBold() {
        try {
            StyledDocument doc = (StyledDocument) contentArea.getDocument();
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) return;
            javax.swing.text.AttributeSet selectionAttrs = ((StyledEditorKit) contentArea.getEditorKit()).getInputAttributes();
            boolean enable = !StyleConstants.isBold(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setBold(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Exception ignored) {}
    }

    private void applyInlineStyleItalic() {
        try {
            StyledDocument doc = (StyledDocument) contentArea.getDocument();
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) return;
            javax.swing.text.AttributeSet selectionAttrs = ((StyledEditorKit) contentArea.getEditorKit()).getInputAttributes();
            boolean enable = !StyleConstants.isItalic(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setItalic(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Exception ignored) {}
    }

    private void applyInlineStyleUnderline() {
        try {
            StyledDocument doc = (StyledDocument) contentArea.getDocument();
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) return;
            javax.swing.text.AttributeSet selectionAttrs = ((StyledEditorKit) contentArea.getEditorKit()).getInputAttributes();
            boolean enable = !StyleConstants.isUnderline(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setUnderline(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Exception ignored) {}
    }

    private void applyInlineStyleStrike() {
        try {
            StyledDocument doc = (StyledDocument) contentArea.getDocument();
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) return;
            javax.swing.text.AttributeSet selectionAttrs = ((StyledEditorKit) contentArea.getEditorKit()).getInputAttributes();
            boolean enable = !StyleConstants.isStrikeThrough(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setStrikeThrough(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Exception ignored) {}
    }

    private void applyParagraphFontToAll() {
        try {
            StyledDocument doc = (StyledDocument) contentArea.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attrs, contentArea.getFont().getFamily());
            StyleConstants.setFontSize(attrs, contentArea.getFont().getSize());
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        } catch (Exception ignored) {}
    }

    private void applyLineSpacing(String val) {
        float spacing = switch (val) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };
        try {
            StyledDocument doc = (StyledDocument) contentArea.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        } catch (Exception ignored) {}
    }

    // --- Typing style (affects new text via input attributes) ---
    private void setTypingStyleBold(boolean on) { RichTextStyler.setTypingBold(contentArea, on); }
    private void setTypingStyleItalic(boolean on) { RichTextStyler.setTypingItalic(contentArea, on); }
    private void setTypingStyleUnderline(boolean on) { RichTextStyler.setTypingUnderline(contentArea, on); }
    private void setTypingStyleStrike(boolean on) { RichTextStyler.setTypingStrike(contentArea, on); }

    private void toggleDistractionFree() {
        distractionFree = !distractionFree;
        // Swap toolbar with minimal df header in NORTH and hide bottom panel
        if (distractionFree) {
            try { remove(toolbarContainer); } catch (Throwable ignored) {}
            add(dfHeader, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(false);
        } else {
            try { remove(dfHeader); } catch (Throwable ignored) {}
            add(toolbarContainer, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(true);
        }
        revalidate();
        repaint();
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
        toolbarContainer = new JPanel(new BorderLayout(0, 5));
        // Solid background so the page wallpaper does not seep through the toolbar
        toolbarContainer.setOpaque(true);
        toolbarContainer.setBackground(new Color(0xE7, 0xE7, 0xE7)); // #e7e7e7
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Build right-side controls (journal-specific)
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.setOpaque(false);
        ToolbarIconButton dfBtn = new ToolbarIconButton("fullscreen");
        dfBtn.setToolTipText("Distraction-Free Mode");
        dfBtn.addActionListener(e -> toggleDistractionFree());
        ToolbarIconButton settingsBtn = new ToolbarIconButton("options");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            EntryBackgroundDialog dialog = new EntryBackgroundDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        ToolbarIconButton guidanceBtn = new ToolbarIconButton("simguidance");
        guidanceBtn.setToolTipText("Ask Sim for guidance on this entry");
        guidanceBtn.addActionListener(e -> {
            try {
                Document doc = contentArea.getDocument();
                String all = doc.getText(0, doc.getLength());
                int cap = 4000;
                String text = all.length() > cap ? all.substring(all.length() - cap) : all;
                showGuidanceThinkingPlaceholder();
                SimEventBus.get().emitGuidanceRequested(text);
            } catch (BadLocationException ex) { /* no-op */ }
        });
        rightToolbar.add(guidanceBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));
        rightToolbar.add(dfBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));
        rightToolbar.add(settingsBtn);

        // Create shared poetry-style toolbar
        NotebookInfo nbInfo = new NotebookInfo(
                journalFolder.getName(),
                NotebookInfo.Type.JOURNAL,
                journalFolder,
                journalFolder.lastModified(),
                null
        );
        main.ui.components.toolbars.PoetryStyleToolbar sharedToolbar = new main.ui.components.toolbars.PoetryStyleToolbar(
                app,
                nbInfo,
                "Title:",
                "Untitled entry",
                (selected) -> setTypingStyleBold(selected),
                (selected) -> setTypingStyleItalic(selected),
                (selected) -> setTypingStyleUnderline(selected),
                (selected) -> setTypingStyleStrike(selected),
                (fontName) -> {
                    Font current = contentArea.getFont();
                    contentArea.setFont(new Font(fontName, current.getStyle(), current.getSize()));
                    applyParagraphFontToAll();
                },
                (size) -> {
                    contentArea.setFont(contentArea.getFont().deriveFont(size.floatValue()));
                    applyParagraphFontToAll();
                    SettingsStore.get().setJournalFontSize(size);
                    SettingsStore.get().save();
                },
                this::applyLineSpacing,
                rightToolbar
        );
        // Bind the shared title field to our reference used elsewhere
        titleField = sharedToolbar.getTitleField();

        // Build mood slider stack to show under the shared toolbar
        JPanel moodRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        moodRow.setOpaque(false);
        moodSlider = new MoodSlider();
        moodRow.add(moodSlider);
        moodSlider.addChangeListener(e -> {
            try { SimEventBus.get().emitMoodChanged((double) moodSlider.getValue()); } catch (Throwable ignored) {}
        });
        RoundedButton expandMoodBtn = new RoundedButton("\u203A");
        expandMoodBtn.setToolTipText("Open detailed mood logging");
        expandMoodBtn.setForeground(AeroTheme.TEXT_PRIMARY);
        expandMoodBtn.setPreferredSize(new Dimension(28, 28));
        expandMoodBtn.setMargin(new Insets(0, 0, 0, 0));
        moodRow.add(expandMoodBtn);
        JPanel bottomStack = new JPanel();
        bottomStack.setOpaque(false);
        bottomStack.setLayout(new BoxLayout(bottomStack, BoxLayout.Y_AXIS));
        bottomStack.add(moodRow);
        detailedMoodPanel = new DetailedMoodPanel(composite -> {
            moodSlider.setValue(composite);
            recordMood(composite);
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Mood Logged",
                    "Detailed mood saved (" + composite + ")",
                    false).showDialog();
        });
        bottomStack.add(detailedMoodPanel);
        expandMoodBtn.addActionListener(e -> {
            boolean next = detailedMoodPanel == null || !detailedMoodPanel.isExpanded();
            if (detailedMoodPanel != null) detailedMoodPanel.setExpanded(next);
            expandMoodBtn.setText(next ? "\u2039" : "\u203A");
        });

        // Wrap shared toolbar + mood stack into a single NORTH container
        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.setOpaque(false);
        northWrapper.add(sharedToolbar, BorderLayout.NORTH);
        northWrapper.add(bottomStack, BorderLayout.CENTER);

        toolbarContainer.add(northWrapper, BorderLayout.CENTER);
        toolbarContainer.add(rightToolbar, BorderLayout.EAST);

        add(toolbarContainer, BorderLayout.NORTH);

        // Distraction-free header: only Back button, no other controls
        dfHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dfHeader.setOpaque(false);
        dfHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        // Use the standard back icon, but here it exits fullscreen
        ToolbarIconButton dfBack = new ToolbarIconButton("back");
        dfBack.setToolTipText("Exit Fullscreen");
        dfBack.addActionListener(e -> toggleDistractionFree());
        dfHeader.add(dfBack);

        // --- Content Area (match PoemPanel style) ---
        // Use the same paper-like rounded rectangle container as in PoemPanel
        JPanel textWrapper = new TranslucentPanel() {
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
        };
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Content Area: Rich text editor (StyledDocument)
        contentArea = new JTextPane();

        // Load font size directly from settings to ensure persistence
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        contentArea.setFont(new Font("Serif", Font.ITALIC, savedFontSize));
        // JTextPane handles wrapping automatically via view; ensure editor kit is styled
        contentArea.setEditorKit(new StyledEditorKit());
        contentArea.setOpaque(false);
        // Match poem editor text color
        contentArea.setForeground(new Color(40, 40, 40));
        ensureSimStyles();

        // Enable rich image paste & drag-and-drop into the editor
        ImagePasteManager.install(
                contentArea,
                () -> new File(journalFolder, "attachments"),
                800 // max width in pixels for inserted images
        );

        // Keep formatting toggles in sync with caret/selection changes
        contentArea.addCaretListener(e -> updateFormattingToggleState());

        // Sync toolbar toggle states from caret typing attributes (after contentArea exists)
        contentArea.addCaretListener(e -> {
            RichTextStyler.StyleState st = RichTextStyler.getTypingState(contentArea);
            sharedToolbar.setToggleStates(st.bold(), st.italic(), st.underline(), st.strike());
        });

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
        // Apply modern, slim scrollbars (match PoemPanel)
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

        // Add scroll pane to the translucent wrapper (no inline formatting bar)
        textWrapper.setLayout(new BorderLayout());
        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Add some vertical space between toolbar and content (like PoemPanel)
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        
        // Create guided question bubble (hidden by default)
        createQuestionBubble();
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.setOpaque(false);
        topArea.add(Box.createRigidArea(new Dimension(0, 15)), BorderLayout.NORTH);
        topArea.add(questionBubble, BorderLayout.CENTER);
        centerContainer.add(topArea, BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);

        // Add to main panel
        add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Panel: Save Button ---
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
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
        // Ensure a normal/user text style exists (used to reset typing attributes)
        Style normal = sd.getStyle("normalText");
        if (normal == null) {
            normal = sd.addStyle("normalText", null);
            StyleConstants.setForeground(normal, AeroTheme.TEXT_PRIMARY);
            // Default character attributes (not bold/italic/underline)
            StyleConstants.setBold(normal, false);
            StyleConstants.setItalic(normal, false);
            StyleConstants.setUnderline(normal, false);
        }
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
            Style normal = sd.getStyle("normalText");
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
            // Append an invisible zero-width space with normal style so caret sits on a normal run
            int afterGuidance = sd.getLength();
            if (normal != null) {
                sd.insertString(afterGuidance, "\u200B", normal);
                contentArea.setCaretPosition(sd.getLength());
                // Also reset input attributes defensively
                contentArea.setCharacterAttributes(normal, true);
            } else {
                contentArea.setCaretPosition(sd.getLength());
            }
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

            // Save title + a blank line + content (with guided mode metadata if applicable)
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println(title);
                writer.println(); // separator
                
                // Save guided mode if active
                if (guidedQuestions != null && guidedQuestions.length > 0) {
                    // Save current question's response first
                    questionResponses.put(currentQuestionIndex, contentArea.getText());
                    
                    // Write guided mode metadata
                    writer.println("[GUIDED_MODE:" + getGuidedModeTemplateName() + "]");
                    writer.println();
                    
                    // Write all question-response pairs
                    for (int i = 0; i < guidedQuestions.length; i++) {
                        writer.println("[Q" + (i+1) + ": " + guidedQuestions[i] + "]");
                        String response = questionResponses.getOrDefault(i, "");
                        if (!response.trim().isEmpty()) {
                            writer.println(response);
                        }
                        writer.println();
                    }
                } else {
                    // Regular mode: just header written here; RTF appended below
                    writer.flush();
                }
            }
            
            // For non-guided mode, append RTF styling
            if (guidedQuestions == null || guidedQuestions.length == 0) {
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
            }

            // Mark last successful save for status bar
            LastSaveTracker.markSaved();

            // Remember as last opened file for startup restore
            try {
                SettingsStore.get().setLastOpenedFilePath(file.getAbsolutePath());
                SettingsStore.get().save();
            } catch (Throwable ignored) {}

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

    @Override
    public void setInitialContent(String content) {
        if (content != null && !content.isEmpty()) {
            contentArea.setText(content);
            // Position cursor at the end so user can start typing immediately
            contentArea.setCaretPosition(content.length());
        }
    }

    @Override
    public void setGuidedQuestions(String[] questions) {
        this.guidedQuestions = questions;
        this.currentQuestionIndex = 0;
        this.questionResponses.clear();
        if (questions != null && questions.length > 0) {
            showQuestion(0);
        }
    }

    private void createQuestionBubble() {
        questionBubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 16;
                // Semi-opaque white gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 200),
                    0, getHeight(), new Color(255, 255, 255, 180)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                // Border
                g2.setColor(new Color(200, 200, 200, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        questionBubble.setOpaque(false);
        questionBubble.setLayout(new BorderLayout(12, 0));
        questionBubble.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        questionBubble.setVisible(false);
        
        questionLabel = new JLabel();
        questionLabel.setFont(questionLabel.getFont().deriveFont(Font.PLAIN, 15f));
        questionLabel.setForeground(new Color(40, 40, 40));
        questionBubble.add(questionLabel, BorderLayout.CENTER);
        
        // Navigation buttons panel
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        navPanel.setOpaque(false);
        
        // Question counter (e.g., "2 / 4")
        questionCountLabel = new JLabel();
        questionCountLabel.setFont(questionCountLabel.getFont().deriveFont(Font.PLAIN, 12f));
        questionCountLabel.setForeground(new Color(100, 100, 100));
        navPanel.add(questionCountLabel);
        
        prevQuestionBtn = new RoundedButton("← Previous");
        prevQuestionBtn.setPreferredSize(new Dimension(110, 32));
        prevQuestionBtn.addActionListener(e -> previousQuestion());
        navPanel.add(prevQuestionBtn);
        
        nextQuestionBtn = new RoundedButton("Next →");
        nextQuestionBtn.setPreferredSize(new Dimension(110, 32));
        nextQuestionBtn.addActionListener(e -> nextQuestion());
        navPanel.add(nextQuestionBtn);
        
        questionBubble.add(navPanel, BorderLayout.EAST);
    }

    private void showQuestion(int index) {
        if (guidedQuestions == null || guidedQuestions.length == 0) {
            questionBubble.setVisible(false);
            return;
        }
        
        // Save current question's response before switching
        if (currentQuestionIndex >= 0 && currentQuestionIndex < guidedQuestions.length) {
            questionResponses.put(currentQuestionIndex, contentArea.getText());
        }
        
        // Clamp index to valid range
        if (index < 0) index = 0;
        if (index >= guidedQuestions.length) index = guidedQuestions.length - 1;
        
        currentQuestionIndex = index;
        questionLabel.setText(guidedQuestions[index]);
        
        // Update question counter (e.g., "2 / 4")
        questionCountLabel.setText((index + 1) + " / " + guidedQuestions.length);
        
        // Restore this question's response
        String savedResponse = questionResponses.getOrDefault(index, "");
        contentArea.setText(savedResponse);
        contentArea.setCaretPosition(savedResponse.length());
        
        // Enable/disable buttons based on position
        prevQuestionBtn.setEnabled(index > 0);
        nextQuestionBtn.setEnabled(index < guidedQuestions.length - 1);
        
        questionBubble.setVisible(true);
        questionBubble.revalidate();
        questionBubble.repaint();
    }

    private void nextQuestion() {
        if (guidedQuestions == null || currentQuestionIndex >= guidedQuestions.length - 1) {
            return; // Already at last question
        }
        showQuestion(currentQuestionIndex + 1);
    }
    
    private void previousQuestion() {
        if (guidedQuestions == null || currentQuestionIndex <= 0) {
            return; // Already at first question
        }
        showQuestion(currentQuestionIndex - 1);
    }
    
    private String getGuidedModeTemplateName() {
        // Infer template name from first question (simplified mapping)
        if (guidedQuestions == null || guidedQuestions.length == 0) return "BLANK";
        String firstQ = guidedQuestions[0].toLowerCase();
        if (firstQ.contains("grateful")) return "GRATITUDE";
        if (firstQ.contains("anxious")) return "ANXIETY";
        if (firstQ.contains("morning")) return "DAILY_LOG";
        if (firstQ.contains("mood")) return "MOOD_TRACKER";
        if (firstQ.contains("priorities")) return "GOAL_PLANNING";
        if (firstQ.contains("went well")) return "REFLECTION";
        return "UNKNOWN";
    }
    
    private void restoreGuidedMode(String templateName, BufferedReader reader) throws Exception {
        // Map template name back to questions using manager
        JournalTemplateManager.JournalTemplate template = JournalTemplateManager.getInstance().getTemplateById(templateName);
        if (template == null) {
            // Template not found, load as regular
            return;
        }
        
        this.guidedQuestions = template.getQuestions();
        this.questionResponses.clear();
        
        // Parse saved question-response pairs
        String line;
        int currentQ = -1;
        StringBuilder currentResponse = new StringBuilder();
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("[Q")) {
                // Save previous response
                if (currentQ >= 0) {
                    questionResponses.put(currentQ, currentResponse.toString().trim());
                    currentResponse = new StringBuilder();
                }
                // Extract question number
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String qNum = line.substring(2, colonIdx).trim();
                    try {
                        currentQ = Integer.parseInt(qNum) - 1;
                    } catch (NumberFormatException ignored) {}
                }
            } else if (currentQ >= 0 && !line.trim().isEmpty()) {
                currentResponse.append(line).append("\n");
            }
        }
        
        // Save last response
        if (currentQ >= 0) {
            questionResponses.put(currentQ, currentResponse.toString().trim());
        }
        
        // Show first question
        showQuestion(0);
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
