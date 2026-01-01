/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.editing;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.Document;

/**
 * A utility class that provides undo/redo functionality for text components.
 * It handles both the UndoManager and the keyboard shortcuts.
 */
public class UndoRedoManager {
    private static final ThreadLocal<Boolean> UNDO_REDO_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final CountingUndoManager undoManager;
    private final JTextComponent textComponent;
    private UndoableEditListener editListener;

    /**
     * Creates a new UndoRedoManager for the specified text component.
     *
     * @param textComponent the text component to add undo/redo support to
     */
    public UndoRedoManager(JTextComponent textComponent) {
        this.textComponent = textComponent;
        this.undoManager = new CountingUndoManager();
        this.undoManager.setLimit(1000);
        this.editListener = e -> this.undoManager.addEdit(e.getEdit());
        setupUndoRedo();
    }

    private void setupUndoRedo() {
        Document doc = textComponent.getDocument();
        if (doc != null) {
            doc.addUndoableEditListener(editListener);
        }
        textComponent.addPropertyChangeListener("document", evt -> {
            try {
                Document old = (Document) evt.getOldValue();
                Document neu = (Document) evt.getNewValue();
                if (old != null) old.removeUndoableEditListener(editListener);
                if (neu != null) neu.addUndoableEditListener(editListener);
                // Reset history when document is swapped to avoid cross-doc undos
                undoManager.discardAllEdits();
                undoManager.resetCounters();
            } catch (Throwable ignored) {}
        });

        // Get the input map and action map for the text component
        InputMap inputMap = textComponent.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = textComponent.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("control Z"), "undo");
        inputMap.put(KeyStroke.getKeyStroke("meta Z"), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UNDO_REDO_FLAG.set(Boolean.TRUE);
                try {
                    if (undoManager.canUndo()) {
                        undoManager.undo();
                    }
                } finally {
                    UNDO_REDO_FLAG.set(Boolean.FALSE);
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("control Y"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("control shift Z"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("meta Y"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("meta shift Z"), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UNDO_REDO_FLAG.set(Boolean.TRUE);
                try {
                    if (undoManager.canRedo()) {
                        undoManager.redo();
                    }
                } finally {
                    UNDO_REDO_FLAG.set(Boolean.FALSE);
                }
            }
        });
    }

    /**
     * Clears the undo/redo history.
     */
    public void clearHistory() {
        undoManager.discardAllEdits();
        undoManager.resetCounters();
    }

    /**
     * Returns whether there are actions that can be undone.
     */
    public boolean canUndo() {
        return undoManager.canUndo();
    }

    /**
     * Returns whether there are actions that can be redone.
     */
    public boolean canRedo() {
        return undoManager.canRedo();
    }

    /**
     * Undoes the last action.
     */
    public void undo() {
        UNDO_REDO_FLAG.set(Boolean.TRUE);
        try {
            if (canUndo()) {
                undoManager.undo();
            }
        } finally {
            UNDO_REDO_FLAG.set(Boolean.FALSE);
        }
    }

    /**
     * Redoes the last undone action.
     */
    public void redo() {
        UNDO_REDO_FLAG.set(Boolean.TRUE);
        try {
            if (canRedo()) {
                undoManager.redo();
            }
        } finally {
            UNDO_REDO_FLAG.set(Boolean.FALSE);
        }
    }

    public void markSavePoint() {
        undoManager.markSavePoint();
    }

    public boolean isAtSavePoint() {
        return undoManager.isAtSavePoint();
    }

    public static boolean isUndoOrRedoInProgress() {
        return Boolean.TRUE.equals(UNDO_REDO_FLAG.get());
    }

    private static final class CountingUndoManager extends UndoManager {
        private int changeIndex = 0;
        private int savePoint = 0;

        @Override
        public boolean addEdit(UndoableEdit anEdit) {
            boolean added = super.addEdit(anEdit);
            if (added) changeIndex++;
            return added;
        }

        @Override
        public void undo() {
            super.undo();
            if (changeIndex > 0) changeIndex--;
        }

        @Override
        public void redo() {
            super.redo();
            changeIndex++;
        }

        void markSavePoint() { savePoint = changeIndex; }
        boolean isAtSavePoint() { return changeIndex == savePoint; }
        void resetCounters() { changeIndex = 0; savePoint = 0; }
    }
}
