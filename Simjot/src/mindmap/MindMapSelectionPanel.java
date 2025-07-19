package mindmap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Arrays;

public class MindMapSelectionPanel extends JPanel {
    private final JPanel gridPanel;
    private final File mindmapsFolder;
    private final MindMapPanel parent;
    
    public MindMapSelectionPanel(MindMapPanel parent, File mindmapsFolder) {
        this.parent = parent;
        this.mindmapsFolder = mindmapsFolder;
        setLayout(new BorderLayout());
        setOpaque(false);

        JLabel header = new JLabel("Select a Mind Map", SwingConstants.CENTER);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(header, BorderLayout.NORTH);

        gridPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        gridPanel.setOpaque(true);
        gridPanel.setBackground(Color.DARK_GRAY);

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void reloadMindMaps() {
        gridPanel.removeAll();

        // Load the .mmap files
        File[] mapFiles = mindmapsFolder.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".mmap")
        );

        if (mapFiles != null && mapFiles.length > 0) {
            // Sort them by last modified descending, just as an example
            Arrays.sort(mapFiles, (f1, f2) -> 
                Long.compare(f2.lastModified(), f1.lastModified())
            );

            // For each file, read the MindMap object
            for (File file : mapFiles) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    MindMap map = (MindMap) ois.readObject();
                    DraggableMindMapButton btn = new DraggableMindMapButton(map, file, parent);
                    gridPanel.add(btn);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Always add a "New Mind Map" button
        JButton newMapBtn = new JButton("New Mind Map");
        newMapBtn.setBackground(Color.DARK_GRAY);
        newMapBtn.setForeground(Color.WHITE);
        newMapBtn.addActionListener(e -> {
            MindMap map = new MindMap("Untitled Mind Map");
            parent.showEditingPanel(map);
        });
        gridPanel.add(newMapBtn);

        gridPanel.revalidate();
        gridPanel.repaint();
    }
}
