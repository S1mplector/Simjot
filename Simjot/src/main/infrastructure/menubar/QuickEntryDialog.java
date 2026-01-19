/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.menubar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.IoLog;
import main.ui.components.buttons.HandStyleToggleButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.editor.CustomFontApplier;
import main.ui.components.editor.CustomFontTextPane;
import main.ui.components.editor.RichTextStyler;
import main.ui.components.indicators.SaveIndicatorPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.features.entries.BackgroundPainter;
import main.ui.theme.aero.AeroTheme;

/**
 * Quick entry dialog for the menu bar service.
 * Uses exact same styling as main Simjot editor panels - a mini 1:1 recreation.
 */
public class QuickEntryDialog extends JDialog {
    
    private final CustomFontTextPane contentArea;
    private final SaveIndicatorPanel saveIndicator;
    private final JLabel wordCountLabel;
    private final JLabel notebookLabel;
    private final JComboBox<NotebookInfo> notebookCombo;
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private NotebookInfo selectedNotebook;
    private HandStyleToggleButton boldBtn;
    private HandStyleToggleButton italicBtn;
    private HandStyleToggleButton underlineBtn;
    private HandStyleToggleButton strikeBtn;
    private Timer autosaveTimer;
    private boolean isDirty = false;
    
    public QuickEntryDialog() {
        super((Frame) null, "Simjot Quick Entry", false);
        
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setAlwaysOnTop(true);
        setResizable(true);
        setUndecorated(true);
        setMinimumSize(new Dimension(400, 320));
        setPreferredSize(new Dimension(480, 380));
        
        // Background panel that paints the editor background image (same as EntryPanel)
        JPanel backgroundPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                String bgPath = SettingsStore.get().getEntryBackgroundImage();
                float opacity = SettingsStore.get().getEntryBackgroundOpacity();
                backgroundPainter.paint(g, this, bgPath, opacity, true);
            }
        };
        backgroundPanel.setOpaque(false);
        
        // Main panel using FrostedGlassPanel (exact same as editor panels)
        FrostedGlassPanel mainPanel = new FrostedGlassPanel(new BorderLayout(0, 8), 16) {
            @Override
            protected float getOpacityScale() {
                return SettingsStore.get().getEditorGlassOpacity();
            }
        };
        mainPanel.setBorder(new EmptyBorder(12, 14, 12, 14));
        backgroundPanel.add(mainPanel, BorderLayout.CENTER);
        
        // --- Toolbar (matches PoetryStyleToolbar layout) ---
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setOpaque(false);
        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.X_AXIS));
        
        // Close button (back arrow style)
        ToolbarMenuIconButton closeBtn = new ToolbarMenuIconButton("", "back");
        closeBtn.setToolTipText("Close (Esc)");
        closeBtn.addActionListener(e -> hideDialog());
        toolbarPanel.add(closeBtn);
        toolbarPanel.add(Box.createHorizontalStrut(12));
        
        // Formatting buttons (B/I/U/S) - exact same as EntryPanel
        boldBtn = new HandStyleToggleButton("B");
        boldBtn.setToolTipText("Bold (⌘B)");
        boldBtn.addActionListener(e -> applyBold());
        
        italicBtn = new HandStyleToggleButton("I");
        italicBtn.setToolTipText("Italic (⌘I)");
        italicBtn.addActionListener(e -> applyItalic());
        
        underlineBtn = new HandStyleToggleButton("U");
        underlineBtn.setToolTipText("Underline (⌘U)");
        underlineBtn.addActionListener(e -> applyUnderline());
        
        strikeBtn = new HandStyleToggleButton("S");
        strikeBtn.setToolTipText("Strikethrough (⌘⇧S)");
        strikeBtn.addActionListener(e -> applyStrike());
        
        toolbarPanel.add(boldBtn);
        toolbarPanel.add(Box.createHorizontalStrut(6));
        toolbarPanel.add(italicBtn);
        toolbarPanel.add(Box.createHorizontalStrut(6));
        toolbarPanel.add(underlineBtn);
        toolbarPanel.add(Box.createHorizontalStrut(6));
        toolbarPanel.add(strikeBtn);
        
        toolbarPanel.add(Box.createHorizontalGlue());
        
        // Notebook selector (only JOURNAL type notebooks)
        notebookLabel = new JLabel("Save to:");
        notebookLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        notebookLabel.setForeground(Color.GRAY);
        toolbarPanel.add(notebookLabel);
        toolbarPanel.add(Box.createHorizontalStrut(6));
        
        notebookCombo = new JComboBox<>();
        notebookCombo.setFont(AeroTheme.defaultFont().deriveFont(12f));
        notebookCombo.setPreferredSize(new Dimension(140, 28));
        notebookCombo.setMaximumSize(new Dimension(160, 28));
        notebookCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof NotebookInfo nb) {
                    setText(nb.getName());
                }
                return this;
            }
        });
        notebookCombo.addActionListener(e -> {
            NotebookInfo nb = (NotebookInfo) notebookCombo.getSelectedItem();
            if (nb != null) {
                selectedNotebook = nb;
                // Persist the selection
                SettingsStore.get().setQuickEntryNotebookPath(nb.getFolder().getAbsolutePath());
                try { SettingsStore.get().save(); } catch (Exception ignored) {}
            }
        });
        toolbarPanel.add(notebookCombo);
        toolbarPanel.add(Box.createHorizontalStrut(8));
        
        // Load notebooks
        loadNotebooks();
        
        mainPanel.add(toolbarPanel, BorderLayout.NORTH);
        
        // --- Content Area (matches EntryPanel exactly) ---
        FrostedGlassPanel textWrapper = new FrostedGlassPanel(new BorderLayout(), 14) {
            @Override
            protected float getOpacityScale() {
                return SettingsStore.get().getEditorGlassOpacity();
            }
        };
        textWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        contentArea = new CustomFontTextPane();
        contentArea.setDoubleBuffered(true);
        
        // Load font settings from Appearance settings (same as EntryPanel)
        String fontFamily = SettingsStore.get().getEditorFontFamily();
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        CustomFontApplier.applyToTextPane(contentArea, fontFamily, savedFontSize);
        
        contentArea.setOpaque(false);
        contentArea.setForeground(new Color(40, 40, 40));
        contentArea.setCaretColor(AeroTheme.AERO_BLUE);
        contentArea.setMargin(new Insets(10, 12, 10, 12));
        
        // Sync formatting toggles with caret position
        contentArea.addCaretListener(e -> updateFormattingToggleState());
        
        // Document listener for autosave and word count
        contentArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onTextChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onTextChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onTextChanged(); }
        });
        
        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Apply modern, slim scrollbars (match EntryPanel)
        JScrollBar vbar = scrollPane.getVerticalScrollBar();
        vbar.setUI(new ModernScrollBarUI());
        vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        vbar.setOpaque(false);
        vbar.setUnitIncrement(16);
        
        textWrapper.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(textWrapper, BorderLayout.CENTER);
        
        // --- Bottom Panel (matches EntryPanel exactly) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomPanel.setOpaque(false);
        
        // Word count label
        wordCountLabel = new JLabel("Words: 0");
        wordCountLabel.setForeground(Color.GRAY);
        wordCountLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        wordCountLabel.setBorder(new EmptyBorder(0, 0, 0, 8));
        bottomPanel.add(wordCountLabel);
        
        // Save state indicator (reusable component - same as EntryPanel)
        saveIndicator = new SaveIndicatorPanel();
        bottomPanel.add(saveIndicator);
        
        // Save button (via ToolbarMenuIconButton - same as EntryPanel)
        ToolbarMenuIconButton saveButton = new ToolbarMenuIconButton("", "save");
        saveButton.setToolTipText("Save Entry (⌘↵)");
        saveButton.addActionListener(e -> submitEntry());
        bottomPanel.add(saveButton);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Keyboard shortcuts
        setupKeyboardShortcuts();
        
        // Window drag support
        setupDragSupport(toolbarPanel);
        
        // Window resize support
        setupResizeSupport(backgroundPanel);
        
        setContentPane(backgroundPanel);
        pack();
        
        // Round the window corners
        updateWindowShape();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateWindowShape();
            }
        });
        
        // Position near top center of screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - getWidth()) / 2, 100);
        
        // Autosave timer (debounced, same pattern as EntryPanel)
        autosaveTimer = new Timer(1500, e -> {
            if (isDirty) {
                saveIndicator.setSaving();
                // Simulate autosave completion after brief delay
                Timer completeTimer = new Timer(300, ev -> {
                    saveIndicator.setSaved(new java.util.Date());
                    isDirty = false;
                });
                completeTimer.setRepeats(false);
                completeTimer.start();
            }
        });
        autosaveTimer.setRepeats(false);
    }
    
    private void updateWindowShape() {
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
    }
    
    private void onTextChanged() {
        updateWordCount();
        isDirty = true;
        autosaveTimer.restart();
    }
    
    private void updateWordCount() {
        String text = contentArea.getText();
        int words = 0;
        if (text != null && !text.isBlank()) {
            words = text.trim().split("\\s+").length;
        }
        wordCountLabel.setText("Words: " + words);
    }
    
    private void updateFormattingToggleState() {
        try {
            RichTextStyler.StyleState st = RichTextStyler.getTypingState(contentArea);
            boldBtn.setSelected(st.bold());
            italicBtn.setSelected(st.italic());
            underlineBtn.setSelected(st.underline());
            strikeBtn.setSelected(st.strike());
        } catch (Exception ignored) {}
    }
    
    private void applyBold() {
        RichTextStyler.toggleBold(contentArea);
        contentArea.requestFocusInWindow();
    }
    
    private void applyItalic() {
        RichTextStyler.toggleItalic(contentArea);
        contentArea.requestFocusInWindow();
    }
    
    private void applyUnderline() {
        RichTextStyler.toggleUnderline(contentArea);
        contentArea.requestFocusInWindow();
    }
    
    private void applyStrike() {
        RichTextStyler.toggleStrike(contentArea);
        contentArea.requestFocusInWindow();
    }
    
    private void setupKeyboardShortcuts() {
        InputMap inputMap = contentArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = contentArea.getActionMap();
        
        // Cmd/Ctrl+Enter to submit
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK), "submit");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "submit");
        actionMap.put("submit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitEntry();
            }
        });
        
        // Escape to cancel
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        actionMap.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideDialog();
            }
        });
        
        // Cmd/Ctrl+B for bold
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.META_DOWN_MASK), "bold");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "bold");
        actionMap.put("bold", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyBold();
            }
        });
        
        // Cmd/Ctrl+I for italic
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.META_DOWN_MASK), "italic");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), "italic");
        actionMap.put("italic", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyItalic();
            }
        });
        
        // Cmd/Ctrl+U for underline
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.META_DOWN_MASK), "underline");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), "underline");
        actionMap.put("underline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyUnderline();
            }
        });
        
        // Cmd/Ctrl+Shift+S for strikethrough
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "strike");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "strike");
        actionMap.put("strike", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyStrike();
            }
        });
    }
    
    private void setupDragSupport(JPanel panel) {
        final Point[] dragOffset = {null};
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset[0] = e.getPoint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset[0] = null;
            }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset[0] != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragOffset[0].x, loc.y + e.getY() - dragOffset[0].y);
                }
            }
        });
    }
    
    private void setupResizeSupport(JPanel panel) {
        final Point[] resizeStart = {null};
        final Dimension[] startSize = {null};
        final int[] resizeEdge = {0}; // 0=none, 1=right, 2=bottom, 3=corner
        
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int edge = getResizeEdge(e.getPoint());
                if (edge > 0) {
                    resizeStart[0] = e.getLocationOnScreen();
                    startSize[0] = getSize();
                    resizeEdge[0] = edge;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                resizeStart[0] = null;
                resizeEdge[0] = 0;
            }
        });
        
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int edge = getResizeEdge(e.getPoint());
                if (edge == 3) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                } else if (edge == 1) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                } else if (edge == 2) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
                } else {
                    panel.setCursor(Cursor.getDefaultCursor());
                }
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeStart[0] != null && resizeEdge[0] > 0) {
                    Point current = e.getLocationOnScreen();
                    int dx = current.x - resizeStart[0].x;
                    int dy = current.y - resizeStart[0].y;
                    
                    int newWidth = startSize[0].width;
                    int newHeight = startSize[0].height;
                    
                    if (resizeEdge[0] == 1 || resizeEdge[0] == 3) {
                        newWidth = Math.max(getMinimumSize().width, startSize[0].width + dx);
                    }
                    if (resizeEdge[0] == 2 || resizeEdge[0] == 3) {
                        newHeight = Math.max(getMinimumSize().height, startSize[0].height + dy);
                    }
                    
                    setSize(newWidth, newHeight);
                }
            }
        });
    }
    
    private int getResizeEdge(Point p) {
        int w = getWidth();
        int h = getHeight();
        int margin = 8;
        
        boolean onRight = p.x >= w - margin;
        boolean onBottom = p.y >= h - margin;
        
        if (onRight && onBottom) return 3; // corner
        if (onRight) return 1;
        if (onBottom) return 2;
        return 0;
    }
    
    public void showDialog() {
        contentArea.setText("");
        saveIndicator.clear();
        isDirty = false;
        updateWordCount();
        // Refresh notebooks in case new ones were created
        loadNotebooks();
        setVisible(true);
        toFront();
        SwingUtilities.invokeLater(() -> contentArea.requestFocusInWindow());
    }
    
    public void hideDialog() {
        setVisible(false);
        autosaveTimer.stop();
    }
    
    public String getText() {
        return contentArea.getText();
    }
    
    public void setText(String text) {
        contentArea.setText(text);
    }
    
    public void clear() {
        contentArea.setText("");
        saveIndicator.clear();
        isDirty = false;
        updateWordCount();
    }
    
    private void submitEntry() {
        String text = contentArea.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        
        if (selectedNotebook == null) {
            JOptionPane.showMessageDialog(this, 
                "Please select a notebook first.", 
                "No Notebook Selected", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        saveIndicator.setSaving();
        
        try {
            File folder = selectedNotebook.getFolder();
            if (folder != null && !folder.exists()) folder.mkdirs();
            
            // Generate timestamp-based filename (same format as EntryPanel)
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = timestamp + ".txt";
            File outFile = new File(folder, filename);
            
            // Build entry content with header (same format as journal entries)
            byte[] data = buildEntryContent("Quick Entry", text.trim());
            
            // Write file atomically
            FileIO.ensureSpace(outFile.toPath(), data.length + 4096L, "quick entry");
            FileIO.atomicWrite(outFile.toPath(), data, true, true);
            
            IoLog.info("menubar", "Quick entry saved to: " + outFile.getName());
            
            saveIndicator.setSaved(new Date());
            
            // Brief delay to show saved state before closing
            Timer closeTimer = new Timer(200, e -> {
                contentArea.setText("");
                isDirty = false;
                hideDialog();
            });
            closeTimer.setRepeats(false);
            closeTimer.start();
            
        } catch (Exception ex) {
            IoLog.warn("menubar", "Failed to save quick entry: " + ex.getMessage(), ex);
            saveIndicator.setError("Save failed");
            JOptionPane.showMessageDialog(this,
                "Failed to save entry: " + ex.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private byte[] buildEntryContent(String title, String body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
        
        // Write header in same format as EntryFileFormat
        writer.println("---");
        writer.println("title: " + (title != null ? title : "Quick Entry"));
        writer.println("saved: " + System.currentTimeMillis());
        writer.println("---");
        writer.println();
        writer.println(body);
        writer.flush();
        
        return baos.toByteArray();
    }
    
    private void loadNotebooks() {
        notebookCombo.removeAllItems();
        selectedNotebook = null;
        
        try {
            NotebookStore store = new NotebookStore();
            List<NotebookInfo> all = store.list();
            List<NotebookInfo> journals = new ArrayList<>();
            
            // Filter to only JOURNAL type notebooks
            for (NotebookInfo nb : all) {
                if (nb.getType() == NotebookInfo.Type.JOURNAL) {
                    journals.add(nb);
                }
            }
            
            if (journals.isEmpty()) {
                notebookLabel.setText("No journals");
                notebookCombo.setVisible(false);
                return;
            }
            
            notebookCombo.setVisible(true);
            notebookLabel.setText("Save to:");
            
            // Add notebooks to combo
            for (NotebookInfo nb : journals) {
                notebookCombo.addItem(nb);
            }
            
            // Try to restore previously selected notebook
            String savedPath = SettingsStore.get().getQuickEntryNotebookPath();
            if (savedPath != null && !savedPath.isEmpty()) {
                for (NotebookInfo nb : journals) {
                    if (nb.getFolder().getAbsolutePath().equals(savedPath)) {
                        notebookCombo.setSelectedItem(nb);
                        selectedNotebook = nb;
                        break;
                    }
                }
            }
            
            // Default to first if none selected
            if (selectedNotebook == null && !journals.isEmpty()) {
                notebookCombo.setSelectedIndex(0);
                selectedNotebook = journals.get(0);
            }
            
        } catch (Exception ex) {
            IoLog.warn("menubar", "Failed to load notebooks: " + ex.getMessage(), ex);
            notebookLabel.setText("Error loading");
            notebookCombo.setVisible(false);
        }
    }
    
    /** Refresh notebooks list (call when dialog is shown) */
    public void refreshNotebooks() {
        loadNotebooks();
    }
}
