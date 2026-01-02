/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single glyph (character) in a custom font, composed of strokes.
 */
public final class CustomGlyph {
    
    private final int codepoint;
    private final List<CustomStroke> strokes;
    private boolean defined;
    
    // Metrics
    private float advanceWidth;
    private float leftBearing;
    private float rightBearing;
    private float bboxX;
    private float bboxY;
    private float bboxWidth;
    private float bboxHeight;
    
    public CustomGlyph(int codepoint) {
        this.codepoint = codepoint;
        this.strokes = new ArrayList<>();
        this.defined = false;
        this.advanceWidth = 600.0f; // Default advance
        this.leftBearing = 50.0f;
        this.rightBearing = 50.0f;
    }
    
    public CustomGlyph(char ch) {
        this((int) ch);
    }
    
    public CustomGlyph copy() {
        CustomGlyph copy = new CustomGlyph(codepoint);
        for (CustomStroke stroke : strokes) {
            copy.strokes.add(stroke.copy());
        }
        copy.defined = defined;
        copy.advanceWidth = advanceWidth;
        copy.leftBearing = leftBearing;
        copy.rightBearing = rightBearing;
        copy.bboxX = bboxX;
        copy.bboxY = bboxY;
        copy.bboxWidth = bboxWidth;
        copy.bboxHeight = bboxHeight;
        return copy;
    }
    
    public int getCodepoint() { return codepoint; }
    
    public char getCharacter() { 
        return codepoint < 0x10000 ? (char) codepoint : '?'; 
    }
    
    public String getCharacterString() {
        return new String(Character.toChars(codepoint));
    }
    
    public boolean isDefined() { return defined; }
    public void setDefined(boolean defined) { this.defined = defined; }
    
    // Stroke management
    public void addStroke(CustomStroke stroke) {
        if (stroke != null && stroke.getPointCount() > 0) {
            strokes.add(stroke.copy());
            defined = true;
            computeMetrics();
        }
    }
    
    public void removeStroke(int index) {
        if (index >= 0 && index < strokes.size()) {
            strokes.remove(index);
            defined = !strokes.isEmpty();
            computeMetrics();
        }
    }
    
    public void clearStrokes() {
        strokes.clear();
        defined = false;
        bboxX = bboxY = bboxWidth = bboxHeight = 0;
    }
    
    public List<CustomStroke> getStrokes() {
        return Collections.unmodifiableList(strokes);
    }
    
    public int getStrokeCount() { return strokes.size(); }
    
    public CustomStroke getStroke(int index) {
        return strokes.get(index);
    }
    
    // Metrics
    public float getAdvanceWidth() { return advanceWidth; }
    public void setAdvanceWidth(float width) { this.advanceWidth = width; }
    
    public float getLeftBearing() { return leftBearing; }
    public void setLeftBearing(float bearing) { this.leftBearing = bearing; }
    
    public float getRightBearing() { return rightBearing; }
    public void setRightBearing(float bearing) { this.rightBearing = bearing; }
    
    public float getBboxX() { return bboxX; }
    public float getBboxY() { return bboxY; }
    public float getBboxWidth() { return bboxWidth; }
    public float getBboxHeight() { return bboxHeight; }
    
    public float[] getBounds() {
        return new float[] { bboxX, bboxY, bboxWidth, bboxHeight };
    }
    
    public void computeMetrics() {
        if (strokes.isEmpty()) {
            bboxX = bboxY = bboxWidth = bboxHeight = 0;
            return;
        }
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (CustomStroke stroke : strokes) {
            float[] bounds = stroke.getBounds();
            minX = Math.min(minX, bounds[0]);
            minY = Math.min(minY, bounds[1]);
            maxX = Math.max(maxX, bounds[0] + bounds[2]);
            maxY = Math.max(maxY, bounds[1] + bounds[3]);
        }
        
        bboxX = minX;
        bboxY = minY;
        bboxWidth = maxX - minX;
        bboxHeight = maxY - minY;
        
        // Auto-calculate advance width
        advanceWidth = bboxWidth + leftBearing + rightBearing;
    }
    
    public void computeMetrics(float emSize) {
        computeMetrics();
        
        // Adjust bearing based on em size
        float sideBearing = emSize * 0.05f;
        leftBearing = sideBearing;
        rightBearing = sideBearing;
        advanceWidth = bboxWidth + sideBearing * 2.0f;
    }
    
    // Transformations
    public void translate(float dx, float dy) {
        for (CustomStroke stroke : strokes) {
            stroke.translate(dx, dy);
        }
        computeMetrics();
    }
    
    public void scale(float sx, float sy, float cx, float cy) {
        for (CustomStroke stroke : strokes) {
            stroke.scale(sx, sy, cx, cy);
        }
        computeMetrics();
    }
    
    public void normalize(float emSize, float margin) {
        if (strokes.isEmpty()) return;
        
        computeMetrics();
        
        float targetSize = emSize - margin * 2.0f;
        float scale = Math.min(targetSize / bboxWidth, targetSize / bboxHeight);
        
        if (Float.isInfinite(scale) || Float.isNaN(scale) || scale <= 0) return;
        
        float cx = bboxX + bboxWidth * 0.5f;
        float cy = bboxY + bboxHeight * 0.5f;
        float targetCx = emSize * 0.5f;
        float targetCy = emSize * 0.5f;
        
        for (CustomStroke stroke : strokes) {
            stroke.scale(scale, scale, cx, cy);
            stroke.translate(targetCx - cx, targetCy - cy);
        }
        
        computeMetrics(emSize);
    }
    
    public void smooth(int iterations) {
        for (CustomStroke stroke : strokes) {
            stroke.smooth(iterations);
        }
    }
    
    public void simplify(float epsilon) {
        for (CustomStroke stroke : strokes) {
            stroke.simplify(epsilon);
        }
    }
    
    @Override
    public String toString() {
        String ch = codepoint >= 32 && codepoint < 127 ? "'" + (char) codepoint + "'" : String.format("U+%04X", codepoint);
        return String.format("CustomGlyph(%s, %d strokes, %s)", ch, strokes.size(), defined ? "defined" : "empty");
    }
}
