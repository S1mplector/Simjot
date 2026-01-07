/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.ui.app.JournalApp;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.RichTextStyler;
import main.ui.dialog.file.SimjotFileChooser;

/**
 * Notetaking editor: extends the standard EntryPanel with
 * enhanced image pasting (wider max width) and richer formatting shortcuts.
 */
public class NotetakingPanel extends EntryPanel {

    // --- Drawing overlay state ---
    private DrawingOverlay drawingOverlay;
    private boolean drawingEnabled = false; // capture mode (draw interactions)
    private boolean overlayVisible = true;  // paint overlay visibility
    private DrawTool currentDrawTool = DrawTool.PEN;
    private final List<DrawStroke> drawStrokes = new ArrayList<>(); // persisted per-note
    private final List<UndoAction> undoStack = new ArrayList<>(); // for undo/redo
    private final List<UndoAction> redoStack = new ArrayList<>();
    private JWindow colorWindow;
    private JPopupMenu drawToolMenu;
    private Color penColor = new Color(20,20,20,255);
    private Color highlightColor = new Color(255,235,59,120);
    private int penThickness = 3;
    private int highlightThickness = 18;
    private int eraserRadius = 18;
    private JSpinner strokeSpinner;
    private JButton colorBtn;
    private JLayeredPane overlayStack;
    private main.ui.components.buttons.ToolbarIconButton textModeBtn;
    private main.ui.components.buttons.ToolbarIconButton paintModeBtn;
    private main.ui.components.buttons.ToolbarIconButton highlighterBtn;
    private main.ui.components.buttons.ToolbarIconButton eraserBtn;
    private main.ui.components.buttons.ToolbarIconButton lassoBtn;
    private main.ui.components.buttons.TextColorButton textColorBtn;
    private Color currentTextColor = Color.BLACK;
    private EditingMode editingMode = EditingMode.TEXT;
    private final java.util.Deque<Color> recentColors = new java.util.ArrayDeque<>();

    public NotetakingPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Reinstall image paste support with a larger max width for notes
        try {
            ImagePasteManager.install(
                    contentArea,
                    () -> new File(journalFolder, "attachments"),
                    1200 // larger default width for pasted images
            );
        } catch (Throwable ignored) {}
        // Add richer formatting accelerators on top of base behavior
        installAdvancedFormattingShortcuts();
    }

    private void maybeShowDrawMenu(MouseEvent e, JComponent invoker) {
        if (!(e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3)) return; // right-click or platform popup
        if (drawToolMenu != null && drawToolMenu.isVisible()) return; // prevent re-show on release
        ensureDrawMenu();
        drawToolMenu.show(invoker, 0, invoker.getHeight());
        e.consume();
    }
    
    @Override
    protected boolean supportsMoodControls() {
        return false;
    }

    @Override
    protected boolean supportsClockButton() {
        return false;
    }

    @Override
    protected boolean supportsGuidanceButton() {
        return false;
    }

    // Install Draw toggle + tool chooser into the right toolbar
    @Override
    protected void installExtraRightToolbarButtons(JPanel rightToolbar) {
        // Text color button (Bradley Hand "A" colored by current text color)
        textColorBtn = new main.ui.components.buttons.TextColorButton();
        textColorBtn.setTextColor(currentTextColor);
        textColorBtn.addActionListener(e -> showTextColorPicker());
        rightToolbar.add(textColorBtn);
        rightToolbar.add(Box.createHorizontalStrut(8));
        
        // Text mode selector
        textModeBtn = new main.ui.components.buttons.ToolbarIconButton("select_text");
        textModeBtn.setToolTipText("Text mode");
        textModeBtn.addActionListener(e -> selectTextMode());
        rightToolbar.add(textModeBtn);
        rightToolbar.add(Box.createHorizontalStrut(4));

        // Paint mode selector (Pen)
        paintModeBtn = new main.ui.components.buttons.ToolbarIconButton("pen_tool");
        paintModeBtn.setToolTipText("Pen tool");
        paintModeBtn.addActionListener(e -> { currentDrawTool = DrawTool.PEN; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(paintModeBtn);
        rightToolbar.add(Box.createHorizontalStrut(4));

        // Highlighter tool
        highlighterBtn = new main.ui.components.buttons.ToolbarIconButton("highlighter_tool");
        highlighterBtn.setToolTipText("Highlighter");
        highlighterBtn.addActionListener(e -> { currentDrawTool = DrawTool.HIGHLIGHT; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(highlighterBtn);
        rightToolbar.add(Box.createHorizontalStrut(4));

        // Eraser tool
        eraserBtn = new main.ui.components.buttons.ToolbarIconButton("eraser_tool");
        eraserBtn.setToolTipText("Eraser");
        eraserBtn.addActionListener(e -> { currentDrawTool = DrawTool.ERASER; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(eraserBtn);
        rightToolbar.add(Box.createHorizontalStrut(4));

        // Lasso selection tool
        lassoBtn = new main.ui.components.buttons.ToolbarIconButton("lasso_tool");
        lassoBtn.setToolTipText("Lasso Select (move strokes)");
        lassoBtn.addActionListener(e -> { currentDrawTool = DrawTool.LASSO; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(lassoBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));

        // Stroke width spinner
        strokeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 64, 1));
        try { strokeSpinner.setUI(new main.ui.components.spinner.ModernSpinnerUI()); } catch (Throwable ignored) {}
        strokeSpinner.setPreferredSize(new Dimension(52, 24));
        strokeSpinner.addChangeListener(e -> {
            int v = (int) strokeSpinner.getValue();
            if (currentDrawTool == DrawTool.PEN) penThickness = v;
            else if (currentDrawTool == DrawTool.HIGHLIGHT) highlightThickness = v;
            else if (currentDrawTool == DrawTool.ERASER) eraserRadius = v;
            if (drawingOverlay != null) drawingOverlay.repaint();
        });
        rightToolbar.add(strokeSpinner);
        rightToolbar.add(Box.createHorizontalStrut(6));

        // Color button
        colorBtn = new JButton();
        colorBtn.setPreferredSize(new Dimension(24, 24));
        colorBtn.setFocusPainted(false);
        colorBtn.setOpaque(true);
        colorBtn.setContentAreaFilled(true);
        colorBtn.setBorder(BorderFactory.createLineBorder(new Color(0,0,0,80), 1));
        colorBtn.addActionListener(e -> showSimpleColorPicker());
        rightToolbar.add(colorBtn);

        updatePickersForCurrentTool();
        setEditingMode(EditingMode.TEXT);
    }

    private void selectTextMode() {
        setEditingMode(EditingMode.TEXT);
        main.ui.components.toast.ToastOverlay.info("Text mode");
    }

    private void selectPaintMode() {
        setEditingMode(EditingMode.PAINT);
        String toolName = switch (currentDrawTool) {
            case PEN -> "Pen";
            case HIGHLIGHT -> "Highlighter";
            case ERASER -> "Eraser";
            case LASSO -> "Lasso Select";
        };
        main.ui.components.toast.ToastOverlay.info(toolName + " mode");
    }

    private void setEditingMode(EditingMode mode) {
        this.editingMode = mode;
        boolean paint = (mode == EditingMode.PAINT);
        drawingEnabled = paint;
        if (drawingOverlay != null) {
            drawingOverlay.setOverlayVisible(true); // Always keep overlay visible to show strokes
            drawingOverlay.setCaptureEnabled(paint);
        }
        if (textModeBtn != null) {
            boolean textActive = !paint;
            textModeBtn.setSelected(textActive);
            textModeBtn.setIconOpacity(textActive ? 1f : 0.45f);
        }
        if (paintModeBtn != null) {
            boolean paintActive = paint;
            paintModeBtn.setSelected(paintActive);
            paintModeBtn.setIconOpacity(paintActive ? 1f : 0.45f);
        }
    }

    private enum EditingMode { TEXT, PAINT }

    private void showSimpleColorPicker() {
        if (currentDrawTool == DrawTool.ERASER) return;
        
        Color currentColor = (currentDrawTool == DrawTool.HIGHLIGHT) ? highlightColor : penColor;
        Color picked = main.ui.dialog.utils.SimpleColorPicker.showDialog(this, "Pick Color", 
            new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue()));
        
        if (picked != null) {
            if (currentDrawTool == DrawTool.HIGHLIGHT) {
                highlightColor = new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), highlightColor.getAlpha());
            } else {
                penColor = new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), 255);
            }
            updatePickersForCurrentTool();
        }
    }
    
    private void showTextColorPicker() {
        Color picked = main.ui.dialog.utils.SimpleColorPicker.showDialog(this, "Text Color", currentTextColor);
        if (picked != null) {
            currentTextColor = picked;
            if (textColorBtn != null) {
                textColorBtn.setTextColor(currentTextColor);
            }
            // Apply color to selection or set typing color
            RichTextStyler.applyColor(contentArea, currentTextColor);
        }
    }

    private JPanel buildColorPopupContent() {
        Color base = (currentDrawTool == DrawTool.PEN ? penColor : highlightColor);
        Color baseOpaque = new Color(base.getRed(), base.getGreen(), base.getBlue());

        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(8,10,10,10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(240, currentDrawTool==DrawTool.HIGHLIGHT ? 180 : 150));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); header.setOpaque(false);
        JLabel modeLbl = new JLabel(currentDrawTool == DrawTool.PEN ? "Pen" : "Highlighter");
        JPanel preview = new JPanel();
        preview.setOpaque(true);
        preview.setPreferredSize(new Dimension(34, 16));
        preview.setBorder(BorderFactory.createLineBorder(new Color(0,0,0,60)));
        preview.setBackground(baseOpaque);
        header.add(modeLbl); header.add(preview);
        panel.add(header);
        panel.add(Box.createVerticalStrut(6));

        JPanel recentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); recentRow.setOpaque(false);
        recentRow.add(new JLabel("Recent:"));
        JPanel recentBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); recentBox.setOpaque(false);
        for (Color rc : recentColors) { recentBox.add(makeSwatch(rc, 18, c -> { applyPickedColor(c, false); preview.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue())); rebuildRecentRow(recentBox, preview); })); }
        recentRow.add(recentBox);
        panel.add(recentRow);
        panel.add(Box.createVerticalStrut(6));

        JPanel paletteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); paletteRow.setOpaque(false);
        JPanel grid = new JPanel(new GridLayout(2, 8, 6, 6)); grid.setOpaque(false);
        Color[] palette = new Color[]{
                new Color(20,20,20), new Color(90,90,90), new Color(150,150,150), new Color(220,220,220),
                new Color(244,67,54), new Color(255,152,0), new Color(255,235,59), new Color(255,193,7),
                new Color(76,175,80), new Color(0,150,136), new Color(3,169,244), new Color(33,150,243),
                new Color(63,81,181), new Color(156,39,176), new Color(233,30,99), new Color(121,85,72)
        };
        for (Color c : palette) {
            grid.add(makeSwatch(c, 18, chosen -> { applyPickedColor(chosen, false); preview.setBackground(new Color(chosen.getRed(), chosen.getGreen(), chosen.getBlue())); rebuildRecentRow(recentBox, preview); }));
        }
        paletteRow.add(grid);
        panel.add(paletteRow);
        panel.add(Box.createVerticalStrut(6));

        // Row: Opacity (only for highlighter)
        if (currentDrawTool == DrawTool.HIGHLIGHT) {
            JPanel alphaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); alphaRow.setOpaque(false);
            alphaRow.add(new JLabel("Opacity:"));
            int[] alphas = new int[]{60, 90, 120, 150, 180, 210};
            for (int a : alphas) {
                JButton chip = new JButton(Integer.toString(a));
                chip.setMargin(new Insets(2,6,2,6));
                chip.setFocusPainted(false);
                chip.addActionListener(e -> {
                    Color rgb = new Color(preview.getBackground().getRed(), preview.getBackground().getGreen(), preview.getBackground().getBlue());
                    highlightColor = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), a);
                    updatePickersForCurrentTool();
                });
                alphaRow.add(chip);
            }
            panel.add(alphaRow);
        }

        return panel;
    }

    private void rebuildShades(JPanel shadesBox, Color hueBase, JPanel preview) {
        shadesBox.removeAll();
        float[] hsb = Color.RGBtoHSB(hueBase.getRed(), hueBase.getGreen(), hueBase.getBlue(), null);
        // Generate 8 shades across brightness while keeping saturation high
        for (int i=0;i<8;i++){
            float v = 0.25f + (i*(0.75f/7f));
            Color shade = Color.getHSBColor(hsb[0], 0.9f, v);
            shadesBox.add(makeSwatch(shade, 18, c -> {
                applyPickedColor(c, false);
                preview.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue()));
            }));
        }
        shadesBox.revalidate(); shadesBox.repaint();
    }

    private JButton makeSwatch(Color c, int size, java.util.function.Consumer<Color> onPick){
        JButton sw = new JButton();
        sw.setPreferredSize(new Dimension(size, size));
        sw.setMargin(new Insets(0,0,0,0));
        sw.setBorder(BorderFactory.createLineBorder(new Color(0,0,0,60)));
        sw.setOpaque(true);
        sw.setContentAreaFilled(true);
        sw.setFocusPainted(false);
        sw.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue()));
        sw.addActionListener(e -> onPick.accept(new Color(c.getRed(), c.getGreen(), c.getBlue())));
        return sw;
    }

    private void applyPickedColor(Color rgb, boolean closeAfter){
        if (currentDrawTool == DrawTool.HIGHLIGHT) {
            // Preserve existing alpha
            int a = highlightColor.getAlpha();
            highlightColor = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), a);
        } else if (currentDrawTool == DrawTool.PEN) {
            penColor = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255);
        }
        pushRecentColor(rgb);
        updatePickersForCurrentTool();
        if (closeAfter) {
            try { if (colorWindow != null) colorWindow.setVisible(false); } catch (Throwable ignored) {}
        }
    }

    private void rebuildRecentRow(JPanel recentBox, JPanel preview){
        recentBox.removeAll();
        for (Color rc : recentColors) {
            recentBox.add(makeSwatch(rc, 18, c -> { applyPickedColor(c, false); preview.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue())); rebuildRecentRow(recentBox, preview); }));
        }
        recentBox.revalidate(); recentBox.repaint();
    }

    private void pushRecentColor(Color rgb){
        // Store as opaque RGB, most-recent first, de-duplicate
        int rgbInt = (rgb.getRGB() & 0x00FFFFFF);
        java.util.Iterator<Color> it = recentColors.iterator();
        while(it.hasNext()){
            if ((it.next().getRGB() & 0x00FFFFFF) == rgbInt) { it.remove(); break; }
        }
        recentColors.addFirst(new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
        while(recentColors.size() > 8) recentColors.removeLast();
    }

    private void ensureDrawMenu() {
        if (drawToolMenu == null) {
            drawToolMenu = new JPopupMenu();
            try { drawToolMenu.setLightWeightPopupEnabled(false); } catch (Throwable ignored) { try { JPopupMenu.setDefaultLightWeightPopupEnabled(false); } catch (Throwable ignored2) {} }
        }
        drawToolMenu.removeAll();
        JRadioButtonMenuItem pen = new JRadioButtonMenuItem("Pen", currentDrawTool == DrawTool.PEN);
        JRadioButtonMenuItem hi = new JRadioButtonMenuItem("Highlighter", currentDrawTool == DrawTool.HIGHLIGHT);
        JRadioButtonMenuItem er = new JRadioButtonMenuItem("Eraser", currentDrawTool == DrawTool.ERASER);
        pen.setActionCommand("pen"); hi.setActionCommand("highlighter"); er.setActionCommand("eraser");
        ButtonGroup g = new ButtonGroup(); g.add(pen); g.add(hi); g.add(er);
        ActionListener act = e -> {
            String cmd = e.getActionCommand();
            if ("pen".equals(cmd)) {
                currentDrawTool = DrawTool.PEN;
                pen.setSelected(true); hi.setSelected(false); er.setSelected(false);
            } else if ("highlighter".equals(cmd)) {
                currentDrawTool = DrawTool.HIGHLIGHT;
                pen.setSelected(false); hi.setSelected(true); er.setSelected(false);
            } else {
                currentDrawTool = DrawTool.ERASER;
                pen.setSelected(false); hi.setSelected(false); er.setSelected(true);
            }
            updatePickersForCurrentTool();
            // Close after selection to avoid event re-entry quirks
            try { drawToolMenu.setVisible(false); } catch (Throwable ignored) {}
        };
        pen.addActionListener(act); hi.addActionListener(act); er.addActionListener(act);
        java.awt.event.MouseAdapter immediate = new java.awt.event.MouseAdapter(){
            @Override public void mousePressed(java.awt.event.MouseEvent e){
                Object src = e.getSource();
                if (src == pen) pen.doClick(0);
                else if (src == hi) hi.doClick(0);
                else if (src == er) er.doClick(0);
            }
        };
        pen.addMouseListener(immediate); hi.addMouseListener(immediate); er.addMouseListener(immediate);
        drawToolMenu.add(pen); drawToolMenu.add(hi); drawToolMenu.add(er);
    }

    // Overlay above the text scrollPane that scrolls in sync with content
    @Override
    protected void installContentOverlay(JComponent textWrapper, JScrollPane scrollPane) {
        try { textWrapper.remove(scrollPane); } catch (Throwable ignored) {}
        overlayStack = new JLayeredPane() {
            @Override public Dimension getPreferredSize() { return scrollPane.getPreferredSize(); }
            @Override public void doLayout() {
                for (Component c : getComponents()) { c.setBounds(0, 0, getWidth(), getHeight()); }
            }
        };
        drawingOverlay = new DrawingOverlay(scrollPane, contentArea);
        overlayStack.add(scrollPane, Integer.valueOf(0));
        overlayStack.add(drawingOverlay, Integer.valueOf(100));
        textWrapper.add(overlayStack, BorderLayout.CENTER);
        drawingOverlay.setOverlayVisible(overlayVisible);
        drawingOverlay.setCaptureEnabled(drawingEnabled);
    }

    private void installAdvancedFormattingShortcuts() {
        int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = contentArea.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap am = contentArea.getActionMap();

        im.put(KeyStroke.getKeyStroke('B', meta), "ntk-bold");
        am.put("ntk-bold", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionBold(contentArea);
                else
                    RichTextStyler.setTypingBold(contentArea, !RichTextStyler.getTypingState(contentArea).bold());
            }
        });
        im.put(KeyStroke.getKeyStroke('I', meta), "ntk-italic");
        am.put("ntk-italic", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionItalic(contentArea);
                else
                    RichTextStyler.setTypingItalic(contentArea, !RichTextStyler.getTypingState(contentArea).italic());
            }
        });
        im.put(KeyStroke.getKeyStroke('U', meta), "ntk-underline");
        am.put("ntk-underline", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionUnderline(contentArea);
                else
                    RichTextStyler.setTypingUnderline(contentArea, !RichTextStyler.getTypingState(contentArea).underline());
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "ntk-strike");
        am.put("ntk-strike", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionStrike(contentArea);
                else
                    RichTextStyler.setTypingStrike(contentArea, !RichTextStyler.getTypingState(contentArea).strike());
            }
        });

        // Headings: Cmd/Ctrl+1/2/3 and +0 to reset
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, meta), "ntk-h1");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, meta), "ntk-h2");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, meta), "ntk-h3");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, meta), "ntk-p");
        am.put("ntk-h1", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(22f, true); }});
        am.put("ntk-h2", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(18f, true); }});
        am.put("ntk-h3", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(16f, true); }});
        am.put("ntk-p",  new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(14f, false); }});
    }

    private void applyFontSizeToSelection(float size, boolean bold) {
        try {
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) {
                // typing attributes
                MutableAttributeSet attrs = new SimpleAttributeSet(((javax.swing.text.StyledEditorKit) contentArea.getEditorKit()).getInputAttributes());
                StyleConstants.setFontSize(attrs, Math.round(size));
                StyleConstants.setBold(attrs, bold);
                contentArea.setCharacterAttributes(attrs, true);
                return;
            }
            StyledDocument doc = contentArea.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontSize(attrs, Math.round(size));
            StyleConstants.setBold(attrs, bold);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Throwable ignored) {}
    }

    @Override
    public String fileExtension() {
        return ".ntk";
    }

    // --- Persistence: save/load sidecar for drawings ---
    @Override
    protected void saveEntry() {
        super.saveEntry();
        try { saveDrawingSidecar(currentFile); } catch (Throwable ignored) {}
    }

    @Override
    protected void safeLoadFile(File f) {
        super.safeLoadFile(f);
        try { loadDrawingSidecar(f); } catch (Throwable ignored) {}
        if (drawingOverlay != null) drawingOverlay.repaint();
    }

    private void saveDrawingSidecar(File mainFile) {
        if (mainFile == null) return;
        File side = new File(mainFile.getAbsolutePath() + ".draw");
        try (PrintWriter out = new PrintWriter(new FileWriter(side))) {
            out.println("DRAWV1");
            for (DrawStroke s : drawStrokes) {
                Color c = s.color;
                String pts = toPointsCsv(s.points);
                out.println("S|" + (s.tool==DrawTool.PEN?"pen":"hl") + "|" + c.getRed()+","+c.getGreen()+","+c.getBlue()+","+c.getAlpha() + "|" + s.thickness + "|" + pts);
            }
        } catch (IOException ignored) { }
    }

    private void loadDrawingSidecar(File mainFile) {
        drawStrokes.clear();
        if (mainFile == null) return;
        File side = new File(mainFile.getAbsolutePath() + ".draw");
        if (!side.exists()) return;
        try (BufferedReader in = new BufferedReader(new FileReader(side))) {
            String first = in.readLine();
            if (first == null || !first.startsWith("DRAWV1")) return;
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("S|")) continue;
                String[] parts = line.split("\\|");
                if (parts.length < 5) continue;
                DrawTool tool = "pen".equals(parts[1]) ? DrawTool.PEN : DrawTool.HIGHLIGHT;
                String[] rgba = parts[2].split(",");
                int r = Integer.parseInt(rgba[0]);
                int g = Integer.parseInt(rgba[1]);
                int b = Integer.parseInt(rgba[2]);
                int a = Integer.parseInt(rgba[3]);
                int thick = Integer.parseInt(parts[3]);
                List<Point> pts = fromPointsCsv(parts[4]);
                drawStrokes.add(new DrawStroke(tool, new Color(r,g,b,a), thick, pts));
            }
        } catch (Exception ignored) {}
    }

    private static String toPointsCsv(List<Point> pts) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<pts.size();i++) {
            Point p = pts.get(i);
            if (i>0) sb.append(';');
            sb.append(p.x).append(',').append(p.y);
        }
        return sb.toString();
    }
    private static List<Point> fromPointsCsv(String csv) {
        List<Point> pts = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return pts;
        String[] tokens = csv.split(";");
        for (String t : tokens) {
            String[] xy = t.split(",");
            if (xy.length==2) {
                try { pts.add(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]))); } catch (NumberFormatException ignore) {}
            }
        }
        return pts;
    }

    // --- Export helpers and tool UI sync ---
    private void updatePickersForCurrentTool() {
        if (strokeSpinner == null || colorBtn == null) return;
        
        // Update tool button selection states
        if (paintModeBtn != null) {
            paintModeBtn.setSelected(currentDrawTool == DrawTool.PEN);
            paintModeBtn.setIconOpacity(currentDrawTool == DrawTool.PEN ? 1f : 0.6f);
        }
        if (highlighterBtn != null) {
            highlighterBtn.setSelected(currentDrawTool == DrawTool.HIGHLIGHT);
            highlighterBtn.setIconOpacity(currentDrawTool == DrawTool.HIGHLIGHT ? 1f : 0.6f);
        }
        if (eraserBtn != null) {
            eraserBtn.setSelected(currentDrawTool == DrawTool.ERASER);
            eraserBtn.setIconOpacity(currentDrawTool == DrawTool.ERASER ? 1f : 0.6f);
        }
        if (lassoBtn != null) {
            lassoBtn.setSelected(currentDrawTool == DrawTool.LASSO);
            lassoBtn.setIconOpacity(currentDrawTool == DrawTool.LASSO ? 1f : 0.6f);
        }
        
        // Update spinner and color button for current tool
        if (currentDrawTool == DrawTool.PEN) {
            strokeSpinner.setValue(penThickness);
            strokeSpinner.setEnabled(true);
            colorBtn.setBackground(new Color(penColor.getRed(), penColor.getGreen(), penColor.getBlue()));
            colorBtn.setEnabled(true);
        } else if (currentDrawTool == DrawTool.HIGHLIGHT) {
            strokeSpinner.setValue(highlightThickness);
            strokeSpinner.setEnabled(true);
            colorBtn.setBackground(new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue()));
            colorBtn.setEnabled(true);
        } else if (currentDrawTool == DrawTool.ERASER) {
            strokeSpinner.setValue(eraserRadius);
            strokeSpinner.setEnabled(true);
            colorBtn.setEnabled(false);
        } else if (currentDrawTool == DrawTool.LASSO) {
            // Lasso tool doesn't use stroke width or color
            strokeSpinner.setEnabled(false);
            colorBtn.setEnabled(false);
        }
        colorBtn.repaint();
    }

    private java.awt.image.BufferedImage renderSnapshotImage() {
        try {
            final int[] wh = new int[2];
            Runnable measure = () -> {
                int w = contentArea.getWidth();
                if (w <= 0) w = 900;
                int h = Math.max(1, contentArea.getPreferredSize().height);
                wh[0] = w; wh[1] = h;
            };
            if (SwingUtilities.isEventDispatchThread()) measure.run(); else SwingUtilities.invokeAndWait(measure);
            int width = wh[0], height = wh[1];
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0,0,width,height);
            Runnable print = () -> {
                Dimension old = contentArea.getSize();
                contentArea.setSize(width, height);
                contentArea.doLayout();
                contentArea.printAll(g2);
                contentArea.setSize(old);
                contentArea.doLayout();
            };
            if (SwingUtilities.isEventDispatchThread()) print.run(); else SwingUtilities.invokeAndWait(print);
            for (DrawStroke s : drawStrokes) {
                g2.setColor(s.color);
                g2.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Float();
                for (int i=0;i<s.points.size();i++) {
                    Point p = s.points.get(i);
                    if (i==0) path.moveTo(p.x, p.y); else path.lineTo(p.x, p.y);
                }
                g2.draw(path);
            }
            g2.dispose();
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    private void exportSnapshotToImage() {
        java.awt.image.BufferedImage img = renderSnapshotImage();
        if (img == null) return;
        String base = (titleField != null && titleField.getText() != null && !titleField.getText().isBlank()) ? titleField.getText().trim().replaceAll("[^a-zA-Z0-9-_]+","_") : "note";
        SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Export Snapshot");
        chooser.setMode(SimjotFileChooser.Mode.SAVE);
        if (currentFile != null && currentFile.getParentFile() != null) {
            chooser.setCurrentDirectory(currentFile.getParentFile());
        }
        chooser.setSuggestedFileName(base + "_snapshot.png");
        File out = chooser.showDialog();
        if (out != null) {
            try { javax.imageio.ImageIO.write(img, "png", out); } catch (IOException ignored) {}
        }
    }

    private void exportSnapshotToPdf() {
        java.awt.image.BufferedImage img = renderSnapshotImage();
        if (img == null) return;
        String base = (titleField != null && titleField.getText() != null && !titleField.getText().isBlank()) ? titleField.getText().trim().replaceAll("[^a-zA-Z0-9-_]+","_") : "note";
        SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Export PDF");
        chooser.setMode(SimjotFileChooser.Mode.SAVE);
        if (currentFile != null && currentFile.getParentFile() != null) {
            chooser.setCurrentDirectory(currentFile.getParentFile());
        }
        chooser.setSuggestedFileName(base + "_snapshot.pdf");
        File out = chooser.showDialog();
        if (out != null) {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.common.PDRectangle rect = new org.apache.pdfbox.pdmodel.common.PDRectangle(img.getWidth(), img.getHeight());
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(rect);
                doc.addPage(page);
                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImg = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImg, 0, 0, rect.getWidth(), rect.getHeight());
                }
                doc.save(out);
            } catch (Throwable ignored) {}
        }
    }

    // --- Drawing model & overlay ---
    private enum DrawTool { PEN, HIGHLIGHT, ERASER, LASSO }
    
    // Enhanced stroke with float precision and timestamps for velocity-based rendering
    private static class DrawStroke {
        final DrawTool tool;
        final Color color;
        final int thickness;
        final List<Point> points; // Legacy integer points for persistence
        final List<float[]> floatPoints; // [x, y, timestamp] for native engine
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private boolean boundsDirty = true;
        
        // Cached arrays for performance (invalidated on point add)
        private float[] cachedXs;
        private float[] cachedYs;
        private float[] cachedThicknesses;
        private boolean cacheValid = false;
        
        DrawStroke(DrawTool tool, Color color, int thickness) {
            this(tool, color, thickness, new ArrayList<>());
        }
        
        DrawStroke(DrawTool tool, Color color, int thickness, List<Point> pts) {
            this.tool = tool;
            this.color = color;
            this.thickness = thickness;
            this.points = pts;
            this.floatPoints = new ArrayList<>();
            // Convert existing points to float format
            for (Point p : pts) {
                floatPoints.add(new float[]{p.x, p.y, System.currentTimeMillis()});
                updateBounds(p.x, p.y);
            }
            if (pts.isEmpty()) {
                boundsDirty = true;
            }
        }
        
        void addPoint(float x, float y, long timestamp) {
            points.add(new Point(Math.round(x), Math.round(y)));
            floatPoints.add(new float[]{x, y, timestamp});
            updateBounds(Math.round(x), Math.round(y));
            cacheValid = false; // Invalidate cache
        }
        
        void invalidateCache() {
            cacheValid = false;
        }
        
        float[] getPointsX() {
            if (!cacheValid || cachedXs == null || cachedXs.length != floatPoints.size()) {
                rebuildCache();
            }
            return cachedXs;
        }
        
        float[] getPointsY() {
            if (!cacheValid || cachedYs == null || cachedYs.length != floatPoints.size()) {
                rebuildCache();
            }
            return cachedYs;
        }
        
        float[] getCachedThicknesses() {
            return cachedThicknesses;
        }
        
        void setCachedThicknesses(float[] thicknesses) {
            this.cachedThicknesses = thicknesses;
        }
        
        private void updateBounds(int x, int y) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            boundsDirty = false;
        }
        
        void recomputeBounds() {
            if (points.isEmpty()) {
                minX = minY = Integer.MAX_VALUE;
                maxX = maxY = Integer.MIN_VALUE;
                boundsDirty = true;
                return;
            }
            minX = minY = Integer.MAX_VALUE;
            maxX = maxY = Integer.MIN_VALUE;
            for (Point p : points) {
                updateBounds(p.x, p.y);
            }
            boundsDirty = false;
        }
        
        void shiftBounds(int dx, int dy) {
            if (boundsDirty) return;
            minX += dx;
            maxX += dx;
            minY += dy;
            maxY += dy;
        }
        
        private java.awt.Rectangle getBoundsWithPadding(int pad) {
            if (boundsDirty) {
                recomputeBounds();
            }
            if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE) {
                return new java.awt.Rectangle();
            }
            int width = (maxX - minX) + pad * 2;
            int height = (maxY - minY) + pad * 2;
            return new java.awt.Rectangle(minX - pad, minY - pad, Math.max(1, width), Math.max(1, height));
        }
        
        boolean intersectsRect(java.awt.Rectangle rect, int pad) {
            if (points.isEmpty()) return false;
            java.awt.Rectangle bounds = getBoundsWithPadding(pad);
            return bounds.intersects(rect);
        }

        void addLegacyPoint(Point p) {
            points.add(p);
            updateBounds(p.x, p.y);
            cacheValid = false;
        }
        
        private void rebuildCache() {
            int n = floatPoints.size();
            cachedXs = new float[n];
            cachedYs = new float[n];
            for (int i = 0; i < n; i++) {
                float[] p = floatPoints.get(i);
                cachedXs[i] = p[0];
                cachedYs[i] = p[1];
            }
            cacheValid = true;
        }
        
        long[] getTimestamps() {
            long[] ts = new long[floatPoints.size()];
            for (int i = 0; i < floatPoints.size(); i++) ts[i] = (long) floatPoints.get(i)[2];
            return ts;
        }
    }
    
    // Undo action wrapper for stroke operations
    private sealed interface UndoAction permits AddStrokeAction, EraseStrokesAction {}
    private record AddStrokeAction(DrawStroke stroke) implements UndoAction {}
    private record EraseStrokesAction(List<DrawStroke> erased) implements UndoAction {}
    
    private void performUndo() {
        if (undoStack.isEmpty()) return;
        UndoAction action = undoStack.remove(undoStack.size() - 1);
        switch (action) {
            case AddStrokeAction a -> {
                drawStrokes.remove(a.stroke());
                redoStack.add(action);
            }
            case EraseStrokesAction a -> {
                drawStrokes.addAll(a.erased());
                redoStack.add(action);
            }
        }
        if (drawingOverlay != null) drawingOverlay.repaint();
    }
    
    private void performRedo() {
        if (redoStack.isEmpty()) return;
        UndoAction action = redoStack.remove(redoStack.size() - 1);
        switch (action) {
            case AddStrokeAction a -> {
                drawStrokes.add(a.stroke());
                undoStack.add(action);
            }
            case EraseStrokesAction a -> {
                drawStrokes.removeAll(a.erased());
                undoStack.add(action);
            }
        }
        if (drawingOverlay != null) drawingOverlay.repaint();
    }

    private class DrawingOverlay extends JComponent {
        private final JViewport vp;
        private final JComponent view;
        private boolean capture = false; // whether to intercept mouse events
        private DrawStroke current;
        private Point lastPoint; // For incremental drawing
        private Point mousePos; // Current mouse position (screen coords) for eraser cursor
        
        // Native stroke engine
        private main.infrastructure.ffi.NativeDrawing nativeDrawing;
        private long nativeStrokeManager = 0;
        private int currentNativeStrokeIdx = -1;
        
        // Stroke buffer caching for performance
        private java.awt.image.BufferedImage penStrokeBuffer;
        private java.awt.image.BufferedImage highlightStrokeBuffer;
        private boolean strokeBufferDirty = true;
        private boolean highlightBufferDirty = true;
        private int lastBufferWidth = 0, lastBufferHeight = 0;
        private float lastOffsetX = 0, lastOffsetY = 0;
        
        // Optimizer for large stroke collections (quadtree spatial index)
        private long optimizerHandle = 0;
        private java.util.Map<DrawStroke, Integer> strokeToOptId = new java.util.HashMap<>();
        private static final int OPTIMIZER_THRESHOLD = 100; // Enable when stroke count exceeds this
        private boolean useOptimizer = false;
        
        // Float-precision smoothing state (keeps sub-pixel precision)
        private float smoothX, smoothY;
        private float lastRawX, lastRawY;
        private long lastTimestamp;
        private static final float SMOOTH_ALPHA = 0.6f; // Responsive but smooth
        private static final float MIN_DISTANCE_SQ = 4.0f; // 2px minimum distance
        
        // Lasso selection state
        private List<Point> lassoPath = new ArrayList<>();
        private java.util.Set<DrawStroke> selectedStrokes = new java.util.HashSet<>();
        private java.awt.Rectangle selectionBounds = null;
        private boolean isDraggingSelection = false;
        private Point dragStart = null;
        
        DrawingOverlay(JScrollPane host, JComponent view) {
            this.vp = host.getViewport();
            this.view = view;
            setOpaque(false);
            
            // Initialize native stroke engine if available
            try {
                nativeDrawing = main.infrastructure.ffi.NativeDrawing.getInstance();
                if (nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable()) {
                    nativeStrokeManager = nativeDrawing.strokeManagerCreate(4096, 4096);
                }
            } catch (Throwable ignored) {
                nativeDrawing = null;
            }
            
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!capture || !SwingUtilities.isLeftMouseButton(e)) return;
                    Point p = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(p); return; }
                    
                    // Lasso tool handling
                    if (currentDrawTool == DrawTool.LASSO) {
                        handleLassoPress(p);
                        return;
                    }
                    
                    long now = System.currentTimeMillis();
                    redoStack.clear();
                    current = makeStroke();
                    
                    if (currentDrawTool == DrawTool.HIGHLIGHT) {
                        // Simple raw point for highlighter - no advanced processing
                        current.addLegacyPoint(p);
                    } else {
                        // Initialize smoothing state with float precision for pen
                        smoothX = p.x;
                        smoothY = p.y;
                        lastRawX = p.x;
                        lastRawY = p.y;
                        lastTimestamp = now;
                        current.addPoint(smoothX, smoothY, now);
                    }
                    
                    lastPoint = p;
                    drawStrokes.add(current);
                    undoStack.add(new AddStrokeAction(current));
                    strokeBufferDirty = true;
                    highlightBufferDirty = true;
                    repaintDirty(p, p, current.thickness);
                }
                
                @Override public void mouseDragged(MouseEvent e) {
                    if (!capture) return;
                    updateEraserCursor(e.getPoint());
                    Point raw = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(raw); return; }
                    if (currentDrawTool == DrawTool.LASSO) { handleLassoDrag(raw); return; }
                    if (current == null) return;
                    
                    if (currentDrawTool == DrawTool.HIGHLIGHT) {
                        // Simple raw point for highlighter - no smoothing, no sampling
                        Point prev = lastPoint;
                        current.addLegacyPoint(raw);
                        lastPoint = raw;
                        highlightBufferDirty = true;
                        repaintDirty(prev, raw, current.thickness);
                        return;
                    }
                    
                    // PEN tool: advanced processing with smoothing and distance sampling
                    long now = System.currentTimeMillis();
                    float rawX = raw.x;
                    float rawY = raw.y;
                    
                    // Distance-based sampling: skip points too close together
                    float dx = rawX - lastRawX;
                    float dy = rawY - lastRawY;
                    if (dx * dx + dy * dy < MIN_DISTANCE_SQ) {
                        return; // Too close, skip to reduce jitter
                    }
                    
                    // Apply EMA smoothing with float precision (no rounding during stroke)
                    smoothX = SMOOTH_ALPHA * rawX + (1f - SMOOTH_ALPHA) * smoothX;
                    smoothY = SMOOTH_ALPHA * rawY + (1f - SMOOTH_ALPHA) * smoothY;
                    
                    Point prev = lastPoint;
                    current.addPoint(smoothX, smoothY, now);
                    lastPoint = new Point(Math.round(smoothX), Math.round(smoothY));
                    lastRawX = rawX;
                    lastRawY = rawY;
                    lastTimestamp = now;
                    // Incrementally render the new segment to avoid full buffer rebuilds on every drag
                    appendPenSegment(current, prev, lastPoint);
                    repaintDirty(prev, lastPoint, current.thickness);
                }
                
                @Override public void mouseMoved(MouseEvent e) {
                    updateEraserCursor(e.getPoint());
                }
                
                @Override public void mouseReleased(MouseEvent e) {
                    if (currentDrawTool == DrawTool.LASSO) {
                        handleLassoRelease();
                        return;
                    }
                    if (current != null && current.floatPoints.size() >= 3 && currentDrawTool == DrawTool.PEN) {
                        // Apply Chaikin smoothing on stroke completion for extra polish
                        applyFinalSmoothing(current);
                    }
                    // Add completed stroke to optimizer if active
                    if (current != null && useOptimizer) {
                        addStrokeToOptimizer(current);
                    }
                    current = null;
                    lastPoint = null;
                    strokeBufferDirty = true;
                    highlightBufferDirty = true;
                    // Check if we should enable/disable optimizer
                    checkOptimizerThreshold();
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            vp.addChangeListener(e -> { strokeBufferDirty = true; highlightBufferDirty = true; repaint(); });
            
            // Keyboard shortcuts for undo/redo
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, meta), "undoStroke");
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "redoStroke");
            getActionMap().put("undoStroke", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (capture) { performUndo(); strokeBufferDirty = true; highlightBufferDirty = true; } }});
            getActionMap().put("redoStroke", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (capture) { performRedo(); strokeBufferDirty = true; highlightBufferDirty = true; } }});
            view.addComponentListener(new ComponentAdapter(){
                @Override public void componentResized(ComponentEvent e) { strokeBufferDirty = true; highlightBufferDirty = true; revalidate(); repaint(); }
            });
        }
        
        private void applyFinalSmoothing(DrawStroke stroke) {
            if (nativeDrawing == null || stroke.floatPoints.size() < 3) return;
            try {
                float[] xs = stroke.getPointsX();
                float[] ys = stroke.getPointsY();
                float[][] smoothed = nativeDrawing.strokeSmoothChaikin(xs, ys, 2);
                if (smoothed != null && smoothed[0].length > 0) {
                    // Replace points with smoothed version
                    stroke.points.clear();
                    stroke.floatPoints.clear();
                    long ts = System.currentTimeMillis();
                    for (int i = 0; i < smoothed[0].length; i++) {
                        stroke.addPoint(smoothed[0][i], smoothed[1][i], ts);
                    }
                }
            } catch (Throwable ignored) {}
        }
        
        private void repaintDirty(Point p1, Point p2, int thickness) {
            Point vpPos = vp.getViewPosition();
            int pad = thickness + 4;
            int x1 = Math.min(p1.x, p2.x) - vpPos.x - pad;
            int y1 = Math.min(p1.y, p2.y) - vpPos.y - pad;
            int x2 = Math.max(p1.x, p2.x) - vpPos.x + pad;
            int y2 = Math.max(p1.y, p2.y) - vpPos.y + pad;
            repaint(x1, y1, x2 - x1, y2 - y1);
        }

        /**
         * Append a newly drawn pen segment directly to the cached pen buffer to avoid
         * forcing a full buffer rebuild on every drag event.
         */
        private void appendPenSegment(DrawStroke stroke, Point p1, Point p2) {
            if (stroke == null || stroke.tool != DrawTool.PEN) return;
            if (strokeBufferDirty || penStrokeBuffer == null) {
                // Will be rebuilt on next paint; avoid drawing with stale offsets
                return;
            }
            try {
                Point vpPos = vp.getViewPosition();
                Graphics2D g2 = penStrokeBuffer.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(-vpPos.x, -vpPos.y);
                g2.setColor(stroke.color);
                g2.setStroke(new BasicStroke(stroke.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                g2.dispose();
            } catch (Throwable ignored) {
                // If anything goes wrong, fall back to a full rebuild on next paint
                strokeBufferDirty = true;
            }
        }
        
        private void updateEraserCursor(Point screenPos) {
            if (currentDrawTool != DrawTool.ERASER) return;
            Point oldPos = mousePos;
            mousePos = screenPos;
            // Repaint old and new cursor areas
            int r = eraserRadius + 2;
            if (oldPos != null) repaint(oldPos.x - r, oldPos.y - r, r * 2, r * 2);
            repaint(screenPos.x - r, screenPos.y - r, r * 2, r * 2);
        }
        
        void setOverlayVisible(boolean on) {
            setVisible(on);
            repaint();
        }
        void setCaptureEnabled(boolean on) {
            this.capture = on;
            setCursor(on ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
            revalidate();
            repaint();
        }
        @Override public boolean contains(int x, int y) {
            // When not capturing, pretend we are not under the mouse so the text editor receives events
            return capture && super.contains(x, y);
        }
        private Point toDoc(Point p) { Point vpPos = vp.getViewPosition(); return new Point(p.x + vpPos.x, p.y + vpPos.y); }
        private DrawStroke makeStroke() {
            if (currentDrawTool == DrawTool.HIGHLIGHT) {
                return new DrawStroke(DrawTool.HIGHLIGHT, highlightColor, highlightThickness);
            }
            return new DrawStroke(DrawTool.PEN, penColor, penThickness);
        }
        private void eraseAt(Point p) {
            int r = eraserRadius;
            int r2 = r * r;
            List<DrawStroke> erased = new ArrayList<>();
            float px = p.x, py = p.y;
            
            // Check all strokes directly
            for (DrawStroke s : new ArrayList<>(drawStrokes)) {
                boolean shouldErase = false;
                
                // For pen strokes, check floatPoints (what's actually rendered)
                // For highlighter, check integer points
                if (s.tool == DrawTool.PEN && !s.floatPoints.isEmpty()) {
                    List<float[]> fpts = s.floatPoints;
                    if (fpts.size() == 1) {
                        float[] pt = fpts.get(0);
                        float dx = px - pt[0], dy = py - pt[1];
                        shouldErase = (dx * dx + dy * dy <= r2);
                    } else {
                        for (int i = 1; i < fpts.size(); i++) {
                            float[] a = fpts.get(i - 1);
                            float[] b = fpts.get(i);
                            if (distPointToSegmentSqF(px, py, a[0], a[1], b[0], b[1]) <= r2) {
                                shouldErase = true;
                                break;
                            }
                        }
                    }
                } else {
                    // Highlighter or fallback - use integer points
                    List<Point> pts = s.points;
                    if (pts.isEmpty()) continue;
                    if (pts.size() == 1) {
                        Point pt = pts.get(0);
                        int dx = p.x - pt.x, dy = p.y - pt.y;
                        shouldErase = (dx * dx + dy * dy <= r2);
                    } else {
                        for (int i = 1; i < pts.size(); i++) {
                            if (distPointToSegmentSq(p, pts.get(i - 1), pts.get(i)) <= r2) {
                                shouldErase = true;
                                break;
                            }
                        }
                    }
                }
                
                if (shouldErase) {
                    drawStrokes.remove(s);
                    removeStrokeFromOptimizer(s);
                    erased.add(s);
                }
            }
            
            if (!erased.isEmpty()) {
                redoStack.clear();
                undoStack.add(new EraseStrokesAction(erased));
                checkOptimizerThreshold();
                strokeBufferDirty = true;
                highlightBufferDirty = true;
                repaint();
            }
        }
        
        // Float version of point-to-segment distance
        private float distPointToSegmentSqF(float px, float py, float ax, float ay, float bx, float by) {
            float vx = bx - ax, vy = by - ay;
            float wx = px - ax, wy = py - ay;
            float c1 = vx * wx + vy * wy;
            if (c1 <= 0) return (px - ax) * (px - ax) + (py - ay) * (py - ay);
            float c2 = vx * vx + vy * vy;
            if (c2 <= c1) return (px - bx) * (px - bx) + (py - by) * (py - by);
            float t = c1 / c2;
            float projx = ax + t * vx, projy = ay + t * vy;
            float dx = px - projx, dy = py - projy;
            return dx * dx + dy * dy;
        }
        private int distPointToSegmentSq(Point p, Point a, Point b) {
            int vx = b.x - a.x, vy = b.y - a.y;
            int wx = p.x - a.x, wy = p.y - a.y;
            int c1 = vx * wx + vy * wy;
            if (c1 <= 0) return (p.x - a.x) * (p.x - a.x) + (p.y - a.y) * (p.y - a.y);
            int c2 = vx * vx + vy * vy;
            if (c2 <= c1) return (p.x - b.x) * (p.x - b.x) + (p.y - b.y) * (p.y - b.y);
            double t = c1 / (double) c2;
            double projx = a.x + t * vx, projy = a.y + t * vy;
            double dx = p.x - projx, dy = p.y - projy;
            return (int) Math.round(dx * dx + dy * dy);
        }
        
        // ═══════════════════════════════════════════════════════════════════════════
        // OPTIMIZER MANAGEMENT (for large stroke collections)
        // ═══════════════════════════════════════════════════════════════════════════
        
        private void checkOptimizerThreshold() {
            if (nativeDrawing == null || !nativeDrawing.isOptimizerAvailable()) return;
            
            int count = drawStrokes.size();
            if (!useOptimizer && count > OPTIMIZER_THRESHOLD) {
                // Enable optimizer - migrate all strokes
                enableOptimizer();
            } else if (useOptimizer && count < OPTIMIZER_THRESHOLD / 2) {
                // Disable optimizer when stroke count drops significantly
                disableOptimizer();
            }
        }
        
        private void enableOptimizer() {
            if (optimizerHandle != 0) return;
            optimizerHandle = nativeDrawing.optimizerCreate(8192, 8192);
            if (optimizerHandle == 0) return;
            
            // Add all existing strokes to optimizer
            for (DrawStroke s : drawStrokes) {
                addStrokeToOptimizer(s);
            }
            useOptimizer = true;
        }
        
        private void disableOptimizer() {
            if (optimizerHandle == 0) return;
            nativeDrawing.optimizerDestroy(optimizerHandle);
            optimizerHandle = 0;
            strokeToOptId.clear();
            useOptimizer = false;
        }
        
        private void addStrokeToOptimizer(DrawStroke s) {
            if (optimizerHandle == 0 || nativeDrawing == null) return;
            float[] xs, ys;
            if (s.tool == DrawTool.HIGHLIGHT) {
                xs = new float[s.points.size()];
                ys = new float[s.points.size()];
                for (int i = 0; i < s.points.size(); i++) {
                    xs[i] = s.points.get(i).x;
                    ys[i] = s.points.get(i).y;
                }
            } else {
                xs = s.getPointsX();
                ys = s.getPointsY();
            }
            int optId = nativeDrawing.optimizerAddStroke(optimizerHandle, xs, ys, null,
                                                          s.color.getRGB(), s.thickness);
            if (optId >= 0) {
                strokeToOptId.put(s, optId);
            }
        }
        
        private void removeStrokeFromOptimizer(DrawStroke s) {
            if (optimizerHandle == 0 || nativeDrawing == null) return;
            Integer optId = strokeToOptId.remove(s);
            if (optId != null) {
                nativeDrawing.optimizerRemoveStroke(optimizerHandle, optId);
            }
        }
        
        private void moveStrokeInOptimizer(DrawStroke s, int dx, int dy) {
            if (optimizerHandle == 0 || nativeDrawing == null) return;
            Integer optId = strokeToOptId.get(s);
            if (optId != null) {
                nativeDrawing.optimizerMoveStroke(optimizerHandle, optId, dx, dy);
            }
        }
        
        // Fast eraser hit test using optimizer's spatial index
        private List<DrawStroke> queryStrokesNearPoint(Point p, int radius) {
            List<DrawStroke> result = new ArrayList<>();
            if (useOptimizer && optimizerHandle != 0 && nativeDrawing != null) {
                int[] nearbyIds = nativeDrawing.optimizerQueryPoint(optimizerHandle, p.x, p.y, radius);
                // Map optimizer IDs back to DrawStroke objects
                for (int optId : nearbyIds) {
                    for (java.util.Map.Entry<DrawStroke, Integer> e : strokeToOptId.entrySet()) {
                        if (e.getValue() == optId) {
                            result.add(e.getKey());
                            break;
                        }
                    }
                }
            } else {
                // Fallback: return all strokes (original behavior)
                result.addAll(drawStrokes);
            }
            return result;
        }
        
        // ═══════════════════════════════════════════════════════════════════════════
        // LASSO SELECTION
        // ═══════════════════════════════════════════════════════════════════════════
        
        private void handleLassoPress(Point p) {
            // Check if clicking inside existing selection to drag
            if (selectionBounds != null && selectionBounds.contains(p)) {
                isDraggingSelection = true;
                dragStart = p;
            } else {
                // Start new lasso path
                clearSelection();
                lassoPath.clear();
                lassoPath.add(p);
                isDraggingSelection = false;
            }
            repaint();
        }
        
        private void handleLassoDrag(Point p) {
            if (isDraggingSelection && dragStart != null) {
                // Move selected strokes
                int dx = p.x - dragStart.x;
                int dy = p.y - dragStart.y;
                moveSelectedStrokes(dx, dy);
                dragStart = p;
                strokeBufferDirty = true;
                highlightBufferDirty = true;
                repaint();
            } else {
                // Continue drawing lasso path
                lassoPath.add(p);
                repaint();
            }
        }
        
        private void handleLassoRelease() {
            if (isDraggingSelection) {
                isDraggingSelection = false;
                dragStart = null;
            } else if (lassoPath.size() >= 3) {
                // Complete lasso and select strokes
                selectStrokesInLasso();
                lassoPath.clear();
            }
            repaint();
        }
        
        private void selectStrokesInLasso() {
            selectedStrokes.clear();
            float[] lassoX = new float[lassoPath.size()];
            float[] lassoY = new float[lassoPath.size()];
            for (int i = 0; i < lassoPath.size(); i++) {
                lassoX[i] = lassoPath.get(i).x;
                lassoY[i] = lassoPath.get(i).y;
            }
            
            for (DrawStroke s : drawStrokes) {
                float[] strokeX, strokeY;
                if (s.tool == DrawTool.HIGHLIGHT) {
                    strokeX = new float[s.points.size()];
                    strokeY = new float[s.points.size()];
                    for (int i = 0; i < s.points.size(); i++) {
                        strokeX[i] = s.points.get(i).x;
                        strokeY[i] = s.points.get(i).y;
                    }
                } else {
                    strokeX = s.getPointsX();
                    strokeY = s.getPointsY();
                }
                
                // Use native lasso test if available, else Java fallback
                boolean selected = false;
                if (nativeDrawing != null && nativeDrawing.isLassoAvailable()) {
                    selected = nativeDrawing.lassoTestStroke(strokeX, strokeY, lassoX, lassoY, 0);
                } else {
                    selected = javaLassoTestStroke(strokeX, strokeY, lassoX, lassoY);
                }
                
                if (selected) selectedStrokes.add(s);
            }
            
            updateSelectionBounds();
        }
        
        private boolean javaLassoTestStroke(float[] strokeX, float[] strokeY, float[] lassoX, float[] lassoY) {
            // Java fallback: check if any point is inside the lasso polygon
            for (int i = 0; i < strokeX.length; i++) {
                if (pointInPolygon(strokeX[i], strokeY[i], lassoX, lassoY)) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean pointInPolygon(float px, float py, float[] polyX, float[] polyY) {
            int n = polyX.length;
            if (n < 3) return false;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if (((polyY[i] > py) != (polyY[j] > py)) &&
                    (px < (polyX[j] - polyX[i]) * (py - polyY[i]) / (polyY[j] - polyY[i]) + polyX[i])) {
                    inside = !inside;
                }
            }
            return inside;
        }
        
        private void updateSelectionBounds() {
            if (selectedStrokes.isEmpty()) {
                selectionBounds = null;
                return;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (DrawStroke s : selectedStrokes) {
                for (Point p : s.points) {
                    if (p.x < minX) minX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y > maxY) maxY = p.y;
                }
            }
            int pad = 8;
            selectionBounds = new java.awt.Rectangle(minX - pad, minY - pad, maxX - minX + 2*pad, maxY - minY + 2*pad);
        }
        
        private void moveSelectedStrokes(int dx, int dy) {
            for (DrawStroke s : selectedStrokes) {
                for (Point p : s.points) {
                    p.x += dx;
                    p.y += dy;
                }
                for (float[] fp : s.floatPoints) {
                    fp[0] += dx;
                    fp[1] += dy;
                }
                s.invalidateCache();
                // Sync with optimizer
                moveStrokeInOptimizer(s, dx, dy);
            }
            if (selectionBounds != null) {
                selectionBounds.translate(dx, dy);
            }
        }
        
        private void clearSelection() {
            selectedStrokes.clear();
            selectionBounds = null;
            lassoPath.clear();
        }
        
        // Get stroke bounding box clipped to viewport
        private java.awt.Rectangle getStrokeBounds(DrawStroke s, int offsetX, int offsetY, int viewW, int viewH) {
            if (s.points.isEmpty()) return new java.awt.Rectangle(0, 0, 0, 0);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Point p : s.points) {
                if (p.x < minX) minX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.x > maxX) maxX = p.x;
                if (p.y > maxY) maxY = p.y;
            }
            int pad = (int)(s.thickness / 2) + 2;
            minX -= pad; minY -= pad;
            maxX += pad; maxY += pad;
            // Convert to screen coords and clip to viewport
            int sx = minX - offsetX, sy = minY - offsetY;
            int sw = maxX - minX, sh = maxY - minY;
            if (sx < 0) { sw += sx; sx = 0; }
            if (sy < 0) { sh += sy; sy = 0; }
            if (sx + sw > viewW) sw = viewW - sx;
            if (sy + sh > viewH) sh = viewH - sy;
            return new java.awt.Rectangle(sx, sy, Math.max(1, sw), Math.max(1, sh));
        }
        
        @Override public Dimension getPreferredSize() { return view.getPreferredSize(); }
        
        @Override protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            
            Point vpPos = vp.getViewPosition();
            float offsetX = vpPos.x;
            float offsetY = vpPos.y;
            
            // Check if viewport changed (requires full redraw)
            boolean viewportChanged = (offsetX != lastOffsetX || offsetY != lastOffsetY);
            if (viewportChanged) {
                strokeBufferDirty = true;
                highlightBufferDirty = true;
                lastOffsetX = offsetX;
                lastOffsetY = offsetY;
            }
            
            // Check if buffer size changed
            boolean sizeChanged = (w != lastBufferWidth || h != lastBufferHeight);
            if (sizeChanged) {
                penStrokeBuffer = null;
                highlightStrokeBuffer = null;
                strokeBufferDirty = true;
                highlightBufferDirty = true;
                lastBufferWidth = w;
                lastBufferHeight = h;
            }
            
            // Render pen strokes with caching
            if (strokeBufferDirty || penStrokeBuffer == null) {
                // Check if any pen strokes exist
                boolean hasPenStrokes = false;
                java.awt.Rectangle viewRect = new java.awt.Rectangle((int) offsetX, (int) offsetY, w, h);
                for (DrawStroke s : drawStrokes) {
                    if (s.tool == DrawTool.PEN && s.intersectsRect(viewRect, s.thickness + 4)) { hasPenStrokes = true; break; }
                }
                
                if (hasPenStrokes) {
                    if (penStrokeBuffer == null || penStrokeBuffer.getWidth() != w || penStrokeBuffer.getHeight() != h) {
                        penStrokeBuffer = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    }
                    
                    int[] pixels = ((java.awt.image.DataBufferInt) penStrokeBuffer.getRaster().getDataBuffer()).getData();
                    java.util.Arrays.fill(pixels, 0);
                    
                    if (nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable()) {
                        for (DrawStroke s : drawStrokes) {
                            if (s.tool != DrawTool.PEN || s.floatPoints.isEmpty()) continue;
                            if (!s.intersectsRect(viewRect, s.thickness + 6)) continue;
                            float[] xs = s.getPointsX();
                            float[] ys = s.getPointsY();
                            float[] thicknesses = getOrComputeThicknesses(s);
                            nativeDrawing.strokeRenderVariable(pixels, w, h, xs, ys, thicknesses, s.color.getRGB(), offsetX, offsetY);
                        }
                    } else {
                        // Java fallback with Path2D for better performance
                        Graphics2D g2 = penStrokeBuffer.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.translate(-offsetX, -offsetY);
                        for (DrawStroke s : drawStrokes) {
                            if (s.tool != DrawTool.PEN) continue;
                            if (!s.intersectsRect(viewRect, s.thickness + 6)) continue;
                            g2.setColor(s.color);
                            g2.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            drawStrokePath(g2, s.floatPoints);
                        }
                        g2.dispose();
                    }
                } else {
                    // No pen strokes - clear the buffer so erased strokes don't persist
                    penStrokeBuffer = null;
                }
                strokeBufferDirty = false;
            }
            // Always draw the pen buffer if it exists
            if (penStrokeBuffer != null) {
                g.drawImage(penStrokeBuffer, 0, 0, null);
            }
            
            // Render highlighter strokes with caching
            if (highlightBufferDirty || highlightStrokeBuffer == null) {
                // Check if any highlighter strokes exist
                boolean hasHighlighters = false;
                for (DrawStroke s : drawStrokes) {
                    if (s.tool == DrawTool.HIGHLIGHT) { hasHighlighters = true; break; }
                }
                
                if (!hasHighlighters) {
                    // No highlighter strokes - clear the buffer so erased strokes don't persist
                    highlightStrokeBuffer = null;
                } else if (hasHighlighters) {
                    if (highlightStrokeBuffer == null || highlightStrokeBuffer.getWidth() != w || highlightStrokeBuffer.getHeight() != h) {
                        highlightStrokeBuffer = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    }
                    
                    Graphics2D bufG = highlightStrokeBuffer.createGraphics();
                    bufG.setComposite(java.awt.AlphaComposite.Clear);
                    bufG.fillRect(0, 0, w, h);
                    bufG.setComposite(java.awt.AlphaComposite.SrcOver);
                    bufG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    bufG.translate(-offsetX, -offsetY);
                    java.awt.Rectangle viewRect = new java.awt.Rectangle((int) offsetX, (int) offsetY, w, h);
                    
                    // Render each highlighter stroke with proper alpha compositing
                    for (DrawStroke s : drawStrokes) {
                        if (s.tool != DrawTool.HIGHLIGHT || s.points.isEmpty()) continue;
                        if (!s.intersectsRect(viewRect, s.thickness + 10)) continue;
                        int alpha = s.color.getAlpha();
                        Color opaqueColor = new Color(s.color.getRed(), s.color.getGreen(), s.color.getBlue(), 255);
                        
                        // Use a small clip-bounded buffer for this stroke only
                        java.awt.Rectangle bounds = getStrokeBounds(s, (int)offsetX, (int)offsetY, w, h);
                        if (bounds.width <= 0 || bounds.height <= 0) continue;
                        
                        java.awt.image.BufferedImage strokeBuf = new java.awt.image.BufferedImage(
                            bounds.width, bounds.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        Graphics2D sg = strokeBuf.createGraphics();
                        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        sg.translate(-bounds.x - offsetX, -bounds.y - offsetY);
                        sg.setColor(opaqueColor);
                        sg.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        drawPointsPath(sg, s.points);
                        sg.dispose();
                        
                        // Composite to main highlighter buffer with alpha
                        bufG.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha / 255f));
                        bufG.drawImage(strokeBuf, bounds.x + (int)offsetX, bounds.y + (int)offsetY, null);
                        bufG.setComposite(java.awt.AlphaComposite.SrcOver);
                    }
                    bufG.dispose();
                }
                highlightBufferDirty = false;
            }
            if (highlightStrokeBuffer != null) {
                g.drawImage(highlightStrokeBuffer, 0, 0, null);
            }
            
            // Draw eraser cursor overlay (in screen coords, after stroke drawing)
            if (capture && currentDrawTool == DrawTool.ERASER && mousePos != null) {
                Graphics2D eg = (Graphics2D) g.create();
                eg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int r = eraserRadius;
                eg.setColor(new Color(100, 100, 100, 150));
                eg.setStroke(new BasicStroke(1.5f));
                eg.drawOval(mousePos.x - r, mousePos.y - r, r * 2, r * 2);
                eg.dispose();
            }
            
            // Draw lasso selection overlay
            if (currentDrawTool == DrawTool.LASSO) {
                Graphics2D lg = (Graphics2D) g.create();
                lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                lg.translate(-offsetX, -offsetY);
                
                // Draw lasso path while drawing
                if (!lassoPath.isEmpty()) {
                    lg.setColor(new Color(70, 130, 180, 200)); // Steel blue
                    lg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{6, 4}, 0)); // Dashed line
                    java.awt.geom.Path2D path = new java.awt.geom.Path2D.Float();
                    path.moveTo(lassoPath.get(0).x, lassoPath.get(0).y);
                    for (int i = 1; i < lassoPath.size(); i++) {
                        path.lineTo(lassoPath.get(i).x, lassoPath.get(i).y);
                    }
                    if (lassoPath.size() > 2) {
                        path.closePath();
                        lg.setColor(new Color(70, 130, 180, 30)); // Fill with light blue
                        lg.fill(path);
                        lg.setColor(new Color(70, 130, 180, 200));
                    }
                    lg.draw(path);
                }
                
                // Draw selection bounds
                if (selectionBounds != null) {
                    lg.setColor(new Color(70, 130, 180, 50));
                    lg.fill(selectionBounds);
                    lg.setColor(new Color(70, 130, 180, 200));
                    lg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{8, 4}, 0));
                    lg.draw(selectionBounds);
                    
                    // Draw corner handles
                    int hs = 6; // handle size
                    lg.setColor(new Color(70, 130, 180));
                    lg.setStroke(new BasicStroke(1.5f));
                    int[] hx = {selectionBounds.x, selectionBounds.x + selectionBounds.width};
                    int[] hy = {selectionBounds.y, selectionBounds.y + selectionBounds.height};
                    for (int x : hx) {
                        for (int y : hy) {
                            lg.fillRect(x - hs/2, y - hs/2, hs, hs);
                        }
                    }
                }
                
                lg.dispose();
            }
        }
        
        // Get or compute cached thicknesses for a stroke
        private float[] getOrComputeThicknesses(DrawStroke s) {
            float[] cached = s.getCachedThicknesses();
            if (cached != null && cached.length == s.floatPoints.size()) {
                return cached;
            }
            // Compute and cache
            int n = s.floatPoints.size();
            float[] thicknesses = new float[n];
            float baseT = s.thickness;
            
            if (n < 2) {
                java.util.Arrays.fill(thicknesses, baseT);
                s.setCachedThicknesses(thicknesses);
                return thicknesses;
            }
            
            thicknesses[0] = baseT;
            float smoothVelocity = 0f;
            
            for (int i = 1; i < n; i++) {
                float[] prev = s.floatPoints.get(i - 1);
                float[] curr = s.floatPoints.get(i);
                
                float dx = curr[0] - prev[0];
                float dy = curr[1] - prev[1];
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float dt = curr[2] - prev[2];
                if (dt < 1) dt = 1;
                
                float velocity = dist / dt;
                smoothVelocity = 0.3f * velocity + 0.7f * smoothVelocity;
                
                // Map velocity to thickness (slower = thicker, faster = thinner)
                float normalized = smoothVelocity / 0.8f;
                float pressure = (float) Math.exp(-normalized * 1.5);
                pressure = Math.max(0.2f, Math.min(1f, pressure));
                
                float factor = 0.4f + pressure * 0.8f;
                thicknesses[i] = baseT * factor;
            }
            
            s.setCachedThicknesses(thicknesses);
            return thicknesses;
        }
        
        // Fast stroke drawing using Path2D (much faster than individual drawLine calls)
        private void drawStrokePath(Graphics2D g2, List<float[]> floatPoints) {
            int n = floatPoints.size();
            if (n == 0) return;
            if (n == 1) {
                float[] p = floatPoints.get(0);
                g2.fillOval((int)(p[0] - 1), (int)(p[1] - 1), 3, 3);
                return;
            }
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            float[] first = floatPoints.get(0);
            path.moveTo(first[0], first[1]);
            for (int i = 1; i < n; i++) {
                float[] p = floatPoints.get(i);
                path.lineTo(p[0], p[1]);
            }
            g2.draw(path);
        }
        
        // Path2D drawing for integer Point list (highlighter)
        private void drawPointsPath(Graphics2D g2, List<Point> points) {
            int n = points.size();
            if (n == 0) return;
            if (n == 1) {
                Point p = points.get(0);
                g2.fillOval(p.x - 1, p.y - 1, 3, 3);
                return;
            }
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            Point first = points.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < n; i++) {
                Point p = points.get(i);
                path.lineTo(p.x, p.y);
            }
            g2.draw(path);
        }
    }
}
