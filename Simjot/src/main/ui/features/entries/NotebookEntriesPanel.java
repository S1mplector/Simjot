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
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
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
            meta.setText(String.format("%s  •  Created %s  •  Last edited %s", wc+" words", df.format(created), df.format(modified)));
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
        RoundedButton backBtn = new RoundedButton("< Notebooks");
        backBtn.setBackground(new Color(240,240,240)); backBtn.setForeground(Color.DARK_GRAY);
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

        searchField.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){update();}
            public void removeUpdate(DocumentEvent e){update();}
            public void changedUpdate(DocumentEvent e){update();}
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
            public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2){ openSelected(); }
            }
        });
        add(new JScrollPane(list),BorderLayout.CENTER);

        loadFiles(); update();
    }

    private void loadFiles(){
        File folder = nb.getFolder();
        File[] arr = folder.listFiles((d,name)->{
            String s=name.toLowerCase();
            return s.endsWith(".txt")||s.endsWith(".md")||s.endsWith(".rtf")||s.endsWith(".note")||s.endsWith(".poem");
        });
        if(arr!=null){
            allFiles = Arrays.asList(arr);
            for(File f: allFiles){
                wordCounts.put(f, calculateWordCount(f));
                titles.put(f, extractTitle(f));
            }
        }
    }

    private void update(){
        String q = searchField.getText().toLowerCase();
        List<File> filtered = allFiles.stream().filter(f->f.getName().toLowerCase().contains(q)).collect(Collectors.toList());
        switch(sortBox.getSelectedIndex()){
            case 0 -> filtered.sort(Comparator.comparingLong(File::lastModified).reversed());
            case 1 -> filtered.sort(Comparator.comparingLong(File::lastModified));
            case 2 -> filtered.sort(Comparator.comparing((File fl)-> titles.getOrDefault(fl, fl.getName()), String.CASE_INSENSITIVE_ORDER));
            case 3 -> filtered.sort(Comparator.comparing((File fl)-> titles.getOrDefault(fl, fl.getName()), String.CASE_INSENSITIVE_ORDER).reversed());
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
        String title = titles.getOrDefault(f, f.getName());
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Entry", "Delete entry '"+title+"'?");
        if(ok){ f.delete(); loadFiles(); update(); }
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
    @Override public void addNotify(){ super.addNotify(); refresh(); }
} 
