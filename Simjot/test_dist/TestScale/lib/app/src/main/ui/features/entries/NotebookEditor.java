/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.entries;

import java.io.File;

import javax.swing.JComponent;

/**
 * Common contract for all notebook editor panels (journal, poem, notes, etc.).
 * 
 * @author S1mplector
 */
public interface NotebookEditor {
    // Current file bound to this editor (null until first save)
    File getCurrentFile();
    void setCurrentFile(File f);

    // Load a file into the editor UI
    void loadFile(File f);

    // Trigger a save using the panel's own UX/validation
    void triggerSave();

    // File extension this editor writes (e.g., ".txt", ".poem")
    String fileExtension();

    // Focus a sensible control (e.g., title field or text area)
    void requestInitialFocus();

    // Set initial content for a new entry (e.g., from templates)
    void setInitialContent(String content);

    // Set up guided question mode with a series of prompts
    void setGuidedQuestions(String[] questions);

    // Typically returns the panel itself; provided for future flexibility
    JComponent getMainComponent();
}
