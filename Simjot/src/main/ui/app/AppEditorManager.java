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

package main.ui.app;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import main.ui.features.entries.NotebookEditor;
import main.ui.features.entries.NotebookEditorFactory;
import main.ui.features.entries.NotebookEditorType;

/**
 * Manages open notebook editors throughout the application lifecycle.
 * Provides centralized tracking, save-on-exit, and editor factory access.
 */
public class AppEditorManager {
    
    /** Thread-safe list of currently open editors */
    private final List<NotebookEditor> openEditors = new CopyOnWriteArrayList<>();
    
    /** Factory for creating new editors */
    private NotebookEditorFactory editorFactory;
    
    /** Reference to main app for editor factory initialization */
    private JournalApp app;
    
    /** Path to last opened file (for resume functionality) */
    private String lastOpenedFilePath;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the editor manager with app reference.
     */
    public void initialize(JournalApp app, java.awt.CardLayout cardLayout, javax.swing.JPanel cardPanel) {
        this.app = app;
        this.editorFactory = new NotebookEditorFactory(app, cardLayout, cardPanel);
    }
    
    /**
     * Get the editor factory.
     */
    public NotebookEditorFactory getEditorFactory() {
        return editorFactory;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EDITOR TRACKING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Register an open editor.
     */
    public void registerEditor(NotebookEditor editor) {
        if (editor != null && !openEditors.contains(editor)) {
            openEditors.add(editor);
        }
    }
    
    /**
     * Unregister an editor (e.g., when closed).
     */
    public void unregisterEditor(NotebookEditor editor) {
        openEditors.remove(editor);
    }
    
    /**
     * Get all currently open editors (unmodifiable view).
     */
    public List<NotebookEditor> getOpenEditors() {
        return Collections.unmodifiableList(new ArrayList<>(openEditors));
    }
    
    /**
     * Get count of open editors.
     */
    public int getOpenEditorCount() {
        return openEditors.size();
    }
    
    /**
     * Check if any editors are open.
     */
    public boolean hasOpenEditors() {
        return !openEditors.isEmpty();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EDITOR CREATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Create an editor for a specific folder.
     */
    public NotebookEditor createEditorInFolder(NotebookEditorType type, File folder) {
        if (editorFactory == null) return null;
        NotebookEditor editor = editorFactory.createInFolder(type, folder);
        registerEditor(editor);
        return editor;
    }
    
    /**
     * Create an editor for a specific file.
     */
    public NotebookEditor createEditorForFile(File file) {
        if (editorFactory == null) return null;
        NotebookEditor editor = editorFactory.createForFile(file);
        registerEditor(editor);
        setLastOpenedFile(file.getAbsolutePath());
        return editor;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SAVE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Save all open editors.
     * Returns the number of editors successfully saved.
     */
    public int saveAllEditors() {
        int saved = 0;
        for (NotebookEditor editor : openEditors) {
            try {
                editor.triggerSave();
                saved++;
            } catch (Throwable ignored) {}
        }
        return saved;
    }
    
    /**
     * Save all editors on EDT with timeout.
     * Used during shutdown.
     */
    public boolean saveAllEditorsWithTimeout(long timeoutMs) {
        final List<NotebookEditor> editors = new ArrayList<>(openEditors);
        
        return AppLifecycle.runOnEdtWithTimeout(() -> {
            for (NotebookEditor editor : editors) {
                try {
                    editor.triggerSave();
                } catch (Throwable ignored) {}
            }
        }, timeoutMs);
    }
    
    /**
     * Quick save for shutdown hook (no timeout, best-effort).
     */
    public void quickSaveAll() {
        for (NotebookEditor editor : new ArrayList<>(openEditors)) {
            try {
                editor.triggerSave();
            } catch (Throwable ignored) {}
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LAST OPENED FILE TRACKING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Set the last opened file path.
     */
    public void setLastOpenedFile(String path) {
        this.lastOpenedFilePath = path;
    }
    
    /**
     * Get the last opened file path.
     */
    public String getLastOpenedFilePath() {
        return lastOpenedFilePath;
    }
    
    /**
     * Check if there's a valid last opened file.
     */
    public boolean hasValidLastOpenedFile() {
        if (lastOpenedFilePath == null || lastOpenedFilePath.isBlank()) {
            return false;
        }
        File file = new File(lastOpenedFilePath);
        return file.exists() && file.isFile() && file.canRead();
    }
    
    /**
     * Get the last opened file if valid.
     */
    public File getLastOpenedFile() {
        if (hasValidLastOpenedFile()) {
            return new File(lastOpenedFilePath);
        }
        return null;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Close all editors and clear tracking.
     */
    public void closeAll() {
        saveAllEditors();
        openEditors.clear();
    }
    
    /**
     * Dispose and cleanup.
     */
    public void dispose() {
        openEditors.clear();
        editorFactory = null;
        app = null;
    }
}
