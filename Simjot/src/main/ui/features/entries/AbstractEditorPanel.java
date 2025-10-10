package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.io.File;
import javax.swing.JComponent;
import javax.swing.JPanel;
import main.ui.app.JournalApp;

/**
 * Abstract base for notebook editors providing shared wiring and behaviors.
 * Subclasses supply file-specific IO and focus behavior.
 */
public abstract class AbstractEditorPanel extends JPanel implements NotebookEditor {

    protected final CardLayout cardLayout;
    protected final JPanel cardPanel;
    protected final File journalFolder;
    protected final JournalApp app;

    protected File currentFile = null;

    protected AbstractEditorPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(new BorderLayout());
        this.app = app;
        this.journalFolder = journalFolder;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setOpaque(false);
    }

    // --- NotebookEditor shared impls ---
    @Override
    public File getCurrentFile() {
        return currentFile;
    }

    @Override
    public void setCurrentFile(File f) {
        this.currentFile = f;
    }

    @Override
    public void loadFile(File f) {
        this.currentFile = f;
        if (f != null) {
            safeLoadFile(f);
            // Record last opened file for startup restoration
            try {
                main.core.service.SettingsStore store = main.core.service.SettingsStore.get();
                store.setLastOpenedFilePath(f.getAbsolutePath());
                store.save();
            } catch (Throwable ignored) {}
        } else {
            clearEditor();
        }
    }

    @Override
    public void triggerSave() {
        performSave();
    }

    @Override
    public JComponent getMainComponent() {
        return this;
    }

    // Subclass hooks
    protected abstract void safeLoadFile(File f);
    protected abstract void clearEditor();
    protected abstract void performSave();
}
