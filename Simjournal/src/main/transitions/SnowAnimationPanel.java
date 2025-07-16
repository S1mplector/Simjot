package main.transitions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class SnowAnimationPanel extends JPanel {
    private static final int PARTICLE_COUNT = 50;
    private SnowParticle[] particles;
    private Timer timer;
    private Random random = new Random();
    
    public SnowAnimationPanel() {
        setOpaque(false);  // Transparent panel
        setBackground(Color.BLACK);  // Ensure background is black if needed
        initParticles();
        // Change delay to 20 ms for smoother animation.
        timer = new Timer(20, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateParticles();
                repaint();
            }
        });
        timer.start();
    }
    
    private void initParticles() {
        particles = new SnowParticle[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = new SnowParticle(
                random.nextInt(800),  // x position
                random.nextInt(600),  // y position
                2 + random.nextInt(4),  // size between 2 and 5
                1 + random.nextInt(3)   // speed between 1 and 3
            );
        }
    }
    
    private void updateParticles() {
        int height = getHeight();
        int width = getWidth();
        for (SnowParticle p : particles) {
            p.y += p.speed;
            if (p.y > height) {
                p.y = 0;
                p.x = random.nextInt(width);
            }
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (particles == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 255, 255, 200));
        for (SnowParticle p : particles) {
            g2.fillOval(p.x, p.y, p.size, p.size);
        }
        g2.dispose();
    }
    
    private static class SnowParticle {
        int x, y, size, speed;
        public SnowParticle(int x, int y, int size, int speed) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
        }
    }
}
