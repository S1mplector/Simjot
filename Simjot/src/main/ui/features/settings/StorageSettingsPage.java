/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.settings;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import main.ui.app.AppConfig;
import main.ui.app.AppLifecycle;
import main.ui.app.JournalApp;
import main.core.security.EncryptionManager;
import main.core.service.SettingsStore;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.backup.BackupManager;
import main.infrastructure.backup.BackupService;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.monitoring.AppPerf;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.file.SimjotFileChooser;
import main.ui.dialog.security.EncryptionUnlockDialog;

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

        JPanel content = new JPanel();
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Page header
        content.add(SettingsUi.header("Storage", "Folders, backups and paths"));
        content.add(Box.createVerticalStrut(10));

        JPanel rootInfo = createSectionPanel("Data location", "Current Simjot root folder");
        JPanel rootBody = new JPanel();
        rootBody.setOpaque(false);
        rootBody.setLayout(new BoxLayout(rootBody, BoxLayout.Y_AXIS));

        pathLbl = new JLabel(AppDirectories.getRoot().getAbsolutePath());
        pathLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        pathLbl.setForeground(new Color(0, 0, 0, 150));
        pathLbl.setToolTipText(pathLbl.getText());
        pathLbl.setAlignmentX(LEFT_ALIGNMENT);
        rootBody.add(pathLbl);
        rootInfo.add(rootBody, BorderLayout.CENTER);
        content.add(rootInfo);

        content.add(Box.createVerticalStrut(12));

        // Directory tree with sizes and descriptions
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
        treeScroll.setOpaque(true);
        treeScroll.getViewport().setOpaque(true);
        treeScroll.getViewport().setBackground(Color.WHITE);
        treeScroll.setBorder(BorderFactory.createLineBorder(new Color(225, 231, 238)));
        treeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel treeSection = createSectionPanel("Storage structure", "Folders, descriptions and sizes");
        treeSection.add(treeScroll, BorderLayout.CENTER);
        content.add(treeSection);
        // Ensure the tree is tall enough to show all rows without scrolling
        adjustTreeHeight(treeScroll);

        // Actions
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setAlignmentX(LEFT_ALIGNMENT);

        String openLabel = AppLifecycle.isMacOS() ? "Open in Finder" : "Open in Explorer";
        RoundedButton openBtn = new RoundedButton(openLabel);
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(AppDirectories.getRoot());
            } catch (Exception ignored) {}
        });
        actions.add(openBtn);
        actions.add(Box.createHorizontalStrut(8));

        RoundedButton refreshBtn = new RoundedButton("Refresh sizes");
        refreshBtn.addActionListener(e -> computeSizesAsync());
        actions.add(refreshBtn);

        content.add(Box.createVerticalStrut(8));
        content.add(actions);

        if (AppLifecycle.isMacOS()) {
            JPanel icloudCard = createSectionPanel("iCloud Sync", "Sync your Simjot folder across Macs");

            JPanel icloudBody = new JPanel();
            icloudBody.setOpaque(false);
            icloudBody.setLayout(new BoxLayout(icloudBody, BoxLayout.Y_AXIS));

            File icloudRoot = AppDirectories.suggestedIcloudRoot();
            boolean icloudAvailable = icloudRoot != null;
            boolean rootInIcloud = AppDirectories.isIcloudRoot(AppDirectories.getRoot());

            JLabel status = new JLabel();
            status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
            status.setForeground(new Color(0,0,0,160));

            if (!icloudAvailable) {
                status.setText("iCloud Drive not detected. Sign in to iCloud to enable sync.");
            } else if (rootInIcloud) {
                status.setText("Your Simjot data is already stored in iCloud Drive.");
            } else {
                status.setText("Move your Simjot folder to iCloud Drive for automatic sync.");
            }
            icloudBody.add(status);

            if (icloudAvailable) {
                JLabel icloudPath = new JLabel(icloudRoot.getAbsolutePath());
                icloudPath.setFont(new Font("Monospaced", Font.PLAIN, 11));
                icloudPath.setForeground(new Color(0,0,0,120));
                icloudPath.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
                icloudBody.add(icloudPath);
            }

            // Sync status section (uses CloudSyncManager)
            if (rootInIcloud) {
                icloudBody.add(Box.createVerticalStrut(12));
                JPanel syncStatusPanel = createSyncStatusPanel();
                icloudBody.add(syncStatusPanel);
            }

            if (icloudAvailable && !rootInIcloud) {
                icloudBody.add(Box.createVerticalStrut(8));
                RoundedButton moveBtn = new RoundedButton("Move to iCloud");
                moveBtn.putClientProperty("iconId", "open_folder");
                moveBtn.addActionListener(e -> startIcloudMigration(icloudRoot, moveBtn));
                icloudBody.add(moveBtn);
            }

            icloudCard.add(icloudBody, BorderLayout.CENTER);
            content.add(Box.createVerticalStrut(12));
            content.add(icloudCard);
        }

        // Backup settings (moved from General) — placed at bottom
        content.add(Box.createVerticalStrut(12));

        JPanel backupsCard = createSectionPanel("Backups", "Automatic backups and manual trigger");

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
            apply(); // Save current settings (including destination) before backup
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() {
                    try { return main.infrastructure.backup.BackupService.get().triggerNow(StorageSettingsPage.this); } catch (Throwable ignored) {}
                    return false;
                }
                @Override protected void done() {
                    backupNowBtn.setEnabled(true);
                    boolean ok = false;
                    try { ok = get(); } catch (Throwable ignored) {}
                    if (ok) {
                        main.ui.components.toast.ToastOverlay.success("Backup completed");
                    } else {
                        main.ui.components.toast.ToastOverlay.error("Backup failed");
                    }
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
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(StorageSettingsPage.this), "Select Backup Destination");
            chooser.setMode(SimjotFileChooser.Mode.DIRECTORY);
            if (!backupDestField.getText().isBlank()) {
                File current = new File(backupDestField.getText());
                if (current.isDirectory()) chooser.setCurrentDirectory(current);
            }
            File selected = chooser.showDialog();
            if (selected != null) {
                backupDestField.setText(selected.getAbsolutePath());
                // Persist immediately so the path is saved even if user doesn't navigate away
                SettingsStore.get().setBackupDestinationPath(selected.getAbsolutePath());
                SettingsStore.get().save();
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
        openBackups.addActionListener(ev -> {
            try {
                String d = backupDestField.getText();
                File backupRoot = (d==null || d.isBlank()) ? new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups") : new File(d);
                Desktop.getDesktop().open(backupRoot);
            } catch (Exception ignored) {}
        });
        RoundedButton restoreBtn = new RoundedButton("Restore…");
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

        JPanel pageRoot = new JPanel(new BorderLayout());
        pageRoot.setOpaque(true);
        pageRoot.setBackground(Color.WHITE);
        pageRoot.add(content, BorderLayout.NORTH);
        add(pageRoot, BorderLayout.CENTER);
    }

    private JPanel createSectionPanel(String title, String subtitle) {
        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel sectionTitle = new JLabel(title);
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD));
        JLabel sectionSubtitle = new JLabel(subtitle);
        sectionSubtitle.setFont(sectionSubtitle.getFont().deriveFont(Font.PLAIN, 11f));
        sectionSubtitle.setForeground(new Color(0, 0, 0, 120));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(sectionTitle);
        header.add(sectionSubtitle);
        section.add(header, BorderLayout.NORTH);
        return section;
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
                case WALLPAPERS:
                case CUSTOM_FONTS: {
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
            case CUSTOM_FONTS: return "Custom fonts";
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

    private void startIcloudMigration(File icloudRoot, RoundedButton button) {
        if (icloudRoot == null) {
            CustomMessageDialog.display(this, "iCloud Sync", "iCloud Drive is not available on this Mac.", true);
            return;
        }

        File currentRoot = AppDirectories.getRoot();
        if (AppDirectories.isIcloudRoot(currentRoot)) {
            CustomMessageDialog.display(this, "iCloud Sync", "Simjot is already using iCloud Drive.", false);
            return;
        }

        if (AppDirectories.looksLikeSimjotRoot(icloudRoot)) {
            boolean switchNow = CustomConfirmDialog.confirm(
                this,
                "iCloud Sync",
                "An iCloud Simjot folder already exists. Switch to it on next restart?"
            );
            if (switchNow) {
                if (AppConfig.saveRootFolder(icloudRoot)) {
                    promptRestart("iCloud folder selected. Restart to use it now?");
                } else {
                    CustomMessageDialog.display(this, "iCloud Sync", "Failed to update the data location.", true);
                }
            }
            return;
        }

        boolean ok = CustomConfirmDialog.confirm(
            this,
            "Move to iCloud",
            "This will copy your current Simjot data to iCloud Drive and keep the local folder as a backup. Continue?"
        );
        if (!ok) return;

        String originalText = button.getText();
        button.setEnabled(false);
        button.setText("Migrating...");

        final java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    java.nio.file.Path src = currentRoot.toPath().toAbsolutePath().normalize();
                    java.nio.file.Path dst = icloudRoot.toPath().toAbsolutePath().normalize();
                    if (src.equals(dst)) return true;
                    if (dst.startsWith(src)) {
                        throw new IllegalStateException("iCloud path cannot be inside the current root.");
                    }
                    java.nio.file.Files.createDirectories(dst);
                    if (!NativeAccess.verifyWritable(dst.toString())) {
                        throw new java.io.IOException("iCloud folder is not writable.");
                    }
                    long bytesNeeded = folderSize(currentRoot);
                    if (bytesNeeded > 0) {
                        FileIO.ensureSpace(dst, bytesNeeded, "iCloud migration");
                    }
                    FileIO.copyDirectory(src, dst, true);
                    try { main.infrastructure.ffi.NativeAccess.setupInit(icloudRoot.getAbsolutePath()); } catch (Throwable ignored) {}
                    if (!main.infrastructure.ffi.NativeAccess.isSetupComplete(icloudRoot.getAbsolutePath())) {
                        throw new java.io.IOException("iCloud folder verification failed.");
                    }
                    return true;
                } catch (Throwable t) {
                    err.set(t);
                    return false;
                }
            }

            @Override
            protected void done() {
                button.setText(originalText);
                button.setEnabled(true);
                boolean success = false;
                try {
                    success = get();
                } catch (Throwable t) {
                    err.set(t);
                }
                if (!success) {
                    String msg = "Migration failed.";
                    Throwable t = err.get();
                    if (t != null && t.getMessage() != null && !t.getMessage().isBlank()) {
                        msg += " " + t.getMessage();
                    }
                    CustomMessageDialog.display(StorageSettingsPage.this, "iCloud Sync", msg, true);
                    return;
                }
                if (!AppConfig.saveRootFolder(icloudRoot)) {
                    CustomMessageDialog.display(StorageSettingsPage.this, "iCloud Sync", "Copy complete, but config update failed.", true);
                    return;
                }
                promptRestart("iCloud copy complete. Restart to use the new location?");
            }
        };
        worker.execute();
    }

    private void promptRestart(String message) {
        boolean restart = CustomConfirmDialog.confirm(this, "Restart Required", message);
        if (!restart) return;
        java.awt.Window win = SwingUtilities.getWindowAncestor(this);
        if (win instanceof JournalApp app) {
            app.restartAfterSettingsChange();
        } else {
            AppLifecycle.relaunch();
            System.exit(0);
        }
    }

    private void doRestore() {
        try {
            String d = backupDestField.getText();
            File defaultRoot = (d==null || d.isBlank()) ? new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups") : new File(d);
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Choose a backup folder or .sjbackup file to restore");
            chooser.setMode(SimjotFileChooser.Mode.OPEN);
            if (defaultRoot.isDirectory()) {
                chooser.setCurrentDirectory(defaultRoot);
            }
            File selected = chooser.showDialog();
            if (selected == null || (!selected.isDirectory() && !selected.isFile())) return;
            boolean isEncryptedFile = selected.isFile() && selected.getName().toLowerCase().endsWith(".sjbackup");
            if (selected.isDirectory()) {
                boolean looksBackup = new File(selected, "notebooks").exists() || new File(selected, "notebooks.json").exists();
                if (!looksBackup) {
                    try { main.ui.dialog.message.CustomMessageDialog.display(this, "Restore", "This folder does not look like a Simjot backup.", true); } catch (Throwable ignored) {}
                    return;
                }
            } else if (!isEncryptedFile) {
                try { main.ui.dialog.message.CustomMessageDialog.display(this, "Restore", "This file does not look like a Simjot backup.", true); } catch (Throwable ignored) {}
                return;
            }
            boolean ok = CustomConfirmDialog.confirm(SwingUtilities.getWindowAncestor(this), "Restore Backup",
                    "This will overwrite your current data with the selected backup. Continue?");
            if (!ok) return;
            final char[] restorePassword;
            final boolean rememberPassword;
            if (isEncryptedFile) {
                if (EncryptionManager.hasPasswordSet()) {
                    String pw = EncryptionManager.getPasswordForUse(this, true);
                    if (pw == null || pw.isBlank()) return;
                    restorePassword = pw.toCharArray();
                    rememberPassword = false;
                } else {
                    EncryptionUnlockDialog.Result resPass = EncryptionUnlockDialog.prompt(this);
                    if (resPass == null || resPass.password == null || resPass.password.length == 0) return;
                    restorePassword = Arrays.copyOf(resPass.password, resPass.password.length);
                    rememberPassword = resPass.remember;
                    Arrays.fill(resPass.password, '\0');
                }
            } else {
                restorePassword = null;
                rememberPassword = false;
            }

            new SwingWorker<Throwable, Void>() {
                @Override protected Throwable doInBackground() {
                    try {
                        if (isEncryptedFile) {
                            BackupManager.restoreFromBackup(selected, AppDirectories.getRoot(), new String(restorePassword));
                        } else {
                            BackupManager.restoreFromBackup(selected, AppDirectories.getRoot());
                        }
                    } catch (Throwable t) {
                        return t;
                    }
                    return null;
                }
                @Override protected void done() {
                    Throwable err = null;
                    try { err = get(); } catch (Throwable ignored) {}
                    if (err == null) {
                        if (isEncryptedFile && rememberPassword && restorePassword != null) {
                            EncryptionManager.cacheSessionPassword(restorePassword);
                        }
                        main.ui.components.toast.ToastOverlay.success("Restore completed");
                    } else {
                        main.ui.components.toast.ToastOverlay.error("Restore failed");
                    }
                    if (restorePassword != null) Arrays.fill(restorePassword, '\0');
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
                g2.setColor(new Color(0, 0, 0, 26));
                g2.fillRoundRect(2, 2, getWidth()-4, getHeight()-4, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private static class IconCanvas extends JPanel {
            IconCanvas() { setOpaque(false); setPreferredSize(new Dimension(28, 28)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                String res = ImageIconRenderer.mapIdToResource("open_folder");
                if (res != null) {
                    ImageIconRenderer.draw(g2, res, 2, 4, 20, this, false);
                } else {
                    int x = 2, y = 5; int fw = 20, fh = 14;
                    drawFolderIcon(g2, x, y, fw, fh, new Color(255, 204, 77));
                }
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

    // ---- Cloud Sync Status Panel ----
    private JPanel createSyncStatusPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0, 0, 0, 30)),
            BorderFactory.createEmptyBorder(8, 0, 0, 0)
        ));

        JLabel syncTitle = new JLabel("Sync Status");
        syncTitle.setFont(syncTitle.getFont().deriveFont(Font.BOLD, 11f));
        syncTitle.setForeground(new Color(0, 0, 0, 180));
        panel.add(syncTitle);
        panel.add(Box.createVerticalStrut(6));

        // Status labels
        JLabel stateLabel = new JLabel("State: Idle");
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.PLAIN, 11f));
        stateLabel.setForeground(new Color(0, 0, 0, 140));
        panel.add(stateLabel);

        JLabel networkLabel = new JLabel("Network: Unknown");
        networkLabel.setFont(networkLabel.getFont().deriveFont(Font.PLAIN, 11f));
        networkLabel.setForeground(new Color(0, 0, 0, 140));
        panel.add(networkLabel);

        JLabel conflictLabel = new JLabel("Conflicts: 0");
        conflictLabel.setFont(conflictLabel.getFont().deriveFont(Font.PLAIN, 11f));
        conflictLabel.setForeground(new Color(0, 0, 0, 140));
        panel.add(conflictLabel);

        JLabel metricsLabel = new JLabel("Success rate: --");
        metricsLabel.setFont(metricsLabel.getFont().deriveFont(Font.PLAIN, 11f));
        metricsLabel.setForeground(new Color(0, 0, 0, 140));
        panel.add(metricsLabel);

        // Buttons
        panel.add(Box.createVerticalStrut(8));
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

        RoundedButton syncNowBtn = new RoundedButton("Sync Now");
        syncNowBtn.addActionListener(e -> {
            main.infrastructure.io.IcloudSyncService.triggerSync();
            main.ui.components.toast.ToastOverlay.info("Sync triggered");
        });
        buttons.add(syncNowBtn);

        buttons.add(Box.createHorizontalStrut(8));

        RoundedButton resolveBtn = new RoundedButton("Resolve Conflicts");
        resolveBtn.addActionListener(e -> {
            int resolved = main.infrastructure.io.IcloudSyncService.resolveAllConflictsKeepLocal();
            if (resolved > 0) {
                main.ui.components.toast.ToastOverlay.success("Resolved " + resolved + " conflicts");
            } else {
                main.ui.components.toast.ToastOverlay.info("No conflicts to resolve");
            }
        });
        buttons.add(resolveBtn);
        panel.add(buttons);

        // Timer to update status periodically
        Timer updateTimer = new Timer(2000, e -> {
            main.infrastructure.io.CloudSyncManager mgr = main.infrastructure.io.CloudSyncManager.getInstance();
            if (!mgr.isInitialized()) {
                stateLabel.setText("State: Not initialized");
                return;
            }

            int state = mgr.getState();
            String stateStr = switch (state) {
                case main.infrastructure.io.CloudSyncManager.STATE_IDLE -> "Idle";
                case main.infrastructure.io.CloudSyncManager.STATE_SCANNING -> "Scanning...";
                case main.infrastructure.io.CloudSyncManager.STATE_SYNCING -> "Syncing...";
                case main.infrastructure.io.CloudSyncManager.STATE_RESOLVING -> "Resolving...";
                case main.infrastructure.io.CloudSyncManager.STATE_ERROR -> "Error";
                default -> "Unknown";
            };
            stateLabel.setText("State: " + stateStr);

            int net = mgr.getNetworkState();
            String netStr = switch (net) {
                case main.infrastructure.io.CloudSyncManager.NETWORK_DISCONNECTED -> "Disconnected";
                case main.infrastructure.io.CloudSyncManager.NETWORK_WIFI -> "WiFi";
                case main.infrastructure.io.CloudSyncManager.NETWORK_CELLULAR -> "Cellular";
                case main.infrastructure.io.CloudSyncManager.NETWORK_WIRED -> "Wired";
                default -> "Unknown";
            };
            networkLabel.setText("Network: " + netStr);

            int conflicts = mgr.getConflictCount();
            conflictLabel.setText("Conflicts: " + conflicts);
            conflictLabel.setForeground(conflicts > 0 ? new Color(200, 100, 0) : new Color(0, 0, 0, 140));

            float successRate = mgr.getSuccessRate();
            long totalBytes = mgr.getTotalBytesSynced();
            String bytesStr = formatBytes(totalBytes);
            metricsLabel.setText(String.format("Success: %.1f%% | Synced: %s", successRate, bytesStr));
        });
        updateTimer.setInitialDelay(100);
        updateTimer.start();

        // Stop timer when panel is removed
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                if (!panel.isDisplayable()) {
                    updateTimer.stop();
                }
            }
        });

        return panel;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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
