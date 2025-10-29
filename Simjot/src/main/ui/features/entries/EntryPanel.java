package main.ui.features.entries;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalTime;
import java.time.ZoneId;
import javax.swing.*;
import javax.imageio.ImageIO;
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
import main.ui.dialog.message.UIMessage;
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
    private UndoRedoManager titleUndoManager;
    private UndoRedoManager contentUndoManager;

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

    /**
     * Whether this editor should display mood controls under the toolbar.
     * Subclasses can override to disable (e.g., NotetakingPanel).
     */
    protected boolean supportsMoodControls() { return true; }

    /**
     * Whether to show the clock (insert time) button in the right toolbar.
     */
    protected boolean supportsClockButton() { return true; }

    /**
     * Whether to show the Sim guidance button in the right toolbar.
     */
    protected boolean supportsGuidanceButton() { return true; }

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
            // Extract optional image manifest header before RTF
            String manifest = null;
            String r = remainder;
            int nl = r.indexOf('\n');
            if (nl >= 0) {
                String head = r.substring(0, nl).trim();
                if (head.startsWith("IMGMAP:")) {
                    manifest = head.length() > 7 ? head.substring(7).trim() : "";
                    // Advance past header and an optional extra blank line
                    r = r.substring(nl + 1);
                    r = r.stripLeading();
                }
            }
            if (r.stripLeading().startsWith("{\\rtf")) {
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
                try (ByteArrayInputStream bin = new ByteArrayInputStream(r.getBytes())) {
                    kit.read(bin, doc, 0);
                }
                contentArea.setDocument(doc);
                ensureSimStyles();
                // Reinsert icons from manifest using saved offsets and filenames
                if (manifest != null && !manifest.isBlank()) {
                    try { applyImageManifest(doc, manifest); } catch (Throwable ignored) {}
                }
            } else {
                contentArea.setText(r);
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
        if (supportsClockButton()) {
            JButton clockBtn = new ClockToolbarButton();
            clockBtn.setToolTipText("Insert time snapshot");
            clockBtn.addActionListener(e -> insertClockSnapshot());
            rightToolbar.add(clockBtn);
            rightToolbar.add(Box.createHorizontalStrut(6));
        }
        if (supportsGuidanceButton()) {
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
        }
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

        // Build mood slider stack to show under the shared toolbar (optional)
        JPanel bottomStack = null;
        if (supportsMoodControls()) {
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
            bottomStack = new JPanel();
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
            RoundedButton finalExpandMoodBtn = expandMoodBtn;
            expandMoodBtn.addActionListener(e -> {
                boolean next = detailedMoodPanel == null || !detailedMoodPanel.isExpanded();
                if (detailedMoodPanel != null) detailedMoodPanel.setExpanded(next);
                finalExpandMoodBtn.setText(next ? "\u2039" : "\u203A");
            });
        }

        // Wrap shared toolbar + mood stack into a single NORTH container
        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.setOpaque(false);
        northWrapper.add(sharedToolbar, BorderLayout.NORTH);
        if (bottomStack != null) {
            northWrapper.add(bottomStack, BorderLayout.CENTER);
        }

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
        this.contentUndoManager = new UndoRedoManager(contentArea);
        this.titleUndoManager = new UndoRedoManager(titleField);

        // Add document listener for word count
        contentArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty();
            }
        });
        // Autosave on title change too
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveManager != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveManager.markDirty(); }
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

    private void insertClockSnapshot() {
        try {
            int size = 220;
            BufferedImage img = renderClock(size, size);
            File dir = new File(journalFolder, "attachments");
            if (!dir.exists()) dir.mkdirs();
            String name = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new java.util.Date()) + "_clock.png";
            File out = new File(dir, name);
            try { ImageIO.write(img, "PNG", out); } catch (IOException ignored) {}

            ImageIcon icon = new ImageIcon(img);
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute("imageSourceFile", out);
            StyledDocument doc = contentArea.getStyledDocument();
            int pos = contentArea.getCaretPosition();
            doc.insertString(pos, " ", attrs);
            doc.insertString(pos + 1, "\n", null);

            ensureSimStyles();
            Style normal = doc.getStyle("normalText");
            String sep = "——————————————";
            doc.insertString(pos + 2, sep + "\n\n", normal);
            contentArea.setCaretPosition(Math.min(doc.getLength(), pos + 2 + sep.length() + 2));
            contentArea.requestFocusInWindow();
        } catch (BadLocationException ignored) {}
    }

    private static BufferedImage renderClock(int w, int h) {
        int size = Math.max(32, Math.min(w, h));
        BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int cx = size / 2;
        int cy = size / 2;
        int r = Math.max(20, size / 2 - 2);

        Graphics2D sh = (Graphics2D) g2.create();
        sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        sh.setPaint(new RadialGradientPaint(new Point(cx, cy + r/3), r,
                new float[]{0f, 1f}, new Color[]{new Color(0,0,0,60), new Color(0,0,0,0)}));
        sh.fillOval(cx - r - 4, cy - r + 8, (r * 2) + 8, (r * 2) - 6);
        sh.dispose();

        Paint bezel = new LinearGradientPaint(cx, cy - r, cx, cy + r,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(230,230,230), new Color(190,190,190), new Color(220,220,220)});
        g2.setPaint(bezel);
        g2.fillOval(cx - r, cy - r, r*2, r*2);

        int dialInset = Math.max(4, (int)(r * 0.06));
        int dialR = r - dialInset;
        java.awt.geom.Point2D center = new java.awt.geom.Point2D.Float(cx, cy);
        RadialGradientPaint dialPaint = new RadialGradientPaint(center, dialR,
                new float[]{0f, 0.85f, 1f},
                new Color[]{new Color(255,255,255), new Color(242,242,242), new Color(230,230,230)});
        g2.setPaint(dialPaint);
        g2.fillOval(cx - dialR, cy - dialR, dialR*2, dialR*2);

        g2.setColor(new Color(180,180,180));
        g2.setStroke(new BasicStroke(Math.max(1f, r * 0.02f)));
        g2.drawOval(cx - dialR, cy - dialR, dialR*2, dialR*2);

        int tickOuter = dialR - Math.max(2, (int)(r * 0.02));
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            boolean hour = (i % 5 == 0);
            int len = hour ? Math.max(12, (int)(r * 0.10)) : Math.max(5, (int)(r * 0.05));
            int inner = tickOuter - len;
            int x1 = cx + (int)(inner * Math.cos(a));
            int y1 = cy + (int)(inner * Math.sin(a));
            int x2 = cx + (int)(tickOuter * Math.cos(a));
            int y2 = cy + (int)(tickOuter * Math.sin(a));
            if (hour) {
                g2.setColor(new Color(80,80,80));
                g2.setStroke(new BasicStroke(Math.max(2f, r * 0.02f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            } else {
                g2.setColor(new Color(120,120,120,160));
                g2.setStroke(new BasicStroke(Math.max(1f, r * 0.013f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            g2.drawLine(x1, y1, x2, y2);
        }

        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        double hourAngle = Math.toRadians((hours + minutes / 60.0 + seconds / 3600.0) * 30 - 180);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 180);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        int hourLen = (int)(dialR * 0.55);
        int minuteLen = (int)(dialR * 0.78);
        int secondLen = (int)(dialR * 0.84);

        {
            int base = (int)(dialR * 0.10);
            int tipW = Math.max(3, (int)(r * 0.04));
            int baseW = Math.max(tipW + 2, (int)(r * 0.07));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(-baseW/2.0, 0);
            p.lineTo(baseW/2.0, 0);
            p.lineTo(tipW/2.0, hourLen);
            p.lineTo(-tipW/2.0, hourLen);
            p.closePath();
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(cx, cy);
            at.rotate(hourAngle);
            at.translate(0, -base);
            Shape shp = at.createTransformedShape(p);
            g2.setPaint(new LinearGradientPaint(cx, cy - hourLen, cx, cy + hourLen,
                    new float[]{0f,1f}, new Color[]{new Color(70,70,70), new Color(30,30,30)}));
            g2.fill(shp);
            g2.setColor(new Color(20,20,20));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(shp);
        }

        {
            int base = (int)(dialR * 0.12);
            int tipW = Math.max(2, (int)(r * 0.03));
            int baseW = Math.max(tipW + 2, (int)(r * 0.055));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(-baseW/2.0, 0);
            p.lineTo(baseW/2.0, 0);
            p.lineTo(tipW/2.0, minuteLen);
            p.lineTo(-tipW/2.0, minuteLen);
            p.closePath();
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(cx, cy);
            at.rotate(minuteAngle);
            at.translate(0, -base);
            Shape shp = at.createTransformedShape(p);
            g2.setPaint(new LinearGradientPaint(cx, cy - minuteLen, cx, cy + minuteLen,
                    new float[]{0f,1f}, new Color[]{new Color(90,90,90), new Color(40,40,40)}));
            g2.fill(shp);
            g2.setColor(new Color(25,25,25));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(shp);
        }

        {
            int tail = (int)(dialR * 0.20);
            int cwR = Math.max(3, (int)(r * 0.045));
            double cos = Math.cos(secondAngle), sin = Math.sin(secondAngle);
            int xTip = cx + (int)(secondLen * cos);
            int yTip = cy + (int)(secondLen * sin);
            int xTail = cx - (int)(tail * cos);
            int yTail = cy - (int)(tail * sin);
            g2.setStroke(new BasicStroke(Math.max(1f, r * 0.012f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(200, 40, 40));
            g2.drawLine(xTail, yTail, xTip, yTip);
            Paint redGlass = new RadialGradientPaint(new Point(cx, cy), cwR,
                    new float[]{0f,1f}, new Color[]{new Color(255, 80, 80), new Color(180,20,20)});
            g2.setPaint(redGlass);
            g2.fillOval(cx - cwR, cy - cwR, cwR*2, cwR*2);
            g2.setColor(new Color(120,0,0,180));
            g2.drawOval(cx - cwR, cy - cwR, cwR*2, cwR*2);
        }

        int capR = Math.max(3, (int)(r * 0.06));
        Paint cap = new RadialGradientPaint(new Point(cx - capR/3, cy - capR/3), capR,
                new float[]{0f, 0.65f, 1f},
                new Color[]{new Color(255,255,255), new Color(210,210,210), new Color(160,160,160)});
        g2.setPaint(cap);
        g2.fillOval(cx - capR, cy - capR, capR*2, capR*2);
        g2.setColor(new Color(90,90,90));
        g2.drawOval(cx - capR, cy - capR, capR*2, capR*2);

        Graphics2D hg = (Graphics2D) g2.create();
        hg.setClip(new java.awt.geom.Ellipse2D.Double(cx - dialR, cy - dialR, dialR*2, dialR*2));
        hg.setPaint(new GradientPaint(0, cy - dialR, new Color(255,255,255,120), 0, cy, new Color(255,255,255,0)));
        hg.fillRoundRect(cx - dialR + 4, cy - dialR + 4, dialR*2 - 8, dialR - 6, dialR, dialR);
        hg.dispose();

        g2.dispose();
        return bi;
    }

    private static BufferedImage renderSmallClockIcon(int w, int h) {
        int s = Math.max(12, Math.min(w, h));
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int cx = s / 2;
        int cy = s / 2;
        int r = Math.max(5, (int) Math.round(s * 0.48));

        // Dial background
        Paint dial = new RadialGradientPaint(new Point(cx, cy), r,
                new float[]{0f, 1f}, new Color[]{new Color(250, 250, 250), new Color(220, 220, 220)});
        g2.setPaint(dial);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(new Color(160, 160, 160));
        g2.setStroke(new BasicStroke(Math.max(1f, s * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // Hour markers (12 only)
        g2.setStroke(new BasicStroke(Math.max(1f, s * 0.07f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(120, 120, 120));
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(i * 30 - 90);
            double cos = Math.cos(ang), sin = Math.sin(ang);
            int r0 = (int) Math.round(r * 0.72);
            int r1 = (int) Math.round(r * 0.88);
            int x0 = cx + (int) Math.round(r0 * cos);
            int y0 = cy + (int) Math.round(r0 * sin);
            int x1 = cx + (int) Math.round(r1 * cos);
            int y1 = cy + (int) Math.round(r1 * sin);
            g2.drawLine(x0, y0, x1, y1);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hr = now.getHour() % 12;
        int min = now.getMinute();
        int sec = now.getSecond();

        double ah = Math.toRadians(((hr + min / 60.0 + sec / 3600.0) * 30.0) - 90);
        double am = Math.toRadians((min + sec / 60.0) * 6.0 - 90);
        double as = Math.toRadians(sec * 6.0 - 90);

        // Hands
        // Hour hand
        g2.setColor(new Color(60, 60, 60));
        g2.setStroke(new BasicStroke(Math.max(1.2f, s * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int hLen = (int) Math.round(r * 0.52);
        int hx = cx + (int) Math.round(hLen * Math.cos(ah));
        int hy = cy + (int) Math.round(hLen * Math.sin(ah));
        g2.drawLine(cx, cy, hx, hy);

        // Minute hand
        g2.setColor(new Color(40, 40, 40));
        g2.setStroke(new BasicStroke(Math.max(1.0f, s * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int mLen = (int) Math.round(r * 0.76);
        int mx = cx + (int) Math.round(mLen * Math.cos(am));
        int my = cy + (int) Math.round(mLen * Math.sin(am));
        g2.drawLine(cx, cy, mx, my);

        // Second hand (thin red)
        g2.setColor(new Color(200, 30, 30));
        g2.setStroke(new BasicStroke(Math.max(0.8f, s * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int sLen = (int) Math.round(r * 0.82);
        int sx = cx + (int) Math.round(sLen * Math.cos(as));
        int sy = cy + (int) Math.round(sLen * Math.sin(as));
        g2.drawLine(cx, cy, sx, sy);

        // Center cap
        int cap = Math.max(2, (int) Math.round(s * 0.12));
        g2.setPaint(new RadialGradientPaint(new Point(cx - cap / 3, cy - cap / 3), cap,
                new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255), new Color(160, 160, 160)}));
        g2.fillOval(cx - cap, cy - cap, cap * 2, cap * 2);
        g2.setColor(new Color(90, 90, 90));
        g2.drawOval(cx - cap, cy - cap, cap * 2, cap * 2);

        g2.dispose();
        return bi;
    }

    private static final class ClockToolbarButton extends JButton {
        private javax.swing.Timer timer;
        ClockToolbarButton(){
            setPreferredSize(new Dimension(40, 40));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
        }
        @Override public void addNotify(){
            super.addNotify();
            if (timer == null) {
                timer = new javax.swing.Timer(1000, e -> repaint());
                timer.start();
            }
        }
        @Override public void removeNotify(){
            if (timer != null) { timer.stop(); timer = null; }
            super.removeNotify();
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight();
            Color top = new Color(252,252,252,230);
            Color bottom = new Color(226,226,226,230);
            Rectangle r = new Rectangle(0,0,w,h);
            GradientPaint gp = new GradientPaint(0,0,top,0,h,bottom);
            g2.setPaint(gp);
            g2.fillRoundRect(0,0,w,h,10,10);
            g2.setPaint(new GradientPaint(0,0,new Color(255,255,255,170),0,h/2f,new Color(255,255,255,0)));
            g2.fillRoundRect(1,1,w-2,h/2,9,9);
            g2.setColor(new Color(180,180,180));
            g2.drawRoundRect(0,0,w-1,h-1,10,10);

            int size = Math.max(18, Math.min(w, h) - 16);
            BufferedImage img = renderStatic(size);
            if (img != null) {
                g2.drawImage(img, (w - img.getWidth())/2, (h - img.getHeight())/2, null);
            }
            g2.dispose();
        }
        private BufferedImage renderStatic(int s){
            int ss = Math.max(16, s);
            int pad = 1;
            BufferedImage clock = renderSmallClockIcon(ss - 2 * pad, ss - 2 * pad);
            if (clock == null) return null;
            BufferedImage out = new BufferedImage(ss, ss, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(clock, pad, pad, null);
            g2.dispose();
            return out;
        }
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
        if (autosaveManager != null) {
            try { autosaveManager.stop(); } catch (Throwable ignored) {}
        }
        // Snapshot UI state on EDT to avoid touching Swing components from autosave thread
        final String[] titleHolder = new String[1];
        final String[] contentHolder = new String[1];
        final int[] moodHolder = new int[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                titleHolder[0] = titleField.getText().trim();
                contentHolder[0] = contentArea.getText();
                moodHolder[0] = (moodSlider != null) ? moodSlider.getValue() : -1;
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    titleHolder[0] = titleField.getText().trim();
                    contentHolder[0] = contentArea.getText();
                    moodHolder[0] = (moodSlider != null) ? moodSlider.getValue() : -1;
                });
            }
        } catch (Exception invokeErr) {
            // If we cannot read UI state, fail gracefully
            return;
        }
        String title = titleHolder[0];
        String content = contentHolder[0];
        if (title.isEmpty() && content.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> UIMessage.error(this,
                    "Error",
                    "<b>Error:</b> cannot save entry without a content.\n\n\"Your entry is empty.\"",
                    "- Add a title or a few lines\n- Press Save again"));
            return;
        }
        int moodValue = moodHolder[0]; // 0 - 100
        if (moodValue >= 0) {
            recordMood(moodValue);
        }

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
            String manifestForRestore = null;
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println(title);
                writer.println(); // separator
                
                // Save guided mode if active
                if (guidedQuestions != null && guidedQuestions.length > 0) {
                    // Save current question's response first (tokenize images)
                    try {
                        String tok = stringifyWithImageTokens((StyledDocument) contentArea.getDocument());
                        questionResponses.put(currentQuestionIndex, tok);
                    } catch (Throwable t) {
                        questionResponses.put(currentQuestionIndex, contentArea.getText());
                    }
                    
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
                    // Regular mode: write image manifest header, then RTF appended below
                    manifestForRestore = buildImageManifest((StyledDocument) contentArea.getDocument());
                    if (manifestForRestore == null) manifestForRestore = "";
                    writer.println("IMGMAP:" + manifestForRestore);
                    writer.println();
                    writer.flush();
                }
            }
            
            // For non-guided mode, append RTF styling
            if (guidedQuestions == null || guidedQuestions.length == 0) {
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    RTFEditorKit kit = new RTFEditorKit();
                    StyledDocument sd = (StyledDocument) contentArea.getDocument();
                    try {
                        // Document already tokenized by buildImageManifest for header; just write it.
                        kit.write(fos, sd, 0, sd.getLength());
                    } catch (BadLocationException ble) {
                        // fallback to plain text if unexpected
                        fos.write(content.getBytes());
                    }
                }
                // Restore icons back into the live document so the UI remains unchanged
                try {
                    StyledDocument sd = (StyledDocument) contentArea.getDocument();
                    if (manifestForRestore != null && !manifestForRestore.isBlank()) {
                        restoreIconsFromTokens(sd, manifestForRestore);
                    }
                } catch (Throwable ignored) {}
            }

            // Mark last successful save for status bar
            LastSaveTracker.markSaved();

            // Mark undo save-points so subsequent undos redo to a clean state
            try {
                if (contentUndoManager != null) contentUndoManager.markSavePoint();
                if (titleUndoManager != null) titleUndoManager.markSavePoint();
            } catch (Throwable ignored) {}

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

    // --- Image persistence helpers (tokenize -> write -> restore) ---
    private String buildImageManifest(StyledDocument doc) {
        try {
            int len = doc.getLength();
            StringBuilder sb = new StringBuilder(128);
            int pos = 0;
            while (pos < len) {
                javax.swing.text.Element el = doc.getCharacterElement(pos);
                if (el == null) { pos++; continue; }
                javax.swing.text.AttributeSet as = el.getAttributes();
                Object ico = javax.swing.text.StyleConstants.getIcon(as);
                if (ico instanceof ImageIcon) {
                    ImageIcon icon = (ImageIcon) ico;
                    int w = icon.getIconWidth();
                    int h = icon.getIconHeight();
                    Object srcAttr = as.getAttribute("imageSourceFile");
                    File srcFile = (srcAttr instanceof File) ? (File) srcAttr : null;
                    if (srcFile == null) {
                        // Persist icon to attachments if not already saved
                        File dir = new File(journalFolder, "attachments");
                        if (!dir.exists()) dir.mkdirs();
                        String name = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new java.util.Date()) + ".png";
                        srcFile = new File(dir, name);
                        try {
                            BufferedImage buf = toBuffered(icon.getImage());
                            ImageIO.write(buf, "PNG", srcFile);
                        } catch (Throwable ignored) {}
                    }
                    String rel = makeRelativeToJournal(srcFile);
                    // Replace the icon character with a token including rel path and size
                    String token = "[[IMG|" + rel + "|" + w + "x" + h + "]]";
                    try {
                        doc.remove(el.getStartOffset(), 1);
                        doc.insertString(el.getStartOffset(), token, null);
                        len = doc.getLength();
                        pos = el.getStartOffset() + token.length();
                    } catch (BadLocationException ignored) { pos++; }
                    // Append to manifest as a reference for completeness (not used in replacement)
                    if (sb.length() > 0) sb.append(';');
                    sb.append(rel).append('|').append(w).append('x').append(h);
                } else {
                    pos = el.getEndOffset();
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void restoreIconsFromTokens(StyledDocument doc, String manifest) {
        try {
            String text = doc.getText(0, doc.getLength());
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[\\[IMG\\|([^|]+)\\|(\\d+)x(\\d+)\\]\\]");
            java.util.regex.Matcher m = p.matcher(text);
            java.util.List<int[]> ranges = new java.util.ArrayList<>();
            java.util.List<String> rels = new java.util.ArrayList<>();
            java.util.List<Integer> widths = new java.util.ArrayList<>();
            while (m.find()) {
                ranges.add(new int[]{m.start(), m.end()});
                rels.add(m.group(1));
                widths.add(Integer.parseInt(m.group(2)));
            }
            // Replace from end to start to keep offsets valid
            for (int i = ranges.size() - 1; i >= 0; i--) {
                int start = ranges.get(i)[0];
                int end = ranges.get(i)[1];
                String rel = rels.get(i);
                int targetW = widths.get(i);
                File f = new File(journalFolder, rel);
                BufferedImage img = null;
                try { img = ImageIO.read(f); } catch (Throwable ignored) {}
                if (img == null) continue;
                BufferedImage scaled = (targetW > 0) ? scaleToWidth(img, targetW) : img;
                ImageIcon icon = new ImageIcon(scaled);
                javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setIcon(attrs, icon);
                attrs.addAttribute("imageSourceFile", f);
                try {
                    doc.remove(start, end - start);
                    doc.insertString(start, " ", attrs);
                } catch (BadLocationException ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void applyImageManifest(StyledDocument doc, String manifest) {
        // Current strategy relies on tokens in the RTF; the header is informational only.
        restoreIconsFromTokens(doc, manifest);
    }

    private static BufferedImage scaleToWidth(BufferedImage src, int targetW) {
        if (targetW <= 0 || src.getWidth() == targetW) return src;
        float scale = targetW / (float) src.getWidth();
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private static BufferedImage toBuffered(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage b = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = b.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return b;
    }

    private String makeRelativeToJournal(File f) {
        try {
            String base = journalFolder.getAbsolutePath();
            String path = f.getAbsolutePath();
            if (path.startsWith(base)) {
                String rel = path.substring(base.length());
                if (rel.startsWith(File.separator)) rel = rel.substring(1);
                return rel.replace('\\', '/');
            }
            return f.getName();
        } catch (Throwable t) {
            return f.getName();
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
        try {
            if (contentUndoManager != null) contentUndoManager.clearHistory();
            if (titleUndoManager != null) titleUndoManager.clearHistory();
            if (contentUndoManager != null) contentUndoManager.markSavePoint();
            if (titleUndoManager != null) titleUndoManager.markSavePoint();
        } catch (Throwable ignored) {}
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

    @Override
    public void removeNotify() {
        try { if (autosaveManager != null) autosaveManager.stop(); } catch (Throwable ignored) {}
        super.removeNotify();
    }

    private void createQuestionBubble() {
        questionBubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 16;
                // Solid semi-opaque background (no gradient)
                g2.setColor(new Color(255, 255, 255, 200));
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
        
        // Save current question's response before switching (tokenize images)
        if (currentQuestionIndex >= 0 && currentQuestionIndex < guidedQuestions.length) {
            try {
                String tok = stringifyWithImageTokens((StyledDocument) contentArea.getDocument());
                questionResponses.put(currentQuestionIndex, tok);
            } catch (Throwable t) {
                questionResponses.put(currentQuestionIndex, contentArea.getText());
            }
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
        contentArea.setText(savedResponse == null ? "" : savedResponse);
        try { restoreIconsFromTokens((StyledDocument) contentArea.getDocument(), ""); } catch (Throwable ignored) {}
        contentArea.setCaretPosition(contentArea.getDocument().getLength());
        
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
        // Robust restore: reconstruct questions and responses from the file content itself,
        // ignoring external template lookup. This keeps old entries stable even if templates change.

        java.util.List<String> questions = new java.util.ArrayList<>();
        this.questionResponses.clear();

        String line;
        int currentQ = -1;
        StringBuilder currentResponse = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("[Q")) {
                // Save previous response block
                if (currentQ >= 0) {
                    questionResponses.put(currentQ, currentResponse.toString().trim());
                    currentResponse = new StringBuilder();
                }
                // Parse header: [Qn: question text]
                int colonIdx = line.indexOf(':');
                int endBracket = line.lastIndexOf(']');
                String qText = "";
                if (colonIdx > 0 && endBracket > colonIdx) {
                    qText = line.substring(colonIdx + 1, endBracket).trim();
                }
                questions.add(qText);
                currentQ = questions.size() - 1;
            } else if (currentQ >= 0) {
                // Accumulate response (preserve blank lines between paragraphs)
                currentResponse.append(line).append("\n");
            }
        }

        // Save final response
        if (currentQ >= 0) {
            questionResponses.put(currentQ, currentResponse.toString().trim());
        }

        // Apply reconstructed questions; if none parsed, leave guided mode empty
        if (!questions.isEmpty()) {
            this.guidedQuestions = questions.toArray(new String[0]);
        } else {
            this.guidedQuestions = new String[0];
        }

        // Important: prevent showQuestion() from saving the (empty) editor content over Q0
        // by marking that there is no current question yet.
        this.currentQuestionIndex = -1;

        // Show first question (if any)
        showQuestion(0);
    }

    // Build a plain string from a StyledDocument, replacing any icon runs with [[IMG|rel|WxH]] tokens.
    private String stringifyWithImageTokens(StyledDocument doc) throws BadLocationException {
        StringBuilder out = new StringBuilder(doc.getLength() + 64);
        int pos = 0;
        int len = doc.getLength();
        while (pos < len) {
            javax.swing.text.Element el = doc.getCharacterElement(pos);
            if (el == null) { pos++; continue; }
            javax.swing.text.AttributeSet as = el.getAttributes();
            Object ico = javax.swing.text.StyleConstants.getIcon(as);
            if (ico instanceof ImageIcon) {
                ImageIcon icon = (ImageIcon) ico;
                int w = icon.getIconWidth();
                int h = icon.getIconHeight();
                Object srcAttr = as.getAttribute("imageSourceFile");
                File srcFile = (srcAttr instanceof File) ? (File) srcAttr : null;
                if (srcFile == null) {
                    // Persist icon to attachments if not already saved
                    File dir = new File(journalFolder, "attachments");
                    if (!dir.exists()) dir.mkdirs();
                    String name = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new java.util.Date()) + ".png";
                    srcFile = new File(dir, name);
                    try {
                        BufferedImage buf = toBuffered(icon.getImage());
                        ImageIO.write(buf, "PNG", srcFile);
                    } catch (Throwable ignored) {}
                }
                String rel = makeRelativeToJournal(srcFile);
                out.append("[[IMG|").append(rel).append('|').append(w).append('x').append(h).append("]] ");
                pos = el.getEndOffset();
            } else {
                int start = el.getStartOffset();
                int end = Math.min(el.getEndOffset(), len);
                String seg = doc.getText(start, end - start);
                out.append(seg);
                pos = end;
            }
        }
        return out.toString();
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
