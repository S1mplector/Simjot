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
    private static volatile NativeFonts nfInstance;
    private static volatile boolean attempted;

    private NativeFontSupport() {}

    private static NativeFonts nf() {
        if (nfInstance != null || attempted) return nfInstance;
        synchronized (LOCK) {
            if (nfInstance != null || attempted) return nfInstance;
            attempted = true;
            SymbolLookup lookup = NativeAccess.symbolLookup();
            if (lookup == null) return null;
            nfInstance = new NativeFonts(lookup);
        }
        return nfInstance;
    }

    public static boolean isAvailable() {
        return nf() != null;
    }

    public static void clearCache() {
        NativeFonts fonts = nf();
        synchronized (FONT_CACHE) {
            if (fonts != null) {
                for (FontHandle handle : FONT_CACHE.values()) {
                    if (handle != null) {
                        fonts.fontFree(handle.font);
                    }
                }
            }
            FONT_CACHE.clear();
        }
    }

    public static BufferedImage renderGlyph(CustomFont font, CustomGlyph glyph, int size, int oversample, boolean antialiasing) {
        if (font == null || glyph == null || !glyph.isDefined() || size <= 0) return null;
        NativeFonts fonts = nf();
        if (fonts == null) return null;

        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;

            MemorySegment nativeGlyph = fonts.fontGetGlyph(handle.font, glyph.getCodepoint());
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;

            int os = antialiasing ? Math.max(1, Math.min(8, oversample)) : 1;
            MemorySegment bitmap = fonts.rasterGlyph(nativeGlyph, size, os, 1.0f, font.getEmSize());
            if (bitmap == null || bitmap.equals(MemorySegment.NULL)) return null;

            try {
                int width = fonts.bitmapGetWidth(bitmap);
                int height = fonts.bitmapGetHeight(bitmap);
                if (width <= 0 || height <= 0) return null;

                int[] pixels = new int[width * height];
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment out = arena.allocate((long) pixels.length * ValueLayout.JAVA_INT.byteSize());
                    int result = fonts.bitmapToArgb(bitmap, Color.BLACK.getRGB(), out, width);
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
                fonts.bitmapFree(bitmap);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static float[] getGlyphBounds(CustomFont font, int codepoint) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;

            MemorySegment nativeGlyph = fonts.fontGetGlyph(handle.font, codepoint);
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;
            return fonts.glyphGetBounds(nativeGlyph);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getGlyphAdvance(CustomFont font, int codepoint) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;

            MemorySegment nativeGlyph = fonts.fontGetGlyph(handle.font, codepoint);
            if (nativeGlyph == null || nativeGlyph.equals(MemorySegment.NULL)) return null;
            return fonts.glyphGetAdvance(nativeGlyph);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float measureText(CustomFont font, String text, int size) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null || text == null || text.isEmpty()) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;
            return fonts.fontMeasureText(handle.font, text, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float measureChar(CustomFont font, int codepoint, int size) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;
            return fonts.fontMeasureChar(handle.font, codepoint, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getLineHeight(CustomFont font, int size) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;
            return fonts.fontGetLineHeight(handle.font, size);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Float getAscender(CustomFont font, int size) {
        NativeFonts fonts = nf();
        if (fonts == null || font == null) return null;
        try {
            FontHandle handle = fontHandle(font, fonts);
            if (handle == null) return null;
            return fonts.fontGetAscender(handle.font, size);
        } catch (Throwable t) {
            return null;
        }
    }

    private static FontHandle fontHandle(CustomFont font, NativeFonts fonts) {
        long ts = font.getModifiedTimestamp();
        synchronized (FONT_CACHE) {
            FontHandle handle = FONT_CACHE.get(font);
            if (handle != null && handle.modifiedTimestamp == ts) return handle;

            if (handle != null) {
                fonts.fontFree(handle.font);
                FONT_CACHE.remove(font);
            }

            MemorySegment nativeFont = unpackFont(fonts, font);
            if (nativeFont == null || nativeFont.equals(MemorySegment.NULL)) return null;

            float emSize = font.getEmSize();
            for (int codepoint : font.getCodepoints()) {
                MemorySegment glyph = fonts.fontGetGlyph(nativeFont, codepoint);
                if (glyph != null && !glyph.equals(MemorySegment.NULL)) {
                    fonts.glyphComputeMetrics(glyph, emSize);
                }
            }

            FontHandle created = new FontHandle(nativeFont, ts);
            FONT_CACHE.put(font, created);
            return created;
        }
    }

    private static MemorySegment unpackFont(NativeFonts fonts, CustomFont font) {
        byte[] data = CustomFontStorage.saveToBytes(font);
        if (data == null || data.length == 0) return MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(data.length);
            MemorySegment.copy(data, 0, buffer, ValueLayout.JAVA_BYTE, 0, data.length);
            return fonts.fontUnpack(buffer, data.length);
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
