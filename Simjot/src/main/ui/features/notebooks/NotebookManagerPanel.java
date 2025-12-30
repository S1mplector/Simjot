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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.fields.TitleDividerField;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.file.SimjotFileChooser;
import main.ui.dialog.input.CustomInputDialog;
import main.ui.theme.aero.AeroTheme;

public class NotebookManagerPanel extends JPanel {
    private final NotebookStore store = new NotebookStore();
    private final JPanel gallery = new JPanel();
    private final JournalApp app;

    public NotebookManagerPanel(JournalApp app){
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Top toolbar matching other panels
        JPanel topBar = new FrostedGlassPanel(new FlowLayout(FlowLayout.LEFT, 8, 6), 16);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        ToolbarMenuIconButton backBtn = new ToolbarMenuIconButton("", "back");
        backBtn.setToolTipText("Back to Main Menu");
        backBtn.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));

        topBar.add(backBtn);
        

        add(topBar, BorderLayout.NORTH);

        gallery.setOpaque(false);
        gallery.setLayout(new BoxLayout(gallery, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(gallery);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll,BorderLayout.CENTER);

        refresh();
    }

    public void refresh(){
        store.reload();
        gallery.removeAll();
        
        // Display clusters first
        List<String> clusterIds = store.getClusterIds();
        for (String clusterId : clusterIds) {
            gallery.add(createClusterPanel(clusterId));
            gallery.add(Box.createVerticalStrut(10));
        }
        
        // Display unclustered notebooks
        List<NotebookInfo> unclustered = store.getUnclusteredNotebooks();
        if (!unclustered.isEmpty()) {
            JPanel unclusteredPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
            unclusteredPanel.setOpaque(false);
            unclusteredPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (NotebookInfo nb : unclustered) {
                unclusteredPanel.add(createTile(nb));
            }
            unclusteredPanel.add(createAddTile());
            gallery.add(unclusteredPanel);
        } else {
            // Only add tile if no notebooks at all
            JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
            addPanel.setOpaque(false);
            addPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            addPanel.add(createAddTile());
            gallery.add(addPanel);
        }
        
        revalidate(); repaint();
    }
    
    private JPanel createClusterPanel(String clusterId) {
        JPanel clusterPanel = new JPanel(new BorderLayout(8, 8));
        clusterPanel.setOpaque(false);
        clusterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        clusterPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(147, 112, 219, 150)),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        
        // Cluster header with name and actions
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel clusterLabel = new JLabel(clusterId);
        clusterLabel.setFont(clusterLabel.getFont().deriveFont(Font.BOLD, 14f));
        clusterLabel.setForeground(new Color(80, 80, 80));
        header.add(clusterLabel, BorderLayout.WEST);
        
        // Cluster actions - notebook delete icon button
        ToolbarMenuIconButton disbandBtn = new ToolbarMenuIconButton("", "delete_notebook");
        disbandBtn.setToolTipText("Disband cluster");
        disbandBtn.addActionListener(e -> {
            boolean confirm = CustomConfirmDialog.confirm(this, 
                "Disband Cluster",
                "Disband cluster '" + clusterId + "'?<br>Notebooks will become unclustered.");
            if (confirm) {
                store.disbandCluster(clusterId);
                refresh();
            }
        });
        header.add(disbandBtn, BorderLayout.EAST);
        clusterPanel.add(header, BorderLayout.NORTH);
        
        // Notebooks in cluster
        JPanel notebooksFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        notebooksFlow.setOpaque(false);
        List<NotebookInfo> inCluster = store.getNotebooksInCluster(clusterId);
        for (NotebookInfo nb : inCluster) {
            notebooksFlow.add(createTile(nb));
        }
        clusterPanel.add(notebooksFlow, BorderLayout.CENTER);
        
        // Make cluster a drop target
        setupClusterDropTarget(clusterPanel, clusterId);
        
        return clusterPanel;
    }
    
    private void setupClusterDropTarget(JPanel clusterPanel, String clusterId) {
        new DropTarget(clusterPanel, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    Transferable t = dtde.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String nbName = (String) t.getTransferData(DataFlavor.stringFlavor);
                        // Find notebook by name and assign to cluster
                        for (NotebookInfo nb : store.list()) {
                            if (nb.getName().equals(nbName)) {
                                store.assignToCluster(nb, clusterId);
                                break;
                            }
                        }
                        refresh();
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
            
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                clusterPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(100, 149, 237)),
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 149, 237, 100), 2),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    )
                ));
            }
            
            @Override
            public void dragExit(DropTargetEvent dte) {
                clusterPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(147, 112, 219, 150)),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
                ));
            }
        });
    }

    private JPanel createTile(NotebookInfo nb){
        NotebookTile tile = new NotebookTile(nb);
        tile.setPreferredSize(new Dimension(120,120));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==1) openNotebook(nb); }
        });
        return tile;
    }

    private static BufferedImage createIcon(NotebookInfo nb){
        final int S = 100; // tile icon canvas
        final int ICON_SIZE = 72;
        BufferedImage canvas = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int x = (S - ICON_SIZE) / 2;
        int y = (S - ICON_SIZE) / 2;
        
        // Check for custom icon first
        String customPath = nb.getCustomIconPath();
        if (customPath != null && !customPath.isEmpty()) {
            try {
                BufferedImage customImg = javax.imageio.ImageIO.read(new java.io.File(customPath));
                if (customImg != null) {
                    java.awt.Image scaled = customImg.getScaledInstance(ICON_SIZE, ICON_SIZE, java.awt.Image.SCALE_SMOOTH);
                    g2.drawImage(scaled, x, y, null);
                    g2.dispose();
                    return canvas;
                }
            } catch (Exception ignored) {}
        }
        
        // Use default notebook icon with system accent color
        String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("notebook");
        java.awt.image.BufferedImage scaled = (res != null)
                ? main.ui.components.icons.ImageIconRenderer.get(res, ICON_SIZE, true)
                : null;

        if (scaled != null) {
            g2.drawImage(scaled, x, y, null);
        } else {
            // Fallback: simple vector placeholder
            g2.setColor(new Color(200, 200, 200));
            g2.fillRoundRect(x, y, ICON_SIZE, ICON_SIZE, 10, 10);
            g2.setColor(new Color(150, 150, 150));
            g2.drawRoundRect(x, y, ICON_SIZE, ICON_SIZE, 10, 10);
        }
        g2.dispose();
        return canvas;
    }

    private static int countEntries(NotebookInfo nb){
        if(nb==null) return 0;
        File folder = nb.getFolder();
        if(folder==null || !folder.exists()) return 0;
        File[] files = folder.listFiles((d,name)->{
            String s=name.toLowerCase();
            return s.endsWith(".txt")||s.endsWith(".md")||s.endsWith(".rtf")||s.endsWith(".note")||s.endsWith(".poem")||s.endsWith(".ntk");
        });
        return files==null?0:files.length;
    }

    private class NotebookTile extends JPanel implements MouseListener, DragGestureListener, DragSourceListener {
        private final NotebookInfo nb;
        private final DragSource dragSource;
        
        NotebookTile(NotebookInfo nb){
            this.nb = nb;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
            addMouseListener(this);
            
            // Setup drag source for clustering
            dragSource = new DragSource();
            dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);

            JLabel icon = new JLabel(new ImageIcon(createIcon(nb)));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            MouseAdapter forward = new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){ NotebookTile.this.mouseEntered(e); }
                @Override public void mouseExited(MouseEvent e){ NotebookTile.this.mouseExited(e); }
                @Override public void mouseClicked(MouseEvent e){ NotebookTile.this.mouseClicked(e); }
            };
            icon.addMouseListener(forward);
            add(icon, BorderLayout.CENTER);

            JLabel nameLbl = new JLabel(nb.getName(),SwingConstants.CENTER);
            nameLbl.setForeground(AeroTheme.TEXT_PRIMARY);
            NotebookTile.this.add(nameLbl, BorderLayout.SOUTH);
            
            // Show description tooltip if present
            if (nb.getDescription() != null && !nb.getDescription().isEmpty()) {
                setToolTipText(nb.getDescription());
            }
            
            // Setup as drop target for creating new clusters
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
                            String draggedName = (String) t.getTransferData(DataFlavor.stringFlavor);
                            if (!draggedName.equals(nb.getName())) {
                                // Create new cluster or add to existing
                                String clusterId = nb.isClustered() ? nb.getClusterId() : promptForClusterName();
                                if (clusterId != null && !clusterId.isEmpty()) {
                                    // Assign both notebooks to cluster
                                    for (NotebookInfo notebook : store.list()) {
                                        if (notebook.getName().equals(draggedName) || notebook.getName().equals(nb.getName())) {
                                            store.assignToCluster(notebook, clusterId);
                                        }
                                    }
                                    refresh();
                                }
                            }
                        }
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        dtde.dropComplete(false);
                    }
                }
                
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 149, 237), 2),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)
                    ));
                }
                
                @Override
                public void dragExit(DropTargetEvent dte) {
                    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                }
            });
        }
        
        private String promptForClusterName() {
            return CustomInputDialog.prompt(
                NotebookManagerPanel.this,
                "Create Cluster",
                "Enter a name for the new cluster:",
                ""
            );
        }
        
        // DragGestureListener
        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            StringSelection transferable = new StringSelection(nb.getName());
            dragSource.startDrag(dge, DragSource.DefaultMoveDrop, transferable, this);
        }
        
        // DragSourceListener methods
        @Override public void dragEnter(DragSourceDragEvent dsde) {}
        @Override public void dragOver(DragSourceDragEvent dsde) {}
        @Override public void dropActionChanged(DragSourceDragEvent dsde) {}
        @Override public void dragExit(DragSourceEvent dse) {}
        @Override public void dragDropEnd(DragSourceDropEvent dsde) {}
        
        private boolean hover=false;
        
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            // Accent color indicator
            Color accent = nb.getAccentColor();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g2.fillRoundRect(0, h - 6, w, 6, 4, 4);

            // Subtle hover background
            if(hover){
                g2.setColor(new Color(255,255,255,90));
                g2.fillRoundRect(0,0,w-1,h-7,arc,arc);
                g2.setColor(new Color(0,0,0,40));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1,1,w-3,h-9,arc,arc);
            }

            g2.dispose();
        }
        @Override public void mouseEntered(MouseEvent e){ hover=true; repaint(); }
        @Override public void mouseExited(MouseEvent e){ hover=false; repaint(); }
        @Override public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e)){
                if(e.getClickCount()==1){ openNotebook(nb); }
                else if(e.getClickCount()==2){ showNotebookOptions(nb); }
            } else if (SwingUtilities.isRightMouseButton(e)) {
                showContextMenu(e);
            }
        }
        @Override public void mousePressed(MouseEvent e){}
        @Override public void mouseReleased(MouseEvent e){}
        
        private void showContextMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem openItem = new JMenuItem("Open");
            openItem.addActionListener(ev -> openNotebook(nb));
            menu.add(openItem);
            
            JMenuItem editItem = new JMenuItem("Edit Settings...");
            editItem.addActionListener(ev -> showNotebookOptions(nb));
            menu.add(editItem);
            
            menu.addSeparator();
            
            if (nb.isClustered()) {
                JMenuItem removeFromCluster = new JMenuItem("Remove from Cluster");
                removeFromCluster.addActionListener(ev -> {
                    store.removeFromCluster(nb);
                    refresh();
                });
                menu.add(removeFromCluster);
            }
            
            menu.show(this, e.getX(), e.getY());
        }
    }
    
    private void showNotebookOptions(NotebookInfo nb) {
        NotebookOptionsDialog dlg = new NotebookOptionsDialog((Frame) SwingUtilities.getWindowAncestor(this), nb, store);
        dlg.setVisible(true);
        if (dlg.wasModified()) {
            refresh();
        }
    }

    private void promptNew(){
        CreateNotebookDialog dlg = new CreateNotebookDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        if(dlg.isAccepted()){
            String name = dlg.getNotebookName();
            NotebookInfo.Type type = dlg.getNotebookType();
            String description = dlg.getDescription();
            if(name!=null && !name.isEmpty()){
                try {
                    store.create(name, type, "notebook", description, -1); // Use system accent color
                    refresh();
                } catch (IllegalArgumentException ex) {
                    CustomConfirmDialog.confirm(this, "Could not create notebook", ex.getMessage());
                }
            }
        }
    }

    private void openNotebook(NotebookInfo nb){
        app.openNotebookEntries(nb);
    }

    /* Create dialog with customization options */
    private static class CreateNotebookDialog extends JDialog{
        private boolean accepted=false;
        private final TitleDividerField nameField = new TitleDividerField(24);
        private NotebookInfo.Type selectedType = NotebookInfo.Type.POETRY;
        private TypeCard poetryCard, journalCard;

        // Type-specific colors
        private static final Color POETRY_COLOR = new Color(147, 112, 219);
        private static final Color JOURNAL_COLOR = new Color(100, 149, 237);

        CreateNotebookDialog(Frame parent){
            super(parent, "Create Notebook", true);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(0, 16), 18);
            panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));

            // Header with icon and title
            JPanel header = new JPanel(new BorderLayout(12, 0));
            header.setOpaque(false);
            
            // Decorative notebook icon
            JPanel iconPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int s = Math.min(getWidth(), getHeight()) - 4;
                    int x = (getWidth() - s) / 2;
                    int y = (getHeight() - s) / 2;
                    // Draw a stylized notebook
                    g2.setColor(new Color(100, 149, 237, 40));
                    g2.fillRoundRect(x, y, s, s, 8, 8);
                    g2.setColor(new Color(100, 149, 237));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 2, y + 2, s - 4, s - 4, 6, 6);
                    // Lines inside
                    g2.setStroke(new BasicStroke(1f));
                    int lineY = y + s / 3;
                    g2.drawLine(x + 6, lineY, x + s - 6, lineY);
                    g2.drawLine(x + 6, lineY + 6, x + s - 10, lineY + 6);
                    g2.drawLine(x + 6, lineY + 12, x + s - 14, lineY + 12);
                    g2.dispose();
                }
            };
            iconPanel.setOpaque(false);
            iconPanel.setPreferredSize(new Dimension(40, 40));
            header.add(iconPanel, BorderLayout.WEST);
            
            JPanel titlePanel = new JPanel();
            titlePanel.setOpaque(false);
            titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Create New Notebook");
            title.setForeground(new Color(40, 40, 40));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel subtitle = new JLabel("Choose a type and give it a name");
            subtitle.setForeground(new Color(120, 120, 120));
            subtitle.setFont(subtitle.getFont().deriveFont(12f));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            titlePanel.add(title);
            titlePanel.add(Box.createVerticalStrut(2));
            titlePanel.add(subtitle);
            header.add(titlePanel, BorderLayout.CENTER);
            
            panel.add(header, BorderLayout.NORTH);

            // Center content
            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            // Type selection cards
            JLabel typeLabel = new JLabel("Notebook Type");
            typeLabel.setForeground(new Color(80, 80, 80));
            typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 12f));
            typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(typeLabel);
            center.add(Box.createVerticalStrut(8));

            JPanel typeCards = new JPanel(new GridBagLayout());
            typeCards.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.weightx = 1.0;
            gc.weighty = 1.0;
            gc.insets = new Insets(0, 0, 0, 8);

            poetryCard = new TypeCard(
                "Poetry",
                "For poems, verses, and creative writing",
                POETRY_COLOR,
                NotebookInfo.Type.POETRY
            );
            journalCard = new TypeCard(
                "Journal",
                "For diary entries and personal reflections",
                JOURNAL_COLOR,
                NotebookInfo.Type.JOURNAL
            );

            gc.gridx = 0;
            typeCards.add(poetryCard, gc);
            gc.gridx = 1;
            gc.insets = new Insets(0, 8, 0, 0);
            typeCards.add(journalCard, gc);

            typeCards.setAlignmentX(Component.LEFT_ALIGNMENT);
            typeCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
            center.add(typeCards);
            center.add(Box.createVerticalStrut(16));

            // Name field (title-style divider)
            nameField.setToolTipText("Enter notebook name");
            nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            nameField.setPlaceholder("Notebook name");
            try {
                String family = SettingsStore.get().getEditorFontFamily();
                int size = SettingsStore.get().getJournalFontSize();
                nameField.setFont(new Font(family, Font.PLAIN, size));
            } catch (Throwable ignored) {
                nameField.setFont(nameField.getFont().deriveFont(Font.PLAIN, 16f));
            }
            center.add(nameField);
            center.add(Box.createVerticalStrut(6));

            panel.add(center, BorderLayout.CENTER);

            // Buttons
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            btns.setOpaque(false);
            IconMenuButton cancel = new IconMenuButton("Cancel", "exit");
            cancel.setToolTipText("Cancel");
            cancel.setPreferredSize(new Dimension(84, 80));
            cancel.addActionListener(e -> { accepted = false; setVisible(false); dispose(); });
            IconMenuButton okBtn = new IconMenuButton("Create", "save");
            okBtn.setToolTipText("Create");
            okBtn.setPreferredSize(new Dimension(84, 80));
            okBtn.setEnabled(false);
            okBtn.addActionListener(e -> { accepted = true; setVisible(false); dispose(); });
            btns.add(cancel);
            btns.add(okBtn);
            panel.add(btns, BorderLayout.SOUTH);

            // Enable Create only when name entered
            nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                private void upd(){ okBtn.setEnabled(!nameField.getText().trim().isEmpty()); }
                public void insertUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e){ upd(); }
            });

            // Default selection
            updateTypeSelection(NotebookInfo.Type.POETRY);

            add(panel);
            pack();
            setSize(440, 300);
            setLocationRelativeTo(parent);
        }

        private void updateTypeSelection(NotebookInfo.Type type) {
            selectedType = type;
            poetryCard.setSelected(type == NotebookInfo.Type.POETRY);
            journalCard.setSelected(type == NotebookInfo.Type.JOURNAL);
        }

        boolean isAccepted(){ return accepted; }
        String getNotebookName(){ return nameField.getText().trim(); }
        String getDescription(){ return ""; }
        NotebookInfo.Type getNotebookType(){ return selectedType; }

        /** Card component for notebook type selection */
        private class TypeCard extends JPanel {
            private final NotebookInfo.Type type;
            private final Color accentColor;
            private boolean selected = false;
            private boolean hovered = false;

            TypeCard(String title, String description, Color accent, NotebookInfo.Type type) {
                this.type = type;
                this.accentColor = accent;
                setOpaque(false);
                setLayout(new BorderLayout(8, 4));
                setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                JPanel textPanel = new JPanel();
                textPanel.setOpaque(false);
                textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

                JLabel titleLbl = new JLabel(title);
                titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));
                titleLbl.setForeground(new Color(50, 50, 50));
                titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel descLbl = new JLabel("<html><body style='width:140px'>" + description + "</body></html>");
                descLbl.setFont(descLbl.getFont().deriveFont(11f));
                descLbl.setForeground(new Color(110, 110, 110));
                descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                textPanel.add(titleLbl);
                textPanel.add(Box.createVerticalStrut(3));
                textPanel.add(descLbl);
                add(textPanel, BorderLayout.CENTER);

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        updateTypeSelection(type);
                    }
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
            }

            void setSelected(boolean sel) {
                this.selected = sel;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int arc = 12;
                // Background
                if (selected) {
                    g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 25));
                } else if (hovered) {
                    g2.setColor(new Color(240, 242, 248));
                } else {
                    g2.setColor(new Color(250, 251, 253));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

                // Border
                if (selected) {
                    g2.setColor(accentColor);
                    g2.setStroke(new BasicStroke(2f));
                } else {
                    g2.setColor(hovered ? new Color(200, 205, 215) : new Color(220, 225, 235));
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);

                // Accent stripe on left when selected
                if (selected) {
                    g2.setColor(accentColor);
                    g2.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        }
    }

    /* Options dialog for editing existing notebooks */
    private class NotebookOptionsDialog extends JDialog{
        private boolean modified = false;
        private final NotebookStore store;
        private final NotebookInfo notebook;
        private final ModernTextField descField;
        private final JPanel iconPreview;
        private String customIconPath = null;
        
        NotebookOptionsDialog(Frame parent, NotebookInfo nb, NotebookStore store){
            super(parent, "Edit: " + nb.getName(), true);
            this.store = store;
            this.notebook = nb;
            this.customIconPath = nb.getCustomIconPath();
            
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(12,12), 16);
            panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

            // Title with notebook info (match entry/poem title styling)
            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

            TitleDividerField titleField = new TitleDividerField(24);
            titleField.setText(nb.getName());
            titleField.setPlaceholder(null);
            titleField.setEditable(false);
            titleField.setFocusable(false);
            titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
            try {
                String family = SettingsStore.get().getEditorFontFamily();
                int size = SettingsStore.get().getJournalFontSize();
                titleField.setFont(new Font(family, Font.PLAIN, size));
            } catch (Throwable ignored) {
                titleField.setFont(titleField.getFont().deriveFont(Font.PLAIN, 16f));
            }
            header.add(titleField);

            long daysAgo = (System.currentTimeMillis() - nb.getCreatedMillis()) / 86400000L;
            JLabel info = new JLabel("Created " + daysAgo + " days ago • " + countEntries(nb) + " entries");
            info.setForeground(new Color(120, 120, 120));
            info.setFont(info.getFont().deriveFont(12f));
            info.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(Box.createVerticalStrut(6));
            header.add(info);
            panel.add(header, BorderLayout.NORTH);

            // Center content
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx=0; gc.gridy=0; gc.anchor=GridBagConstraints.WEST; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; gc.insets=new Insets(8,4,8,4);

            // Description field
            JLabel descLabel = new JLabel("Description:");
            descLabel.setForeground(Color.DARK_GRAY);
            descLabel.setFont(descLabel.getFont().deriveFont(Font.BOLD, 13f));
            center.add(descLabel, gc);
            gc.gridy++;
            descField = new ModernTextField(24);
            descField.setText(nb.getDescription());
            center.add(descField, gc);
            
            // Icon section
            gc.gridy++;
            gc.insets = new Insets(14, 4, 6, 4);
            JLabel iconLabel = new JLabel("Notebook Icon:");
            iconLabel.setForeground(Color.DARK_GRAY);
            iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 13f));
            center.add(iconLabel, gc);
            
            gc.gridy++;
            gc.insets = new Insets(8, 4, 8, 4);
            JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            iconRow.setOpaque(false);
            
            // Show current icon preview
            iconPreview = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    // Draw the actual notebook icon (custom or default)
                    String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("notebook");
                    java.awt.image.BufferedImage img = null;
                    if (customIconPath != null && !customIconPath.isEmpty()) {
                        try {
                            img = javax.imageio.ImageIO.read(new java.io.File(customIconPath));
                            if (img != null) {
                                java.awt.Image scaled = img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
                                g2.drawImage(scaled, 0, 0, null);
                                g2.dispose();
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (img != null) {
                        g2.drawImage(img, 0, 0, null);
                    } else if (res != null) {
                        main.ui.components.icons.ImageIconRenderer.draw(g2, res, 0, 0, 48, this, true);
                    }
                    g2.dispose();
                }
            };
            iconPreview.setPreferredSize(new Dimension(48, 48));
            iconPreview.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));
            iconRow.add(iconPreview);
            
            // Change icon button
            IconMenuButton changeIconBtn = new IconMenuButton("Change Icon", "backgroundoptions");
            changeIconBtn.setToolTipText("Upload custom icon");
            changeIconBtn.addActionListener(e -> chooseCustomIcon());
            iconRow.add(changeIconBtn);
            
            // Remove custom icon button (only if custom icon is set)
            if (customIconPath != null && !customIconPath.isEmpty()) {
                IconMenuButton removeIconBtn = new IconMenuButton("Reset", "close");
                removeIconBtn.setToolTipText("Reset to default icon");
                removeIconBtn.addActionListener(e -> {
                    customIconPath = null;
                    iconPreview.repaint();
                });
                iconRow.add(removeIconBtn);
            }
            
            center.add(iconRow, gc);
            
            // Cluster info
            if (nb.isClustered()) {
                gc.gridy++;
                gc.insets = new Insets(6, 4, 6, 4);
                JLabel clusterLabel = new JLabel("In cluster: " + nb.getClusterId());
                clusterLabel.setForeground(new Color(100, 100, 100));
                center.add(clusterLabel, gc);
            }

            panel.add(center, BorderLayout.CENTER);

            // Buttons (IconMenuButton style)
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 8));
            btns.setOpaque(false);
            IconMenuButton saveBtn = new IconMenuButton("Save", "save");
            saveBtn.setToolTipText("Save changes");
            saveBtn.addActionListener(e->{ 
                store.updateCustomization(notebook, descField.getText().trim(), -1, customIconPath);
                modified = true;
                setVisible(false); 
                dispose(); 
            });
            IconMenuButton deleteBtn = new IconMenuButton("Delete", "delete_notebook");
            deleteBtn.setToolTipText("Delete this notebook");
            deleteBtn.addActionListener(e->{ 
                boolean confirm = CustomConfirmDialog.confirm(this, 
                    "Delete Notebook",
                    "Delete notebook '" + nb.getName() + "' and all its contents?");
                if (confirm) {
                    store.delete(nb);
                    modified = true;
                    setVisible(false);
                    dispose();
                }
            });
            IconMenuButton cancelBtn = new IconMenuButton("Cancel", "exit");
            cancelBtn.setToolTipText("Cancel and close");
            cancelBtn.addActionListener(e->{ setVisible(false); dispose(); });
            btns.add(saveBtn); btns.add(deleteBtn); btns.add(cancelBtn);
            panel.add(btns, BorderLayout.SOUTH);

            add(panel);
            pack();
            setSize(420, 420);
            setLocationRelativeTo(parent);
        }
        
        private void chooseCustomIcon() {
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Choose Notebook Icon");
            chooser.setMode(SimjotFileChooser.Mode.OPEN);
            chooser.addFileFilter("Images", "png", "jpg", "jpeg", "gif");
            File selected = chooser.showDialog();
            if (selected != null) {
                customIconPath = selected.getAbsolutePath();
                iconPreview.repaint();
            }
        }
        
        boolean wasModified(){ return modified; }
    }

    // Create the permanent tile for adding a new notebook
    private JPanel createAddTile(){
        JPanel tile = new JPanel(){
            private boolean hover=false;
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Simple background
                if(hover){
                    g2.setColor(new Color(245,245,245));
                    g2.fillRoundRect(0,0,w,h,12,12);
                }

                // Simple border
                g2.setColor(new Color(200,200,200));
                g2.drawRoundRect(0,0,w-1,h-1,12,12);

                // Center icon for creating a new notebook
                int iconSize = 42;
                String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("new");
                if (res != null) {
                    int x = (getWidth() - iconSize) / 2;
                    int y = (getHeight() - iconSize) / 2;
                    main.ui.components.icons.ImageIconRenderer.draw(g2, res, x, y, iconSize, this, true);
                }

                g2.dispose();
            }
            @Override protected void processMouseEvent(MouseEvent e){
                switch(e.getID()){
                    case MouseEvent.MOUSE_ENTERED -> { hover=true; repaint(); }
                    case MouseEvent.MOUSE_EXITED -> { hover=false; repaint(); }
                }
                super.processMouseEvent(e);
            }
        };
        tile.setPreferredSize(new Dimension(70,70));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ promptNew(); }
        });
        return tile;
    }

    // reusable rounded text field class
    private static class ModernTextField extends JTextField{
        ModernTextField(int cols){ super(cols); setOpaque(false); setBorder(BorderFactory.createEmptyBorder(6,10,6,10)); }
        @Override protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(240,240,240));
            g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
            super.paintComponent(g2);
            g2.dispose();
        }
        @Override protected void paintBorder(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
            g2.dispose();
        }
    }
}
