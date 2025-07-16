package mindmap;

import java.awt.Color;
import java.awt.Point;
import java.io.Serializable;

public class MindMapNode implements Serializable {
    private static final long serialVersionUID = 1L;

    public int x, y, radius;
    public Color color;
    public String text;

    public MindMapNode(int x, int y, int radius, Color color, String text) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.color = color;
        this.text = text;
    }

    public boolean contains(Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        return dx * dx + dy * dy <= radius * radius;
    }
}
