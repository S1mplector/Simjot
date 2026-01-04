/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for native custom font functions.
 * Provides Java interface to stroke-based font creation, manipulation, and rasterization.
 */
public final class NativeFonts implements AutoCloseable {
    
    private final Arena arena;
    private final SymbolLookup lookup;
    private final Linker linker;
    
    // Font management handles
    private final MethodHandle fontCreateHandle;
    private final MethodHandle fontFreeHandle;
    private final MethodHandle fontLoadHandle;
    private final MethodHandle fontSaveHandle;
    private final MethodHandle fontGetNameHandle;
    private final MethodHandle fontSetNameHandle;
    private final MethodHandle fontGetAuthorHandle;
    private final MethodHandle fontSetAuthorHandle;
    private final MethodHandle fontGlyphCountHandle;
    private final MethodHandle fontDefinedGlyphCountHandle;
    private final MethodHandle fontImportHandle;
    
    // Font metrics handles
    private final MethodHandle fontGetAscenderHandle;
    private final MethodHandle fontGetDescenderHandle;
    private final MethodHandle fontGetLineHeightHandle;
    private final MethodHandle fontGetEmSizeHandle;
    private final MethodHandle fontMeasureTextHandle;
    private final MethodHandle fontMeasureCharHandle;
    
    // Glyph management handles
    private final MethodHandle fontGetGlyphHandle;
    private final MethodHandle fontAddGlyphHandle;
    private final MethodHandle glyphAddStrokeHandle;
    private final MethodHandle glyphClearStrokesHandle;
    private final MethodHandle glyphComputeMetricsHandle;
    private final MethodHandle glyphNormalizeHandle;
    private final MethodHandle glyphGetAdvanceHandle;
    private final MethodHandle glyphGetWidthHandle;
    private final MethodHandle glyphGetHeightHandle;
    private final MethodHandle glyphGetBoundsHandle;
    
    // Stroke handles
    private final MethodHandle strokeCreateHandle;
    private final MethodHandle strokeFreeHandle;
    private final MethodHandle strokeAddPointHandle;
    private final MethodHandle strokeClearHandle;
    private final MethodHandle strokeSmoothHandle;
    private final MethodHandle strokeLengthHandle;
    private final MethodHandle strokeGetPointCountHandle;
    private final MethodHandle strokeGetPointsHandle;
    private final MethodHandle strokeBoundsHandle;
    private final MethodHandle strokeTranslateHandle;
    private final MethodHandle strokeScaleHandle;
    private final MethodHandle strokeNormalizeHandle;
    private final MethodHandle strokeSimplifyHandle;
    
    // Rasterization handles
    private final MethodHandle bitmapCreateHandle;
    private final MethodHandle bitmapFreeHandle;
    private final MethodHandle bitmapClearHandle;
    private final MethodHandle bitmapGetWidthHandle;
    private final MethodHandle bitmapGetHeightHandle;
    private final MethodHandle bitmapGetStrideHandle;
    private final MethodHandle bitmapGetPixelsHandle;
    private final MethodHandle bitmapToArgbHandle;
    private final MethodHandle rasterGlyphHandle;
    private final MethodHandle rasterStrokeHandle;
    private final MethodHandle renderGlyphToBufferHandle;
    
    // Atlas handles
    private final MethodHandle atlasCreateHandle;
    private final MethodHandle atlasFreeHandle;
    private final MethodHandle atlasAddGlyphHandle;
    
    // Serialization handles
    private final MethodHandle fontPackHandle;
    private final MethodHandle fontUnpackHandle;
    
    public NativeFonts(SymbolLookup lookup) {
        this.arena = Arena.ofShared();
        this.lookup = lookup;
        this.linker = Linker.nativeLinker();
        
        // Font management
        fontCreateHandle = downcall("sjf_font_create", 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        fontFreeHandle = downcall("sjf_font_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        fontLoadHandle = downcall("sjf_font_load",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        fontSaveHandle = downcall("sjf_font_save",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        fontGetNameHandle = downcall("sjf_font_get_name",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontSetNameHandle = downcall("sjf_font_set_name",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        fontGetAuthorHandle = downcall("sjf_font_get_author",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontSetAuthorHandle = downcall("sjf_font_set_author",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        fontGlyphCountHandle = downcall("sjf_font_glyph_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        fontDefinedGlyphCountHandle = downcall("sjf_font_defined_glyph_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        fontImportHandle = downcall("sjf_font_import",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Font metrics
        fontGetAscenderHandle = downcall("sjf_font_get_ascender",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontGetDescenderHandle = downcall("sjf_font_get_descender",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontGetLineHeightHandle = downcall("sjf_font_get_line_height",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontGetEmSizeHandle = downcall("sjf_font_get_em_size",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        fontMeasureTextHandle = downcall("sjf_font_measure_text",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontMeasureCharHandle = downcall("sjf_font_measure_char",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        
        // Glyph management
        fontGetGlyphHandle = downcall("sjf_font_get_glyph",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontAddGlyphHandle = downcall("sjf_font_add_glyph",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        glyphAddStrokeHandle = downcall("sjf_glyph_add_stroke",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        glyphClearStrokesHandle = downcall("sjf_glyph_clear_strokes",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        glyphComputeMetricsHandle = downcall("sjf_glyph_compute_metrics",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
        glyphNormalizeHandle = downcall("sjf_glyph_normalize",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        glyphGetAdvanceHandle = downcall("sjf_glyph_get_advance",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        glyphGetWidthHandle = downcall("sjf_glyph_get_width",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        glyphGetHeightHandle = downcall("sjf_glyph_get_height",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        glyphGetBoundsHandle = downcall("sjf_glyph_get_bounds",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, 
                                      ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Stroke management
        strokeCreateHandle = downcall("sjf_stroke_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        strokeFreeHandle = downcall("sjf_stroke_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        strokeAddPointHandle = downcall("sjf_stroke_add_point",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, 
                ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        strokeClearHandle = downcall("sjf_stroke_clear",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        strokeSmoothHandle = downcall("sjf_stroke_smooth",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
        strokeLengthHandle = downcall("sjf_stroke_length",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        strokeGetPointCountHandle = downcall("sjf_stroke_get_point_count",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        strokeGetPointsHandle = downcall("sjf_stroke_get_points",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        strokeBoundsHandle = downcall("sjf_stroke_bounds",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        strokeTranslateHandle = downcall("sjf_stroke_translate",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        strokeScaleHandle = downcall("sjf_stroke_scale",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        strokeNormalizeHandle = downcall("sjf_stroke_normalize",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
        strokeSimplifyHandle = downcall("sjf_stroke_simplify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
        
        // Rasterization
        bitmapCreateHandle = downcall("sjf_bitmap_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        bitmapFreeHandle = downcall("sjf_bitmap_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        bitmapClearHandle = downcall("sjf_bitmap_clear",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        bitmapGetWidthHandle = downcall("sjf_bitmap_get_width",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        bitmapGetHeightHandle = downcall("sjf_bitmap_get_height",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        bitmapGetStrideHandle = downcall("sjf_bitmap_get_stride",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        bitmapGetPixelsHandle = downcall("sjf_bitmap_get_pixels",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        bitmapToArgbHandle = downcall("sjf_bitmap_to_argb",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        rasterGlyphHandle = downcall("sjf_raster_glyph",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        rasterStrokeHandle = downcall("sjf_raster_stroke",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        renderGlyphToBufferHandle = downcall("sjf_render_glyph_to_buffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
        
        // Atlas
        atlasCreateHandle = downcall("sjf_atlas_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        atlasFreeHandle = downcall("sjf_atlas_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        atlasAddGlyphHandle = downcall("sjf_atlas_add_glyph",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        // Serialization
        fontPackHandle = downcall("sjf_font_pack",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        fontUnpackHandle = downcall("sjf_font_unpack",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }
    
    private MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return lookup.find(name)
            .map(addr -> linker.downcallHandle(addr, descriptor))
            .orElse(null);
    }
    
    private MemorySegment allocString(String s) {
        if (s == null) return MemorySegment.NULL;
        return arena.allocateFrom(s);
    }
    
    @Override
    public void close() {
        arena.close();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FONT MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public MemorySegment fontCreate(String name, String author) {
        try {
            return (MemorySegment) fontCreateHandle.invokeExact(allocString(name), allocString(author));
        } catch (Throwable t) {
            throw new RuntimeException("fontCreate failed", t);
        }
    }
    
    public void fontFree(MemorySegment font) {
        try {
            fontFreeHandle.invokeExact(font);
        } catch (Throwable t) {
            throw new RuntimeException("fontFree failed", t);
        }
    }
    
    public MemorySegment fontLoad(String path) {
        try {
            return (MemorySegment) fontLoadHandle.invokeExact(allocString(path));
        } catch (Throwable t) {
            throw new RuntimeException("fontLoad failed", t);
        }
    }
    
    public int fontSave(MemorySegment font, String path) {
        try {
            return (int) fontSaveHandle.invokeExact(font, allocString(path));
        } catch (Throwable t) {
            throw new RuntimeException("fontSave failed", t);
        }
    }
    
    public String fontGetName(MemorySegment font) {
        try {
            MemorySegment buf = arena.allocate(64);
            int len = (int) fontGetNameHandle.invokeExact(font, buf, 64);
            if (len <= 0) return "";
            byte[] bytes = new byte[len];
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, bytes, 0, len);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetName failed", t);
        }
    }
    
    public int fontSetName(MemorySegment font, String name) {
        try {
            return (int) fontSetNameHandle.invokeExact(font, allocString(name));
        } catch (Throwable t) {
            throw new RuntimeException("fontSetName failed", t);
        }
    }
    
    public String fontGetAuthor(MemorySegment font) {
        try {
            MemorySegment buf = arena.allocate(64);
            int len = (int) fontGetAuthorHandle.invokeExact(font, buf, 64);
            if (len <= 0) return "";
            byte[] bytes = new byte[len];
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, bytes, 0, len);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetAuthor failed", t);
        }
    }
    
    public int fontSetAuthor(MemorySegment font, String author) {
        try {
            return (int) fontSetAuthorHandle.invokeExact(font, allocString(author));
        } catch (Throwable t) {
            throw new RuntimeException("fontSetAuthor failed", t);
        }
    }
    
    public int fontGlyphCount(MemorySegment font) {
        try {
            return (int) fontGlyphCountHandle.invokeExact(font);
        } catch (Throwable t) {
            throw new RuntimeException("fontGlyphCount failed", t);
        }
    }

    public int fontDefinedGlyphCount(MemorySegment font) {
        try {
            return (int) fontDefinedGlyphCountHandle.invokeExact(font);
        } catch (Throwable t) {
            throw new RuntimeException("fontDefinedGlyphCount failed", t);
        }
    }

    public boolean hasFontImport() {
        return fontImportHandle != null;
    }

    public record FontImportResult(MemorySegment font, int error, int total, int defined, int skipped) {}

    public FontImportResult fontImport(String path, String charset, float thickness) {
        if (fontImportHandle == null || path == null) return null;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pathSeg = local.allocateFrom(path);
            MemorySegment charsetSeg = (charset == null || charset.isBlank())
                ? MemorySegment.NULL
                : local.allocateFrom(charset);
            MemorySegment errSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment totalSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment definedSeg = local.allocate(ValueLayout.JAVA_INT);
            MemorySegment skippedSeg = local.allocate(ValueLayout.JAVA_INT);

            MemorySegment font = (MemorySegment) fontImportHandle.invokeExact(
                pathSeg, charsetSeg, thickness, errSeg, totalSeg, definedSeg, skippedSeg);

            int err = errSeg.get(ValueLayout.JAVA_INT, 0);
            int total = totalSeg.get(ValueLayout.JAVA_INT, 0);
            int defined = definedSeg.get(ValueLayout.JAVA_INT, 0);
            int skipped = skippedSeg.get(ValueLayout.JAVA_INT, 0);
            return new FontImportResult(font, err, total, defined, skipped);
        } catch (Throwable t) {
            return null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FONT METRICS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public float fontGetAscender(MemorySegment font, int size) {
        try {
            return (float) fontGetAscenderHandle.invokeExact(font, size);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetAscender failed", t);
        }
    }
    
    public float fontGetDescender(MemorySegment font, int size) {
        try {
            return (float) fontGetDescenderHandle.invokeExact(font, size);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetDescender failed", t);
        }
    }
    
    public float fontGetLineHeight(MemorySegment font, int size) {
        try {
            return (float) fontGetLineHeightHandle.invokeExact(font, size);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetLineHeight failed", t);
        }
    }
    
    public float fontGetEmSize(MemorySegment font) {
        try {
            return (float) fontGetEmSizeHandle.invokeExact(font);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetEmSize failed", t);
        }
    }
    
    public float fontMeasureText(MemorySegment font, String text, int size) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment str = text == null ? MemorySegment.NULL : tmp.allocateFrom(text);
            return (float) fontMeasureTextHandle.invokeExact(font, str, size);
        } catch (Throwable t) {
            throw new RuntimeException("fontMeasureText failed", t);
        }
    }
    
    public float fontMeasureChar(MemorySegment font, int codepoint, int size) {
        try {
            return (float) fontMeasureCharHandle.invokeExact(font, codepoint, size);
        } catch (Throwable t) {
            throw new RuntimeException("fontMeasureChar failed", t);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GLYPH MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public MemorySegment fontGetGlyph(MemorySegment font, int codepoint) {
        try {
            return (MemorySegment) fontGetGlyphHandle.invokeExact(font, codepoint);
        } catch (Throwable t) {
            throw new RuntimeException("fontGetGlyph failed", t);
        }
    }
    
    public MemorySegment fontAddGlyph(MemorySegment font, int codepoint) {
        try {
            return (MemorySegment) fontAddGlyphHandle.invokeExact(font, codepoint);
        } catch (Throwable t) {
            throw new RuntimeException("fontAddGlyph failed", t);
        }
    }
    
    public int glyphAddStroke(MemorySegment glyph, MemorySegment stroke) {
        try {
            return (int) glyphAddStrokeHandle.invokeExact(glyph, stroke);
        } catch (Throwable t) {
            throw new RuntimeException("glyphAddStroke failed", t);
        }
    }
    
    public void glyphClearStrokes(MemorySegment glyph) {
        try {
            glyphClearStrokesHandle.invokeExact(glyph);
        } catch (Throwable t) {
            throw new RuntimeException("glyphClearStrokes failed", t);
        }
    }
    
    public int glyphComputeMetrics(MemorySegment glyph, float emSize) {
        try {
            return (int) glyphComputeMetricsHandle.invokeExact(glyph, emSize);
        } catch (Throwable t) {
            throw new RuntimeException("glyphComputeMetrics failed", t);
        }
    }
    
    public int glyphNormalize(MemorySegment glyph, float emSize, float margin) {
        try {
            return (int) glyphNormalizeHandle.invokeExact(glyph, emSize, margin);
        } catch (Throwable t) {
            throw new RuntimeException("glyphNormalize failed", t);
        }
    }
    
    public float glyphGetAdvance(MemorySegment glyph) {
        try {
            return (float) glyphGetAdvanceHandle.invokeExact(glyph);
        } catch (Throwable t) {
            throw new RuntimeException("glyphGetAdvance failed", t);
        }
    }
    
    public float glyphGetWidth(MemorySegment glyph) {
        try {
            return (float) glyphGetWidthHandle.invokeExact(glyph);
        } catch (Throwable t) {
            throw new RuntimeException("glyphGetWidth failed", t);
        }
    }
    
    public float glyphGetHeight(MemorySegment glyph) {
        try {
            return (float) glyphGetHeightHandle.invokeExact(glyph);
        } catch (Throwable t) {
            throw new RuntimeException("glyphGetHeight failed", t);
        }
    }

    public float[] glyphGetBounds(MemorySegment glyph) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment x = tmp.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment y = tmp.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment w = tmp.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment h = tmp.allocate(ValueLayout.JAVA_FLOAT);
            glyphGetBoundsHandle.invokeExact(glyph, x, y, w, h);
            return new float[] {
                x.get(ValueLayout.JAVA_FLOAT, 0),
                y.get(ValueLayout.JAVA_FLOAT, 0),
                w.get(ValueLayout.JAVA_FLOAT, 0),
                h.get(ValueLayout.JAVA_FLOAT, 0)
            };
        } catch (Throwable t) {
            throw new RuntimeException("glyphGetBounds failed", t);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STROKE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public MemorySegment strokeCreate(int initialCapacity) {
        try {
            return (MemorySegment) strokeCreateHandle.invokeExact(initialCapacity);
        } catch (Throwable t) {
            throw new RuntimeException("strokeCreate failed", t);
        }
    }
    
    public void strokeFree(MemorySegment stroke) {
        try {
            strokeFreeHandle.invokeExact(stroke);
        } catch (Throwable t) {
            throw new RuntimeException("strokeFree failed", t);
        }
    }
    
    public int strokeAddPoint(MemorySegment stroke, float x, float y, float pressure, float timestamp) {
        try {
            return (int) strokeAddPointHandle.invokeExact(stroke, x, y, pressure, timestamp);
        } catch (Throwable t) {
            throw new RuntimeException("strokeAddPoint failed", t);
        }
    }
    
    public void strokeClear(MemorySegment stroke) {
        try {
            strokeClearHandle.invokeExact(stroke);
        } catch (Throwable t) {
            throw new RuntimeException("strokeClear failed", t);
        }
    }
    
    public int strokeSmooth(MemorySegment stroke, int iterations, float tension, float resampleDist, boolean preserveCorners) {
        try {
            return (int) strokeSmoothHandle.invokeExact(stroke, iterations, tension, resampleDist, preserveCorners ? 1 : 0);
        } catch (Throwable t) {
            throw new RuntimeException("strokeSmooth failed", t);
        }
    }
    
    public float strokeLength(MemorySegment stroke) {
        try {
            return (float) strokeLengthHandle.invokeExact(stroke);
        } catch (Throwable t) {
            throw new RuntimeException("strokeLength failed", t);
        }
    }

    public int strokeGetPointCount(MemorySegment stroke) {
        try {
            return (int) strokeGetPointCountHandle.invokeExact(stroke);
        } catch (Throwable t) {
            throw new RuntimeException("strokeGetPointCount failed", t);
        }
    }

    public int strokeGetPoints(MemorySegment stroke, MemorySegment out, int outLen) {
        try {
            return (int) strokeGetPointsHandle.invokeExact(stroke, out, outLen);
        } catch (Throwable t) {
            throw new RuntimeException("strokeGetPoints failed", t);
        }
    }

    public void strokeTranslate(MemorySegment stroke, float dx, float dy) {
        try {
            strokeTranslateHandle.invokeExact(stroke, dx, dy);
        } catch (Throwable t) {
            throw new RuntimeException("strokeTranslate failed", t);
        }
    }
    
    public void strokeScale(MemorySegment stroke, float sx, float sy, float cx, float cy) {
        try {
            strokeScaleHandle.invokeExact(stroke, sx, sy, cx, cy);
        } catch (Throwable t) {
            throw new RuntimeException("strokeScale failed", t);
        }
    }
    
    public void strokeNormalize(MemorySegment stroke, float targetSize) {
        try {
            strokeNormalizeHandle.invokeExact(stroke, targetSize);
        } catch (Throwable t) {
            throw new RuntimeException("strokeNormalize failed", t);
        }
    }
    
    public int strokeSimplify(MemorySegment stroke, float epsilon) {
        try {
            return (int) strokeSimplifyHandle.invokeExact(stroke, epsilon);
        } catch (Throwable t) {
            throw new RuntimeException("strokeSimplify failed", t);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RASTERIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    public MemorySegment bitmapCreate(int width, int height) {
        try {
            return (MemorySegment) bitmapCreateHandle.invokeExact(width, height);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapCreate failed", t);
        }
    }
    
    public void bitmapFree(MemorySegment bitmap) {
        try {
            bitmapFreeHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapFree failed", t);
        }
    }
    
    public void bitmapClear(MemorySegment bitmap) {
        try {
            bitmapClearHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapClear failed", t);
        }
    }

    public int bitmapGetWidth(MemorySegment bitmap) {
        try {
            return (int) bitmapGetWidthHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapGetWidth failed", t);
        }
    }

    public int bitmapGetHeight(MemorySegment bitmap) {
        try {
            return (int) bitmapGetHeightHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapGetHeight failed", t);
        }
    }

    public int bitmapGetStride(MemorySegment bitmap) {
        try {
            return (int) bitmapGetStrideHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapGetStride failed", t);
        }
    }

    public MemorySegment bitmapGetPixels(MemorySegment bitmap) {
        try {
            return (MemorySegment) bitmapGetPixelsHandle.invokeExact(bitmap);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapGetPixels failed", t);
        }
    }

    public int bitmapToArgb(MemorySegment bitmap, int color, MemorySegment outArgb, int outStride) {
        try {
            return (int) bitmapToArgbHandle.invokeExact(bitmap, color, outArgb, outStride);
        } catch (Throwable t) {
            throw new RuntimeException("bitmapToArgb failed", t);
        }
    }
    
    public MemorySegment rasterGlyph(MemorySegment glyph, int size, int oversample, float gamma, float emSize) {
        try {
            return (MemorySegment) rasterGlyphHandle.invokeExact(glyph, size, oversample, gamma, emSize);
        } catch (Throwable t) {
            throw new RuntimeException("rasterGlyph failed", t);
        }
    }
    
    public int rasterStroke(MemorySegment bitmap, MemorySegment stroke, float scale, float offsetX, float offsetY) {
        try {
            return (int) rasterStrokeHandle.invokeExact(bitmap, stroke, scale, offsetX, offsetY);
        } catch (Throwable t) {
            throw new RuntimeException("rasterStroke failed", t);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    public int fontPack(MemorySegment font, MemorySegment buffer, int bufferLen) {
        try {
            return (int) fontPackHandle.invokeExact(font, buffer, bufferLen);
        } catch (Throwable t) {
            throw new RuntimeException("fontPack failed", t);
        }
    }
    
    public MemorySegment fontUnpack(MemorySegment buffer, int bufferLen) {
        try {
            return (MemorySegment) fontUnpackHandle.invokeExact(buffer, bufferLen);
        } catch (Throwable t) {
            throw new RuntimeException("fontUnpack failed", t);
        }
    }
}
