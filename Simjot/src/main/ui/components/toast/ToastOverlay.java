/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.toast;

import java.awt.AlphaComposite;
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

import main.infrastructure.ffi.NativeAccess;

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
    private static final int ARC = 16;
    private static final int MARGIN_TOP = 24;
    private static final long DISPLAY_DURATION_MS = 2200;
    private static final long SLIDE_IN_MS = 320;      // Slightly longer for smoothness
    private static final long SLIDE_OUT_MS = 400;     // Even longer for graceful exit
    private static final int FRAME_RATE = 120;        // High frame rate for smoothness
    
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
        private float opacity = 0f;
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
            this.progress = 1f;
            this.opacity = 1f;
            this.dismissed = false;
            
            // Instant show - no animation
            setLocation(getX(), targetY);
            setVisible(true);
            
            // Hold for display duration, then dismiss
            Timer holdTimer = new Timer((int) DISPLAY_DURATION_MS, ev -> {
                if (!dismissed) dismissInstant();
            });
            holdTimer.setRepeats(false);
            holdTimer.start();
        }
        
        void dismissQuick() {
            if (dismissed) return;
            dismissed = true;
            if (animationTimer != null) animationTimer.stop();
            dispose();
            if (onComplete != null) onComplete.run();
        }
        
        void dismissInstant() {
            if (dismissed) return;
            dismissed = true;
            if (animationTimer != null) animationTimer.stop();
            dispose();
            if (onComplete != null) onComplete.run();
        }
        
        private void startAnimation() {
            animationStart = System.nanoTime();
            int delay = 1000 / FRAME_RATE;
            
            animationTimer = new Timer(delay, e -> {
                long elapsed = (System.nanoTime() - animationStart) / 1_000_000;
                float t = Math.min(1f, elapsed / (float) SLIDE_IN_MS);
                
                // Use native smoothstep for ultra-smooth easing
                float eased = NativeAccess.easeSmoothstep(t);
                progress = eased;
                opacity = eased;
                
                // Update position and trigger repaint for opacity
                int y = (int) (startY + (targetY - startY) * progress);
                setLocation(getX(), y);
                repaint();
                
                if (t >= 1f) {
                    animationTimer.stop();
                    opacity = 1f;
                    
                    // Hold for display duration, then slide out
                    Timer holdTimer = new Timer((int) DISPLAY_DURATION_MS, ev -> {
                        if (!dismissed) slideOut();
                    });
                    holdTimer.setRepeats(false);
                    holdTimer.start();
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
                float t = Math.min(1f, elapsed / (float) SLIDE_OUT_MS);
                
                // Use custom easing: slight bounce/pop before sliding up
                // First 15% of animation: subtle downward "anticipation"
                // Rest: smooth slide up with fade
                float eased;
                if (t < 0.15f) {
                    // Anticipation: move slightly down
                    float anticipationT = t / 0.15f;
                    eased = 1f + 0.02f * NativeAccess.easeSmoothstep(anticipationT);
                } else {
                    // Main slide out
                    float mainT = (t - 0.15f) / 0.85f;
                    float smoothed = NativeAccess.easeSmoothstep(mainT);
                    eased = 1.02f - 1.02f * smoothed;
                }
                
                progress = Math.max(0f, eased);
                opacity = 1f - NativeAccess.easeSmoothstep(t); // Fade out smoothly
                
                int y = (int) (startY + (targetY - startY) * progress);
                setLocation(getX(), y);
                repaint();
                
                if (t >= 1f) {
                    animationTimer.stop();
                    dispose();
                    if (onComplete != null) onComplete.run();
                }
            });
            animationTimer.start();
        }
        
        private class ToastPanel extends JPanel {
            private final Color bgTop;
            private final Color bgBottom;
            private final Color barTop;
            private final Color barBottom;
            private final Color badgeTop;
            private final Color badgeBottom;
            private final Color iconColor;
            private final Color textColor;
            private final Color borderOuter;
            private final Color borderInner;

            ToastPanel() {
                setOpaque(false);
                Color accent = toastType.accent;
                bgTop = mix(new Color(255, 255, 255), accent, 0.06f);
                bgBottom = mix(new Color(242, 244, 246), accent, 0.03f);
                barTop = mix(accent, Color.WHITE, 0.18f);
                barBottom = mix(accent, Color.BLACK, 0.12f);
                badgeTop = mix(accent, Color.WHITE, 0.8f);
                badgeBottom = mix(accent, Color.WHITE, 0.65f);
                iconColor = mix(accent, Color.BLACK, 0.18f);
                textColor = new Color(35, 40, 46);
                borderOuter = new Color(0, 0, 0, 50);
                borderInner = new Color(255, 255, 255, 130);
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                
                int w = getWidth(), h = getHeight();
                float alpha = Math.max(0f, Math.min(1f, opacity));
                RoundRectangle2D outer = new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC);
                RoundRectangle2D inner = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, ARC - 2, ARC - 2);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

                // Soft shadow
                g2.setColor(new Color(0, 0, 0, 26));
                g2.fill(new RoundRectangle2D.Float(2, 4, w - 4, h - 2, ARC, ARC));
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new RoundRectangle2D.Float(1, 2, w - 2, h - 1, ARC, ARC));

                // Polished background with subtle tint
                g2.setPaint(new GradientPaint(0, 0, bgTop, 0, h, bgBottom));
                g2.fill(outer);
                g2.setPaint(new GradientPaint(0, 0, withAlpha(Color.WHITE, 170), 0, h * 0.6f, withAlpha(Color.WHITE, 0)));
                g2.fill(inner);
                g2.setPaint(new GradientPaint(0, h * 0.6f, withAlpha(Color.BLACK, 0), 0, h, withAlpha(Color.BLACK, 18)));
                g2.fill(inner);

                // Accent bar
                int barW = 6;
                int barArc = Math.min(ARC, barW * 2);
                g2.setPaint(new GradientPaint(0, 0, barTop, 0, h, barBottom));
                g2.fill(new RoundRectangle2D.Float(0, 0, barW, h, barArc, barArc));
                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawLine(1, 2, 1, h - 3);

                // Border
                g2.setColor(borderOuter);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC));
                g2.setColor(borderInner);
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, ARC - 2, ARC - 2));

                // Icon badge
                int badgeSize = 22;
                int badgeX = barW + 10;
                int badgeY = (h - badgeSize) / 2;
                g2.setPaint(new GradientPaint(0, badgeY, badgeTop, 0, badgeY + badgeSize, badgeBottom));
                g2.fillOval(badgeX, badgeY, badgeSize, badgeSize);
                g2.setColor(new Color(0, 0, 0, 28));
                g2.drawOval(badgeX, badgeY, badgeSize, badgeSize);

                // Icon based on type
                int iconX = badgeX + (badgeSize - 18) / 2;
                int iconY = badgeY + (badgeSize - 18) / 2;
                g2.setColor(iconColor);
                drawIcon(g2, iconX, iconY, toastType);

                // Message text
                Font baseFont = getFont();
                if (baseFont == null) baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
                g2.setFont(baseFont.deriveFont(Font.PLAIN, 13f));
                g2.setColor(textColor);
                FontMetrics fm = g2.getFontMetrics();
                int textX = badgeX + badgeSize + 10;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                
                // Truncate if needed
                String displayText = message;
                int maxWidth = w - textX - 14;
                if (fm.stringWidth(displayText) > maxWidth) {
                    while (fm.stringWidth(displayText + "...") > maxWidth && displayText.length() > 0) {
                        displayText = displayText.substring(0, displayText.length() - 1);
                    }
                    displayText += "...";
                }
                g2.drawString(displayText, textX, textY);
                
                g2.dispose();
            }

            private Color mix(Color a, Color b, float t) {
                t = Math.max(0f, Math.min(1f, t));
                int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
                int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
                int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
                int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
                return new Color(r, g, bl, al);
            }

            private Color withAlpha(Color c, int alpha) {
                int a = Math.max(0, Math.min(255, alpha));
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
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
