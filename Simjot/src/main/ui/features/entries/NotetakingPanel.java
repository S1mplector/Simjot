package main.ui.features.entries;

import main.ui.app.JournalApp;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.RichTextStyler;

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Notetaking editor: extends the standard EntryPanel with
 * enhanced image pasting (wider max width) and richer formatting shortcuts.
 */
public class NotetakingPanel extends EntryPanel {

    // --- Drawing overlay state ---
    private DrawingOverlay drawingOverlay;
    private boolean drawingEnabled = false;
    private DrawTool currentDrawTool = DrawTool.PEN;
    private final List<DrawStroke> drawStrokes = new ArrayList<>(); // persisted per-note
    private JPopupMenu drawToolMenu;
    private Color penColor = new Color(20,20,20,255);
    private Color highlightColor = new Color(255,235,59,120);
    private int penThickness = 3;
    private int highlightThickness = 18;
    private int eraserRadius = 18;
    private JSpinner strokeSpinner;
    private JButton colorBtn;
    private JLayeredPane overlayStack;

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
        JButton drawBtn = new main.ui.components.buttons.ToolbarIconButton("pencil");
        drawBtn.setToolTipText("Draw on (click to toggle, right-click to choose tool)");
        drawBtn.addActionListener(e -> {
            drawingEnabled = !drawingEnabled;
            if (drawingOverlay != null) drawingOverlay.setActive(drawingEnabled);
            drawBtn.setForeground(drawingEnabled ? new Color(0,120,215) : UIManager.getColor("Button.foreground"));
        });
        drawBtn.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    ensureDrawMenu();
                    drawToolMenu.show(drawBtn, 0, drawBtn.getHeight());
                }
            }
        });
        rightToolbar.add(drawBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));

        strokeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 64, 1));
        strokeSpinner.addChangeListener(e -> {
            int v = (int) strokeSpinner.getValue();
            if (currentDrawTool == DrawTool.PEN) penThickness = v;
            else if (currentDrawTool == DrawTool.HIGHLIGHT) highlightThickness = v;
            else if (currentDrawTool == DrawTool.ERASER) eraserRadius = v;
            if (drawingOverlay != null) drawingOverlay.repaint();
        });
        rightToolbar.add(strokeSpinner);
        rightToolbar.add(Box.createHorizontalStrut(6));

        colorBtn = new JButton();
        colorBtn.setPreferredSize(new Dimension(24, 24));
        colorBtn.setFocusPainted(false);
        colorBtn.addActionListener(e -> {
            Color base = JColorChooser.showDialog(NotetakingPanel.this, "Choose Color", currentDrawTool==DrawTool.HIGHLIGHT ? new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue()) : new Color(penColor.getRed(), penColor.getGreen(), penColor.getBlue()));
            if (base != null) {
                if (currentDrawTool == DrawTool.HIGHLIGHT) highlightColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 120);
                else if (currentDrawTool == DrawTool.PEN) penColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 255);
                updatePickersForCurrentTool();
            }
        });
        rightToolbar.add(colorBtn);
        rightToolbar.add(Box.createHorizontalStrut(6));

        JButton exportBtn = new JButton("Export");
        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem exportPng = new JMenuItem("Image (PNG)");
        JMenuItem exportPdf = new JMenuItem("PDF");
        exportPng.addActionListener(e -> exportSnapshotToImage());
        exportPdf.addActionListener(e -> exportSnapshotToPdf());
        exportMenu.add(exportPng);
        exportMenu.add(exportPdf);
        exportBtn.addActionListener(e -> exportMenu.show(exportBtn, 0, exportBtn.getHeight()));
        rightToolbar.add(exportBtn);

        updatePickersForCurrentTool();
    }

    private void ensureDrawMenu() {
        if (drawToolMenu != null) return;
        drawToolMenu = new JPopupMenu();
        JRadioButtonMenuItem pen = new JRadioButtonMenuItem("Pen", true);
        JRadioButtonMenuItem hi = new JRadioButtonMenuItem("Highlighter");
        JRadioButtonMenuItem er = new JRadioButtonMenuItem("Eraser");
        ButtonGroup g = new ButtonGroup(); g.add(pen); g.add(hi); g.add(er);
        pen.addActionListener(e -> currentDrawTool = DrawTool.PEN);
        hi.addActionListener(e -> currentDrawTool = DrawTool.HIGHLIGHT);
        er.addActionListener(e -> currentDrawTool = DrawTool.ERASER);
        ActionListener sync = e -> updatePickersForCurrentTool();
        pen.addActionListener(sync); hi.addActionListener(sync); er.addActionListener(sync);
        drawToolMenu.add(pen);
        drawToolMenu.add(hi);
        drawToolMenu.add(er);
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
        drawingOverlay.setActive(drawingEnabled);
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
        JFileChooser ch = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        ch.setDialogTitle("Export Snapshot");
        String base = (titleField != null && titleField.getText() != null && !titleField.getText().isBlank()) ? titleField.getText().trim().replaceAll("[^a-zA-Z0-9-_]+","_") : "note";
        ch.setSelectedFile(new File(base + "_snapshot.png"));
        if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = ch.getSelectedFile();
            try { javax.imageio.ImageIO.write(img, "png", out); } catch (IOException ignored) {}
        }
    }

    private void exportSnapshotToPdf() {
        java.awt.image.BufferedImage img = renderSnapshotImage();
        if (img == null) return;
        JFileChooser ch = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        ch.setDialogTitle("Export PDF");
        String base = (titleField != null && titleField.getText() != null && !titleField.getText().isBlank()) ? titleField.getText().trim().replaceAll("[^a-zA-Z0-9-_]+","_") : "note";
        ch.setSelectedFile(new File(base + "_snapshot.pdf"));
        if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = ch.getSelectedFile();
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.common.PDRectangle rect = new org.apache.pdfbox.pdmodel.common.PDRectangle(img.getWidth(), img.getHeight());
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(rect);
                doc.addPage(page);
                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImg = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImg, 0, 0, rect.getWidth(), rect.getHeight());
                }
                doc.save(out);
            } catch (IOException ignored) {}
        }
    }

    // --- Drawing model & overlay ---
    private enum DrawTool { PEN, HIGHLIGHT, ERASER }
    private static class DrawStroke {
        final DrawTool tool; final Color color; final int thickness; final List<Point> points;
        DrawStroke(DrawTool tool, Color color, int thickness) { this(tool,color,thickness,new ArrayList<>()); }
        DrawStroke(DrawTool tool, Color color, int thickness, List<Point> pts) { this.tool=tool; this.color=color; this.thickness=thickness; this.points=pts; }
    }

    private class DrawingOverlay extends JComponent {
        private final JScrollPane host;
        private final JViewport vp;
        private final JComponent view;
        private boolean active = false;
        private DrawStroke current;
        DrawingOverlay(JScrollPane host, JComponent view) {
            this.host = host;
            this.vp = host.getViewport();
            this.view = view;
            setOpaque(false);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!active || !SwingUtilities.isLeftMouseButton(e)) return;
                    Point p = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(p); return; }
                    current = makeStroke();
                    current.points.add(p);
                    drawStrokes.add(current);
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!active) return;
                    Point p = toDoc(e.getPoint());
                    if (currentDrawTool == DrawTool.ERASER) { eraseAt(p); return; }
                    if (current == null) return;
                    current.points.add(p);
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) { current = null; }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            vp.addChangeListener(e -> repaint());
            // Keep size in sync with content view
            view.addComponentListener(new ComponentAdapter(){
                @Override public void componentResized(ComponentEvent e) { revalidate(); repaint(); }
            });
        }
        void setActive(boolean on) {
            this.active = on;
            setVisible(on);
            setEnabled(on);
            setCursor(on ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
            repaint();
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
            boolean removed = false;
            java.util.Iterator<DrawStroke> it = drawStrokes.iterator();
            while (it.hasNext()) {
                DrawStroke s = it.next();
                List<Point> pts = s.points;
                for (int i = 1; i < pts.size(); i++) {
                    if (distPointToSegmentSq(p, pts.get(i - 1), pts.get(i)) <= r2) { it.remove(); removed = true; break; }
                }
            }
            if (removed) repaint();
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
        @Override public Dimension getPreferredSize() { return view.getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Point vpPos = vp.getViewPosition();
            g2.translate(-vpPos.x, -vpPos.y);
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
        }
    }
}
