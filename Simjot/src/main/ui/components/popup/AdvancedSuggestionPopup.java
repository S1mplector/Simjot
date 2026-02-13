/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.popup;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
    private boolean opacitySupported = true;
    private float panelAlpha = 1f;

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

        JPanel wrapped = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapped.setOpaque(false);
        for (Component c : content.getComponents()) {
            wrapped.add(c);
        }
        contentHost.add(wrapped, BorderLayout.CENTER);
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
            applyOpacity(0f);
            animate(startBounds, targetBounds, 0f, 1f, SHOW_DURATION_MS, null);
            return;
        }

        Rectangle startBounds = getBounds();
        float startOpacity = currentOpacity();
        if (startBounds.equals(targetBounds) && startOpacity >= 0.99f) {
            return;
        }
        animate(startBounds, targetBounds, startOpacity, 1f, UPDATE_DURATION_MS, null);
    }

    public void hidePopup() {
        if (!isVisible()) return;
        Rectangle startBounds = getBounds();
        Rectangle endBounds = new Rectangle(startBounds.x, startBounds.y + SHOW_OFFSET_Y,
                startBounds.width, startBounds.height);
        float startOpacity = currentOpacity();
        animate(startBounds, endBounds, startOpacity, 0f, HIDE_DURATION_MS, () -> {
            setVisible(false);
            applyOpacity(1f);
        });
    }

    private void ensureUiShell() {
        if (chromePanel != null) return;

        chromePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float alpha = opacitySupported ? 1f : panelAlpha;
                if (alpha < 1f) {
                    g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                }

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

    private void animate(Rectangle startBounds, Rectangle endBounds,
                         float startOpacity, float endOpacity,
                         long durationMs, Runnable onComplete) {
        stopAnimation();

        if (durationMs <= 0L) {
            setBounds(endBounds);
            applyOpacity(endOpacity);
            if (onComplete != null) onComplete.run();
            return;
        }

        final long start = System.nanoTime();
        animationTimer = new Timer(ANIM_TICK_MS, null);
        animationTimer.addActionListener(e -> {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            float t = (float) Math.min(1.0, elapsedMs / durationMs);
            float ease = easeOutCubic(t);

            int x = lerp(startBounds.x, endBounds.x, ease);
            int y = lerp(startBounds.y, endBounds.y, ease);
            int w = lerp(startBounds.width, endBounds.width, ease);
            int h = lerp(startBounds.height, endBounds.height, ease);
            float opacity = startOpacity + (endOpacity - startOpacity) * ease;

            setBounds(x, y, w, h);
            applyOpacity(opacity);
            revalidate();
            repaint();

            if (t >= 1f) {
                stopAnimation();
                setBounds(endBounds);
                applyOpacity(endOpacity);
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

    private float currentOpacity() {
        if (opacitySupported) {
            try {
                return getOpacity();
            } catch (Throwable ignored) {
                opacitySupported = false;
            }
        }
        return panelAlpha;
    }

    private void applyOpacity(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (opacitySupported) {
            try {
                setOpacity(clamped);
                return;
            } catch (Throwable ignored) {
                opacitySupported = false;
            }
        }
        panelAlpha = clamped;
        if (chromePanel != null) {
            chromePanel.repaint();
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
