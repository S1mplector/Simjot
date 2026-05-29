/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.spelling;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;

import main.ui.components.popup.AdvancedSuggestionPopup;

/**
 * AutocorrectDocumentFilter - non-intrusive autocorrect suggestions for JTextComponent.
 *
 * It preserves the same correction logic from IntelligentAutocorrect, but instead of
 * immediately replacing typed words, it marks candidates with a red underline and shows
 * a hover popup for explicit user approval.
 */
public class AutocorrectDocumentFilter extends DocumentFilter {

    private static final String CLIENT_PROP_FILTER = "autocorrect.filter.instance";
    private static final String CLIENT_PROP_UNDO_INSTALLED = "autocorrect.undo.installed";
    private static final String CLIENT_PROP_UNDO_DELEGATE = "autocorrect.undo.delegate";
    private static final int HOVER_SHOW_DELAY_MS = 120;
    private static final int HOVER_HIDE_DELAY_MS = 180;

    // Trigger characters that cause autocorrect detection to run.
    private static final String TRIGGER_CHARS = " .,;:!?)\n\t";
    private static final int SMART_SYMBOL_WINDOW_RADIUS = 6;

    private final IntelligentAutocorrect autocorrect;
    private final JTextComponent textComponent;
    private final List<PendingSuggestion> pendingSuggestions = new ArrayList<>();
    private final Set<String> ignoredThisSession = new HashSet<>();
    private final Highlighter.HighlightPainter suggestionPainter = new RedUnderlinePainter();

    private final MouseMotionListener hoverMotionListener;
    private final MouseAdapter hoverMouseListener;
    private final CaretListener caretListener;
    private final Timer popupShowTimer;
    private final Timer popupHideTimer;

    private AdvancedSuggestionPopup suggestionPopup;
    private PendingSuggestion hoveredSuggestion;
    private PendingSuggestion pendingHoverSuggestion;
    private Point pendingHoverPoint;

    private boolean suggestionsEnabled = true;
    private boolean undoInProgress = false;

    // Track last accepted correction for undo support.
    private String lastOriginal = null;
    private String lastCorrection = null;
    private int lastCorrectionStart = -1;

    public AutocorrectDocumentFilter(JTextComponent textComponent) {
        this.textComponent = textComponent;
        this.autocorrect = IntelligentAutocorrect.get();
        this.hoverMotionListener = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                onMouseMoved(e);
            }
        };
        this.hoverMouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                cancelHidePopup();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                scheduleHidePopup();
            }
        };
        this.caretListener = e -> {
            cancelShowPopup();
            hideSuggestionPopup();
        };
        this.popupShowTimer = new Timer(HOVER_SHOW_DELAY_MS, e -> showPendingSuggestionPopup());
        this.popupShowTimer.setRepeats(false);
        this.popupHideTimer = new Timer(HOVER_HIDE_DELAY_MS, e -> hidePopupIfPointerIsOutside());
        this.popupHideTimer.setRepeats(false);
        setupUndoShortcut();
        installHoverSupport();
    }

    /**
     * Install autocorrect suggestions on a JTextComponent.
     */
    public static AutocorrectDocumentFilter install(JTextComponent component) {
        return install(component, true);
    }

    /**
     * Install smart typography and optionally enable autocorrect suggestions.
     */
    public static AutocorrectDocumentFilter install(JTextComponent component, boolean suggestionsEnabled) {
        if (component == null) return null;
        uninstall(component);

        Document doc = component.getDocument();
        if (doc instanceof AbstractDocument) {
            AutocorrectDocumentFilter filter = new AutocorrectDocumentFilter(component);
            filter.setEnabled(suggestionsEnabled);
            ((AbstractDocument) doc).setDocumentFilter(filter);
            try {
                component.putClientProperty(CLIENT_PROP_FILTER, filter);
            } catch (Throwable ignored) {}
            return filter;
        }
        return null;
    }

    /**
     * Remove autocorrect from a JTextComponent.
     */
    public static void uninstall(JTextComponent component) {
        if (component == null) return;
        try {
            Object existing = component.getClientProperty(CLIENT_PROP_FILTER);
            if (existing instanceof AutocorrectDocumentFilter filter) {
                filter.dispose();
            }
        } catch (Throwable ignored) {}
        try {
            Object delegate = component.getClientProperty(CLIENT_PROP_UNDO_DELEGATE);
            if (delegate instanceof Action action) {
                component.getActionMap().put("undo", action);
            }
        } catch (Throwable ignored) {}
        try {
            Document doc = component.getDocument();
            if (doc instanceof AbstractDocument) {
                ((AbstractDocument) doc).setDocumentFilter(null);
            }
        } catch (Throwable ignored) {}
        try {
            component.putClientProperty(CLIENT_PROP_FILTER, null);
            component.putClientProperty(CLIENT_PROP_UNDO_INSTALLED, null);
            component.putClientProperty(CLIENT_PROP_UNDO_DELEGATE, null);
        } catch (Throwable ignored) {}
    }

    private void dispose() {
        cancelShowPopup();
        cancelHidePopup();
        clearAllSuggestions();
        hideSuggestionPopup();
        if (suggestionPopup != null) {
            try { suggestionPopup.dispose(); } catch (Throwable ignored) {}
            suggestionPopup = null;
        }
        try { textComponent.removeMouseMotionListener(hoverMotionListener); } catch (Throwable ignored) {}
        try { textComponent.removeMouseListener(hoverMouseListener); } catch (Throwable ignored) {}
        try { textComponent.removeCaretListener(caretListener); } catch (Throwable ignored) {}
    }

    private void installHoverSupport() {
        textComponent.addMouseMotionListener(hoverMotionListener);
        textComponent.addMouseListener(hoverMouseListener);
        textComponent.addCaretListener(caretListener);
    }

    private void setupUndoShortcut() {
        InputMap im = textComponent.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textComponent.getActionMap();

        KeyStroke ctrlZ = KeyStroke.getKeyStroke("control Z");
        KeyStroke metaZ = KeyStroke.getKeyStroke("meta Z");

        Action delegate = null;
        try {
            Object cached = textComponent.getClientProperty(CLIENT_PROP_UNDO_DELEGATE);
            if (cached instanceof Action action) {
                delegate = action;
            }
        } catch (Throwable ignored) {}

        if (delegate == null) {
            delegate = am.get("undo");
            if (delegate == null) {
                Object k1 = im.get(ctrlZ);
                if (k1 != null) delegate = am.get(k1);
            }
            if (delegate == null) {
                Object k2 = im.get(metaZ);
                if (k2 != null) delegate = am.get(k2);
            }
            try { textComponent.putClientProperty(CLIENT_PROP_UNDO_DELEGATE, delegate); } catch (Throwable ignored) {}
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
        try { textComponent.putClientProperty(CLIENT_PROP_UNDO_INSTALLED, Boolean.TRUE); } catch (Throwable ignored) {}
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        super.insertString(fb, offset, string, attr);

        if (undoInProgress) return;
        if (string != null && !string.isEmpty()) {
            int pivot = offset + string.length();
            applySmartSymbolSubstitutionsAround(pivot);
        }
        if (!suggestionsEnabled) return;
        pruneSuggestionsAfterDocumentChange();
        if (isTriggerChar(string)) {
            SwingUtilities.invokeLater(() -> checkAndProposeWord(offset));
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        super.replace(fb, offset, length, text, attrs);

        if (undoInProgress) return;
        if (text != null && !text.isEmpty()) {
            int pivot = offset + text.length();
            applySmartSymbolSubstitutionsAround(pivot);
        }
        if (!suggestionsEnabled) return;
        pruneSuggestionsAfterDocumentChange();
        if (text != null && isTriggerChar(text)) {
            SwingUtilities.invokeLater(() -> checkAndProposeWord(offset));
        }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        super.remove(fb, offset, length);
        if (!suggestionsEnabled || undoInProgress) return;
        pruneSuggestionsAfterDocumentChange();
    }

    private boolean isTriggerChar(String text) {
        if (text == null || text.isEmpty()) return false;
        char c = text.charAt(0);
        return TRIGGER_CHARS.indexOf(c) >= 0;
    }

    private void applySmartSymbolSubstitutionsAround(int pivotOffset) {
        if (undoInProgress) return;
        try {
            Document doc = textComponent.getDocument();
            int docLen = doc.getLength();
            if (docLen <= 1) return;

            int pivot = Math.max(0, Math.min(pivotOffset, docLen));
            int start = Math.max(0, pivot - SMART_SYMBOL_WINDOW_RADIUS);
            int end = Math.min(docLen, pivot + SMART_SYMBOL_WINDOW_RADIUS);
            if (end <= start) return;

            String before = doc.getText(start, end - start);
            String after = substituteSmartSymbols(before);
            if (before.equals(after)) return;

            AttributeSet attrs = null;
            if (doc instanceof javax.swing.text.StyledDocument sd) {
                int attrOffset = Math.max(0, Math.min(start, Math.max(0, docLen - 1)));
                try {
                    attrs = sd.getCharacterElement(attrOffset).getAttributes();
                } catch (Throwable ignored) {}
            }

            int caret = textComponent.getCaretPosition();
            int delta = after.length() - before.length();

            undoInProgress = true;
            try {
                doc.remove(start, before.length());
                doc.insertString(start, after, attrs);
            } finally {
                undoInProgress = false;
            }

            int newCaret = Math.max(0, Math.min(doc.getLength(), caret + delta));
            if (newCaret != caret) {
                textComponent.setCaretPosition(newCaret);
            }
        } catch (BadLocationException ignored) {
        }
    }

    private String substituteSmartSymbols(String value) {
        if (value == null || value.isEmpty()) return value;
        // Longest first to avoid partial overlaps.
        String out = value
                .replace("<->", "↔")
                .replace("--", "—")
                .replace("->", "→")
                .replace("<-", "←")
                .replace(">=", "≥")
                .replace("<=", "≤")
                .replace("!=", "≠")
                .replace("...", "…");
        return out;
    }

    private void checkAndProposeWord(int triggerOffset) {
        try {
            Document doc = textComponent.getDocument();
            String text = doc.getText(0, doc.getLength());

            int wordEnd = triggerOffset;
            int wordStart = wordEnd - 1;
            while (wordStart >= 0 && isWordChar(text.charAt(wordStart))) {
                wordStart--;
            }
            wordStart++;

            if (wordStart >= wordEnd) return;
            String word = text.substring(wordStart, wordEnd);
            if (word.length() <= 1) return;

            if (ignoredThisSession.contains(word.toLowerCase(Locale.ROOT))) {
                return;
            }

            String prevWord = getPreviousWord(text, wordStart);
            String nextWord = null;

            String correction = autocorrect.getContextualCorrection(word, prevWord, nextWord);
            if (correction == null) {
                correction = autocorrect.getCorrection(word);
            }

            if (correction != null && !correction.equals(word)) {
                proposeSuggestion(wordStart, wordEnd, word, correction);
            }
        } catch (BadLocationException ignored) {
        }
    }

    private void proposeSuggestion(int start, int end, String original, String correction) {
        if (start < 0 || end <= start || original == null || correction == null) return;
        String lower = original.toLowerCase(Locale.ROOT);
        if (ignoredThisSession.contains(lower)) return;

        pruneSuggestionsAfterDocumentChange();

        for (PendingSuggestion existing : pendingSuggestions) {
            if (existing.start() == start
                    && existing.end() == end
                    && original.equals(existing.original)
                    && correction.equals(existing.correction)) {
                return;
            }
        }

        try {
            Document doc = textComponent.getDocument();
            Position startPos = doc.createPosition(start);
            Position endPos = doc.createPosition(end);

            PendingSuggestion suggestion = new PendingSuggestion(startPos, endPos, original, correction, lower);
            suggestion.highlightTag = textComponent.getHighlighter().addHighlight(start, end, suggestionPainter);
            pendingSuggestions.add(suggestion);
            textComponent.repaint();
        } catch (BadLocationException ignored) {
        }
    }

    private void pruneSuggestionsAfterDocumentChange() {
        if (pendingSuggestions.isEmpty()) return;
        List<PendingSuggestion> toRemove = new ArrayList<>();
        for (PendingSuggestion suggestion : pendingSuggestions) {
            if (!suggestion.matchesCurrentWord(textComponent.getDocument())) {
                toRemove.add(suggestion);
            }
        }
        for (PendingSuggestion suggestion : toRemove) {
            removeSuggestion(suggestion);
        }
        if (hoveredSuggestion != null && !pendingSuggestions.contains(hoveredSuggestion)) {
            hideSuggestionPopup();
        }
    }

    private void onMouseMoved(MouseEvent e) {
        if (!suggestionsEnabled || pendingSuggestions.isEmpty()) {
            cancelShowPopup();
            scheduleHidePopup();
            return;
        }
        int offset = -1;
        try {
            offset = textComponent.viewToModel2D(e.getPoint());
        } catch (Throwable ignored) {}
        if (offset < 0) {
            cancelShowPopup();
            scheduleHidePopup();
            return;
        }

        PendingSuggestion hit = null;
        for (PendingSuggestion suggestion : pendingSuggestions) {
            if (suggestion.containsOffset(offset)) {
                hit = suggestion;
                break;
            }
        }

        if (hit == null) {
            cancelShowPopup();
            scheduleHidePopup();
            return;
        }

        cancelHidePopup();
        if (hit.equals(hoveredSuggestion)) return;
        scheduleShowPopup(hit, e.getPoint());
    }

    private void showSuggestionPopup(PendingSuggestion suggestion, Point localPoint) {
        AdvancedSuggestionPopup popup = ensureSuggestionPopup();
        if (popup == null) return;

        Point p = new Point(localPoint);
        SwingUtilities.convertPointToScreen(p, textComponent);
        int popupX = p.x + 26;
        int popupY = p.y + 26;
        popup.showAt(popupX, popupY, () -> buildSuggestionPanel(suggestion));
    }

    private void scheduleShowPopup(PendingSuggestion suggestion, Point localPoint) {
        pendingHoverSuggestion = suggestion;
        pendingHoverPoint = localPoint != null ? new Point(localPoint) : anchorPointForSuggestion(suggestion);
        popupShowTimer.restart();
    }

    private void showPendingSuggestionPopup() {
        PendingSuggestion suggestion = pendingHoverSuggestion;
        if (!suggestionsEnabled || suggestion == null || !pendingSuggestions.contains(suggestion)) {
            pendingHoverSuggestion = null;
            pendingHoverPoint = null;
            return;
        }
        hoveredSuggestion = suggestion;
        Point anchor = pendingHoverPoint != null ? pendingHoverPoint : anchorPointForSuggestion(suggestion);
        showSuggestionPopup(suggestion, anchor);
        pendingHoverSuggestion = null;
        pendingHoverPoint = null;
    }

    private void cancelShowPopup() {
        popupShowTimer.stop();
        pendingHoverSuggestion = null;
        pendingHoverPoint = null;
    }

    private void scheduleHidePopup() {
        popupHideTimer.restart();
    }

    private void cancelHidePopup() {
        popupHideTimer.stop();
    }

    private void hidePopupIfPointerIsOutside() {
        if (isPointerOverCurrentSuggestion()) {
            popupHideTimer.restart();
            return;
        }
        if (isPointerOverPopup()) {
            popupHideTimer.restart();
            return;
        }
        hideSuggestionPopup();
    }

    private boolean isPointerOverCurrentSuggestion() {
        PendingSuggestion current = hoveredSuggestion;
        if (current == null || !pendingSuggestions.contains(current)) return false;
        try {
            Point mouse = textComponent.getMousePosition();
            if (mouse == null) return false;
            int offset = textComponent.viewToModel2D(mouse);
            return current.containsOffset(offset);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isPointerOverPopup() {
        if (suggestionPopup == null || !suggestionPopup.isVisible()) return false;
        try {
            return suggestionPopup.getMousePosition() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Point anchorPointForSuggestion(PendingSuggestion suggestion) {
        if (suggestion == null) return new Point(0, 0);
        try {
            int anchorOffset = Math.max(suggestion.start(), suggestion.end() - 1);
            Shape shape = textComponent.modelToView2D(anchorOffset);
            Rectangle r = shape != null ? shape.getBounds() : null;
            if (r != null) {
                return new Point(r.x + Math.max(8, r.width / 2), r.y + Math.max(6, r.height / 2));
            }
        } catch (Throwable ignored) {}
        return new Point(0, 0);
    }

    private AdvancedSuggestionPopup ensureSuggestionPopup() {
        Window owner = SwingUtilities.getWindowAncestor(textComponent);
        if (owner == null) return null;
        if (suggestionPopup == null || suggestionPopup.getOwner() != owner) {
            suggestionPopup = new AdvancedSuggestionPopup(owner);
        }
        return suggestionPopup;
    }

    private JPanel buildSuggestionPanel(PendingSuggestion suggestion) {
        JPanel root = new JPanel();
        root.setOpaque(false);
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JLabel title = new JLabel("<html><b>Autocorrect suggestion</b></html>");
        title.setForeground(new Color(46, 50, 62));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 12f));

        JLabel body = new JLabel("<html><span style='color:#444'>"
                + escapeHtml(suggestion.original) + " &rarr; <b>"
                + escapeHtml(suggestion.correction)
                + "</b></span></html>");
        body.setForeground(new Color(65, 70, 84));
        body.setFont(body.getFont().deriveFont(java.awt.Font.PLAIN, 12f));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        JButton accept = actionButton("Accept");
        accept.addActionListener(ev -> acceptSuggestion(suggestion));
        JButton ignore = actionButton("Don't ask again");
        ignore.addActionListener(ev -> ignoreSuggestion(suggestion));
        actions.add(accept);
        actions.add(ignore);

        root.add(title);
        root.add(javax.swing.Box.createVerticalStrut(8));
        root.add(body);
        root.add(javax.swing.Box.createVerticalStrut(10));
        root.add(actions);
        return root;
    }

    private JButton actionButton(String text) {
        return new SnapshotHoverButton(text);
    }

    private void acceptSuggestion(PendingSuggestion suggestion) {
        if (suggestion == null) return;
        applyCorrection(suggestion.start(), suggestion.end(), suggestion.original, suggestion.correction, false);
        removeSuggestion(suggestion);
        hideSuggestionPopup();
    }

    private void ignoreSuggestion(PendingSuggestion suggestion) {
        if (suggestion == null) return;
        ignoredThisSession.add(suggestion.originalLower);
        autocorrect.ignore(suggestion.originalLower);
        removeSuggestion(suggestion);
        hideSuggestionPopup();
    }

    private void hideSuggestionPopup() {
        cancelShowPopup();
        cancelHidePopup();
        hoveredSuggestion = null;
        if (suggestionPopup != null) {
            try { suggestionPopup.hidePopup(); } catch (Throwable ignored) {}
        }
    }

    private void clearAllSuggestions() {
        if (pendingSuggestions.isEmpty()) return;
        List<PendingSuggestion> copy = new ArrayList<>(pendingSuggestions);
        for (PendingSuggestion suggestion : copy) {
            removeSuggestion(suggestion);
        }
        hideSuggestionPopup();
    }

    private void removeSuggestion(PendingSuggestion suggestion) {
        if (suggestion == null) return;
        pendingSuggestions.remove(suggestion);
        try {
            if (suggestion.highlightTag != null) {
                textComponent.getHighlighter().removeHighlight(suggestion.highlightTag);
            }
        } catch (Throwable ignored) {}
        suggestion.highlightTag = null;
        textComponent.repaint();
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

    private void applyCorrection(int start, int end, String original, String correction, boolean advancePastTrigger) {
        undoInProgress = true;
        try {
            Document doc = textComponent.getDocument();
            AttributeSet attrs = null;
            if (doc instanceof javax.swing.text.StyledDocument sd) {
                try {
                    attrs = sd.getCharacterElement(Math.max(0, start)).getAttributes();
                } catch (Throwable ignored) {}
            }

            lastOriginal = original;
            lastCorrection = correction;
            lastCorrectionStart = start;

            doc.remove(start, end - start);
            doc.insertString(start, correction, attrs);

            int caretPos = start + correction.length();
            if (advancePastTrigger) caretPos++;
            textComponent.setCaretPosition(Math.min(caretPos, doc.getLength()));
        } catch (BadLocationException ignored) {
        } finally {
            undoInProgress = false;
        }
    }

    /**
     * Undo the last accepted correction.
     *
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

            if (currentText.equals(lastCorrection)) {
                doc.remove(lastCorrectionStart, lastCorrection.length());
                doc.insertString(lastCorrectionStart, lastOriginal, null);

                autocorrect.ignore(lastOriginal.toLowerCase(Locale.ROOT));

                lastOriginal = null;
                lastCorrection = null;
                lastCorrectionStart = -1;

                return true;
            }
        } catch (BadLocationException ignored) {
        } finally {
            undoInProgress = false;
        }

        return false;
    }

    /**
     * Enable or disable autocorrect suggestions.
     */
    public void setEnabled(boolean enabled) {
        this.suggestionsEnabled = enabled;
        if (!enabled) {
            clearAllSuggestions();
        }
    }

    /**
     * Check if autocorrect suggestions are enabled.
     */
    public boolean isEnabled() {
        return suggestionsEnabled;
    }

    /**
     * Clear undo state for accepted corrections.
     */
    public void clearUndoState() {
        lastOriginal = null;
        lastCorrection = null;
        lastCorrectionStart = -1;
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static final class PendingSuggestion {
        private final Position startPos;
        private final Position endPos;
        private final String original;
        private final String correction;
        private final String originalLower;
        private Object highlightTag;

        private PendingSuggestion(Position startPos,
                                  Position endPos,
                                  String original,
                                  String correction,
                                  String originalLower) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.original = original;
            this.correction = correction;
            this.originalLower = originalLower;
        }

        private int start() {
            return startPos.getOffset();
        }

        private int end() {
            return endPos.getOffset();
        }

        private boolean containsOffset(int offset) {
            int s = start();
            int e = end();
            return offset >= s && offset < e;
        }

        private boolean matchesCurrentWord(Document doc) {
            if (doc == null) return false;
            int s = start();
            int e = end();
            if (s < 0 || e <= s || e > doc.getLength()) return false;
            try {
                return original.equals(doc.getText(s, e - s));
            } catch (BadLocationException ex) {
                return false;
            }
        }
    }

    /**
     * Uses the same hover plate treatment as ToolbarMenuIconButton (clock/snapshot button).
     */
    private static final class SnapshotHoverButton extends JButton {
        private boolean hovering;

        private SnapshotHoverButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setForeground(new Color(44, 49, 62));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isArmed() && getModel().isPressed();

            Shape plate = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10);
            if (hovering || pressed) {
                Color top = pressed ? new Color(235, 238, 243, 220) : new Color(245, 248, 252, 210);
                Color bot = pressed ? new Color(215, 220, 230, 220) : new Color(225, 230, 238, 210);
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
                g2.fill(plate);
                g2.setColor(new Color(170, 180, 195, 200));
                g2.draw(plate);
            } else {
                g2.setColor(new Color(255, 255, 255, 235));
                g2.fill(plate);
                g2.setColor(new Color(205, 211, 220, 210));
                g2.draw(plate);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RedUnderlinePainter extends LayeredHighlighter.LayerPainter {
        private static final Color STROKE = new Color(212, 64, 64, 220);
        private static final Color GLOW = new Color(255, 142, 142, 96);

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            // Handled by paintLayer.
        }

        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c, View view) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                Shape s = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds);
                Rectangle r = (s instanceof Rectangle) ? (Rectangle) s : s.getBounds();
                if (r.width <= 0 || r.height <= 0) return r;

                int y = r.y + r.height - 2;
                g2.setColor(GLOW);
                g2.fillRect(r.x, y - 1, r.width, 2);

                g2.setColor(STROKE);
                int step = 4;
                int amp = 1;
                int x = r.x;
                while (x < r.x + r.width) {
                    int x2 = Math.min(r.x + r.width, x + step);
                    int mid = (x + x2) / 2;
                    g2.drawLine(x, y, mid, y + amp);
                    g2.drawLine(mid, y + amp, x2, y);
                    x = x2;
                }
                return r;
            } catch (BadLocationException ex) {
                return null;
            } finally {
                g2.dispose();
            }
        }
    }
}
