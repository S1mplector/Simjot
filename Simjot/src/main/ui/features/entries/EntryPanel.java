/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

// Java Swing imports
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.rtf.RTFEditorKit;

import main.core.security.EncryptionManager;
import main.core.security.crypto.ContentType;
import main.core.security.crypto.CryptoConfig;
import main.core.security.crypto.CryptoException;
import main.core.security.crypto.EncryptedMetadata;
import main.core.service.LastSaveTracker;
import main.core.service.SettingsStore;
import main.core.sim.api.SimEventBus;
import main.core.spelling.AutocorrectDocumentFilter;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.IoLog;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.editor.FormattingHotkeyHandler;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.LinkManager;
import main.ui.components.editor.RichTextStyler;
import main.ui.components.fields.TitleDividerField;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.ui.components.popup.AnimatedGlassPopup;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.slider.MoodSlider;
import main.ui.components.util.EditorUIUtils;
import main.ui.dialog.confirmation.CustomChoiceDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.EntryBackgroundDialog;
import main.ui.features.editing.UndoRedoManager;
import main.ui.theme.aero.AeroTheme;
 
public class EntryPanel extends AbstractEditorPanel {

    // UI components for the entry
    protected TitleDividerField titleField;
    protected JTextPane contentArea;
    protected MoodSlider moodSlider;
    private DetailedMoodPanel detailedMoodPanel;
    private SaveIndicatorPanel saveIndicator;
    private boolean titleFocusedOnce = false;
    private AnimatedGlassPopup formatPopup;
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private NativeAutosaveCoordinator autosaveCoordinator;
    private volatile boolean isAutosaving = false;
    
    // Track temporary placeholder range for Sim guidance (disabled; retained for compatibility)
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
    private JPanel toolbarGroup;
    private JPanel moodContainer;
    private JPanel bottomPanel;
    private ToolbarIconButton saveButton;
    private boolean distractionFree = false;
    private boolean readOnlyMode = false;
    private FileLock entryLock;
    private JPanel recoveryBanner;
    private JLabel recoveryLabel;
    private JButton recoveryButton;
    private JButton recoveryDismissButton;
    private File recoveryCandidate;
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
    // Reusable document listener reference so we can reattach after setDocument()
    private javax.swing.event.DocumentListener editorDocListener;

    public EntryPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
        // Sim guidance disabled visually; listener intentionally not registered
    }

    private void prefillDetailedMoodFromLogToday() {
        if (detailedMoodPanel == null) return;
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        if (!moodFile.exists()) return;
        final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        LocalDate today = LocalDate.now();
        DetailedMoodPanel.DetailedMoodSnapshot bestSnap = null;
        Integer bestComposite = null;
        LocalDateTime bestTs = null;
        try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 10) {
                    try {
                        LocalDateTime ts = LocalDateTime.parse(parts[0].trim(), TS);
                        if (!ts.toLocalDate().equals(today)) continue;
                        int composite = Integer.parseInt(parts[1].trim());
                        int joy = Integer.parseInt(parts[2].trim());
                        int calm = Integer.parseInt(parts[3].trim());
                        int gratitude = Integer.parseInt(parts[4].trim());
                        int energy = Integer.parseInt(parts[5].trim());
                        int sadness = Integer.parseInt(parts[6].trim());
                        int anger = Integer.parseInt(parts[7].trim());
                        int anxiety = Integer.parseInt(parts[8].trim());
                        int stress = Integer.parseInt(parts[9].trim());
                        if (bestTs == null || ts.isAfter(bestTs)) {
                            bestTs = ts;
                            bestComposite = composite;
                            bestSnap = new DetailedMoodPanel.DetailedMoodSnapshot(joy, calm, gratitude, energy, sadness, anger, anxiety, stress);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (IOException ignored) {}
        if (bestSnap != null) {
            detailedMoodPanel.applySnapshot(bestSnap);
            try { if (bestComposite != null) moodSlider.setValue(bestComposite); } catch (Throwable ignored) {}
        }
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
    protected boolean supportsGuidanceButton() { return false; }

    protected void installExtraRightToolbarButtons(JPanel rightToolbar) { }
    protected void installContentOverlay(JComponent textWrapper, JScrollPane scrollPane) { }

    // Load an existing entry file into the editor fields
    private void loadExistingEntry(File fileToEdit) {
        if (!acquireEntryLock(fileToEdit)) {
            setReadOnlyMode(true);
            CustomMessageDialog.display(this, "Read-only", "This entry is open in another Simjot instance. Opened read-only.", true);
        } else {
            setReadOnlyMode(false);
        }
        if (!verifyIntegrityAndOfferRestore(fileToEdit)) {
            return;
        }
        hideRecoveryBanner();
        try (BufferedReader reader = openEntryReader(fileToEdit)) {
            if (reader == null) return;
            String firstLine = reader.readLine();
            applyEntryContent(reader, firstLine);
        } catch (CryptoException ex) {
            CustomMessageDialog.display(this, "Encryption", ex.getUserMessage(), true);
        } catch (Exception ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading journal entry.", true).showDialog();
        }
        checkRecoveryCandidates(fileToEdit);
    }

    private void applyEntryContent(BufferedReader reader, String firstLine) throws Exception {
        EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(firstLine);
        String title = "";
        String firstContentLine;
        if (meta != null) {
            title = meta.title == null ? "" : meta.title;
            // Skip optional blank separator
            firstContentLine = reader.readLine();
            if (firstContentLine != null && firstContentLine.isBlank()) {
                firstContentLine = reader.readLine();
            }
            if (meta.mood >= 0 && moodSlider != null) {
                try { moodSlider.setValue(meta.mood); } catch (Throwable ignored) {}
            }
        } else {
            title = (firstLine == null ? "" : firstLine);
            // Expect a blank separator line
            reader.readLine();
            firstContentLine = reader.readLine();
        }

        titleField.setText(title);

        // Check for guided mode metadata
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
        // After loading content (text or RTF), refresh the word count
        try { updateWordCount(); } catch (Throwable ignored) {}
    }

    private boolean verifyIntegrityAndOfferRestore(File entryFile) {
        try {
            String expected = EntryHistoryManager.getLastChecksum(entryFile);
            if (expected == null || expected.isBlank()) return true;
            String actual = FileIO.sha256(entryFile.toPath());
            if (expected.equals(actual)) return true;
            EntryHistoryManager.Snapshot latest = EntryHistoryManager.getLatestSnapshot(entryFile);
            if (latest == null) return true;
            String[] options = {"Restore latest backup", "Open anyway", "Cancel"};
            int choice = CustomChoiceDialog.choose(this, "Integrity Check",
                    "This entry looks corrupted or incomplete. Restore the latest backup?", options);
            if (choice == 0) {
                boolean ok = EntryHistoryManager.restoreSnapshot(entryFile, latest);
                if (!ok) return true;
            } else if (choice == 2) {
                return false;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private void showRestoreDialog() {
        if (currentFile == null || !currentFile.exists()) {
            CustomMessageDialog.display(this, "Restore", "No saved versions yet.", true);
            return;
        }
        java.util.List<EntryHistoryManager.Snapshot> snaps = EntryHistoryManager.listSnapshots(currentFile);
        if (snaps.isEmpty()) {
            CustomMessageDialog.display(this, "Restore", "No saved versions yet.", true);
            return;
        }
        int max = Math.min(5, snaps.size());
        String[] options = new String[max + 1];
        for (int i = 0; i < max; i++) {
            EntryHistoryManager.Snapshot s = snaps.get(snaps.size() - 1 - i);
            options[i] = formatSnapshotLabel(s);
        }
        options[max] = "Cancel";
        int choice = CustomChoiceDialog.choose(this, "Restore Entry", "Select a previous version to restore:", options);
        if (choice < 0 || choice >= max) return;
        EntryHistoryManager.Snapshot selected = snaps.get(snaps.size() - 1 - choice);
        if (EntryHistoryManager.restoreSnapshot(currentFile, selected)) {
            loadExistingEntry(currentFile);
        } else {
            CustomMessageDialog.display(this, "Restore", "Failed to restore selected version.", true);
        }
    }

    private String formatSnapshotLabel(EntryHistoryManager.Snapshot snap) {
        String ts = snap.timestamp;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date d = in.parse(ts);
            return out.format(d);
        } catch (Exception e) {
            return ts;
        }
    }

    // --- Formatting helpers used by shared toolbar ---
    private void applyInlineStyleBold() {
        RichTextStyler.toggleBold(contentArea);
    }

    private void applyInlineStyleItalic() {
        RichTextStyler.toggleItalic(contentArea);
    }

    private void applyInlineStyleUnderline() {
        RichTextStyler.toggleUnderline(contentArea);
    }

    private void applyInlineStyleStrike() {
        RichTextStyler.toggleStrike(contentArea);
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
            try { remove(toolbarGroup != null ? toolbarGroup : toolbarContainer); } catch (Throwable ignored) {}
            add(dfHeader, BorderLayout.NORTH);
            if (bottomPanel != null) bottomPanel.setVisible(false);
        } else {
            try { remove(dfHeader); } catch (Throwable ignored) {}
            add(toolbarGroup != null ? toolbarGroup : toolbarContainer, BorderLayout.NORTH);
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
        // Build right-side controls (journal-specific) that live inside the main frosted bar
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightToolbar.setOpaque(false);
        if (supportsClockButton()) {
            ToolbarMenuIconButton clockBtn = new ToolbarMenuIconButton("", "clock");
            clockBtn.setToolTipText("Insert time snapshot");
            clockBtn.addActionListener(e -> insertClockSnapshot());
            rightToolbar.add(clockBtn);
            rightToolbar.add(Box.createHorizontalStrut(6));
        }
        ToolbarMenuIconButton restoreBtn = new ToolbarMenuIconButton("", "load");
        restoreBtn.setToolTipText("Restore previous version");
        restoreBtn.addActionListener(e -> showRestoreDialog());
        rightToolbar.add(restoreBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));
        ToolbarMenuIconButton dfBtn = new ToolbarMenuIconButton("", "fullscreen");
        dfBtn.setToolTipText("Distraction-Free Mode");
        dfBtn.addActionListener(e -> toggleDistractionFree());
        rightToolbar.add(dfBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));
        ToolbarMenuIconButton settingsBtn = new ToolbarMenuIconButton("", "backgroundoptions");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            EntryBackgroundDialog dialog = new EntryBackgroundDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            repaint();
        });
        rightToolbar.add(settingsBtn);

        // Create shared poetry-style toolbar
        NotebookInfo nbInfo = new NotebookInfo(
                journalFolder.getName(),
                NotebookInfo.Type.JOURNAL,
                journalFolder,
                journalFolder.lastModified(),
                null
        );
        installExtraRightToolbarButtons(rightToolbar);
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
        toolbarContainer = sharedToolbar.getContainer();

        // Stack toolbar + mood controls (mood lives below the frosted bar)
        toolbarGroup = new JPanel();
        toolbarGroup.setOpaque(false);
        toolbarGroup.setLayout(new BoxLayout(toolbarGroup, BoxLayout.Y_AXIS));
        toolbarGroup.add(toolbarContainer);

        if (supportsMoodControls()) {
            JPanel moodRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            moodRow.setOpaque(false);
            moodSlider = new MoodSlider();
            moodRow.add(moodSlider);
            moodSlider.addChangeListener(e -> {
                try { SimEventBus.get().emitMoodChanged((double) moodSlider.getValue()); } catch (Throwable ignored) {}
            });
            moodContainer = new JPanel(new BorderLayout());
            moodContainer.setOpaque(false);
            moodContainer.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
            moodContainer.add(moodRow, BorderLayout.CENTER);
            toolbarGroup.add(Box.createVerticalStrut(6));
            toolbarGroup.add(moodContainer);
        }

        add(toolbarGroup, BorderLayout.NORTH);

        // Distraction-free header: only Back button, no other controls
        dfHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dfHeader.setOpaque(false);
        dfHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        // Use the standard back icon, but here it exits fullscreen
        ToolbarMenuIconButton dfBack = new ToolbarMenuIconButton("", "back");
        dfBack.setToolTipText("Exit Fullscreen");
        dfBack.addActionListener(e -> toggleDistractionFree());
        dfHeader.add(dfBack);

        // --- Content Area (match PoemPanel style) ---
        // Use the same paper-like rounded rectangle container as in PoemPanel
        // Glass effect with adjustable opacity from settings
        JPanel textWrapper = new FrostedGlassPanel(new BorderLayout(), 16) {
            @Override
            protected float getOpacityScale() {
                return SettingsStore.get().getEditorGlassOpacity();
            }
        };
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Content Area: Rich text editor (StyledDocument)
        contentArea = new JTextPane();
        contentArea.setDoubleBuffered(true);

        // Load font settings from Appearance settings
        String fontFamily = SettingsStore.get().getEditorFontFamily();
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        String lineSpacingStr = SettingsStore.get().getEditorLineSpacing();
        contentArea.setFont(new Font(fontFamily, Font.PLAIN, savedFontSize));
        if (titleField != null) {
            titleField.setFont(new Font(fontFamily, Font.PLAIN, savedFontSize));
            titleField.setPlaceholder(null);
        }
        // JTextPane handles wrapping automatically via view; ensure editor kit is styled
        contentArea.setEditorKit(new StyledEditorKit());
        contentArea.setOpaque(false);
        // Apply line spacing from settings
        float spacing = switch (lineSpacingStr) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = (StyledDocument) contentArea.getStyledDocument();
                MutableAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setLineSpacing(attrs, spacing);
                doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
            } catch (Exception ignored) {}
        });
        // Match poem editor text color
        contentArea.setForeground(new Color(40, 40, 40));
        ensureSimStyles();

        // Enable rich image paste & drag-and-drop into the editor
        ImagePasteManager.install(
                contentArea,
                () -> new File(journalFolder, "attachments"),
                800 // max width in pixels for inserted images
        );

        // Install native-accelerated formatting hotkeys (Cmd/Ctrl + B/I/U, Cmd/Ctrl+Shift+S)
        FormattingHotkeyHandler.install(contentArea,
                this::applyInlineStyleBold,
                this::applyInlineStyleItalic,
                this::applyInlineStyleUnderline,
                this::applyInlineStyleStrike);

        // Enable link detection and styling on paste (deferred until displayable)
        LinkManager.installWhenReady(contentArea);

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

        try {
            if (SettingsStore.get().isJournalAutocorrectEnabled()) {
                AutocorrectDocumentFilter.install(contentArea);
            }
        } catch (Throwable ignored) {}

        // Add document listener for word count and typing snapshot; keep a reference
        editorDocListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
                emitTypingSnapshot();
                if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
            }
        };
        contentArea.getDocument().addDocumentListener(editorDocListener);
        // Reattach listener whenever the document instance is replaced (e.g., opening existing RTF)
        contentArea.addPropertyChangeListener("document", evt -> {
            try {
                if (evt.getOldValue() instanceof javax.swing.text.Document oldDoc && editorDocListener != null) {
                    oldDoc.removeDocumentListener(editorDocListener);
                }
            } catch (Throwable ignored) {}
            try {
                if (evt.getNewValue() instanceof javax.swing.text.Document newDoc && editorDocListener != null) {
                    newDoc.addDocumentListener(editorDocListener);
                }
            } catch (Throwable ignored) {}
            // Ensure label reflects loaded content immediately
            try { updateWordCount(); } catch (Throwable ignored) {}

            try {
                if (SettingsStore.get().isJournalAutocorrectEnabled()) {
                    AutocorrectDocumentFilter.install(contentArea);
                }
            } catch (Throwable ignored) {}
        });
        // Autosave on title change too
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty(); }
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
        installContentOverlay(textWrapper, scrollPane);

        // Add some vertical space between toolbar and content (like PoemPanel)
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        
        // Create guided question bubble (hidden by default)
        createQuestionBubble();
        createRecoveryBanner();
        JPanel topArea = new JPanel();
        topArea.setOpaque(false);
        topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
        topArea.add(Box.createRigidArea(new Dimension(0, 15)));
        topArea.add(recoveryBanner);
        topArea.add(Box.createRigidArea(new Dimension(0, 8)));
        topArea.add(questionBubble);
        centerContainer.add(topArea, BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);

        // Add to main panel
        add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Panel: Save Button ---
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);

        // Save button (via EditorUIUtils)
        saveButton = EditorUIUtils.createSaveButton("Save Entry", this::saveEntry);

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

        // --- Autosave wiring (native C++ coordinator) ---
        int delayMs = SettingsStore.get().getAutosaveDelayMs();
        if (delayMs > 0) {
            String filePath = (currentFile != null) ? currentFile.getAbsolutePath() : null;
            autosaveCoordinator = new NativeAutosaveCoordinator(filePath, delayMs,
                    this::saveEntry,
                    () -> { isAutosaving = true; if (saveIndicator != null) saveIndicator.setSaving(); },
                    () -> { 
                        updateSaveIndicatorFromCurrentFile();
                        isAutosaving = false; 
                    });
        } else {
            autosaveCoordinator = null; // autosave disabled
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

        // Note: Keyboard shortcuts (Cmd/Ctrl+B/I/U/Shift+S) are handled by
        // FormattingHotkeyHandler installed in initContentArea() with platform-aware modifiers

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
        int count = countWords(text);

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
        if (readOnlyMode) {
            CustomMessageDialog.display(this, "Read-only", "This entry is locked by another instance and cannot be saved.", true);
            return;
        }
        if (autosaveCoordinator != null) {
            try { autosaveCoordinator.stop(); } catch (Throwable ignored) {}
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
        int moodValue = moodHolder[0]; // 0 - 100
        if (moodValue >= 0) {
            recordMood(moodValue);
        }

        String manifestForRestore = null;
        boolean tokensApplied = false;
        try {
            // Update status to Saving…
            if (saveIndicator != null) saveIndicator.setSaving();
            // Ensure target folder exists
            if (journalFolder != null && !journalFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                journalFolder.mkdirs();
            }

            File file;

            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + fileExtension();
                file = new File(journalFolder, filename);
                currentFile = file;
            } else {
                // Subsequent saves - use existing file
                file = currentFile;
            }

            // Acquire lock for the entry file (first save or restore)
            if (entryLock == null) {
                if (!acquireEntryLock(file)) {
                    setReadOnlyMode(true);
                    CustomMessageDialog.display(this, "Read-only", "This entry is locked by another instance.", true);
                    if (currentFile == file) currentFile = null;
                    return;
                }
            }

            EntryFileFormat.EntryMeta meta = new EntryFileFormat.EntryMeta();
            meta.title = title;
            meta.mood = moodValue;
            meta.guided = guidedQuestions != null && guidedQuestions.length > 0;
            meta.template = meta.guided ? getGuidedModeTemplateName() : null;
            meta.savedAt = System.currentTimeMillis();

            manifestForRestore = null;
            tokensApplied = false;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
            writer.println(EntryFileFormat.buildHeader(meta));
            writer.println(); // separator

            // Save guided mode if active
            if (meta.guided) {
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
                writer.flush();
            } else {
                // Regular mode: write image manifest header, then RTF appended below
                manifestForRestore = buildImageManifest((StyledDocument) contentArea.getDocument());
                if (manifestForRestore == null) manifestForRestore = "";
                tokensApplied = true;
                writer.println("IMGMAP:" + manifestForRestore);
                writer.println();
                writer.flush();

                // Append RTF styling
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument sd = (StyledDocument) contentArea.getDocument();
                try {
                    // Document already tokenized by buildImageManifest for header; just write it.
                    kit.write(baos, sd, 0, sd.getLength());
                } catch (BadLocationException ble) {
                    // fallback to plain text if unexpected
                    baos.write(content.getBytes(StandardCharsets.UTF_8));
                }
            }

            byte[] data = baos.toByteArray();
            int wordCount = countWords(new String(data, StandardCharsets.UTF_8));
            if (EncryptionManager.isEncryptionEnabled()) {
                String password = EncryptionManager.getPasswordForUse(this, !isAutosaving);
                if (password == null || password.isBlank()) {
                    if (saveIndicator != null) saveIndicator.setError("Encryption locked");
                    return;
                }
                CryptoConfig config = CryptoConfig.forEntries()
                        .withIdentifier(EncryptedMetadata.encodeEntry(
                                meta.title,
                                meta.mood,
                                meta.guided,
                                meta.template,
                                meta.savedAt,
                                wordCount
                        ));
                try {
                    data = EncryptionManager.encrypt(data, password, ContentType.ENTRY, config);
                } catch (CryptoException ex) {
                    if (saveIndicator != null) saveIndicator.setError("Encrypt failed");
                    SwingUtilities.invokeLater(() -> CustomMessageDialog.display(this, "Encryption", ex.getUserMessage(), true));
                    return;
                }
            }
            try {
                FileIO.ensureSpace(file.toPath(), data.length + 4096L, "entry save");
                FileIO.atomicWrite(file.toPath(), data, true, true);
                clearRecoverySnapshot(file);
                hideRecoveryBanner();
            } catch (IOException io) {
                File candidate = writeRecoverySnapshot(file, data);
                if (candidate == null) candidate = findLatestTempForTarget(file);
                if (candidate != null) {
                    String msg = isAutosaving
                            ? "Autosave failed. Recover unsaved changes?"
                            : "Save failed. Recover unsaved changes?";
                    File finalCandidate = candidate;
                    SwingUtilities.invokeLater(() -> showRecoveryBanner(msg, finalCandidate));
                }
                throw io;
            }

            // Restore icons back into the live document so the UI remains unchanged
            if (!meta.guided && tokensApplied) {
                try {
                    StyledDocument sd = (StyledDocument) contentArea.getDocument();
                    if (manifestForRestore != null && !manifestForRestore.isBlank()) {
                        restoreIconsFromTokens(sd, manifestForRestore);
                    }
                } catch (Throwable ignored) {}
            }

            // Record versioned snapshot
            try {
                int keep = SettingsStore.get().getBackupKeepCount();
                EntryHistoryManager.recordSnapshot(file, keep);
            } catch (Throwable ignored) {}

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

            // Suppress success popups; rely on status indicator only

            // Update status to Saved · time
            updateSaveIndicatorFromCurrentFile();
        } catch (IOException ex) {
            ex.printStackTrace();
            if (tokensApplied) {
                try {
                    StyledDocument sd = (StyledDocument) contentArea.getDocument();
                    if (manifestForRestore != null && !manifestForRestore.isBlank()) {
                        restoreIconsFromTokens(sd, manifestForRestore);
                    }
                } catch (Throwable ignored) {}
            }
            SwingUtilities.invokeLater(() -> new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving entry.", true).showDialog());
            if (saveIndicator != null) saveIndicator.setError("Error saving");
        }
    }

    private boolean acquireEntryLock(File file) {
        if (entryLock != null) return true;
        try {
            entryLock = FileIO.tryLock(file.toPath());
            if (entryLock == null) {
                return false;
            }
            return true;
        } catch (IOException e) {
            IoLog.warn("entry-lock", "Failed to lock entry file: " + file, e);
            return false;
        }
    }

    private void releaseEntryLock() {
        if (entryLock != null) {
            FileIO.releaseQuietly(entryLock);
            entryLock = null;
        }
    }

    private void setReadOnlyMode(boolean readOnly) {
        this.readOnlyMode = readOnly;
        try { if (titleField != null) titleField.setEditable(!readOnly); } catch (Throwable ignored) {}
        try { if (contentArea != null) contentArea.setEditable(!readOnly); } catch (Throwable ignored) {}
        try { if (moodSlider != null) moodSlider.setEnabled(!readOnly); } catch (Throwable ignored) {}
        try { if (saveButton != null) saveButton.setEnabled(!readOnly); } catch (Throwable ignored) {}
        if (readOnly && autosaveCoordinator != null) {
            try { autosaveCoordinator.stop(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void removeNotify() {
        try { if (autosaveCoordinator != null) autosaveCoordinator.stop(); } catch (Throwable ignored) {}
        releaseEntryLock();
        super.removeNotify();
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

    private BufferedReader openEntryReader(File file) throws IOException, CryptoException {
        byte[] data = EncryptionManager.readFileMaybeDecrypt(file, this, true);
        if (data == null) return null;
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));
    }

    private static int countWords(String text) {
        if (text == null) return 0;
        int nativeCount = NativeAccess.countWords(text);
        if (nativeCount >= 0) return nativeCount;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
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

    private void recordMood(int composite,
                             DetailedMoodPanel.DetailedMoodSnapshot details) {
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(moodFile, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            // CSV: ts,composite,joy,calm,gratitude,energy,sadness,anger,anxiety,stress
            writer.println(String.join(",",
                timestamp,
                String.valueOf(composite),
                String.valueOf(details.joy),
                String.valueOf(details.calm),
                String.valueOf(details.gratitude),
                String.valueOf(details.energy),
                String.valueOf(details.sadness),
                String.valueOf(details.anger),
                String.valueOf(details.anxiety),
                String.valueOf(details.stress)
            ));
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
        hideRecoveryBanner();
        if (saveIndicator != null) saveIndicator.clear();
    }

    private void updateSaveIndicatorFromCurrentFile() {
        if (saveIndicator == null) return;
        long ts = (currentFile != null && currentFile.exists()) ? currentFile.lastModified() : System.currentTimeMillis();
        saveIndicator.setSaved(new Date(ts));
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

    private void createRecoveryBanner() {
        recoveryBanner = new JPanel(new BorderLayout(10, 0));
        recoveryBanner.setOpaque(true);
        recoveryBanner.setBackground(new Color(255, 244, 204));
        recoveryBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 190, 130)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        recoveryLabel = new JLabel("Recovered changes are available.");
        recoveryLabel.setForeground(new Color(80, 60, 20));
        recoveryLabel.setFont(recoveryLabel.getFont().deriveFont(Font.PLAIN, 13f));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        recoveryButton = new RoundedButton("Recover");
        recoveryButton.setPreferredSize(new Dimension(90, 28));
        recoveryButton.addActionListener(e -> recoverFromCandidate(recoveryCandidate));
        recoveryDismissButton = new RoundedButton("Dismiss");
        recoveryDismissButton.setPreferredSize(new Dimension(90, 28));
        recoveryDismissButton.addActionListener(e -> {
            try { if (recoveryCandidate != null) recoveryCandidate.delete(); } catch (Throwable ignored) {}
            clearRecoverySnapshot(currentFile);
            hideRecoveryBanner();
        });
        btns.add(recoveryButton);
        btns.add(recoveryDismissButton);

        recoveryBanner.add(recoveryLabel, BorderLayout.CENTER);
        recoveryBanner.add(btns, BorderLayout.EAST);
        recoveryBanner.setVisible(false);
        recoveryBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
    }

    private void showRecoveryBanner(String message, File candidate) {
        if (recoveryBanner == null || recoveryLabel == null) return;
        recoveryCandidate = candidate;
        recoveryLabel.setText(message == null ? "Recovered changes are available." : message);
        SwingUtilities.invokeLater(() -> {
            recoveryBanner.setVisible(true);
            revalidate();
            repaint();
        });
    }

    private void hideRecoveryBanner() {
        if (recoveryBanner == null) return;
        recoveryCandidate = null;
        SwingUtilities.invokeLater(() -> {
            recoveryBanner.setVisible(false);
            revalidate();
            repaint();
        });
    }

    private void checkRecoveryCandidates(File entryFile) {
        if (entryFile == null || !entryFile.exists()) return;
        File temp = findLatestTempForTarget(entryFile);
        File recovery = getRecoveryFile(entryFile);
        long baseline = entryFile.lastModified();
        File candidate = null;
        if (temp != null && temp.length() > 0 && temp.lastModified() > baseline) {
            candidate = temp;
        }
        if (recovery != null && recovery.exists() && recovery.length() > 0 && recovery.lastModified() > baseline) {
            if (candidate == null || recovery.lastModified() > candidate.lastModified()) {
                candidate = recovery;
            }
        }
        if (candidate != null) {
            String msg = candidate.getName().contains(".tmp")
                    ? "Unsaved changes were found after a crash. Recover them?"
                    : "Autosave recovery is available. Recover unsaved changes?";
            showRecoveryBanner(msg, candidate);
        }
    }

    private File findLatestTempForTarget(File target) {
        if (target == null) return null;
        File dir = target.getParentFile();
        if (dir == null || !dir.exists()) return null;
        String prefix = target.getName() + ".tmp";
        File[] matches = dir.listFiles((d, name) -> name.startsWith(prefix));
        if (matches == null || matches.length == 0) return null;
        File latest = matches[0];
        for (File f : matches) {
            if (f != null && f.lastModified() > latest.lastModified()) latest = f;
        }
        return latest;
    }

    private File getRecoveryFile(File target) {
        if (target == null || journalFolder == null) return null;
        File dir = new File(journalFolder, ".recovery");
        return new File(dir, target.getName() + ".recover");
    }

    private File writeRecoverySnapshot(File target, byte[] data) {
        if (target == null || data == null) return null;
        try {
            File rec = getRecoveryFile(target);
            if (rec == null) return null;
            File dir = rec.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileIO.atomicWrite(rec.toPath(), data, true, true);
            return rec;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearRecoverySnapshot(File target) {
        if (target == null) return;
        try {
            File rec = getRecoveryFile(target);
            if (rec != null && rec.exists()) rec.delete();
        } catch (Throwable ignored) {}
    }

    private void recoverFromCandidate(File candidate) {
        if (candidate == null || !candidate.exists()) {
            hideRecoveryBanner();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(candidate.toPath(), StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            applyEntryContent(reader, firstLine);
            if (saveIndicator != null) saveIndicator.setError("Recovered copy loaded — save to keep");
        } catch (Exception ex) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Recovery Failed", "Could not load recovery file.", true).showDialog();
        } finally {
            try { candidate.delete(); } catch (Throwable ignored) {}
            clearRecoverySnapshot(currentFile);
            hideRecoveryBanner();
        }
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
