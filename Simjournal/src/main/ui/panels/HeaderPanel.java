package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;
import javax.swing.*;

public class HeaderPanel extends JPanel {
    private float textAlpha = 0f;
    private float heartScale = 1f;
    private float ecgDraw = 0f;    // 0 = not drawn, 1 = fully drawn
    private float ecgOpacity = 0f; // current alpha of ECG line
    private boolean beatPeak = false; // tracks heart peak to trigger ECG
    private Timer fadeTimer, pulseTimer;
    private String quote;
    
    public HeaderPanel() {
        setPreferredSize(new Dimension(800, 120));
        setOpaque(false);
        // Random calming quotes.
        String[] quotes = {
            "Take a deep breath. You are enough.",
            "Every day is a fresh start.",
            "Peace begins with a smile.",
            "Keep calm and carry on.",
            "What's something you did that you're proud of today?",
            "Jot down your thoughts, calm them down."
        };
        quote = quotes[new Random().nextInt(quotes.length)];
    }
    
    public void startAnimation() {
        fadeTimer = new Timer(50, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                textAlpha += 0.05f;
                if (textAlpha >= 1f) {
                    textAlpha = 1f;
                    fadeTimer.stop();
                }
                repaint();
            }
        });
        fadeTimer.start();
        
        pulseTimer = new Timer(16, new ActionListener() { // ~60 FPS for smoothness
            double t = 0;
            public void actionPerformed(ActionEvent e) {
                t += 0.05; // finer step for same overall speed but smoother
                double sin = Math.sin(t);
                heartScale = (float) (1 + 0.1 * sin);

                // Detect beat peak and trigger ECG line
                if(sin > 0.95 && !beatPeak){
                    beatPeak = true;
                    ecgDraw = 0f;       // restart drawing
                    ecgOpacity = 1f;    // full opacity at start
                } else if(sin < 0.0){
                    beatPeak = false; // reset for next beat
                }

                // Advance ECG drawing while visible
                if(ecgOpacity > 0f){
                    if(ecgDraw < 1f){
                        ecgDraw += 0.06f; // speed of drawing left→right
                        if(ecgDraw > 1f) ecgDraw = 1f;
                    } else {
                        ecgOpacity -= 0.02f; // fade out after fully drawn
                        if(ecgOpacity < 0f) ecgOpacity = 0f;
                    }
                }
                repaint();
            }
        });
        pulseTimer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw heart shape behind text.
        AffineTransform old = g2.getTransform();
        g2.translate(width / 2, height / 2 - 10);
        g2.scale(heartScale, heartScale);
        Shape heart = createHeartShape();
        g2.setColor(new Color(255, 0, 0, (int) (100 * textAlpha)));
        g2.fill(heart);
        g2.setTransform(old);
        
        // Draw ECG pulse line under heart
        if(ecgOpacity > 0f){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ecgOpacity));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Color.WHITE);
            int ecgWidth = 150;
            int startX = width/2 - ecgWidth/2;
            int yBase = height/2 - 8;
            Path2D path = new Path2D.Double();
            path.moveTo(startX, yBase);
            path.lineTo(startX+20, yBase);
            path.lineTo(startX+35, yBase-20);
            path.lineTo(startX+50, yBase+15);
            path.lineTo(startX+70, yBase);
            path.lineTo(startX+ecgWidth, yBase);
            // Clip to progressive width
            Shape oldClip = g2.getClip();
            g2.setClip(startX, yBase-25, (int)(ecgWidth*ecgDraw), 50);
            g2.draw(path);
            g2.setClip(oldClip);
            // reset composite for subsequent drawings
            g2.setComposite(AlphaComposite.SrcOver);
        }
        
        // Draw "Simjournal" text.
        g2.setFont(new Font("SansSerif", Font.BOLD, 36));
        FontMetrics fm = g2.getFontMetrics();
        String text = "Simnote";
        int textWidth = fm.stringWidth(text);
        int x = (width - textWidth) / 2;
        int y = height / 2;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, textAlpha));
        g2.setColor(Color.WHITE);
        g2.drawString(text, x, y);
        
        // Draw the quote below in a smaller italic font.
        g2.setFont(new Font("SansSerif", Font.ITALIC, 18));
        FontMetrics fm2 = g2.getFontMetrics();
        int quoteWidth = fm2.stringWidth(quote);
        int quoteX = (width - quoteWidth) / 2;
        int quoteY = y + fm.getDescent() + 25;
        g2.drawString(quote, quoteX, quoteY);
        
        g2.dispose();
    }
    
    private Shape createHeartShape() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, -20);
        path.curveTo(-25, -50, -60, -10, 0, 30);
        path.curveTo(60, -10, 25, -50, 0, -20);
        path.closePath();
        return path;
    }
}
