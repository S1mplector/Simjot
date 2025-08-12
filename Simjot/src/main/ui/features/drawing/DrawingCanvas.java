package main.ui.features.drawing;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Lightweight single-layer drawing canvas for embedding inside NotePanel.
 */
public class DrawingCanvas extends JPanel {
    public enum Tool { PENCIL, ERASER }

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke = null;

    private Tool currentTool = Tool.PENCIL;
    private Color currentColor = Color.BLACK;
    private int currentSize = 4;

    public DrawingCanvas(){
        setOpaque(false);
        setBackground(Color.WHITE);
        MouseAdapter ma = new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){ startStroke(e.getPoint()); }
            @Override public void mouseDragged(MouseEvent e){ addPoint(e.getPoint()); }
            @Override public void mouseReleased(MouseEvent e){ endStroke(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        setPreferredSize(new Dimension(800,600));
    }

    public void setTool(Tool t){ this.currentTool = t; }
    public void setColor(Color c){ this.currentColor = c; }
    public void setSize(int s){ this.currentSize = s; }

    private void startStroke(Point p){
        currentStroke = new Stroke(currentTool, currentColor, currentSize);
        currentStroke.addPoint(p);
        strokes.add(currentStroke);
        repaint();
    }
    private void addPoint(Point p){ if(currentStroke!=null){ currentStroke.addPoint(p); repaint(); } }
    private void endStroke(){ currentStroke=null; }

    public List<Stroke> getStrokes(){ return strokes; }
    public void clear(){ strokes.clear(); repaint(); }

    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(Stroke s: strokes){ s.draw(g2); }
        g2.dispose();
    }

    // ------------ Stroke inner class ---------------
    public static class Stroke{
        private final Tool tool; private final Color color; private final int thickness; private final List<Point> pts=new ArrayList<>(); private final Path2D path=new Path2D.Float();
        public Stroke(Tool t, Color c, int th){ this.tool=t; this.color=c; this.thickness=th; }
        public void addPoint(Point p){ pts.add(p); if(pts.size()==1) path.moveTo(p.x,p.y); else path.lineTo(p.x,p.y);}        
        public void draw(Graphics2D g2){
            if(tool==Tool.ERASER){ g2.setComposite(AlphaComposite.Clear); g2.setColor(new Color(0,0,0,0)); }
            else{ g2.setComposite(AlphaComposite.SrcOver); g2.setColor(color); }
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
        // --- serialization helpers ---
        public String toLine(){ StringBuilder sb=new StringBuilder(); sb.append(tool).append(" ").append(color.getRGB()).append(" ").append(thickness); for(Point p:pts){ sb.append(" ").append(p.x).append(",").append(p.y);} return sb.toString(); }
        public static Stroke fromLine(String line){ try{
            String[] parts=line.split(" "); Tool t=Tool.valueOf(parts[0]); int rgb=Integer.parseInt(parts[1]); int th=Integer.parseInt(parts[2]); Stroke s=new Stroke(t,new Color(rgb,true),th); for(int i=3;i<parts.length;i++){ String[] xy=parts[i].split(","); int x=Integer.parseInt(xy[0]); int y=Integer.parseInt(xy[1]); s.addPoint(new Point(x,y)); } return s; }catch(Exception ex){ return null; }}
    }
}
