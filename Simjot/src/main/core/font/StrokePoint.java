/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.font;

/**
 * A single point in a stroke with position, pressure, and timing information.
 */
public final class StrokePoint {
    
    private float x;
    private float y;
    private float pressure;
    private float timestamp;
    
    public StrokePoint(float x, float y) {
        this(x, y, 1.0f, 0.0f);
    }
    
    public StrokePoint(float x, float y, float pressure) {
        this(x, y, pressure, 0.0f);
    }
    
    public StrokePoint(float x, float y, float pressure, float timestamp) {
        this.x = x;
        this.y = y;
        this.pressure = Math.max(0.0f, Math.min(1.0f, pressure));
        this.timestamp = timestamp;
    }
    
    public StrokePoint copy() {
        return new StrokePoint(x, y, pressure, timestamp);
    }
    
    public float getX() { return x; }
    public float getY() { return y; }
    public float getPressure() { return pressure; }
    public float getTimestamp() { return timestamp; }
    
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setPressure(float pressure) { 
        this.pressure = Math.max(0.0f, Math.min(1.0f, pressure)); 
    }
    public void setTimestamp(float timestamp) { this.timestamp = timestamp; }
    
    public void translate(float dx, float dy) {
        this.x += dx;
        this.y += dy;
    }
    
    public void scale(float sx, float sy, float cx, float cy) {
        this.x = cx + (this.x - cx) * sx;
        this.y = cy + (this.y - cy) * sy;
    }
    
    public float distanceTo(StrokePoint other) {
        float dx = other.x - this.x;
        float dy = other.y - this.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    public float distanceTo(float ox, float oy) {
        float dx = ox - this.x;
        float dy = oy - this.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    public StrokePoint lerp(StrokePoint other, float t) {
        return new StrokePoint(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
            pressure + (other.pressure - pressure) * t,
            timestamp + (other.timestamp - timestamp) * t
        );
    }
    
    @Override
    public String toString() {
        return String.format("StrokePoint(%.2f, %.2f, p=%.2f)", x, y, pressure);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StrokePoint)) return false;
        StrokePoint other = (StrokePoint) obj;
        return Float.compare(x, other.x) == 0 &&
               Float.compare(y, other.y) == 0 &&
               Float.compare(pressure, other.pressure) == 0;
    }
    
    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(pressure);
        return result;
    }
}
