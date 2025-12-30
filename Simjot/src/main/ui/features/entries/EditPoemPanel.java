/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.*;
import java.io.*;
import java.nio.channels.FileLock;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.dialog.message.CustomMessageDialog;
import main.infrastructure.io.FileIO;

public class EditPoemPanel extends PoemPanel {
    private final File poemFile;
    private FileLock poemLock;
    private boolean readOnly = false;

    public EditPoemPanel(JournalApp app, File poemFile, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        this.poemFile = poemFile;
        loadPoemFile();
    }

    private void loadPoemFile() {
        if (!acquirePoemLock()) {
            setReadOnlyMode(true);
            CustomMessageDialog.display(this, "Read-only", "This poem is open in another Simjot instance. Opened read-only.", true);
        } else {
            setReadOnlyMode(false);
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(poemFile))) {
            String title = reader.readLine();
            if (title == null) title = "";
            poemTitleField.setText(title);
            reader.readLine(); // skip blank line

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            poemEditor.setText(content.toString());
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading poem.", true).showDialog();
        }
    }

    @Override
    protected void savePoem() {
        if (readOnly) {
            CustomMessageDialog.display(this, "Read-only", "This poem is locked by another instance and cannot be saved.", true);
            return;
        }
        this.currentFile = poemFile;
        super.savePoem();
    }

    private boolean acquirePoemLock() {
        try {
            poemLock = FileIO.tryLock(poemFile.toPath());
            return poemLock != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void setReadOnlyMode(boolean ro) {
        this.readOnly = ro;
        try { if (poemTitleField != null) poemTitleField.setEditable(!ro); } catch (Throwable ignored) {}
        try { if (poemEditor != null) poemEditor.setEditable(!ro); } catch (Throwable ignored) {}
    }

    @Override
    public void removeNotify() {
        FileIO.releaseQuietly(poemLock);
        poemLock = null;
        super.removeNotify();
    }
}
