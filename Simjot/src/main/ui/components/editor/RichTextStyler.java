/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;

/**
 * Centralized rich-text styling utilities for Swing JTextPane.
 * All editors (e.g., PoemPanel, EntryPanel) should use this class to ensure
 * consistent rendering and behavior across Bold / Italic / Underline / Strikethrough
 * for both typing attributes (new text) and selection-based toggles.
 */
public final class RichTextStyler {
    private RichTextStyler() {}

    // --- Typing attributes (affect new text) ---
    public static void setTypingBold(JTextPane pane, boolean on) {
        setTypingAttribute(pane, a -> StyleConstants.setBold(a, on));
    }
    public static void setTypingItalic(JTextPane pane, boolean on) {
        setTypingAttribute(pane, a -> StyleConstants.setItalic(a, on));
    }
    public static void setTypingUnderline(JTextPane pane, boolean on) {
        setTypingAttribute(pane, a -> StyleConstants.setUnderline(a, on));
    }
    public static void setTypingStrike(JTextPane pane, boolean on) {
        setTypingAttribute(pane, a -> StyleConstants.setStrikeThrough(a, on));
    }

    // --- Selection-based toggles (affect existing text selection) ---
    public static void toggleSelectionBold(JTextPane pane) {
        toggleSelection(pane, StyleConstants::isBold, (a, v) -> StyleConstants.setBold(a, v));
    }
    public static void toggleSelectionItalic(JTextPane pane) {
        toggleSelection(pane, StyleConstants::isItalic, (a, v) -> StyleConstants.setItalic(a, v));
    }
    public static void toggleSelectionUnderline(JTextPane pane) {
        toggleSelection(pane, StyleConstants::isUnderline, (a, v) -> StyleConstants.setUnderline(a, v));
    }
    public static void toggleSelectionStrike(JTextPane pane) {
        toggleSelection(pane, StyleConstants::isStrikeThrough, (a, v) -> StyleConstants.setStrikeThrough(a, v));
    }

    // Combined toggles (selection if present, otherwise typing attributes)
    public static void toggleBold(JTextPane pane) {
        if (hasSelection(pane)) {
            toggleSelectionBold(pane);
        } else {
            setTypingBold(pane, !getTypingState(pane).bold());
        }
    }

    public static void toggleItalic(JTextPane pane) {
        if (hasSelection(pane)) {
            toggleSelectionItalic(pane);
        } else {
            setTypingItalic(pane, !getTypingState(pane).italic());
        }
    }

    public static void toggleUnderline(JTextPane pane) {
        if (hasSelection(pane)) {
            toggleSelectionUnderline(pane);
        } else {
            setTypingUnderline(pane, !getTypingState(pane).underline());
        }
    }

    public static void toggleStrike(JTextPane pane) {
        if (hasSelection(pane)) {
            toggleSelectionStrike(pane);
        } else {
            setTypingStrike(pane, !getTypingState(pane).strike());
        }
    }

    // --- Read current typing state ---
    public static StyleState getTypingState(JTextPane pane) {
        try {
            AttributeSet as = ((StyledEditorKit) pane.getEditorKit()).getInputAttributes();
            return new StyleState(
                StyleConstants.isBold(as),
                StyleConstants.isItalic(as),
                StyleConstants.isUnderline(as),
                StyleConstants.isStrikeThrough(as)
            );
        } catch (Throwable t) {
            return new StyleState(false, false, false, false);
        }
    }

    // --- Internal helpers ---
    private static void setTypingAttribute(JTextPane pane, java.util.function.Consumer<MutableAttributeSet> applier) {
        try {
            MutableAttributeSet attrs = new SimpleAttributeSet(((StyledEditorKit) pane.getEditorKit()).getInputAttributes());
            applier.accept(attrs);
            pane.setCharacterAttributes(attrs, true);
        } catch (Throwable ignored) {}
    }

    private static boolean hasSelection(JTextPane pane) {
        return pane.getSelectionStart() != pane.getSelectionEnd();
    }

    private static void toggleSelection(JTextPane pane,
                                        java.util.function.Function<AttributeSet, Boolean> currentGetter,
                                        java.util.function.BiConsumer<MutableAttributeSet, Boolean> setter) {
        try {
            int start = pane.getSelectionStart();
            int end = pane.getSelectionEnd();
            if (start == end) return; // nothing selected
            StyledDocument doc = pane.getStyledDocument();
            AttributeSet selectionAttrs = doc.getCharacterElement(start).getAttributes();
            boolean enable = !currentGetter.apply(selectionAttrs);
            MutableAttributeSet attrs = new SimpleAttributeSet();
            setter.accept(attrs, enable);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Throwable ignored) {}
    }

    // Holds the current typing attributes.
    public record StyleState(boolean bold, boolean italic, boolean underline, boolean strike) {}

    // --- Text color methods ---
    
    /**
     * Set the foreground color for new text being typed.
     */
    public static void setTypingColor(JTextPane pane, Color color) {
        setTypingAttribute(pane, a -> StyleConstants.setForeground(a, color));
    }
    
    /**
     * Apply foreground color to selected text.
     */
    public static void setSelectionColor(JTextPane pane, Color color) {
        try {
            int start = pane.getSelectionStart();
            int end = pane.getSelectionEnd();
            if (start == end) return;
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, color);
            pane.getStyledDocument().setCharacterAttributes(start, end - start, attrs, false);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Apply color to selection if present, otherwise set typing color.
     */
    public static void applyColor(JTextPane pane, Color color) {
        if (hasSelection(pane)) {
            setSelectionColor(pane, color);
        } else {
            setTypingColor(pane, color);
        }
    }
    
    /**
     * Get the current typing color.
     */
    public static Color getTypingColor(JTextPane pane) {
        try {
            AttributeSet as = ((StyledEditorKit) pane.getEditorKit()).getInputAttributes();
            Color c = StyleConstants.getForeground(as);
            return c != null ? c : Color.BLACK;
        } catch (Throwable t) {
            return Color.BLACK;
        }
    }

    // --- List formatting methods ---
    
    /**
     * Toggle bullet points on selected lines or current line.
     * @return true if bullets were added, false if removed
     */
    public static boolean toggleBulletList(JTextPane pane) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            int start = pane.getSelectionStart();
            int end = pane.getSelectionEnd();
            
            int lineStart = getLineStart(doc, start);
            int lineEnd = getLineEnd(doc, end);
            
            String text = doc.getText(lineStart, lineEnd - lineStart);
            String[] lines = text.split("\n", -1);
            
            // Check if all non-empty lines already have bullets
            boolean allBulleted = true;
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.startsWith("• ")) {
                    allBulleted = false;
                    break;
                }
            }
            
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i > 0) result.append("\n");
                
                if (line.trim().isEmpty()) {
                    result.append(line);
                } else if (allBulleted) {
                    // Remove bullets
                    if (line.startsWith("• ")) {
                        result.append(line.substring(2));
                    } else {
                        result.append(line);
                    }
                } else {
                    // Add bullets
                    if (!line.startsWith("• ")) {
                        result.append("• ").append(line);
                    } else {
                        result.append(line);
                    }
                }
            }
            
            doc.remove(lineStart, lineEnd - lineStart);
            doc.insertString(lineStart, result.toString(), null);
            
            return !allBulleted;
        } catch (BadLocationException e) {
            return false;
        }
    }
    
    /**
     * Toggle numbered list on selected lines or current line.
     * @return true if numbers were added, false if removed
     */
    public static boolean toggleNumberedList(JTextPane pane) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            int start = pane.getSelectionStart();
            int end = pane.getSelectionEnd();
            
            int lineStart = getLineStart(doc, start);
            int lineEnd = getLineEnd(doc, end);
            
            String text = doc.getText(lineStart, lineEnd - lineStart);
            String[] lines = text.split("\n", -1);
            
            // Check if all non-empty lines already have numbers
            boolean allNumbered = true;
            for (String line : lines) {
                if (!line.trim().isEmpty() && !isNumberedLine(line)) {
                    allNumbered = false;
                    break;
                }
            }
            
            StringBuilder result = new StringBuilder();
            int num = 1;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i > 0) result.append("\n");
                
                if (line.trim().isEmpty()) {
                    result.append(line);
                } else if (allNumbered) {
                    // Remove numbers
                    result.append(stripNumberPrefix(line));
                } else {
                    // Add numbers
                    if (!isNumberedLine(line)) {
                        result.append(num++).append(". ").append(line);
                    } else {
                        result.append(line);
                        num++;
                    }
                }
            }
            
            doc.remove(lineStart, lineEnd - lineStart);
            doc.insertString(lineStart, result.toString(), null);
            
            return !allNumbered;
        } catch (BadLocationException e) {
            return false;
        }
    }
    
    private static boolean isNumberedLine(String line) {
        return line.matches("^\\d+\\.\\s.*") || line.matches("^\\d+\\.\\s*$");
    }
    
    private static String stripNumberPrefix(String line) {
        return line.replaceFirst("^\\d+\\.\\s*", "");
    }
    
    private static int getLineStart(StyledDocument doc, int pos) throws BadLocationException {
        String text = doc.getText(0, pos);
        int lastNewline = text.lastIndexOf('\n');
        return lastNewline + 1;
    }
    
    private static int getLineEnd(StyledDocument doc, int pos) throws BadLocationException {
        String text = doc.getText(0, doc.getLength());
        int nextNewline = text.indexOf('\n', pos);
        return nextNewline == -1 ? doc.getLength() : nextNewline;
    }
}
