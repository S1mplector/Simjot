package main.transitions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FadeTransitionPanel extends JComponent {
    private float alpha = 0f;
    private Timer fadeTimer;
    
    public void startFadeOut(Runnable callback) {
        alpha = 0f;
        setVisible(true);
        fadeTimer = new Timer(33, null);
        fadeTimer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                alpha += 0.055f; // adjusted for 33ms tick to keep duration similar
                if (alpha >= 1f) {
                    alpha = 1f;
                    fadeTimer.stop();
                    if (callback != null) {
                        callback.run();
                    }
                    repaint();
                } else {
                    repaint();
                }
            }
        });
        fadeTimer.start();
    }
    
    public void startFadeIn() {
        alpha = 1f;
        fadeTimer = new Timer(33, null);
        fadeTimer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                alpha -= 0.055f; // adjusted for 33ms tick to keep duration similar
                if (alpha <= 0f) {
                    alpha = 0f;
                    fadeTimer.stop();
                    setVisible(false);
                    repaint();
                } else {
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
}
