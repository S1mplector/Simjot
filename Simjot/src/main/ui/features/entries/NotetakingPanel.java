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
        main.ui.components.buttons.ToolbarIconButton highlighterBtn = new main.ui.components.buttons.ToolbarIconButton("highlighter_tool");
        highlighterBtn.setToolTipText("Highlighter");
        highlighterBtn.addActionListener(e -> { currentDrawTool = DrawTool.HIGHLIGHT; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(highlighterBtn);
        rightToolbar.add(Box.createHorizontalStrut(4));

        // Eraser tool
        main.ui.components.buttons.ToolbarIconButton eraserBtn = new main.ui.components.buttons.ToolbarIconButton("eraser_tool");
        eraserBtn.setToolTipText("Eraser");
        eraserBtn.addActionListener(e -> { currentDrawTool = DrawTool.ERASER; selectPaintMode(); updatePickersForCurrentTool(); });
        rightToolbar.add(eraserBtn);
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
        if (currentDrawTool == DrawTool.PEN) {
            strokeSpinner.setValue(penThickness);
            colorBtn.setBackground(new Color(penColor.getRed(), penColor.getGreen(), penColor.getBlue()));
            colorBtn.setEnabled(true);
        } else if (currentDrawTool == DrawTool.HIGHLIGHT) {
            strokeSpinner.setValue(highlightThickness);
            colorBtn.setBackground(new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue()));
            colorBtn.setEnabled(true);
        } else {
            strokeSpinner.setValue(eraserRadius);
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
    private enum DrawTool { PEN, HIGHLIGHT, ERASER }
    private static class DrawStroke {
        final DrawTool tool; final Color color; final int thickness; final List<Point> points;
        DrawStroke(DrawTool tool, Color color, int thickness) { this(tool,color,thickness,new ArrayList<>()); }
        DrawStroke(DrawTool tool, Color color, int thickness, List<Point> pts) { this.tool=tool; this.color=color; this.thickness=thickness; this.points=pts; }
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
        private final JScrollPane host;
        private final JViewport vp;
        private final JComponent view;
        private boolean capture = false; // whether to intercept mouse events
        private DrawStroke current;
        private Point lastPoint; // For interpolation
        private static final int MIN_POINT_DISTANCE = 2; // Minimum distance between points
        private static final int MAX_POINT_DISTANCE = 8; // Max distance before interpolation
        
        DrawingOverlay(JScrollPane host, JComponent view) {
            this.host = host;
            this.vp = host.getViewport();
            this.view = view;
            setOpaque(false);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!capture || !SwingUtilities.isLeftMouseButton(e)) return;
                    Point p = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(p); return; }
                    redoStack.clear(); // New stroke clears redo history
                    current = makeStroke();
                    current.points.add(p);
                    lastPoint = p;
                    drawStrokes.add(current);
                    undoStack.add(new AddStrokeAction(current));
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!capture) return;
                    Point p = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(p); return; }
                    if (current == null || lastPoint == null) return;
                    
                    // Calculate distance from last point
                    double dist = lastPoint.distance(p);
                    
                    // Skip if too close (reduces jitter)
                    if (dist < MIN_POINT_DISTANCE) return;
                    
                    // Interpolate if points are far apart (fills gaps from fast movement)
                    if (dist > MAX_POINT_DISTANCE) {
                        int steps = (int) Math.ceil(dist / MAX_POINT_DISTANCE);
                        for (int i = 1; i <= steps; i++) {
                            double t = (double) i / steps;
                            int ix = (int) Math.round(lastPoint.x + t * (p.x - lastPoint.x));
                            int iy = (int) Math.round(lastPoint.y + t * (p.y - lastPoint.y));
                            current.points.add(new Point(ix, iy));
                        }
                    } else {
                        current.points.add(p);
                    }
                    lastPoint = p;
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (current != null && current.points.size() >= 3) {
                        smoothStroke(current);
                    }
                    current = null;
                    lastPoint = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            vp.addChangeListener(e -> repaint());
            
            // Keyboard shortcuts for undo/redo
            int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, meta), "undoStroke");
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "redoStroke");
            getActionMap().put("undoStroke", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (capture) performUndo(); }});
            getActionMap().put("redoStroke", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (capture) performRedo(); }});
            // Keep size in sync with content view
            view.addComponentListener(new ComponentAdapter(){
                @Override public void componentResized(ComponentEvent e) { revalidate(); repaint(); }
            });
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
            java.util.Iterator<DrawStroke> it = drawStrokes.iterator();
            while (it.hasNext()) {
                DrawStroke s = it.next();
                List<Point> pts = s.points;
                for (int i = 1; i < pts.size(); i++) {
                    if (distPointToSegmentSq(p, pts.get(i - 1), pts.get(i)) <= r2) { 
                        it.remove(); 
                        erased.add(s); 
                        break; 
                    }
                }
            }
            if (!erased.isEmpty()) {
                redoStack.clear();
                undoStack.add(new EraseStrokesAction(erased));
                repaint();
            }
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
        
        /**
         * Apply a light Chaikin pass to soften corners without over-smoothing.
         * Only run once to preserve the original handwriting character.
         */
        private void smoothStroke(DrawStroke stroke) {
            if (stroke.points.size() < 4) return;
            
            List<Point> pts = stroke.points;
            List<Point> smoothed = new ArrayList<>(pts.size() * 2);
            
            // Keep first point
            smoothed.add(pts.get(0));
            
            for (int i = 0; i < pts.size() - 1; i++) {
                Point p0 = pts.get(i);
                Point p1 = pts.get(i + 1);
                
                // Gentle Chaikin weights to keep shape (70/30 instead of 75/25)
                int q0x = (int) Math.round(p0.x * 0.7 + p1.x * 0.3);
                int q0y = (int) Math.round(p0.y * 0.7 + p1.y * 0.3);
                int q1x = (int) Math.round(p0.x * 0.3 + p1.x * 0.7);
                int q1y = (int) Math.round(p0.y * 0.3 + p1.y * 0.7);
                
                smoothed.add(new Point(q0x, q0y));
                smoothed.add(new Point(q1x, q1y));
            }
            
            // Keep last point
            smoothed.add(pts.get(pts.size() - 1));
            
            stroke.points.clear();
            stroke.points.addAll(smoothed);
        }
        
        @Override public Dimension getPreferredSize() { return view.getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Point vpPos = vp.getViewPosition();
            g2.translate(-vpPos.x, -vpPos.y);
            for (DrawStroke s : drawStrokes) {
                g2.setColor(s.color);
                g2.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawPolyline(g2, s.points);
            }
            g2.dispose();
        }
        
        /**
         * Draw the stroke as a simple polyline (after optional smoothing).
         */
        private void drawPolyline(Graphics2D g2, List<Point> points) {
            if (points.isEmpty()) return;
            
            Path2D path = new Path2D.Float();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) {
                Point p = points.get(i);
                path.lineTo(p.x, p.y);
            }
            g2.draw(path);
        }
    }
}
