/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.BooleanSupplier;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

/**
 * Installs a subtle current-line glow highlight on a text component.
 */
public final class CurrentLineGlowHighlighter {
    private CurrentLineGlowHighlighter() {}

    public static void install(JTextComponent editor, BooleanSupplier enabledSupplier) {
        install(editor, enabledSupplier,
                new Color(255, 236, 206, 60),
                new Color(220, 180, 140, 90));
    }

    public static void install(JTextComponent editor, BooleanSupplier enabledSupplier, Color fill, Color outline) {
        if (editor == null || enabledSupplier == null) return;
        Controller controller = new Controller(editor, enabledSupplier, fill, outline);
        editor.addCaretListener(e -> controller.update());
        editor.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { controller.update(); }
            @Override public void focusLost(FocusEvent e) { controller.clear(); }
        });
        editor.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { controller.update(); }
        });
        controller.update();
    }

    private static final class Controller {
        private final JTextComponent editor;
        private final BooleanSupplier enabledSupplier;
        private final Highlighter.HighlightPainter painter;
        private Object tag;
        private int currentStart = -1;
        private int currentEnd = -1;

        private Controller(JTextComponent editor, BooleanSupplier enabledSupplier, Color fill, Color outline) {
            this.editor = editor;
            this.enabledSupplier = enabledSupplier;
            this.painter = new GlowPainter(fill, outline);
        }

        private void update() {
            if (!enabledSupplier.getAsBoolean()) {
                clear();
                return;
            }
            if (!editor.isFocusOwner()) {
                clear();
                return;
            }
            if (editor.getSelectionStart() != editor.getSelectionEnd()) {
                clear();
                return;
            }
            try {
                int caret = editor.getCaretPosition();
                int start = Utilities.getRowStart(editor, caret);
                int end = Utilities.getRowEnd(editor, caret);
                if (start < 0 || end < start) {
                    clear();
                    return;
                }
                if (tag != null && start == currentStart && end == currentEnd) {
                    return;
                }
                Highlighter hl = editor.getHighlighter();
                if (tag != null) hl.removeHighlight(tag);
                tag = hl.addHighlight(start, end, painter);
                currentStart = start;
                currentEnd = end;
            } catch (BadLocationException ignored) {
                clear();
            }
        }

        private void clear() {
            if (tag != null) {
                try { editor.getHighlighter().removeHighlight(tag); } catch (Throwable ignored) {}
                tag = null;
            }
            currentStart = -1;
            currentEnd = -1;
        }
    }

    private static final class GlowPainter implements Highlighter.HighlightPainter {
        private final Color fill;
        private final Color outline;

        private GlowPainter(Color fill, Color outline) {
            this.fill = fill;
            this.outline = outline;
        }

        @Override
        public void paint(Graphics g, int offs0, int offs1, java.awt.Shape bounds, JTextComponent c) {
            try {
                java.awt.Rectangle r0 = c.modelToView(offs0);
                if (r0 == null) return;
                Insets insets = c.getInsets();
                int x = insets.left;
                int y = r0.y - 1;
                int h = r0.height + 2;
                int w = Math.max(0, c.getWidth() - insets.left - insets.right);

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(x, y, w, h, 12, 12);
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1.1f));
                g2.drawRoundRect(x, y, w, h, 12, 12);
                g2.dispose();
            } catch (BadLocationException ignored) {}
        }
    }
}
