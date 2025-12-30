package main.ui.dialog.setup;

import java.io.File;
import javax.swing.JDialog;
import javax.swing.JFrame;
import main.ui.dialog.file.SimjotFileChooser;

/**
 * Thin wrapper around {@link SimjotFileChooser} for folder selection.
 */
public class DirectoryChooserDialog extends JDialog {

    private File selectedDirectory;

    public DirectoryChooserDialog(JFrame owner) {
        super(owner, "Select a folder in which Simjot data will be stored", true);
    }

    public File getSelectedDirectory() {
        return selectedDirectory;
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            SimjotFileChooser chooser = new SimjotFileChooser(getOwner(), getTitle());
            chooser.setMode(SimjotFileChooser.Mode.DIRECTORY);
            selectedDirectory = chooser.showDialog();
            dispose();
            return;
        }
        super.setVisible(false);
    }
} 
