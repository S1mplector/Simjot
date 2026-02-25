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
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Element;
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
import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.core.sim.api.SimEventBus;
import main.core.spelling.AutocorrectDocumentFilter;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.MoodFile;
import main.infrastructure.io.IoLog;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
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
    protected CustomFontTextPane contentArea;
    protected MoodSlider moodSlider;
    private DetailedMoodPanel detailedMoodPanel;
    private SaveIndicatorPanel saveIndicator;
    private JLabel wordCountLabel;
    private boolean titleFocusedOnce = false;
    private AnimatedGlassPopup formatPopup;
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private NativeAutosaveCoordinator autosaveCoordinator;
    private volatile boolean isAutosaving = false;
    
    // Track temporary placeholder range for Sim guidance.
    private int pendingGuidanceStart = -1;
    private int pendingGuidanceLen = 0;
    private SimpleAttributeSet pendingGuidanceTypingAttrs = null;
    private volatile boolean awaitingGuidanceResponse = false;
    private volatile boolean awaitingTemplateResponse = false;
    private final SimEventBus.Listener simGuidanceListener = new SimEventBus.Listener() {
        @Override
        public void onGuidanceProduced(String text) {
            if (!awaitingGuidanceResponse) return;
            awaitingGuidanceResponse = false;
            SwingUtilities.invokeLater(() -> {
                String guidance = text == null ? "" : text.strip();
                if (guidance.isEmpty()) {
                    guidance = "I could not generate guidance this time. Please try again.";
                }
                insertGuidanceStyled(guidance);
            });
        }

        @Override
        public void onTemplateGenerated(String notebookName, String name, String description, String[] questions) {
            awaitingTemplateResponse = false;
        }
    };
    private boolean simGuidanceListenerRegistered = false;
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
    private JPanel moodSummaryPills;
    private MoodTrendBaseline moodTrendBaseline;
    private ToolbarMenuIconButton moodDetailsToggleButton;
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
    private Insets baseTextMargin;
    private PaperFeelViewport paperViewport;
    private HandwrittenHeaderStrip headerStamp;
    // Reusable document listener reference so we can reattach after setDocument()
    private javax.swing.event.DocumentListener editorDocListener;
    private javax.swing.Timer metricsDebounceTimer;
    private static final int METRICS_DEBOUNCE_MS = 160;
    private String lastTypingSnapshot = "";
    private static final java.util.regex.Pattern IMAGE_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("\\[\\[IMG\\|([^|]+)\\|(\\d+)x(\\d+)\\]\\]");

    public EntryPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
        registerSimGuidanceListenerIfSupported();
    }

    private void setDetailedMoodExpanded(boolean expanded) {
        if (detailedMoodPanel == null) return;
        detailedMoodPanel.setExpanded(expanded);
        refreshDetailedMoodToggleVisual();
    }

    private void refreshDetailedMoodToggleVisual() {
        if (moodDetailsToggleButton == null) return;
        boolean expanded = detailedMoodPanel != null && detailedMoodPanel.isExpanded();
        moodDetailsToggleButton.animateIconRotationRadians(expanded ? Math.PI / 2.0 : 0.0, 200);
        moodDetailsToggleButton.setToolTipText(expanded ? "Hide detailed emotions" : "Show detailed emotions");
    }

    private static int[] detailedMoodArrayFromSnapshot(DetailedMoodPanel.DetailedMoodSnapshot details) {
        if (details == null) return null;
        int[] values = new int[] {
                details.joy, details.calm, details.gratitude, details.energy,
                details.sadness, details.anger, details.anxiety, details.stress
        };
        return hasAnyDetailValue(values) ? values : null;
    }

    private static DetailedMoodPanel.DetailedMoodSnapshot detailedMoodSnapshotFromArray(int[] values) {
        if (values == null || values.length < 8) return null;
        if (!hasAnyDetailValue(values)) return null;
        return new DetailedMoodPanel.DetailedMoodSnapshot(
                safeDetailValue(values[0]), safeDetailValue(values[1]),
                safeDetailValue(values[2]), safeDetailValue(values[3]),
                safeDetailValue(values[4]), safeDetailValue(values[5]),
                safeDetailValue(values[6]), safeDetailValue(values[7])
        );
    }

    private static int safeDetailValue(int v) {
        if (v < 0) return -1;
        return Math.max(0, Math.min(100, v));
    }

    private static boolean hasAnyDetailValue(int[] values) {
        if (values == null || values.length == 0) return false;
        for (int value : values) {
            if (value >= 0) return true;
        }
        return false;
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

    public boolean isSimGuidanceAvailable() {
        if (!supportsGuidanceButton()) return false;
        if (contentArea == null) return false;
        if (!contentArea.isEditable()) return false;
        return isShowing();
    }

    public boolean requestSimGuidanceFromOverlay() {
        if (!isSimGuidanceAvailable()) return false;
        if (awaitingGuidanceResponse) return false;
        String text = contentArea.getText();
        if (text == null || text.isBlank()) return false;
        try {
            showGuidanceThinkingPlaceholder();
            awaitingGuidanceResponse = true;
            SimEventBus.get().emitGuidanceRequested(text);
            contentArea.requestFocusInWindow();
            return true;
        } catch (Throwable ignored) {
            awaitingGuidanceResponse = false;
            return false;
        }
    }

    public boolean isSimTemplateGenerationAvailable() {
        if (!supportsGuidanceButton()) return false;
        if (contentArea == null) return false;
        if (!contentArea.isEditable()) return false;
        return isShowing();
    }

    public boolean requestSimTemplateGenerationFromOverlay() {
        if (!isSimTemplateGenerationAvailable()) return false;
        if (awaitingTemplateResponse) return false;
        String text = contentArea.getText();
        if (text == null || text.isBlank()) return false;
        String notebookName = resolveJournalNotebookName();
        try {
            awaitingTemplateResponse = true;
            SimEventBus.get().emitTemplateGenerationRequested(text, notebookName);
            contentArea.requestFocusInWindow();
            return true;
        } catch (Throwable ignored) {
            awaitingTemplateResponse = false;
            return false;
        }
    }

    private String resolveJournalNotebookName() {
        try {
            if (journalFolder == null) return "";
            java.nio.file.Path current = journalFolder.toPath().toAbsolutePath().normalize();
            for (NotebookInfo nb : new NotebookStore().list()) {
                if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) continue;
                File folder = nb.getFolder();
                if (folder == null) continue;
                java.nio.file.Path folderPath = folder.toPath().toAbsolutePath().normalize();
                if (folderPath.equals(current)) return nb.getName();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private void registerSimGuidanceListenerIfSupported() {
        if (!supportsGuidanceButton()) return;
        if (simGuidanceListenerRegistered) return;
        try {
            SimEventBus.get().addListener(simGuidanceListener);
            simGuidanceListenerRegistered = true;
        } catch (Throwable ignored) {}
    }

    private void unregisterSimGuidanceListener() {
        if (!simGuidanceListenerRegistered) return;
        try { SimEventBus.get().removeListener(simGuidanceListener); } catch (Throwable ignored) {}
        simGuidanceListenerRegistered = false;
    }

    /**
     * Opacity for the glass panel used in this editor.
     */
    protected float getEditorGlassOpacity() { return SettingsStore.get().getEntryGlassOpacity(); }

    protected void installExtraRightToolbarButtons(JPanel rightToolbar) { }
    protected void installContentOverlay(JComponent textWrapper, JScrollPane scrollPane) { }
    protected Runnable getCodeBlockInsertAction() { return null; }

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
        DetailedMoodPanel.DetailedMoodSnapshot loadedMoodDetails = null;
        try { if (moodSlider != null) moodSlider.setValue(50); } catch (Throwable ignored) {}
        try {
            if (detailedMoodPanel != null) {
                detailedMoodPanel.clearSnapshot();
                setDetailedMoodExpanded(false);
            }
        } catch (Throwable ignored) {}

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
            if (meta.moodDetails != null && detailedMoodPanel != null) {
                DetailedMoodPanel.DetailedMoodSnapshot snapshot = detailedMoodSnapshotFromArray(meta.moodDetails);
                if (snapshot != null) {
                    detailedMoodPanel.applySnapshot(snapshot);
                    loadedMoodDetails = snapshot;
                }
            }
        } else {
            title = (firstLine == null ? "" : firstLine);
            // Expect a blank separator line
            reader.readLine();
            firstContentLine = reader.readLine();
        }

        titleField.setText(title);
        refreshMoodTrendBaselineFromHistory();
        updateMoodSummaryPills(loadedMoodDetails, moodSlider != null ? moodSlider.getValue() : 50);

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
        EntryHistoryManager.Snapshot selected = chooseSnapshotWithDiffPreview(snaps);
        if (selected == null) return;
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

    private EntryHistoryManager.Snapshot chooseSnapshotWithDiffPreview(
            java.util.List<EntryHistoryManager.Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return null;

        java.util.List<EntryHistoryManager.Snapshot> choices = new java.util.ArrayList<>(snapshots);
        java.util.Collections.reverse(choices); // newest first

        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        javax.swing.JDialog dialog;
        if (owner instanceof java.awt.Frame frameOwner) {
            dialog = new javax.swing.JDialog(frameOwner, "Restore Entry", true);
        } else if (owner instanceof java.awt.Dialog dialogOwner) {
            dialog = new javax.swing.JDialog(dialogOwner, "Restore Entry", true);
        } else {
            dialog = new javax.swing.JDialog((java.awt.Frame) null, "Restore Entry", true);
        }
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setOpaque(true);
        root.setBackground(Color.WHITE);

        JLabel subtitle = new JLabel("Select a version and preview mini diff before restoring.");
        subtitle.setForeground(new Color(95, 103, 118));
        subtitle.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        root.add(subtitle, BorderLayout.NORTH);

        javax.swing.DefaultListModel<SnapshotListItem> model = new javax.swing.DefaultListModel<>();
        for (EntryHistoryManager.Snapshot snap : choices) {
            model.addElement(new SnapshotListItem(snap, formatSnapshotLabel(snap)));
        }
        javax.swing.JList<SnapshotListItem> snapshotList = new javax.swing.JList<>(model);
        snapshotList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        snapshotList.setVisibleRowCount(8);
        snapshotList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            panel.setBackground(isSelected ? new Color(235, 244, 255) : Color.WHITE);

            JLabel line1 = new JLabel(value == null ? "" : value.label);
            line1.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 12f));
            line1.setForeground(new Color(38, 44, 56));

            String meta = value == null || value.snapshot == null
                    ? ""
                    : formatSnapshotSize(value.snapshot.size) + " · " + safeShortChecksum(value.snapshot.checksum);
            JLabel line2 = new JLabel(meta);
            line2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
            line2.setForeground(new Color(112, 122, 138));

            panel.add(line1, BorderLayout.NORTH);
            panel.add(line2, BorderLayout.SOUTH);
            return panel;
        });

        javax.swing.JScrollPane leftScroll = new javax.swing.JScrollPane(snapshotList);
        leftScroll.setPreferredSize(new Dimension(260, 220));
        leftScroll.getViewport().setBackground(Color.WHITE);

        JTextArea diffPreview = new JTextArea();
        diffPreview.setEditable(false);
        diffPreview.setFocusable(false);
        diffPreview.setLineWrap(false);
        diffPreview.setWrapStyleWord(false);
        diffPreview.setFont(new Font("Menlo", Font.PLAIN, 12));
        diffPreview.setForeground(new Color(42, 48, 59));
        diffPreview.setBackground(new Color(252, 253, 255));
        diffPreview.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titleDelta = new JLabel("Title: -");
        titleDelta.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        titleDelta.setForeground(new Color(72, 82, 98));
        JLabel moodDelta = new JLabel("Mood: -");
        moodDelta.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        moodDelta.setForeground(new Color(72, 82, 98));

        JPanel previewMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        previewMeta.setOpaque(false);
        previewMeta.add(titleDelta);
        previewMeta.add(moodDelta);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.add(previewMeta, BorderLayout.NORTH);
        right.add(new javax.swing.JScrollPane(diffPreview), BorderLayout.CENTER);

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
        split.setResizeWeight(0.35);
        split.setDividerLocation(270);
        root.add(split, BorderLayout.CENTER);

        RoundedButton restoreButton = new RoundedButton("Restore");
        RoundedButton cancelButton = new RoundedButton("Cancel");
        restoreButton.setEnabled(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(restoreButton);
        root.add(actions, BorderLayout.SOUTH);

        java.util.Map<File, EntryPreviewData> previewCache = new java.util.HashMap<>();
        EntryPreviewData current = readEntryPreviewData(currentFile);
        EntryHistoryManager.Snapshot[] selected = new EntryHistoryManager.Snapshot[1];

        Runnable updatePreview = () -> {
            SnapshotListItem item = snapshotList.getSelectedValue();
            if (item == null || item.snapshot == null) {
                restoreButton.setEnabled(false);
                titleDelta.setText("Title: -");
                moodDelta.setText("Mood: -");
                diffPreview.setText("Select a snapshot to preview changes.");
                return;
            }
            restoreButton.setEnabled(true);
            EntryPreviewData snapPreview = previewCache.computeIfAbsent(item.snapshot.file, this::readEntryPreviewData);
            titleDelta.setText("Title: " + previewTitleDelta(current.title, snapPreview.title));
            moodDelta.setText("Mood: " + previewMoodDelta(current.mood, snapPreview.mood));
            diffPreview.setText(buildMiniDiffPreview(current.text, snapPreview.text));
            diffPreview.setCaretPosition(0);
        };

        snapshotList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview.run();
        });
        snapshotList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2 && snapshotList.getSelectedValue() != null) {
                    selected[0] = snapshotList.getSelectedValue().snapshot;
                    dialog.dispose();
                }
            }
        });

        restoreButton.addActionListener(e -> {
            SnapshotListItem item = snapshotList.getSelectedValue();
            selected[0] = item == null ? null : item.snapshot;
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setContentPane(root);
        dialog.setSize(new Dimension(920, 520));
        dialog.setLocationRelativeTo(this);
        if (!model.isEmpty()) {
            snapshotList.setSelectedIndex(0);
            updatePreview.run();
        }
        dialog.setVisible(true);
        return selected[0];
    }

    private EntryPreviewData readEntryPreviewData(File file) {
        if (file == null || !file.exists()) {
            return new EntryPreviewData("", -1, "");
        }
        try (BufferedReader reader = openEntryReader(file)) {
            if (reader == null) return new EntryPreviewData("", -1, "");
            String firstLine = reader.readLine();
            if (firstLine == null) return new EntryPreviewData("", -1, "");

            EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(firstLine);
            String title = "";
            int mood = -1;
            String firstBodyLine;
            if (meta != null) {
                title = meta.title == null ? "" : meta.title;
                mood = meta.mood;
                firstBodyLine = reader.readLine();
                if (firstBodyLine != null && firstBodyLine.isBlank()) {
                    firstBodyLine = reader.readLine();
                }
            } else {
                title = firstLine.trim();
                reader.readLine(); // legacy separator
                firstBodyLine = reader.readLine();
            }

            StringBuilder rest = new StringBuilder();
            if (firstBodyLine != null) {
                rest.append(firstBodyLine).append('\n');
            }
            String line;
            while ((line = reader.readLine()) != null) {
                rest.append(line).append('\n');
            }

            String plain = previewToPlainText(rest.toString());
            return new EntryPreviewData(title, mood, plain);
        } catch (Exception ignored) {
            return new EntryPreviewData("", -1, "");
        }
    }

    private static String previewToPlainText(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String body = raw.stripLeading();
        if (body.startsWith("IMGMAP:")) {
            int nl = body.indexOf('\n');
            body = nl >= 0 ? body.substring(nl + 1).stripLeading() : "";
        }
        if (body.startsWith("{\\rtf")) {
            try {
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
                kit.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), doc, 0);
                body = doc.getText(0, doc.getLength());
            } catch (Exception ignored) {
                // Keep raw fallback when RTF parse fails
            }
        }
        body = stripGuidedMarkersForPreview(body);
        body = stripImageTokens(body);
        return body.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static String stripGuidedMarkersForPreview(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(text.length());
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[GUIDED_MODE:")) continue;
                if (trimmed.startsWith("[Q") && trimmed.endsWith("]")) continue;
                out.append(line).append('\n');
            }
        } catch (IOException ignored) {}
        return out.toString();
    }

    private static String previewTitleDelta(String current, String snapshot) {
        String c = current == null ? "" : current.trim();
        String s = snapshot == null ? "" : snapshot.trim();
        if (Objects.equals(c, s)) {
            return "unchanged";
        }
        if (c.isBlank() && !s.isBlank()) {
            return "set to \"" + s + "\"";
        }
        if (!c.isBlank() && s.isBlank()) {
            return "cleared";
        }
        return "\"" + c + "\" → \"" + s + "\"";
    }

    private static String previewMoodDelta(int currentMood, int snapshotMood) {
        if (snapshotMood < 0 && currentMood < 0) return "unavailable";
        if (snapshotMood < 0) return "snapshot unavailable";
        if (currentMood < 0) return "snapshot " + snapshotMood;
        int delta = snapshotMood - currentMood;
        if (delta == 0) return snapshotMood + " (no change)";
        String direction = delta > 0 ? "↑" : "↓";
        return snapshotMood + " (" + direction + " " + Math.abs(delta) + " vs current)";
    }

    private static String safeShortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) return "no checksum";
        return checksum.length() <= 10 ? checksum : checksum.substring(0, 10) + "...";
    }

    private static String formatSnapshotSize(long bytes) {
        double b = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (b >= 1024.0 && idx < units.length - 1) {
            b /= 1024.0;
            idx++;
        }
        return String.format(idx == 0 ? "%.0f %s" : "%.1f %s", b, units[idx]);
    }

    private static String buildMiniDiffPreview(String currentText, String snapshotText) {
        String[] current = splitPreviewLines(currentText, 130);
        String[] snapshot = splitPreviewLines(snapshotText, 130);
        java.util.List<DiffLine> diff = computeLineDiff(current, snapshot);

        int added = 0;
        int removed = 0;
        for (DiffLine line : diff) {
            if (line.type == DiffType.ADD) added++;
            else if (line.type == DiffType.REMOVE) removed++;
        }
        if (added == 0 && removed == 0) {
            return "No text differences detected between current entry and this snapshot.";
        }

        StringBuilder out = new StringBuilder(2048);
        out.append("Legend: '-' removed from current, '+' added from snapshot")
                .append("\n")
                .append("Changes: +").append(added).append(" / -").append(removed)
                .append("\n\n");

        boolean[] include = new boolean[diff.size()];
        for (int i = 0; i < diff.size(); i++) {
            if (diff.get(i).type == DiffType.KEEP) continue;
            int start = Math.max(0, i - 2);
            int end = Math.min(diff.size() - 1, i + 2);
            for (int j = start; j <= end; j++) include[j] = true;
        }

        int hidden = 0;
        int shown = 0;
        int maxShown = 160;
        for (int i = 0; i < diff.size(); i++) {
            if (!include[i]) {
                hidden++;
                continue;
            }
            if (hidden > 0) {
                out.append("  ... ").append(hidden).append(" unchanged line(s) ...").append('\n');
                hidden = 0;
            }
            if (shown >= maxShown) {
                out.append("\n… diff preview truncated …");
                return out.toString();
            }
            DiffLine line = diff.get(i);
            switch (line.type) {
                case ADD -> out.append("+ ");
                case REMOVE -> out.append("- ");
                case KEEP -> out.append("  ");
            }
            out.append(trimPreviewDiffLine(line.text, 180)).append('\n');
            shown++;
        }
        if (hidden > 0) {
            out.append("  ... ").append(hidden).append(" unchanged line(s) ...").append('\n');
        }
        return out.toString();
    }

    private static String[] splitPreviewLines(String text, int maxLines) {
        if (text == null || text.isBlank()) return new String[0];
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int limit = Math.min(maxLines, lines.length);
        String[] out = new String[limit];
        for (int i = 0; i < limit; i++) {
            out[i] = lines[i];
        }
        return out;
    }

    private static java.util.List<DiffLine> computeLineDiff(String[] fromLines, String[] toLines) {
        int n = fromLines == null ? 0 : fromLines.length;
        int m = toLines == null ? 0 : toLines.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (Objects.equals(fromLines[i], toLines[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        java.util.List<DiffLine> out = new java.util.ArrayList<>(n + m);
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (Objects.equals(fromLines[i], toLines[j])) {
                out.add(new DiffLine(DiffType.KEEP, fromLines[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffLine(DiffType.REMOVE, fromLines[i]));
                i++;
            } else {
                out.add(new DiffLine(DiffType.ADD, toLines[j]));
                j++;
            }
        }
        while (i < n) {
            out.add(new DiffLine(DiffType.REMOVE, fromLines[i]));
            i++;
        }
        while (j < m) {
            out.add(new DiffLine(DiffType.ADD, toLines[j]));
            j++;
        }
        return out;
    }

    private static String trimPreviewDiffLine(String line, int maxLen) {
        if (line == null) return "";
        if (line.length() <= maxLen) return line;
        return line.substring(0, maxLen - 1) + "…";
    }

    private static final class SnapshotListItem {
        private final EntryHistoryManager.Snapshot snapshot;
        private final String label;

        private SnapshotListItem(EntryHistoryManager.Snapshot snapshot, String label) {
            this.snapshot = snapshot;
            this.label = label == null ? "" : label;
        }
    }

    private static final class EntryPreviewData {
        private final String title;
        private final int mood;
        private final String text;

        private EntryPreviewData(String title, int mood, String text) {
            this.title = title == null ? "" : title;
            this.mood = mood;
            this.text = text == null ? "" : text;
        }
    }

    private enum DiffType { KEEP, ADD, REMOVE }

    private static final class DiffLine {
        private final DiffType type;
        private final String text;

        private DiffLine(DiffType type, String text) {
            this.type = type;
            this.text = text == null ? "" : text;
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
        float spacing = resolveLineSpacing(val);
        try {
            StyledDocument doc = (StyledDocument) contentArea.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            applyParagraphRhythm(attrs);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        } catch (Exception ignored) {}
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
        if (contentArea == null) return;
        if (SettingsStore.get().isEditorPaperFeelEnabled()) {
            contentArea.setMargin(new Insets(18, 64, 18, 32));
        } else if (baseTextMargin != null) {
            contentArea.setMargin(baseTextMargin);
        }
    }

    private String buildHeaderStampText() {
        String date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"));
        String location = SettingsStore.get().getEditorHeaderStampLocation();
        if (location != null && !location.isBlank()) {
            return date + " - " + location.trim();
        }
        return date;
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

    private void openEntryBackgroundSettings() {
        EntryBackgroundDialog dialog = new EntryBackgroundDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
        repaint();
    }

    private ToolbarIconButton createToolbarActionButton(String iconId, String tooltip, Runnable action) {
        ToolbarIconButton button = new ToolbarIconButton(iconId);
        Dimension size = new Dimension(38, 36);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setToolTipText(tooltip);
        }
        button.addActionListener(e -> {
            if (action != null) action.run();
        });
        return button;
    }

    private void initUI() {
        // Build right-side controls (journal-specific) that live inside the main frosted bar
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightToolbar.setOpaque(false);
        if (supportsClockButton()) {
            rightToolbar.add(createToolbarActionButton("clock", "Insert time snapshot", this::insertClockSnapshot));
        }
        installExtraRightToolbarButtons(rightToolbar);
        rightToolbar.add(createToolbarActionButton("fullscreen", "Toggle focus mode", this::toggleDistractionFree));
        rightToolbar.add(createToolbarActionButton("backgroundoptions", "Choose wallpaper", this::openEntryBackgroundSettings));

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
                "",
                null,
                (selected) -> setTypingStyleBold(selected),
                (selected) -> setTypingStyleItalic(selected),
                (selected) -> setTypingStyleUnderline(selected),
                (selected) -> setTypingStyleStrike(selected),
                (fontName) -> {
                    int size = contentArea.getFont() != null ? contentArea.getFont().getSize() : SettingsStore.get().getJournalFontSize();
                    CustomFontApplier.applyToTextPane(contentArea, fontName, size);
                    applyParagraphFontToAll();
                },
                (size) -> {
                    contentArea.setFont(contentArea.getFont().deriveFont(size.floatValue()));
                    applyParagraphFontToAll();
                    SettingsStore.get().setJournalFontSize(size);
                    SettingsStore.get().save();
                },
                this::applyLineSpacing,
                rightToolbar,
                () -> main.ui.components.editor.RichTextStyler.toggleBulletList(contentArea),
                () -> main.ui.components.editor.RichTextStyler.toggleNumberedList(contentArea),
                () -> RichTextStyler.applyHeaderToSelection(contentArea),
                getCodeBlockInsertAction(),
                this::insertTextDivider
        );

        // Bind the shared title field to our reference used elsewhere
        titleField = sharedToolbar.getTitleField();
        toolbarContainer = sharedToolbar.getContainer();
        add(toolbarContainer, BorderLayout.NORTH);

        if (SettingsStore.get().isEditorHeaderStampEnabled()) {
            String stamp = buildHeaderStampText();
            if (stamp != null && !stamp.isBlank()) {
                headerStamp = new HandwrittenHeaderStrip();
                headerStamp.setStampText(stamp);
                headerStamp.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }

        // Stack toolbar + mood controls (mood lives below the frosted bar)
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

        if (supportsMoodControls()) {
            JPanel moodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            moodRow.setOpaque(false);
            moodSlider = new MoodSlider();
            moodSlider.setHoverFadeEnabled(true);
            moodRow.add(moodSlider);
            moodSlider.addChangeListener(e -> {
                if (detailedMoodPanel == null || !detailedMoodPanel.hasSnapshot()) {
                    updateMoodSummaryPills(null, moodSlider.getValue());
                }
                try { SimEventBus.get().emitMoodChanged((double) moodSlider.getValue()); } catch (Throwable ignored) {}
            });

            moodDetailsToggleButton = new ToolbarMenuIconButton("", "forward");
            moodDetailsToggleButton.setToolTipText("Show detailed emotions");
            moodDetailsToggleButton.setIconOpacity(0.8f);
            moodDetailsToggleButton.setPreferredSize(new Dimension(32, 32));
            moodDetailsToggleButton.setMinimumSize(new Dimension(32, 32));
            moodDetailsToggleButton.setMaximumSize(new Dimension(32, 32));
            moodDetailsToggleButton.addActionListener(e -> setDetailedMoodExpanded(
                    detailedMoodPanel == null || !detailedMoodPanel.isExpanded()
            ));
            moodRow.add(moodDetailsToggleButton);

            detailedMoodPanel = new DetailedMoodPanel((composite, snapshot) -> {
                try {
                    if (moodSlider != null) {
                        moodSlider.setValue(composite);
                    }
                    updateMoodSummaryPills(snapshot, composite);
                    SimEventBus.get().emitMoodChanged((double) composite);
                } catch (Throwable ignored) {}
            });
            detailedMoodPanel.setBorder(BorderFactory.createEmptyBorder(6, 30, 0, 6));

            moodContainer = new JPanel(new BorderLayout());
            moodContainer.setOpaque(false);
            moodContainer.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
            moodContainer.add(moodRow, BorderLayout.NORTH);
            moodContainer.add(detailedMoodPanel, BorderLayout.CENTER);
            moodSummaryPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            moodSummaryPills.setOpaque(false);
            moodSummaryPills.setBorder(BorderFactory.createEmptyBorder(4, 30, 0, 6));
            moodSummaryPills.setVisible(false);
            moodContainer.add(moodSummaryPills, BorderLayout.SOUTH);
            toolbarGroup.add(Box.createVerticalStrut(6));
            toolbarGroup.add(moodContainer);
            refreshDetailedMoodToggleVisual();
            refreshMoodTrendBaselineFromHistory();
            updateMoodSummaryPills(null, moodSlider.getValue());
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
                return getEditorGlassOpacity();
            }
        };
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Content Area: Rich text editor (StyledDocument)
        contentArea = new CustomFontTextPane();
        contentArea.setDoubleBuffered(true);

        // Load font settings from Appearance settings
        String fontFamily = SettingsStore.get().getEditorFontFamily();
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        String lineSpacingStr = SettingsStore.get().getEditorLineSpacing();
        CustomFontApplier.applyToTextPane(contentArea, fontFamily, savedFontSize);
        if (titleField != null) {
            titleField.setFont(CustomFontApplier.resolveUiFont(fontFamily, savedFontSize));
            titleField.setPlaceholder(null);
        }
        // JTextPane handles wrapping automatically via view; ensure editor kit is styled
        contentArea.setEditorKit(new main.ui.components.editor.CustomFontEditorKit());
        contentArea.setOpaque(false);
        baseTextMargin = contentArea.getMargin();
        applyPaperFeelInsets();
        // Apply line spacing from settings
        float spacing = resolveLineSpacing(lineSpacingStr);
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = (StyledDocument) contentArea.getStyledDocument();
                MutableAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setLineSpacing(attrs, spacing);
                applyParagraphRhythm(attrs);
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
        installHeaderShortcut(contentArea);

        // Enable link detection and styling on paste (deferred until displayable)
        LinkManager.installWhenReady(contentArea);

        // Keep formatting toggles in sync with caret/selection changes
        contentArea.addCaretListener(e -> updateFormattingToggleState());

        // Sync toolbar toggle states from caret typing attributes (after contentArea exists)
        contentArea.addCaretListener(e -> {
            RichTextStyler.StyleState st = RichTextStyler.getTypingState(contentArea);
            sharedToolbar.setToggleStates(st.bold(), st.italic(), st.underline(), st.strike());
        });
        CurrentLineGlowHighlighter.install(contentArea, () -> SettingsStore.get().isEditorTypographyPolishEnabled());

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
                scheduleMetricsUpdate();
                if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                scheduleMetricsUpdate();
                if (autosaveCoordinator != null && !UndoRedoManager.isUndoOrRedoInProgress()) autosaveCoordinator.markDirty();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                scheduleMetricsUpdate();
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
        paperViewport = new PaperFeelViewport(contentArea);
        paperViewport.setPaperFeelEnabled(SettingsStore.get().isEditorPaperFeelEnabled());
        paperViewport.setView(contentArea);
        scrollPane.setViewport(paperViewport);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
        wordCountLabel = new JLabel("Words: 0");
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

    protected String getHelpTitle() {
        return "Journal Editor";
    }

    protected String getHelpMessage() {
        return "<html><body style='text-align:left;'>"
                + "<b>Journal editor basics</b><br>"
                + "• Title and rich-text formatting (bold/italic/underline/strike).<br>"
                + "• Bullet and numbered lists for structure.<br>"
                + "• Background settings and distraction-free mode in the toolbar.<br>"
                + "• Mood slider logs overall mood; use the arrow button for detailed emotions.<br>"
                + "• Autosave keeps changes safe while you write."
                + "</body></html>";
    }

    protected void showHelpDialog() {
        CustomMessageDialog.display(this, getHelpTitle(), getHelpMessage(), false);
    }

    private void installHeaderShortcut(JComponent editor) {
        try {
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            editor.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_H, meta | InputEvent.SHIFT_DOWN_MASK), "header-selection");
            editor.getActionMap().put("header-selection", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (contentArea.getSelectionStart() != contentArea.getSelectionEnd()) {
                        RichTextStyler.applyHeaderToSelection(contentArea);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    // --- Sim helpers ---
    private void emitTypingSnapshot() {
        emitTypingSnapshot(contentArea != null ? contentArea.getText() : "");
    }

    private void emitTypingSnapshot(String text) {
        try {
            if (text == null) return;
            int max = 500;
            int len = text.length();
            int start = Math.max(0, len - max);
            String snapshot = text.substring(start);
            if (snapshot.equals(lastTypingSnapshot)) return;
            lastTypingSnapshot = snapshot;
            SimEventBus.get().emitTyping(snapshot);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void scheduleMetricsUpdate() {
        if (metricsDebounceTimer == null) {
            metricsDebounceTimer = new javax.swing.Timer(METRICS_DEBOUNCE_MS, e -> {
                metricsDebounceTimer.stop();
                String text = contentArea != null ? contentArea.getText() : "";
                updateWordCount(text);
                emitTypingSnapshot(text);
            });
            metricsDebounceTimer.setRepeats(false);
        }
        metricsDebounceTimer.restart();
    }

    private void ensureSimStyles(){
        StyledDocument sd = (StyledDocument) contentArea.getDocument();
        // Brighter sky blue for inserted Sim guidance text.
        Color simBlue = new Color(108, 194, 255);
        // Ensure a normal/user text style exists (used to reset typing attributes)
        Style normal = sd.getStyle("normalText");
        if (normal == null) {
            normal = sd.addStyle("normalText", null);
        }
        Color baseText = contentArea != null && contentArea.getForeground() != null
                ? contentArea.getForeground()
                : AeroTheme.TEXT_PRIMARY;
        StyleConstants.setForeground(normal, baseText);
        // Default character attributes (not bold/italic/underline)
        StyleConstants.setBold(normal, false);
        StyleConstants.setItalic(normal, false);
        StyleConstants.setUnderline(normal, false);
        Style body = sd.getStyle("simGuidanceBody");
        if (body == null) {
            body = sd.addStyle("simGuidanceBody", null);
        }
        // Re-apply in case this style already existed with an older color.
        StyleConstants.setForeground(body, simBlue);
        Style header = sd.getStyle("simGuidanceHeader");
        if (header == null) {
            header = sd.addStyle("simGuidanceHeader", body);
            StyleConstants.setBold(header, true);
        }
        // Keep header aligned with guidance body color if reused from previous sessions.
        StyleConstants.setForeground(header, simBlue);
        Style thinking = sd.getStyle("simThinking");
        if (thinking == null) {
            thinking = sd.addStyle("simThinking", null);
            StyleConstants.setItalic(thinking, true);
            StyleConstants.setForeground(thinking, Color.GRAY);
        }
    }

    private void snapshotTypingAttributesForGuidance() {
        try {
            if (!(contentArea.getEditorKit() instanceof StyledEditorKit kit)) {
                pendingGuidanceTypingAttrs = null;
                return;
            }
            SimpleAttributeSet snapshot = new SimpleAttributeSet(kit.getInputAttributes());
            if (!snapshot.isDefined(StyleConstants.Foreground)) {
                Color baseText = contentArea != null && contentArea.getForeground() != null
                        ? contentArea.getForeground()
                        : AeroTheme.TEXT_PRIMARY;
                StyleConstants.setForeground(snapshot, baseText);
            }
            pendingGuidanceTypingAttrs = snapshot;
        } catch (RuntimeException ignored) {
            pendingGuidanceTypingAttrs = null;
        }
    }

    private void restoreTypingAttributesAfterGuidance(Style normalFallback) {
        try {
            int docEnd = contentArea.getDocument().getLength();
            contentArea.select(docEnd, docEnd);
            AttributeSet restoreAttrs = pendingGuidanceTypingAttrs;
            if (restoreAttrs != null) {
                contentArea.setCharacterAttributes(restoreAttrs, true);
            } else if (normalFallback != null) {
                contentArea.setCharacterAttributes(normalFallback, true);
            }
            updateFormattingToggleState();
        } catch (RuntimeException ignored) {
            // ignore
        } finally {
            pendingGuidanceTypingAttrs = null;
        }
    }

    private void showGuidanceThinkingPlaceholder(){
        try {
            ensureSimStyles();
            snapshotTypingAttributesForGuidance();
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
            } else {
                contentArea.setCaretPosition(sd.getLength());
            }
            restoreTypingAttributesAfterGuidance(normal);
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

            contentArea.setCaretPosition(Math.min(doc.getLength(), pos + 2));
            contentArea.requestFocusInWindow();
        } catch (BadLocationException ignored) {}
    }

    private void insertTextDivider() {
        try {
            int targetWidth = Math.max(0, contentArea.getVisibleRect().width - 40);
            if (targetWidth <= 0) targetWidth = contentArea.getWidth() - 40;
            if (targetWidth < 320) targetWidth = 520;
            targetWidth = Math.min(780, targetWidth);
            int targetHeight = DateDividerPainter.DEFAULT_HEIGHT;
            BufferedImage img = DateDividerPainter.renderImage(targetWidth, targetHeight, "");

            File dir = new File(journalFolder, "attachments");
            if (!dir.exists()) dir.mkdirs();
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date()) + "_divider.png";
            File out = new File(dir, name);
            try { ImageIO.write(img, "PNG", out); } catch (IOException ignored) {}

            ImageIcon icon = new ImageIcon(img);
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute("imageSourceFile", out);
            StyledDocument doc = contentArea.getStyledDocument();
            int pos = contentArea.getCaretPosition();
            if (pos > 0) {
                String prev = doc.getText(pos - 1, 1);
                if (!"\n".equals(prev)) {
                    doc.insertString(pos, "\n", null);
                    pos++;
                }
            }
            doc.insertString(pos, " ", attrs);
            doc.insertString(pos + 1, "\n", null);

            contentArea.setCaretPosition(Math.min(doc.getLength(), pos + 2));
            contentArea.requestFocusInWindow();
        } catch (Exception ignored) {}
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
        updateWordCount(contentArea != null ? contentArea.getText() : "");
    }

    private void updateWordCount(String text) {
        int count = countWords(text);
        if (wordCountLabel != null) {
            wordCountLabel.setText("Words: " + count);
            return;
        }

        // Fallback: locate the label if for some reason it is not cached.
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel panel) {
                for (Component innerComp : panel.getComponents()) {
                    if (innerComp instanceof JLabel label && label.getText().startsWith("Words:")) {
                        label.setText("Words: " + count);
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
        final DetailedMoodPanel.DetailedMoodSnapshot[] detailHolder = new DetailedMoodPanel.DetailedMoodSnapshot[1];
        final boolean[] hasDetailHolder = new boolean[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                titleHolder[0] = titleField.getText().trim();
                contentHolder[0] = contentArea.getText();
                moodHolder[0] = (moodSlider != null) ? moodSlider.getValue() : -1;
                if (detailedMoodPanel != null) {
                    detailHolder[0] = detailedMoodPanel.captureSnapshot();
                    hasDetailHolder[0] = detailedMoodPanel.hasSnapshot();
                    if (hasDetailHolder[0]) {
                        moodHolder[0] = detailedMoodPanel.computeCompositeScore();
                    }
                }
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    titleHolder[0] = titleField.getText().trim();
                    contentHolder[0] = contentArea.getText();
                    moodHolder[0] = (moodSlider != null) ? moodSlider.getValue() : -1;
                    if (detailedMoodPanel != null) {
                        detailHolder[0] = detailedMoodPanel.captureSnapshot();
                        hasDetailHolder[0] = detailedMoodPanel.hasSnapshot();
                        if (hasDetailHolder[0]) {
                            moodHolder[0] = detailedMoodPanel.computeCompositeScore();
                        }
                    }
                });
            }
        } catch (Exception invokeErr) {
            // If we cannot read UI state, fail gracefully
            return;
        }
        String title = titleHolder[0];
        String content = contentHolder[0];
        int moodValue = moodHolder[0]; // 0 - 100
        DetailedMoodPanel.DetailedMoodSnapshot moodDetails = hasDetailHolder[0] ? detailHolder[0] : null;

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
            meta.moodDetails = detailedMoodArrayFromSnapshot(moodDetails);
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
                TokenizedDocument tokenized = buildTokenizedDocument((StyledDocument) contentArea.getDocument());
                manifestForRestore = tokenized.manifest;
                if (manifestForRestore == null) manifestForRestore = "";
                writer.println("IMGMAP:" + manifestForRestore);
                writer.println();
                writer.flush();

                // Append RTF styling
                RTFEditorKit kit = new RTFEditorKit();
                StyledDocument sd = tokenized.doc;
                try {
                    // Write the tokenized document copy to avoid mutating the live editor.
                    kit.write(baos, sd, 0, sd.getLength());
                } catch (BadLocationException ble) {
                    // fallback to plain text if unexpected
                    baos.write(content.getBytes(StandardCharsets.UTF_8));
                }
            }

            byte[] data = baos.toByteArray();
            int wordCount = meta.guided
                    ? countWordsInGuidedResponses(questionResponses, guidedQuestions)
                    : countWords(stripImageTokens(content));
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

            // Log mood only after the entry file is successfully persisted,
            // based on the exact metadata that was written for this save.
            logMoodFromPersistedMetadata(meta);
            setMoodTrendBaseline(meta.mood, meta.moodDetails);

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

            // Update status to Saved · time
            updateSaveIndicatorFromCurrentFile();
            
            // Toast notification for manual saves only
            if (!isAutosaving) {
                main.ui.components.toast.ToastOverlay.success("Entry saved manually.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving entry.", true).showDialog());
            if (saveIndicator != null) saveIndicator.setError("Error saving");
            main.ui.components.toast.ToastOverlay.error("Failed to save entry");
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
        try { if (detailedMoodPanel != null) detailedMoodPanel.setEnabled(!readOnly); } catch (Throwable ignored) {}
        try { if (moodDetailsToggleButton != null) moodDetailsToggleButton.setEnabled(!readOnly); } catch (Throwable ignored) {}
        try { if (saveButton != null) saveButton.setEnabled(!readOnly); } catch (Throwable ignored) {}
        if (readOnly && autosaveCoordinator != null) {
            try { autosaveCoordinator.stop(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        registerSimGuidanceListenerIfSupported();
    }

    @Override
    public void removeNotify() {
        try { if (autosaveCoordinator != null) autosaveCoordinator.shutdown(); } catch (Throwable ignored) {}
        awaitingGuidanceResponse = false;
        awaitingTemplateResponse = false;
        unregisterSimGuidanceListener();
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

    private static final class TokenizedDocument {
        private final StyledDocument doc;
        private final String manifest;

        private TokenizedDocument(StyledDocument doc, String manifest) {
            this.doc = doc;
            this.manifest = manifest;
        }
    }

    private TokenizedDocument buildTokenizedDocument(StyledDocument src) {
        DefaultStyledDocument out = new DefaultStyledDocument();
        StringBuilder manifest = new StringBuilder(128);

        try {
            Element root = src.getDefaultRootElement();
            int paraCount = root.getElementCount();
            int[] paraStarts = new int[paraCount];
            AttributeSet[] paraAttrs = new AttributeSet[paraCount];
            int[] paraOutOffsets = new int[paraCount];
            java.util.Arrays.fill(paraOutOffsets, -1);
            for (int i = 0; i < paraCount; i++) {
                Element p = root.getElement(i);
                paraStarts[i] = p.getStartOffset();
                paraAttrs[i] = p.getAttributes();
            }

            int nextPara = 0;
            int pos = 0;
            int len = src.getLength();
            int outPos = 0;
            while (pos < len) {
                while (nextPara < paraCount && pos >= paraStarts[nextPara]) {
                    paraOutOffsets[nextPara] = outPos;
                    nextPara++;
                }
                Element el = src.getCharacterElement(pos);
                if (el == null) {
                    pos++;
                    continue;
                }
                AttributeSet as = el.getAttributes();
                Object ico = StyleConstants.getIcon(as);
                int end = Math.min(el.getEndOffset(), len);
                if (ico instanceof ImageIcon) {
                    ImageIcon icon = (ImageIcon) ico;
                    int w = icon.getIconWidth();
                    int h = icon.getIconHeight();
                    Object srcAttr = as.getAttribute("imageSourceFile");
                    File srcFile = (srcAttr instanceof File) ? (File) srcAttr : null;
                    if (srcFile == null) {
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
                    String token = "[[IMG|" + rel + "|" + w + "x" + h + "]]";
                    out.insertString(outPos, token, null);
                    outPos += token.length();
                    if (manifest.length() > 0) manifest.append(';');
                    manifest.append(rel).append('|').append(w).append('x').append(h);
                } else {
                    String seg = src.getText(pos, end - pos);
                    out.insertString(outPos, seg, as);
                    outPos += seg.length();
                }
                pos = end;
            }
            while (nextPara < paraCount) {
                paraOutOffsets[nextPara] = outPos;
                nextPara++;
            }
            for (int i = 0; i < paraCount; i++) {
                int start = paraOutOffsets[i];
                if (start >= 0 && start <= out.getLength()) {
                    out.setParagraphAttributes(start, 1, paraAttrs[i], true);
                }
            }
        } catch (BadLocationException ignored) {
            try {
                out.insertString(0, src.getText(0, src.getLength()), null);
            } catch (BadLocationException ignoredAgain) {}
        }

        return new TokenizedDocument(out, manifest.toString());
    }

    private void restoreIconsFromTokens(StyledDocument doc, String manifest) {
        try {
            String text = doc.getText(0, doc.getLength());
            java.util.regex.Matcher m = IMAGE_TOKEN_PATTERN.matcher(text);
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
        if (text == null || text.isEmpty()) return 0;
        int nativeCount = NativeAccess.countWords(text);
        if (nativeCount >= 0) return nativeCount;
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

    private static int countWordsInGuidedResponses(java.util.Map<Integer, String> responses, String[] questions) {
        if (responses == null || responses.isEmpty()) return 0;
        if (questions == null || questions.length == 0) {
            int total = 0;
            for (String response : responses.values()) {
                total += countWords(stripImageTokens(response));
            }
            return total;
        }
        int total = 0;
        for (int i = 0; i < questions.length; i++) {
            String response = responses.get(i);
            total += countWords(stripImageTokens(response));
        }
        return total;
    }

    private static String stripImageTokens(String text) {
        if (text == null || text.isEmpty()) return "";
        if (text.indexOf("[[IMG|") < 0) return text;
        return IMAGE_TOKEN_PATTERN.matcher(text).replaceAll(" ");
    }

    private void logMoodFromPersistedMetadata(EntryFileFormat.EntryMeta meta) {
        if (meta == null || meta.mood < 0) return;
        DetailedMoodPanel.DetailedMoodSnapshot details = detailedMoodSnapshotFromArray(meta.moodDetails);
        if (details != null) {
            recordMood(meta.mood, details);
        } else {
            recordMood(meta.mood);
        }
    }

    private void refreshMoodTrendBaselineFromHistory() {
        try {
            List<MoodFile.MoodRecord> records = MoodFile.readAllRecords();
            if (records == null || records.isEmpty()) {
                moodTrendBaseline = null;
                return;
            }

            MoodFile.MoodRecord latest = null;
            for (MoodFile.MoodRecord record : records) {
                if (record == null || record.timestamp == null) continue;
                if (latest == null || record.timestamp.isAfter(latest.timestamp)) {
                    latest = record;
                }
            }

            if (latest == null) {
                latest = records.get(records.size() - 1);
            }
            moodTrendBaseline = baselineFromRecord(latest);
        } catch (Throwable ignored) {
            moodTrendBaseline = null;
        }
    }

    private void setMoodTrendBaseline(int composite, int[] details) {
        moodTrendBaseline = new MoodTrendBaseline(
                Math.max(0, Math.min(100, composite)),
                normalizeDetailArray(details)
        );
    }

    private static MoodTrendBaseline baselineFromRecord(MoodFile.MoodRecord record) {
        if (record == null) return null;
        return new MoodTrendBaseline(
                Math.max(0, Math.min(100, record.composite)),
                normalizeDetailArray(record.details)
        );
    }

    private static int[] normalizeDetailArray(int[] values) {
        int[] out = new int[8];
        for (int i = 0; i < out.length; i++) {
            out[i] = -1;
        }
        if (values == null) return out;
        int limit = Math.min(values.length, out.length);
        for (int i = 0; i < limit; i++) {
            int v = values[i];
            out[i] = v < 0 ? -1 : Math.max(0, Math.min(100, v));
        }
        return out;
    }

    private static String trendArrowSuffix(int current, int previous, int threshold) {
        if (previous < 0) return "";
        int delta = current - previous;
        if (delta >= threshold) return "↑";
        if (delta <= -threshold) return "↓";
        return "";
    }

    private static String trendLabel(int current, int previous, int threshold) {
        if (previous < 0) return "";
        int delta = current - previous;
        if (delta >= threshold) return "up";
        if (delta <= -threshold) return "down";
        return "steady";
    }

    private void updateMoodSummaryPills(DetailedMoodPanel.DetailedMoodSnapshot snapshot, int composite) {
        if (moodSummaryPills == null) return;
        int safeComposite = Math.max(0, Math.min(100, composite));
        java.util.List<DetailedMoodPanel.EmotionIntensity> strongest =
                DetailedMoodPanel.strongestEmotions(snapshot, 2);
        MoodTrendBaseline baseline = moodTrendBaseline;
        moodSummaryPills.removeAll();

        if (strongest.isEmpty()) {
            moodSummaryPills.setVisible(false);
            moodSummaryPills.revalidate();
            moodSummaryPills.repaint();
            return;
        }

        String moodTrend = baseline != null ? trendArrowSuffix(safeComposite, baseline.composite, 3) : "";
        MoodSummaryPill moodPill = new MoodSummaryPill(
                "Mood " + safeComposite + moodTrend,
                new Color(196, 208, 226),
                new Color(224, 232, 244),
                new Color(150, 164, 184)
        );
        if (baseline != null) {
            moodPill.setToolTipText("Previous mood " + baseline.composite + " (" +
                    trendLabel(safeComposite, baseline.composite, 3) + ")");
        }
        moodSummaryPills.add(moodPill);

        for (DetailedMoodPanel.EmotionIntensity emotion : strongest) {
            Color base = DetailedMoodPanel.emotionColor(emotion.index);
            int previousValue = baseline != null ? baseline.detailAt(emotion.index) : -1;
            String trend = trendArrowSuffix(emotion.value, previousValue, 4);
            MoodSummaryPill pill = new MoodSummaryPill(
                    emotion.name + trend,
                    blend(base, Color.WHITE, 0.44f),
                    blend(base, Color.WHITE, 0.70f),
                    blend(base, new Color(90, 96, 112), 0.34f)
            );
            String semantic = DetailedMoodPanel.semanticIntensityLabel(emotion.index, emotion.value);
            if (previousValue >= 0) {
                pill.setToolTipText(emotion.name + " · " + semantic + " (" + emotion.value
                        + ") · previous " + previousValue + " ("
                        + trendLabel(emotion.value, previousValue, 4) + ")");
            } else {
                pill.setToolTipText(emotion.name + " · " + semantic + " (" + emotion.value + ")");
            }
            moodSummaryPills.add(pill);
        }

        moodSummaryPills.setVisible(true);
        moodSummaryPills.revalidate();
        moodSummaryPills.repaint();
    }

    private static Color blend(Color a, Color b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        float inv = 1f - k;
        int r = Math.round(a.getRed() * k + b.getRed() * inv);
        int g = Math.round(a.getGreen() * k + b.getGreen() * inv);
        int bl = Math.round(a.getBlue() * k + b.getBlue() * inv);
        int alpha = Math.round(a.getAlpha() * k + b.getAlpha() * inv);
        return new Color(r, g, bl, alpha);
    }

    private static final class MoodSummaryPill extends JLabel {
        private final Color top;
        private final Color bottom;
        private final Color border;

        private MoodSummaryPill(String text, Color top, Color bottom, Color border) {
            super(text);
            this.top = top;
            this.bottom = bottom;
            this.border = border;
            setOpaque(false);
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            setForeground(new Color(55, 62, 76));
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(24, d.height + 2);
            d.width += 2;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(16, h - 6);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class MoodTrendBaseline {
        private final int composite;
        private final int[] details;

        private MoodTrendBaseline(int composite, int[] details) {
            this.composite = Math.max(0, Math.min(100, composite));
            this.details = normalizeDetailArray(details);
        }

        private int detailAt(int index) {
            if (index < 0 || index >= details.length) return -1;
            return details[index];
        }
    }

    private void recordMood(int moodValue) {
        try {
            MoodFile.appendNow(moodValue);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    private void recordMood(int composite,
                             DetailedMoodPanel.DetailedMoodSnapshot details) {
        int[] det = null;
        if (details != null) {
            det = new int[] {
                details.joy, details.calm, details.gratitude, details.energy,
                details.sadness, details.anger, details.anxiety, details.stress
            };
            if (!hasAnyDetailValue(det)) {
                det = null;
            }
        }
        try {
            MoodFile.appendNow(composite, det);
        } catch (Throwable ex) {
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
        try { if (moodSlider != null) moodSlider.setValue(50); } catch (Throwable ignored) {}
        refreshMoodTrendBaselineFromHistory();
        updateMoodSummaryPills(null, moodSlider != null ? moodSlider.getValue() : 50);
        try {
            if (detailedMoodPanel != null) {
                detailedMoodPanel.clearSnapshot();
                setDetailedMoodExpanded(false);
            }
        } catch (Throwable ignored) {}
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
