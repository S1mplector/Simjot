package main.transitions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PetalAnimationPanel extends JPanel {
    private List<Petal> petals;
    private Timer timer;
    private Random random = new Random();
    private BufferedImage[] petalImages;
    private int petalCount = 30;

    public PetalAnimationPanel() {
        setOpaque(false);
        setBackground(Color.BLACK);
        loadImages();
        initPetals();
        timer = new Timer(20, new ActionListener(){
            public void actionPerformed(ActionEvent e){
                updatePetals();
                repaint();
            }
        });
        timer.start();
    }

    private void loadImages() {
        petalImages = new BufferedImage[9];
        for (int i = 0; i < 9; i++) {
            try {
                petalImages[i] = ImageIO.read(new File("img/petal" + (i+1) + ".png"));
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void initPetals() {
        petals = new ArrayList<>();
        for(int i = 0; i < petalCount; i++){
            petals.add(createPetal());
        }
    }

    private Petal createPetal() {
        int width = getWidth() > 0 ? getWidth() : 800;
        int height = getHeight() > 0 ? getHeight() : 600;
        Petal p = new Petal();
        p.x = random.nextInt(width);
        p.y = -random.nextInt(height);
        p.size = random.nextInt(10) + 10;
        p.speedX = random.nextDouble() * 3 - 1.5;
        p.speedY = random.nextDouble() + 2;
        p.rotationSpeed = random.nextDouble() * 0.05 + 0.01;
        p.rotationAngle = 0;
        p.fadeSpeed = random.nextDouble() * 0.005 + 0.002;
        p.opacity = 1.0;
        p.imageIndex = random.nextInt(9);
        return p;
    }

    private void updatePetals() {
        int width = getWidth();
        int height = getHeight();
        for(Petal p : petals){
            p.x += p.speedX;
            p.y += p.speedY;
            p.rotationAngle += p.rotationSpeed;
            if(p.y > height / 2) {
                p.opacity -= p.fadeSpeed;
            }
            if(p.opacity <= 0 || p.y > height) {
                p.opacity = 1.0;
                p.y = -p.size;
                p.x = random.nextInt(width);
                p.imageIndex = random.nextInt(9);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(petals == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        for(Petal p : petals){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)p.opacity));
            g2.translate(p.x + p.size/2, p.y + p.size/2);
            g2.rotate(p.rotationAngle);
            g2.drawImage(petalImages[p.imageIndex], -p.size/2, -p.size/2, p.size, p.size, null);
            g2.rotate(-p.rotationAngle);
            g2.translate(- (p.x + p.size/2), - (p.y + p.size/2));
        }
        g2.dispose();
    }
    
    private class Petal {
        int x, y, size;
        double speedX, speedY;
        double rotationAngle;
        double rotationSpeed;
        double fadeSpeed;
        double opacity;
        int imageIndex;
    }
}
