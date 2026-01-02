/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.font;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import main.core.font.CustomGlyph;
import main.core.font.CustomStroke;
import main.core.font.StrokePoint;
import main.infrastructure.input.TabletInputSupport;

/**
 * Canvas for drawing glyph strokes with mouse/stylus input.
 * Supports pressure sensitivity and real-time preview.
 */
public class GlyphCanvas extends JPanel {
    
    private static final Color GRID_COLOR = new Color(230, 230, 235);
    private static final Color BASELINE_COLOR = new Color(180, 180, 220);
    private static final Color STROKE_COLOR = new Color(30, 30, 30);
    private static final Color PREVIEW_COLOR = new Color(100, 100, 100, 180);
    
    private CustomGlyph currentGlyph;
    private CustomStroke currentStroke;
    private float strokeThickness = 3.0f;
    private float emSize = 1000.0f;
    
    private boolean showGrid = true;
    private boolean showBaselines = true;
    private boolean smoothing = true;
    private int smoothIterations = 2;
    
    private final List<GlyphCanvasListener> listeners = new ArrayList<>();
    
    private long strokeStartTime;
    private int lastScreenX;
    private int lastScreenY;
    private float lastTimestamp;
    private boolean hasLastScreen;
    
    /* Tablet input state */
    private boolean tabletInitialized;
    private boolean useTabletPressure = true;
    private float pressureGamma = 1.0f;
    private float minPressure = 0.05f;
    
    public interface GlyphCanvasListener {
        void onStrokeStarted();
        void onStrokeEnded(CustomStroke stroke);
        void onGlyphModified(CustomGlyph glyph);
    }
    
    public GlyphCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 400));
        setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startStroke(e.getX(), e.getY(), getPressure(e));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                endStroke();
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                addPoint(e.getX(), e.getY(), getPressure(e));
            }
        };
        
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }
    
    private float getPressure(MouseEvent e) {
        if (!useTabletPressure) return 1.0f;
        
        // Initialize tablet support on first use
        if (!tabletInitialized) {
            tabletInitialized = true;
            try {
                TabletInputSupport.initialize();
                TabletInputSupport.setPressureGamma(pressureGamma);
            } catch (Throwable t) {
                // Tablet not available, use mouse fallback
            }
        }
        
        // Get pressure from tablet if available
        if (TabletInputSupport.isAvailable()) {
            float pressure = TabletInputSupport.getPressure();
            // Clamp to minimum pressure for visibility
            return Math.max(minPressure, pressure);
        }
        
        // Default to full pressure for mouse
        return 1.0f;
    }
    
    public void addListener(GlyphCanvasListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(GlyphCanvasListener listener) {
        listeners.remove(listener);
    }
    
    public void setGlyph(CustomGlyph glyph) {
        this.currentGlyph = glyph;
        repaint();
    }
    
    public CustomGlyph getGlyph() {
        return currentGlyph;
    }
    
    public void setStrokeThickness(float thickness) {
        this.strokeThickness = Math.max(0.5f, thickness);
    }
    
    public float getStrokeThickness() {
        return strokeThickness;
    }
    
    public void setEmSize(float emSize) {
        this.emSize = emSize;
    }
    
    public void setShowGrid(boolean show) {
        this.showGrid = show;
        repaint();
    }
    
    public void setShowBaselines(boolean show) {
        this.showBaselines = show;
        repaint();
    }
    
    public void setSmoothing(boolean enabled) {
        this.smoothing = enabled;
    }
    
    public void setSmoothIterations(int iterations) {
        this.smoothIterations = Math.max(0, Math.min(5, iterations));
    }
    
    /**
     * Enable or disable tablet pressure sensitivity.
     */
    public void setUseTabletPressure(boolean use) {
        this.useTabletPressure = use;
    }
    
    /**
     * Check if tablet pressure is enabled.
     */
    public boolean isUseTabletPressure() {
        return useTabletPressure;
    }
    
    /**
     * Set the pressure gamma curve.
     * @param gamma 1.0 = linear, <1.0 = softer start, >1.0 = harder start
     */
    public void setPressureGamma(float gamma) {
        this.pressureGamma = Math.max(0.1f, Math.min(5.0f, gamma));
        if (tabletInitialized) {
            TabletInputSupport.setPressureGamma(this.pressureGamma);
        }
    }
    
    /**
     * Set minimum pressure threshold for visibility.
     */
    public void setMinPressure(float min) {
        this.minPressure = Math.max(0.0f, Math.min(1.0f, min));
    }
    
    /**
     * Check if tablet input is available.
     */
    public boolean isTabletAvailable() {
        if (!tabletInitialized) {
            tabletInitialized = true;
            try {
                TabletInputSupport.initialize();
            } catch (Throwable t) {
                return false;
            }
        }
        return TabletInputSupport.isAvailable();
    }
    
    private void startStroke(int x, int y, float pressure) {
        // Reset pressure smoothing for new stroke
        if (tabletInitialized && TabletInputSupport.isAvailable()) {
            TabletInputSupport.resetPressureSmoothing();
        }
        
        // Reset spline history
        historyCount = 0;
        
        currentStroke = new CustomStroke(strokeThickness);
        strokeStartTime = System.currentTimeMillis();
        lastScreenX = x;
        lastScreenY = y;
        lastTimestamp = 0.0f;
        hasLastScreen = true;
        
        float[] coords = screenToGlyph(x, y);
        currentStroke.addPoint(coords[0], coords[1], pressure, 0);
        
        for (GlyphCanvasListener l : listeners) {
            l.onStrokeStarted();
        }
        
        repaint();
    }
    
    private void addPoint(int x, int y, float pressure) {
        if (currentStroke == null) return;
        addPointWithResample(x, y, pressure);
        
        repaint();
    }
    
    private void endStroke() {
        if (currentStroke == null || currentStroke.getPointCount() < 2) {
            currentStroke = null;
            hasLastScreen = false;
            return;
        }
        
        // Apply smoothing with adaptive resampling to avoid thick-stroke distortion
        if (smoothing && smoothIterations > 0) {
            smoothStroke(currentStroke);
        }
        
        // Add to glyph
        if (currentGlyph != null) {
            currentGlyph.addStroke(currentStroke);
            
            for (GlyphCanvasListener l : listeners) {
                l.onGlyphModified(currentGlyph);
            }
        }
        
        CustomStroke completedStroke = currentStroke;
        currentStroke = null;
        hasLastScreen = false;
        
        for (GlyphCanvasListener l : listeners) {
            l.onStrokeEnded(completedStroke);
        }
        
        repaint();
    }
    
    public void clearCurrentStroke() {
        currentStroke = null;
        repaint();
    }
    
    public void clearGlyph() {
        if (currentGlyph != null) {
            currentGlyph.clearStrokes();
            
            for (GlyphCanvasListener l : listeners) {
                l.onGlyphModified(currentGlyph);
            }
        }
        repaint();
    }
    
    public void undoLastStroke() {
        if (currentGlyph != null && currentGlyph.getStrokeCount() > 0) {
            currentGlyph.removeStroke(currentGlyph.getStrokeCount() - 1);
            
            for (GlyphCanvasListener l : listeners) {
                l.onGlyphModified(currentGlyph);
            }
            
            repaint();
        }
    }
    
    private float[] screenToGlyph(int x, int y) {
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h) - 40;
        int offsetX = (w - size) / 2;
        int offsetY = (h - size) / 2;
        
        float gx = (x - offsetX) * emSize / size;
        float gy = (y - offsetY) * emSize / size;
        
        return new float[] { gx, gy };
    }
    
    private float[] glyphToScreen(float gx, float gy) {
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h) - 40;
        int offsetX = (w - size) / 2;
        int offsetY = (h - size) / 2;
        
        float sx = gx * size / emSize + offsetX;
        float sy = gy * size / emSize + offsetY;
        
        return new float[] { sx, sy };
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h) - 40;
        int offsetX = (w - size) / 2;
        int offsetY = (h - size) / 2;
        
        // Draw em square
        g2.setColor(new Color(250, 250, 252));
        g2.fillRect(offsetX, offsetY, size, size);
        g2.setColor(new Color(180, 180, 190));
        g2.drawRect(offsetX, offsetY, size, size);
        
        // Draw grid
        if (showGrid) {
            drawGrid(g2, offsetX, offsetY, size);
        }
        
        // Draw baselines
        if (showBaselines) {
            drawBaselines(g2, offsetX, offsetY, size);
        }
        
        // Draw existing strokes
        if (currentGlyph != null) {
            g2.setColor(STROKE_COLOR);
            for (CustomStroke stroke : currentGlyph.getStrokes()) {
                drawStroke(g2, stroke, offsetX, offsetY, size);
            }
        }
        
        // Draw current stroke (preview)
        if (currentStroke != null && currentStroke.getPointCount() > 0) {
            g2.setColor(PREVIEW_COLOR);
            drawStroke(g2, currentStroke, offsetX, offsetY, size);
        }
        
        g2.dispose();
    }
    
    private void drawGrid(Graphics2D g2, int offsetX, int offsetY, int size) {
        g2.setColor(GRID_COLOR);
        g2.setStroke(new BasicStroke(0.5f));
        
        int divisions = 10;
        float step = (float) size / divisions;
        
        for (int i = 1; i < divisions; i++) {
            int pos = Math.round(i * step);
            g2.drawLine(offsetX + pos, offsetY, offsetX + pos, offsetY + size);
            g2.drawLine(offsetX, offsetY + pos, offsetX + size, offsetY + pos);
        }
    }
    
    private void drawBaselines(Graphics2D g2, int offsetX, int offsetY, int size) {
        g2.setColor(BASELINE_COLOR);
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
                                      10.0f, new float[] { 5.0f, 3.0f }, 0.0f));
        
        // Baseline at 80% (ascender line at 20%, descender at 100%)
        int baseline = offsetY + (int)(size * 0.8f);
        g2.drawLine(offsetX, baseline, offsetX + size, baseline);
        
        // X-height line (approximately 50%)
        int xHeight = offsetY + (int)(size * 0.5f);
        g2.setColor(new Color(200, 180, 180));
        g2.drawLine(offsetX, xHeight, offsetX + size, xHeight);
        
        // Cap height line (approximately 20%)
        int capHeight = offsetY + (int)(size * 0.2f);
        g2.setColor(new Color(180, 200, 180));
        g2.drawLine(offsetX, capHeight, offsetX + size, capHeight);
    }
    
    private void drawStroke(Graphics2D g2, CustomStroke stroke, int offsetX, int offsetY, int size) {
        List<StrokePoint> points = stroke.getPoints();
        if (points.size() < 2) {
            if (points.size() == 1) {
                StrokePoint p = points.get(0);
                float[] screen = glyphToScreen(p.getX(), p.getY());
                float r = stroke.getThickness() * p.getPressure() * size / emSize * 0.5f;
                g2.fill(new Ellipse2D.Float(screen[0] - r, screen[1] - r, r * 2, r * 2));
            }
            return;
        }
        
        float baseThickness = stroke.getThickness() * size / emSize;
        float minP = 1.0f;
        float maxP = 0.0f;
        for (StrokePoint p : points) {
            float pr = p.getPressure();
            minP = Math.min(minP, pr);
            maxP = Math.max(maxP, pr);
        }
        if (maxP - minP < 0.05f) {
            float avgP = (minP + maxP) * 0.5f;
            Path2D.Float path = new Path2D.Float();
            StrokePoint first = points.get(0);
            float[] s0 = glyphToScreen(first.getX(), first.getY());
            path.moveTo(s0[0], s0[1]);
            for (int i = 1; i < points.size(); i++) {
                StrokePoint p = points.get(i);
                float[] s = glyphToScreen(p.getX(), p.getY());
                path.lineTo(s[0], s[1]);
            }
            java.awt.Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(Math.max(1.0f, baseThickness * avgP),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
            g2.setStroke(old);
            return;
        }
        
        for (int i = 0; i < points.size() - 1; i++) {
            StrokePoint p0 = points.get(i);
            StrokePoint p1 = points.get(i + 1);
            
            float[] s0 = glyphToScreen(p0.getX(), p0.getY());
            float[] s1 = glyphToScreen(p1.getX(), p1.getY());
            
            float thick0 = baseThickness * p0.getPressure();
            float thick1 = baseThickness * p1.getPressure();
            
            drawVariableWidthSegment(g2, s0[0], s0[1], s1[0], s1[1], thick0, thick1);
        }
        for (StrokePoint p : points) {
            float[] s = glyphToScreen(p.getX(), p.getY());
            float r = baseThickness * p.getPressure() * 0.5f;
            g2.fill(new Ellipse2D.Float(s[0] - r, s[1] - r, r * 2, r * 2));
        }
    }
    
    private void drawVariableWidthSegment(Graphics2D g2, float x0, float y0, float x1, float y1,
                                           float thick0, float thick1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (len < 0.001f) return;
        
        float nx = -dy / len;
        float ny = dx / len;
        
        float r0 = thick0 * 0.5f;
        float r1 = thick1 * 0.5f;
        
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x0 + nx * r0, y0 + ny * r0);
        path.lineTo(x1 + nx * r1, y1 + ny * r1);
        path.lineTo(x1 - nx * r1, y1 - ny * r1);
        path.lineTo(x0 - nx * r0, y0 - ny * r0);
        path.closePath();
        
        g2.fill(path);
    }

    private void smoothStroke(CustomStroke stroke) {
        if (stroke == null || stroke.getPointCount() < 3) return;
        float thickness = stroke.getThickness();
        float targetDist = Math.max(2.5f, 10.0f - thickness * 0.2f);
        stroke.resample(targetDist);
        int iterations = smoothIterations;
        if (thickness >= 12f) iterations = Math.max(1, smoothIterations - 1);
        if (thickness >= 24f) iterations = 1;
        if (thickness >= 32f) iterations = 0;
        if (iterations > 0) {
            stroke.smooth(iterations);
        }
    }

    /* Ring buffer for Catmull-Rom spline interpolation */
    private final float[] historyX = new float[4];
    private final float[] historyY = new float[4];
    private final float[] historyP = new float[4];
    private final float[] historyT = new float[4];
    private int historyCount = 0;
    
    private void addPointWithResample(int x, int y, float pressure) {
        float timestamp = (System.currentTimeMillis() - strokeStartTime);
        
        if (!hasLastScreen) {
            float[] coords = screenToGlyph(x, y);
            currentStroke.addPoint(coords[0], coords[1], pressure, timestamp);
            lastScreenX = x;
            lastScreenY = y;
            lastTimestamp = timestamp;
            hasLastScreen = true;
            
            // Initialize history for spline
            historyCount = 1;
            historyX[0] = x;
            historyY[0] = y;
            historyP[0] = pressure;
            historyT[0] = timestamp;
            return;
        }

        // Add to history ring buffer
        if (historyCount < 4) {
            historyX[historyCount] = x;
            historyY[historyCount] = y;
            historyP[historyCount] = pressure;
            historyT[historyCount] = timestamp;
            historyCount++;
        } else {
            // Shift history
            historyX[0] = historyX[1]; historyX[1] = historyX[2]; historyX[2] = historyX[3]; historyX[3] = x;
            historyY[0] = historyY[1]; historyY[1] = historyY[2]; historyY[2] = historyY[3]; historyY[3] = y;
            historyP[0] = historyP[1]; historyP[1] = historyP[2]; historyP[2] = historyP[3]; historyP[3] = pressure;
            historyT[0] = historyT[1]; historyT[1] = historyT[2]; historyT[2] = historyT[3]; historyT[3] = timestamp;
        }

        float dx = x - lastScreenX;
        float dy = y - lastScreenY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxStep = maxScreenStep();

        if (dist <= maxStep) {
            float[] coords = screenToGlyph(x, y);
            currentStroke.addPoint(coords[0], coords[1], pressure, timestamp);
        } else if (historyCount >= 4) {
            // Use Catmull-Rom spline for smooth interpolation
            int segments = (int) Math.ceil(dist / maxStep);
            for (int i = 1; i <= segments; i++) {
                float t = i / (float) segments;
                float ix = catmullRom(historyX[0], historyX[1], historyX[2], historyX[3], t);
                float iy = catmullRom(historyY[0], historyY[1], historyY[2], historyY[3], t);
                float ip = catmullRom(historyP[0], historyP[1], historyP[2], historyP[3], t);
                float it = lastTimestamp + (timestamp - lastTimestamp) * t;
                ip = Math.max(0.0f, Math.min(1.0f, ip)); // Clamp pressure
                float[] coords = screenToGlyph(Math.round(ix), Math.round(iy));
                currentStroke.addPoint(coords[0], coords[1], ip, it);
            }
        } else {
            // Linear fallback when not enough history
            int segments = (int) Math.ceil(dist / maxStep);
            for (int i = 1; i <= segments; i++) {
                float t = i / (float) segments;
                float ix = lastScreenX + dx * t;
                float iy = lastScreenY + dy * t;
                float it = lastTimestamp + (timestamp - lastTimestamp) * t;
                float[] coords = screenToGlyph(Math.round(ix), Math.round(iy));
                currentStroke.addPoint(coords[0], coords[1], pressure, it);
            }
        }

        lastScreenX = x;
        lastScreenY = y;
        lastTimestamp = timestamp;
    }
    
    /* Catmull-Rom spline interpolation for smooth curves */
    private float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
            (2 * p1) +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
        );
    }

    private float maxScreenStep() {
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h) - 40;
        if (size <= 0) return 6.0f;
        float thicknessPx = strokeThickness * size / emSize;
        return Math.max(2.0f, 6.0f - thicknessPx * 0.2f);
    }
}
