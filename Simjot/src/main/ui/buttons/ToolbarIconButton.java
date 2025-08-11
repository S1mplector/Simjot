package main.ui.buttons;

import java.awt.*;
import java.awt.geom.AffineTransform;
import javax.swing.*;
import main.util.ResourceLoader;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.icons.VectorIconPainter;

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

        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        // Selected gives a light blue tint similar to Windows 7 selection
        Color top = pressed ? AeroTheme.BUTTON_PRESS_TOP : (hover || selected ? AeroTheme.BUTTON_HOVER_TOP : AeroTheme.BUTTON_BG_TOP);
        Color bottom = pressed ? AeroTheme.BUTTON_PRESS_BOTTOM : (hover || selected ? AeroTheme.BUTTON_HOVER_BOTTOM : AeroTheme.BUTTON_BG_BOTTOM);

        Rectangle r = new Rectangle(0,0,getWidth(),getHeight());
        AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
        AeroPainters.paintGlassOverlay(g2, r, 10);

        // Soft border
        g2.setColor(new Color(180,180,180));
        g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);

        // Draw icon/vector. For 'trash', 'new', and 'delete', always prefer vector rendering.
        boolean painted=false;
        if(!("trash".equals(id) || "new".equals(id) || "delete".equals(id)) && icon!=null){
            icon.paintIcon(this,g2,(getWidth()-icon.getIconWidth())/2,(getHeight()-icon.getIconHeight())/2);
            painted=true;
        }
        if(!painted){ drawVector(g2); }

        // Optional glow retained but softened
        if(glow){
            float alpha = 0.25f + 0.15f*(float)Math.sin(glowPhase);
            int glowSize = Math.min(getWidth(), getHeight());
            Color glowCol = new Color(255, 220, 120, (int)(alpha*255));
            java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(
                    new Point(getWidth()/2, getHeight()/2), glowSize/2f,
                    new float[]{0f,1f}, new Color[]{glowCol, new Color(255,220,120,0)});
            g2.setPaint(paint);
            g2.fillOval(3,3,getWidth()-6,getHeight()-6);
        }

        g2.dispose();
    }

    private void drawVector(Graphics2D g2){
        int w=getWidth(), h=getHeight();
        int cx=w/2, cy=h/2;
        g2.setStroke(new BasicStroke(2f));
        switch(id){
            case "new": { // Aero-styled pencil icon with gradients and gloss
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-Math.PI/4); // diagonal orientation

                // Drop shadow under pencil
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(2, 6), 14f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(-16, 4, 28, 6);
                sh.dispose();

                // Pencil body gradient
                LinearGradientPaint body = new LinearGradientPaint(-14, -3, -14, 3,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 232, 120), new Color(255, 215, 0), new Color(235, 190, 0)});
                g2.setPaint(body);
                g2.fillRoundRect(-14, -3, 18, 6, 2, 2);

                // Top gloss on body
                g2.setPaint(new GradientPaint(0, -3, new Color(255,255,255,180), 0, 0, new Color(255,255,255,0)));
                g2.fillRoundRect(-13, -3, 16, 3, 2, 2);

                // Ferrule (metal band) with metallic gradient
                LinearGradientPaint ferrule = new LinearGradientPaint(4, -3, 4, 3,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(210,210,210), new Color(170,170,170), new Color(200,200,200)});
                g2.setPaint(ferrule);
                g2.fillRect(4, -3, 3, 6);

                // Eraser with soft gradient
                LinearGradientPaint eraser = new LinearGradientPaint(7, -3, 7, 3,
                        new float[]{0f,1f}, new Color[]{new Color(255, 170, 170), new Color(255, 120, 120)});
                g2.setPaint(eraser);
                g2.fillRect(7, -3, 5, 6);

                // Wooden tip + graphite
                int[] px = { -14, -18, -14 };
                int[] py = { -3, 0, 3 };
                LinearGradientPaint wood = new LinearGradientPaint(-18, -3, -18, 3,
                        new float[]{0f,1f}, new Color[]{new Color(214, 174, 123), new Color(160, 120, 80)});
                g2.setPaint(wood);
                g2.fillPolygon(px, py, 3);
                g2.setColor(new Color(70,70,70));
                g2.fillOval(-19, -1, 3, 2); // graphite tip

                // Outlines for definition
                g2.setColor(new Color(90, 90, 90));
                g2.setStroke(new BasicStroke(1.1f));
                g2.drawRoundRect(-14, -3, 18, 6, 2, 2); // body outline
                g2.drawRect(4, -3, 3, 6);    // ferrule outline
                g2.drawRect(7, -3, 5, 6);    // eraser outline
                g2.drawPolygon(px, py, 3);   // tip outline

                g2.setTransform(old);
                break; }
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
            case "delete": {
                // Delegate to shared painter for exact reuse
                int size = Math.min(w, h) - 8;
                VectorIconPainter.paint(g2, "delete", cx - size/2, cy - size/2, size);
                break; }
            case "trash": {
                // Windows 7-like glossy trash can
                int bw = 16, bh = 18; int x = cx - bw/2, y = cy - bh/2 + 2;

                // Base shadow
                Graphics2D s = (Graphics2D) g2.create();
                s.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                s.setPaint(new RadialGradientPaint(new Point(cx, y+bh+1), bw/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,80), new Color(0,0,0,0)}));
                s.fillOval(x-2, y+bh-2, bw+4, 5);
                s.dispose();

                // Body gradient (light top to slightly darker bottom)
                LinearGradientPaint bodyPaint = new LinearGradientPaint(
                        x, y, x, y+bh,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(245,245,245), new Color(225,225,225), new Color(205,205,205)});
                g2.setPaint(bodyPaint);
                g2.fillRoundRect(x, y, bw, bh, 4, 4);

                // Body inner highlight
                g2.setPaint(new LinearGradientPaint(x, y+1, x, y+bh/2f,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255,160), new Color(255,255,255,0)}));
                g2.fillRoundRect(x+1, y+1, bw-2, bh/2, 3, 3);

                // Body border
                g2.setColor(new Color(150,150,150));
                g2.drawRoundRect(x, y, bw, bh, 4, 4);

                // Lid (slightly wider with bevel)
                int lw = bw + 6; int lh = 4; int lx = cx - lw/2; int ly = y - lh - 1;
                LinearGradientPaint lidPaint = new LinearGradientPaint(lx, ly, lx, ly+lh,
                        new float[]{0f,1f}, new Color[]{new Color(235,235,235), new Color(200,200,200)});
                g2.setPaint(lidPaint);
                g2.fillRoundRect(lx, ly, lw, lh, 3, 3);
                g2.setColor(new Color(140,140,140));
                g2.drawRoundRect(lx, ly, lw, lh, 3, 3);

                // Handle on lid
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(120,120,120));
                g2.drawLine(cx-5, ly-2, cx+5, ly-2);

                // Vertical slats with highlight and separator
                int[] sx = new int[]{ x+4, cx, x+bw-4 };
                for(int xi : sx){
                    // light highlight
                    g2.setColor(new Color(255,255,255,130));
                    g2.drawLine(xi, y+3, xi, y+bh-3);
                    // darker separator offset by 1px
                    g2.setColor(new Color(150,150,150,180));
                    g2.drawLine(xi+1, y+3, xi+1, y+bh-3);
                }
                break; }
            
            default:
                g2.setColor(Color.DARK_GRAY);
                g2.drawLine(cx-5,cy-5,cx+5,cy+5);
                g2.drawLine(cx-5,cy+5,cx+5,cy-5);
        }
    }
} 