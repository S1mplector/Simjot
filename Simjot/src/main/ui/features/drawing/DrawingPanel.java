package main.ui.features.drawing;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.ui.app.JournalApp;
import main.ui.dialog.input.CustomInputDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.message.UIMessage;

/**
 * A modern infinite drawing panel with layers, panning, zooming, and more.
 */
public class DrawingPanel extends JPanel {

    private int offsetX = 0, offsetY = 0;
    private double zoom = 1.0;

    private final JournalApp app;
    private Point mousePos = null;

    private Tool currentTool = Tool.PENCIL;
    private Color currentColor = Color.BLACK;

    private int pencilSize = SettingsStore.get().getDefaultBrushSize();
    private int eraserSize = 5;

    private final List<DrawingLayer> layers = new ArrayList<>();
    private DrawingLayer activeLayer;
    private Stroke currentStroke = null;

    private Point panStart = null;
    private BufferedImage backgroundMap = null;
    private String backgroundMapPath = "none";

    private final JComboBox<Tool> toolSelector;
    private final IconButton eraserButton;
    private final JSlider sizeSlider;
    private final JLabel sizeLabel;

    private final JList<DrawingLayer> layerList;
    private final DefaultListModel<DrawingLayer> layerListModel;

    private final ColorPopup colorPopup;
    private final BrushPreviewPopup previewPopup;

    public DrawingPanel(JournalApp app) {
        super(new BorderLayout());
        this.app = app;
        setBackground(Color.WHITE);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBackground(new Color(230, 230, 230));
        add(toolbar, BorderLayout.NORTH);

        toolbar.add(new JLabel("Brush:"));
        Tool[] brushes = { Tool.PENCIL, Tool.LINEART, Tool.CALLIGRAPHY, Tool.HIGHLIGHTER, Tool.WATERCOLOR, Tool.RAINBOW };
        toolSelector = new JComboBox<>(brushes);
        toolSelector.setRenderer(new ModernComboBoxRenderer());
        toolSelector.setUI(new ModernComboBoxUI());
        toolSelector.addActionListener(e -> setTool((Tool) toolSelector.getSelectedItem()));
        toolbar.add(toolSelector);
        
        eraserButton = new IconButton("img/eraser.png");
        eraserButton.setToolTipText("Eraser");
        eraserButton.addActionListener(e -> setTool(Tool.ERASER));
        toolbar.add(eraserButton);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        sizeLabel = new JLabel("Pencil:");
        toolbar.add(sizeLabel);
        sizeSlider = new JSlider(1, 50, pencilSize);
        sizeSlider.setPreferredSize(new Dimension(100, 24));
        sizeSlider.setUI(new ModernSliderUI());
        sizeSlider.setBackground(new Color(230,230,230));
        sizeSlider.addChangeListener(e -> {
            if (currentTool == Tool.PENCIL || currentTool == Tool.CALLIGRAPHY || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.WATERCOLOR || currentTool == Tool.RAINBOW) {
                pencilSize = sizeSlider.getValue();
            } else if (currentTool == Tool.ERASER) {
                eraserSize = sizeSlider.getValue();
                repaint();
            }
        });
        toolbar.add(sizeSlider);
        
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        IconButton clearButton = new IconButton("img/clear.png");
        clearButton.setToolTipText("Clear Canvas");
        clearButton.addActionListener(e -> {
            if (activeLayer != null) {
                activeLayer.clear();
                repaint();
            }
        });
        toolbar.add(clearButton);

        JButton setBackgroundButton = new ModernTextButton("Set Bkg");
        setBackgroundButton.setToolTipText("Set Background Image");
        setBackgroundButton.addActionListener(e -> loadBackgroundImage());
        toolbar.add(setBackgroundButton);

        JButton loadMapButton = new ModernTextButton("Load");
        loadMapButton.setToolTipText("Load Drawing (.mydraw)");
        loadMapButton.addActionListener(e -> loadDrawingFromCustomFile());
        toolbar.add(loadMapButton);

        JButton saveButton = new ModernTextButton("Save");
        saveButton.setToolTipText("Save Drawing (Custom Format)");
        saveButton.addActionListener(e -> saveDrawingAsCustomFile());
        toolbar.add(saveButton);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        JButton backButton = new ModernTextButton("Back");
        backButton.setToolTipText("Back to Main Menu");
        backButton.addActionListener(e -> this.app.switchCard(JournalApp.MAIN_MENU));
        toolbar.add(backButton);

        colorPopup = new ColorPopup();

        layerListModel = new DefaultListModel<>();
        layerList = new JList<>(layerListModel);
        layerList.setCellRenderer(new LayerCellRenderer());
        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DrawingLayer selected = layerList.getSelectedValue();
                if (selected != null) {
                    activeLayer = selected;
                }
            }
        });
        
        layerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = layerList.locationToIndex(e.getPoint());
                if (index != -1) {
                    Rectangle bounds = layerList.getCellBounds(index, index);
                    if (e.getX() < bounds.x + 30) { // click on checkbox
                        DrawingLayer layer = layerListModel.getElementAt(index);
                        layer.setVisible(!layer.isVisible());
                        layerList.repaint(bounds);
                        DrawingPanel.this.repaint();
                    } else if (e.getClickCount() == 2) { // double-click elsewhere => rename
                        DrawingLayer layer = layerListModel.getElementAt(index);
                        String newName = CustomInputDialog.prompt(DrawingPanel.this, "Rename Layer", "Enter new name:", layer.getName());
                        if (newName != null && !newName.trim().isEmpty()) {
                            layer.setName(newName.trim());
                            layerList.repaint(bounds);
                        }
                    }
                }
            }
        });

        JButton addLayerButton = new ModernTextButton("+");
        addLayerButton.setPreferredSize(new Dimension(26,22));
        addLayerButton.addActionListener(e -> addNewLayer());
        JButton delLayerButton = new ModernTextButton("-");
        delLayerButton.setPreferredSize(new Dimension(26,22));
        delLayerButton.addActionListener(e -> deleteSelectedLayer());
        JPanel layerButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        layerButtons.setOpaque(false);
        layerButtons.add(addLayerButton);
        layerButtons.add(delLayerButton);

        JPanel layerPanel = new JPanel(new BorderLayout());
        layerPanel.setBackground(new Color(245,245,245));
        layerPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        layerPanel.add(new JScrollPane(layerList), BorderLayout.CENTER);
        layerPanel.add(layerButtons, BorderLayout.SOUTH);
        layerPanel.setPreferredSize(new Dimension(200, 0));
        layerList.setBackground(new Color(245,245,245));
        layerList.setSelectionBackground(new Color(0,120,215,80));
        layerList.setSelectionForeground(Color.DARK_GRAY);
        layerList.setBorder(null);
        add(layerPanel, BorderLayout.EAST);
        
        addNewLayer();
        updateToolbar();

        DrawingMouseAdapter dma = new DrawingMouseAdapter();
        addMouseListener(dma);
        addMouseMotionListener(dma);

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == '+') { zoom *= 1.1; repaint(); } 
                else if (e.getKeyChar() == '-') { zoom /= 1.1; repaint(); }
            }
        });

        // Brush preview on hover over the combo box
        previewPopup = new BrushPreviewPopup();
        toolSelector.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Point onScreen = toolSelector.getLocationOnScreen();
                previewPopup.setTool(currentTool);
                previewPopup.showAt(onScreen.x, onScreen.y + toolSelector.getHeight() + 4);
            }
            @Override public void mouseExited(MouseEvent e) { previewPopup.setVisible(false); }
        });
    }

    private void setTool(Tool t) {
        currentTool = t;
        updateToolbar();
    }
    
    private void updateToolbar() {
        if (currentTool != Tool.ERASER) {
            toolSelector.setSelectedItem(currentTool);
        }
        eraserButton.setSelected(currentTool == Tool.ERASER);

        if (currentTool == Tool.PENCIL) {
            sizeLabel.setText("Pencil:");
            sizeSlider.setValue(pencilSize);
        } else if (currentTool == Tool.ERASER) {
            sizeLabel.setText("Eraser:");
            sizeSlider.setValue(eraserSize);
        } else if (currentTool == Tool.CALLIGRAPHY) {
            sizeLabel.setText("Nib:");
            sizeSlider.setValue(pencilSize);
        } else if (currentTool == Tool.HIGHLIGHTER) {
            sizeLabel.setText("Width:");
            sizeSlider.setValue(pencilSize);
        } else if (currentTool == Tool.WATERCOLOR) {
            sizeLabel.setText("Size:");
            sizeSlider.setValue(pencilSize);
        } else if (currentTool == Tool.LINEART) {
            sizeLabel.setText("Width:");
            sizeSlider.setValue(pencilSize);
        } else if (currentTool == Tool.RAINBOW) {
            sizeLabel.setText("Width:");
            sizeSlider.setValue(pencilSize);
        }
    }

    private void loadBackgroundImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Background Image");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            backgroundMapPath = selected.getAbsolutePath();
            try {
                backgroundMap = ImageIO.read(selected);
                repaint();
            } catch (IOException ex) {
                UIMessage.error(this,
                        "Error Loading Image",
                        "We couldn't open that image.",
                        "Pick a different image file (PNG/JPG) and try again.",
                        ex);
            }
        }
    }

    /**
     * Loads a drawing from a .mydraw file, replacing current layers/background.
     */
    public void loadFromFile(File loadFile){
        if(loadFile==null || !loadFile.exists()) return;
        try (BufferedReader in = new BufferedReader(new FileReader(loadFile))) {
            layers.clear();
            layerListModel.clear();
            backgroundMap = null;
            backgroundMapPath = "none";
            String line;
            DrawingLayer currentParseLayer = null;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("BACKGROUND_MAP:")) {
                    backgroundMapPath = line.substring("BACKGROUND_MAP:".length());
                    if (!backgroundMapPath.equals("none")) {
                        try { backgroundMap = javax.imageio.ImageIO.read(new java.io.File(backgroundMapPath)); } 
                        catch (IOException ex) { backgroundMap = null; }
                    }
                } else if (line.equals("LAYER_START")) {
                    String name = in.readLine().substring("NAME:".length());
                    currentParseLayer = new DrawingLayer(name);
                    currentParseLayer.setVisible(Boolean.parseBoolean(in.readLine().substring("VISIBLE:".length())));
                    layers.add(currentParseLayer);
                    layerListModel.addElement(currentParseLayer);
                } else if (line.equals("STROKE_START") && currentParseLayer != null) {
                    Tool tool = Tool.valueOf(in.readLine().substring("TOOL:".length()));
                    String[] rgb = in.readLine().substring("COLOR:".length()).split(",");
                    Color color = new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
                    int thickness = Integer.parseInt(in.readLine().substring("THICKNESS:".length()));
                    Stroke stroke = new Stroke(tool, color, thickness);
                    int pointCount = Integer.parseInt(in.readLine().substring("POINT_COUNT:".length()));
                    for (int i = 0; i < pointCount; i++) {
                        String[] xy = in.readLine().split(",");
                        stroke.addPoint(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), 0);
                    }
                    in.readLine();
                    currentParseLayer.addStroke(stroke);
                }
            }
            if (!layers.isEmpty()) layerList.setSelectedIndex(layers.size() - 1);
            repaint();
        } catch (Exception ex) {
            UIMessage.error(this,
                    "Load Failed",
                    "Couldn't load the drawing file.",
                    "Choose a valid .mydraw file from the Drawings folder and try again.",
                    ex);
        }
    }

    private void loadDrawingFromCustomFile() {
        java.io.File dir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
        java.io.File chosen = DrawingChooserDialog.chooseDrawing(this, dir);
        if(chosen!=null){ loadFromFile(chosen); }
    }

    private void saveDrawingAsCustomFile() {
        // Auto-save into the dedicated drawings directory inside the Simjot root
        File drawingsDir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
        String filename = "drawing_" + sdf.format(new java.util.Date()) + ".mydraw";
        File saveFile = new File(drawingsDir, filename);

        try (PrintWriter out = new PrintWriter(new FileWriter(saveFile))) {
            out.println("BACKGROUND_MAP:" + backgroundMapPath);
            for (DrawingLayer layer : layers) {
                out.println("LAYER_START");
                out.println("NAME:" + layer.getName());
                out.println("VISIBLE:" + layer.isVisible());
                for (Stroke s : layer.getStrokes()) {
                    out.println("STROKE_START");
                    out.println("TOOL:" + s.getTool());
                    Color c = s.getColor();
                    out.println("COLOR:" + c.getRed() + "," + c.getGreen() + "," + c.getBlue());
                    out.println("THICKNESS:" + s.getThickness());
                    List<Point> pathPoints = s.getPointList();
                    out.println("POINT_COUNT:" + pathPoints.size());
                    for (Point p : pathPoints) out.println(p.x + "," + p.y);
                    out.println("STROKE_END");
                }
                out.println("LAYER_END");
            }
        } catch (IOException ex) {
            CustomMessageDialog.display(this, "Save", "Failed to save drawing.", true);
            return;
        }

        if(SettingsStore.get().isThumbnailGeneration()){
            try {
                java.awt.image.BufferedImage thumb = renderThumbnail(200);
                javax.imageio.ImageIO.write(thumb, "png", new File(drawingsDir, filename.replace(".mydraw", ".png")));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Inform user and maybe refresh gallery if open
        CustomMessageDialog.display(this, "Save", "Drawing saved successfully!", false);

        // Notify app to refresh gallery list (if available)
        //if(app!=null) app.refreshGallery();
    }

    /**
     * Renders current drawing (visible layers) onto a square thumbnail image.
     */
    private BufferedImage renderThumbnail(int size){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // White background
        g2.setColor(Color.WHITE);
        g2.fillRect(0,0,size,size);

        // Determine bounding box of strokes to scale appropriately (naive: assume 800x600 canvas)
        double scale = Math.min((double)size / getWidth(), (double)size / getHeight());
        g2.scale(scale, scale);

        // Draw optional background map scaled
        if(backgroundMap!=null){
            g2.drawImage(backgroundMap, 0,0, this);
        }
        // Draw strokes
        for (DrawingLayer layer : layers) {
            if (layer.isVisible()) {
                // Fully opaque draw for visible layers
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                for (Stroke s : layer.getStrokes()) {
                    s.draw(g2);
                }
            }
        }
        g2.dispose();
        return img;
    }

    private class DrawingMouseAdapter extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (SwingUtilities.isMiddleMouseButton(e)) {
                colorPopup.showAt(e.getXOnScreen(), e.getYOnScreen());
                return;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
                panStart = e.getPoint();
                return;
            }
            if (activeLayer == null) return;
            int size = (currentTool == Tool.PENCIL || currentTool == Tool.LINEART || currentTool == Tool.CALLIGRAPHY || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.WATERCOLOR || currentTool == Tool.RAINBOW) ? pencilSize : eraserSize;
            currentStroke = new Stroke(currentTool, currentColor, size);
            activeLayer.addStroke(currentStroke);
            Point p = toWorldCoords(e.getPoint());
            currentStroke.addPoint(p.x, p.y, 0);
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            mousePos = e.getPoint();
            if (SwingUtilities.isRightMouseButton(e)) {
                if (panStart != null) {
                    offsetX += e.getX() - panStart.x;
                    offsetY += e.getY() - panStart.y;
                    panStart = e.getPoint();
                    repaint();
                }
                return;
            }
            if (currentStroke != null) {
                Point p = toWorldCoords(e.getPoint());
                Point last = currentStroke.getLastPoint();
                double angle = (last != null) ? Math.atan2(p.y - last.y, p.x - last.x) : 0;
                // For smoother strokes, interpolate if distance between points > 3 px
                if(last!=null){
                    double dist = last.distance(p);
                    int steps = (int)dist; // ~1px resolution for smoother path
                    if(steps>0){
                        double dx = (p.x-last.x)/(double)steps;
                        double dy = (p.y-last.y)/(double)steps;
                        for(int s=1;s<=steps;s++){
                            int ix = (int)(last.x + dx*s);
                            int iy = (int)(last.y + dy*s);
                            currentStroke.addPoint(ix, iy, angle);
                        }
                    } else {
                        currentStroke.addPoint(p.x, p.y, angle);
                    }
                } else {
                    currentStroke.addPoint(p.x, p.y, angle);
                }
                repaint();
            }
        }

        @Override public void mouseMoved(MouseEvent e) { mousePos = e.getPoint(); if (currentTool == Tool.ERASER) repaint(); }
        @Override public void mouseExited(MouseEvent e) { mousePos = null; if (currentTool == Tool.ERASER) repaint(); }
        @Override public void mouseReleased(MouseEvent e) { if (SwingUtilities.isRightMouseButton(e)) panStart = null; else currentStroke = null; }
    }

    private Point toWorldCoords(Point screen) {
        return new Point((int)((screen.x - offsetX) / zoom), (int)((screen.y - offsetY) / zoom));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage strokeImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D strokeG = strokeImage.createGraphics();
        strokeG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        strokeG.translate(offsetX, offsetY);
        strokeG.scale(zoom, zoom);
        for (DrawingLayer layer : layers) {
            if (layer.isVisible()) {
                // Fully opaque draw for visible layers
                strokeG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                for (Stroke s : layer.getStrokes()) {
                    s.draw(strokeG);
                }
            }
        }
        strokeG.dispose();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(offsetX, offsetY);
        g2.scale(zoom, zoom);
        if (backgroundMap != null) g2.drawImage(backgroundMap, 0, 0, this);
        g2.dispose();
        g.drawImage(strokeImage, 0, 0, this);
        if (currentTool == Tool.ERASER && mousePos != null) {
            g.setColor(Color.DARK_GRAY);
            int r = (int) (eraserSize * zoom / 2);
            g.drawOval(mousePos.x - r, mousePos.y - r, r * 2, r * 2);
        }
    }

    private void addNewLayer() {
        DrawingLayer newLayer = new DrawingLayer("Layer " + (layers.size() + 1));
        layers.add(newLayer);
        layerListModel.addElement(newLayer);
        layerList.setSelectedValue(newLayer, true);
        activeLayer = newLayer;
    }

    private void deleteSelectedLayer() {
        DrawingLayer selectedLayer = layerList.getSelectedValue();
        if (selectedLayer != null && layers.size() > 1) {
            layers.remove(selectedLayer);
            layerListModel.removeElement(selectedLayer);
            if (!layers.isEmpty()) layerList.setSelectedIndex(0);
            repaint();
        }
    }

    // --- Inner Classes ---
    private enum Tool { PENCIL, LINEART, CALLIGRAPHY, ERASER, HIGHLIGHTER, WATERCOLOR, RAINBOW }

    private static class Stroke {
        private final Tool tool;
        private final Color color;
        private final int thickness;
        private final List<Point> points = new ArrayList<>();
        private final Path2D path = new Path2D.Float();
        private final List<Shape> calligraphyShapes = new ArrayList<>();
        private final float rainbowSeed;
        public Stroke(Tool tool, Color color, int thickness) {
            this.tool = tool;
            this.color = color;
            this.thickness = thickness;
            this.rainbowSeed = (float)Math.random();
        }
        public void addPoint(int x, int y, double angle) {
            points.add(new Point(x,y));
            if (tool == Tool.PENCIL || tool == Tool.LINEART || tool == Tool.ERASER || tool == Tool.HIGHLIGHTER) {
                if (points.size() == 1) {
                    path.moveTo(x, y);
                } else {
                    Point prev = points.get(points.size()-2);
                    int midX = (prev.x + x)/2;
                    int midY = (prev.y + y)/2;
                    path.quadTo(prev.x, prev.y, midX, midY);
                }
            } else if (tool == Tool.CALLIGRAPHY) {
                if (points.size() < 2) return;
                Point p1 = points.get(points.size() - 2);
                Point p2 = points.get(points.size() - 1);
                final double nibAngle = Math.toRadians(-45);
                double angleDiff = Math.abs(angle - nibAngle);
                double widthFactor = Math.sin(angleDiff);
                double angularWidth = (thickness * 0.2) + (thickness * 0.8) * widthFactor;
                double dist = p1.distance(p2);
                double speedMultiplier = 0.6 + ( (1.0 - Math.min(1.0, (dist - 1.0) / 19.0)) * 0.5 );
                double finalWidth = angularWidth * speedMultiplier;
                int steps = Math.max(1, (int) (dist / 2.0));
                for (int i = 0; i < steps; i++) {
                    float t = (float) i / steps;
                    Point int_p1 = new Point((int)(p1.x * (1-t) + p2.x * t), (int)(p1.y * (1-t) + p2.y * t));
                    float t_next = (float) (i + 1) / steps;
                    Point int_p2 = new Point((int)(p1.x * (1-t_next) + p2.x * t_next), (int)(p1.y * (1-t_next) + p2.y * t_next));
                    Line2D.Float segment = new Line2D.Float(int_p1, int_p2);
                    calligraphyShapes.add(new BasicStroke((float)finalWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(segment));
                }
            } else if (tool == Tool.WATERCOLOR) {
                int drops = 25;
                float spread = thickness * 1.5f;
                Random rand = new Random();
                for (int i=0;i<drops;i++) {
                    float dx = (rand.nextFloat()-0.5f)*spread;
                    float dy = (rand.nextFloat()-0.5f)*spread;
                    float radius = thickness/2f + rand.nextFloat()* (thickness);
                    calligraphyShapes.add(new Ellipse2D.Float(x+dx-radius/2, y+dy-radius/2, radius, radius));
                }
            } else if (tool == Tool.RAINBOW) {
                if (points.size() == 1) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
        }
        public void draw(Graphics2D g2) {
            g2.setColor(color);
            if (tool == Tool.ERASER) g2.setComposite(AlphaComposite.Clear);
            else g2.setComposite(AlphaComposite.SrcOver);
            if (tool == Tool.PENCIL || tool == Tool.LINEART || tool == Tool.ERASER || tool == Tool.HIGHLIGHTER) {
                g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (tool == Tool.HIGHLIGHTER) {
                    Color hi = new Color(color.getRed(), color.getGreen(), color.getBlue(), 90);
                    g2.setColor(hi);
                    g2.draw(path);
                } else {
                    g2.draw(path);
                }
            } else if (tool == Tool.CALLIGRAPHY) {
                for (Shape s : calligraphyShapes) g2.fill(s);
            } else if (tool == Tool.WATERCOLOR) {
                Color col = new Color(color.getRed(), color.getGreen(), color.getBlue(), 80);
                g2.setColor(col);
                for (Shape s : calligraphyShapes) g2.fill(s);
            } else if (tool == Tool.RAINBOW) {
                g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float hue = rainbowSeed;
                for(int i=1;i<points.size();i++){
                    Point p0 = points.get(i-1);
                    Point p1 = points.get(i);
                    g2.setColor(Color.getHSBColor(hue,1f,1f));
                    g2.drawLine(p0.x,p0.y,p1.x,p1.y);
                    hue += 0.01f; if(hue>1f) hue -=1f;
                }
            }
        }
        public Tool getTool() { return tool; }
        public Color getColor() { return color; }
        public int getThickness() { return thickness; }
        public List<Point> getPointList() { return points; }
        public Point getLastPoint() { return points.isEmpty() ? null : points.get(points.size() - 1); }
        public float getOpacity() { return 1f; }
        public void setOpacity(float o) { }
    }

    private class DrawingLayer {
        private String name;
        private boolean isVisible = true;
        private final List<Stroke> strokes = new ArrayList<>();
        public DrawingLayer(String name) { this.name = name; }
        public String getName() { return name; }
        public boolean isVisible() { return isVisible; }
        public void setVisible(boolean visible) { isVisible = visible; }
        public List<Stroke> getStrokes() { return strokes; }
        public void addStroke(Stroke s) { strokes.add(s); }
        public void clear() { strokes.clear(); }
        public void setName(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private class LayerCellRenderer extends JPanel implements ListCellRenderer<DrawingLayer> {
        private final JLabel label = new JLabel();
        private final JCheckBox visibilityToggle = new JCheckBox();
        public LayerCellRenderer() {
            super(new BorderLayout(5, 5));
            visibilityToggle.setOpaque(false);
            add(visibilityToggle, BorderLayout.WEST); add(label, BorderLayout.CENTER); setOpaque(true);
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends DrawingLayer> list, DrawingLayer value, int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(value.getName());
            visibilityToggle.setSelected(value.isVisible());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

    private class ColorPopup extends JWindow {
        public ColorPopup() {
            setLayout(new BorderLayout());
            JPanel palettePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            palettePanel.setBackground(new Color(240, 240, 240));

            setBackground(new Color(240, 240, 240));
            Color[] palette = { Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.GRAY, Color.CYAN, Color.WHITE, Color.YELLOW, Color.PINK, new Color(139, 69, 19) };
            for(Color c : palette) {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(30,30));
                b.setBackground(c);
                b.addActionListener(e -> { currentColor = c; setVisible(false); });
                palettePanel.add(b);
            }
            
            JButton moreButton = new JButton("More...");
            moreButton.addActionListener(e -> {
                setVisible(false);
                Color newColor = javax.swing.JColorChooser.showDialog(DrawingPanel.this, "Choose Brush Color", currentColor);
                if (newColor != null) {
                    currentColor = newColor;
                }
            });

            add(palettePanel, BorderLayout.CENTER);
            add(moreButton, BorderLayout.SOUTH);
            pack();
        }
        public void showAt(int screenX, int screenY) { setLocation(screenX, screenY); setVisible(true); }
    }

    private static class IconButton extends JButton {
        private boolean isSelected = false;
        private String id;
        private ImageIcon icon;
        public IconButton(String iconPath) {
            super();

            // Derive simple id from file name (used for vector fallback / selection)
            String nameTmp = iconPath;
            int slash = nameTmp.lastIndexOf('/') ;
            if (slash != -1) nameTmp = nameTmp.substring(slash + 1);
            if (nameTmp.toLowerCase().endsWith(".png")) nameTmp = nameTmp.substring(0, nameTmp.length() - 4);
            this.id = nameTmp.toLowerCase();

            // Always use vector artwork for certain icons regardless of bitmap presence
            boolean forceVector = id.equals("eraser") || id.equals("clear");

            if (!forceVector) {
                // Attempt to load icon from resources – works from both jar & filesystem.
                this.icon = ResourceLoader.createImageIcon(iconPath.startsWith("Simjot/") ? iconPath.substring("Simjot/".length()) : iconPath);
                if (this.icon != null) {
                    setIcon(this.icon);
                }
            } else {
                this.icon = null; // ensure vector routine triggers
            }
             
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setPreferredSize(new Dimension(40, 40));
        }
        public void setSelected(boolean selected) { this.isSelected = selected; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw button background depending on state
            if (isSelected) g2.setColor(new Color(0, 120, 215, 80));
            else if (getModel().isPressed()) g2.setColor(new Color(200, 200, 200));
            else if (getModel().isRollover()) g2.setColor(new Color(220, 220, 220));
            else g2.setColor(getParent() != null ? getParent().getBackground() : Color.LIGHT_GRAY);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            // Decide whether to draw bitmap icon or vector fallback.
            boolean drewIcon = false;
            if (icon != null && icon.getIconWidth() > 0) {
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
                drewIcon = true;
            }

            if (!drewIcon) {
                // Vector fallback drawing for missing resources
                int w = getWidth();
                int h = getHeight();
                int cx = w / 2;
                int cy = h / 2;
                switch (id) {
                    case "eraser": {
                        // Draw a tilted eraser (simple rectangle rotated 45°)
                        g2.setColor(new Color(255, 182, 193)); // light pink
                        AffineTransform old = g2.getTransform();
                        g2.translate(cx, cy);
                        g2.rotate(Math.toRadians(-35));
                        int ew = 18, eh = 10;
                        g2.fillRoundRect(-ew/2, -eh/2, ew, eh, 3, 3);
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawRoundRect(-ew/2, -eh/2, ew, eh, 3, 3);
                        g2.setTransform(old);
                        break;
                    }
                    case "clear": {
                        // Draw a simple trash bin icon
                        g2.setColor(Color.DARK_GRAY);
                        int binW = 14, binH = 16;
                        int x = cx - binW/2;
                        int y = cy - binH/2 + 2;
                        g2.drawRoundRect(x, y, binW, binH, 2, 2);
                        // lid
                        g2.fillRect(x - 2, y - 4, binW + 4, 3);
                        // handle
                        g2.drawLine(cx - 4, y - 6, cx + 4, y - 6);
                        break;
                    }
                    default: {
                        // Generic placeholder if specific icon missing
                        g2.setColor(Color.DARK_GRAY);
                        int s = 12;
                        g2.drawLine(cx - s/2, cy - s/2, cx + s/2, cy + s/2);
                        g2.drawLine(cx + s/2, cy - s/2, cx - s/2, cy + s/2);
                    }
                }
            }

            // Border
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
    }

    private static class ModernTextButton extends JButton {
        public ModernTextButton(String text) {
            super(text);
            setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
            setForeground(Color.DARK_GRAY); setFont(new Font("SansSerif", Font.BOLD, 12));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isPressed()) g2.setColor(new Color(200, 200, 200));
            else if (getModel().isRollover()) g2.setColor(new Color(220, 220, 220));
            else g2.setColor(new Color(240, 240, 240));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(getForeground());
            FontMetrics metrics = g2.getFontMetrics(getFont());
            g2.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2, ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent());
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
    }

    private static class ModernComboBoxUI extends BasicComboBoxUI {
        @Override protected JButton createArrowButton() { return new JButton() {{ setVisible(false); }}; }
        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {}
        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(comboBox.isPopupVisible() ? new Color(220,220,220) : new Color(240,240,240));
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 10, 10);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
            if (comboBox.getSelectedItem() != null) {
                g2.setColor(c.getForeground());
                g2.setFont(c.getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(comboBox.getSelectedItem().toString(), 5, fm.getAscent() + (c.getHeight() - fm.getHeight()) / 2);
            }
            g2.setColor(Color.DARK_GRAY);
            int x = c.getWidth() - 15, y = c.getHeight()/2 - 2;
            g2.fillPolygon(new int[]{x, x + 5, x + 10}, new int[]{y, y + 5, y}, 3);
            g2.dispose();
        }
    }

    private static class ModernComboBoxRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setBackground(isSelected ? new Color(0, 120, 215, 80) : Color.WHITE);
            setBorder(new EmptyBorder(5, 5, 5, 5));
            return this;
        }
    }

    private static class ModernSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        private final Color trackColor = new Color(200,200,200);
        private final Color fillColor = new Color(0,120,215);
        private final Color thumbBorder = new Color(120,120,120);
        public ModernSliderUI() { super(new JSlider()); }
        @Override protected Dimension getThumbSize() { return new Dimension(12,14); }
        @Override public void paintTrack(Graphics g) {
            Graphics2D g2=(Graphics2D)g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int y = trackRect.y + trackRect.height/2 - 2;
            g2.setColor(trackColor);
            g2.fillRoundRect(trackRect.x, y, trackRect.width, 4,4,4);
            int fill = (int)(trackRect.width * (slider.getValue()-slider.getMinimum())/(double)(slider.getMaximum()-slider.getMinimum()));
            g2.setColor(fillColor);
            g2.fillRoundRect(trackRect.x, y, fill,4,4,4);
        }
        @Override public void paintThumb(Graphics g) {
            Graphics2D g2=(Graphics2D)g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height,4,4);
            g2.setColor(thumbBorder);
            g2.drawRoundRect(thumbRect.x, thumbRect.y, thumbRect.width-1, thumbRect.height-1,4,4);
        }
    }

    // --- Preview Popup -------------------------------------------------
    private class BrushPreviewPopup extends JWindow {
        private Tool tool = Tool.PENCIL;
        private final int W = 160, H = 60;
        public BrushPreviewPopup() {
            setSize(W, H);
            setBackground(new Color(255,255,255,0)); // transparent background
        }
        public void setTool(Tool t){ this.tool = t; repaint(); }
        public void showAt(int screenX, int screenY){ setLocation(screenX, screenY); setVisible(true); }
        @Override public void paint(Graphics g){
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Draw rounded white background with drop shadow
            g2.setColor(new Color(0,0,0,50));
            g2.fillRoundRect(4,4,W-8,H-8,10,10);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0,0,W-8,H-8,10,10);
            g2.translate(8,8);

            // Sample line coordinates
            int x1 = 10, x2 = W-40; int yMid = (H-16)/2;

            switch(tool){
                case PENCIL:
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(pencilSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
                case LINEART:
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(pencilSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
                case CALLIGRAPHY:
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(pencilSize*1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                    g2.rotate(Math.toRadians(-20), (x1+x2)/2.0, yMid);
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
                case HIGHLIGHTER:
                    g2.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 90));
                    g2.setStroke(new BasicStroke(pencilSize*2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
                case WATERCOLOR:
                    g2.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 80));
                    g2.setStroke(new BasicStroke(pencilSize*1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
                case RAINBOW:
                    float hue = 0f;
                    int segments = 40;
                    for(int i=0;i<segments;i++){
                        float segX1 = x1 + (x2-x1)*i/(float)segments;
                        float segX2 = x1 + (x2-x1)*(i+1)/(float)segments;
                        g2.setColor(Color.getHSBColor(hue,1f,1f));
                        g2.setStroke(new BasicStroke(pencilSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine((int)segX1, yMid, (int)segX2, yMid);
                        hue += 0.02f; if(hue>1f) hue-=1f;
                    }
                    break;
                case ERASER:
                    g2.setColor(Color.LIGHT_GRAY);
                    g2.setStroke(new BasicStroke(eraserSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x1,yMid,x2,yMid);
                    break;
            }
            g2.dispose();
        }
    }
}

