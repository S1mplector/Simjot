/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.drawing;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A single continuous stroke made with a tool, color, and thickness.
 * Uses Path2D for smooth, scalable lines.
 */
public class Stroke {
    private final Tool tool;
    private final Color color;
    private final int thickness;
    private final Path2D path;
    private final List<Point> points; // store the discrete points for saving

    public Stroke(Tool tool, Color color, int thickness) {
        this.tool = tool;
        this.color = color;
        this.thickness = thickness;
        this.path = new Path2D.Float();
        this.points = new ArrayList<>();
    }

    public void addPoint(int x, int y) {
        if (points.isEmpty()) {
            path.moveTo(x, y);
        } else {
            path.lineTo(x, y);
        }
        points.add(new Point(x, y));
    }

    public List<Point> getPointList() {
        return points;
    }

    public void draw(Graphics2D g2) {
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (tool == Tool.ERASER) {
            g2.setComposite(AlphaComposite.Clear);
        } else {
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(color);
        }
        g2.draw(path);
    }
} 
