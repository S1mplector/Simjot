package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import javax.swing.*;
import main.infrastructure.io.AppDirectories;
import main.ui.JournalApp;
import main.ui.dialog.CustomConfirmDialog;
import main.ui.dialog.CustomMessageDialog;

public class ViewEntriesPanel extends JPanel {
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JournalApp app;

    private JList<String> journalList, poemList;
    private File[] journalFiles, poemFiles;
    private JTextPane previewPane;

    public ViewEntriesPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        initUI();

        // Reload data each time the panel is shown.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                reloadData();
            }
        });
    }

    private void initUI() {
        // Header
        JLabel headerLabel = new JLabel("View All Entries", SwingConstants.CENTER);
        headerLabel.setForeground(Color.BLACK);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        add(headerLabel, BorderLayout.NORTH);

        // Grid Panel for two columns, with extra gaps
        JPanel gridPanel = new JPanel(new GridLayout(1, 2, 15, 15));
        gridPanel.setBackground(Color.WHITE);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Journal Column in a RoundedPanel
        RoundedPanel journalPanel = new RoundedPanel();
        journalPanel.setLayout(new BorderLayout(5, 5));
        journalPanel.setBackground(new Color(245, 245, 245, 200));
        JLabel journalLabel = new JLabel("Journal Entries", SwingConstants.CENTER);
        journalLabel.setForeground(Color.BLACK);
        journalLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        journalPanel.add(journalLabel, BorderLayout.NORTH);

        DefaultListModel<String> journalModel = new DefaultListModel<>();
        journalList = new JList<>(journalModel);
        journalList.setBackground(new Color(255, 255, 255));
        journalList.setForeground(Color.BLACK);
        journalList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        journalList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane journalScroll = new JScrollPane(journalList);
        journalScroll.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        journalPanel.add(journalScroll, BorderLayout.CENTER);
        gridPanel.add(journalPanel);

        // Poem Column
        RoundedPanel poemPanel = new RoundedPanel();
        poemPanel.setLayout(new BorderLayout(5, 5));
        poemPanel.setBackground(new Color(245, 245, 245, 200));
        JLabel poemLabel = new JLabel("Poem Entries", SwingConstants.CENTER);
        poemLabel.setForeground(Color.BLACK);
        poemLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        poemPanel.add(poemLabel, BorderLayout.NORTH);

        DefaultListModel<String> poemModel = new DefaultListModel<>();
        poemList = new JList<>(poemModel);
        poemList.setBackground(new Color(255, 255, 255));
        poemList.setForeground(Color.BLACK);
        poemList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        poemList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane poemScroll = new JScrollPane(poemList);
        poemScroll.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        poemPanel.add(poemScroll, BorderLayout.CENTER);
        gridPanel.add(poemPanel);


        add(gridPanel, BorderLayout.CENTER);

        // --- Preview Pane ---
        previewPane = new JTextPane();
        previewPane.setEditable(false);
        previewPane.setFont(new Font("Serif", Font.ITALIC, 14));
        previewPane.setBackground(new Color(250, 250, 235)); // A very light cream
        previewPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10,10,10,10),
            BorderFactory.createTitledBorder("Preview")
        ));
        previewPane.setPreferredSize(new Dimension(0, 120)); // Give it some initial height
        add(previewPane, BorderLayout.SOUTH);

        // Bottom panel with "Delete" and "Back"
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomPanel.setBackground(Color.WHITE);

        RoundedButton deleteButton = new RoundedButton("Delete Entry");
        deleteButton.setBackground(Color.DARK_GRAY);
        deleteButton.setForeground(Color.WHITE);
        deleteButton.addActionListener(e -> deleteSelectedEntry());
        bottomPanel.add(deleteButton);

        RoundedButton backButton = new RoundedButton("Back");
        backButton.setBackground(Color.DARK_GRAY);
        backButton.setForeground(Color.WHITE);
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        bottomPanel.add(backButton);

        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setOpaque(false);
        southWrapper.add(previewPane, BorderLayout.NORTH);
        southWrapper.add(bottomPanel, BorderLayout.CENTER);
        add(southWrapper, BorderLayout.SOUTH);

        // Add hover listeners to each list
        addHoverListener(journalList, journalFiles, EntryType.JOURNAL);
        addHoverListener(poemList, poemFiles, EntryType.POEM);

        // Add double-click listeners for each list
        journalList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = journalList.locationToIndex(e.getPoint());
                    if (journalFiles != null && index >= 0 && index < journalFiles.length) {
                        openJournalFile(journalFiles[index]);
                    }
                }
            }
        });
        poemList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = poemList.locationToIndex(e.getPoint());
                    if (poemFiles != null && index >= 0 && index < poemFiles.length) {
                        openPoemFile(poemFiles[index]);
                    }
                }
            }
        });
    }

    private enum EntryType { JOURNAL, POEM }

    private void addHoverListener(JList<String> list, File[] files, EntryType type) {
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index > -1) {
                    // Update this based on the latest file list from reloadData
                    File[] currentFiles;
                    switch (type) {
                        case JOURNAL: currentFiles = journalFiles; break;
                        case POEM:    currentFiles = poemFiles; break;
                        default:      return;
                    }
                    if (currentFiles != null && index < currentFiles.length) {
                        showPreview(currentFiles[index]);
                    }
                }
            }
        });
        // Add listener to clear preview when mouse leaves the list
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                previewPane.setText("");
            }
        });
    }

    private void showPreview(File file) {
        // For journal and poem files, treat as plain text
        StringBuilder previewContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // Skip title line
            reader.readLine(); // Skip blank line
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 5) {
                previewContent.append(line).append("\n");
                lineCount++;
            }
            if (line != null) {
                previewContent.append("...");
            }
        } catch (IOException ex) {
            previewPane.setText("Error reading preview.");
            return;
        }
        previewPane.setText(previewContent.toString());
        previewPane.setCaretPosition(0); // Scroll to top
    }

    public void reloadData() {
        // Reload Journal Entries
        DefaultListModel<String> journalModel = (DefaultListModel<String>) journalList.getModel();
        journalModel.clear();
        File entriesFolder = AppDirectories.folder(AppDirectories.Type.ENTRIES);
        journalFiles = (entriesFolder.exists() && entriesFolder.isDirectory()) ?
            entriesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"))
            : new File[0];
        if (journalFiles != null) {
            Arrays.sort(journalFiles, (f1, f2) ->
                Long.compare(f2.lastModified(), f1.lastModified())
            );
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm");
            for (File f : journalFiles) {
                String dateTime = sdf.format(new Date(f.lastModified()));
                String title;
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    title = reader.readLine();
                    if (title == null || title.trim().isEmpty()) title = "Untitled";
                } catch (IOException ex) {
                    ex.printStackTrace();
                    title = "Error reading file";
                }
                journalModel.addElement(dateTime + " - " + title);
            }
        }

        // Reload Poem Entries
        DefaultListModel<String> poemModel = (DefaultListModel<String>) poemList.getModel();
        poemModel.clear();
        File poemsFolder = AppDirectories.folder(AppDirectories.Type.POEMS);
        poemFiles = (poemsFolder.exists() && poemsFolder.isDirectory())
            ? poemsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".poem"))
            : new File[0];
        if (poemFiles != null) {
            Arrays.sort(poemFiles, (f1, f2) ->
                Long.compare(f2.lastModified(), f1.lastModified())
            );
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm");
            for (File f : poemFiles) {
                String dateTime = sdf.format(new Date(f.lastModified()));
                String title;
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    title = reader.readLine();
                    if (title == null || title.trim().isEmpty()) title = "Untitled";
                } catch (IOException ex) {
                    ex.printStackTrace();
                    title = "Error reading file";
                }
                poemModel.addElement(dateTime + " - " + title);
            }
        }

    }

    private void deleteSelectedEntry() {
        java.util.List<File> filesToDelete = new java.util.ArrayList<>();

        int[] jSel = journalList.getSelectedIndices();
        for(int idx : jSel) {
            if(journalFiles != null && idx >=0 && idx < journalFiles.length) {
                filesToDelete.add(journalFiles[idx]);
            }
        }

        int[] pSel = poemList.getSelectedIndices();
        for(int idx : pSel) {
            if(poemFiles != null && idx >=0 && idx < poemFiles.length) {
                filesToDelete.add(poemFiles[idx]);
            }
        }


        if(filesToDelete.isEmpty()) {
            new CustomMessageDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                "No Selection",
                "Please select entries to delete.",
                true).showDialog();
            return;
        }

        // Confirm deletion of multiple files at once
        String message = "Are you sure you want to delete " + filesToDelete.size() + " selected entr" + (filesToDelete.size()==1?"y":"ies") + "?";
        boolean confirmed = CustomConfirmDialog.confirm(this, "Confirm Deletion", message);

        if(confirmed) {
            for(File f : filesToDelete) {
                if(!f.delete()) {
                    System.err.println("Failed to delete " + f.getAbsolutePath());
                }
            }
            reloadData();
        }
    }

    // Instead of "EditXPanel", we want to reuse the new creation UIs 
    // so the user has the same toolbar, background, etc.
    // We'll create a new instance of each "Panel" but call loadX(...) to fill it.

    private void openJournalFile(File file) {
        if (!cardPanel.isAncestorOf(this)) return; // Check if panel is still in hierarchy
        String cardName = "editJournal-" + file.getName();
        EditEntryPanel editPanel = new EditEntryPanel(app, file, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel);
        cardPanel.add(editPanel, cardName);
        cardLayout.show(cardPanel, cardName);
    }

    private void openPoemFile(File file) {
        String cardName = "editPoem-" + file.getName();
        EditPoemPanel editPanel = new EditPoemPanel(app, file, AppDirectories.folder(AppDirectories.Type.POEMS), cardLayout, cardPanel);
        cardPanel.add(editPanel, cardName);
        cardLayout.show(cardPanel, cardName);
    }


    // --- Custom Rounded Button for the bottom panel ---
    class RoundedButton extends JButton {
        public RoundedButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setPreferredSize(new Dimension(100, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int arc = 20;
            if (getModel().isPressed()) {
                g2.setColor(new Color(180, 180, 180));
            } else {
                g2.setColor(getBackground());
            }
            g2.fillRoundRect(0, 0, width, height, arc, arc);
            g2.setColor(getForeground());
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public boolean contains(int x, int y) {
            int width = getWidth();
            int height = getHeight();
            int arc = 20;
            Shape shape = new RoundRectangle2D.Float(0, 0, width, height, arc, arc);
            return shape.contains(x, y);
        }
    }
}
