package main.ui.buttons;

import java.awt.*;
import java.awt.geom.AffineTransform;
import javax.swing.*;
import main.util.ResourceLoader;

public class ToolbarIconButton extends JButton {
    private final String id;
    private ImageIcon icon;
    private boolean selected;
    private boolean glow;
    private Timer glowTimer;
    private float glowPhase=0f;

    private static boolean globalGlow = main.util.SettingsStore.get().isGlowEnabled();
    private static final java.util.List<ToolbarIconButton> INSTANCES = new java.util.ArrayList<>();

    public ToolbarIconButton(String iconId){
        this.id = iconId.toLowerCase();
        setPreferredSize(new Dimension(40,40));
        setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);

        // attempt load bitmap resource from Simjot/img/{id}.png
        this.icon = ResourceLoader.createImageIcon("Simjot/img/"+id+".png");

        INSTANCES.add(this);
        setGlow(globalGlow);
    }

    public void setSelected(boolean s){ this.selected=s; repaint(); }

    /** Enable or disable fancy glow animation */
    public void setGlow(boolean g){
        if(this.glow==g) return;
        this.glow=g;
        if(glow){
            glowTimer = new Timer(60, e->{ glowPhase+=0.05f; repaint(); });
            glowTimer.start();
        } else {
            if(glowTimer!=null){ glowTimer.stop(); glowTimer=null; }
        }
    }

    /** globally enable/disable glow for all toolbar icon buttons */
    public static void setGlowEnabled(boolean enabled){
        globalGlow = enabled;
        for(ToolbarIconButton b: INSTANCES){ b.setGlow(enabled); }
    }
    public static boolean isGlowEnabled(){ return globalGlow; }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(getModel().isPressed()) g2.setColor(new Color(200,200,200));
        else if(selected) g2.setColor(new Color(0,120,215,80));
        else if(getModel().isRollover()) g2.setColor(new Color(220,220,220));
        else g2.setColor(getParent()!=null?getParent().getBackground():Color.LIGHT_GRAY);
        g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);

        boolean painted=false;
        if(icon!=null){ icon.paintIcon(this,g2,(getWidth()-icon.getIconWidth())/2,(getHeight()-icon.getIconHeight())/2); painted=true; }
        if(!painted){ drawVector(g2); }
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);

        // draw glow if enabled
        if(glow){
            float alpha = 0.4f + 0.2f*(float)Math.sin(glowPhase);
            int glowSize = getWidth();
            Color glowCol = new Color(255, 200, 0, (int)(alpha*255));
            java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(
                    new Point(getWidth()/2, getHeight()/2), glowSize/2f,
                    new float[]{0f,1f}, new Color[]{glowCol, new Color(255,200,0,0)});
            g2.setPaint(paint);
            g2.fillOval(2,2,getWidth()-4,getHeight()-4);
        }

        g2.dispose();
    }

    private void drawVector(Graphics2D g2){
        int w=getWidth(), h=getHeight();
        int cx=w/2, cy=h/2;
        g2.setStroke(new BasicStroke(2f));
        switch(id){
            case "new": // improved pencil icon
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-Math.PI/4); // diagonal orientation

                // Pencil body
                g2.setColor(new Color(255, 215, 0)); // yellow
                g2.fillRect(-14, -3, 18, 6);

                // Ferrule (metal band)
                g2.setColor(new Color(180, 180, 180));
                g2.fillRect(4, -3, 3, 6);

                // Eraser
                g2.setColor(new Color(255, 120, 120));
                g2.fillRect(7, -3, 5, 6);

                // Tip
                g2.setColor(new Color(140, 100, 60));
                int[] px = { -14, -18, -14 };
                int[] py = { -3, 0, 3 };
                g2.fillPolygon(px, py, 3);

                // Outline for definition
                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(-14, -3, 18, 6); // body outline
                g2.drawRect(4, -3, 3, 6);    // ferrule outline
                g2.drawRect(7, -3, 5, 6);    // eraser outline
                g2.drawPolygon(px, py, 3);   // tip outline

                g2.setTransform(old);
                break;
            case "cork":
                // Draw a cork round with subtle speckles
                int r = Math.min(w, h) - 16; // padding
                int x0 = cx - r/2, y0 = cy - r/2;
                // base cork color
                g2.setColor(new Color(205, 155, 100));
                g2.fillOval(x0, y0, r, r);
                // inner shading ring
                g2.setColor(new Color(185, 140, 90));
                g2.drawOval(x0+1, y0+1, r-2, r-2);
                // speckles
                g2.setColor(new Color(160, 110, 70));
                for(int i=0;i<10;i++){
                    double ang = i * (Math.PI*2/10.0) + 0.3*i;
                    int sx = (int)(cx + (r*0.25)*Math.cos(ang));
                    int sy = (int)(cy + (r*0.25)*Math.sin(ang));
                    g2.fillOval(sx-2, sy-1, 3, 3);
                }
                // highlight
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillOval(x0+3, y0+3, r/2, r/2);
                // outline
                g2.setColor(new Color(120, 80, 50));
                g2.drawOval(x0, y0, r, r);
                break;
            case "delete":
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx-6,cy-6,cx+6,cy+6);
                g2.drawLine(cx-6,cy+6,cx+6,cy-6);
                break;
            case "trash":
                g2.setColor(Color.BLACK);
                // bin outline
                int bw=14,bh=16; int x=cx-bw/2, y=cy-bh/2+3;
                g2.drawRoundRect(x,y,bw,bh,3,3);
                g2.fillRect(x-2,y-4,bw+4,3); // lid
                g2.drawLine(cx-4,y-6,cx+4,y-6); // handle
                break;
            default:
                g2.setColor(Color.DARK_GRAY);
                g2.drawLine(cx-5,cy-5,cx+5,cy+5);
                g2.drawLine(cx-5,cy+5,cx+5,cy-5);
        }
    }
} 