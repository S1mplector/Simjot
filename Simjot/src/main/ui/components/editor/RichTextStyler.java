/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
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
}
