/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.notebooks;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroTextField;
import main.ui.dialog.input.CustomInputDialog;

/**
 * Modern, polished notebook cluster organizer with drag-drop and visual feedback.
 * Features: collapsible clusters, smooth interactions, create/rename/disband clusters.
 * 
 * @author S1mplector
 */
public class ModernClusterOrganizer extends JDialog {
    
    private static final Color ACCENT = new Color(147, 112, 219);  // Purple theme for notebooks
    private static final Color ACCENT_LIGHT = new Color(240, 235, 255);
    private static final Color CARD_BORDER = new Color(220, 225, 235);
    private static final Color CARD_HOVER = new Color(248, 245, 255);
    private static final Color TEXT_PRIMARY = new Color(40, 45, 55);
    private static final Color TEXT_SECONDARY = new Color(100, 110, 125);
    private static final Color TEXT_MUTED = new Color(150, 160, 175);
    private static final Color CLUSTER_BG = new Color(252, 250, 255);
    private static final Color DROP_HIGHLIGHT = new Color(147, 112, 219, 40);
    
    private final NotebookStore store = new NotebookStore();
    private final JPanel clustersContainer;
    private final AeroTextField searchField;
    private final Map<String, ClusterPanel> clusterPanels = new HashMap<>();
    private Timer searchDebounceTimer;
    private Runnable onChangeCallback;
    
    public ModernClusterOrganizer(Frame parent) {
        super(parent, "Organize Notebooks", true);
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());
        
        FrostedGlassPanel main = new FrostedGlassPanel(new BorderLayout(0, 0), 20);
        main.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // ═══════════════════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════════════════
        JPanel header = createHeader();
        main.add(header, BorderLayout.NORTH);
        
        // ═══════════════════════════════════════════════════════════════════
        // SEARCH & ACTIONS
        // ═══════════════════════════════════════════════════════════════════
        JPanel topSection = new JPanel(new BorderLayout(12, 8));
        topSection.setOpaque(false);
        topSection.setBorder(new EmptyBorder(0, 24, 16, 24));
        
        searchField = new AeroTextField(28);
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.putClientProperty("JTextField.placeholderText", "Search notebooks...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
        });
        
        RoundedButton newClusterBtn = new RoundedButton("+ New Cluster");
        newClusterBtn.setPreferredSize(new Dimension(130, 36));
        newClusterBtn.addActionListener(e -> createNewCluster());
        
        topSection.add(searchField, BorderLayout.CENTER);
        topSection.add(newClusterBtn, BorderLayout.EAST);
        
        // ═══════════════════════════════════════════════════════════════════
        // CLUSTERS CONTAINER
        // ═══════════════════════════════════════════════════════════════════
        clustersContainer = new JPanel();
        clustersContainer.setLayout(new BoxLayout(clustersContainer, BoxLayout.Y_AXIS));
        clustersContainer.setOpaque(true);
        clustersContainer.setDoubleBuffered(true);
        clustersContainer.setBackground(new Color(250, 251, 253));
        clustersContainer.setBorder(new EmptyBorder(16, 24, 16, 24));
        
        JScrollPane scroll = new JScrollPane(clustersContainer);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(new Color(250, 251, 253));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().setScrollMode(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE);
        
        try {
            scroll.getVerticalScrollBar().setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        } catch (Throwable ignored) {}
        
        JPanel centerSection = new JPanel(new BorderLayout());
        centerSection.setOpaque(false);
        centerSection.add(topSection, BorderLayout.NORTH);
        centerSection.add(scroll, BorderLayout.CENTER);
        main.add(centerSection, BorderLayout.CENTER);
        
        // ═══════════════════════════════════════════════════════════════════
        // FOOTER
        // ═══════════════════════════════════════════════════════════════════
        JPanel footer = createFooter();
        main.add(footer, BorderLayout.SOUTH);
        
        add(main);
        
        // Keyboard shortcuts
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        setSize(750, 600);
        setLocationRelativeTo(parent);
        
        refreshClusters();
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 16, 24));
        
        JLabel title = new JLabel("Organize Notebooks");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(TEXT_PRIMARY);
        
        JLabel subtitle = new JLabel("Drag notebooks between clusters to organize them");
        subtitle.setFont(subtitle.getFont().deriveFont(12f));
        subtitle.setForeground(TEXT_MUTED);
        
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);
        
        // Close button
        JButton closeBtn = createIconButton("✕", "Close");
        closeBtn.addActionListener(e -> dispose());
        
        header.add(titlePanel, BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 24, 20, 24));
        
        JLabel hint = new JLabel("Drag notebooks to clusters • Right-click for options");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(TEXT_MUTED);
        
        RoundedButton doneBtn = new RoundedButton("Done");
        doneBtn.setPreferredSize(new Dimension(100, 38));
        doneBtn.addActionListener(e -> dispose());
        
        footer.add(hint, BorderLayout.WEST);
        footer.add(doneBtn, BorderLayout.EAST);
        
        return footer;
    }
    
    private JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(btn.getFont().deriveFont(16f));
        btn.setForeground(TEXT_SECONDARY);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }
    
    private void debouncedRefresh() {
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        searchDebounceTimer = new Timer(150, e -> {
            searchDebounceTimer.stop();
            refreshClusters();
        });
        searchDebounceTimer.setRepeats(false);
        searchDebounceTimer.start();
    }
    
    private void refreshClusters() {
        clustersContainer.removeAll();
        clusterPanels.clear();
        store.reload();
        
        String query = searchField.getText().trim();
        
        // Get all clusters
        List<String> clusterIds = store.getClusterIds();
        
        // Add cluster panels
        for (String clusterId : clusterIds) {
            List<NotebookInfo> notebooks = filterNotebooks(store.getNotebooksInCluster(clusterId), query);
            if (!query.isEmpty() && notebooks.isEmpty()) continue; // Skip empty clusters when filtering
            
            ClusterPanel clusterPanel = new ClusterPanel(clusterId, notebooks);
            clusterPanels.put(clusterId, clusterPanel);
            clustersContainer.add(clusterPanel);
            clustersContainer.add(Box.createVerticalStrut(16));
        }
        
        // Add unclustered notebooks section
        List<NotebookInfo> unclustered = filterNotebooks(store.getUnclusteredNotebooks(), query);
        if (!unclustered.isEmpty() || query.isEmpty()) {
            UnclusteredPanel unclusteredPanel = new UnclusteredPanel(unclustered);
            clustersContainer.add(unclusteredPanel);
        }
        
        clustersContainer.add(Box.createVerticalGlue());
        clustersContainer.revalidate();
        clustersContainer.repaint();
    }
    
    private List<NotebookInfo> filterNotebooks(List<NotebookInfo> notebooks, String query) {
        if (query.isEmpty()) return notebooks;
        List<NotebookInfo> filtered = new ArrayList<>();
        for (NotebookInfo nb : notebooks) {
            if (nativeSearchMatch(nb.getName(), query) || 
                nativeSearchMatch(nb.getDescription(), query)) {
                filtered.add(nb);
            }
        }
        return filtered;
    }
    
    private boolean nativeSearchMatch(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return text != null;
        try {
            if (NativeAccess.stringOpsReady()) {
                return NativeAccess.stringContainsCi(text, query);
            }
        } catch (Throwable ignored) {}
        return text.toLowerCase().contains(query.toLowerCase());
    }
    
    private void createNewCluster() {
        String name = CustomInputDialog.prompt(this, "New Cluster", "Enter cluster name:", "");
        if (name != null && !name.trim().isEmpty()) {
            // Create a placeholder notebook assignment to create the cluster
            // The cluster will appear when a notebook is assigned to it
            // For now, show message that user should drag notebooks
            refreshClusters();
        }
    }
    
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }
    
    private void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CLUSTER PANEL
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class ClusterPanel extends JPanel {
        private final String clusterId;
        private final List<NotebookInfo> notebooks;
        private boolean expanded = true;
        private boolean dropHighlight = false;
        private final main.ui.features.entries.BackgroundPainter bgPainter = new main.ui.features.entries.BackgroundPainter();
        
        ClusterPanel(String clusterId, List<NotebookInfo> notebooks) {
            this.clusterId = clusterId;
            this.notebooks = notebooks;
            
            setLayout(new BorderLayout(0, 8));
            setOpaque(false);  // Allow background to show through
            setDoubleBuffered(true);
            setBorder(new RoundedBorder(12, new Color(200, 190, 220)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            setAlignmentX(LEFT_ALIGNMENT);
            
            // Header
            JPanel header = createClusterHeader();
            add(header, BorderLayout.NORTH);
            
            // Notebooks grid
            JPanel notebooksPanel = createNotebooksPanel();
            add(notebooksPanel, BorderLayout.CENTER);
            
            // Setup drop target
            setupDropTarget();
        }
        
        private JPanel createClusterHeader() {
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(12, 16, 8, 16));
            
            // Left: expand/collapse + name
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            
            JLabel expandIcon = new JLabel(expanded ? "▼" : "▶");
            expandIcon.setFont(expandIcon.getFont().deriveFont(10f));
            expandIcon.setForeground(TEXT_MUTED);
            expandIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            expandIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    expanded = !expanded;
                    expandIcon.setText(expanded ? "▼" : "▶");
                    refreshClusters();
                }
            });
            
            JLabel nameLabel = new JLabel(clusterId);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
            nameLabel.setForeground(ACCENT);
            
            JLabel countLabel = new JLabel("(" + notebooks.size() + ")");
            countLabel.setFont(countLabel.getFont().deriveFont(12f));
            countLabel.setForeground(TEXT_MUTED);
            
            left.add(expandIcon);
            left.add(nameLabel);
            left.add(countLabel);
            
            // Right: actions
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            actions.setOpaque(false);
            
            JButton renameBtn = createSmallButton("✎", "Rename cluster");
            renameBtn.addActionListener(e -> renameCluster());
            
            JButton disbandBtn = createSmallButton("✕", "Disband cluster");
            disbandBtn.addActionListener(e -> disbandCluster());
            
            actions.add(renameBtn);
            actions.add(disbandBtn);
            
            header.add(left, BorderLayout.WEST);
            header.add(actions, BorderLayout.EAST);
            
            return header;
        }
        
        private JPanel createNotebooksPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(0, 12, 12, 12));
            
            if (!expanded) {
                panel.setVisible(false);
                return panel;
            }
            
            if (notebooks.isEmpty()) {
                JLabel empty = new JLabel("Drag notebooks here");
                empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 12f));
                empty.setForeground(TEXT_MUTED);
                panel.add(empty);
            } else {
                for (NotebookInfo nb : notebooks) {
                    panel.add(new NotebookChip(nb));
                }
            }
            
            return panel;
        }
        
        private JButton createSmallButton(String icon, String tooltip) {
            JButton btn = new JButton(icon);
            btn.setFont(btn.getFont().deriveFont(12f));
            btn.setForeground(TEXT_MUTED);
            btn.setPreferredSize(new Dimension(28, 28));
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setToolTipText(tooltip);
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setForeground(ACCENT);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setForeground(TEXT_MUTED);
                }
            });
            return btn;
        }
        
        private void renameCluster() {
            String newName = CustomInputDialog.prompt(
                ModernClusterOrganizer.this, 
                "Rename Cluster", 
                "Enter new name:", 
                clusterId
            );
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(clusterId)) {
                // Reassign all notebooks to new cluster name
                for (NotebookInfo nb : notebooks) {
                    store.assignToCluster(nb, newName.trim());
                }
                refreshClusters();
                notifyChange();
            }
        }
        
        private void disbandCluster() {
            for (NotebookInfo nb : notebooks) {
                store.removeFromCluster(nb);
            }
            refreshClusters();
            notifyChange();
        }
        
        private void setupDropTarget() {
            new DropTarget(this, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        Transferable t = dtde.getTransferable();
                        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String nbName = (String) t.getTransferData(DataFlavor.stringFlavor);
                            for (NotebookInfo nb : store.list()) {
                                if (nb.getName().equals(nbName)) {
                                    store.assignToCluster(nb, clusterId);
                                    break;
                                }
                            }
                            refreshClusters();
                            notifyChange();
                        }
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        dtde.dropComplete(false);
                    }
                    dropHighlight = false;
                    repaint();
                }
                
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    dropHighlight = true;
                    repaint();
                }
                
                @Override
                public void dragExit(DropTargetEvent dte) {
                    dropHighlight = false;
                    repaint();
                }
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Clip to rounded rect
            g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            
            // Paint entry background subtly if available
            String bgPath = main.core.service.SettingsStore.get().getEntryBackgroundImage();
            if (bgPath != null && !bgPath.isEmpty()) {
                bgPainter.paint(g2, this, bgPath, 0.15f, true);  // Very subtle opacity
            } else {
                g2.setColor(CLUSTER_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            
            // Frosted overlay for readability
            g2.setColor(new Color(255, 255, 255, dropHighlight ? 200 : 230));
            g2.fillRect(0, 0, getWidth(), getHeight());
            
            // Drop highlight tint
            if (dropHighlight) {
                g2.setColor(DROP_HIGHLIGHT);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            
            g2.setClip(null);
            
            // Left accent bar
            g2.setColor(ACCENT);
            g2.fill(new RoundRectangle2D.Float(0, 0, 4, getHeight(), 4, 4));
            
            // Border
            g2.setColor(new Color(200, 190, 220));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 12, 12));
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UNCLUSTERED PANEL
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class UnclusteredPanel extends JPanel {
        private final List<NotebookInfo> notebooks;
        private boolean dropHighlight = false;
        
        UnclusteredPanel(List<NotebookInfo> notebooks) {
            this.notebooks = notebooks;
            
            setLayout(new BorderLayout(0, 8));
            setOpaque(true);
            setDoubleBuffered(true);
            setBackground(new Color(250, 251, 253));
            setBorder(new RoundedBorder(12, CARD_BORDER));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            setAlignmentX(LEFT_ALIGNMENT);
            
            // Header
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(12, 16, 8, 16));
            
            JLabel nameLabel = new JLabel("Unclustered");
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
            nameLabel.setForeground(TEXT_SECONDARY);
            
            JLabel countLabel = new JLabel("(" + notebooks.size() + ")");
            countLabel.setFont(countLabel.getFont().deriveFont(12f));
            countLabel.setForeground(TEXT_MUTED);
            
            header.add(nameLabel);
            header.add(countLabel);
            add(header, BorderLayout.NORTH);
            
            // Notebooks
            JPanel notebooksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
            notebooksPanel.setOpaque(false);
            notebooksPanel.setBorder(new EmptyBorder(0, 12, 12, 12));
            
            if (notebooks.isEmpty()) {
                JLabel empty = new JLabel("All notebooks are organized!");
                empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 12f));
                empty.setForeground(TEXT_MUTED);
                notebooksPanel.add(empty);
            } else {
                for (NotebookInfo nb : notebooks) {
                    notebooksPanel.add(new NotebookChip(nb));
                }
            }
            add(notebooksPanel, BorderLayout.CENTER);
            
            // Drop target to remove from clusters
            setupDropTarget();
        }
        
        private void setupDropTarget() {
            new DropTarget(this, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        Transferable t = dtde.getTransferable();
                        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String nbName = (String) t.getTransferData(DataFlavor.stringFlavor);
                            for (NotebookInfo nb : store.list()) {
                                if (nb.getName().equals(nbName)) {
                                    store.removeFromCluster(nb);
                                    break;
                                }
                            }
                            refreshClusters();
                            notifyChange();
                        }
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        dtde.dropComplete(false);
                    }
                    dropHighlight = false;
                    repaint();
                }
                
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    dropHighlight = true;
                    repaint();
                }
                
                @Override
                public void dragExit(DropTargetEvent dte) {
                    dropHighlight = false;
                    repaint();
                }
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g2.setColor(dropHighlight ? new Color(245, 248, 255) : new Color(250, 251, 253));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NOTEBOOK CHIP
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class NotebookChip extends JPanel implements DragGestureListener, DragSourceListener {
        private final NotebookInfo notebook;
        private boolean hovered = false;
        private final DragSource dragSource;
        
        NotebookChip(NotebookInfo notebook) {
            this.notebook = notebook;
            
            setOpaque(true);
            setDoubleBuffered(true);
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(150, 44));
            
            // Type indicator (no emojis)
            String typeAbbr = switch (notebook.getType()) {
                case JOURNAL -> "J";
                case POETRY -> "P";
                case NOTETAKING -> "N";
            };
            JLabel typeLabel = new JLabel(typeAbbr);
            typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 11f));
            typeLabel.setForeground(notebook.getAccentColor());
            
            // Name (truncated)
            String name = notebook.getName();
            if (name.length() > 12) name = name.substring(0, 11) + "…";
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(12f));
            nameLabel.setForeground(TEXT_PRIMARY);
            
            add(typeLabel);
            add(nameLabel);
            
            // Hover effect
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
            
            // Drag support
            dragSource = new DragSource();
            dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Background
            Color bg = hovered ? CARD_HOVER : Color.WHITE;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            
            // Border with notebook accent
            Color accent = notebook.getAccentColor();
            g2.setColor(hovered ? accent : CARD_BORDER);
            g2.setStroke(new BasicStroke(hovered ? 1.5f : 1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 10, 10));
            
            // Left accent strip
            if (hovered) {
                g2.setColor(accent);
                g2.fill(new RoundRectangle2D.Float(0, 0, 3, getHeight(), 3, 3));
            }
            
            g2.dispose();
            super.paintComponent(g);
        }
        
        // Drag gesture listener
        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            Transferable transferable = new StringSelection(notebook.getName());
            dragSource.startDrag(dge, DragSource.DefaultMoveDrop, transferable, this);
        }
        
        @Override public void dragEnter(DragSourceDragEvent dsde) {}
        @Override public void dragOver(DragSourceDragEvent dsde) {}
        @Override public void dropActionChanged(DragSourceDragEvent dsde) {}
        @Override public void dragExit(DragSourceEvent dse) {}
        @Override public void dragDropEnd(DragSourceDropEvent dsde) {}
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ROUNDED BORDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        
        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, radius, radius));
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
    }
}
