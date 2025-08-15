package main.ui.features.entries;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.text.SimpleDateFormat;
import java.nio.file.*;
import main.infrastructure.backup.NotebookInfo;
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
    private List<File> allFiles = new ArrayList<>();

    // Debounced search and background metadata loader
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(200, e -> update());
    private SwingWorker<Void, FileMeta> metaLoader;

    // Folder watch
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning;

    private static class FileMeta {
        final File file; final int wc; final String title;
        FileMeta(File f, int wc, String title){ this.file=f; this.wc=wc; this.title=title; }
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
            String t = titles != null ? titles.getOrDefault(value, value.getName()) : value.getName();
            int wc = wordCounts != null ? wordCounts.getOrDefault(value, 0) : 0;

            // Created from filename if matches yyyyMMdd_HHmmss, else fallback to modified
            Date created = new Date(value.lastModified());
            try {
                String nm = value.getName();
                String base = nm.contains(".") ? nm.substring(0, nm.lastIndexOf('.')) : nm;
                SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
                created = fmt.parse(base);
            } catch (Exception ignored) {}
            Date modified = new Date(value.lastModified());

            title.setText((t==null||t.isBlank())?"Untitled":t);
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
            g2.setColor(selected ? selectedBg : cardBg);
            g2.fillRoundRect(3, 2, w - 6, h - 4, arc, arc);
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
        list.setBackground(new Color(247, 247, 249));
        list.setFixedCellHeight(84);
        list.setCellRenderer(new EntryCardRenderer());
        list.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2){ openSelected(); }
            }
        });
        add(new JScrollPane(list),BorderLayout.CENTER);

        loadFiles(); update();
    }

    private void loadFiles(){
        // Cancel any ongoing metadata load
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }

        File folder = nb.getFolder();
        java.util.Set<String> exts = app.getEditorFactory().getRegisteredExtensions();
        File[] arr = folder.listFiles((d,name)->{
            String s = name.toLowerCase();
            int dot = s.lastIndexOf('.');
            String ext = dot>=0 ? s.substring(dot) : "";
            return exts.contains(ext);
        });
        titles.clear();
        wordCounts.clear();
        if(arr!=null){
            allFiles = Arrays.asList(arr);
            // Seed placeholders fast on EDT
            for(File f: allFiles){
                titles.put(f, null);
                wordCounts.put(f, 0);
            }

            // Load real metadata in background and live-update UI
            metaLoader = new SwingWorker<>() {
                @Override protected Void doInBackground() {
                    for (File f : allFiles) {
                        if (isCancelled()) break;
                        int wc = calculateWordCount(f);
                        String t = extractTitle(f);
                        publish(new FileMeta(f, wc, t));
                    }
                    return null;
                }
                @Override protected void process(java.util.List<FileMeta> chunks) {
                    for (FileMeta m : chunks) {
                        titles.put(m.file, m.title);
                        wordCounts.put(m.file, m.wc);
                    }
                    // Re-apply filter/sort as data enriches
                    update();
                }
            };
            metaLoader.execute();
        } else {
            allFiles = java.util.Collections.emptyList();
        }
    }

    private void update(){
        String q = searchField.getText()==null? "" : searchField.getText().toLowerCase();
        List<File> filtered = allFiles.stream().filter(f -> {
            String name = f.getName().toLowerCase();
            String title = java.util.Objects.toString(titles.get(f), f.getName()).toLowerCase();
            return name.contains(q) || title.contains(q);
        }).collect(Collectors.toList());
        switch(sortBox.getSelectedIndex()){
            case 0 -> filtered.sort(Comparator.comparingLong(File::lastModified).reversed());
            case 1 -> filtered.sort(Comparator.comparingLong(File::lastModified));
            case 2 -> filtered.sort(Comparator.comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER));
            case 3 -> filtered.sort(Comparator.comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER).reversed());
            case 4 -> filtered.sort(Comparator.comparingInt((File f)->wordCounts.getOrDefault(f,0)).reversed());
            case 5 -> filtered.sort(Comparator.comparingInt((File f)->wordCounts.getOrDefault(f,0)));
        }
        model.clear();
        filtered.forEach(model::addElement);
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
        int count = 0;
        try(Scanner sc=new Scanner(f)){
            while(sc.hasNext()){
                sc.next(); count++;
            }
        }catch(Exception ignored){}
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
                            // Debounce a bit by coalescing via Swing EDT
                            SwingUtilities.invokeLater(this::refresh);
                        }
                        key.reset();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ignored) {
                        // ignore and continue
                    }
                }
            }, "NotebookEntriesWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (Exception ignored) {
            // If watch service fails, we simply won't auto-refresh
        }
    }

    private void stopWatching(){
        watchRunning = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (Exception ignored) {}
            watchService = null;
        }
    }

    // --- Helpers ---
    static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
