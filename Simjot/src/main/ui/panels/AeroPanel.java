package main.ui.panels;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import main.ui.theme.aero.AeroPainters;

public class AeroPanel extends JPanel {
    private int arc = 10;

    public AeroPanel() {
        setOpaque(false);
    }

    public AeroPanel(int arc) {
        setOpaque(false);
        this.arc = arc;
    }

    public void setArc(int arc) { this.arc = arc; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
        AeroPainters.paintVerticalGradient(g2, r, new Color(255,255,255,200), new Color(235,235,235,200), arc);
        AeroPainters.paintGlassOverlay(g2, r, arc);
        // Inner shadow
        g2.setColor(new Color(0,0,0,30));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1f, getHeight()-1f, arc, arc));
        g2.dispose();
    }
}
