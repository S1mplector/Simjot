package main.ui.dialog.setup;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.dialog.message.UIMessage;

/**
 * A more visually integrated folder picker that embeds a {@link JFileChooser}
 * inside a rounded, modern-styled dialog and uses the app's {@link RoundedButton}
 * for action buttons.
 */
public class DirectoryChooserDialog extends JDialog {

    private File selectedDirectory;

    public DirectoryChooserDialog(JFrame owner) {
        super(owner, "Select a folder in which Simjot data will be stored", true);
        initUI();
    }

    public File getSelectedDirectory() {
        return selectedDirectory;
    }

    private void initUI() {
        // Rounded outer panel
        FrostedGlassPanel container = new FrostedGlassPanel(new BorderLayout(10,10), 15);
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
                UIMessage.info(this,
                        "No Folder Selected",
                        "You haven't chosen where to store Simjot data.",
                        "Click a folder in the file chooser, then press Select."
                );
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
