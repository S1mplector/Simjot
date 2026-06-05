/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.font;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A continuous pen stroke consisting of multiple points.
 * Represents a single drawing gesture for glyph creation.
 */
public final class CustomStroke {
    
    private final List<StrokePoint> points;
    private float thickness;
    private Color color;
    
    public static final float DEFAULT_THICKNESS = 2.0f;
    public static final Color DEFAULT_COLOR = Color.BLACK;
    
    public CustomStroke() {
        this(DEFAULT_THICKNESS, DEFAULT_COLOR);
    }
    
    public CustomStroke(float thickness) {
        this(thickness, DEFAULT_COLOR);
    }
    
    public CustomStroke(float thickness, Color color) {
        this.points = new ArrayList<>();
        this.thickness = Math.max(0.5f, thickness);
        this.color = color != null ? color : DEFAULT_COLOR;
    }
    
    public CustomStroke copy() {
        CustomStroke copy = new CustomStroke(thickness, color);
        for (StrokePoint p : points) {
            copy.points.add(p.copy());
        }
        return copy;
    }
    
    public void addPoint(float x, float y) {
        addPoint(x, y, 1.0f, 0.0f);
    }
    
    public void addPoint(float x, float y, float pressure) {
        addPoint(x, y, pressure, 0.0f);
    }
    
    public void addPoint(float x, float y, float pressure, float timestamp) {
        points.add(new StrokePoint(x, y, pressure, timestamp));
    }
    
    public void addPoint(StrokePoint point) {
        if (point != null) {
            points.add(point.copy());
        }
    }
    
    public void clear() {
        points.clear();
    }
    
    public List<StrokePoint> getPoints() {
        return Collections.unmodifiableList(points);
    }
    
    public int getPointCount() {
        return points.size();
    }
    
    public StrokePoint getPoint(int index) {
        return points.get(index);
    }
    
    public StrokePoint getFirstPoint() {
        return points.isEmpty() ? null : points.get(0);
    }
    
    public StrokePoint getLastPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }
    
    public float getThickness() { return thickness; }
    public void setThickness(float thickness) { 
        this.thickness = Math.max(0.5f, thickness); 
    }
    
    public Color getColor() { return color; }
    public void setColor(Color color) { 
        this.color = color != null ? color : DEFAULT_COLOR; 
    }
    
    public float getLength() {
        if (points.size() < 2) return 0.0f;
        float length = 0.0f;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distanceTo(points.get(i));
        }
        return length;
    }
    
    public float[] getBounds() {
        if (points.isEmpty()) return new float[] { 0, 0, 0, 0 };
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        float padding = thickness * 0.5f;
        
        for (StrokePoint p : points) {
            minX = Math.min(minX, p.getX() - padding);
            minY = Math.min(minY, p.getY() - padding);
            maxX = Math.max(maxX, p.getX() + padding);
            maxY = Math.max(maxY, p.getY() + padding);
        }
        
        return new float[] { minX, minY, maxX - minX, maxY - minY };
    }
    
    public void translate(float dx, float dy) {
        for (StrokePoint p : points) {
            p.translate(dx, dy);
        }
    }
    
    public void scale(float sx, float sy, float cx, float cy) {
        for (StrokePoint p : points) {
            p.scale(sx, sy, cx, cy);
        }
        thickness *= Math.max(sx, sy);
    }
    
    public void normalize(float targetSize) {
        if (points.isEmpty()) return;
        
        float[] bounds = getBounds();
        float width = bounds[2];
        float height = bounds[3];
        float size = Math.max(width, height);
        
        if (size < 0.001f) return;
        
        float scale = targetSize / size;
        float cx = bounds[0] + width * 0.5f;
        float cy = bounds[1] + height * 0.5f;
        
        for (StrokePoint p : points) {
            float newX = (p.getX() - cx) * scale + targetSize * 0.5f;
            float newY = (p.getY() - cy) * scale + targetSize * 0.5f;
            p.setX(newX);
            p.setY(newY);
        }
        
        thickness *= scale;
    }
    
    public void smooth(int iterations) {
        if (points.size() < 3) return;
        
        for (int iter = 0; iter < iterations; iter++) {
            List<StrokePoint> smoothed = new ArrayList<>();
            smoothed.add(points.get(0).copy());
            
            for (int i = 1; i < points.size() - 1; i++) {
                StrokePoint prev = points.get(i - 1);
                StrokePoint curr = points.get(i);
                StrokePoint next = points.get(i + 1);
                
                float x = (prev.getX() + curr.getX() * 2 + next.getX()) / 4.0f;
                float y = (prev.getY() + curr.getY() * 2 + next.getY()) / 4.0f;
                float pressure = (prev.getPressure() + curr.getPressure() * 2 + next.getPressure()) / 4.0f;
                
                smoothed.add(new StrokePoint(x, y, pressure, curr.getTimestamp()));
            }
            
            smoothed.add(points.get(points.size() - 1).copy());
            
            points.clear();
            points.addAll(smoothed);
        }
    }
    
    public void resample(float targetDistance) {
        if (points.size() < 2 || targetDistance <= 0) return;
        
        List<StrokePoint> resampled = new ArrayList<>();
        resampled.add(points.get(0).copy());
        
        float accumulated = 0.0f;
        
        for (int i = 1; i < points.size(); i++) {
            StrokePoint prev = points.get(i - 1);
            StrokePoint curr = points.get(i);
            float dist = prev.distanceTo(curr);
            
            if (dist < 0.0001f) continue;
            
            while (accumulated + dist >= targetDistance) {
                float t = (targetDistance - accumulated) / dist;
                resampled.add(prev.lerp(curr, t));
                accumulated = 0.0f;
                dist -= targetDistance - accumulated;
            }
            
            accumulated += dist;
        }
        
        if (resampled.isEmpty() || resampled.get(resampled.size() - 1).distanceTo(points.get(points.size() - 1)) > 0.1f) {
            resampled.add(points.get(points.size() - 1).copy());
        }
        
        points.clear();
        points.addAll(resampled);
    }
    
    public void simplify(float epsilon) {
        if (points.size() < 3 || epsilon <= 0) return;
        
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        
        douglasPeuckerRecursive(0, points.size() - 1, epsilon, keep);
        
        List<StrokePoint> simplified = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                simplified.add(points.get(i));
            }
        }
        
        points.clear();
        points.addAll(simplified);
    }
    
    private void douglasPeuckerRecursive(int start, int end, float epsilon, boolean[] keep) {
        if (end <= start + 1) return;
        
        float maxDist = 0.0f;
        int maxIdx = start;
        
        StrokePoint startPt = points.get(start);
        StrokePoint endPt = points.get(end);
        
        for (int i = start + 1; i < end; i++) {
            float dist = perpendicularDistance(points.get(i), startPt, endPt);
            if (dist > maxDist) {
                maxDist = dist;
                maxIdx = i;
            }
        }
        
        if (maxDist > epsilon) {
            keep[maxIdx] = true;
            douglasPeuckerRecursive(start, maxIdx, epsilon, keep);
            douglasPeuckerRecursive(maxIdx, end, epsilon, keep);
        }
    }
    
    private float perpendicularDistance(StrokePoint point, StrokePoint lineStart, StrokePoint lineEnd) {
        float dx = lineEnd.getX() - lineStart.getX();
        float dy = lineEnd.getY() - lineStart.getY();
        float mag = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (mag < 0.0001f) {
            return point.distanceTo(lineStart);
        }
        
        float u = ((point.getX() - lineStart.getX()) * dx + (point.getY() - lineStart.getY()) * dy) / (mag * mag);
        u = Math.max(0.0f, Math.min(1.0f, u));
        
        float projX = lineStart.getX() + u * dx;
        float projY = lineStart.getY() + u * dy;
        
        return point.distanceTo(projX, projY);
    }
    
    @Override
    public String toString() {
        return String.format("CustomStroke(%d points, thickness=%.1f)", points.size(), thickness);
    }
}
