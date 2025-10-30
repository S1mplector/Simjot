package main.ui.features.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.RoundedButton;
import javax.swing.Timer;
import main.infrastructure.monitoring.AppPerf;
import main.core.service.SettingsStore;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.infrastructure.backup.BackupService;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.infrastructure.backup.BackupManager;

class StorageSettingsPage extends JPanel implements SettingsPage {
    private final JLabel pathLbl;
    private JTree dirTree;
    private DefaultTreeModel treeModel;
    private final Timer spinnerTimer;
    private float spinnerAngle = 0f;

    // Backup controls
    private final JComboBox<String> backupFreqBox;
    private final JSpinner backupKeepSpin;
    private final javax.swing.JButton backupNowBtn;
    private final JTextField backupDestField;
    private final JSpinner backupPruneDaysSpin;
    private final JCheckBox includeMoodChk;
    private final JCheckBox includeSettingsChk;
    private final JCheckBox includeWallpapersChk;
    private final JCheckBox verifyChk;
    private final JCheckBox onExitAlwaysChk;

    StorageSettingsPage() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Color.WHITE);

        // Center contents using a wrapper panel with GridBagLayout
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.NONE;

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Page header
        content.add(SettingsUi.header("Storage", "Folders, backups and paths"));
        content.add(Box.createVerticalStrut(8));

        // Title and path (centered)
        JLabel title = new JLabel("Simjot root folder:");
        title.setAlignmentX(0.5f);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        // allow full-width so CENTER alignment is visible
        Dimension titlePref = title.getPreferredSize();
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, titlePref.height));
        content.add(title);

        pathLbl = new JLabel(AppDirectories.getRoot().getAbsolutePath());
        pathLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pathLbl.setForeground(new Color(0,0,0,150));
        pathLbl.setAlignmentX(0.5f);
        pathLbl.setHorizontalAlignment(SwingConstants.CENTER);
        Dimension pathPref = pathLbl.getPreferredSize();
        pathLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, pathPref.height));
        content.add(Box.createVerticalStrut(4));
        content.add(pathLbl);

        content.add(Box.createVerticalStrut(12));

        // Directory tree with sizes and descriptions (Aero-like glass card)
        treeModel = buildTreeModel();
        dirTree = new JTree(treeModel);
        dirTree.setRootVisible(true);
        dirTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        dirTree.setRowHeight(44);
        dirTree.setOpaque(false);
        dirTree.setCellRenderer(new StorageTreeCellRenderer());
        // Expand root so all folder rows are visible
        try { dirTree.expandRow(0); } catch (Exception ignored) {}

        JScrollPane treeScroll = new JScrollPane(dirTree);
        treeScroll.setPreferredSize(new Dimension(480, 260));
        treeScroll.setOpaque(false);
        treeScroll.getViewport().setOpaque(false);
        treeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel glassCard = new GlassCardPanel();
        glassCard.setLayout(new BorderLayout());
        glassCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel treeTitle = new JLabel("Storage structure");
        treeTitle.setFont(treeTitle.getFont().deriveFont(Font.BOLD));
        JLabel treeSub = new JLabel("Folders, descriptions and sizes");
        treeSub.setFont(treeSub.getFont().deriveFont(Font.PLAIN, 11f));
        treeSub.setForeground(new Color(0,0,0,120));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(treeTitle);
        header.add(treeSub);
        glassCard.add(header, BorderLayout.NORTH);
        glassCard.add(treeScroll, BorderLayout.CENTER);
        content.add(glassCard);
        // Ensure the tree is tall enough to show all rows without scrolling
        adjustTreeHeight(treeScroll);

        // Push subsequent controls to the bottom area
        content.add(Box.createVerticalGlue());

        // Actions
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setAlignmentX(0.5f);

        RoundedButton openBtn = new RoundedButton("Open in Explorer");
        // Use RoundedButton's iconId mechanism so custom painter draws the icon
        openBtn.putClientProperty("iconId", "explorer");
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(AppDirectories.getRoot());
            } catch (Exception ignored) {}
        });
        actions.add(openBtn);
        actions.add(Box.createHorizontalStrut(8));

        RoundedButton refreshBtn = new RoundedButton("Refresh sizes");
        refreshBtn.putClientProperty("iconId", "refreshsizes");
        refreshBtn.addActionListener(e -> computeSizesAsync());
        actions.add(refreshBtn);

        RoundedButton revealBtn = new RoundedButton("Reveal selected");
        revealBtn.putClientProperty("iconId", "revealselected");
        revealBtn.addActionListener(e -> revealSelected());
        actions.add(Box.createHorizontalStrut(8));
        actions.add(revealBtn);

        content.add(Box.createVerticalStrut(8));
        content.add(actions);

        // Backup settings (moved from General) — placed at bottom
        content.add(Box.createVerticalStrut(12));

        JPanel backupsCard = new GlassCardPanel();
        backupsCard.setLayout(new BorderLayout());
        backupsCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        backupsCard.setAlignmentX(0.5f);
        JLabel backupsTitle = new JLabel("Backups");
        backupsTitle.setFont(backupsTitle.getFont().deriveFont(Font.BOLD));
        JLabel backupsSub = new JLabel("Automatic backups and manual trigger");
        backupsSub.setFont(backupsSub.getFont().deriveFont(Font.PLAIN, 11f));
        backupsSub.setForeground(new Color(0,0,0,120));

        JPanel backupsHeader = new JPanel();
        backupsHeader.setOpaque(false);
        backupsHeader.setLayout(new BoxLayout(backupsHeader, BoxLayout.Y_AXIS));
        backupsHeader.add(backupsTitle);
        backupsHeader.add(backupsSub);
        backupsCard.add(backupsHeader, BorderLayout.NORTH);

        JPanel backups = new JPanel(new GridBagLayout());
        backups.setOpaque(false);
        GridBagConstraints bgc = new GridBagConstraints();
        bgc.insets = new Insets(5, 5, 5, 5);
        bgc.fill = GridBagConstraints.HORIZONTAL;

        SettingsStore store = SettingsStore.get();

        String[] freqs = new String[] { "Off", "Daily", "Weekly", "Monthly" };
        backupFreqBox = new JComboBox<>(freqs);
        backupFreqBox.setUI(new ModernComboBoxUI());
        backupFreqBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        backupFreqBox.setSelectedItem(store.getBackupFrequency());
        bgc.gridx = 0; bgc.gridy = 0; backups.add(SettingsUi.label("Auto-backup:"), bgc);
        bgc.gridx = 1; backups.add(backupFreqBox, bgc);

        backupKeepSpin = new JSpinner(new SpinnerNumberModel(store.getBackupKeepCount(), 1, 100, 1));
        backupKeepSpin.setUI(new ModernSpinnerUI());
        ((JSpinner.DefaultEditor) backupKeepSpin.getEditor()).getTextField().setColumns(3);
        bgc.gridx = 0; bgc.gridy = 1; backups.add(SettingsUi.label("Keep last N backups:"), bgc);
        bgc.gridx = 1; backups.add(backupKeepSpin, bgc);

        backupNowBtn = new main.ui.components.buttons.RoundedButton("Backup Now");
        backupNowBtn.addActionListener(e -> {
            backupNowBtn.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    try { main.infrastructure.backup.BackupService.get().triggerNow(); } catch (Throwable ignored) {}
                    return null;
                }
                @Override protected void done() {
                    backupNowBtn.setEnabled(true);
                    try { main.ui.dialog.message.CustomMessageDialog.display(StorageSettingsPage.this, "Backup", "Backup completed.", false); } catch (Throwable ignored) {}
                }
            }.execute();
        });
        bgc.gridx = 0; bgc.gridy = 2; bgc.gridwidth = 2; backups.add(backupNowBtn, bgc);

        bgc.gridy = 3; bgc.gridwidth = 1; bgc.gridx = 0; backups.add(SettingsUi.label("Destination:"), bgc);
        JPanel destRow = new JPanel(new BorderLayout(6,0)); destRow.setOpaque(false);
        String dest = store.getBackupDestinationPath();
        backupDestField = new JTextField(dest==null?"":dest);
        backupDestField.setColumns(24);
        javax.swing.JButton chooseDest = new RoundedButton("Choose…");
        chooseDest.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!backupDestField.getText().isBlank()) fc.setCurrentDirectory(new File(backupDestField.getText()));
            int res = fc.showOpenDialog(StorageSettingsPage.this);
            if (res == JFileChooser.APPROVE_OPTION && fc.getSelectedFile()!=null) {
                backupDestField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        destRow.add(backupDestField, BorderLayout.CENTER);
        destRow.add(chooseDest, BorderLayout.EAST);
        bgc.gridx = 1; backups.add(destRow, bgc);

        includeMoodChk = new JCheckBox("Include mood data", store.isBackupIncludeMood()); includeMoodChk.setOpaque(false); includeMoodChk.setUI(new ModernCheckBoxUI());
        includeSettingsChk = new JCheckBox("Include app settings", store.isBackupIncludeSettings()); includeSettingsChk.setOpaque(false); includeSettingsChk.setUI(new ModernCheckBoxUI());
        includeWallpapersChk = new JCheckBox("Include wallpapers", store.isBackupIncludeWallpapers()); includeWallpapersChk.setOpaque(false); includeWallpapersChk.setUI(new ModernCheckBoxUI());
        verifyChk = new JCheckBox("Verify after backup", store.isBackupVerify()); verifyChk.setOpaque(false); verifyChk.setUI(new ModernCheckBoxUI());
        onExitAlwaysChk = new JCheckBox("Always backup on exit", store.isBackupOnExitAlways()); onExitAlwaysChk.setOpaque(false); onExitAlwaysChk.setUI(new ModernCheckBoxUI());

        bgc.gridy = 4; bgc.gridx = 0; backups.add(SettingsUi.label("Includes:"), bgc);
        JPanel includesRow = new JPanel(); includesRow.setOpaque(false); includesRow.setLayout(new BoxLayout(includesRow, BoxLayout.Y_AXIS));
        includesRow.add(includeMoodChk);
        includesRow.add(includeSettingsChk);
        includesRow.add(includeWallpapersChk);
        bgc.gridx = 1; backups.add(includesRow, bgc);

        bgc.gridy = 5; bgc.gridx = 0; backups.add(SettingsUi.label("Safeguards:"), bgc);
        JPanel safeRow = new JPanel(); safeRow.setOpaque(false); safeRow.setLayout(new BoxLayout(safeRow, BoxLayout.Y_AXIS));
        safeRow.add(verifyChk);
        safeRow.add(onExitAlwaysChk);
        bgc.gridx = 1; backups.add(safeRow, bgc);

        backupPruneDaysSpin = new JSpinner(new SpinnerNumberModel(store.getBackupPruneDays(), 0, 3650, 1));
        backupPruneDaysSpin.setUI(new ModernSpinnerUI());
        ((JSpinner.DefaultEditor) backupPruneDaysSpin.getEditor()).getTextField().setColumns(4);
        bgc.gridy = 6; bgc.gridx = 0; backups.add(SettingsUi.label("Prune backups older than (days):"), bgc);
        bgc.gridx = 1; backups.add(backupPruneDaysSpin, bgc);

        JPanel bottomActions = new JPanel(); bottomActions.setOpaque(false); bottomActions.setLayout(new BoxLayout(bottomActions, BoxLayout.X_AXIS));
        RoundedButton openBackups = new RoundedButton("Open Backups Folder");
        openBackups.putClientProperty("iconId", "explorer");
        openBackups.addActionListener(ev -> {
            try {
                String d = backupDestField.getText();
                File backupRoot = (d==null || d.isBlank()) ? new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups") : new File(d);
                Desktop.getDesktop().open(backupRoot);
            } catch (Exception ignored) {}
        });
        RoundedButton restoreBtn = new RoundedButton("Restore…");
        restoreBtn.putClientProperty("iconId", "load");
        restoreBtn.addActionListener(ev -> doRestore());
        bottomActions.add(openBackups);
        bottomActions.add(Box.createHorizontalStrut(8));
        bottomActions.add(restoreBtn);
        bgc.gridx = 0; bgc.gridy = 7; bgc.gridwidth = 2; backups.add(bottomActions, bgc);

        backupsCard.add(backups, BorderLayout.CENTER);
        content.add(backupsCard);

        // Interactions: double-click open, context menu
        installTreeInteractions();

        // Compute sizes asynchronously on load
        computeSizesAsync();

        // Spinner animation timer for renderer
        spinnerTimer = new Timer(AppPerf.getAnimationDelay(), e -> {
            spinnerAngle += 0.08f;
            if (spinnerAngle > Math.PI * 2) spinnerAngle -= Math.PI * 2;
            if (dirTree != null) dirTree.repaint();
        });
        spinnerTimer.start();

        gc.gridx = 0; gc.gridy = 0;
        centerWrapper.add(content, gc);
        add(centerWrapper, BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public void apply() {
        // Persist backup settings here
        try {
            SettingsStore store = SettingsStore.get();
            store.setBackupFrequency((String) backupFreqBox.getSelectedItem());
            store.setBackupKeepCount((Integer) backupKeepSpin.getValue());
            store.setBackupDestinationPath(backupDestField.getText());
            store.setBackupPruneDays((Integer) backupPruneDaysSpin.getValue());
            store.setBackupIncludeMood(includeMoodChk.isSelected());
            store.setBackupIncludeSettings(includeSettingsChk.isSelected());
            store.setBackupIncludeWallpapers(includeWallpapersChk.isSelected());
            store.setBackupVerify(verifyChk.isSelected());
            store.setBackupOnExitAlways(onExitAlwaysChk.isSelected());
            try { BackupService.get().start(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    // clearThumbs action removed: thumbnails cache button no longer used

    // ---- Tree helpers ----
    private DefaultTreeModel buildTreeModel() {
        File root = AppDirectories.getRoot();
        StorageNode rootInfo = StorageNode.root(root);
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootInfo);
        // Show a consolidated 'notebooks' folder instead of entries/poems,
        // and omit drawings and tasks (since there is no save/load for them).
        File notebooks = new File(root, "notebooks"); // do not create; just show
        StorageNode nbInfo = new StorageNode(null, notebooks, "notebooks",
                "Notebooks (entries and poems)", null, true);
        rootNode.add(new DefaultMutableTreeNode(nbInfo));

        // Include only the relevant remaining app folders
        for (AppDirectories.Type t : AppDirectories.Type.values()) {
            switch (t) {
                case MOOD_DATA:
                case SETTINGS:
                case WALLPAPERS: {
                    File f = AppDirectories.folder(t);
                    StorageNode info = StorageNode.leaf(t, f, t.folderName(), describe(t));
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(info);
                    rootNode.add(child);
                    break;
                }
                // Skip ENTRIES, POEMS, DRAWINGS, TASKS intentionally
                default: break;
            }
        }
        return new DefaultTreeModel(rootNode);
    }

    private static String describe(AppDirectories.Type t) {
        switch (t) {
            case ENTRIES: return "Journal entries";
            case POEMS: return "Poems";
            case DRAWINGS: return "Drawings & thumbnails";
            case MOOD_DATA: return "Mood tracking data";
            case SETTINGS: return "App settings";
            case NOTEBOOKS: return "Notebooks (entries and poems)";
            case TASKS: return "Tasks";
            case WALLPAPERS: return "Wallpapers";
            default: return t.name();
        }
    }

    private static long folderSize(File f) {
        if (f == null || !f.exists()) return 0L;
        if (f.isFile()) return f.length();
        long total = 0L;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) {
                total += folderSize(c);
            }
        }
        return total;
    }

    private static String formatSize(long bytes) {
        double b = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (b >= 1024 && idx < units.length - 1) {
            b /= 1024.0;
            idx++;
        }
        return String.format(idx == 0 ? "%.0f %s" : "%.1f %s", b, units[idx]);
    }

    // Ensure the scroll container is tall enough to show all rows without scrolling
    private void adjustTreeHeight(JScrollPane scroll) {
        int rows = dirTree.getRowCount();
        int rowH = dirTree.getRowHeight() > 0 ? dirTree.getRowHeight() : 24;
        int headerH = 40; // title + subtitle area inside glass card
        int vPad = 16;    // inner padding of glass card
        int contentH = rows * rowH;
        int totalH = headerH + contentH + vPad;
        Dimension pref = scroll.getPreferredSize();
        Dimension newSize = new Dimension(pref.width, totalH);
        scroll.setPreferredSize(newSize);
        scroll.setMinimumSize(newSize);
        dirTree.setPreferredSize(new Dimension(pref.width - 24, contentH));
        scroll.revalidate();
    }

    // ---- Interactions & actions ----
    private void installTreeInteractions() {
        dirTree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = dirTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) openPath(path);
                }
                if (e.isPopupTrigger() || (e.getButton() == MouseEvent.BUTTON3)) {
                    showContextMenu(e);
                }
            }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e); }
        });
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = dirTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        dirTree.setSelectionPath(path);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(a -> openPath(path));
        JMenuItem copy = new JMenuItem("Copy path");
        copy.addActionListener(a -> copyPath(path));
        menu.add(open);
        menu.add(copy);
        menu.show(dirTree, e.getX(), e.getY());
    }

    private void openPath(TreePath path) {
        Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(last instanceof StorageNode)) return;
        StorageNode n = (StorageNode) last;
        try { Desktop.getDesktop().open(n.file); } catch (Exception ignored) {}
    }

    private void copyPath(TreePath path) {
        Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(last instanceof StorageNode)) return;
        StorageNode n = (StorageNode) last;
        try {
            java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(n.file.getAbsolutePath());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
        } catch (Exception ignored) {}
    }

    private void revealSelected() {
        TreePath sel = dirTree.getSelectionPath();
        if (sel == null) return;
        openPath(sel);
    }

    private void doRestore() {
        try {
            String d = backupDestField.getText();
            File defaultRoot = (d==null || d.isBlank()) ? new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups") : new File(d);
            JFileChooser fc = new JFileChooser(defaultRoot);
            fc.setDialogTitle("Choose a backup folder to restore");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int res = fc.showOpenDialog(this);
            if (res != JFileChooser.APPROVE_OPTION) return;
            File selected = fc.getSelectedFile();
            if (selected == null || !selected.isDirectory()) return;
            boolean looksBackup = new File(selected, "notebooks").exists() || new File(selected, "notebooks.json").exists();
            if (!looksBackup) {
                try { main.ui.dialog.message.CustomMessageDialog.display(this, "Restore", "This folder does not look like a Simjot backup.", true); } catch (Throwable ignored) {}
                return;
            }
            boolean ok = CustomConfirmDialog.confirm(SwingUtilities.getWindowAncestor(this), "Restore Backup",
                    "This will overwrite your current data with the selected backup. Continue?");
            if (!ok) return;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    try { BackupManager.restoreFromBackup(selected, AppDirectories.getRoot()); } catch (Throwable ignored) {}
                    return null;
                }
                @Override protected void done() {
                    try { main.ui.dialog.message.CustomMessageDialog.display(StorageSettingsPage.this, "Restore", "Restore completed.", false); } catch (Throwable ignored) {}
                }
            }.execute();
        } catch (Throwable ignored) {}
    }

    // ---- Async sizes ----
    private void computeSizesAsync() {
        // Mark as loading
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object uo = child.getUserObject();
            if (uo instanceof StorageNode) {
                StorageNode sn = (StorageNode) uo;
                sn.size = null; // unknown
                sn.loading = true;
                treeModel.nodeChanged(child);
            }
        }

        new SwingWorker<Void, NodeSizeUpdate>() {
            @Override protected Void doInBackground() {
                for (int i = 0; i < root.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                    Object uo = child.getUserObject();
                    if (uo instanceof StorageNode) {
                        StorageNode sn = (StorageNode) uo;
                        long size = folderSize(sn.file);
                        publish(new NodeSizeUpdate(child, size));
                    }
                }
                return null;
            }

            @Override protected void process(java.util.List<NodeSizeUpdate> chunks) {
                for (NodeSizeUpdate upd : chunks) {
                    Object uo = ((DefaultMutableTreeNode) upd.node).getUserObject();
                    if (uo instanceof StorageNode) {
                        StorageNode sn = (StorageNode) uo;
                        sn.size = upd.size;
                        sn.loading = false;
                        treeModel.nodeChanged(upd.node);
                    }
                }
            }
        }.execute();
    }

    private static class NodeSizeUpdate {
        final DefaultMutableTreeNode node; final long size;
        NodeSizeUpdate(DefaultMutableTreeNode node, long size) { this.node = node; this.size = size; }
    }

    // ---- Model ----
    private static class StorageNode {
        final AppDirectories.Type type; // null for root
        final File file;
        final String name;
        final String description; // null for root
        volatile Long size; // null when loading/unknown
        volatile boolean loading;

        private StorageNode(AppDirectories.Type type, File file, String name, String description, Long size, boolean loading) {
            this.type = type; this.file = file; this.name = name; this.description = description; this.size = size; this.loading = loading;
        }
        static StorageNode root(File f) { return new StorageNode(null, f, f.getName(), null, null, false); }
        static StorageNode leaf(AppDirectories.Type t, File f, String name, String desc) { return new StorageNode(t, f, name, desc, null, true); }
        @Override public String toString() { return name; }
    }

    // ---- Renderer ----
    private class StorageTreeCellRenderer extends JPanel implements javax.swing.tree.TreeCellRenderer {
        private final JLabel nameLbl = new JLabel();
        private final JLabel descLbl = new JLabel();
        private final SizeBadgeLabel sizeLbl = new SizeBadgeLabel();
        private final SpinnerCanvas spinner = new SpinnerCanvas();
        private final JPanel rightPanel = new JPanel();
        private boolean selected;

        StorageTreeCellRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD));
            descLbl.setFont(descLbl.getFont().deriveFont(11f));
            descLbl.setForeground(new Color(0,0,0,140));
            text.add(nameLbl);
            text.add(descLbl);

            add(new IconCanvas(), BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            rightPanel.setOpaque(false);
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
            spinner.setAlignmentY(0.5f);
            sizeLbl.setAlignmentY(0.5f);
            rightPanel.add(spinner);
            rightPanel.add(Box.createHorizontalStrut(6));
            rightPanel.add(sizeLbl);
            add(rightPanel, BorderLayout.EAST);
        }

        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            selected = sel;
            Object uo = ((DefaultMutableTreeNode) value).getUserObject();
            if (uo instanceof StorageNode) {
                StorageNode n = (StorageNode) uo;
                nameLbl.setText(n.name);
                descLbl.setText(n.description == null ? n.file.getAbsolutePath() : n.description);
                boolean loading = (n.size == null);
                spinner.setVisible(loading);
                sizeLbl.setVisible(!loading);
                sizeLbl.setText(loading ? "" : formatSize(n.size));
            } else {
                nameLbl.setText(String.valueOf(value));
                descLbl.setText("");
                spinner.setVisible(false);
                sizeLbl.setVisible(false);
            }
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                int arc = 12;
                g2.setColor(new Color(90, 150, 255, 60));
                g2.fillRoundRect(2, 2, getWidth()-4, getHeight()-4, arc, arc);
                g2.setColor(new Color(90, 150, 255, 100));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private static class IconCanvas extends JPanel {
            IconCanvas() { setOpaque(false); setPreferredSize(new Dimension(28, 28)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = 2, y = 5; int fw = 20, fh = 14;
                drawFolderIcon(g2, x, y, fw, fh, new Color(255, 204, 77));
                g2.dispose();
            }
        }

        private class SpinnerCanvas extends JPanel {
            SpinnerCanvas() { setOpaque(false); setPreferredSize(new Dimension(16, 16)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int r = Math.min(w, h) / 2 - 2;
                int cx = w / 2, cy = h / 2;
                float thickness = Math.max(2f, Math.min(w, h) * 0.18f);
                g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color base = new Color(0, 120, 215);
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 60));
                g2.drawArc(cx - r, cy - r, r * 2, r * 2, 0, 360);
                g2.setColor(base);
                g2.drawArc(cx - r, cy - r, r * 2, r * 2, (int) Math.toDegrees(spinnerAngle), 90);
                g2.dispose();
            }
        }
    }

    private static class SizeBadgeLabel extends JLabel {
        private boolean muted;
        private final int padX = 8;
        private final int padY = 4;
        void setMuted(boolean m) { this.muted = m; repaint(); }
        @Override public boolean isOpaque() { return false; }
        @Override public Dimension getPreferredSize() {
            String text = getText();
            if (text == null || text.isEmpty()) return new Dimension(0, 0);
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text) + padX * 2;
            int h = fm.getAscent() + fm.getDescent() + padY * 2;
            return new Dimension(w, h);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            String text = getText();
            if (text == null || text.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text) + padX * 2;
            int h = fm.getAscent() + fm.getDescent() + padY * 2;
            int x = 0;
            int y = Math.max(0, (getHeight() - h) / 2);
            int arc = h;
            Color bg = muted ? new Color(0,0,0,20) : new Color(0,0,0,35);
            Color fg = new Color(0,0,0,180);
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, arc, arc);
            g2.setColor(fg);
            int textX = x + padX;
            int textY = y + padY + fm.getAscent();
            g2.drawString(text, textX, textY);
            g2.dispose();
        }
    }

    // ---- Glass card panel ----
    private static class GlassCardPanel extends JPanel {
        GlassCardPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            // Intentionally draw nothing to remove outer frames around sections.
            // Keep transparency so the panel acts as a simple layout container
            // while preserving any inner padding set via borders.
            super.paintComponent(g);
        }
    }

    // ---- Vector icon drawing ----
    private static void drawFolderIcon(Graphics2D g2, int x, int y, int w, int h, Color base) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color darker = base.darker();
        int tabH = Math.max(4, h/3);
        // tab
        g2.setColor(base);
        g2.fillRoundRect(x, y, (int)(w*0.55), tabH, 4, 4);
        // body
        g2.setColor(base);
        g2.fillRoundRect(x, y+tabH-2, w, h-tabH+2, 4, 4);
        // outline
        g2.setColor(darker);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, (int)(w*0.55), tabH, 4, 4);
        g2.drawRoundRect(x, y+tabH-2, w, h-tabH+2, 4, 4);
    }
}
