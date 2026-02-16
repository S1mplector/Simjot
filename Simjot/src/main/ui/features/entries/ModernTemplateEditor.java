/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;

/**
 * Modern template editor with inline editing, smooth drag-reorder,
 * and polished visual feedback.
 */
public class ModernTemplateEditor extends JDialog {
    private boolean saved = false;
    private JournalTemplateManager.JournalTemplate template;
    
    public ModernTemplateEditor(Dialog parent, NotebookInfo notebook) {
        this(parent, notebook, null);
    }
    
    public ModernTemplateEditor(Dialog parent, NotebookInfo notebook, JournalTemplateManager.JournalTemplate existing) {
        super(parent, existing == null ? "New Template" : "Edit Template", true);
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());

        JournalApp app = null;
        if (parent != null && parent.getOwner() instanceof JournalApp ja) {
            app = ja;
        }
        ModernTemplateEditorPanel editorPanel = new ModernTemplateEditorPanel(notebook, existing, app);
        editorPanel.setOnCancel(this::dispose);
        editorPanel.setOnSave(savedTemplate -> {
            template = savedTemplate;
            saved = true;
            dispose();
        });
        add(editorPanel, BorderLayout.CENTER);
        
        setSize(620, 600);
        setLocationRelativeTo(parent);
        
        // ESC to close
        getRootPane().registerKeyboardAction(e -> editorPanel.attemptCancel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean isSaved() { return saved; }
    public JournalTemplateManager.JournalTemplate getTemplate() { return template; }
}
