package main.ui.features.entries;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import javax.swing.*;
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
import main.ui.components.fields.ModernTextField;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.util.EditorUIUtils;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.utils.PoemBackgroundDialog;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.infrastructure.backup.NotebookInfo;

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

    public PoemPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
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
        // --- Modern Toolbar Container ---
        toolbarContainer = new JPanel(new BorderLayout(0, 5));
        // Solid background so the page wallpaper does not seep through the toolbar
        toolbarContainer.setOpaque(true);
        toolbarContainer.setBackground(new Color(0xE7, 0xE7, 0xE7)); // #e7e7e7
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar row
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topToolbar.setOpaque(false);

        // Back button -> return to this notebook's entries manager
        NotebookInfo nbInfo = new NotebookInfo(
                journalFolder.getName(),
                NotebookInfo.Type.POETRY,
                journalFolder,
                journalFolder.lastModified(),
                null
        );
        ToolbarIconButton backButton = EditorUIUtils.createBackToEntriesButton(app, nbInfo);
        topToolbar.add(backButton);
        
        // Right-side controls
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.setOpaque(false);
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
        // Export button (Markdown)
        ToolbarIconButton exportBtn = new ToolbarIconButton("export");
        exportBtn.setToolTipText("Export to Markdown");
        exportBtn.addActionListener(e -> exportAsMarkdown());
        rightToolbar.add(exportBtn);
        rightToolbar.add(dfBtn);
        rightToolbar.add(settingsBtn);

        // Title label & field
        JLabel titleLabel = new JLabel("Poem Title:");
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 16));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(titleLabel);

        poemTitleField = new ModernTextField(24);
        poemTitleField.setFont(new Font("Serif", Font.BOLD, 16));
        // Placeholder for consistency with EntryPanel
        if (poemTitleField instanceof ModernTextField mtf) {
            mtf.setPlaceholder("Untitled poem");
        }
        topToolbar.add(poemTitleField);

        // Formatting toolbar (Bold/Italic/Underline)
        RoundedButton boldBtn = new RoundedButton("B");
        boldBtn.addActionListener(e -> toggleStyle(StyleConstants.CharacterConstants.Bold));
        RoundedButton italicBtn = new RoundedButton("I");
        italicBtn.addActionListener(e -> toggleStyle(StyleConstants.CharacterConstants.Italic));
        RoundedButton underlineBtn = new RoundedButton("U");
        underlineBtn.addActionListener(e -> toggleStyle(StyleConstants.CharacterConstants.Underline));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(boldBtn);
        topToolbar.add(italicBtn);
        topToolbar.add(underlineBtn);

        // Bottom toolbar row with font selector
        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomToolbar.setOpaque(false);
        
        JLabel fontLabel = new JLabel("Font:");
        fontLabel.setForeground(Color.DARK_GRAY);
        fontLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        bottomToolbar.add(fontLabel);

        String[] fonts = {"Serif", "Georgia", "Verdana", "Garamond", "Baskerville", "Cursive"};
        JComboBox<String> fontSelector = new JComboBox<>(fonts);
        fontSelector.setUI(new ModernComboBoxUI());
        fontSelector.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontSelector.setSelectedItem("Serif"); // Default font
        fontSelector.addActionListener(e -> {
            String selectedFont = (String) fontSelector.getSelectedItem();
            Font currentFont = poemEditor.getFont();
            poemEditor.setFont(new Font(selectedFont, currentFont.getStyle(), currentFont.getSize()));
            applyParagraphFontToAll();
        });
        bottomToolbar.add(fontSelector);

        // Font size selector
        bottomToolbar.add(new JLabel(" Size:"));
        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 28};
        JComboBox<Integer> sizeSelector = new JComboBox<>(sizes);
        sizeSelector.setUI(new ModernComboBoxUI());
        sizeSelector.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        sizeSelector.setSelectedItem(SettingsStore.get().getPoemFontSize());
        sizeSelector.addActionListener(e -> {
            Integer sz = (Integer) sizeSelector.getSelectedItem();
            if (sz != null) {
                poemEditor.setFont(poemEditor.getFont().deriveFont(sz.floatValue()));
                applyParagraphFontToAll();
                // persist preferred size
                SettingsStore.get().setPoemFontSize(sz);
                SettingsStore.get().save();
            }
        });
        bottomToolbar.add(sizeSelector);

        // Line spacing
        bottomToolbar.add(new JLabel(" Spacing:"));
        JComboBox<String> spacing = new JComboBox<>(new String[]{"1.0", "1.2", "1.5"});
        spacing.setUI(new ModernComboBoxUI());
        spacing.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        spacing.setSelectedIndex(0);
        spacing.addActionListener(e -> applyLineSpacing((String) spacing.getSelectedItem()));
        bottomToolbar.add(spacing);

        // Add both toolbar rows to the container
        toolbarContainer.add(topToolbar, BorderLayout.NORTH);
        toolbarContainer.add(bottomToolbar, BorderLayout.CENTER);
        toolbarContainer.add(rightToolbar, BorderLayout.EAST);

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

        JScrollPane scrollPane = new JScrollPane(poemEditor);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Add some vertical space between title and text area
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(Box.createRigidArea(new Dimension(0, 15)), BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

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
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); if (autosaveManager != null) autosaveManager.markDirty(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); if (autosaveManager != null) autosaveManager.markDirty(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); if (autosaveManager != null) autosaveManager.markDirty(); }
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
        RoundedButton inspireButton = new RoundedButton("✨ Inspire Me");
        inspireButton.addActionListener(e -> showInspirationalWord());
        centerFlow.add(statusLabel);
        centerFlow.add(inspireButton);
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
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Please enter a title or some content for your poem.", true).showDialog();
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

    private void exportAsMarkdown() {
        String title = poemTitleField.getText().trim();
        String content = poemEditor.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Export", "Nothing to export.", true).showDialog();
            return;
        }
        try {
            File mdFile;
            if (currentFile != null) {
                String base = currentFile.getName().replaceFirst("\\.poem$", "");
                mdFile = new File(journalFolder, base + ".md");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String ts = sdf.format(new Date());
                mdFile = new File(journalFolder, ts + ".md");
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(mdFile))) {
                if (!title.isEmpty()) writer.println("# " + title);
                writer.println();
                writer.print(content);
            }
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Export", "Exported to: " + mdFile.getName(), false).showDialog();
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error exporting Markdown.", true).showDialog();
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
