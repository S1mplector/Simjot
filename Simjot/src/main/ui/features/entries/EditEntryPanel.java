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
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.dialog.message.CustomMessageDialog;

public class EditEntryPanel extends EntryPanel {
    private File fileToEdit;
    
    public EditEntryPanel(JournalApp app, File fileToEdit, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        this.fileToEdit = fileToEdit;
        this.currentFile = fileToEdit;
        loadFile();
    }

    private void loadFile() {
        try {
            super.safeLoadFile(fileToEdit);
        } catch (Throwable t) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading journal entry.", true).showDialog();
        }
    }

    // Use the base class save implementation to update the existing file using the hardened logic.
    @Override
    protected void saveEntry() { super.saveEntry(); }
}
