package main.ui.features.entries;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.ResourceLoader;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.input.AeroTextField;
import main.ui.dialog.confirmation.CustomConfirmDialog;

public class NotebookEntriesPanel extends JPanel {
    private final JournalApp app;
    private final NotebookInfo nb;
    private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> list = new JList<>(model);
    private final JTextField searchField = new AeroTextField(20);
    private final JComboBox<String> sortBox = new JComboBox<>(new String[]{
            "Date (Newest)",
            "Date (Oldest)",
            "Name (A-Z)",
            "Name (Z-A)",
            "Word Count (High→Low)",
            "Word Count (Low→High)"});

    private final java.util.Map<File,Integer> wordCounts = new java.util.HashMap<>();
    private final java.util.Map<File,String> titles = new java.util.HashMap<>();
    private final java.util.Map<File, MetaSnapshot> metaCache = new java.util.HashMap<>();
    private List<File> allFiles = new ArrayList<>();

    // Debounced search and background metadata loader
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(200, e -> update());
    private SwingWorker<Void, FileMeta> metaLoader;

    // Debounced list rebuild to avoid frequent model churn on metadata updates
    private final javax.swing.Timer listUpdateDebounce = new javax.swing.Timer(160, e -> applyFilterSort());
    // Track which files have computed metadata and which are enqueued
    private final java.util.Set<File> metaComputed = new java.util.HashSet<>();
    private final java.util.Set<File> metaQueued = new java.util.HashSet<>();
    private javax.swing.JScrollPane listScroll;

    // Debounced folder watch refresh to coalesce rapid file system events
    private final javax.swing.Timer watchDebounce = new javax.swing.Timer(250, e -> refresh());

    // Lightweight animation when items are reordered (fade highlight)
    private final java.util.Map<File, Float> reorderAnimProgress = new java.util.HashMap<>();
    private final javax.swing.Timer reorderAnimTimer = new javax.swing.Timer(16, e -> {
        try {
            java.util.List<File> toRemove = new java.util.ArrayList<>();
            for (java.util.Map.Entry<File, Float> en : reorderAnimProgress.entrySet()) {
                Float fv = en.getValue();
                float v = (fv == null ? 0f : fv.floatValue());
                v *= 0.88f; // exponential decay
                if (v < 0.06f) {
                    toRemove.add(en.getKey());
                } else {
                    en.setValue(v);
                }
            }
            for (File f : toRemove) reorderAnimProgress.remove(f);
            if (reorderAnimProgress.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
            list.repaint();
        } catch (Throwable ignored) {}
    });

    // Folder watch
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning;

    private static class FileMeta {
        final File file; final int wc; final String title;
        FileMeta(File f, int wc, String title){ this.file=f; this.wc=wc; this.title=title; }
    }

    private static class MetaSnapshot {
        final long lastModified;
        final long length;
        final int wordCount;
        final String title;
        MetaSnapshot(long lastModified, long length, int wordCount, String title) {
            this.lastModified = lastModified;
            this.length = length;
            this.wordCount = wordCount;
            this.title = title;
        }
        boolean isFresh(File f) {
            return f != null && f.lastModified() == lastModified && f.length() == length;
        }
    }

    // Renderer for entry cards inside a notebook
    private static class EntryCardRenderer extends JPanel implements ListCellRenderer<File> {
        private final JLabel title = new JLabel();
        private final JLabel meta = new JLabel();
        private final Color cardBg = new Color(248, 249, 252);
        private final Color cardBorder = new Color(210, 216, 228);
        private final Color metaColor = new Color(105, 110, 120);
        private final Color accent = new Color(88, 133, 255);
        private final Color selectedBg = new Color(235, 240, 255);
        private final Color selectedBorder = new Color(88, 133, 255);
        private boolean selected;
        private float reorderGlow = 0f;

        // Background image cache (scaled+blurred per size)
        private static BufferedImage ENTRY_BG_BASE;
        private static final Map<String, BufferedImage> BG_CACHE = new HashMap<>();
        private static BufferedImage loadBase(){
            if (ENTRY_BG_BASE != null) return ENTRY_BG_BASE;
            Image img = ResourceLoader.createImage("img/background/entrybg.png");
            if (img == null) return null;
            int w = Math.max(1, new ImageIcon(img).getIconWidth());
            int h = Math.max(1, new ImageIcon(img).getIconHeight());
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(img, 0, 0, null);
            g2.dispose();
            ENTRY_BG_BASE = bi;
            return ENTRY_BG_BASE;
        }
        private static BufferedImage getBg(int w, int h){
            String key = w+"x"+h;
            BufferedImage cached = BG_CACHE.get(key);
            if (cached != null) return cached;
            BufferedImage base = loadBase();
            if (base == null || w <= 0 || h <= 0) return null;
            // Scale to cover (center-crop)
            double sx = w / (double) base.getWidth();
            double sy = h / (double) base.getHeight();
            double scale = Math.max(sx, sy);
            int sw = (int) Math.ceil(base.getWidth() * scale);
            int sh = (int) Math.ceil(base.getHeight() * scale);
            BufferedImage scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gs = scaled.createGraphics();
            gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gs.drawImage(base, 0, 0, sw, sh, null);
            gs.dispose();
            // Crop center to requested size
            int x = (sw - w) / 2; if (x < 0) x = 0;
            int y = (sh - h) / 2; if (y < 0) y = 0;
            BufferedImage cropped = scaled.getSubimage(x, y, Math.min(w, sw), Math.min(h, sh));
            // Apply a small blur kernel for softness
            float[] kernel = {
                    1f/16, 2f/16, 1f/16,
                    2f/16, 4f/16, 2f/16,
                    1f/16, 2f/16, 1f/16
            };
            ConvolveOp op = new ConvolveOp(new Kernel(3,3,kernel), ConvolveOp.EDGE_NO_OP, null);
            BufferedImage blurred = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_ARGB);
            op.filter(cropped, blurred);
            BG_CACHE.put(key, blurred);
            return blurred;
        }

        EntryCardRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            JPanel content = new JPanel(new BorderLayout());
            content.setOpaque(false);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
            title.setForeground(new Color(0x2B, 0x2B, 0x2B));
            meta.setFont(meta.getFont().deriveFont(12f));
            meta.setForeground(metaColor);
            content.add(title, BorderLayout.NORTH);
            content.add(meta, BorderLayout.SOUTH);
            add(content, BorderLayout.CENTER);
            // Extra left padding so text never collides with the left accent bar
            setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 12));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends File> list, File value, int index, boolean isSelected, boolean cellHasFocus) {
            // Title will be set externally via putClientProperty on the list for this renderer
            @SuppressWarnings("unchecked") Map<File,String> titles = (Map<File,String>) list.getClientProperty("titles");
            @SuppressWarnings("unchecked") Map<File,Integer> wordCounts = (Map<File,Integer>) list.getClientProperty("wordCounts");
            @SuppressWarnings("unchecked") Map<File,Float> reorderAnim = (Map<File,Float>) list.getClientProperty("reorderAnim");
            String fallback = value.getName();
            int dotIdx = fallback.lastIndexOf('.');
            if (dotIdx > 0) fallback = fallback.substring(0, dotIdx);
            String t = titles != null ? titles.get(value) : null;
            int wc = wordCounts != null ? wordCounts.getOrDefault(value, 0) : 0;
            Float glow = reorderAnim != null ? reorderAnim.get(value) : null;
            reorderGlow = glow == null ? 0f : glow;

            // Created from filename if matches yyyyMMdd_HHmmss, else fallback to modified
            Date created = new Date(value.lastModified());
            try {
                String nm = value.getName();
                String base = nm.contains(".") ? nm.substring(0, nm.lastIndexOf('.')) : nm;
                SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
                created = fmt.parse(base);
            } catch (Exception ignored) {}
            Date modified = new Date(value.lastModified());

            String displayTitle = (t==null||t.isBlank()) ? fallback : t;
            title.setText(displayTitle);
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
            String size = NotebookEntriesPanel.humanReadableSize(value.length());
            meta.setText(String.format("%s  •  %s  •  Created %s  •  Last edited %s", size, wc+" words", df.format(created), df.format(modified)));
            this.selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 14;
            int w = getWidth(), h = getHeight();
            // Clip to card shape and draw background image with reduced opacity
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(3, 2, w - 6, h - 4, arc, arc);
            java.awt.Shape oldClip = g2.getClip();
            g2.setClip(shape);
            BufferedImage bg = getBg(Math.max(1, w - 6), Math.max(1, h - 4));
            if (bg != null) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.28f));
                g2.drawImage(bg, 3, 2, null);
                g2.setComposite(old);
            } else {
                // Fallback solid fill if image missing
                g2.setColor(selected ? selectedBg : cardBg);
                g2.fill(shape);
            }
            // Overlay a soft veil to keep text readable and selected state visible
            g2.setComposite(AlphaComposite.SrcOver.derive(selected ? 0.30f : 0.20f));
            g2.setColor(selected ? selectedBg : Color.WHITE);
            g2.fill(shape);
            if (reorderGlow > 0f) {
                float a = Math.max(0f, Math.min(0.35f, reorderGlow * 0.35f));
                g2.setComposite(AlphaComposite.SrcOver.derive(a));
                g2.setColor(accent);
                g2.fill(shape);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setClip(oldClip);
            g2.setColor(accent);
            g2.fillRoundRect(6, 8, 6, h - 16, 6, 6);
            g2.setColor(selected ? selectedBorder : cardBorder);
            g2.drawRoundRect(3, 2, w - 6, h - 4, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public NotebookEntriesPanel(JournalApp app, NotebookInfo nb){
        this.app = app; this.nb = nb;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Top bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // Replace text back button with PNG back icon button
        ToolbarIconButton backBtn = new ToolbarIconButton("back");
        backBtn.setToolTipText("Back to Notebooks");
        backBtn.addActionListener(e->app.switchCard(JournalApp.NOTEBOOK_MANAGER));

        ToolbarIconButton newBtn = new ToolbarIconButton("new");
        newBtn.addActionListener(e->createNew());
        ToolbarIconButton deleteBtn = new ToolbarIconButton("delete");
        deleteBtn.addActionListener(e->deleteSelected());

        ToolbarIconButton delNbBtn = new ToolbarIconButton("trash");
        delNbBtn.addActionListener(e->deleteNotebook());

        top.add(backBtn);
        top.add(new JLabel(nb.getName()));
        top.add(Box.createHorizontalStrut(20));
        top.add(new JLabel("Search:")); top.add(searchField);
        top.add(new JLabel("Sort:")); top.add(sortBox);
        top.add(newBtn); top.add(deleteBtn); top.add(delNbBtn);
        add(top,BorderLayout.NORTH);

        // Debounce search updates to avoid frequent resorting/filtering while typing
        searchDebounce.setRepeats(false);
        searchField.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e){ searchDebounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e){ searchDebounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e){ searchDebounce.restart(); }
        });
        sortBox.setUI(new ModernComboBoxUI());
        sortBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        sortBox.addActionListener(e->update());

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Attach shared maps for renderer access
        list.putClientProperty("titles", titles);
        list.putClientProperty("wordCounts", wordCounts);
        list.putClientProperty("reorderAnim", reorderAnimProgress);
        list.setBackground(new Color(247, 247, 249));
        list.setFixedCellHeight(84);
        list.setCellRenderer(new EntryCardRenderer());
        list.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2){ openSelected(); }
            }
        });
        listScroll = new JScrollPane(list);
        add(listScroll,BorderLayout.CENTER);
        // Prioritize metadata for visible items on scroll/resize
        try {
            listScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
                if (!e.getValueIsAdjusting()) ensureMetaForVisibleRange();
            });
        } catch (Throwable t) {
            logWarn("Failed to attach scroll listener", t);
        }

        // Configure debouncers
        listUpdateDebounce.setRepeats(false);
        watchDebounce.setRepeats(false);
        reorderAnimTimer.setRepeats(true);

        loadFiles();
        update();
        // After initial layout, compute meta for visible items first
        SwingUtilities.invokeLater(this::ensureMetaForVisibleRange);
    }

    private void loadFiles(){
        // Cancel any ongoing metadata load
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }

        File folder = nb.getFolder();
        java.util.Set<String> exts = app.getEditorFactory().getRegisteredExtensions();
        File[] arr = folder.listFiles((d,name)->{
            // Exclude hidden dotfiles (e.g., legacy metadata like .journal_templates.txt)
            if (name.startsWith(".")) return false;
            String s = name.toLowerCase();
            int dot = s.lastIndexOf('.');
            String ext = dot>=0 ? s.substring(dot) : "";
            return exts.contains(ext);
        });
        if (arr != null) {
            allFiles = Arrays.asList(arr);
            java.util.Set<File> current = new java.util.HashSet<>(allFiles);
            // Prune caches for removed files but preserve known titles to avoid flicker
            titles.keySet().retainAll(current);
            wordCounts.keySet().retainAll(current);
            metaComputed.retainAll(current);
            synchronized (metaQueued) { metaQueued.retainAll(current); }
            metaCache.keySet().retainAll(current);

            // Seed provisional values for new files
            java.util.Map<File, MetaSnapshot> refreshedCache = new java.util.HashMap<>();
            for (File f : allFiles) {
                MetaSnapshot snap = metaCache.get(f);
                if (snap != null && snap.isFresh(f)) {
                    titles.put(f, snap.title);
                    wordCounts.put(f, snap.wordCount);
                    metaComputed.add(f);
                    refreshedCache.put(f, snap);
                } else {
                    metaComputed.remove(f);
                    synchronized (metaQueued) { metaQueued.remove(f); }
                }
                if (!titles.containsKey(f)) {
                    String nm = f.getName();
                    int dot = nm.lastIndexOf('.');
                    titles.put(f, (dot > 0 ? nm.substring(0, dot) : nm));
                }
                if (!wordCounts.containsKey(f)) wordCounts.put(f, 0);
            }
            metaCache.clear();
            metaCache.putAll(refreshedCache);
            // Start prioritized metadata loading (visible first)
            startPrioritizedMetaLoader(java.util.List.copyOf(allFiles));
        } else {
            allFiles = java.util.Collections.emptyList();
            titles.clear();
            wordCounts.clear();
            metaComputed.clear();
            metaQueued.clear();
            metaCache.clear();
        }
    }

    private void update(){
        // Debounce model rebuilds
        listUpdateDebounce.restart();
    }

    private void applyFilterSort(){
        // Preserve selection and scroll to minimize visible twitch
        File sel = list.getSelectedValue();
        int scrollVal = 0;
        try { scrollVal = listScroll.getVerticalScrollBar().getValue(); } catch (Throwable ignored) {}

        String q = searchField.getText()==null? "" : searchField.getText().toLowerCase();
        List<File> filtered = allFiles.stream().filter(f -> {
            String name = f.getName().toLowerCase();
            String title = java.util.Objects.toString(titles.get(f), f.getName()).toLowerCase();
            return name.contains(q) || title.contains(q);
        }).collect(Collectors.toList());
        switch(sortBox.getSelectedIndex()){
            case 0 -> filtered.sort(Comparator
                    .comparingLong(File::lastModified)
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
            case 1 -> filtered.sort(Comparator
                    .comparingLong(File::lastModified)
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
            case 2 -> filtered.sort(Comparator
                    .comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
            case 3 -> filtered.sort(Comparator
                    .comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER)
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
            case 4 -> filtered.sort(Comparator
                    .comparingInt((File f)->wordCounts.getOrDefault(f,0))
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
            case 5 -> filtered.sort(Comparator
                    .comparingInt((File f)->wordCounts.getOrDefault(f,0))
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
        }
        // If order hasn't changed, skip rebuild to avoid flicker
        boolean sameOrder = (model.size() == filtered.size());
        if (sameOrder) {
            for (int i = 0; i < model.size(); i++) {
                if (!Objects.equals(model.get(i), filtered.get(i))) { sameOrder = false; break; }
            }
        }
        if (!sameOrder) {
            java.util.Set<File> changed = updateModelWithMinimalChanges(filtered);
            bumpReorderAnimation(changed);
        }

        // Restore selection without forcing scroll
        if (sel != null && filtered.contains(sel)) {
            list.setSelectedValue(sel, false);
        }
        // Restore approximate scroll position
        try {
            JScrollBar bar = listScroll.getVerticalScrollBar();
            bar.setValue(Math.min(scrollVal, Math.max(0, bar.getMaximum() - bar.getVisibleAmount())));
        } catch (Throwable ignored) {}

        // After resort/filter, make sure visible items are prioritized for metadata
        ensureMetaForVisibleRange();
    }

    // Returns set of files that were inserted or moved
    private java.util.Set<File> updateModelWithMinimalChanges(java.util.List<File> target){
        java.util.Set<File> changed = new java.util.HashSet<>();
        int i = 0;
        while (i < target.size()) {
            File desired = target.get(i);
            if (i < model.size() && java.util.Objects.equals(model.get(i), desired)) {
                i++;
                continue;
            }
            int existingIdx = indexOfInModel(desired, i+1);
            if (existingIdx >= 0) {
                // Move existing item to new position
                model.remove(existingIdx);
                model.add(i, desired);
                changed.add(desired);
            } else {
                // Insert new item
                model.add(i, desired);
                changed.add(desired);
            }
            i++;
        }
        // Remove trailing extras
        while (model.size() > target.size()) {
            model.remove(model.size()-1);
        }
        return changed;
    }

    private int indexOfInModel(File f, int start){
        for (int i = Math.max(0, start); i < model.size(); i++) {
            if (java.util.Objects.equals(model.get(i), f)) return i;
        }
        return -1;
    }

    private void bumpReorderAnimation(java.util.Set<File> files){
        if (files == null || files.isEmpty()) return;
        for (File f : files) reorderAnimProgress.put(f, 1f);
        list.repaint();
        if (!reorderAnimTimer.isRunning()) {
            try { reorderAnimTimer.start(); } catch (Throwable ignored) {}
        }
    }

    private void createNew(){
        app.openNewEntryEditor(nb);
    }

    private void deleteSelected(){
        File f = list.getSelectedValue();
        if(f==null) return;
        String title = java.util.Objects.toString(titles.get(f), f.getName());
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Entry", "Delete entry '"+title+"'?");
        if(!ok) return;
        if(!f.exists()){
            loadFiles(); update();
            return;
        }
        boolean deleted = false;
        try {
            deleted = f.delete();
        } catch (SecurityException se) {
            deleted = false;
        }
        if(!deleted){
            JOptionPane.showMessageDialog(this, "Could not delete '"+title+"'.", "Delete Failed", JOptionPane.ERROR_MESSAGE);
        }
        loadFiles(); update();
    }

    private void deleteNotebook(){
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Notebook", "Delete entire notebook '"+nb.getName()+"'?" );
        if(!ok) return;
        // Access store to delete
        new main.core.service.NotebookStore().delete(nb);
        // refresh manager panel
        app.refreshNotebookManager();
        app.switchCard(JournalApp.NOTEBOOK_MANAGER);
    }

    private void openSelected(){ File f=list.getSelectedValue(); if(f!=null) openFile(f); }

    private void openFile(File f){
        app.openExistingEntryEditor(nb, f);
    }

    private int calculateWordCount(File f){
        // Skip expensive counting for very large files to keep UI responsive
        if (f.length() > 1_500_000L) {
            return 0;
        }
        int count = 0;
        try(Scanner sc=new Scanner(f)){
            while(sc.hasNext()){
                sc.next(); count++;
            }
        }catch(Exception ex){
            logWarn("Word count failed for " + f.getName(), ex);
        }
        return count;
    }

    private String extractTitle(File f){
        String nm = f.getName();
        String lower = nm.toLowerCase();
        if(lower.endsWith(".note")||lower.endsWith(".poem")||lower.endsWith(".txt")||lower.endsWith(".rtf")){
            try(BufferedReader br = new BufferedReader(new FileReader(f))){
                String first = br.readLine();
                if(first!=null && !first.isBlank()) return first.trim();
            }catch(Exception ignore){}
        }
        // fallback: strip extension
        int dot = nm.lastIndexOf('.');
        return dot>0? nm.substring(0,dot): nm;
    }

    /** Reload the file list and update the UI */
    public void refresh(){
        loadFiles();
        update();
    }

    

    // ensure list refresh when panel becomes visible
    @Override public void addNotify(){
        super.addNotify();
        startWatching();
        refresh();
        SwingUtilities.invokeLater(this::ensureMetaForVisibleRange);
    }

    @Override public void removeNotify(){
        stopWatching();
        super.removeNotify();
    }

    private void startWatching(){
        stopWatching();
        try {
            Path path = nb.getFolder().toPath();
            watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchRunning = true;
            watchThread = new Thread(() -> {
                while (watchRunning) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            // Debounce via EDT timer to coalesce rapid saves/changes
                            SwingUtilities.invokeLater(() -> {
                                try { watchDebounce.restart(); } catch (Throwable ignored2) {}
                            });
                        }
                        key.reset();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        if (watchRunning) {
                            logWarn("Notebook watch error", ex);
                        }
                    }
                }
            }, "NotebookEntriesWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (Exception ex) {
            // If watch service fails, log and continue without auto-refresh
            logWarn("File watch setup failed", ex);
        }
    }

    private void stopWatching(){
        watchRunning = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (Exception ex) { logWarn("WatchService close failed", ex); }
            watchService = null;
        }
    }

    // Prioritize metadata loading for currently visible items
    private void ensureMetaForVisibleRange(){
        try {
            int first = list.getFirstVisibleIndex();
            int last = list.getLastVisibleIndex();
            if (first < 0 || last < 0 || last < first) return;
            java.util.List<File> visible = new java.util.ArrayList<>();
            for (int i = first; i <= last && i < model.size(); i++) {
                File f = model.get(i);
                if (f != null) visible.add(f);
            }
            if (!visible.isEmpty()) {
                startPrioritizedMetaLoader(visible);
            }
        } catch (Throwable ignored) {}
    }

    private void startPrioritizedMetaLoader(java.util.List<File> preferredFirst){
        if (preferredFirst == null) preferredFirst = java.util.Collections.emptyList();
        // Cancel ongoing worker to re-prioritize
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }
        java.util.LinkedHashSet<File> order = new java.util.LinkedHashSet<>(preferredFirst);
        for (File f : allFiles) order.add(f);
        metaLoader = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                for (File f : order) {
                    if (isCancelled()) break;
                    if (metaComputed.contains(f)) continue;
                    synchronized (metaQueued) {
                        if (metaQueued.contains(f)) continue;
                        metaQueued.add(f);
                    }
                    int wc = calculateWordCount(f);
                    String t = extractTitle(f);
                    publish(new FileMeta(f, wc, t));
                    if (isCancelled()) break;
                }
                return null;
            }
            @Override protected void process(java.util.List<FileMeta> chunks) {
                for (FileMeta m : chunks) {
                    titles.put(m.file, m.title);
                    wordCounts.put(m.file, m.wc);
                    metaComputed.add(m.file);
                    metaCache.put(m.file, new MetaSnapshot(
                            m.file.lastModified(),
                            m.file.length(),
                            m.wc,
                            m.title
                    ));
                }
                update();
            }
        };
        metaLoader.execute();
    }

    // --- Helpers ---
    static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static void logWarn(String msg, Throwable t){
        System.err.println("[NotebookEntriesPanel] " + msg + (t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : ""));
        if (t != null) t.printStackTrace(System.err);
    }
}
