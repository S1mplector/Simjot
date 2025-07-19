package mindmap;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MindMapPanel extends JPanel {
    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private final File mindmapsFolder;

    private final MindMapSelectionPanel selectionPanel;
    private final MindMapEditingPanel editingPanel;

    public MindMapPanel(File journalFolder) {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);

        // Create folder "mindmaps" inside the journalFolder
        mindmapsFolder = new File(journalFolder, "mindmaps");
        if (!mindmapsFolder.exists()) {
            mindmapsFolder.mkdirs();
        }

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setOpaque(false);

        selectionPanel = new MindMapSelectionPanel(this, mindmapsFolder);
        editingPanel = new MindMapEditingPanel(this);

        mainPanel.add(selectionPanel, "selection");
        mainPanel.add(editingPanel, "editing");

        add(mainPanel, BorderLayout.CENTER);

        // By default, show the selection screen
        showSelectionPanel();
    }

    public void showSelectionPanel() {
        selectionPanel.reloadMindMaps();
        cardLayout.show(mainPanel, "selection");
    }

    public void showEditingPanel(MindMap map) {
        editingPanel.loadMindMap(map);
        cardLayout.show(mainPanel, "editing");
    }

    public File getMindmapsFolder() {
        return mindmapsFolder;
    }

    // Optionally, a main method for quick testing:
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mind Map");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Provide a test folder
            MindMapPanel mmp = new MindMapPanel(new File("."));
            frame.getContentPane().add(mmp);
            frame.setSize(1000, 800);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
