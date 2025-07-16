package mindmap;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import main.util.ResourceLoader;

public class MindMapEditingPanel extends JPanel {
    private final JTextField mapTitleField;
    private final JButton addBubbleButton, deleteBubbleButton, saveMapButton, backButton;
    private MindMapCanvas canvas = null;
    private MindMap currentMap;
    private final MindMapPanel parent;

    public MindMapEditingPanel(MindMapPanel parent) {
        this.parent = parent;
        setLayout(new BorderLayout(5, 5));
        setBackground(Color.BLACK);

        // Top panel (title + toolbar)
        JPanel topPanel = new JPanel(new BorderLayout(5,5));
        topPanel.setBackground(Color.BLACK);

        mapTitleField = new JTextField();
        mapTitleField.setFont(new Font("SansSerif", Font.BOLD, 18));
        mapTitleField.setForeground(Color.DARK_GRAY);
        topPanel.add(mapTitleField, BorderLayout.CENTER);

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        toolBar.setOpaque(false);

        addBubbleButton = new JButton(ResourceLoader.createImageIcon("img/addmap.png"));
        configureButton(addBubbleButton, "Add new bubble");
        toolBar.add(addBubbleButton);

        deleteBubbleButton = new JButton(ResourceLoader.createImageIcon("img/deletemap.png"));
        configureButton(deleteBubbleButton, "Delete selected bubble");
        toolBar.add(deleteBubbleButton);

        topPanel.add(toolBar, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Center: Canvas
        canvas = new MindMapCanvas();
        add(canvas, BorderLayout.CENTER);

        // Bottom: Save + Back
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Color.BLACK);

        saveMapButton = new JButton("Save Mind Map");
        saveMapButton.setBackground(Color.DARK_GRAY);
        saveMapButton.setForeground(Color.WHITE);
        saveMapButton.addActionListener(e -> saveMindMap());

        backButton = new JButton("Back");
        backButton.setBackground(Color.DARK_GRAY);
        backButton.setForeground(Color.WHITE);
        backButton.addActionListener(e -> parent.showSelectionPanel());

        bottomPanel.add(saveMapButton);
        bottomPanel.add(backButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void configureButton(JButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(40,40));
        button.setBackground(Color.DARK_GRAY);
        button.setForeground(Color.WHITE);
    }

    public void loadMindMap(MindMap map) {
        this.currentMap = map;
        mapTitleField.setText(map.getTitle());
        canvas.setMindMap(map);
    }

    private void saveMindMap() {
        if (currentMap == null) return;

        currentMap.setTitle(mapTitleField.getText().trim());

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            String filename = timestamp + ".mmap";

            File mindmapsFolder = parent.getMindmapsFolder();
            File file = new File(mindmapsFolder, filename);

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(currentMap);
            }
            JOptionPane.showMessageDialog(this, "Mind map saved successfully!");
            parent.showSelectionPanel();
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving mind map.", 
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public class MindMapCanvas extends JPanel {
        private java.util.List<MindMapNode> bubbles;
        private java.util.List<MindMapRelation> relations;
        private Point dragOffset = null;
        private MindMapNode draggingNode = null;
        private MindMapNode selectedNode = null;
        private boolean relationMode = false;

        public MindMapCanvas() {
            setBackground(new Color(255, 255, 255, 0)); // Transparent White
            addMouseListener(new MouseAdapter(){
                @Override
                public void mousePressed(MouseEvent e) {
                    Point p = e.getPoint();
                    for (MindMapNode bubble : bubbles) {
                        if (bubble.contains(p)) {
                            draggingNode = bubble;
                            dragOffset = new Point(p.x - bubble.x, p.y - bubble.y);
                            if (SwingUtilities.isRightMouseButton(e)) {
                                showBubbleContextMenu(bubble, e.getX(), e.getY());
                            }
                            selectedNode = bubble;
                            repaint();
                            return;
                        }
                    }
                    draggingNode = null;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    draggingNode = null;
                    dragOffset = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (relationMode && !SwingUtilities.isRightMouseButton(e)) {
                        for (MindMapNode bubble : bubbles) {
                            if (bubble.contains(e.getPoint())) {
                                if (selectedNode != null && selectedNode != bubble) {
                                    relations.add(new MindMapRelation(selectedNode, bubble));
                                    relationMode = false;
                                    selectedNode = null;
                                    repaint();
                                    return;
                                }
                            }
                        }
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter(){
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (draggingNode != null && dragOffset != null) {
                        draggingNode.x = e.getX() - dragOffset.x;
                        draggingNode.y = e.getY() - dragOffset.y;
                        repaint();
                    }
                }
            });
        }

        public void setMindMap(MindMap map) {
            this.bubbles = map.getNodes();
            this.relations = map.getRelations();
            repaint();
        }

        public void addBubble() {
            int x = getWidth() / 2;
            int y = getHeight() / 2;
            MindMapNode bubble = new MindMapNode(x, y, 40, Color.CYAN, "Bubble");
            bubbles.add(bubble);
            repaint();
        }

        public void deleteSelectedBubble() {
            if (selectedNode != null) {
                bubbles.remove(selectedNode);
                relations.removeIf(rel -> rel.from == selectedNode || rel.to == selectedNode);
                selectedNode = null;
                repaint();
            }
        }

        private void showBubbleContextMenu(MindMapNode bubble, int x, int y) {
            JPopupMenu popup = new JPopupMenu();

            JMenuItem relationItem = new JMenuItem("Relation");
            relationItem.addActionListener(e -> {
                selectedNode = bubble;
                relationMode = true;
            });

            JMenuItem colorItem = new JMenuItem("Color");
            colorItem.addActionListener(e -> {
                Color chosen = JColorChooser.showDialog(this, 
                    "Choose Bubble Color", bubble.color
                );
                if (chosen != null) {
                    bubble.color = chosen;
                    repaint();
                }
            });

            popup.add(relationItem);
            popup.add(colorItem);
            popup.show(this, x, y);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2));
            g2.setColor(Color.BLACK);

            // Draw lines & arrowheads
            if (relations != null) {
                for (MindMapRelation rel : relations) {
                    int x1 = rel.from.x;
                    int y1 = rel.from.y;
                    int x2 = rel.to.x;
                    int y2 = rel.to.y;
                    g2.drawLine(x1, y1, x2, y2);
                    drawArrowHead(g2, new Point(x2, y2), new Point(x1, y1));
                }
            }

            // Draw the bubbles (nodes)
            if (bubbles != null) {
                for (MindMapNode bubble : bubbles) {
                    g2.setColor(bubble.color);
                    g2.fillOval(bubble.x - bubble.radius, bubble.y - bubble.radius, 
                                bubble.radius * 2, bubble.radius * 2);

                    g2.setColor(Color.BLACK);
                    g2.drawOval(bubble.x - bubble.radius, bubble.y - bubble.radius, 
                                bubble.radius * 2, bubble.radius * 2);

                    // Centered text
                    FontMetrics fm = g2.getFontMetrics();
                    int textWidth = fm.stringWidth(bubble.text);
                    int textHeight = fm.getHeight();
                    g2.drawString(bubble.text, 
                                  bubble.x - textWidth / 2, 
                                  bubble.y + textHeight / 4);
                }
            }

            g2.dispose();
        }

        private void drawArrowHead(Graphics2D g2, Point tip, Point tail) {
            double phi = Math.toRadians(20);
            int barb = 10;
            double dy = tip.y - tail.y;
            double dx = tip.x - tail.x;
            double theta = Math.atan2(dy, dx);

            double rho = theta + phi;
            for (int j = 0; j < 2; j++) {
                double x = tip.x - barb * Math.cos(rho);
                double y = tip.y - barb * Math.sin(rho);
                g2.drawLine(tip.x, tip.y, (int) x, (int) y);
                rho = theta - phi;
            }
        }
    }
}
