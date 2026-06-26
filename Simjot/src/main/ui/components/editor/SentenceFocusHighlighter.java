/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Softly fades completed sentences once the writer starts the next sentence.
 */
public final class SentenceFocusHighlighter {
    public static final String DIM_RANGES_PROPERTY = "simjot.sentenceFocus.dimRanges";

    private SentenceFocusHighlighter() {}

    public record DimRange(int start, int end) {
        public boolean contains(int offset) {
            return offset >= start && offset < end;
        }
    }

    public static void install(JTextComponent editor, BooleanSupplier enabledSupplier) {
        if (editor == null || enabledSupplier == null) return;
        Controller controller = new Controller(editor, enabledSupplier);
        controller.install();
    }

    private static final class Controller {
        private final JTextComponent editor;
        private final BooleanSupplier enabledSupplier;
        private List<DimRange> ranges = List.of();
        private Document document;
        private boolean updateQueued;

        private final DocumentListener documentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleUpdate(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleUpdate(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleUpdate(); }
        };

        private Controller(JTextComponent editor, BooleanSupplier enabledSupplier) {
            this.editor = editor;
            this.enabledSupplier = enabledSupplier;
        }

        private void install() {
            attachDocument(editor.getDocument());
            editor.addCaretListener(e -> scheduleUpdate());
            editor.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { scheduleUpdate(); }
                @Override public void focusLost(FocusEvent e) { clear(); }
            });
            editor.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { scheduleUpdate(); }
            });
            editor.addPropertyChangeListener("document", evt -> {
                if (evt.getOldValue() instanceof Document oldDoc) oldDoc.removeDocumentListener(documentListener);
                if (evt.getNewValue() instanceof Document newDoc) attachDocument(newDoc);
                scheduleUpdate();
            });
            scheduleUpdate();
        }

        private void attachDocument(Document newDocument) {
            document = newDocument;
            if (document != null) document.addDocumentListener(documentListener);
        }

        private void scheduleUpdate() {
            if (updateQueued) return;
            updateQueued = true;
            SwingUtilities.invokeLater(() -> {
                updateQueued = false;
                update();
            });
        }

        private void update() {
            if (!enabledSupplier.getAsBoolean() || !editor.isFocusOwner()) {
                clear();
                return;
            }
            if (editor.getSelectionStart() != editor.getSelectionEnd()) {
                clear();
                return;
            }
            Document doc = editor.getDocument();
            if (doc == null || doc.getLength() == 0) {
                clear();
                return;
            }
            try {
                int docLen = doc.getLength();
                int caret = Math.max(0, Math.min(editor.getCaretPosition(), docLen));
                int fetchLen = Math.min(docLen, caret + 500);
                String text = doc.getText(0, fetchLen);
                int limit = dimLimit(text, caret);
                if (limit <= 0) {
                    clear();
                    return;
                }

                publishRanges(completedSentencesBefore(text, limit));
            } catch (BadLocationException ignored) {
                clear();
            }
        }

        private void clear() {
            publishRanges(List.of());
        }

        private void publishRanges(List<DimRange> nextRanges) {
            List<DimRange> immutable = nextRanges == null || nextRanges.isEmpty() ? List.of() : List.copyOf(nextRanges);
            if (ranges.equals(immutable)) return;
            List<DimRange> previous = ranges;
            ranges = immutable;
            editor.putClientProperty(DIM_RANGES_PROPERTY, ranges);
            repaintChangedRanges(previous, immutable);
        }

        private void repaintChangedRanges(List<DimRange> previous, List<DimRange> next) {
            Rectangle dirty = null;
            dirty = addRangesToDirty(dirty, previous);
            dirty = addRangesToDirty(dirty, next);
            if (dirty == null) {
                editor.repaint();
                return;
            }
            dirty.grow(4, 4);
            editor.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }

        private Rectangle addRangesToDirty(Rectangle dirty, List<DimRange> source) {
            if (source == null || source.isEmpty()) return dirty;
            for (DimRange range : source) {
                Rectangle bounds = rangeBounds(range);
                if (bounds == null) continue;
                dirty = dirty == null ? bounds : dirty.union(bounds);
            }
            return dirty;
        }

        private Rectangle rangeBounds(DimRange range) {
            if (range == null || range.end <= range.start) return null;
            try {
                java.awt.geom.Rectangle2D start = editor.modelToView2D(range.start);
                java.awt.geom.Rectangle2D end = editor.modelToView2D(Math.max(range.start, range.end - 1));
                if (start == null || end == null) return null;
                Rectangle a = start.getBounds();
                Rectangle b = end.getBounds();
                Rectangle union = a.union(b);
                union.x = 0;
                union.width = Math.max(editor.getWidth(), union.width);
                return union;
            } catch (BadLocationException ignored) {
                return null;
            }
        }
    }

    private static int dimLimit(String text, int caret) {
        int activeStart = activeSentenceStart(text, caret);
        if (activeStart <= 0) return 0;
        if (hasNonWhitespace(text, activeStart, caret)) {
            return activeStart;
        }
        int terminator = previousTerminatorIndex(text, caret);
        if (terminator < 0) return 0;
        return sentenceStartForTerminator(text, terminator);
    }

    private static int activeSentenceStart(String text, int caret) {
        int scan = Math.max(0, Math.min(caret, text.length())) - 1;
        while (scan >= 0) {
            if (isSentenceTerminator(text, scan)) {
                int start = scan + 1;
                while (start < text.length() && isSentenceTerminator(text, start)) start++;
                while (start < text.length() && isSentenceCloser(text.charAt(start))) start++;
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
                return start;
            }
            scan--;
        }
        return 0;
    }

    private static int previousTerminatorIndex(String text, int caret) {
        int scan = Math.max(0, Math.min(caret, text.length())) - 1;
        while (scan >= 0 && (Character.isWhitespace(text.charAt(scan)) || isSentenceCloser(text.charAt(scan)))) scan--;
        while (scan >= 0) {
            if (isSentenceTerminator(text, scan)) return scan;
            scan--;
        }
        return -1;
    }

    private static int sentenceStartForTerminator(String text, int terminatorIndex) {
        int scan = Math.max(0, Math.min(terminatorIndex, text.length() - 1)) - 1;
        while (scan >= 0) {
            if (isSentenceTerminator(text, scan)) {
                int start = scan + 1;
                while (start < text.length() && isSentenceTerminator(text, start)) start++;
                while (start < text.length() && isSentenceCloser(text.charAt(start))) start++;
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
                return start;
            }
            scan--;
        }
        return 0;
    }

    private static List<DimRange> completedSentencesBefore(String text, int limit) {
        List<DimRange> ranges = new ArrayList<>();
        int sentenceStart = nextNonWhitespace(text, 0, limit);
        int i = sentenceStart;
        while (i < limit) {
            char ch = text.charAt(i);
            if (isSentenceTerminator(text, i)) {
                int end = i + 1;
                while (end < limit && isSentenceTerminator(text, end)) end++;
                while (end < limit && isSentenceCloser(text.charAt(end))) end++;
                if (hasSentenceBody(text, sentenceStart, i)) {
                    ranges.add(new DimRange(sentenceStart, end));
                }
                sentenceStart = nextNonWhitespace(text, end, limit);
                i = sentenceStart;
            } else {
                i++;
            }
        }
        return coalesceRanges(ranges);
    }

    private static List<DimRange> coalesceRanges(List<DimRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return List.of();
        int start = ranges.get(0).start();
        int end = ranges.get(ranges.size() - 1).end();
        return List.of(new DimRange(start, end));
    }

    private static int nextNonWhitespace(String text, int start, int limit) {
        int i = Math.max(0, start);
        int end = Math.min(limit, text.length());
        while (i < end && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static boolean hasNonWhitespace(String text, int start, int end) {
        int from = Math.max(0, start);
        int to = Math.min(end, text.length());
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isSentenceTerminator(char ch) {
        return ch == '.' || ch == '!' || ch == '?' || ch == '\u2026';
    }

    private static boolean isSentenceTerminator(String text, int index) {
        char ch = text.charAt(index);
        if (!isSentenceTerminator(ch)) return false;
        if (ch == '.' && index > 0 && index + 1 < text.length()
                && Character.isDigit(text.charAt(index - 1))
                && Character.isDigit(text.charAt(index + 1))) {
            return false;
        }
        return true;
    }

    private static boolean hasSentenceBody(String text, int start, int terminatorIndex) {
        int from = Math.max(0, start);
        int to = Math.min(terminatorIndex, text.length());
        for (int i = from; i < to; i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isSentenceCloser(char ch) {
        return ch == '"' || ch == '\'' || ch == ')' || ch == ']' || ch == '}' || ch == '\u201d' || ch == '\u2019';
    }

}
