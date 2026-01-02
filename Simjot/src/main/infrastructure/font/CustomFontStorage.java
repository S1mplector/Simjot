/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import main.core.font.CustomFont;
import main.core.font.CustomGlyph;
import main.core.font.CustomStroke;
import main.core.font.StrokePoint;

/**
 * Handles loading and saving custom fonts in .sjf format.
 * Pure Java implementation (can optionally use native code for performance).
 */
public final class CustomFontStorage {
    
    private static final int SJF_MAGIC = 0x534A4631; // "SJF1"
    private static final int SJF_VERSION = 1;
    private static final int MAX_NAME_LEN = 64;
    private static final int MAX_AUTHOR_LEN = 64;
    private static final int RESERVED_SIZE = 64;
    
    private CustomFontStorage() {}
    
    public static CustomFont load(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return loadFromBytes(data);
    }
    
    public static CustomFont load(File file) throws IOException {
        return load(file.toPath());
    }
    
    public static CustomFont loadFromBytes(byte[] data) throws IOException {
        if (data == null || data.length < 200) {
            throw new IOException("Invalid font data: too short");
        }
        
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // Read header
        int magic = buf.getInt();
        if (magic != SJF_MAGIC) {
            throw new IOException("Invalid font file: bad magic number");
        }
        
        int version = buf.getInt();
        if (version > SJF_VERSION) {
            throw new IOException("Unsupported font version: " + version);
        }
        
        String name = readString(buf, MAX_NAME_LEN);
        String author = readString(buf, MAX_AUTHOR_LEN);
        int glyphCount = buf.getInt();
        int flags = buf.getInt();
        float emSize = buf.getFloat();
        float ascender = buf.getFloat();
        float descender = buf.getFloat();
        float lineGap = buf.getFloat();
        float defaultThickness = buf.getFloat();
        long createdTimestamp = buf.getLong();
        long modifiedTimestamp = buf.getLong();
        
        // Skip reserved
        buf.position(buf.position() + RESERVED_SIZE);
        
        // Create font
        CustomFont font = new CustomFont(name, author);
        font.setEmSize(emSize);
        font.setAscender(ascender);
        font.setDescender(descender);
        font.setLineGap(lineGap);
        font.setDefaultThickness(defaultThickness);
        
        // Read glyphs
        for (int g = 0; g < glyphCount; g++) {
            int codepoint = buf.getInt();
            int strokeCount = buf.getShort() & 0xFFFF;
            int glyphFlags = buf.getShort() & 0xFFFF;
            boolean defined = (glyphFlags & 1) != 0;
            
            // Read metrics
            float advanceWidth = buf.getFloat();
            float leftBearing = buf.getFloat();
            float rightBearing = buf.getFloat();
            float bboxX = buf.getFloat();
            float bboxY = buf.getFloat();
            float bboxWidth = buf.getFloat();
            float bboxHeight = buf.getFloat();
            
            CustomGlyph glyph = font.getOrCreateGlyph(codepoint);
            glyph.setAdvanceWidth(advanceWidth);
            glyph.setLeftBearing(leftBearing);
            glyph.setRightBearing(rightBearing);
            
            // Read strokes
            for (int s = 0; s < strokeCount; s++) {
                int pointCount = buf.getShort() & 0xFFFF;
                float thickness = buf.getFloat();
                int color = buf.getInt();
                
                CustomStroke stroke = new CustomStroke(thickness);
                
                // Read points
                for (int p = 0; p < pointCount; p++) {
                    float x = buf.getFloat();
                    float y = buf.getFloat();
                    float pressure = buf.getFloat();
                    float timestamp = buf.getFloat();
                    stroke.addPoint(x, y, pressure, timestamp);
                }
                
                glyph.addStroke(stroke);
            }
            
            glyph.setDefined(defined);
        }
        
        return font;
    }
    
    public static void save(CustomFont font, Path path) throws IOException {
        byte[] data = saveToBytes(font);
        Files.write(path, data);
    }
    
    public static void save(CustomFont font, File file) throws IOException {
        save(font, file.toPath());
    }
    
    public static byte[] saveToBytes(CustomFont font) {
        // Calculate size
        int size = calculateSize(font);
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        
        // Write header
        buf.putInt(SJF_MAGIC);
        buf.putInt(SJF_VERSION);
        writeString(buf, font.getName(), MAX_NAME_LEN);
        writeString(buf, font.getAuthor(), MAX_AUTHOR_LEN);
        buf.putInt(font.getGlyphCount());
        buf.putInt(0); // flags
        buf.putFloat(font.getEmSize());
        buf.putFloat(font.getAscender());
        buf.putFloat(font.getDescender());
        buf.putFloat(font.getLineGap());
        buf.putFloat(font.getDefaultThickness());
        buf.putLong(font.getCreatedTimestamp());
        buf.putLong(font.getModifiedTimestamp());
        
        // Reserved
        for (int i = 0; i < RESERVED_SIZE; i++) buf.put((byte) 0);
        
        // Write glyphs
        for (int codepoint : font.getCodepoints()) {
            CustomGlyph glyph = font.getGlyph(codepoint);
            if (glyph == null) continue;
            
            buf.putInt(codepoint);
            buf.putShort((short) glyph.getStrokeCount());
            buf.putShort((short) (glyph.isDefined() ? 1 : 0));
            
            // Metrics
            buf.putFloat(glyph.getAdvanceWidth());
            buf.putFloat(glyph.getLeftBearing());
            buf.putFloat(glyph.getRightBearing());
            buf.putFloat(glyph.getBboxX());
            buf.putFloat(glyph.getBboxY());
            buf.putFloat(glyph.getBboxWidth());
            buf.putFloat(glyph.getBboxHeight());
            
            // Strokes
            for (CustomStroke stroke : glyph.getStrokes()) {
                buf.putShort((short) stroke.getPointCount());
                buf.putFloat(stroke.getThickness());
                buf.putInt(stroke.getColor().getRGB());
                
                for (StrokePoint point : stroke.getPoints()) {
                    buf.putFloat(point.getX());
                    buf.putFloat(point.getY());
                    buf.putFloat(point.getPressure());
                    buf.putFloat(point.getTimestamp());
                }
            }
        }
        
        return buf.array();
    }
    
    private static int calculateSize(CustomFont font) {
        // Header size
        int size = 4 + 4 + MAX_NAME_LEN + MAX_AUTHOR_LEN + 4 + 4 + 
                   5 * 4 + 2 * 8 + RESERVED_SIZE; // magic, version, name, author, count, flags, metrics, timestamps, reserved
        
        for (int codepoint : font.getCodepoints()) {
            CustomGlyph glyph = font.getGlyph(codepoint);
            if (glyph == null) continue;
            
            // Glyph header: codepoint, stroke_count, flags, metrics (7 floats)
            size += 4 + 2 + 2 + 7 * 4;
            
            for (CustomStroke stroke : glyph.getStrokes()) {
                // Stroke header: point_count, thickness, color
                size += 2 + 4 + 4;
                // Points: x, y, pressure, timestamp (4 floats each)
                size += stroke.getPointCount() * 4 * 4;
            }
        }
        
        return size;
    }
    
    private static String readString(ByteBuffer buf, int maxLen) {
        byte[] bytes = new byte[maxLen];
        buf.get(bytes);
        int len = 0;
        while (len < maxLen && bytes[len] != 0) len++;
        return new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    private static void writeString(ByteBuffer buf, String str, int maxLen) {
        byte[] bytes = str != null ? str.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        int len = Math.min(bytes.length, maxLen - 1);
        buf.put(bytes, 0, len);
        for (int i = len; i < maxLen; i++) buf.put((byte) 0);
    }
}
