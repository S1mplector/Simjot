package main.util;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

/**
 * A utility class that provides undo/redo functionality for text components.
 * It handles both the UndoManager and the keyboard shortcuts.
 */
public class UndoRedoManager {
    private final UndoManager undoManager;
    private final JTextComponent textComponent;

    /**
     * Creates a new UndoRedoManager for the specified text component.
     *
     * @param textComponent the text component to add undo/redo support to
     */
    public UndoRedoManager(JTextComponent textComponent) {
        this.textComponent = textComponent;
        this.undoManager = new UndoManager();
        setupUndoRedo();
    }

    private void setupUndoRedo() {
        // Add undo support
        textComponent.getDocument().addUndoableEditListener(e -> 
            undoManager.addEdit(e.getEdit())
        );

        // Get the input map and action map for the text component
        InputMap inputMap = textComponent.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = textComponent.getActionMap();

        // Add Undo action (Ctrl+Z)
        inputMap.put(KeyStroke.getKeyStroke("control Z"), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });

        // Add Redo action (Ctrl+Y or Ctrl+Shift+Z)
        inputMap.put(KeyStroke.getKeyStroke("control Y"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("control shift Z"), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            }
        });
    }

    /**
     * Clears the undo/redo history.
     */
    public void clearHistory() {
        undoManager.discardAllEdits();
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
        if (canUndo()) {
            undoManager.undo();
        }
    }

    /**
     * Redoes the last undone action.
     */
    public void redo() {
        if (canRedo()) {
            undoManager.redo();
        }
    }
}
