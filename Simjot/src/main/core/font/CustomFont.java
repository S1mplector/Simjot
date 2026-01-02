/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.font;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A complete custom font with metadata and glyph collection.
 */
public final class CustomFont {
    
    public static final float DEFAULT_EM_SIZE = 1000.0f;
    public static final float DEFAULT_ASCENDER = 800.0f;
    public static final float DEFAULT_DESCENDER = 200.0f;
    public static final float DEFAULT_LINE_GAP = 100.0f;
    public static final float DEFAULT_THICKNESS = 2.0f;
    
    private String name;
    private String author;
    private float emSize;
    private float ascender;
    private float descender;
    private float lineGap;
    private float defaultThickness;
    private long createdTimestamp;
    private long modifiedTimestamp;
    
    private final Map<Integer, CustomGlyph> glyphs;
    
    public CustomFont(String name) {
        this(name, "");
    }
    
    public CustomFont(String name, String author) {
        this.name = name != null ? name : "Untitled Font";
        this.author = author != null ? author : "";
        this.emSize = DEFAULT_EM_SIZE;
        this.ascender = DEFAULT_ASCENDER;
        this.descender = DEFAULT_DESCENDER;
        this.lineGap = DEFAULT_LINE_GAP;
        this.defaultThickness = DEFAULT_THICKNESS;
        this.createdTimestamp = System.currentTimeMillis();
        this.modifiedTimestamp = this.createdTimestamp;
        this.glyphs = new HashMap<>();
    }
    
    public CustomFont copy() {
        CustomFont copy = new CustomFont(name, author);
        copy.emSize = emSize;
        copy.ascender = ascender;
        copy.descender = descender;
        copy.lineGap = lineGap;
        copy.defaultThickness = defaultThickness;
        copy.createdTimestamp = createdTimestamp;
        copy.modifiedTimestamp = modifiedTimestamp;
        
        for (Map.Entry<Integer, CustomGlyph> entry : glyphs.entrySet()) {
            copy.glyphs.put(entry.getKey(), entry.getValue().copy());
        }
        
        return copy;
    }
    
    // Metadata
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name != null ? name : "Untitled Font"; 
        touch();
    }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { 
        this.author = author != null ? author : ""; 
        touch();
    }
    
    public float getEmSize() { return emSize; }
    public void setEmSize(float emSize) { 
        this.emSize = Math.max(100.0f, emSize); 
        touch();
    }
    
    public float getAscender() { return ascender; }
    public void setAscender(float ascender) { 
        this.ascender = ascender; 
        touch();
    }
    
    public float getDescender() { return descender; }
    public void setDescender(float descender) { 
        this.descender = descender; 
        touch();
    }
    
    public float getLineGap() { return lineGap; }
    public void setLineGap(float lineGap) { 
        this.lineGap = lineGap; 
        touch();
    }
    
    public float getDefaultThickness() { return defaultThickness; }
    public void setDefaultThickness(float thickness) { 
        this.defaultThickness = Math.max(0.5f, thickness); 
        touch();
    }
    
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getModifiedTimestamp() { return modifiedTimestamp; }
    
    public void touch() {
        this.modifiedTimestamp = System.currentTimeMillis();
    }
    
    // Scaled metrics
    public float getAscender(int pixelSize) {
        return ascender * pixelSize / emSize;
    }
    
    public float getDescender(int pixelSize) {
        return descender * pixelSize / emSize;
    }
    
    public float getLineHeight(int pixelSize) {
        return (ascender + descender + lineGap) * pixelSize / emSize;
    }
    
    // Glyph management
    public CustomGlyph getGlyph(int codepoint) {
        return glyphs.get(codepoint);
    }
    
    public CustomGlyph getGlyph(char ch) {
        return glyphs.get((int) ch);
    }
    
    public CustomGlyph getOrCreateGlyph(int codepoint) {
        return glyphs.computeIfAbsent(codepoint, CustomGlyph::new);
    }
    
    public CustomGlyph getOrCreateGlyph(char ch) {
        return getOrCreateGlyph((int) ch);
    }
    
    public void setGlyph(int codepoint, CustomGlyph glyph) {
        if (glyph != null) {
            glyphs.put(codepoint, glyph);
            touch();
        }
    }
    
    public void removeGlyph(int codepoint) {
        if (glyphs.remove(codepoint) != null) {
            touch();
        }
    }
    
    public boolean hasGlyph(int codepoint) {
        CustomGlyph g = glyphs.get(codepoint);
        return g != null && g.isDefined();
    }
    
    public boolean hasGlyph(char ch) {
        return hasGlyph((int) ch);
    }
    
    public Set<Integer> getCodepoints() {
        return Collections.unmodifiableSet(glyphs.keySet());
    }
    
    public int getGlyphCount() {
        return glyphs.size();
    }
    
    public int getDefinedGlyphCount() {
        int count = 0;
        for (CustomGlyph g : glyphs.values()) {
            if (g.isDefined()) count++;
        }
        return count;
    }
    
    // Text measurement
    public float measureText(String text, int pixelSize) {
        if (text == null || text.isEmpty()) return 0.0f;
        
        float scale = (float) pixelSize / emSize;
        float width = 0.0f;
        
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if (Character.isSupplementaryCodePoint(cp)) i++;
            
            CustomGlyph glyph = glyphs.get(cp);
            if (glyph != null && glyph.isDefined()) {
                width += glyph.getAdvanceWidth();
            } else {
                width += emSize * 0.6f; // Default advance for missing glyphs
            }
        }
        
        return width * scale;
    }
    
    public float measureChar(int codepoint, int pixelSize) {
        float scale = (float) pixelSize / emSize;
        
        CustomGlyph glyph = glyphs.get(codepoint);
        if (glyph != null && glyph.isDefined()) {
            return glyph.getAdvanceWidth() * scale;
        }
        
        return emSize * 0.6f * scale;
    }
    
    // Normalization
    public void normalizeAllGlyphs(float margin) {
        for (CustomGlyph glyph : glyphs.values()) {
            if (glyph.isDefined()) {
                glyph.normalize(emSize, margin);
            }
        }
        touch();
    }
    
    public void smoothAllGlyphs(int iterations) {
        for (CustomGlyph glyph : glyphs.values()) {
            if (glyph.isDefined()) {
                glyph.smooth(iterations);
            }
        }
        touch();
    }
    
    public void computeAllMetrics() {
        for (CustomGlyph glyph : glyphs.values()) {
            glyph.computeMetrics(emSize);
        }
        touch();
    }
    
    @Override
    public String toString() {
        return String.format("CustomFont('%s' by %s, %d glyphs, %d defined)", 
            name, author.isEmpty() ? "Unknown" : author, 
            getGlyphCount(), getDefinedGlyphCount());
    }
}
