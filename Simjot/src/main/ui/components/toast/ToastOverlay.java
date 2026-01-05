/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.toast;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Global toast notification overlay with smooth slide-in/out animations.
 * Thread-safe singleton that can be called from any thread.
 * 
 * Usage:
 *   ToastOverlay.success("Entry saved!");
 *   ToastOverlay.error("Failed to save");
 *   ToastOverlay.info("Switched to drawing mode");
 */
public final class ToastOverlay {
    
    public enum ToastType {
        SUCCESS(new Color(46, 160, 67)),      // Green
        ERROR(new Color(220, 60, 60)),         // Red
        INFO(new Color(230, 150, 40));         // Orange
        
        final Color accent;
        ToastType(Color c) { this.accent = c; }
    }
    
    private static final int TOAST_WIDTH = 320;
    private static final int TOAST_HEIGHT = 44;
    private static final int ARC = 14;
    private static final int MARGIN_TOP = 24;
    private static final int SLIDE_DISTANCE = TOAST_HEIGHT + MARGIN_TOP + 10;
    private static final long DISPLAY_DURATION_MS = 2200;
    private static final long ANIMATION_DURATION_MS = 280;
    private static final int FRAME_RATE = 120; // Higher frame rate for smoothness
    
    private static volatile ToastWindow activeWindow;
    private static final ConcurrentLinkedQueue<ToastRequest> queue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean processing = new AtomicBoolean(false);
    
    private record ToastRequest(String message, ToastType type) {}
    
    private ToastOverlay() {}
    
    public static void success(String message) { show(message, ToastType.SUCCESS); }
    public static void error(String message) { show(message, ToastType.ERROR); }
    public static void info(String message) { show(message, ToastType.INFO); }
    
    public static void show(String message, ToastType type) {
        queue.offer(new ToastRequest(message, type));
        processQueue();
    }
    
    private static void processQueue() {
        if (!processing.compareAndSet(false, true)) return;
        
        SwingUtilities.invokeLater(() -> {
            ToastRequest req = queue.poll();
            if (req == null) {
                processing.set(false);
                return;
            }
            
            // Dismiss current toast quickly if one is showing
            if (activeWindow != null && activeWindow.isVisible()) {
                activeWindow.dismissQuick();
            }
            
            // Find the focused window to position relative to
            Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            if (owner == null) {
                for (Window w : Window.getWindows()) {
                    if (w.isVisible() && w instanceof JFrame) { owner = w; break; }
                }
            }
            
            ToastWindow toast = new ToastWindow(owner, req.message, req.type);
            activeWindow = toast;
            toast.showAnimated(() -> {
                processing.set(false);
                if (!queue.isEmpty()) processQueue();
            });
        });
    }
    
    /**
     * The actual toast window - lightweight, undecorated, with smooth animation.
     */
    private static class ToastWindow extends JWindow {
        private final String message;
        private final ToastType toastType;
        private final int targetY;
        private final int startY;
        private float progress = 0f;
        private Timer animationTimer;
        private long animationStart;
        private boolean slidingIn = true;
        private Runnable onComplete;
        private volatile boolean dismissed = false;
        
        ToastWindow(Window owner, String message, ToastType toastType) {
            super(owner);
            this.message = message;
            this.toastType = toastType;
            
            setAlwaysOnTop(true);
            setFocusable(false);
            setFocusableWindowState(false);
            
            // Calculate position
            Rectangle bounds = owner != null ? owner.getBounds() : 
                GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            
            int x = bounds.x + (bounds.width - TOAST_WIDTH) / 2;
            this.startY = bounds.y - TOAST_HEIGHT;
            this.targetY = bounds.y + MARGIN_TOP;
            
            setSize(TOAST_WIDTH, TOAST_HEIGHT);
            setLocation(x, startY);
            setBackground(new Color(0, 0, 0, 0));
            
            setContentPane(new ToastPanel());
        }
        
        void showAnimated(Runnable onComplete) {
            this.onComplete = onComplete;
            this.slidingIn = true;
            this.progress = 0f;
            this.dismissed = false;
            
            setVisible(true);
            startAnimation();
        }
        
        void dismissQuick() {
            if (dismissed) return;
            dismissed = true;
            if (animationTimer != null) animationTimer.stop();
            dispose();
        }
        
        private void startAnimation() {
            animationStart = System.nanoTime();
            int delay = 1000 / FRAME_RATE;
            
            animationTimer = new Timer(delay, e -> {
                long elapsed = (System.nanoTime() - animationStart) / 1_000_000;
                float t = Math.min(1f, elapsed / (float) ANIMATION_DURATION_MS);
                
                // Smooth easing: ease-out for slide in, ease-in for slide out
                if (slidingIn) {
                    progress = easeOutCubic(t);
                } else {
                    progress = 1f - easeInCubic(t);
                }
                
                // Update position
                int y = (int) (startY + (targetY - startY) * progress);
                setLocation(getX(), y);
                
                if (t >= 1f) {
                    animationTimer.stop();
                    
                    if (slidingIn) {
                        // Hold for display duration, then slide out
                        Timer holdTimer = new Timer((int) DISPLAY_DURATION_MS, ev -> {
                            if (!dismissed) slideOut();
                        });
                        holdTimer.setRepeats(false);
                        holdTimer.start();
                    } else {
                        // Animation complete
                        dispose();
                        if (onComplete != null) onComplete.run();
                    }
                }
            });
            animationTimer.start();
        }
        
        private void slideOut() {
            if (dismissed) return;
            slidingIn = false;
            animationStart = System.nanoTime();
            
            int delay = 1000 / FRAME_RATE;
            animationTimer = new Timer(delay, e -> {
                long elapsed = (System.nanoTime() - animationStart) / 1_000_000;
                float t = Math.min(1f, elapsed / (float) ANIMATION_DURATION_MS);
                
                progress = 1f - easeInCubic(t);
                int y = (int) (startY + (targetY - startY) * progress);
                setLocation(getX(), y);
                
                if (t >= 1f) {
                    animationTimer.stop();
                    dispose();
                    if (onComplete != null) onComplete.run();
                }
            });
            animationTimer.start();
        }
        
        // Easing functions for smooth animation
        private static float easeOutCubic(float t) {
            return 1f - (float) Math.pow(1 - t, 3);
        }
        
        private static float easeInCubic(float t) {
            return t * t * t;
        }
        
        /**
         * Custom painted panel with frosted glass aero styling.
         */
        private class ToastPanel extends JPanel {
            ToastPanel() {
                setOpaque(false);
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                
                int w = getWidth(), h = getHeight();
                RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC);
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 35));
                g2.fill(new RoundRectangle2D.Float(2, 3, w - 4, h - 2, ARC, ARC));
                
                // Frosted glass background
                GradientPaint bg = new GradientPaint(0, 0, new Color(255, 255, 255, 235),
                                                     0, h, new Color(245, 245, 245, 220));
                g2.setPaint(bg);
                g2.fill(shape);
                
                // Glass sheen
                GradientPaint sheen = new GradientPaint(0, 0, new Color(255, 255, 255, 120),
                                                        0, h * 0.5f, new Color(255, 255, 255, 30));
                g2.setPaint(sheen);
                g2.fill(shape);
                
                // Accent bar on left
                g2.setColor(toastType.accent);
                g2.fill(new RoundRectangle2D.Float(0, 0, 5, h, 3, 3));
                
                // Inner highlight
                g2.setColor(new Color(255, 255, 255, 100));
                g2.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, ARC - 2, ARC - 2));
                
                // Outer border
                g2.setColor(new Color(0, 0, 0, 40));
                g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, ARC, ARC));
                
                // Icon based on type
                int iconX = 16;
                int iconY = (h - 18) / 2;
                g2.setColor(toastType.accent);
                drawIcon(g2, iconX, iconY, toastType);
                
                // Message text
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                g2.setColor(new Color(40, 45, 55));
                FontMetrics fm = g2.getFontMetrics();
                int textX = iconX + 26;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                
                // Truncate if needed
                String displayText = message;
                int maxWidth = w - textX - 16;
                if (fm.stringWidth(displayText) > maxWidth) {
                    while (fm.stringWidth(displayText + "...") > maxWidth && displayText.length() > 0) {
                        displayText = displayText.substring(0, displayText.length() - 1);
                    }
                    displayText += "...";
                }
                g2.drawString(displayText, textX, textY);
                
                g2.dispose();
            }
            
            private void drawIcon(Graphics2D g2, int x, int y, ToastType tt) {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int s = 18;
                
                if (tt == ToastType.SUCCESS) {
                    // Checkmark
                    g2.drawLine(x + 3, y + s/2, x + s/3 + 2, y + s - 4);
                    g2.drawLine(x + s/3 + 2, y + s - 4, x + s - 2, y + 4);
                } else if (tt == ToastType.ERROR) {
                    // X mark
                    g2.drawLine(x + 4, y + 4, x + s - 4, y + s - 4);
                    g2.drawLine(x + s - 4, y + 4, x + 4, y + s - 4);
                } else if (tt == ToastType.INFO) {
                    // Info circle with i
                    g2.drawOval(x + 1, y + 1, s - 2, s - 2);
                    g2.fillOval(x + s/2 - 2, y + 4, 4, 4);
                    g2.drawLine(x + s/2, y + 10, x + s/2, y + s - 4);
                }
            }
        }
    }
}
