/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

/**
 * Softly fades completed sentences once the writer starts the next sentence.
 */
public final class SentenceFocusHighlighter {
    private SentenceFocusHighlighter() {}

    public static void install(JTextComponent editor, BooleanSupplier enabledSupplier) {
        if (editor == null || enabledSupplier == null) return;
        Controller controller = new Controller(editor, enabledSupplier);
        controller.install();
    }

    private static final class Controller {
        private final JTextComponent editor;
        private final BooleanSupplier enabledSupplier;
        private final Highlighter.HighlightPainter painter = new FadePainter(new Color(255, 255, 255, 118));
        private final List<Object> tags = new ArrayList<>();
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
                String text = doc.getText(0, doc.getLength());
                int caret = Math.max(0, Math.min(editor.getCaretPosition(), text.length()));
                int activeStart = activeSentenceStart(text, caret);
                if (activeStart <= 0 || !hasNonWhitespace(text, activeStart, caret)) {
                    clear();
                    return;
                }

                List<Range> ranges = completedSentencesBefore(text, activeStart);
                Highlighter highlighter = editor.getHighlighter();
                clear();
                for (Range range : ranges) {
                    if (range.end > range.start) {
                        tags.add(highlighter.addHighlight(range.start, range.end, painter));
                    }
                }
            } catch (BadLocationException ignored) {
                clear();
            }
        }

        private void clear() {
            if (tags.isEmpty()) return;
            Highlighter highlighter = editor.getHighlighter();
            for (Object tag : tags) {
                try { highlighter.removeHighlight(tag); } catch (Throwable ignored) {}
            }
            tags.clear();
        }
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

    private static List<Range> completedSentencesBefore(String text, int limit) {
        List<Range> ranges = new ArrayList<>();
        int sentenceStart = nextNonWhitespace(text, 0, limit);
        int i = sentenceStart;
        while (i < limit) {
            char ch = text.charAt(i);
            if (isSentenceTerminator(text, i)) {
                int end = i + 1;
                while (end < limit && isSentenceTerminator(text, end)) end++;
                while (end < limit && isSentenceCloser(text.charAt(end))) end++;
                if (hasSentenceBody(text, sentenceStart, i)) {
                    ranges.add(new Range(sentenceStart, end));
                }
                sentenceStart = nextNonWhitespace(text, end, limit);
                i = sentenceStart;
            } else {
                i++;
            }
        }
        return ranges;
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

    private record Range(int start, int end) {}

    private static final class FadePainter implements Highlighter.HighlightPainter {
        private final Color veil;

        private FadePainter(Color veil) {
            this.veil = veil;
        }

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            if (offs0 >= offs1) return;
            try {
                Rectangle start = c.modelToView2D(offs0).getBounds();
                Rectangle end = c.modelToView2D(Math.max(offs0, offs1 - 1)).getBounds();
                Rectangle clip = bounds instanceof Rectangle r ? r : c.getVisibleRect();

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(veil);

                if (start.y == end.y) {
                    int x = Math.max(start.x, clip.x);
                    int width = Math.max(0, end.x + end.width - x);
                    g2.fillRect(x, start.y, width, start.height);
                } else {
                    int right = clip.x + clip.width;
                    g2.fillRect(start.x, start.y, Math.max(0, right - start.x), start.height);
                    int y = start.y + start.height;
                    while (y < end.y) {
                        g2.fillRect(clip.x, y, clip.width, start.height);
                        y += start.height;
                    }
                    g2.fillRect(clip.x, end.y, Math.max(0, end.x + end.width - clip.x), end.height);
                }
                g2.dispose();
            } catch (BadLocationException ignored) {}
        }
    }
}
