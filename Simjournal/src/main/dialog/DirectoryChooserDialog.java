package main.dialog;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import main.ui.buttons.RoundedButton;
import main.ui.panels.RoundedPanel;

/**
 * A more visually integrated folder picker that embeds a {@link JFileChooser}
 * inside a rounded, modern-styled dialog and uses the app's {@link RoundedButton}
 * for action buttons.
 */
public class DirectoryChooserDialog extends JDialog {

    private File selectedDirectory;

    public DirectoryChooserDialog(JFrame owner) {
        super(owner, "Select a folder in which Simnote data will be stored", true);
        initUI();
    }

    public File getSelectedDirectory() {
        return selectedDirectory;
    }

    private void initUI() {
        // Rounded outer panel
        RoundedPanel container = new RoundedPanel(15);
        container.setBackground(new Color(250, 250, 250));
        container.setLayout(new BorderLayout(10,10));
        container.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // File chooser (directories only) without its own buttons
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setControlButtonsAreShown(false);

        container.add(chooser, BorderLayout.CENTER);

        // Action buttons panel
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);

        RoundedButton cancelBtn = new RoundedButton("Cancel");
        RoundedButton selectBtn = new RoundedButton("Select");

        cancelBtn.addActionListener(e -> {
            selectedDirectory = null;
            dispose();
        });

        selectBtn.addActionListener(e -> {
            File sel = chooser.getSelectedFile();
            if (sel != null) {
                selectedDirectory = sel;
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Please select a folder.", "No folder selected", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        buttons.add(cancelBtn);
        buttons.add(selectBtn);

        container.add(buttons, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(container, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(getOwner());
        setResizable(false);
    }
} 