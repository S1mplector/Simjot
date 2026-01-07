/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.buttons;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

import javax.swing.JButton;
import javax.swing.Timer;

import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

/*
 * This class is used to create toolbar icons
 * It extends JButton and uses the Aero theme to create a button with a gradient background
 * It also has a glow effect that can be enabled/disabled
 */

public class ToolbarIconButton extends JButton {
    private final String id;
    private final String resourcePath; // centralized icon path (may be null)
    private boolean selected;
    private boolean glow;
    private Timer glowTimer;
    private float glowPhase=0f;
    private float iconOpacity = 1f; // 0..1 alpha multiplier for icon only

    private static boolean globalGlow = false;
    private static final java.util.List<ToolbarIconButton> INSTANCES = new java.util.ArrayList<>();

    public ToolbarIconButton(String iconId){
        this.id = iconId.toLowerCase();
        setPreferredSize(new Dimension(46,46));
        setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);

        // Centralized mapping for icons; fallback to legacy img/{id}.png
        String mapped = ImageIconRenderer.mapIdToResource(this.id);
        this.resourcePath = (mapped != null) ? mapped : ("img/" + id + ".png");

        INSTANCES.add(this);
        // Lazy-load glow preference only after settings are available
        ensureGlowInitialized();
        setGlow(globalGlow);
    }

    public void setSelected(boolean s){ this.selected=s; repaint(); }

    /** Adjust only the icon opacity (0..1), leaving button chrome unchanged. */
    public void setIconOpacity(float alpha){
        float a = Math.max(0f, Math.min(1f, alpha));
        if (this.iconOpacity != a) { this.iconOpacity = a; repaint(); }
    }

    /** Enable or disable fancy glow animation */
    public void setGlow(boolean g){
        if(this.glow==g) return;
        this.glow=g;
        if(glow){
            glowTimer = new Timer(80, e->{ glowPhase+=0.066f; repaint(); }); // 80ms tick, similar perceived speed
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

    private static void ensureGlowInitialized() {
        // Avoid early SettingsStore access before AppDirectories is ready.
        if (globalGlow) return;
        try {
            globalGlow = main.core.service.SettingsStore.get().isGlowEnabled();
        } catch (Throwable ignored) {
            // keep default false until settings become available
        }
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();
        // Selected gives a light blue tint similar to Windows 7 selection
        if (Theme.isPlainWhite()) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
            g2.setColor(new Color(180,180,180));
            g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
        } else {
            Color top = pressed ? AeroTheme.BUTTON_PRESS_TOP : (hover || selected ? AeroTheme.BUTTON_HOVER_TOP : AeroTheme.BUTTON_BG_TOP);
            Color bottom = pressed ? AeroTheme.BUTTON_PRESS_BOTTOM : (hover || selected ? AeroTheme.BUTTON_HOVER_BOTTOM : AeroTheme.BUTTON_BG_BOTTOM);
            Rectangle r = new Rectangle(0,0,getWidth(),getHeight());
            AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
            AeroPainters.paintGlassOverlay(g2, r, 10);
            g2.setColor(new Color(180,180,180));
            g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
        }

        // Draw icon via centralized renderer; fallback to vector
        // Force vector icons for drawing tools (pen, highlighter, eraser, lasso)
        boolean useVectorOnly = isVectorOnlyIcon(id);
        boolean painted = false;
        int size = Math.min(getWidth(), getHeight()) - 8;
        int ix = (getWidth()-size)/2;
        int iy = (getHeight()-size)/2;
        if (!useVectorOnly && resourcePath != null) {
            Composite old = g2.getComposite();
            if (iconOpacity < 0.999f) g2.setComposite(AlphaComposite.SrcOver.derive(iconOpacity));
            painted = ImageIconRenderer.draw(g2, resourcePath, ix, iy, size, this, true);
            if (iconOpacity < 0.999f) g2.setComposite(old);
        }
        if(!painted){
            Composite old = g2.getComposite();
            if (iconOpacity < 0.999f) g2.setComposite(AlphaComposite.SrcOver.derive(iconOpacity));
            drawVector(g2);
            if (iconOpacity < 0.999f) g2.setComposite(old);
        }

        // Optional glow retained but softened
        if(glow && !Theme.isPlainWhite()){
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

    // Check if this icon should always use vector rendering (drawing tools)
    private static boolean isVectorOnlyIcon(String iconId) {
        return switch (iconId) {
            case "pen_tool", "highlighter_tool", "eraser_tool", "lasso_tool", "select_text" -> true;
            default -> false;
        };
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
            case "options": {
                // Windows 7-style glossy gear icon
                int size = Math.min(w, h) - 12;
                int gearR = size/2; // gear outer radius
                int toothCount = 8;
                int toothDepth = Math.max(3, size/6);
                int innerR = Math.max(4, gearR - toothDepth);

                // Drop shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(cx, cy+gearR/2), gearR,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(cx-gearR, cy-gearR/2, gearR*2, gearR);
                sh.dispose();

                // Build gear shape with teeth
                java.awt.geom.Area gear = new java.awt.geom.Area();
                for(int i=0;i<toothCount;i++){
                    double a0 = i * (Math.PI*2/toothCount);
                    double a1 = a0 + (Math.PI*2/toothCount)/2.5;
                    int rOuter = gearR;
                    int rInner = innerR;
                    int xA = cx + (int)(rInner * Math.cos(a0));
                    int yA = cy + (int)(rInner * Math.sin(a0));
                    int xB = cx + (int)(rOuter * Math.cos(a0));
                    int yB = cy + (int)(rOuter * Math.sin(a0));
                    int xC = cx + (int)(rOuter * Math.cos(a1));
                    int yC = cy + (int)(rOuter * Math.sin(a1));
                    int xD = cx + (int)(rInner * Math.cos(a1));
                    int yD = cy + (int)(rInner * Math.sin(a1));
                    java.awt.Polygon tooth = new java.awt.Polygon(new int[]{xA,xB,xC,xD}, new int[]{yA,yB,yC,yD}, 4);
                    gear.add(new java.awt.geom.Area(tooth));
                }
                // Add inner hub circle to close the shape ring
                java.awt.geom.Ellipse2D inner = new java.awt.geom.Ellipse2D.Double(cx-innerR, cy-innerR, innerR*2, innerR*2);
                gear.add(new java.awt.geom.Area(inner));

                // Metallic gradient fill
                LinearGradientPaint metal = new LinearGradientPaint(cx, cy-gearR, cx, cy+gearR,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(240,240,240), new Color(210,210,210), new Color(190,190,190)});
                g2.setPaint(metal);
                g2.fill(gear);

                // Top glass highlight
                g2.setPaint(new GradientPaint(0, cy-gearR, new Color(255,255,255,150), 0, cy, new Color(255,255,255,0)));
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(cx-gearR+1, cy-gearR+1, (gearR*2)-2, gearR, 8, 8));

                // Outer border
                g2.setColor(new Color(140,140,140));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(gear);

                // Inner hole
                int holeR = Math.max(3, gearR/4);
                java.awt.geom.Ellipse2D hole = new java.awt.geom.Ellipse2D.Double(cx-holeR, cy-holeR, holeR*2, holeR*2);
                g2.setPaint(new LinearGradientPaint(cx, cy-holeR, cx, cy+holeR,
                        new float[]{0f,1f}, new Color[]{new Color(220,220,220), new Color(200,200,200)}));
                g2.fill(hole);
                g2.setColor(new Color(130,130,130));
                g2.draw(hole);
                break; }
            case "delete": {
                // Use ImageIconRenderer for delete icon
                int size = Math.min(w, h) - 8;
                String resPath = ImageIconRenderer.mapIdToResource("delete");
                if (resPath != null) {
                    ImageIconRenderer.draw(g2, resPath, cx - size/2, cy - size/2, size, this, true);
                }
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
            
            case "lasso_tool": {
                // Lasso selection tool - dashed loop with cursor
                int size = Math.min(w, h) - 14;
                int x = cx - size/2, y = cy - size/2;
                
                // Draw lasso loop (dashed ellipse)
                g2.setColor(new Color(70, 130, 180)); // Steel blue
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{4, 3}, 0));
                
                // Irregular lasso shape (not a perfect ellipse)
                java.awt.geom.Path2D lasso = new java.awt.geom.Path2D.Float();
                lasso.moveTo(x + size*0.2, y + size*0.3);
                lasso.curveTo(x + size*0.1, y + size*0.6, x + size*0.2, y + size*0.9, x + size*0.5, y + size*0.85);
                lasso.curveTo(x + size*0.8, y + size*0.8, x + size*0.95, y + size*0.5, x + size*0.8, y + size*0.25);
                lasso.curveTo(x + size*0.6, y + size*0.05, x + size*0.3, y + size*0.1, x + size*0.2, y + size*0.3);
                g2.draw(lasso);
                
                // Small cursor/handle at the end
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(50, 100, 150));
                int hx = x + (int)(size*0.2), hy = y + (int)(size*0.3);
                g2.fillOval(hx - 3, hy - 3, 6, 6);
                g2.setColor(new Color(70, 130, 180));
                g2.drawOval(hx - 3, hy - 3, 6, 6);
                
                // Inner fill hint (very subtle)
                g2.setColor(new Color(70, 130, 180, 25));
                g2.fill(lasso);
                break; }
            
            case "pen_tool": {
                // Pen/pencil icon for drawing
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-Math.PI/4);
                
                // Pen body
                LinearGradientPaint body = new LinearGradientPaint(-12, -3, -12, 3,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(80, 80, 80), new Color(50, 50, 50), new Color(30, 30, 30)});
                g2.setPaint(body);
                g2.fillRoundRect(-12, -3, 16, 6, 2, 2);
                
                // Pen tip
                int[] px = { -12, -16, -12 };
                int[] py = { -3, 0, 3 };
                g2.setColor(new Color(180, 180, 180));
                g2.fillPolygon(px, py, 3);
                g2.setColor(new Color(30, 30, 30));
                g2.fillOval(-17, -1, 3, 2);
                
                // Grip section
                g2.setColor(new Color(100, 100, 100));
                g2.fillRect(4, -3, 4, 6);
                
                // Outline
                g2.setColor(new Color(60, 60, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(-12, -3, 16, 6, 2, 2);
                g2.drawPolygon(px, py, 3);
                
                g2.setTransform(old);
                break; }
            
            case "highlighter_tool": {
                // Highlighter marker icon
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-Math.PI/4);
                
                // Marker body (yellow)
                LinearGradientPaint body = new LinearGradientPaint(-10, -4, -10, 4,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 240, 100), new Color(255, 220, 50), new Color(240, 200, 30)});
                g2.setPaint(body);
                g2.fillRoundRect(-10, -4, 18, 8, 3, 3);
                
                // Chisel tip
                g2.setColor(new Color(255, 235, 59, 200));
                g2.fillRect(-14, -3, 4, 6);
                
                // Cap
                g2.setColor(new Color(80, 80, 80));
                g2.fillRoundRect(8, -4, 6, 8, 2, 2);
                
                // Outline
                g2.setColor(new Color(180, 160, 30));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(-10, -4, 18, 8, 3, 3);
                
                g2.setTransform(old);
                break; }
            
            case "eraser_tool": {
                // Eraser icon
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(-Math.PI/6);
                
                // Eraser body
                LinearGradientPaint body = new LinearGradientPaint(-10, -6, -10, 6,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 180, 180), new Color(255, 140, 140), new Color(240, 120, 120)});
                g2.setPaint(body);
                g2.fillRoundRect(-10, -6, 20, 12, 4, 4);
                
                // Outline
                g2.setColor(new Color(180, 100, 100));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(-10, -6, 20, 12, 4, 4);
                
                g2.setTransform(old);
                break; }
            
            case "select_text": {
                // Text cursor / I-beam
                g2.setColor(new Color(60, 60, 60));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Vertical bar
                g2.drawLine(cx, cy - 10, cx, cy + 10);
                // Top serif
                g2.drawLine(cx - 5, cy - 10, cx + 5, cy - 10);
                // Bottom serif
                g2.drawLine(cx - 5, cy + 10, cx + 5, cy + 10);
                break; }
            
            default:
                g2.setColor(Color.DARK_GRAY);
                g2.drawLine(cx-5,cy-5,cx+5,cy+5);
                g2.drawLine(cx-5,cy+5,cx+5,cy-5);
        }
    }
} 
