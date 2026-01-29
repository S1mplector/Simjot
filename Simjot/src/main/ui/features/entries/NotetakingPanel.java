/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.AlphaComposite;
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
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.ui.app.JournalApp;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.RichTextStyler;
import main.ui.dialog.file.SimjotFileChooser;
import main.core.service.SettingsStore;
import main.infrastructure.input.TabletInputSupport;

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
    private final List<FloatingImage> floatingImages = new ArrayList<>(); // per-note floating images
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
    private ScrollAssistOverlay scrollAssistOverlay;
    private main.ui.components.buttons.ToolbarIconButton textModeBtn;
    private main.ui.components.buttons.ToolbarIconButton paintModeBtn;
    private main.ui.components.buttons.ToolbarIconButton highlighterBtn;
    private main.ui.components.buttons.ToolbarIconButton eraserBtn;
    private main.ui.components.buttons.ToolbarIconButton lassoBtn;
    private main.ui.components.buttons.TextColorButton textColorBtn;
    private Color currentTextColor = Color.BLACK;
    private EditingMode editingMode = EditingMode.TEXT;
    private final java.util.Deque<Color> recentColors = new java.util.ArrayDeque<>();
    private boolean awaitingTextColorCode = false;
    private Timer textColorCodeTimer;
    private KeyEventDispatcher textColorCodeDispatcher;
    private boolean textColorDispatcherInstalled = false;
    private boolean tabletInitialized = false;
    private boolean useTabletPressure = true;
    private float pressureGamma = 1.0f;
    private float minPressure = 0.05f;

    private static final int TEXT_COLOR_CODE_TIMEOUT_MS = 5000;
    private static final String TEXT_COLOR_CODE_PREF_KEY = "notetaking.textColorCodeMap";
    private static final String DEFAULT_TEXT_COLOR_CODE_MAP =
            "B=#2196F3,R=#F44336,G=#4CAF50,Y=#FFEB3B,O=#FF9800,P=#9C27B0,K=#212121,W=#FFFFFF";
    private static final int DRAW_V2_MAGIC = 0x44525732; // "DRW2"
    private static final int DRAW_V2_VERSION = 2;
    private static final int DRAW_V3_VERSION = 3;
    private static final float DEFAULT_PRESSURE = 1.0f;
    private static final float DRAW_POINT_MIN_DIST = 0.35f;
    private static final long DRAW_POINT_MIN_DT_MS = 2L;
    private static final long DRAW_LARGE_FILE_BYTES = 8L * 1024L * 1024L;

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

    @Override
    protected String getHelpTitle() {
        return "Notetaking Editor";
    }

    @Override
    protected String getHelpMessage() {
        return "<html><body style='text-align:left;'>"
                + "<b>Notetaking features</b><br>"
                + "• Draw on top of notes with pen, highlighter, eraser, or lasso.<br>"
                + "• Text and paint modes live in the right toolbar.<br>"
                + "• Quick heading shortcuts: Cmd/Ctrl+1/2/3, reset with Cmd/Ctrl+0.<br>"
                + "• Text color quick code: Cmd/Ctrl+Shift+C, then a letter.<br>"
                + "• Toggle overlay visibility from the draw menu."
                + "</body></html>";
    }

    @Override
    protected float getEditorGlassOpacity() {
        return SettingsStore.get().getNotetakingGlassOpacity();
    }

    @Override
    public void removeNotify() {
        try { if (drawingOverlay != null) drawingOverlay.shutdown(); } catch (Throwable ignored) {}
        cancelTextColorCode(false);
        super.removeNotify();
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
        boolean showHandwritingToolbar = SettingsStore.get().isHandwritingToolbarEnabled();
        if (showHandwritingToolbar) {
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
            rightToolbar.add(Box.createHorizontalStrut(4));
        }

        if (showHandwritingToolbar) {
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
            colorBtn.addActionListener(e -> showColorPopover());
            rightToolbar.add(colorBtn);

            updatePickersForCurrentTool();
        }
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

            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(img);
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

    private void setEditingMode(EditingMode mode) {
        this.editingMode = mode;
        boolean paint = (mode == EditingMode.PAINT);
        drawingEnabled = paint;
        if (drawingOverlay != null) {
            drawingOverlay.setOverlayVisible(true); // Always keep overlay visible to show strokes
            drawingOverlay.setCaptureEnabled(paint);
        }
        if (scrollAssistOverlay != null) {
            scrollAssistOverlay.setPenMode(paint);
        }
        updateToolButtonStates();
    }

    private enum EditingMode { TEXT, PAINT }

    private void showColorPopover() {
        if (currentDrawTool == DrawTool.ERASER || currentDrawTool == DrawTool.LASSO) return;
        if (colorBtn == null) return;

        if (colorWindow != null && colorWindow.isVisible()) {
            try { colorWindow.setVisible(false); } catch (Throwable ignored) {}
            return;
        }

        if (colorWindow == null) {
            java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
            colorWindow = (owner != null) ? new JWindow(owner) : new JWindow();
            colorWindow.setFocusableWindowState(true);
            colorWindow.setBackground(new Color(0, 0, 0, 0));
            colorWindow.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowLostFocus(java.awt.event.WindowEvent e) {
                    try { colorWindow.setVisible(false); } catch (Throwable ignored) {}
                }
            });
        }

        JPanel content = buildColorPopupContent();
        colorWindow.getContentPane().removeAll();
        colorWindow.getContentPane().add(content);
        colorWindow.pack();
        positionColorWindow();
        colorWindow.setVisible(true);
        colorWindow.requestFocusInWindow();
    }

    private void positionColorWindow() {
        if (colorWindow == null || colorBtn == null) return;
        java.awt.Point p = colorBtn.getLocationOnScreen();
        java.awt.Dimension size = colorWindow.getSize();
        java.awt.Rectangle screen = getScreenBounds(colorBtn);
        int x = p.x + colorBtn.getWidth() - size.width;
        int y = p.y + colorBtn.getHeight() + 8;
        if (x < screen.x + 8) x = screen.x + 8;
        if (x + size.width > screen.x + screen.width - 8) {
            x = screen.x + screen.width - size.width - 8;
        }
        if (y + size.height > screen.y + screen.height - 8) {
            y = p.y - size.height - 8;
        }
        if (y < screen.y + 8) y = screen.y + 8;
        colorWindow.setLocation(x, y);
    }

    private static java.awt.Rectangle getScreenBounds(Component anchor) {
        java.awt.GraphicsConfiguration gc = anchor != null ? anchor.getGraphicsConfiguration() : null;
        if (gc != null) return gc.getBounds();
        java.awt.Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        return new java.awt.Rectangle(0, 0, d.width, d.height);
    }
    
    private void showTextColorPicker() {
        Color picked = main.ui.dialog.utils.SimpleColorPicker.showDialog(
                this, "Text Color", currentTextColor, TEXT_COLOR_CODE_PREF_KEY, DEFAULT_TEXT_COLOR_CODE_MAP);
        applyTextColor(picked);
    }

    private float getPressure(MouseEvent e) {
        if (!useTabletPressure) return DEFAULT_PRESSURE;
        if (!tabletInitialized) {
            tabletInitialized = true;
            try {
                TabletInputSupport.initialize();
                TabletInputSupport.setPressureGamma(pressureGamma);
            } catch (Throwable ignored) {}
        }
        if (TabletInputSupport.isAvailable()) {
            float pressure = TabletInputSupport.getPressure();
            return Math.max(minPressure, pressure);
        }
        return DEFAULT_PRESSURE;
    }

    private JPanel buildColorPopupContent() {
        Color base = (currentDrawTool == DrawTool.PEN ? penColor : highlightColor);
        Color baseOpaque = new Color(base.getRed(), base.getGreen(), base.getBlue());

        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(252, 252, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0,0,0,50), 1),
                BorderFactory.createEmptyBorder(8,10,10,10)
        ));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(228, currentDrawTool==DrawTool.HIGHLIGHT ? 172 : 146));

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
        scrollAssistOverlay = new ScrollAssistOverlay(scrollPane);
        overlayStack.add(scrollAssistOverlay, Integer.valueOf(150));
        textWrapper.add(overlayStack, BorderLayout.CENTER);
        drawingOverlay.setOverlayVisible(overlayVisible);
        drawingOverlay.setCaptureEnabled(drawingEnabled);
        scrollAssistOverlay.setPenMode(drawingEnabled);
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

        // Header: Cmd/Ctrl+Shift+H (selection only)
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "ntk-header");
        am.put("ntk-header", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd()) {
                    RichTextStyler.applyHeaderToSelection(contentArea);
                }
            }
        });

        // Text color quick code: Cmd/Ctrl+Shift+C then a single-letter code (e.g., B/R/G)
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "ntk-text-color-code");
        am.put("ntk-text-color-code", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { beginTextColorCodeCapture(); }
        });
    }

    private void beginTextColorCodeCapture() {
        if (awaitingTextColorCode) return;
        awaitingTextColorCode = true;
        ensureTextColorDispatcherInstalled();
        try { contentArea.requestFocusInWindow(); } catch (Throwable ignored) {}
        if (textColorCodeTimer != null) textColorCodeTimer.stop();
        textColorCodeTimer = new Timer(TEXT_COLOR_CODE_TIMEOUT_MS, e -> cancelTextColorCode(true));
        textColorCodeTimer.setRepeats(false);
        textColorCodeTimer.start();
        main.ui.components.toast.ToastOverlay.info(buildTextColorCodeHint());
    }

    private void cancelTextColorCode(boolean timedOut) {
        if (!awaitingTextColorCode) return;
        awaitingTextColorCode = false;
        if (textColorCodeTimer != null) {
            textColorCodeTimer.stop();
            textColorCodeTimer = null;
        }
        removeTextColorDispatcher();
        if (timedOut) {
            main.ui.components.toast.ToastOverlay.info("Text color code timed out.");
        }
    }

    private void ensureTextColorDispatcherInstalled() {
        if (textColorCodeDispatcher == null) {
            textColorCodeDispatcher = e -> {
                if (!awaitingTextColorCode) return false;
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus == null || !SwingUtilities.isDescendingFrom(focus, contentArea)) {
                    cancelTextColorCode(false);
                    return false;
                }
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        cancelTextColorCode(false);
                        e.consume();
                        return true;
                    }
                    return false;
                }
                if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) {
                    char ch = e.getKeyChar();
                    if (Character.isISOControl(ch)) {
                        e.consume();
                        return true;
                    }
                    handleTextColorCodeChar(ch);
                    e.consume();
                    return true;
                }
                return false;
            };
        }
        if (!textColorDispatcherInstalled) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(textColorCodeDispatcher);
            textColorDispatcherInstalled = true;
        }
    }

    private void removeTextColorDispatcher() {
        if (textColorDispatcherInstalled && textColorCodeDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(textColorCodeDispatcher);
            textColorDispatcherInstalled = false;
        }
    }

    private void handleTextColorCodeChar(char ch) {
        char key = Character.toUpperCase(ch);
        Map<Character, Color> map = resolveTextColorCodeMap();
        Color color = map.get(key);
        if (color != null) {
            applyTextColor(color);
        } else {
            main.ui.components.toast.ToastOverlay.info("Unknown text color code: " + key);
        }
        cancelTextColorCode(false);
    }

    private void applyTextColor(Color color) {
        if (color == null) return;
        currentTextColor = color;
        if (textColorBtn != null) {
            textColorBtn.setTextColor(currentTextColor);
        }
        RichTextStyler.applyColor(contentArea, currentTextColor);
    }

    private Map<Character, Color> resolveTextColorCodeMap() {
        String raw = null;
        try {
            raw = SettingsStore.get().getValue(TEXT_COLOR_CODE_PREF_KEY, DEFAULT_TEXT_COLOR_CODE_MAP);
        } catch (Throwable ignored) {}
        Map<Character, Color> map = parseTextColorCodeMap(raw);
        if (map.isEmpty()) {
            map = parseTextColorCodeMap(DEFAULT_TEXT_COLOR_CODE_MAP);
        }
        return map;
    }

    private Map<Character, Color> parseTextColorCodeMap(String raw) {
        Map<Character, Color> map = new LinkedHashMap<>();
        if (raw == null) return map;
        String[] tokens = raw.split("[,;]");
        for (String token : tokens) {
            if (token == null) continue;
            String t = token.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf('=');
            if (idx < 0) idx = t.indexOf(':');
            if (idx <= 0 || idx >= t.length() - 1) continue;
            char key = Character.toUpperCase(t.substring(0, idx).trim().charAt(0));
            String colorRaw = t.substring(idx + 1).trim();
            Color color = parseColorString(colorRaw);
            if (color != null) {
                map.put(key, color);
            }
        }
        return map;
    }

    private Color parseColorString(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return null;
        switch (s) {
            case "black": return new Color(33, 33, 33);
            case "white": return Color.WHITE;
            case "red": return new Color(244, 67, 54);
            case "green": return new Color(76, 175, 80);
            case "blue": return new Color(33, 150, 243);
            case "yellow": return new Color(255, 235, 59);
            case "orange": return new Color(255, 152, 0);
            case "purple": return new Color(156, 39, 176);
            case "pink": return new Color(233, 30, 99);
            case "brown": return new Color(121, 85, 72);
            case "gray":
            case "grey": return new Color(120, 120, 120);
            case "cyan": return new Color(0, 188, 212);
            case "teal": return new Color(0, 150, 136);
            case "magenta": return new Color(156, 39, 176);
            default: break;
        }
        String hex = s;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.startsWith("0x")) hex = hex.substring(2);
        if (hex.length() == 6 || hex.length() == 8) {
            try {
                long val = Long.parseLong(hex, 16);
                if (hex.length() == 8) {
                    return new Color((int) val, true);
                }
                return new Color((int) val);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String buildTextColorCodeHint() {
        Map<Character, Color> map = resolveTextColorCodeMap();
        if (map.isEmpty()) {
            return "Text color: type a code (Esc to cancel)";
        }
        StringBuilder sb = new StringBuilder("Text color code: ");
        int count = 0;
        for (Map.Entry<Character, Color> entry : map.entrySet()) {
            if (count > 0) sb.append("  ");
            sb.append(entry.getKey()).append("=").append(toHex(entry.getValue()));
            if (++count >= 6 && map.size() > 6) { sb.append("  ..."); break; }
        }
        sb.append("  (Esc to cancel)");
        return sb.toString();
    }

    private static String toHex(Color color) {
        if (color == null) return "#000000";
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
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

    public boolean promoteInlineImage(JTextPane editor, int startOffset, ImageIcon icon, File sourceFile, java.awt.Rectangle viewBounds) {
        if (editor == null || icon == null) return false;
        java.awt.Rectangle bounds = viewBounds;
        if (bounds == null) {
            try {
                java.awt.geom.Rectangle2D r2 = editor.modelToView2D(startOffset);
                if (r2 != null) {
                    bounds = r2.getBounds();
                    bounds.width = icon.getIconWidth();
                    bounds.height = icon.getIconHeight();
                }
            } catch (Throwable ignored) {}
        }
        if (bounds == null) return false;

        BufferedImage img = toBufferedImage(icon.getImage());
        if (img == null) return false;
        File stored = ensureFloatingImageFile(sourceFile, img);
        JViewport vp = null;
        java.awt.Container parent = editor.getParent();
        if (parent instanceof JViewport) vp = (JViewport) parent;
        Point vpPos = vp != null ? vp.getViewPosition() : new Point(0, 0);
        int x = bounds.x + vpPos.x;
        int y = bounds.y + vpPos.y;

        FloatingImage fi = new FloatingImage(stored, img, x, y, icon.getIconWidth(), icon.getIconHeight());
        floatingImages.add(fi);
        try {
            editor.getStyledDocument().remove(startOffset, 1);
        } catch (BadLocationException ignored) {}
        if (drawingOverlay != null) drawingOverlay.repaint();
        return true;
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
        try {
            File sideV2 = new File(f.getAbsolutePath() + ".draw2");
            if (sideV2.exists() && sideV2.length() > DRAW_LARGE_FILE_BYTES) {
                loadDrawingSidecarAsync(f);
            } else {
                loadDrawingSidecar(f);
            }
        } catch (Throwable ignored) {}
        if (drawingOverlay != null) drawingOverlay.repaint();
    }

    private void saveDrawingSidecar(File mainFile) {
        if (mainFile == null) return;
        File sideV2 = new File(mainFile.getAbsolutePath() + ".draw2");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(sideV2)))) {
            out.writeInt(DRAW_V2_MAGIC);
            out.writeInt(DRAW_V3_VERSION);
            out.writeInt(drawStrokes.size());
            out.writeInt(floatingImages.size());
            for (DrawStroke s : drawStrokes) {
                out.writeByte(s.tool == DrawTool.PEN ? 0 : 1);
                out.writeInt(s.color.getRGB());
                out.writeShort(Math.max(1, Math.min(Short.MAX_VALUE, s.thickness)));
                int n = s.floatPoints.size();
                out.writeInt(n);
                long prevTs = 0L;
                for (int i = 0; i < n; i++) {
                    float[] p = s.floatPoints.get(i);
                    out.writeFloat(p[0]);
                    out.writeFloat(p[1]);
                    out.writeFloat(p[2]);
                    long ts = (long) p[3];
                    int dt = (i == 0) ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, ts - prevTs));
                    out.writeInt(dt);
                    prevTs = ts;
                }
            }
            for (FloatingImage img : floatingImages) {
                File stored = ensureFloatingImageFile(img.sourceFile, img.image);
                if (stored == null) continue;
                img.sourceFile = stored;
                String rel = makeRelativeToJournal(stored);
                if (rel == null) continue;
                out.writeUTF(rel);
                out.writeInt(img.x);
                out.writeInt(img.y);
                out.writeInt(img.width);
                out.writeInt(img.height);
            }
        } catch (IOException ignored) { }
    }

    private void loadDrawingSidecar(File mainFile) {
        drawStrokes.clear();
        floatingImages.clear();
        if (mainFile == null) return;
        File sideV2 = new File(mainFile.getAbsolutePath() + ".draw2");
        if (sideV2.exists()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(sideV2)))) {
                int magic = in.readInt();
                int version = in.readInt();
                if (magic != DRAW_V2_MAGIC || (version != DRAW_V2_VERSION && version != DRAW_V3_VERSION)) return;
                int strokeCount = in.readInt();
                int imageCount = in.readInt();
                for (int s = 0; s < strokeCount; s++) {
                    byte toolByte = in.readByte();
                    DrawTool tool = toolByte == 0 ? DrawTool.PEN : DrawTool.HIGHLIGHT;
                    Color color = new Color(in.readInt(), true);
                    int thick = in.readShort();
                    int n = in.readInt();
                    DrawStroke stroke = new DrawStroke(tool, color, thick);
                    long base = System.currentTimeMillis();
                    long ts = base;
                    for (int i = 0; i < n; i++) {
                        float x = in.readFloat();
                        float y = in.readFloat();
                        float pressure = (version >= DRAW_V3_VERSION) ? in.readFloat() : DEFAULT_PRESSURE;
                        int dt = in.readInt();
                        if (i > 0) ts += Math.max(0L, dt);
                        stroke.addPointRaw(x, y, pressure, ts);
                    }
                    drawStrokes.add(stroke);
                }
                for (int i = 0; i < imageCount; i++) {
                    String rel = in.readUTF();
                    File imgFile = resolveImageFile(rel);
                    if (imgFile == null || !imgFile.exists()) continue;
                    int x = in.readInt();
                    int y = in.readInt();
                    int w = in.readInt();
                    int h = in.readInt();
                    BufferedImage img = null;
                    try { img = ImageIO.read(imgFile); } catch (Throwable ignored) {}
                    if (img == null) continue;
                    if (w > 0 && h > 0) {
                        img = scaleToSize(img, w, h);
                    }
                    floatingImages.add(new FloatingImage(imgFile, img, x, y, img.getWidth(), img.getHeight()));
                }
            } catch (Exception ignored) {}
        } else {
            File side = new File(mainFile.getAbsolutePath() + ".draw");
            if (!side.exists()) return;
            try (BufferedReader in = new BufferedReader(new FileReader(side))) {
                String first = in.readLine();
                if (first == null || !first.startsWith("DRAWV1")) return;
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("S|")) {
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
                    } else if (line.startsWith("I|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length < 6) continue;
                        File imgFile = resolveImageFile(parts[1]);
                        if (imgFile == null || !imgFile.exists()) continue;
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        int w = Integer.parseInt(parts[4]);
                        int h = Integer.parseInt(parts[5]);
                        BufferedImage img = null;
                        try { img = ImageIO.read(imgFile); } catch (Throwable ignored) {}
                        if (img == null) continue;
                        if (w > 0 && h > 0) {
                            img = scaleToSize(img, w, h);
                        }
                        floatingImages.add(new FloatingImage(imgFile, img, x, y, img.getWidth(), img.getHeight()));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (drawingOverlay != null) {
            drawingOverlay.markNativeDirtyAll();
        }
    }

    private void loadDrawingSidecarAsync(File mainFile) {
        new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                loadDrawingSidecar(mainFile);
                return null;
            }
            @Override
            protected void done() {
                if (drawingOverlay != null) {
                    drawingOverlay.markNativeDirtyAll();
                    drawingOverlay.repaint();
                }
            }
        }.execute();
    }

    private File ensureFloatingImageFile(File sourceFile, BufferedImage img) {
        if (journalFolder == null) return sourceFile;
        File dir = new File(journalFolder, "attachments");
        if (!dir.exists()) dir.mkdirs();
        File target = sourceFile;
        boolean inJournal = target != null && target.getAbsolutePath().startsWith(journalFolder.getAbsolutePath());
        if (img == null) {
            return (target != null && target.exists()) ? target : null;
        }
        if (target == null || !inJournal) {
            target = new File(dir, timestampName() + ".png");
        }
        if (img != null && target != null && !target.exists()) {
            try { ImageIO.write(img, "PNG", target); } catch (IOException ignored) {}
        }
        return target;
    }

    private File resolveImageFile(String rel) {
        if (rel == null || rel.isBlank()) return null;
        File f = new File(rel);
        if (!f.isAbsolute()) f = new File(journalFolder, rel);
        return f;
    }

    private String makeRelativeToJournal(File f) {
        if (f == null || journalFolder == null) return null;
        String base = journalFolder.getAbsolutePath();
        String path = f.getAbsolutePath();
        if (path.startsWith(base)) {
            String rel = path.substring(base.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel.replace('\\', '/');
        }
        return path;
    }

    private static String timestampName() {
        return new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new java.util.Date());
    }

    private static BufferedImage toBufferedImage(java.awt.Image img) {
        if (img == null) return null;
        if (img instanceof BufferedImage bi) return bi;
        BufferedImage b = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = b.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return b;
    }

    private static BufferedImage scaleToSize(BufferedImage src, int targetW, int targetH) {
        if (src == null || targetW <= 0 || targetH <= 0) return src;
        if (src.getWidth() == targetW && src.getHeight() == targetH) return src;
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, targetW, targetH, null);
        g2.dispose();
        return out;
    }

    private static String toPointsCsv(DrawStroke stroke) {
        StringBuilder sb = new StringBuilder();
        if (stroke == null) return "";
        for (int i=0;i<stroke.floatPoints.size();i++) {
            float[] p = stroke.floatPoints.get(i);
            if (i>0) sb.append(';');
            sb.append(Math.round(p[0])).append(',').append(Math.round(p[1]));
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
        updateToolButtonStates();
        
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

    private void updateToolButtonStates() {
        boolean paint = (editingMode == EditingMode.PAINT);
        float idle = paint ? 0.6f : 0.3f;

        if (textModeBtn != null) {
            boolean textActive = !paint;
            textModeBtn.setSelected(textActive);
            textModeBtn.setIconOpacity(textActive ? 1f : 0.45f);
        }
        if (paintModeBtn != null) {
            boolean selected = paint && currentDrawTool == DrawTool.PEN;
            paintModeBtn.setSelected(selected);
            paintModeBtn.setIconOpacity(selected ? 1f : idle);
        }
        if (highlighterBtn != null) {
            boolean selected = paint && currentDrawTool == DrawTool.HIGHLIGHT;
            highlighterBtn.setSelected(selected);
            highlighterBtn.setIconOpacity(selected ? 1f : idle);
        }
        if (eraserBtn != null) {
            boolean selected = paint && currentDrawTool == DrawTool.ERASER;
            eraserBtn.setSelected(selected);
            eraserBtn.setIconOpacity(selected ? 1f : idle);
        }
        if (lassoBtn != null) {
            boolean selected = paint && currentDrawTool == DrawTool.LASSO;
            lassoBtn.setSelected(selected);
            lassoBtn.setIconOpacity(selected ? 1f : idle);
        }
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
            for (FloatingImage imgEntry : floatingImages) {
                if (imgEntry.image == null) continue;
                g2.drawImage(imgEntry.image, imgEntry.x, imgEntry.y, imgEntry.width, imgEntry.height, null);
            }
            for (DrawStroke s : drawStrokes) {
                g2.setColor(s.color);
                g2.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Float();
                for (int i=0;i<s.floatPoints.size();i++) {
                    float[] p = s.floatPoints.get(i);
                    if (i==0) path.moveTo(p[0], p[1]); else path.lineTo(p[0], p[1]);
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

    private static class FloatingImage {
        private File sourceFile;
        private BufferedImage image;
        private int x;
        private int y;
        private int width;
        private int height;

        private FloatingImage(File sourceFile, BufferedImage image, int x, int y, int width, int height) {
            this.sourceFile = sourceFile;
            this.image = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private java.awt.Rectangle bounds() {
            return new java.awt.Rectangle(x, y, width, height);
        }
    }
    
    // Enhanced stroke with float precision, pressure, and timestamps for velocity-based rendering
    private static class DrawStroke {
        final DrawTool tool;
        final Color color;
        final int thickness;
        final List<float[]> floatPoints; // [x, y, pressure, timestamp] for native engine
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
            this.floatPoints = new ArrayList<>();
            // Convert existing points to float format
            for (Point p : pts) {
                floatPoints.add(new float[]{p.x, p.y, DEFAULT_PRESSURE, System.currentTimeMillis()});
                updateBounds(p.x, p.y);
            }
            if (pts.isEmpty()) {
                boundsDirty = true;
            }
        }
        
        void addPoint(float x, float y, float pressure, long timestamp) {
            if (!floatPoints.isEmpty()) {
                float[] last = floatPoints.get(floatPoints.size() - 1);
                float dx = x - last[0];
                float dy = y - last[1];
                if ((dx * dx + dy * dy) < (DRAW_POINT_MIN_DIST * DRAW_POINT_MIN_DIST)
                        && Math.abs(timestamp - (long) last[3]) < DRAW_POINT_MIN_DT_MS) return;
            }
            addPointRaw(x, y, pressure, timestamp);
        }

        void addPointRaw(float x, float y, float pressure, long timestamp) {
            floatPoints.add(new float[]{x, y, pressure, timestamp});
            updateBounds(Math.round(x), Math.round(y));
            cacheValid = false;
            cachedThicknesses = null;
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
            if (floatPoints.isEmpty()) {
                minX = minY = Integer.MAX_VALUE;
                maxX = maxY = Integer.MIN_VALUE;
                boundsDirty = true;
                return;
            }
            minX = minY = Integer.MAX_VALUE;
            maxX = maxY = Integer.MIN_VALUE;
            for (float[] p : floatPoints) {
                updateBounds(Math.round(p[0]), Math.round(p[1]));
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
            if (floatPoints.isEmpty()) return false;
            java.awt.Rectangle bounds = getBoundsWithPadding(pad);
            return bounds.intersects(rect);
        }

        void addLegacyPoint(Point p) {
            addPointRaw(p.x, p.y, DEFAULT_PRESSURE, System.currentTimeMillis());
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
            for (int i = 0; i < floatPoints.size(); i++) ts[i] = (long) floatPoints.get(i)[3];
            return ts;
        }

        float[] getPressures() {
            float[] ps = new float[floatPoints.size()];
            for (int i = 0; i < floatPoints.size(); i++) ps[i] = floatPoints.get(i)[2];
            return ps;
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
                if (drawingOverlay != null) drawingOverlay.markNativeDirty(a.stroke().tool);
            }
            case EraseStrokesAction a -> {
                drawStrokes.addAll(a.erased());
                redoStack.add(action);
                if (drawingOverlay != null) drawingOverlay.markNativeDirtyAll();
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
                if (drawingOverlay != null) drawingOverlay.markNativeDirty(a.stroke().tool);
            }
            case EraseStrokesAction a -> {
                drawStrokes.removeAll(a.erased());
                undoStack.add(action);
                if (drawingOverlay != null) drawingOverlay.markNativeDirtyAll();
            }
        }
        if (drawingOverlay != null) drawingOverlay.repaint();
    }

private class ScrollAssistOverlay extends JComponent {
    private static final int HOT_ZONE_FALLBACK = 80;
    private static final int BUTTON_SIZE = 44;
    private static final int BUTTON_SPACING = 16;
    private static final int BUTTON_MARGIN = 18;
    private static final int SCROLL_STEP = 20;

    private final JScrollPane host;
    private final JScrollBar verticalBar;
    private final java.awt.Rectangle upBounds = new java.awt.Rectangle();
    private final java.awt.Rectangle downBounds = new java.awt.Rectangle();
    private final Timer fadeTimer;
    private final Timer scrollTimer;

    private float alpha = 0f;
    private float targetAlpha = 0f;
    private boolean penMode = false;
    private boolean hoverUp = false;
    private boolean hoverDown = false;
    private int scrollDirection = 0;
    private Point lastMouse = new Point(-1, -1);

    ScrollAssistOverlay(JScrollPane host) {
        this.host = host;
        this.verticalBar = host.getVerticalScrollBar();
        setOpaque(false);

        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { handleMouseMove(e.getPoint()); }
            @Override public void mouseEntered(MouseEvent e) { handleMouseMove(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { handleMouseMove(null); }
        };
        addMouseMotionListener(adapter);
        addMouseListener(adapter);

        fadeTimer = new Timer(30, e -> animateFade());
        fadeTimer.start();
        scrollTimer = new Timer(16, e -> performScroll());
    }

    void setPenMode(boolean enabled) {
        this.penMode = enabled;
        if (!enabled) {
            setTargetAlpha(0f);
            startScroll(0);
        } else if (lastMouse != null && isInHotZone(lastMouse.x) && canScroll()) {
            setTargetAlpha(1f);
        }
        repaint();
    }

    private void handleMouseMove(Point p) {
        if (p != null) {
            lastMouse = p;
        } else {
            lastMouse = new Point(-1, -1);
        }
        boolean shouldShow = penMode && p != null && isInHotZone(p.x) && canScroll();
        setTargetAlpha(shouldShow ? 1f : 0f);
        updateButtonHover(p);
    }

    private void setTargetAlpha(float value) {
        value = Math.max(0f, Math.min(1f, value));
        if (targetAlpha != value) {
            targetAlpha = value;
        }
    }

    private void animateFade() {
        float diff = targetAlpha - alpha;
        if (Math.abs(diff) < 0.01f) {
            alpha = targetAlpha;
        } else {
            alpha += diff * 0.25f;
        }
        if (alpha < 0.01f && targetAlpha == 0f) {
            startScroll(0);
        }
        repaint();
    }

    private void updateButtonHover(Point p) {
        updateButtonBounds();
        boolean prevHoverUp = hoverUp;
        boolean prevHoverDown = hoverDown;

        if (penMode && alpha > 0.05f && p != null) {
            hoverUp = upBounds.contains(p);
            hoverDown = downBounds.contains(p);
        } else {
            hoverUp = false;
            hoverDown = false;
        }

        if (hoverUp) {
            startScroll(-1);
        } else if (hoverDown) {
            startScroll(1);
        } else {
            startScroll(0);
        }

        if (prevHoverUp != hoverUp || prevHoverDown != hoverDown) {
            repaint();
        }
    }

    private void startScroll(int dir) {
        if (scrollDirection == dir) return;
        scrollDirection = dir;
        if (dir == 0) {
            if (scrollTimer.isRunning()) scrollTimer.stop();
        } else {
            if (!scrollTimer.isRunning()) scrollTimer.start();
        }
    }

    private void performScroll() {
        if (scrollDirection == 0 || !penMode || !canScroll()) return;
        if (verticalBar == null) return;
        int min = verticalBar.getMinimum();
        int max = verticalBar.getMaximum() - verticalBar.getVisibleAmount();
        int value = verticalBar.getValue();
        int next = Math.max(min, Math.min(max, value + scrollDirection * SCROLL_STEP));
        if (next != value) {
            verticalBar.setValue(next);
        }
    }

    private boolean canScroll() {
        if (verticalBar == null) return false;
        return verticalBar.getMaximum() - verticalBar.getMinimum() > verticalBar.getVisibleAmount();
    }

    private int getHotZoneWidth() {
        int sbWidth = (verticalBar != null) ? verticalBar.getPreferredSize().width : 0;
        return Math.max(HOT_ZONE_FALLBACK, sbWidth + 40);
    }

    private boolean isInHotZone(int mouseX) {
        int w = getWidth();
        if (w <= 0) return false;
        return mouseX >= Math.max(0, w - getHotZoneWidth());
    }

    private void updateButtonBounds() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        int btnX = w - BUTTON_SIZE - BUTTON_MARGIN;
        int centerY = h / 2;
        upBounds.setBounds(btnX, centerY - BUTTON_SIZE - BUTTON_SPACING / 2, BUTTON_SIZE, BUTTON_SIZE);
        downBounds.setBounds(btnX, centerY + BUTTON_SPACING / 2, BUTTON_SIZE, BUTTON_SIZE);
    }

    @Override
    public boolean contains(int x, int y) {
        return penMode && isInHotZone(x) && canScroll();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!penMode || alpha <= 0.01f || !canScroll()) return;
        updateButtonBounds();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.9f, alpha)));

        drawButton(g2, upBounds, hoverUp, true);
        drawButton(g2, downBounds, hoverDown, false);
        g2.dispose();
    }

    private void drawButton(Graphics2D g2, java.awt.Rectangle bounds, boolean hover, boolean up) {
        float hoverBoost = hover ? 0.2f : 0f;
        Color fill = new Color(20, 20, 20, (int) (140 * alpha + hoverBoost * 255));
        Color border = new Color(255, 255, 255, (int) (90 * alpha + hoverBoost * 120));
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18));
        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(border);
        g2.draw(new RoundRectangle2D.Float(bounds.x + 0.5f, bounds.y + 0.5f,
                bounds.width - 1f, bounds.height - 1f, 18, 18));

        Path2D.Float arrow = new Path2D.Float();
        int cx = bounds.x + bounds.width / 2;
        int cy = bounds.y + bounds.height / 2;
        int size = 10;
        if (up) {
            arrow.moveTo(cx, cy - size / 2f);
            arrow.lineTo(cx - size / 1.6f, cy + size / 2f);
            arrow.lineTo(cx + size / 1.6f, cy + size / 2f);
        } else {
            arrow.moveTo(cx, cy + size / 2f);
            arrow.lineTo(cx - size / 1.6f, cy - size / 2f);
            arrow.lineTo(cx + size / 1.6f, cy - size / 2f);
        }
        arrow.closePath();
        g2.setColor(new Color(255, 255, 255, (int) (hover ? 230 : 190)));
        g2.fill(arrow);
    }
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
    private long nativePenManager = 0;
    private long nativeHighlightManager = 0;
    private int currentNativeStrokeIdx = -1;
    private DrawTool currentNativeTool = null;
    private boolean nativePenDirty = true;
    private boolean nativeHighlightDirty = true;
    private final ExecutorService strokeWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NotetakingStrokeWorker");
        t.setDaemon(true);
        return t;
    });

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
    private java.util.Map<Integer, DrawStroke> optIdToStroke = new java.util.HashMap<>();
    private static final int OPTIMIZER_THRESHOLD = 100; // Enable when stroke count exceeds this
    private boolean useOptimizer = false;

    // Float-precision smoothing state (keeps sub-pixel precision)
    private float smoothX, smoothY;
    private float lastRawX, lastRawY;
    private long lastTimestamp;
    private static final float SMOOTH_ALPHA_MIN = 0.35f;
    private static final float SMOOTH_ALPHA_MAX = 0.82f;
    private static final float SMOOTH_VELOCITY_SCALE = 0.08f;
    private static final float MIN_DISTANCE_BASE = 0.6f;
    private static final float MAX_DISTANCE_BOOST = 5f;
    private static final float DISTANCE_VELOCITY_SCALE = 4f;
    private static final int FINAL_SMOOTH_ITER = 2;
    private static final float FINAL_SAMPLE_DISTANCE = 1.0f;

    // Lasso selection state
    private List<Point> lassoPath = new ArrayList<>();
    private java.util.Set<DrawStroke> selectedStrokes = new java.util.HashSet<>();
    private java.util.Set<FloatingImage> selectedImages = new java.util.HashSet<>();
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
                nativePenManager = createNativeManager();
                nativeHighlightManager = createNativeManager();
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
                beginNativeStroke(current, p.x, p.y, now);

                if (currentDrawTool == DrawTool.HIGHLIGHT) {
                    current.addLegacyPoint(p);
                } else {
                    // Initialize smoothing state with float precision for pen
                    smoothX = p.x;
                    smoothY = p.y;
                    lastRawX = p.x;
                    lastRawY = p.y;
                    lastTimestamp = now;
                    if (TabletInputSupport.isAvailable()) {
                        TabletInputSupport.resetPressureSmoothing();
                    }
                    float pressure = getPressure(e);
                    current.addPoint(smoothX, smoothY, pressure, now);
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
                    Point prev = lastPoint;
                    current.addLegacyPoint(raw);
                    addNativePoint(current, raw.x, raw.y, System.currentTimeMillis());
                    lastPoint = raw;
                    highlightBufferDirty = true;
                    repaintDirty(prev, raw, current.thickness);
                    return;
                }

                long now = System.currentTimeMillis();
                float rawX = raw.x;
                float rawY = raw.y;

                float dx = rawX - lastRawX;
                float dy = rawY - lastRawY;
                float minDist = MIN_DISTANCE_BASE;
                if (lastTimestamp > 0) {
                    long dt = Math.max(1, now - lastTimestamp);
                    float velocity = (float) (Math.sqrt(dx * dx + dy * dy) / dt);
                    minDist += Math.min(MAX_DISTANCE_BOOST, velocity * DISTANCE_VELOCITY_SCALE);
                }
                float minDistSq = minDist * minDist;
                if (dx * dx + dy * dy < minDistSq) {
                    return;
                }

                float velocity = (float) (Math.sqrt(dx * dx + dy * dy) / Math.max(1, now - lastTimestamp));
                float alpha = SMOOTH_ALPHA_MAX - Math.min(0.45f, velocity * SMOOTH_VELOCITY_SCALE);
                if (alpha < SMOOTH_ALPHA_MIN) alpha = SMOOTH_ALPHA_MIN;
                smoothX = alpha * rawX + (1f - alpha) * smoothX;
                smoothY = alpha * rawY + (1f - alpha) * smoothY;

                Point prev = lastPoint;
                float pressure = getPressure(e);
                current.addPoint(smoothX, smoothY, pressure, now);
                addNativePoint(current, smoothX, smoothY, now);
                lastPoint = new Point(Math.round(smoothX), Math.round(smoothY));
                lastRawX = rawX;
                lastRawY = rawY;
                lastTimestamp = now;
                appendPenSegment(current);
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
                if (current != null) {
                    endNativeStroke(current);
                }
                if (current != null && currentDrawTool == DrawTool.PEN && current.floatPoints.size() >= 3) {
                    final DrawStroke strokeToSmooth = current;
                    strokeWorker.submit(() -> applyFinalSmoothing(strokeToSmooth));
                }
                if (current != null && useOptimizer) {
                    addStrokeToOptimizer(current);
                }
                current = null;
                lastPoint = null;
                strokeBufferDirty = true;
                highlightBufferDirty = true;
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

        private long createNativeManager() {
            if (nativeDrawing == null) return 0;
            Dimension pref = view.getPreferredSize();
            int docW = Math.max(1, Math.max(view.getWidth(), pref != null ? pref.width : 0));
            int docH = Math.max(1, Math.max(view.getHeight(), pref != null ? pref.height : 0));
            return nativeDrawing.strokeManagerCreate(docW, docH);
        }

        private boolean ensureNativeManager(DrawTool tool) {
            if (nativeDrawing == null || !nativeDrawing.isStrokeEngineAvailable()) return false;
            if (tool == DrawTool.PEN) {
                if (nativePenManager == 0) nativePenManager = createNativeManager();
                return nativePenManager != 0;
            }
            if (tool == DrawTool.HIGHLIGHT) {
                if (nativeHighlightManager == 0) nativeHighlightManager = createNativeManager();
                return nativeHighlightManager != 0;
            }
            return false;
        }

        private void markNativeDirty(DrawTool tool) {
            if (tool == DrawTool.PEN) nativePenDirty = true;
            else if (tool == DrawTool.HIGHLIGHT) nativeHighlightDirty = true;
        }

        private void markNativeDirtyAll() {
            nativePenDirty = true;
            nativeHighlightDirty = true;
        }

        private void rebuildNativeManager(DrawTool tool) {
            if (!ensureNativeManager(tool)) return;
            long handle = (tool == DrawTool.PEN) ? nativePenManager : nativeHighlightManager;
            if (handle == 0) return;
            nativeDrawing.strokeClearAll(handle);
            for (DrawStroke s : drawStrokes) {
                if (s.tool != tool || s.floatPoints.isEmpty()) continue;
                addStrokeToNativeManager(handle, s);
            }
            if (tool == DrawTool.PEN) nativePenDirty = false;
            else if (tool == DrawTool.HIGHLIGHT) nativeHighlightDirty = false;
        }

        private void addStrokeToNativeManager(long handle, DrawStroke stroke) {
            if (handle == 0 || stroke == null || stroke.floatPoints.isEmpty()) return;
            float[] first = stroke.floatPoints.get(0);
            int idx = nativeDrawing.strokeBegin(handle, first[0], first[1], (long) first[3],
                    stroke.thickness, stroke.color.getRGB(), false);
            if (idx < 0) return;
            for (int i = 1; i < stroke.floatPoints.size(); i++) {
                float[] p = stroke.floatPoints.get(i);
                nativeDrawing.strokeAddPoint(handle, idx, p[0], p[1], (long) p[3]);
            }
            nativeDrawing.strokeEnd(handle, idx, false);
        }

        private void beginNativeStroke(DrawStroke stroke, float x, float y, long timestamp) {
            if (stroke == null) return;
            DrawTool tool = stroke.tool;
            if (!ensureNativeManager(tool)) return;
            long handle = (tool == DrawTool.PEN) ? nativePenManager : nativeHighlightManager;
            if (tool == DrawTool.PEN && nativePenDirty) {
                rebuildNativeManager(DrawTool.PEN);
            } else if (tool == DrawTool.HIGHLIGHT && nativeHighlightDirty) {
                rebuildNativeManager(DrawTool.HIGHLIGHT);
            }
            if (handle == 0) return;
            currentNativeStrokeIdx = nativeDrawing.strokeBegin(handle, x, y, timestamp,
                    stroke.thickness, stroke.color.getRGB(), false);
            currentNativeTool = tool;
        }

        private void addNativePoint(DrawStroke stroke, float x, float y, long timestamp) {
            if (stroke == null || currentNativeStrokeIdx < 0 || currentNativeTool != stroke.tool) return;
            long handle = (stroke.tool == DrawTool.PEN) ? nativePenManager : nativeHighlightManager;
            if (handle == 0) return;
            nativeDrawing.strokeAddPoint(handle, currentNativeStrokeIdx, x, y, timestamp);
        }

        private void endNativeStroke(DrawStroke stroke) {
            if (stroke == null || currentNativeStrokeIdx < 0 || currentNativeTool != stroke.tool) return;
            long handle = (stroke.tool == DrawTool.PEN) ? nativePenManager : nativeHighlightManager;
            if (handle != 0) nativeDrawing.strokeEnd(handle, currentNativeStrokeIdx, false);
            currentNativeStrokeIdx = -1;
            currentNativeTool = null;
        }
        
        private void applyFinalSmoothing(DrawStroke stroke) {
            if (nativeDrawing == null || stroke == null) return;
            float[] xs = stroke.getPointsX();
            float[] ys = stroke.getPointsY();
            long[] ts = stroke.getTimestamps();
            if (xs.length < 3) return;
            float[] basePressures = stroke.getPressures();
            
            float[] workingX = xs;
            float[] workingY = ys;
            long[] workingTs = ts;
            
            float[][] sampled = nativeDrawing.strokeDistanceSample(xs, ys, FINAL_SAMPLE_DISTANCE);
            if (sampled != null && sampled[0].length >= 3 && sampled[0].length < xs.length) {
                workingX = sampled[0];
                workingY = sampled[1];
                workingTs = mapSampledTimestamps(sampled[0], sampled[1], xs, ys, ts);
            }
            
            float[][] smoothed = nativeDrawing.strokeSmoothChaikin(workingX, workingY, FINAL_SMOOTH_ITER);
            float[] finalX = workingX;
            float[] finalY = workingY;
            long[] finalTs = workingTs;
            if (smoothed != null && smoothed[0].length >= 3) {
                finalX = smoothed[0];
                finalY = smoothed[1];
                finalTs = interpolateTimestamps(finalX, finalY, workingTs[0], workingTs[workingTs.length - 1]);
            }
            
            float[] finalXs = finalX;
            float[] finalYs = finalY;
            long[] finalTimestamps = finalTs;
            SwingUtilities.invokeLater(() -> replaceStrokePoints(stroke, finalXs, finalYs, basePressures, finalTimestamps));
        }

        private void applyFinalHighlightSmoothing(DrawStroke stroke) {
            if (stroke == null || stroke.floatPoints.size() < 3) return;
            float[] xs = stroke.getPointsX();
            float[] ys = stroke.getPointsY();
            if (xs.length >= 3 && ys.length == xs.length && nativeDrawing != null) {
                float[] finalX = xs;
                float[] finalY = ys;
                float[][] sampled = nativeDrawing.strokeDistanceSample(xs, ys, FINAL_SAMPLE_DISTANCE);
                if (sampled != null && sampled[0].length >= 3 && sampled[0].length < xs.length) {
                    finalX = sampled[0];
                    finalY = sampled[1];
                }
                float[][] smoothed = nativeDrawing.strokeSmoothChaikin(finalX, finalY, FINAL_SMOOTH_ITER);
                if (smoothed != null && smoothed[0].length >= 3) {
                    finalX = smoothed[0];
                    finalY = smoothed[1];
                }
                long[] baseTs = stroke.getTimestamps();
                long start = (baseTs.length > 0) ? baseTs[0] : System.currentTimeMillis();
                long end = (baseTs.length > 0) ? baseTs[baseTs.length - 1] : start;
                long[] ts = interpolateTimestamps(finalX, finalY, start, end);
                float[] finalXs = finalX;
                float[] finalYs = finalY;
                long[] finalTs = ts;
                float[] basePressures = stroke.getPressures();
                SwingUtilities.invokeLater(() -> replaceStrokePoints(stroke, finalXs, finalYs, basePressures, finalTs));
                return;
            }
            int n = stroke.floatPoints.size();
            float[] smoothX = new float[n];
            float[] smoothY = new float[n];
            float[] first = stroke.floatPoints.get(0);
            smoothX[0] = first[0];
            smoothY[0] = first[1];
            for (int i = 1; i < n - 1; i++) {
                float[] p0 = stroke.floatPoints.get(i - 1);
                float[] p1 = stroke.floatPoints.get(i);
                float[] p2 = stroke.floatPoints.get(i + 1);
                smoothX[i] = (p0[0] + p1[0] * 2f + p2[0]) / 4f;
                smoothY[i] = (p0[1] + p1[1] * 2f + p2[1]) / 4f;
            }
            float[] last = stroke.floatPoints.get(n - 1);
            smoothX[n - 1] = last[0];
            smoothY[n - 1] = last[1];
            long[] baseTs = stroke.getTimestamps();
            long start = (baseTs.length > 0) ? baseTs[0] : System.currentTimeMillis();
            long end = (baseTs.length > 0) ? baseTs[baseTs.length - 1] : start;
            long[] ts = interpolateTimestamps(smoothX, smoothY, start, end);
            float[] basePressures = stroke.getPressures();
            SwingUtilities.invokeLater(() -> replaceStrokePoints(stroke, smoothX, smoothY, basePressures, ts));
        }
        
        private long[] mapSampledTimestamps(float[] sampledX, float[] sampledY, float[] originalX, float[] originalY, long[] originalTs) {
            long start = originalTs.length > 0 ? originalTs[0] : System.currentTimeMillis();
            long end = originalTs.length > 0 ? originalTs[originalTs.length - 1] : start;
            return interpolateTimestamps(sampledX, sampledY, start, end);
        }
        
        private long[] interpolateTimestamps(float[] xs, float[] ys, long startTs, long endTs) {
            int n = xs.length;
            long[] result = new long[n];
            if (n == 0) return result;
            if (n == 1) {
                result[0] = startTs;
                return result;
            }
            double span = Math.max(1, endTs - startTs);
            for (int i = 0; i < n; i++) {
                double ratio = i / (double)(n - 1);
                result[i] = startTs + (long)(span * ratio);
            }
            return result;
        }

        private float[] resamplePressures(float[] pressures, int newCount) {
            if (pressures == null || pressures.length == 0 || newCount <= 0) return null;
            if (pressures.length == newCount) return pressures.clone();
            float[] out = new float[newCount];
            if (newCount == 1) {
                out[0] = pressures[0];
                return out;
            }
            int last = pressures.length - 1;
            for (int i = 0; i < newCount; i++) {
                float t = i * (last / (float) (newCount - 1));
                int idx = (int) Math.floor(t);
                int next = Math.min(last, idx + 1);
                float frac = t - idx;
                float v0 = pressures[idx];
                float v1 = pressures[next];
                out[i] = v0 + (v1 - v0) * frac;
            }
            return out;
        }
        
        private void replaceStrokePoints(DrawStroke stroke, float[] xs, float[] ys, float[] pressures, long[] ts) {
            stroke.floatPoints.clear();
            float[] resampledPressures = resamplePressures(pressures, xs.length);
            for (int i = 0; i < xs.length; i++) {
                float timestamp = (ts != null && i < ts.length) ? ts[i] : System.currentTimeMillis();
                float pressure = (resampledPressures != null && i < resampledPressures.length) ? resampledPressures[i] : DEFAULT_PRESSURE;
                stroke.addPointRaw(xs[i], ys[i], pressure, (long) timestamp);
            }
            stroke.invalidateCache();
            strokeBufferDirty = true;
            highlightBufferDirty = true;
            markNativeDirty(stroke.tool);
            repaint();
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
        private void appendPenSegment(DrawStroke stroke) {
            if (stroke == null || stroke.tool != DrawTool.PEN) return;
            if (strokeBufferDirty || penStrokeBuffer == null) {
                // Will be rebuilt on next paint; avoid drawing with stale offsets
                return;
            }
            try {
                int n = stroke.floatPoints.size();
                if (n == 0) return;
                Point vpPos = vp.getViewPosition();
                if (nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable()) {
                    int count = Math.min(3, n);
                    float[] xs = new float[count];
                    float[] ys = new float[count];
                    float[] thicknesses = new float[count];
                    for (int i = 0; i < count; i++) {
                        float[] p = stroke.floatPoints.get(n - count + i);
                        xs[i] = p[0];
                        ys[i] = p[1];
                        thicknesses[i] = stroke.thickness * clampPressure(p[2]);
                    }
                    int[] pixels = ((java.awt.image.DataBufferInt) penStrokeBuffer.getRaster().getDataBuffer()).getData();
                    boolean ok = nativeDrawing.strokeRenderVariable(pixels, penStrokeBuffer.getWidth(), penStrokeBuffer.getHeight(),
                            xs, ys, thicknesses, stroke.color.getRGB(), vpPos.x, vpPos.y);
                    if (ok) return;
                }
                Graphics2D g2 = penStrokeBuffer.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(-vpPos.x, -vpPos.y);
                g2.setColor(stroke.color);
                float lastPressure = stroke.floatPoints.get(n - 1)[2];
                g2.setStroke(new BasicStroke(stroke.thickness * clampPressure(lastPressure), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (n == 1) {
                    float[] p0 = stroke.floatPoints.get(0);
                    g2.fillOval(Math.round(p0[0] - 1), Math.round(p0[1] - 1), 3, 3);
                } else if (n == 2) {
                    float[] p0 = stroke.floatPoints.get(0);
                    float[] p1 = stroke.floatPoints.get(1);
                    g2.drawLine(Math.round(p0[0]), Math.round(p0[1]), Math.round(p1[0]), Math.round(p1[1]));
                } else if (n == 3) {
                    float[] p0 = stroke.floatPoints.get(0);
                    float[] p1 = stroke.floatPoints.get(1);
                    float[] p2 = stroke.floatPoints.get(2);
                    float mx = (p1[0] + p2[0]) * 0.5f;
                    float my = (p1[1] + p2[1]) * 0.5f;
                    Path2D.Float path = new Path2D.Float();
                    path.moveTo(p0[0], p0[1]);
                    path.quadTo(p1[0], p1[1], mx, my);
                    path.lineTo(p2[0], p2[1]);
                    g2.draw(path);
                } else {
                    float[] p0 = stroke.floatPoints.get(n - 3);
                    float[] p1 = stroke.floatPoints.get(n - 2);
                    float[] p2 = stroke.floatPoints.get(n - 1);
                    float mx1 = (p0[0] + p1[0]) * 0.5f;
                    float my1 = (p0[1] + p1[1]) * 0.5f;
                    float mx2 = (p1[0] + p2[0]) * 0.5f;
                    float my2 = (p1[1] + p2[1]) * 0.5f;
                    Path2D.Float path = new Path2D.Float();
                    path.moveTo(mx1, my1);
                    path.quadTo(p1[0], p1[1], mx2, my2);
                    g2.draw(path);
                }
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
        void shutdown() {
            try { strokeWorker.shutdownNow(); } catch (Throwable ignored) {}
            try { disableOptimizer(); } catch (Throwable ignored) {}
            try {
                if (nativeDrawing != null) {
                    if (nativePenManager != 0) {
                        nativeDrawing.strokeManagerDestroy(nativePenManager);
                        nativePenManager = 0;
                    }
                    if (nativeHighlightManager != 0) {
                        nativeDrawing.strokeManagerDestroy(nativeHighlightManager);
                        nativeHighlightManager = 0;
                    }
                }
            } catch (Throwable ignored) {}
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
            boolean erasedPen = false;
            boolean erasedHighlight = false;
            
            // Query nearby strokes when the optimizer is available
            for (DrawStroke s : queryStrokesNearPoint(p, r + 2)) {
                boolean shouldErase = false;
                
                // For pen strokes, check floatPoints (what's actually rendered)
                // For highlighter, check integer points
                if (!s.floatPoints.isEmpty()) {
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
                }
                
                if (shouldErase) {
                    drawStrokes.remove(s);
                    removeStrokeFromOptimizer(s);
                    erased.add(s);
                    if (s.tool == DrawTool.PEN) erasedPen = true;
                    else if (s.tool == DrawTool.HIGHLIGHT) erasedHighlight = true;
                }
            }
            
            if (!erased.isEmpty()) {
                redoStack.clear();
                undoStack.add(new EraseStrokesAction(erased));
                checkOptimizerThreshold();
                strokeBufferDirty = true;
                highlightBufferDirty = true;
                if (erasedPen) markNativeDirty(DrawTool.PEN);
                if (erasedHighlight) markNativeDirty(DrawTool.HIGHLIGHT);
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
            strokeToOptId.clear();
            optIdToStroke.clear();
            
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
            optIdToStroke.clear();
            useOptimizer = false;
        }
        
        private void addStrokeToOptimizer(DrawStroke s) {
            if (optimizerHandle == 0 || nativeDrawing == null) return;
            float[] xs = s.getPointsX();
            float[] ys = s.getPointsY();
            int optId = nativeDrawing.optimizerAddStroke(optimizerHandle, xs, ys, null,
                                                          s.color.getRGB(), s.thickness);
            if (optId >= 0) {
                strokeToOptId.put(s, optId);
                optIdToStroke.put(optId, s);
            }
        }
        
        private void removeStrokeFromOptimizer(DrawStroke s) {
            if (optimizerHandle == 0 || nativeDrawing == null) return;
            Integer optId = strokeToOptId.remove(s);
            if (optId != null) {
                nativeDrawing.optimizerRemoveStroke(optimizerHandle, optId);
                optIdToStroke.remove(optId);
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
                    DrawStroke stroke = optIdToStroke.get(optId);
                    if (stroke != null) result.add(stroke);
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
                moveSelectedItems(dx, dy);
                dragStart = p;
                if (!selectedStrokes.isEmpty()) {
                    strokeBufferDirty = true;
                    highlightBufferDirty = true;
                }
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
                selectItemsInLasso();
                lassoPath.clear();
            }
            repaint();
        }
        
        private void selectItemsInLasso() {
            selectedStrokes.clear();
            selectedImages.clear();
            float[] lassoX = new float[lassoPath.size()];
            float[] lassoY = new float[lassoPath.size()];
            for (int i = 0; i < lassoPath.size(); i++) {
                lassoX[i] = lassoPath.get(i).x;
                lassoY[i] = lassoPath.get(i).y;
            }
            
            for (DrawStroke s : drawStrokes) {
                float[] strokeX = s.getPointsX();
                float[] strokeY = s.getPointsY();
                
                // Use native lasso test if available, else Java fallback
                boolean selected = false;
                if (nativeDrawing != null && nativeDrawing.isLassoAvailable()) {
                    selected = nativeDrawing.lassoTestStroke(strokeX, strokeY, lassoX, lassoY, 0);
                } else {
                    selected = javaLassoTestStroke(strokeX, strokeY, lassoX, lassoY);
                }
                
                if (selected) selectedStrokes.add(s);
            }

            for (FloatingImage img : floatingImages) {
                if (img == null || img.width <= 0 || img.height <= 0) continue;
                if (lassoHitsRect(lassoX, lassoY, img.bounds())) {
                    selectedImages.add(img);
                }
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

        private boolean lassoHitsRect(float[] lassoX, float[] lassoY, java.awt.Rectangle rect) {
            if (rect == null || rect.width <= 0 || rect.height <= 0 || lassoX.length < 3) return false;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < lassoX.length; i++) {
                minX = Math.min(minX, lassoX[i]);
                minY = Math.min(minY, lassoY[i]);
                maxX = Math.max(maxX, lassoX[i]);
                maxY = Math.max(maxY, lassoY[i]);
            }
            if (rect.x > maxX || rect.y > maxY || rect.x + rect.width < minX || rect.y + rect.height < minY) {
                return false;
            }
            for (int i = 0; i < lassoX.length; i++) {
                if (rect.contains(lassoX[i], lassoY[i])) return true;
            }
            int rx = rect.x;
            int ry = rect.y;
            int rw = rect.width;
            int rh = rect.height;
            if (pointInPolygon(rx, ry, lassoX, lassoY)) return true;
            if (pointInPolygon(rx + rw, ry, lassoX, lassoY)) return true;
            if (pointInPolygon(rx, ry + rh, lassoX, lassoY)) return true;
            if (pointInPolygon(rx + rw, ry + rh, lassoX, lassoY)) return true;
            return false;
        }
        
        private void updateSelectionBounds() {
            if (selectedStrokes.isEmpty() && selectedImages.isEmpty()) {
                selectionBounds = null;
                return;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            boolean hasStrokeBounds = false;
            for (DrawStroke s : selectedStrokes) {
                java.awt.Rectangle bounds = computeStrokeBoundsNative(s);
                if (bounds.width <= 0 || bounds.height <= 0) continue;
                hasStrokeBounds = true;
                int x1 = bounds.x;
                int y1 = bounds.y;
                int x2 = bounds.x + bounds.width;
                int y2 = bounds.y + bounds.height;
                if (x1 < minX) minX = x1;
                if (y1 < minY) minY = y1;
                if (x2 > maxX) maxX = x2;
                if (y2 > maxY) maxY = y2;
            }
            for (FloatingImage img : selectedImages) {
                int x1 = img.x;
                int y1 = img.y;
                int x2 = img.x + img.width;
                int y2 = img.y + img.height;
                if (x1 < minX) minX = x1;
                if (y1 < minY) minY = y1;
                if (x2 > maxX) maxX = x2;
                if (y2 > maxY) maxY = y2;
            }
            if (!hasStrokeBounds && selectedImages.isEmpty()) {
                selectionBounds = null;
                return;
            }
            int pad = 8;
            selectionBounds = new java.awt.Rectangle(minX - pad, minY - pad, maxX - minX + 2*pad, maxY - minY + 2*pad);
        }
        
        private void moveSelectedItems(int dx, int dy) {
            boolean movedPen = false;
            boolean movedHighlight = false;
            for (DrawStroke s : selectedStrokes) {
                if (!translateStrokeNative(s, dx, dy)) {
                    for (float[] fp : s.floatPoints) {
                        fp[0] += dx;
                        fp[1] += dy;
                    }
                }
                s.invalidateCache();
                s.shiftBounds(dx, dy);
                // Sync with optimizer
                moveStrokeInOptimizer(s, dx, dy);
                if (s.tool == DrawTool.PEN) movedPen = true;
                else if (s.tool == DrawTool.HIGHLIGHT) movedHighlight = true;
            }
            for (FloatingImage img : selectedImages) {
                img.x += dx;
                img.y += dy;
            }
            if (selectionBounds != null) {
                selectionBounds.translate(dx, dy);
            }
            if (movedPen) markNativeDirty(DrawTool.PEN);
            if (movedHighlight) markNativeDirty(DrawTool.HIGHLIGHT);
        }
        
        private void clearSelection() {
            selectedStrokes.clear();
            selectedImages.clear();
            selectionBounds = null;
            lassoPath.clear();
        }

        private java.awt.Rectangle computeStrokeBoundsNative(DrawStroke s) {
            if (s == null || s.floatPoints.isEmpty()) return new java.awt.Rectangle();
            if (nativeDrawing != null && nativeDrawing.isLassoAvailable()) {
                float[] xs = s.getPointsX();
                float[] ys = s.getPointsY();
                if (xs.length > 0 && ys.length == xs.length) {
                    float[] bounds = nativeDrawing.lassoComputeBounds(xs, ys);
                    if (bounds != null && bounds.length == 4) {
                        int minX = (int) Math.floor(bounds[0]);
                        int minY = (int) Math.floor(bounds[1]);
                        int maxX = (int) Math.ceil(bounds[2]);
                        int maxY = (int) Math.ceil(bounds[3]);
                        return new java.awt.Rectangle(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
                    }
                }
            }
            return s.getBoundsWithPadding(0);
        }

        private boolean translateStrokeNative(DrawStroke s, int dx, int dy) {
            if (nativeDrawing == null || !nativeDrawing.isLassoAvailable() || s == null) return false;
            float[] xs = s.getPointsX();
            float[] ys = s.getPointsY();
            if (xs.length == 0 || ys.length != xs.length) return false;
            if (!nativeDrawing.lassoTranslatePoints(xs, ys, dx, dy)) return false;
            int n = xs.length;
            if (s.floatPoints.size() == n) {
                for (int i = 0; i < n; i++) {
                    float[] fp = s.floatPoints.get(i);
                    fp[0] = xs[i];
                    fp[1] = ys[i];
                }
            } else {
                float[] pressures = s.getPressures();
                long[] timestamps = s.getTimestamps();
                s.floatPoints.clear();
                for (int i = 0; i < n; i++) {
                    float pressure = (pressures != null && i < pressures.length) ? pressures[i] : DEFAULT_PRESSURE;
                    long ts = (timestamps != null && i < timestamps.length) ? timestamps[i] : System.currentTimeMillis();
                    s.floatPoints.add(new float[]{xs[i], ys[i], pressure, ts});
                }
            }
            return true;
        }
        
        // Get stroke bounding box clipped to viewport
        private java.awt.Rectangle getStrokeBounds(DrawStroke s, int offsetX, int offsetY, int viewW, int viewH) {
            if (s.floatPoints.isEmpty()) return new java.awt.Rectangle(0, 0, 0, 0);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (float[] p : s.floatPoints) {
                int px = Math.round(p[0]);
                int py = Math.round(p[1]);
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
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

            if (!floatingImages.isEmpty()) {
                Graphics2D ig = (Graphics2D) g.create();
                ig.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (FloatingImage img : floatingImages) {
                    if (img == null || img.image == null) continue;
                    int sx = img.x - (int) offsetX;
                    int sy = img.y - (int) offsetY;
                    if (sx + img.width < 0 || sy + img.height < 0 || sx > w || sy > h) continue;
                    ig.drawImage(img.image, sx, sy, img.width, img.height, null);
                }
                ig.dispose();
            }
            
            // Render pen strokes with caching
            if (strokeBufferDirty || penStrokeBuffer == null) {
                java.awt.Rectangle viewRect = new java.awt.Rectangle((int) offsetX, (int) offsetY, w, h);
                if (penStrokeBuffer == null || penStrokeBuffer.getWidth() != w || penStrokeBuffer.getHeight() != h) {
                    penStrokeBuffer = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                }

                boolean drew = false;
                if (nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable() && ensureNativeManager(DrawTool.PEN)) {
                    if (nativePenDirty) rebuildNativeManager(DrawTool.PEN);
                    if (nativePenManager != 0 && nativeDrawing.strokeCount(nativePenManager) > 0) {
                        int[] pixels = ((java.awt.image.DataBufferInt) penStrokeBuffer.getRaster().getDataBuffer()).getData();
                        java.util.Arrays.fill(pixels, 0);
                        drew = nativeDrawing.strokeRenderAll(nativePenManager, pixels, w, h, offsetX, offsetY);
                    }
                }
                if (!drew && nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable()) {
                    int[] pixels = ((java.awt.image.DataBufferInt) penStrokeBuffer.getRaster().getDataBuffer()).getData();
                    java.util.Arrays.fill(pixels, 0);
                    for (DrawStroke s : drawStrokes) {
                        if (s.tool != DrawTool.PEN || s.floatPoints.isEmpty()) continue;
                        if (!s.intersectsRect(viewRect, s.thickness + 6)) continue;
                        float[] xs = s.getPointsX();
                        float[] ys = s.getPointsY();
                        float[] thicknesses = getOrComputeThicknesses(s);
                        nativeDrawing.strokeRenderVariable(pixels, w, h, xs, ys, thicknesses, s.color.getRGB(), offsetX, offsetY);
                        drew = true;
                    }
                }
                if (!drew) {
                    // Java fallback with Path2D for better performance
                    Graphics2D g2 = penStrokeBuffer.createGraphics();
                    g2.setComposite(java.awt.AlphaComposite.Clear);
                    g2.fillRect(0, 0, w, h);
                    g2.setComposite(java.awt.AlphaComposite.SrcOver);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.translate(-offsetX, -offsetY);
                    for (DrawStroke s : drawStrokes) {
                        if (s.tool != DrawTool.PEN) continue;
                        if (!s.intersectsRect(viewRect, s.thickness + 6)) continue;
                        g2.setColor(s.color);
                        if (hasPressureVariation(s)) {
                            drawStrokePathVariable(g2, s);
                        } else {
                            g2.setStroke(new BasicStroke(s.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            drawStrokePath(g2, s.floatPoints);
                        }
                        drew = true;
                    }
                    g2.dispose();
                }

                if (!drew) {
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
                } else {
                    if (highlightStrokeBuffer == null || highlightStrokeBuffer.getWidth() != w || highlightStrokeBuffer.getHeight() != h) {
                        highlightStrokeBuffer = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    }
                    boolean drew = false;
                    java.awt.Rectangle viewRect = new java.awt.Rectangle((int) offsetX, (int) offsetY, w, h);
                    
                    if (nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable() && ensureNativeManager(DrawTool.HIGHLIGHT)) {
                        if (nativeHighlightDirty) rebuildNativeManager(DrawTool.HIGHLIGHT);
                        if (nativeHighlightManager != 0 && nativeDrawing.strokeCount(nativeHighlightManager) > 0) {
                            int[] pixels = ((java.awt.image.DataBufferInt) highlightStrokeBuffer.getRaster().getDataBuffer()).getData();
                            java.util.Arrays.fill(pixels, 0);
                            drew = nativeDrawing.strokeRenderAll(nativeHighlightManager, pixels, w, h, offsetX, offsetY);
                        }
                    }
                    if (!drew && nativeDrawing != null && nativeDrawing.isStrokeEngineAvailable()) {
                        int[] pixels = ((java.awt.image.DataBufferInt) highlightStrokeBuffer.getRaster().getDataBuffer()).getData();
                        java.util.Arrays.fill(pixels, 0);
                        for (DrawStroke s : drawStrokes) {
                            if (s.tool != DrawTool.HIGHLIGHT || s.floatPoints.isEmpty()) continue;
                            if (!s.intersectsRect(viewRect, s.thickness + 10)) continue;
                            float[] xs = s.getPointsX();
                            float[] ys = s.getPointsY();
                            if (xs.length == 0) continue;
                            float[] thicknesses = getOrComputeThicknesses(s);
                            if (thicknesses.length != xs.length) {
                                thicknesses = buildConstantThicknesses(xs.length, s.thickness);
                            }
                            nativeDrawing.strokeRenderVariable(pixels, w, h, xs, ys, thicknesses, s.color.getRGB(), offsetX, offsetY);
                            drew = true;
                        }
                    }
                    if (!drew) {
                        Graphics2D bufG = highlightStrokeBuffer.createGraphics();
                        bufG.setComposite(java.awt.AlphaComposite.Clear);
                        bufG.fillRect(0, 0, w, h);
                        bufG.setComposite(java.awt.AlphaComposite.SrcOver);
                        bufG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        bufG.translate(-offsetX, -offsetY);
                        
                        // Render each highlighter stroke with proper alpha compositing
                        for (DrawStroke s : drawStrokes) {
                            if (s.tool != DrawTool.HIGHLIGHT || s.floatPoints.isEmpty()) continue;
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
                            drawPointsPath(sg, s.floatPoints);
                            sg.dispose();
                            
                            // Composite to main highlighter buffer with alpha
                            bufG.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha / 255f));
                            bufG.drawImage(strokeBuf, bounds.x + (int)offsetX, bounds.y + (int)offsetY, null);
                            bufG.setComposite(java.awt.AlphaComposite.SrcOver);
                            drew = true;
                        }
                        bufG.dispose();
                    }
                    
                    if (!drew) {
                        highlightStrokeBuffer = null;
                    }
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
            int n = s.floatPoints.size();
            float[] thicknesses = new float[Math.max(1, n)];
            float baseT = s.thickness;
            if (s.tool == DrawTool.PEN) {
                float[] pressures = s.getPressures();
                if (pressures != null && pressures.length == n) {
                    for (int i = 0; i < n; i++) {
                        thicknesses[i] = baseT * clampPressure(pressures[i]);
                    }
                } else {
                    java.util.Arrays.fill(thicknesses, baseT);
                }
            } else {
                java.util.Arrays.fill(thicknesses, baseT);
            }
            s.setCachedThicknesses(thicknesses);
            return thicknesses;
        }

        private float clampPressure(float pressure) {
            if (pressure < 0.05f) return 0.05f;
            if (pressure > 1.6f) return 1.6f;
            return pressure;
        }

        private float[] buildConstantThicknesses(int count, float thickness) {
            if (count <= 0) return new float[0];
            float[] thicknesses = new float[count];
            java.util.Arrays.fill(thicknesses, thickness);
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
            for (int i = 1; i < n - 1; i++) {
                float[] p = floatPoints.get(i);
                float[] next = floatPoints.get(i + 1);
                float mx = (p[0] + next[0]) * 0.5f;
                float my = (p[1] + next[1]) * 0.5f;
                path.quadTo(p[0], p[1], mx, my);
            }
            float[] last = floatPoints.get(n - 1);
            path.lineTo(last[0], last[1]);
            g2.draw(path);
        }

        private boolean hasPressureVariation(DrawStroke stroke) {
            if (stroke == null || stroke.floatPoints.size() < 2) return false;
            float[] pressures = stroke.getPressures();
            if (pressures == null || pressures.length == 0) return false;
            float base = pressures[0];
            for (int i = 1; i < pressures.length; i++) {
                if (Math.abs(pressures[i] - base) > 0.08f) return true;
            }
            return false;
        }

        private void drawStrokePathVariable(Graphics2D g2, DrawStroke stroke) {
            List<float[]> pts = stroke.floatPoints;
            int n = pts.size();
            if (n == 0) return;
            if (n == 1) {
                float[] p = pts.get(0);
                float radius = stroke.thickness * clampPressure(p[2]) * 0.5f;
                g2.fillOval(Math.round(p[0] - radius), Math.round(p[1] - radius), Math.round(radius * 2f), Math.round(radius * 2f));
                return;
            }
            for (int i = 1; i < n; i++) {
                float[] p0 = pts.get(i - 1);
                float[] p1 = pts.get(i);
                float pressure = (p0[2] + p1[2]) * 0.5f;
                g2.setStroke(new BasicStroke(stroke.thickness * clampPressure(pressure), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(Math.round(p0[0]), Math.round(p0[1]), Math.round(p1[0]), Math.round(p1[1]));
            }
        }
        
        // Path2D drawing for float point list (highlighter)
        private void drawPointsPath(Graphics2D g2, List<float[]> points) {
            int n = points.size();
            if (n == 0) return;
            if (n == 1) {
                float[] p = points.get(0);
                g2.fillOval(Math.round(p[0] - 1), Math.round(p[1] - 1), 3, 3);
                return;
            }
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            float[] first = points.get(0);
            path.moveTo(first[0], first[1]);
            for (int i = 1; i < n - 1; i++) {
                float[] p = points.get(i);
                float[] next = points.get(i + 1);
                float mx = (p[0] + next[0]) * 0.5f;
                float my = (p[1] + next[1]) * 0.5f;
                path.quadTo(p[0], p[1], mx, my);
            }
            float[] last = points.get(n - 1);
            path.lineTo(last[0], last[1]);
            g2.draw(path);
        }
    }
}
