/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.spelling;

import java.awt.event.ActionEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * AutocorrectDocumentFilter - Real-time autocorrect for JTextComponent.
 * 
 * Install on any JTextPane or JTextArea to enable automatic typo correction
 * as the user types. Corrections happen when user presses space or punctuation.
 */
public class AutocorrectDocumentFilter extends DocumentFilter {
    
    private final IntelligentAutocorrect autocorrect;
    private final JTextComponent textComponent;
    private boolean enabled = true;
    private boolean undoInProgress = false;
    
    // Track last correction for undo support
    private String lastOriginal = null;
    private String lastCorrection = null;
    private int lastCorrectionStart = -1;
    
    // Trigger characters that cause autocorrect to run
    private static final String TRIGGER_CHARS = " .,;:!?)\n\t";
    
    public AutocorrectDocumentFilter(JTextComponent textComponent) {
        this.textComponent = textComponent;
        this.autocorrect = IntelligentAutocorrect.get();
        setupUndoShortcut();
    }
    
    /**
     * Install autocorrect on a JTextComponent.
     */
    public static AutocorrectDocumentFilter install(JTextComponent component) {
        if (component == null) return null;
        
        Document doc = component.getDocument();
        if (doc instanceof AbstractDocument) {
            AutocorrectDocumentFilter filter = new AutocorrectDocumentFilter(component);
            ((AbstractDocument) doc).setDocumentFilter(filter);
            return filter;
        }
        return null;
    }
    
    /**
     * Remove autocorrect from a JTextComponent.
     */
    public static void uninstall(JTextComponent component) {
        if (component == null) return;
        Document doc = component.getDocument();
        if (doc instanceof AbstractDocument) {
            ((AbstractDocument) doc).setDocumentFilter(null);
        }
    }
    
    private void setupUndoShortcut() {
        try {
            Object installed = textComponent.getClientProperty("autocorrect.undo.installed");
            if (Boolean.TRUE.equals(installed)) return;
        } catch (Throwable ignored) {}

        InputMap im = textComponent.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textComponent.getActionMap();

        KeyStroke ctrlZ = KeyStroke.getKeyStroke("control Z");
        KeyStroke metaZ = KeyStroke.getKeyStroke("meta Z");

        Action delegate = am.get("undo");
        if (delegate == null) {
            Object k1 = im.get(ctrlZ);
            if (k1 != null) delegate = am.get(k1);
        }
        if (delegate == null) {
            Object k2 = im.get(metaZ);
            if (k2 != null) delegate = am.get(k2);
        }
        final Action existingUndo = delegate;

        am.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoLastCorrection()) {
                    return;
                }
                if (existingUndo != null) {
                    existingUndo.actionPerformed(e);
                }
            }
        });

        im.put(ctrlZ, "undo");
        im.put(metaZ, "undo");
        try { textComponent.putClientProperty("autocorrect.undo.installed", Boolean.TRUE); } catch (Throwable ignored) {}
    }
    
    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) 
            throws BadLocationException {
        super.insertString(fb, offset, string, attr);
        
        if (enabled && !undoInProgress && isTriggerChar(string)) {
            SwingUtilities.invokeLater(() -> checkAndCorrectWord(offset));
        }
    }
    
    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) 
            throws BadLocationException {
        super.replace(fb, offset, length, text, attrs);
        
        if (enabled && !undoInProgress && text != null && isTriggerChar(text)) {
            SwingUtilities.invokeLater(() -> checkAndCorrectWord(offset));
        }
    }
    
    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        super.remove(fb, offset, length);
    }
    
    private boolean isTriggerChar(String text) {
        if (text == null || text.isEmpty()) return false;
        char c = text.charAt(0);
        return TRIGGER_CHARS.indexOf(c) >= 0;
    }
    
    private void checkAndCorrectWord(int triggerOffset) {
        try {
            Document doc = textComponent.getDocument();
            String text = doc.getText(0, doc.getLength());
            
            // Find the word that just ended (before the trigger character)
            int wordEnd = triggerOffset;
            int wordStart = wordEnd - 1;
            
            // Move back to find word start
            while (wordStart >= 0 && isWordChar(text.charAt(wordStart))) {
                wordStart--;
            }
            wordStart++; // Move forward to first letter
            
            if (wordStart >= wordEnd) return; // No word found
            
            String word = text.substring(wordStart, wordEnd);
            if (word.length() <= 1) return; // Don't correct single letters
            
            // Get context for better correction
            String prevWord = getPreviousWord(text, wordStart);
            String nextWord = null; // We don't have next word yet since we just typed a trigger
            
            // Try context-aware correction first
            String correction = autocorrect.getContextualCorrection(word, prevWord, nextWord);
            if (correction == null) {
                correction = autocorrect.getCorrection(word);
            }
            
            if (correction != null && !correction.equals(word)) {
                applyCorrection(wordStart, wordEnd, word, correction);
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }
    
    private String getPreviousWord(String text, int beforeOffset) {
        int end = beforeOffset - 1;
        while (end >= 0 && !isWordChar(text.charAt(end))) {
            end--;
        }
        if (end < 0) return null;
        
        int start = end;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        
        if (start <= end) {
            return text.substring(start, end + 1);
        }
        return null;
    }
    
    private boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'';
    }
    
    private void applyCorrection(int start, int end, String original, String correction) {
        undoInProgress = true;
        try {
            Document doc = textComponent.getDocument();

            AttributeSet attrs = null;
            if (doc instanceof javax.swing.text.StyledDocument sd) {
                try {
                    attrs = sd.getCharacterElement(Math.max(0, start)).getAttributes();
                } catch (Throwable ignored) {}
            }
            
            // Store for undo
            lastOriginal = original;
            lastCorrection = correction;
            lastCorrectionStart = start;
            
            // Apply the correction
            doc.remove(start, end - start);
            doc.insertString(start, correction, attrs);
            
            // Move caret to end of corrected word + 1 (after trigger char)
            textComponent.setCaretPosition(Math.min(start + correction.length() + 1, doc.getLength()));
            
        } catch (BadLocationException e) {
            // Ignore
        } finally {
            undoInProgress = false;
        }
    }
    
    /**
     * Undo the last autocorrection.
     * @return true if an undo was performed
     */
    public boolean undoLastCorrection() {
        if (lastOriginal == null || lastCorrection == null || lastCorrectionStart < 0) {
            return false;
        }
        
        undoInProgress = true;
        try {
            Document doc = textComponent.getDocument();
            String currentText = doc.getText(lastCorrectionStart, lastCorrection.length());
            
            // Verify the correction is still there
            if (currentText.equals(lastCorrection)) {
                doc.remove(lastCorrectionStart, lastCorrection.length());
                doc.insertString(lastCorrectionStart, lastOriginal, null);
                
                // Learn that user doesn't want this correction
                autocorrect.ignore(lastOriginal.toLowerCase(Locale.ROOT));
                
                // Clear undo state
                lastOriginal = null;
                lastCorrection = null;
                lastCorrectionStart = -1;
                
                return true;
            }
        } catch (BadLocationException e) {
            // Ignore
        } finally {
            undoInProgress = false;
        }
        
        return false;
    }
    
    /**
     * Enable or disable autocorrect.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Check if autocorrect is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Clear the undo state.
     */
    public void clearUndoState() {
        lastOriginal = null;
        lastCorrection = null;
        lastCorrectionStart = -1;
    }
}
