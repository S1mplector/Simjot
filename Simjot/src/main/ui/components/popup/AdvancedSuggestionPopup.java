/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.popup;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
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
import javax.swing.Timer;

/**
 * Stable popup for hover suggestions with smooth fade/slide animation.
 * 
 * Unlike the older popup, this does not animate width/height from tiny bounds,
 * which avoids jitter when the popup is frequently updated while moving mouse.
 */
public class AdvancedSuggestionPopup extends JWindow {

    private static final int ARC = 12;
    private static final int PADDING = 8;
    private static final int SHADOW = 8;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 72;
    private static final int ANIM_TICK_MS = 15;
    private static final int SHOW_OFFSET_Y = 8;
    private static final long SHOW_DURATION_MS = 180;
    private static final long UPDATE_DURATION_MS = 130;
    private static final long HIDE_DURATION_MS = 150;

    private final KeyEventDispatcher escDispatcher;
    private final AWTEventListener outsideClickListener;

    private JPanel chromePanel;
    private JPanel contentHost;
    private Timer animationTimer;

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
        stopAnimation();
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
        if (!isVisible()) {
            Rectangle startBounds = new Rectangle(targetX, targetY + SHOW_OFFSET_Y, width, height);
            setBounds(startBounds);
            setVisible(true);
            animateLocation(new Point(startBounds.x, startBounds.y),
                    new Point(targetBounds.x, targetBounds.y),
                    SHOW_DURATION_MS, null);
            return;
        }

        Rectangle startBounds = getBounds();
        if (startBounds.equals(targetBounds)) {
            return;
        }
        // Keep updates stable while visible: no fade/scale, just smooth relocation.
        setSize(targetBounds.width, targetBounds.height);
        animateLocation(new Point(startBounds.x, startBounds.y),
                new Point(targetBounds.x, targetBounds.y),
                UPDATE_DURATION_MS, null);
    }

    public void hidePopup() {
        if (!isVisible()) return;
        Rectangle startBounds = getBounds();
        Point from = new Point(startBounds.x, startBounds.y);
        Point to = new Point(startBounds.x, startBounds.y + SHOW_OFFSET_Y);
        animateLocation(from, to, HIDE_DURATION_MS, () -> setVisible(false));
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

    private void animateLocation(Point start, Point end, long durationMs, Runnable onComplete) {
        stopAnimation();

        if (durationMs <= 0L) {
            setLocation(end);
            if (onComplete != null) onComplete.run();
            return;
        }

        final long startTime = System.nanoTime();
        animationTimer = new Timer(ANIM_TICK_MS, null);
        animationTimer.addActionListener(e -> {
            double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;
            float t = (float) Math.min(1.0, elapsedMs / durationMs);
            float ease = easeOutCubic(t);

            int x = lerp(start.x, end.x, ease);
            int y = lerp(start.y, end.y, ease);
            setLocation(x, y);

            if (t >= 1f) {
                stopAnimation();
                setLocation(end);
                if (onComplete != null) onComplete.run();
            }
        });
        animationTimer.start();
    }

    private void stopAnimation() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}
