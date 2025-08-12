package main.ui.panels;

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
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.buttons.ToolbarIconButton;
import main.ui.components.AeroTextField;
import main.ui.components.ModernComboBoxUI;
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
        list.setCellRenderer((JList<? extends File> l, File f, int i, boolean s, boolean fcs)->{
            String name = titles.getOrDefault(f, f.getName());
            int wc = wordCounts.getOrDefault(f, 0);
            JLabel lab=new JLabel(name+"  ("+wc+" words)");
            lab.setOpaque(true);
            lab.setBackground(s?new Color(0,120,215,30):Color.WHITE);
            return lab;
        });
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
