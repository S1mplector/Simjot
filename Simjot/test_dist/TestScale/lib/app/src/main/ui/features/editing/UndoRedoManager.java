/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.editing;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeLibrary;

/**
 * A utility class that provides undo/redo functionality for text components.
 * Uses native C++ implementation when available, with Java fallback.
 */
public class UndoRedoManager {
    private static final ThreadLocal<Boolean> UNDO_REDO_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final boolean NATIVE_AVAILABLE = NativeAccess.hasUndoSupport();
    
    static {
        if (NATIVE_AVAILABLE) {
            NativeAccess.undoInit();
            Runtime.getRuntime().addShutdownHook(new Thread(NativeAccess::undoShutdown));
        }
    }

    private final JTextComponent textComponent;
    
    // Native implementation fields
    private final int nativeSessionId;
    private DocumentListener nativeDocListener;
    private String pendingDeleteText = null;
    
    // Java fallback fields
    private final CountingUndoManager javaUndoManager;
    private final UndoableEditListener javaEditListener;

    /**
     * Creates a new UndoRedoManager for the specified text component.
     *
     * @param textComponent the text component to add undo/redo support to
     */
    public UndoRedoManager(JTextComponent textComponent) {
        this.textComponent = textComponent;
        
        if (NATIVE_AVAILABLE) {
            this.nativeSessionId = NativeAccess.undoCreateSession(1000);
            this.javaUndoManager = null;
            this.javaEditListener = null;
            setupNativeUndoRedo();
        } else {
            this.nativeSessionId = -1;
            this.javaUndoManager = new CountingUndoManager();
            this.javaUndoManager.setLimit(1000);
            this.javaEditListener = e -> this.javaUndoManager.addEdit(e.getEdit());
            setupJavaUndoRedo();
        }
        
        setupKeyBindings();
    }
    
    private void setupNativeUndoRedo() {
        Document doc = textComponent.getDocument();
        
        // Use AbstractDocument's filter to capture text before deletion
        if (doc instanceof javax.swing.text.AbstractDocument) {
            ((javax.swing.text.AbstractDocument) doc).setDocumentFilter(
                new javax.swing.text.DocumentFilter() {
                    @Override
                    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                        if (!isUndoOrRedoInProgress()) {
                            pendingDeleteText = fb.getDocument().getText(offset, length);
                        }
                        super.remove(fb, offset, length);
                    }
                    
                    @Override
                    public void replace(FilterBypass fb, int offset, int length, String text, 
                                        javax.swing.text.AttributeSet attrs) throws BadLocationException {
                        if (!isUndoOrRedoInProgress() && length > 0) {
                            pendingDeleteText = fb.getDocument().getText(offset, length);
                        }
                        super.replace(fb, offset, length, text, attrs);
                    }
                }
            );
        }
        
        nativeDocListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (isUndoOrRedoInProgress()) return;
                try {
                    String text = e.getDocument().getText(e.getOffset(), e.getLength());
                    NativeAccess.undoPushInsert(nativeSessionId, e.getOffset(), text);
                } catch (BadLocationException ignored) {}
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                if (isUndoOrRedoInProgress()) return;
                // Use the text captured by DocumentFilter before removal
                String deletedText = pendingDeleteText;
                pendingDeleteText = null;
                if (deletedText != null && !deletedText.isEmpty()) {
                    NativeAccess.undoPushDelete(nativeSessionId, e.getOffset(), deletedText);
                }
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                // Style changes - record as style edit
                if (isUndoOrRedoInProgress()) return;
                NativeAccess.undoPushStyle(nativeSessionId, e.getOffset(), e.getLength(), 0);
            }
        };
        
        if (doc != null) {
            doc.addDocumentListener(nativeDocListener);
        }
        
        textComponent.addPropertyChangeListener("document", evt -> {
            try {
                Document old = (Document) evt.getOldValue();
                Document neu = (Document) evt.getNewValue();
                if (old != null) old.removeDocumentListener(nativeDocListener);
                if (neu != null) {
                    neu.addDocumentListener(nativeDocListener);
                    // Setup filter on new document too
                    if (neu instanceof javax.swing.text.AbstractDocument) {
                        ((javax.swing.text.AbstractDocument) neu).setDocumentFilter(
                            new javax.swing.text.DocumentFilter() {
                                @Override
                                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                                    if (!isUndoOrRedoInProgress()) {
                                        pendingDeleteText = fb.getDocument().getText(offset, length);
                                    }
                                    super.remove(fb, offset, length);
                                }
                                
                                @Override
                                public void replace(FilterBypass fb, int offset, int length, String text,
                                                    javax.swing.text.AttributeSet attrs) throws BadLocationException {
                                    if (!isUndoOrRedoInProgress() && length > 0) {
                                        pendingDeleteText = fb.getDocument().getText(offset, length);
                                    }
                                    super.replace(fb, offset, length, text, attrs);
                                }
                            }
                        );
                    }
                }
                NativeAccess.undoClear(nativeSessionId);
            } catch (Throwable ignored) {}
        });
    }
    
    private void setupJavaUndoRedo() {
        Document doc = textComponent.getDocument();
        if (doc != null) {
            doc.addUndoableEditListener(javaEditListener);
        }
        textComponent.addPropertyChangeListener("document", evt -> {
            try {
                Document old = (Document) evt.getOldValue();
                Document neu = (Document) evt.getNewValue();
                if (old != null) old.removeUndoableEditListener(javaEditListener);
                if (neu != null) neu.addUndoableEditListener(javaEditListener);
                javaUndoManager.discardAllEdits();
                javaUndoManager.resetCounters();
            } catch (Throwable ignored) {}
        });
    }
    
    private void setupKeyBindings() {
        InputMap inputMap = textComponent.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = textComponent.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("control Z"), "undo");
        inputMap.put(KeyStroke.getKeyStroke("meta Z"), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("control Y"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("control shift Z"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("meta Y"), "redo");
        inputMap.put(KeyStroke.getKeyStroke("meta shift Z"), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                redo();
            }
        });
    }

    /**
     * Clears the undo/redo history.
     */
    public void clearHistory() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            NativeAccess.undoClear(nativeSessionId);
        } else if (javaUndoManager != null) {
            javaUndoManager.discardAllEdits();
            javaUndoManager.resetCounters();
        }
    }

    /**
     * Returns whether there are actions that can be undone.
     */
    public boolean canUndo() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoCanUndo(nativeSessionId);
        }
        return javaUndoManager != null && javaUndoManager.canUndo();
    }

    /**
     * Returns whether there are actions that can be redone.
     */
    public boolean canRedo() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoCanRedo(nativeSessionId);
        }
        return javaUndoManager != null && javaUndoManager.canRedo();
    }

    /**
     * Undoes the last action.
     */
    public void undo() {
        UNDO_REDO_FLAG.set(Boolean.TRUE);
        try {
            if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
                if (NativeAccess.undoCanUndo(nativeSessionId)) {
                    NativeLibrary.UndoResult result = NativeAccess.undoUndo(nativeSessionId);
                    if (result != null) {
                        applyUndoResult(result, true);
                    }
                }
            } else if (javaUndoManager != null && javaUndoManager.canUndo()) {
                javaUndoManager.undo();
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
            if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
                if (NativeAccess.undoCanRedo(nativeSessionId)) {
                    NativeLibrary.UndoResult result = NativeAccess.undoRedo(nativeSessionId);
                    if (result != null) {
                        applyUndoResult(result, false);
                    }
                }
            } else if (javaUndoManager != null && javaUndoManager.canRedo()) {
                javaUndoManager.redo();
            }
        } finally {
            UNDO_REDO_FLAG.set(Boolean.FALSE);
        }
    }
    
    private void applyUndoResult(NativeLibrary.UndoResult result, boolean isUndo) {
        try {
            Document doc = textComponent.getDocument();
            if (doc == null) return;
            
            int type = result.type();
            int offset = result.offset();
            String text = result.text();
            
            switch (type) {
                case NativeLibrary.EDIT_INSERT -> {
                    if (isUndo) {
                        doc.remove(offset, text.length());
                    } else {
                        doc.insertString(offset, text, null);
                    }
                }
                case NativeLibrary.EDIT_DELETE -> {
                    if (isUndo) {
                        doc.insertString(offset, text, null);
                    } else {
                        doc.remove(offset, text.length());
                    }
                }
                case NativeLibrary.EDIT_REPLACE -> {
                    int len = result.length();
                    if (len > 0 && offset + len <= doc.getLength()) {
                        doc.remove(offset, len);
                    }
                    if (text != null && !text.isEmpty()) {
                        doc.insertString(offset, text, null);
                    }
                }
                case NativeLibrary.EDIT_STYLE, NativeLibrary.EDIT_COMPOUND -> {
                    // Style changes don't modify text; compound handled internally
                }
                default -> {}
            }
            
            // Move caret to the edited position
            textComponent.setCaretPosition(Math.min(offset, doc.getLength()));
            
        } catch (BadLocationException ignored) {}
    }

    public void markSavePoint() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            NativeAccess.undoMarkSavePoint(nativeSessionId);
        } else if (javaUndoManager != null) {
            javaUndoManager.markSavePoint();
        }
    }

    public boolean isAtSavePoint() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoIsAtSavePoint(nativeSessionId);
        }
        return javaUndoManager != null && javaUndoManager.isAtSavePoint();
    }
    
    public boolean isDirty() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoIsDirty(nativeSessionId);
        }
        return javaUndoManager != null && !javaUndoManager.isAtSavePoint();
    }
    
    public int getUndoCount() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoGetUndoCount(nativeSessionId);
        }
        return 0;
    }
    
    public int getRedoCount() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            return NativeAccess.undoGetRedoCount(nativeSessionId);
        }
        return 0;
    }
    
    /**
     * Begin a compound edit - groups multiple edits into one undo step.
     */
    public void beginCompoundEdit() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            NativeAccess.undoBeginCompound(nativeSessionId);
        }
    }
    
    /**
     * End a compound edit.
     */
    public void endCompoundEdit() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            NativeAccess.undoEndCompound(nativeSessionId);
        }
    }
    
    /**
     * Check if native undo/redo is being used.
     */
    public boolean isUsingNative() {
        return NATIVE_AVAILABLE && nativeSessionId >= 0;
    }
    
    /**
     * Cleanup resources when no longer needed.
     */
    public void dispose() {
        if (NATIVE_AVAILABLE && nativeSessionId >= 0) {
            NativeAccess.undoDestroySession(nativeSessionId);
        }
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
