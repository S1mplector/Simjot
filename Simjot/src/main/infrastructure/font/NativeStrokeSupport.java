/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.font;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.List;

import main.core.font.CustomStroke;
import main.core.font.StrokePoint;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeFonts;

/**
 * Native-backed helpers for expensive stroke operations used by the font studio.
 */
public final class NativeStrokeSupport {
    private static final Object LOCK = new Object();
    private static volatile NativeFonts nfInstance;
    private static volatile boolean attempted;

    private NativeStrokeSupport() {}

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
        NativeFonts fonts = nf();
        return fonts != null && fonts.hasStrokePointAccess();
    }

    public static boolean smoothStroke(CustomStroke stroke, int iterations, float tension,
                                       float resampleDist, boolean preserveCorners) {
        if (stroke == null) return false;
        List<StrokePoint> points = stroke.getPoints();
        if (points.size() < 2) return false;

        NativeFonts fonts = nf();
        if (fonts == null || !fonts.hasStrokePointAccess()) return false;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeStroke = fonts.strokeCreate(Math.max(16, points.size()));
            if (nativeStroke == null || nativeStroke.equals(MemorySegment.NULL)) return false;
            try {
                if (fonts.hasStrokeSetThickness()) {
                    fonts.strokeSetThickness(nativeStroke, stroke.getThickness());
                }
                if (!uploadPoints(fonts, arena, nativeStroke, points)) return false;

                int result = fonts.strokeSmooth(nativeStroke, iterations, tension, resampleDist, preserveCorners);
                if (result < 0) return false;

                int pointCount = fonts.strokeGetPointCount(nativeStroke);
                if (pointCount <= 0) return false;

                int floatCount = pointCount * 4;
                MemorySegment out = arena.allocate((long) floatCount * ValueLayout.JAVA_FLOAT.byteSize());
                int written = fonts.strokeGetPoints(nativeStroke, out, floatCount);
                if (written < 0) return false;

                float[] values = new float[floatCount];
                MemorySegment.copy(out, ValueLayout.JAVA_FLOAT, 0, values, 0, floatCount);

                stroke.clear();
                for (int i = 0; i < pointCount; i++) {
                    int base = i * 4;
                    stroke.addPoint(values[base], values[base + 1], values[base + 2], values[base + 3]);
                }
                return true;
            } finally {
                fonts.strokeFree(nativeStroke);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean uploadPoints(NativeFonts fonts, Arena arena, MemorySegment stroke, List<StrokePoint> points) {
        int count = points.size();
        if (count == 0) return true;
        if (fonts.hasStrokeSetPoints()) {
            MemorySegment xs = arena.allocate(ValueLayout.JAVA_FLOAT, count);
            MemorySegment ys = arena.allocate(ValueLayout.JAVA_FLOAT, count);
            MemorySegment pressures = arena.allocate(ValueLayout.JAVA_FLOAT, count);
            MemorySegment timestamps = arena.allocate(ValueLayout.JAVA_FLOAT, count);
            for (int i = 0; i < count; i++) {
                StrokePoint p = points.get(i);
                xs.setAtIndex(ValueLayout.JAVA_FLOAT, i, p.getX());
                ys.setAtIndex(ValueLayout.JAVA_FLOAT, i, p.getY());
                pressures.setAtIndex(ValueLayout.JAVA_FLOAT, i, p.getPressure());
                timestamps.setAtIndex(ValueLayout.JAVA_FLOAT, i, p.getTimestamp());
            }
            int result = fonts.strokeSetPoints(stroke, xs, ys, pressures, timestamps, count);
            return result >= 0;
        }
        for (StrokePoint p : points) {
            int result = fonts.strokeAddPoint(stroke, p.getX(), p.getY(), p.getPressure(), p.getTimestamp());
            if (result < 0) return false;
        }
        return true;
    }
}
