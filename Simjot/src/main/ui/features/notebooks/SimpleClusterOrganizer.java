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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;

import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.ShadowedDialogPanel;
import main.ui.dialog.input.CustomInputDialog;
import main.ui.theme.aero.AeroTheme;

/**
 * Simple, clean cluster organizer dialog using frosted glass styling.
 */
public class SimpleClusterOrganizer extends JDialog {
    
    private static final Color ACCENT = AeroTheme.AERO_BLUE;
    private static final Color TEXT_PRIMARY = new Color(50, 55, 65);
    private static final Color TEXT_MUTED = new Color(120, 130, 145);
    
    private final NotebookStore store = new NotebookStore();
    private final JPanel contentPanel;
    private Runnable onChangeCallback;
    
    public SimpleClusterOrganizer(Frame parent) {
        super(parent, "Organize Notebooks", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());
        
        ShadowedDialogPanel main = new ShadowedDialogPanel(new BorderLayout(0, 0), 20);
        main.setBorder(new EmptyBorder(20, 20, 20, 20));
        main.setFlat(true);
        main.setFlatColor(Color.WHITE);
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 12, 24));
        
        JLabel title = new JLabel("Organize Notebooks");
        title.setFont(AeroTheme.defaultBoldFont(18f));
        title.setForeground(TEXT_PRIMARY);
        
        JLabel closeBtn = new JLabel("✕");
        closeBtn.setFont(AeroTheme.defaultFont().deriveFont(16f));
        closeBtn.setForeground(TEXT_MUTED);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dispose(); }
            @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(ACCENT); }
            @Override public void mouseExited(MouseEvent e) { closeBtn.setForeground(TEXT_MUTED); }
        });
        
        header.add(title, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);
        
        // Content area
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(true);
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(new EmptyBorder(0, 16, 8, 16));
        
        JScrollPane scroll = new JScrollPane(contentPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        try {
            scroll.getVerticalScrollBar().setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        } catch (Throwable ignored) {}
        
        main.add(scroll, BorderLayout.CENTER);
        
        // Footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 24, 20, 24));
        
        RoundedButton newClusterBtn = createDialogButton("New Cluster", "new");
        newClusterBtn.setPreferredSize(new Dimension(140, 38));
        newClusterBtn.addActionListener(e -> createNewCluster());
        
        RoundedButton doneBtn = createDialogButton("Done", "save");
        doneBtn.setPreferredSize(new Dimension(120, 38));
        doneBtn.addActionListener(e -> dispose());
        
        footer.add(newClusterBtn, BorderLayout.WEST);
        footer.add(doneBtn, BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        
        add(main, BorderLayout.CENTER);
        setSize(620, 500);
        setLocationRelativeTo(parent);
        
        // ESC to close
        getRootPane().registerKeyboardAction(e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        refresh();
    }
    
    private void refresh() {
        contentPanel.removeAll();
        store.reload();
        
        List<String> clusterIds = store.getClusterIds();
        SettingsStore.get().pruneHiddenClusters(clusterIds);
        
        // Add clusters
        for (String clusterId : clusterIds) {
            List<NotebookInfo> notebooks = store.getNotebooksInCluster(clusterId);
            contentPanel.add(createClusterRow(clusterId, notebooks));
            contentPanel.add(Box.createVerticalStrut(8));
        }
        
        // Unclustered section
        List<NotebookInfo> unclustered = store.getUnclusteredNotebooks();
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createUnclusteredRow(unclustered));
        
        contentPanel.add(Box.createVerticalGlue());
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private JPanel createClusterRow(String clusterId, List<NotebookInfo> notebooks) {
        boolean hidden = SettingsStore.get().isClusterHidden(clusterId);
        JPanel row = new JPanel(new BorderLayout(8, 8)) {
            private boolean highlight = false;
            {
                new DropTarget(this, new DropTargetAdapter() {
                    @Override public void drop(DropTargetDropEvent e) {
                        handleDrop(e, clusterId);
                        highlight = false;
                        repaint();
                    }
                    @Override public void dragEnter(DropTargetDragEvent e) { highlight = true; repaint(); }
                    @Override public void dragExit(DropTargetEvent e) { highlight = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(highlight ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 30) 
                                      : new Color(255, 255, 255, 100));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (highlight) {
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 100));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 12, 12);
                }
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        
        // Header with cluster name
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        
        JLabel nameLabel = new JLabel(clusterId);
        nameLabel.setFont(AeroTheme.defaultBoldFont(13f));
        nameLabel.setForeground(hidden ? TEXT_MUTED : ACCENT);
        
        JLabel countLabel = new JLabel(" (" + notebooks.size() + ")");
        countLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        countLabel.setForeground(TEXT_MUTED);
        
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        namePanel.setOpaque(false);
        namePanel.add(nameLabel);
        namePanel.add(countLabel);
        if (hidden) {
            JLabel hiddenLabel = new JLabel("hidden");
            hiddenLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 10f));
            hiddenLabel.setForeground(TEXT_MUTED);
            hiddenLabel.setBorder(new EmptyBorder(0, 6, 0, 0));
            namePanel.add(hiddenLabel);
        }
        
        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        
        JLabel renameBtn = createActionLabel("✎", "Rename");
        renameBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { renameCluster(clusterId, notebooks); }
        });

        JLabel toggleBtn = createActionLabel(hidden ? "Show" : "Hide", hidden ? "Show cluster" : "Hide cluster");
        toggleBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                SettingsStore.get().setClusterHidden(clusterId, !hidden);
                SettingsStore.get().save();
                refresh();
                notifyChange();
            }
        });

        JLabel deleteBtn = createActionLabel("✕", "Remove cluster");
        deleteBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { disbandCluster(clusterId, notebooks); }
        });

        actions.add(renameBtn);
        actions.add(toggleBtn);
        actions.add(deleteBtn);
        
        headerRow.add(namePanel, BorderLayout.WEST);
        headerRow.add(actions, BorderLayout.EAST);
        row.add(headerRow, BorderLayout.NORTH);
        
        // Notebook chips
        JPanel chipsPanel = new JPanel();
        chipsPanel.setOpaque(false);
        chipsPanel.setLayout(new BoxLayout(chipsPanel, BoxLayout.Y_AXIS));
        chipsPanel.setAlignmentX(LEFT_ALIGNMENT);
        
        if (notebooks.isEmpty()) {
            JLabel empty = new JLabel("Drop notebooks here");
            empty.setFont(AeroTheme.defaultFont().deriveFont(Font.ITALIC, 11f));
            empty.setForeground(TEXT_MUTED);
            chipsPanel.add(createChipRow(empty));
        } else {
            boolean first = true;
            for (NotebookInfo nb : notebooks) {
                if (!first) {
                    chipsPanel.add(Box.createVerticalStrut(4));
                }
                chipsPanel.add(createChipRow(createNotebookChip(nb)));
                first = false;
            }
        }
        row.add(chipsPanel, BorderLayout.CENTER);
        
        return row;
    }
    
    private JPanel createUnclusteredRow(List<NotebookInfo> notebooks) {
        JPanel row = new JPanel(new BorderLayout(8, 8)) {
            private boolean highlight = false;
            {
                new DropTarget(this, new DropTargetAdapter() {
                    @Override public void drop(DropTargetDropEvent e) {
                        handleDrop(e, null); // null = unclustered
                        highlight = false;
                        repaint();
                    }
                    @Override public void dragEnter(DropTargetDragEvent e) { highlight = true; repaint(); }
                    @Override public void dragExit(DropTargetEvent e) { highlight = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(highlight ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 30) 
                                      : new Color(240, 240, 240, 150));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        JLabel nameLabel = new JLabel("Unclustered");
        nameLabel.setFont(AeroTheme.defaultBoldFont(13f));
        nameLabel.setForeground(TEXT_MUTED);
        
        JLabel countLabel = new JLabel(" (" + notebooks.size() + ")");
        countLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        countLabel.setForeground(TEXT_MUTED);
        
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        namePanel.setOpaque(false);
        namePanel.add(nameLabel);
        namePanel.add(countLabel);
        row.add(namePanel, BorderLayout.NORTH);
        
        JPanel chipsPanel = new JPanel();
        chipsPanel.setOpaque(false);
        chipsPanel.setLayout(new BoxLayout(chipsPanel, BoxLayout.Y_AXIS));
        chipsPanel.setAlignmentX(LEFT_ALIGNMENT);
        
        if (notebooks.isEmpty()) {
            JLabel empty = new JLabel("All notebooks are organized!");
            empty.setFont(AeroTheme.defaultFont().deriveFont(Font.ITALIC, 11f));
            empty.setForeground(new Color(100, 180, 100));
            chipsPanel.add(createChipRow(empty));
        } else {
            boolean first = true;
            for (NotebookInfo nb : notebooks) {
                if (!first) {
                    chipsPanel.add(Box.createVerticalStrut(4));
                }
                chipsPanel.add(createChipRow(createNotebookChip(nb)));
                first = false;
            }
        }
        row.add(chipsPanel, BorderLayout.CENTER);
        
        return row;
    }
    
    private JPanel createNotebookChip(NotebookInfo nb) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(0, 0, 0, 30));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setBorder(new EmptyBorder(3, 6, 3, 8));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        
        // Notebook icon
        String iconPath = main.ui.components.icons.ImageIconRenderer.mapIdToResource("notebook_nopen");
        if (iconPath != null) {
            Icon icon = main.ui.components.icons.ImageIconRenderer.icon(iconPath, 14, false);
            if (icon != null) {
                JLabel iconLabel = new JLabel(icon);
                chip.add(iconLabel);
            }
        }
        
        // Name label
        JLabel nameLabel = new JLabel(nb.getName());
        nameLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        nameLabel.setForeground(TEXT_PRIMARY);
        chip.add(nameLabel);
        
        // Setup drag source
        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(chip, DnDConstants.ACTION_MOVE, e -> {
            StringSelection sel = new StringSelection(nb.getName());
            e.startDrag(DragSource.DefaultMoveDrop, sel, new DragSourceAdapter() {});
        });
        
        return chip;
    }
    
    private JLabel createActionLabel(String text, String tooltip) {
        JLabel label = new JLabel(text);
        label.setFont(AeroTheme.defaultFont().deriveFont(12f));
        label.setForeground(TEXT_MUTED);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(tooltip);
        label.setBorder(new EmptyBorder(2, 6, 2, 6));
        label.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { label.setForeground(ACCENT); }
            @Override public void mouseExited(MouseEvent e) { label.setForeground(TEXT_MUTED); }
        });
        return label;
    }
    
    private void handleDrop(DropTargetDropEvent e, String targetCluster) {
        try {
            e.acceptDrop(DnDConstants.ACTION_MOVE);
            String nbName = (String) e.getTransferable().getTransferData(DataFlavor.stringFlavor);
            for (NotebookInfo nb : store.list()) {
                if (nb.getName().equals(nbName)) {
                    if (targetCluster != null) {
                        store.assignToCluster(nb, targetCluster);
                    } else {
                        store.removeFromCluster(nb);
                    }
                    break;
                }
            }
            e.dropComplete(true);
            refresh();
            notifyChange();
        } catch (Exception ex) {
            e.dropComplete(false);
        }
    }
    
    private void createNewCluster() {
        String name = CustomInputDialog.prompt(this, "New Cluster", "Enter cluster name:", "");
        if (name != null && !name.trim().isEmpty()) {
            // Will show when a notebook is assigned
            refresh();
        }
    }
    
    private void renameCluster(String oldName, List<NotebookInfo> notebooks) {
        String newName = CustomInputDialog.prompt(this, "Rename Cluster", "Enter new name:", oldName);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
            boolean wasHidden = SettingsStore.get().isClusterHidden(oldName);
            for (NotebookInfo nb : notebooks) {
                store.assignToCluster(nb, newName.trim());
            }
            if (wasHidden) {
                SettingsStore.get().setClusterHidden(oldName, false);
                SettingsStore.get().setClusterHidden(newName.trim(), true);
                SettingsStore.get().save();
            }
            refresh();
            notifyChange();
        }
    }
    
    private void disbandCluster(String clusterId, List<NotebookInfo> notebooks) {
        for (NotebookInfo nb : notebooks) {
            store.removeFromCluster(nb);
        }
        SettingsStore.get().setClusterHidden(clusterId, false);
        SettingsStore.get().save();
        refresh();
        notifyChange();
    }
    
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }
    
    private void notifyChange() {
        if (onChangeCallback != null) onChangeCallback.run();
    }

    private RoundedButton createDialogButton(String text, String iconId) {
        RoundedButton btn = new RoundedButton(text).withIcon(iconId);
        btn.setFocusPainted(false);
        return btn;
    }

    private JPanel createChipRow(JComponent component) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(component);
        return row;
    }
    
    public static void show(Frame parent, Runnable onChangeCallback) {
        SimpleClusterOrganizer dialog = new SimpleClusterOrganizer(parent);
        dialog.setOnChangeCallback(onChangeCallback);
        dialog.setVisible(true);
    }
}
