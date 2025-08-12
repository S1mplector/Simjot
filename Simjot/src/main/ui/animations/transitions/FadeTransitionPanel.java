package main.ui.animations.transitions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FadeTransitionPanel extends JComponent {
    private float alpha = 0f;
    private Timer fadeTimer;
    // Duration in milliseconds for a full fade.
    private int durationMs = 280;
    private long startNanos;
    private boolean fadingOut = false;
    private Runnable onComplete;
    
    public void startFadeOut(Runnable callback) {
        // Stop any in-flight animation
        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }
        fadingOut = true;
        onComplete = callback;
        alpha = 0f;
        setVisible(true);
        startNanos = System.nanoTime();
        fadeTimer = new Timer(16, null); // ~60 FPS
        fadeTimer.setCoalesce(true);
        fadeTimer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                float t = (System.nanoTime() - startNanos) / (durationMs * 1_000_000f);
                if (t >= 1f) {
                    alpha = 1f;
                    fadeTimer.stop();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    repaint();
                } else {
                    alpha = easeInOut(t);
                    repaint();
                }
            }
        });
        fadeTimer.start();
    }
    
    public void startFadeIn() {
        // Stop any in-flight animation
        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }
        fadingOut = false;
        onComplete = null;
        alpha = 1f;
        setVisible(true);
        startNanos = System.nanoTime();
        fadeTimer = new Timer(16, null); // ~60 FPS
        fadeTimer.setCoalesce(true);
        fadeTimer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                float t = (System.nanoTime() - startNanos) / (durationMs * 1_000_000f);
                if (t >= 1f) {
                    alpha = 0f;
                    fadeTimer.stop();
                    setVisible(false);
                    repaint();
                } else {
                    alpha = 1f - easeInOut(t);
                    repaint();
                }
            }
        });
        fadeTimer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        if (alpha > 0f) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // Smooth cubic ease-in-out easing function for t in [0,1].
    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        // Smootherstep-like cubic: 3t^2 - 2t^3
        return (float) (t * t * (3 - 2 * t));
    }
}
