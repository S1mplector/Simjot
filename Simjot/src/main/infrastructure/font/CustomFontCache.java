/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache for rendered glyph bitmaps.
 */
public final class CustomFontCache {
    
    private static final int DEFAULT_MAX_ENTRIES = 512;
    private static final long DEFAULT_MAX_MEMORY = 64 * 1024 * 1024; // 64MB
    
    private final int maxEntries;
    private final long maxMemory;
    private long currentMemory;
    
    private final Map<String, BufferedImage> glyphCache;
    
    public CustomFontCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_MEMORY);
    }
    
    public CustomFontCache(int maxEntries, long maxMemory) {
        this.maxEntries = maxEntries;
        this.maxMemory = maxMemory;
        this.currentMemory = 0;
        
        this.glyphCache = new LinkedHashMap<String, BufferedImage>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                if (size() > CustomFontCache.this.maxEntries || 
                    currentMemory > CustomFontCache.this.maxMemory) {
                    BufferedImage img = eldest.getValue();
                    if (img != null) {
                        currentMemory -= estimateImageMemory(img);
                    }
                    return true;
                }
                return false;
            }
        };
    }
    
    public synchronized BufferedImage getGlyphImage(String key) {
        return glyphCache.get(key);
    }
    
    public synchronized void putGlyphImage(String key, BufferedImage image) {
        if (key == null || image == null) return;
        
        // Remove old entry if exists
        BufferedImage old = glyphCache.remove(key);
        if (old != null) {
            currentMemory -= estimateImageMemory(old);
        }
        
        // Add new entry
        long imageMemory = estimateImageMemory(image);
        glyphCache.put(key, image);
        currentMemory += imageMemory;
        
        // Trigger cleanup if needed
        while (currentMemory > maxMemory && glyphCache.size() > 1) {
            String firstKey = glyphCache.keySet().iterator().next();
            BufferedImage removed = glyphCache.remove(firstKey);
            if (removed != null) {
                currentMemory -= estimateImageMemory(removed);
            }
        }
    }
    
    public synchronized void remove(String key) {
        BufferedImage removed = glyphCache.remove(key);
        if (removed != null) {
            currentMemory -= estimateImageMemory(removed);
        }
    }
    
    public synchronized void clear() {
        glyphCache.clear();
        currentMemory = 0;
    }
    
    public synchronized int size() {
        return glyphCache.size();
    }
    
    public synchronized long getMemoryUsage() {
        return currentMemory;
    }
    
    public int getMaxEntries() {
        return maxEntries;
    }
    
    public long getMaxMemory() {
        return maxMemory;
    }
    
    private static long estimateImageMemory(BufferedImage img) {
        if (img == null) return 0;
        int bytesPerPixel;
        switch (img.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_RGB:
                bytesPerPixel = 4;
                break;
            case BufferedImage.TYPE_BYTE_GRAY:
                bytesPerPixel = 1;
                break;
            default:
                bytesPerPixel = 4;
        }
        return (long) img.getWidth() * img.getHeight() * bytesPerPixel;
    }
    
    public synchronized String getStats() {
        return String.format("Cache: %d entries, %.2f MB / %.2f MB", 
            glyphCache.size(), 
            currentMemory / (1024.0 * 1024.0), 
            maxMemory / (1024.0 * 1024.0));
    }
}
