/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.WeakHashMap;

import main.core.font.CustomFont;
import main.core.font.CustomGlyph;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeFonts;

/**
 * Native-accelerated custom font helpers with caching and safe fallbacks.
 */
public final class NativeFontSupport {
    private static final Object LOCK = new Object();
    private static final Map<CustomFont, FontHandle> FONT_CACHE = new WeakHashMap<>();
    private static volatile NativeFonts nativeFonts;
    private static volatile boolean attempted;

    private NativeFontSupport() {}

    private static NativeFonts nativeFonts() {
        if (nativeFonts != null || attempted) return nativeFonts;
        synchronized (LOCK) {
            if (nativeFonts != null || attempted) return nativeFonts;
            attempted = true;
            SymbolLookup lookup = NativeAccess.symbolLookup();
            if (lookup == null) return null;
            nativeFonts = new NativeFonts(lookup);
        }
        return nativeFonts;
    }

    public static boolean isAvailable() {
        return nativeFonts() != null;
    }

    public static void clearCache() {
        NativeFonts native = nativeFonts();
        synchronized (FONT_CACHE) {
            if (native != null) {
                for (FontHandle handle : FONT_CACHE.values()) {
                    if (handle != null) {
                        native.fontFree(handle.font);
                    }
                }
            }
            FONT_CACHE.clear();
        }
    }

    public static BufferedImage renderGlyph(CustomFont font, CustomGlyph glyph, int size, int oversample, boolean antialiasing) {
        if (font == null || glyph == null || !glyph.isDefined() || size <= 0) return null;
        NativeFonts native = nativeFonts();
        if (native == null) return null;

        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;

            MemorySegment nativeGlyph = native.fontGetGlyph(handle.font, glyph.getCodepoint());
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;

            int os = antialiasing ? Math.max(1, Math.min(8, oversample)) : 1;
            MemorySegment bitmap = native.rasterGlyph(nativeGlyph, size, os, 1.0f, font.getEmSize());
            if (bitmap == null || bitmap.equals(MemorySegment.NULL)) return null;

            try {
                int width = native.bitmapGetWidth(bitmap);
                int height = native.bitmapGetHeight(bitmap);
                if (width <= 0 || height <= 0) return null;

                int[] pixels = new int[width * height];
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment out = arena.allocateArray(ValueLayout.JAVA_INT, pixels.length);
                    int result = native.bitmapToArgb(bitmap, Color.BLACK.getRGB(), out, width);
                    if (result >= 0) {
                        MemorySegment.copy(out, ValueLayout.JAVA_INT, 0, pixels, 0, pixels.length);
                    } else {
                        return null;
                    }
                }

                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, width, height, pixels, 0, width);
                return img;
            } finally {
                native.bitmapFree(bitmap);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static float[] getGlyphBounds(CustomFont font, int codepoint) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;

            MemorySegment nativeGlyph = native.fontGetGlyph(handle.font, codepoint);
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;
            return native.glyphGetBounds(nativeGlyph);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getGlyphAdvance(CustomFont font, int codepoint) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;

            MemorySegment nativeGlyph = native.fontGetGlyph(handle.font, codepoint);
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;
            return native.glyphGetAdvance(nativeGlyph);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float measureText(CustomFont font, String text, int size) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null || text == null || text.isEmpty()) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;
            return native.fontMeasureText(handle.font, text, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float measureChar(CustomFont font, int codepoint, int size) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;
            return native.fontMeasureChar(handle.font, codepoint, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getLineHeight(CustomFont font, int size) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;
            return native.fontGetLineHeight(handle.font, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getAscender(CustomFont font, int size) {
        NativeFonts native = nativeFonts();
        if (native == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, native);
            if (handle == null) return null;
            return native.fontGetAscender(handle.font, size);
        } catch (Throwable t) {
            return null;
        }
    }

    private static FontHandle fontHandle(CustomFont font, NativeFonts native) {
        long ts = font.getModifiedTimestamp();
        synchronized (FONT_CACHE) {
            FontHandle handle = FONT_CACHE.get(font);
            if (handle != null && handle.modifiedTimestamp == ts) return handle;

            if (handle != null) {
                native.fontFree(handle.font);
                FONT_CACHE.remove(font);
            }

            MemorySegment nativeFont = unpackFont(native, font);
            if (nativeFont == null || nativeFont.equals(MemorySegment.NULL)) return null;

            // Ensure native metrics reflect latest stroke data.
            float emSize = font.getEmSize();
            for (int codepoint : font.getCodepoints()) {
                MemorySegment glyph = native.fontGetGlyph(nativeFont, codepoint);
                if (glyph != null && !glyph.equals(MemorySegment.NULL)) {
                    native.glyphComputeMetrics(glyph, emSize);
                }
            }

            FontHandle created = new FontHandle(nativeFont, ts);
            FONT_CACHE.put(font, created);
            return created;
        }
    }

    private static MemorySegment unpackFont(NativeFonts native, CustomFont font) {
        byte[] data = CustomFontStorage.saveToBytes(font);
        if (data == null || data.length == 0) return MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocateArray(ValueLayout.JAVA_BYTE, data.length);
            MemorySegment.copy(data, 0, buffer, ValueLayout.JAVA_BYTE, 0, data.length);
            return native.fontUnpack(buffer, data.length);
        }
    }

    private static final class FontHandle {
        private final MemorySegment font;
        private final long modifiedTimestamp;

        private FontHandle(MemorySegment font, long modifiedTimestamp) {
            this.font = font;
            this.modifiedTimestamp = modifiedTimestamp;
        }
    }
}
