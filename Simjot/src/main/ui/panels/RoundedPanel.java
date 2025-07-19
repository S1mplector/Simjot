package main.ui.panels;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

public class RoundedPanel extends JPanel {
    private int arc = 20;

    public RoundedPanel() {
        setOpaque(false);
    }

    public RoundedPanel(int arc) {
        setOpaque(false);
        this.arc = arc;
    }

    public void setArc(int arc) {
        this.arc = arc;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        Shape roundRect = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        
        if (getBackground() != null) {
            g2.setColor(getBackground());
            g2.fill(roundRect);
        }

        if (getForeground() != null) {
            g2.setColor(getForeground());
            g2.draw(roundRect);
        }
        
        g2.dispose();
    }
} 
