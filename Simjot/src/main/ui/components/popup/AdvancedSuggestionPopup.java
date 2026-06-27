/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.popup;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JWindow;

/**
 * Stable popup for hover suggestions.
 *
 * Deliberately non-animated: hover suggestions appear and disappear instantly so
 * frequent mouse movement never drives a Swing timer on the UI thread.
 */
public class AdvancedSuggestionPopup extends JWindow {

    private static final int ARC = 12;
    private static final int PADDING = 8;
    private static final int SHADOW = 8;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 72;
    private final KeyEventDispatcher escDispatcher;
    private final AWTEventListener outsideClickListener;

    private JPanel chromePanel;
    private JPanel contentHost;

    public AdvancedSuggestionPopup(Window owner) {
        super(owner);
        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        try { setAutoRequestFocus(false); } catch (Throwable ignored) {}

        escDispatcher = ev -> {
            if (isVisible() && ev.getID() == KeyEvent.KEY_PRESSED && ev.getKeyCode() == KeyEvent.VK_ESCAPE) {
                hidePopup();
                return true;
            }
            return false;
        };
        outsideClickListener = ae -> {
            if (!isVisible()) return;
            if (!(ae instanceof java.awt.event.MouseEvent me)) return;
            if (me.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) return;
            if (!getBounds().contains(me.getLocationOnScreen())) {
                hidePopup();
            }
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escDispatcher);
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void dispose() {
        try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escDispatcher);
        } catch (Throwable ignored) {}
        try {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
        } catch (Throwable ignored) {}
        super.dispose();
    }

    public void showAt(int screenX, int screenY, Supplier<JPanel> contentFactory) {
        if (contentFactory == null) return;
        JPanel content = contentFactory.get();
        if (content == null) return;
        content.setOpaque(false);

        ensureUiShell();
        contentHost.removeAll();
        contentHost.add(content, BorderLayout.CENTER);
        contentHost.revalidate();
        contentHost.repaint();

        Dimension pref = contentHost.getPreferredSize();
        int width = Math.max(MIN_WIDTH, pref.width + SHADOW * 2);
        int height = Math.max(MIN_HEIGHT, pref.height + SHADOW * 2);
        int targetX = screenX - width / 2;
        int targetY = screenY - height - 8;

        Rectangle targetBounds = new Rectangle(targetX, targetY, width, height);
        if (!getBounds().equals(targetBounds)) {
            setBounds(targetBounds);
        }
        if (!isVisible()) {
            setVisible(true);
        }
    }

    public void hidePopup() {
        if (!isVisible()) return;
        setVisible(false);
    }

    private void ensureUiShell() {
        if (chromePanel != null) return;

        chromePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                for (int i = SHADOW; i > 0; i--) {
                    float a = (0.045f * i / SHADOW);
                    g2.setColor(new Color(0, 0, 0, (int) (255 * a)));
                    g2.fillRoundRect(PADDING - i, PADDING - i,
                            w - 2 * (PADDING - i), h - 2 * (PADDING - i),
                            ARC + i * 2, ARC + i * 2);
                }

                Rectangle r = new Rectangle(PADDING, PADDING, w - 2 * PADDING, h - 2 * PADDING);
                g2.setColor(new Color(255, 255, 255, 245));
                g2.fillRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
                g2.setColor(new Color(206, 211, 218, 220));
                g2.drawRoundRect(r.x, r.y, Math.max(0, r.width - 1), Math.max(0, r.height - 1), ARC, ARC);
                g2.dispose();
            }
        };
        chromePanel.setOpaque(false);
        chromePanel.setLayout(new BorderLayout());

        contentHost = new JPanel(new BorderLayout());
        contentHost.setOpaque(false);
        contentHost.setBorder(BorderFactory.createEmptyBorder(
                PADDING + 8, PADDING + 10, PADDING + 8, PADDING + 10));
        chromePanel.add(contentHost, BorderLayout.CENTER);

        setContentPane(chromePanel);
    }

}
