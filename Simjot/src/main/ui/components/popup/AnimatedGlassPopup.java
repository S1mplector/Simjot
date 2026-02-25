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
 * - Animation: size-based expand/collapse using eased cubic timing
 */
public class AnimatedGlassPopup extends JWindow {

    private static final int ARC = 12;
    private static final int PADDING = 8;
    private static final int SHADOW = 6;
    private static final int SCREEN_MARGIN = 6;
    private static final int ANCHOR_GAP = 8;
    private static final int COLLAPSED_W = 50;
    private static final int COLLAPSED_H = 50;
    private static final int ANIM_TICK_MS = 16;
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
        chromePanel.setDoubleBuffered(true);
        chromePanel.setLayout(new GridBagLayout());
        JPanel inner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));
        for (Component c : content.getComponents()) inner.add(c);
        chromePanel.add(inner);

        Dimension pref = inner.getPreferredSize();
        width = Math.max(228, pref.width + (PADDING * 2) + SHADOW * 2);
        height = Math.max(46, pref.height + (PADDING * 2) + SHADOW * 2);
        Rectangle endBounds = resolvePopupBounds(screenX, screenY, width, height);
        targetX = endBounds.x;
        targetY = endBounds.y;

        setContentPane(chromePanel);
        if (resizeTimer != null && resizeTimer.isRunning()) {
            resizeTimer.stop();
        }

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
            repaint();
            if (onComplete != null) onComplete.run();
            return;
        }

        final long startTime = System.nanoTime();
        resizeTimer = new javax.swing.Timer(ANIM_TICK_MS, null);
        resizeTimer.setCoalesce(true);
        resizeTimer.addActionListener(e -> {
            long now = System.nanoTime();
            float elapsedMs = (now - startTime) / 1_000_000f;
            float t = Math.min(1f, elapsedMs / (float) durationMs);
            float ease = 1f - (float) Math.pow(1f - t, 3f);

            int x = (int) (startBounds.x + (endBounds.x - startBounds.x) * ease);
            int y = (int) (startBounds.y + (endBounds.y - startBounds.y) * ease);
            int w = (int) (startBounds.width + (endBounds.width - startBounds.width) * ease);
            int h = (int) (startBounds.height + (endBounds.height - startBounds.height) * ease);

            setBounds(x, y, w, h);
            repaint();
            if (t >= 1f) {
                resizeTimer.stop();
                setBounds(endBounds);
                repaint();
                if (onComplete != null) onComplete.run();
            }
        });
        resizeTimer.start();
    }

    private Rectangle resolvePopupBounds(int anchorX, int anchorY, int popupWidth, int popupHeight) {
        Rectangle usable = resolveUsableScreen(anchorX, anchorY);
        int minX = usable.x + SCREEN_MARGIN;
        int minY = usable.y + SCREEN_MARGIN;
        int maxX = usable.x + Math.max(0, usable.width - popupWidth - SCREEN_MARGIN);
        int maxY = usable.y + Math.max(0, usable.height - popupHeight - SCREEN_MARGIN);

        int preferredX = anchorX - popupWidth / 2;
        int aboveY = anchorY - popupHeight - ANCHOR_GAP;
        int belowY = anchorY + ANCHOR_GAP;

        int x = clamp(preferredX, minX, maxX);
        int y = aboveY;
        if (aboveY < minY && belowY <= maxY) {
            y = belowY;
        }
        y = clamp(y, minY, maxY);

        return new Rectangle(x, y, popupWidth, popupHeight);
    }

    private Rectangle resolveUsableScreen(int x, int y) {
        GraphicsConfiguration best = null;
        Point p = new Point(x, y);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice device : ge.getScreenDevices()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            if (bounds.contains(p)) {
                best = gc;
                break;
            }
        }
        if (best == null && getOwner() != null) {
            best = getOwner().getGraphicsConfiguration();
        }
        if (best == null) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, Math.max(1, screen.width), Math.max(1, screen.height));
        }

        Rectangle bounds = new Rectangle(best.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(best);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width = Math.max(1, bounds.width - insets.left - insets.right);
        bounds.height = Math.max(1, bounds.height - insets.top - insets.bottom);
        return bounds;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }
}
