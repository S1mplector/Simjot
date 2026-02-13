/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.popup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/**
 * Reusable animated glass-styled popup window.
 * - Chrome: soft shadow + Aero vertical gradient + glass overlay
 * - Animation: size-based expand/collapse using cosine ease-in-out over 220ms
 */
public class AnimatedGlassPopup extends JWindow {

    private static final int ARC = 12;
    private static final int PADDING = 8;
    private static final int SHADOW = 6;
    private static final int COLLAPSED_W = 50;
    private static final int COLLAPSED_H = 50;
    private static final int ANIM_TICK_MS = 15;
    private static final long SHOW_DURATION_MS = 190;
    private static final long HIDE_DURATION_MS = 160;
    private static final long UPDATE_DURATION_MS = 130;

    private JPanel chromePanel; // paints glass background; hosts content
    private int width;
    private int height;
    private int targetX;
    private int targetY;
    private javax.swing.Timer resizeTimer;

    public AnimatedGlassPopup(Window owner) {
        super(owner);
        setBackground(new Color(0,0,0,0));
        setAlwaysOnTop(true);
        setFocusableWindowState(true);

        // Close on ESC or click outside
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (isVisible() && ev.getID() == KeyEvent.KEY_PRESSED && ev.getKeyCode() == KeyEvent.VK_ESCAPE) {
                hidePopup();
                return true;
            }
            return false;
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(ae -> {
            if (isVisible() && ae instanceof java.awt.event.MouseEvent me && me.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                if (!getBounds().contains(me.getLocationOnScreen())) hidePopup();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Show at screen coordinates using a deferred content factory. */
    public void showAt(int screenX, int screenY, Supplier<JPanel> contentFactory) {
        boolean wasVisible = isVisible();
        JPanel content = contentFactory.get();
        content.setOpaque(false);

        chromePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Soft shadow
                int w = getWidth();
                int h = getHeight();
                for (int i = SHADOW; i > 0; i--) {
                    float a = 0.05f * i / SHADOW;
                    g2.setColor(new Color(0,0,0,(int)(255*a)));
                    g2.fillRoundRect(PADDING - i, PADDING - i, w - 2*(PADDING - i), h - 2*(PADDING - i), ARC + i*2, ARC + i*2);
                }

                // Glass background
                Rectangle r = new Rectangle(PADDING, PADDING, w - 2*PADDING, h - 2*PADDING);
                Color top = new Color(252,252,252, 235);
                Color bottom = new Color(231,231,231, 235);
                main.ui.theme.aero.AeroPainters.paintVerticalGradient(g2, r, top, bottom, ARC);
                main.ui.theme.aero.AeroPainters.paintGlassOverlay(g2, r, ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        chromePanel.setOpaque(false);
        chromePanel.setLayout(new GridBagLayout());
        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        inner.setOpaque(false);
        for (Component c : content.getComponents()) inner.add(c);
        chromePanel.add(inner);

        Dimension pref = content.getPreferredSize();
        width = Math.max(220, pref.width + (PADDING*2) + SHADOW*2);
        height = Math.max(40, pref.height + (PADDING*2) + SHADOW*2);
        targetX = screenX - width/2;
        targetY = screenY - height - 8;

        setContentPane(chromePanel);
        if (resizeTimer != null && resizeTimer.isRunning()) {
            resizeTimer.stop();
        }

        Rectangle endBounds = new Rectangle(targetX, targetY, width, height);
        if (!wasVisible) {
            Rectangle startBounds = new Rectangle(targetX, targetY, COLLAPSED_W, COLLAPSED_H);
            setBounds(startBounds);
            setVisible(true);
            if (getFocusableWindowState()) {
                requestFocusInWindow();
            }
            animateBounds(startBounds, endBounds, SHOW_DURATION_MS, null);
            return;
        }

        Rectangle startBounds = getBounds();
        if (startBounds.equals(endBounds)) {
            revalidate();
            repaint();
            return;
        }
        animateBounds(startBounds, endBounds, UPDATE_DURATION_MS, null);
    }

    /** Animate collapse then hide. */
    public void hidePopup() {
        if (!isVisible()) return;
        if (resizeTimer != null && resizeTimer.isRunning()) {
            resizeTimer.stop();
        }

        Rectangle startBounds = getBounds();
        Rectangle endBounds = new Rectangle(startBounds.x, startBounds.y, COLLAPSED_W, COLLAPSED_H);
        if (startBounds.width <= COLLAPSED_W && startBounds.height <= COLLAPSED_H) {
            setVisible(false);
            return;
        }
        animateBounds(startBounds, endBounds, HIDE_DURATION_MS, () -> setVisible(false));
    }

    private void animateBounds(Rectangle startBounds, Rectangle endBounds, long durationMs, Runnable onComplete) {
        if (durationMs <= 0L) {
            setBounds(endBounds);
            revalidate();
            repaint();
            if (onComplete != null) onComplete.run();
            return;
        }

        final long startTime = System.currentTimeMillis();
        resizeTimer = new javax.swing.Timer(ANIM_TICK_MS, null);
        resizeTimer.addActionListener(e -> {
            long now = System.currentTimeMillis();
            float t = Math.min(1f, (now - startTime) / (float) durationMs);
            float ease = (float) (0.5 - 0.5 * Math.cos(Math.PI * t));

            int x = (int) (startBounds.x + (endBounds.x - startBounds.x) * ease);
            int y = (int) (startBounds.y + (endBounds.y - startBounds.y) * ease);
            int w = (int) (startBounds.width + (endBounds.width - startBounds.width) * ease);
            int h = (int) (startBounds.height + (endBounds.height - startBounds.height) * ease);

            setBounds(x, y, w, h);
            revalidate();
            repaint();
            if (t >= 1f) {
                resizeTimer.stop();
                setBounds(endBounds);
                if (onComplete != null) onComplete.run();
            }
        });
        resizeTimer.start();
    }
}
