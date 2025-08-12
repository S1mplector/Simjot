package main.ui.features.drawing;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Transparent overlay that captures pen/eraser strokes when ink mode is enabled.
 * It should be placed directly above the JTextPane inside the same viewport so it scrolls together.
 */
public class InkOverlay extends JComponent {

    public enum Tool { PENCIL, HIGHLIGHTER, ERASER }

    private boolean inkMode = false;
    private Tool currentTool = Tool.PENCIL;
    private Color currentColor = Color.BLACK;
    private int currentSize = 3;

    private final List<Stroke> strokes = new ArrayList<>();
    private final List<Stroke> redoStack = new ArrayList<>();
    private Stroke active = null;

    public InkOverlay(){
        setOpaque(false);

        // Keyboard shortcuts for undo/redo
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK), "undoInk");
        getActionMap().put("undoInk", new AbstractAction(){ public void actionPerformed(ActionEvent e){ undo(); }});

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "redoInk");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK), "redoInk");
        getActionMap().put("redoInk", new AbstractAction(){ public void actionPerformed(ActionEvent e){ redo(); }});

        MouseAdapter ma = new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){ if(!inkMode) return; beginStroke(e.getPoint()); }
            @Override public void mouseDragged(MouseEvent e){ if(!inkMode) return; addPoint(e.getPoint()); }
            @Override public void mouseReleased(MouseEvent e){ if(!inkMode) return; endStroke(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // -------------------- public controls -------------------
    public void setInkMode(boolean on){ this.inkMode = on; repaint(); }
    public boolean isInkMode(){ return inkMode; }

    public void setTool(Tool t){ this.currentTool = t; }
    public void setColor(Color c){ this.currentColor = c; }
    public void setSize(int s){ this.currentSize = s; }

    public List<Stroke> getStrokes(){ return strokes; }
    public void clear(){ strokes.clear(); repaint(); }

    // -------------------- drawing ---------------------------
    private void beginStroke(Point p){
        active = new Stroke(currentTool, currentColor, currentSize);
        active.addPoint(p);
        strokes.add(active);
        repaint();
    }
    private void addPoint(Point p){ if(active!=null){ active.addPoint(p); repaint(); } }
    private void endStroke(){ active = null; }

    @Override protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for(Stroke s: strokes){ s.draw(g2); }
        g2.dispose();
    }

    // -------------------- Stroke ----------------------------
    public static class Stroke{
        private final Tool tool; private final Color color; private final int thickness; private final List<Point> pts = new ArrayList<>(); private final Path2D path = new Path2D.Float();
        public Stroke(Tool t, Color c, int th){ this.tool=t; this.color=c; this.thickness=th; }
        public void addPoint(Point p){ pts.add(p); if(pts.size()==1) path.moveTo(p.x,p.y); else path.lineTo(p.x,p.y);}        
        public void draw(Graphics2D g2){
            g2.setColor(color);
            if(tool==Tool.ERASER){ g2.setComposite(AlphaComposite.Clear); g2.setColor(new Color(0,0,0,0)); }
            else if(tool==Tool.HIGHLIGHTER){ g2.setComposite(AlphaComposite.SrcOver); Color hi = new Color(color.getRed(),color.getGreen(),color.getBlue(),90); g2.setColor(hi);} 
            else { g2.setComposite(AlphaComposite.SrcOver); g2.setColor(color); }
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
        // ---------- serialization ----------
        public String toLine(){ StringBuilder sb=new StringBuilder(); sb.append(tool).append(" ").append(color.getRGB()).append(" ").append(thickness); for(Point p:pts){ sb.append(" ").append(p.x).append(",").append(p.y);} return sb.toString(); }
        public static Stroke fromLine(String l){ try{
            String[] parts = l.split(" "); Tool t=Tool.valueOf(parts[0]); int rgb=Integer.parseInt(parts[1]); int th=Integer.parseInt(parts[2]); Stroke s=new Stroke(t,new Color(rgb,true),th); for(int i=3;i<parts.length;i++){ String[] xy=parts[i].split(","); s.addPoint(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]))); } return s; }catch(Exception ex){ return null; }}
    }

    // Prevent overlay from blocking mouse events when ink mode is off
    @Override public boolean contains(int x,int y){ return inkMode && super.contains(x,y); }

    // -------------------- undo / redo --------------------
    public void undo(){ if(!strokes.isEmpty()){ Stroke s = strokes.remove(strokes.size()-1); redoStack.add(s); repaint(); } }
    public void redo(){ if(!redoStack.isEmpty()){ Stroke s = redoStack.remove(redoStack.size()-1); strokes.add(s); repaint(); } }
} 