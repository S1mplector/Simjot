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
 * FFM bindings for native high-performance drawing operations.
 * 
 * Provides native acceleration for:
 * - Stroke rendering to pixel buffers
 * - Eraser hit testing
 * - Stroke geometry calculations
 * - Point array operations
 * 
 * @author S1mplector
 */
public final class NativeDrawing {
    
    private final Arena arena;
    private final SymbolLookup lookup;
    private final Linker linker;
    
    // Method handles
    private final MethodHandle drawStrokeHandle;
    private final MethodHandle drawStrokesBatchHandle;
    private final MethodHandle eraserHitTestHandle;
    private final MethodHandle eraserHitTestBatchHandle;
    private final MethodHandle eraserHitTestArrayHandle;
    private final MethodHandle strokeCalcBoundsHandle;
    private final MethodHandle strokeCalcLengthHandle;
    private final MethodHandle strokeSimplifyRdpHandle;
    private final MethodHandle pointsIntToFloatHandle;
    private final MethodHandle bufferClearRectHandle;
    private final MethodHandle smoothPointEmaHandle;
    private final MethodHandle smoothStrokeEmaHandle;
    private final MethodHandle smoothStrokeAdaptiveHandle;
    
    public NativeDrawing(SymbolLookup lookup) {
        this.arena = Arena.ofShared();
        this.lookup = lookup;
        this.linker = Linker.nativeLinker();
        
        // simjot_draw_stroke
        drawStrokeHandle = downcall("simjot_draw_stroke", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_draw_strokes_batch
        drawStrokesBatchHandle = downcall("simjot_draw_strokes_batch", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_eraser_hit_test
        eraserHitTestHandle = downcall("simjot_eraser_hit_test", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_eraser_hit_test_batch
        eraserHitTestBatchHandle = downcall("simjot_eraser_hit_test_batch", FunctionDescriptor.of(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_eraser_hit_test_array
        eraserHitTestArrayHandle = downcall("simjot_eraser_hit_test_array", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT
        ));
        
        // simjot_stroke_calc_bounds
        strokeCalcBoundsHandle = downcall("simjot_stroke_calc_bounds", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
        ));
        
        // simjot_stroke_calc_length
        strokeCalcLengthHandle = downcall("simjot_stroke_calc_length", FunctionDescriptor.of(
            ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
        ));
        
        // simjot_stroke_simplify_rdp
        strokeSimplifyRdpHandle = downcall("simjot_stroke_simplify_rdp", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_points_int_to_float
        pointsIntToFloatHandle = downcall("simjot_points_int_to_float", FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS
        ));
        
        // simjot_buffer_clear_rect
        bufferClearRectHandle = downcall("simjot_buffer_clear_rect", FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_smooth_point_ema
        smoothPointEmaHandle = downcall("simjot_smooth_point_ema", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_smooth_stroke_ema
        smoothStrokeEmaHandle = downcall("simjot_smooth_stroke_ema", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_smooth_stroke_adaptive
        smoothStrokeAdaptiveHandle = downcall("simjot_smooth_stroke_adaptive", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
    }
    
    private MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return lookup.find(name)
            .map(addr -> linker.downcallHandle(addr, descriptor))
            .orElse(null);
    }
    
    public boolean isAvailable() {
        return drawStrokeHandle != null && eraserHitTestHandle != null;
    }
    
    /**
     * Render a stroke to an ARGB pixel buffer.
     * 
     * @param pixels Direct int[] buffer (TYPE_INT_ARGB)
     * @param width Buffer width
     * @param height Buffer height
     * @param pointsX X coordinates
     * @param pointsY Y coordinates
     * @param thickness Stroke thickness
     * @param argbColor ARGB color
     * @param offsetX X offset (viewport position)
     * @param offsetY Y offset (viewport position)
     * @return true if successful
     */
    public boolean drawStroke(int[] pixels, int width, int height,
                               float[] pointsX, float[] pointsY,
                               float thickness, int argbColor,
                               float offsetX, float offsetY) {
        if (drawStrokeHandle == null || pointsX.length == 0) return false;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = local.allocate(ValueLayout.JAVA_INT, pixels.length);
            for (int i = 0; i < pixels.length; i++) pixelsSeg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            
            MemorySegment xSeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsX.length);
            MemorySegment ySeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsY.length);
            for (int i = 0; i < pointsX.length; i++) {
                xSeg.setAtIndex(ValueLayout.JAVA_FLOAT, i, pointsX[i]);
                ySeg.setAtIndex(ValueLayout.JAVA_FLOAT, i, pointsY[i]);
            }
            
            int result = (int) drawStrokeHandle.invokeExact(
                pixelsSeg, width, height,
                xSeg, ySeg,
                pointsX.length, thickness, argbColor,
                offsetX, offsetY
            );
            
            // Copy back to pixels array
            for (int i = 0; i < pixels.length; i++) pixels[i] = pixelsSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Render multiple strokes to an ARGB pixel buffer in batch.
     */
    public boolean drawStrokesBatch(int[] pixels, int width, int height,
                                     float[] allPointsX, float[] allPointsY,
                                     int[] strokeStarts, int[] strokeLengths,
                                     float[] thicknesses, int[] colors,
                                     float offsetX, float offsetY) {
        if (drawStrokesBatchHandle == null || strokeStarts.length == 0) return false;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = allocateIntArray(local, pixels);
            MemorySegment xSeg = allocateFloatArray(local, allPointsX);
            MemorySegment ySeg = allocateFloatArray(local, allPointsY);
            MemorySegment startsSeg = allocateIntArray(local, strokeStarts);
            MemorySegment lengthsSeg = allocateIntArray(local, strokeLengths);
            MemorySegment thickSeg = allocateFloatArray(local, thicknesses);
            MemorySegment colorsSeg = allocateIntArray(local, colors);
            
            int result = (int) drawStrokesBatchHandle.invokeExact(
                pixelsSeg, width, height,
                xSeg, ySeg,
                startsSeg, lengthsSeg,
                thickSeg, colorsSeg,
                strokeStarts.length,
                offsetX, offsetY
            );
            
            // Copy back
            copyBackIntArray(pixelsSeg, pixels);
            
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Test if eraser at (px, py) hits any stroke.
     * 
     * @return Index of first hit stroke, or -1 if no hit
     */
    public int eraserHitTest(float px, float py, float radiusSq,
                              float[] allPointsX, float[] allPointsY,
                              int[] strokeStarts, int[] strokeLengths) {
        if (eraserHitTestHandle == null || strokeStarts.length == 0) return -1;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, allPointsX);
            MemorySegment ySeg = allocateFloatArray(local, allPointsY);
            MemorySegment startsSeg = allocateIntArray(local, strokeStarts);
            MemorySegment lengthsSeg = allocateIntArray(local, strokeLengths);
            
            return (int) eraserHitTestHandle.invokeExact(
                px, py, radiusSq,
                xSeg, ySeg,
                startsSeg, lengthsSeg,
                strokeStarts.length
            );
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Batch hit test - returns bitmask of which strokes were hit (up to 64 strokes).
     */
    public long eraserHitTestBatch(float px, float py, float radiusSq,
                                    float[] allPointsX, float[] allPointsY,
                                    int[] strokeStarts, int[] strokeLengths) {
        if (eraserHitTestBatchHandle == null || strokeStarts.length == 0) return 0L;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, allPointsX);
            MemorySegment ySeg = allocateFloatArray(local, allPointsY);
            MemorySegment startsSeg = allocateIntArray(local, strokeStarts);
            MemorySegment lengthsSeg = allocateIntArray(local, strokeLengths);
            
            return (long) eraserHitTestBatchHandle.invokeExact(
                px, py, radiusSq,
                xSeg, ySeg,
                startsSeg, lengthsSeg,
                strokeStarts.length
            );
        } catch (Throwable t) {
            return 0L;
        }
    }
    
    /**
     * Hit test for unlimited strokes - returns array of hit indices.
     */
    public int[] eraserHitTestArray(float px, float py, float radiusSq,
                                     float[] allPointsX, float[] allPointsY,
                                     int[] strokeStarts, int[] strokeLengths,
                                     int maxHits) {
        if (eraserHitTestArrayHandle == null || strokeStarts.length == 0) return new int[0];
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, allPointsX);
            MemorySegment ySeg = allocateFloatArray(local, allPointsY);
            MemorySegment startsSeg = allocateIntArray(local, strokeStarts);
            MemorySegment lengthsSeg = allocateIntArray(local, strokeLengths);
            MemorySegment hitsSeg = local.allocate(ValueLayout.JAVA_INT, maxHits);
            
            int hitCount = (int) eraserHitTestArrayHandle.invokeExact(
                px, py, radiusSq,
                xSeg, ySeg,
                startsSeg, lengthsSeg,
                strokeStarts.length,
                hitsSeg, maxHits
            );
            
            if (hitCount <= 0) return new int[0];
            
            int[] result = new int[hitCount];
            for (int i = 0; i < hitCount; i++) result[i] = hitsSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            return result;
        } catch (Throwable t) {
            return new int[0];
        }
    }
    
    /**
     * Calculate stroke bounding box.
     * 
     * @return float[4] = {minX, minY, maxX, maxY} or null on error
     */
    public float[] strokeCalcBounds(float[] pointsX, float[] pointsY) {
        if (strokeCalcBoundsHandle == null || pointsX.length == 0) return null;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, pointsX);
            MemorySegment ySeg = allocateFloatArray(local, pointsY);
            MemorySegment minXSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment minYSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment maxXSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment maxYSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            
            int result = (int) strokeCalcBoundsHandle.invokeExact(
                xSeg, ySeg, pointsX.length,
                minXSeg, minYSeg, maxXSeg, maxYSeg
            );
            
            if (result == 0) return null;
            
            return new float[] {
                minXSeg.get(ValueLayout.JAVA_FLOAT, 0),
                minYSeg.get(ValueLayout.JAVA_FLOAT, 0),
                maxXSeg.get(ValueLayout.JAVA_FLOAT, 0),
                maxYSeg.get(ValueLayout.JAVA_FLOAT, 0)
            };
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Calculate total arc length of stroke.
     */
    public float strokeCalcLength(float[] pointsX, float[] pointsY) {
        if (strokeCalcLengthHandle == null || pointsX.length < 2) return 0f;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, pointsX);
            MemorySegment ySeg = allocateFloatArray(local, pointsY);
            
            return (float) strokeCalcLengthHandle.invokeExact(xSeg, ySeg, pointsX.length);
        } catch (Throwable t) {
            return 0f;
        }
    }
    
    /**
     * Simplify stroke using Ramer-Douglas-Peucker algorithm.
     * 
     * @return Simplified points as float[2][n] = {{x0,x1,...}, {y0,y1,...}} or null
     */
    public float[][] strokeSimplify(float[] pointsX, float[] pointsY, float epsilon) {
        if (strokeSimplifyRdpHandle == null || pointsX.length < 2) return null;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment inXSeg = allocateFloatArray(local, pointsX);
            MemorySegment inYSeg = allocateFloatArray(local, pointsY);
            MemorySegment outXSeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsX.length);
            MemorySegment outYSeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsY.length);
            
            int outCount = (int) strokeSimplifyRdpHandle.invokeExact(
                inXSeg, inYSeg, pointsX.length,
                outXSeg, outYSeg, pointsX.length,
                epsilon
            );
            
            if (outCount <= 0) return null;
            
            float[] outX = new float[outCount];
            float[] outY = new float[outCount];
            for (int i = 0; i < outCount; i++) {
                outX[i] = outXSeg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
                outY[i] = outYSeg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
            }
            
            return new float[][] { outX, outY };
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Clear a rectangular region of a pixel buffer.
     */
    public void bufferClearRect(int[] pixels, int width, int height,
                                 int x, int y, int w, int h, int color) {
        if (bufferClearRectHandle == null) return;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = allocateIntArray(local, pixels);
            
            bufferClearRectHandle.invokeExact(pixelsSeg, width, height, x, y, w, h, color);
            
            // Copy back
            copyBackIntArray(pixelsSeg, pixels);
        } catch (Throwable t) {
            // Ignore
        }
    }
    
    // Helper methods for array allocation
    private static MemorySegment allocateIntArray(Arena arena, int[] arr) {
        MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT, arr.length);
        for (int i = 0; i < arr.length; i++) seg.setAtIndex(ValueLayout.JAVA_INT, i, arr[i]);
        return seg;
    }
    
    private static MemorySegment allocateFloatArray(Arena arena, float[] arr) {
        MemorySegment seg = arena.allocate(ValueLayout.JAVA_FLOAT, arr.length);
        for (int i = 0; i < arr.length; i++) seg.setAtIndex(ValueLayout.JAVA_FLOAT, i, arr[i]);
        return seg;
    }
    
    private static void copyBackIntArray(MemorySegment seg, int[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = seg.getAtIndex(ValueLayout.JAVA_INT, i);
    }
    
    private static void copyBackFloatArray(MemorySegment seg, float[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = seg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
    }
    
    /**
     * Smooth a single point using EMA - call per incoming point.
     * 
     * @param newX, newY   New raw input point
     * @param prevX, prevY Previous smoothed point
     * @param alpha        Smoothing factor (0.5-0.8 for stylus)
     * @return float[2] = {smoothedX, smoothedY}
     */
    public float[] smoothPointEma(float newX, float newY, float prevX, float prevY, float alpha) {
        if (smoothPointEmaHandle == null) {
            // Fallback to Java implementation
            float smoothX = alpha * newX + (1f - alpha) * prevX;
            float smoothY = alpha * newY + (1f - alpha) * prevY;
            return new float[] { smoothX, smoothY };
        }
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment outXSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment outYSeg = local.allocate(ValueLayout.JAVA_FLOAT);
            
            smoothPointEmaHandle.invokeExact(newX, newY, prevX, prevY, outXSeg, outYSeg, alpha);
            
            return new float[] {
                outXSeg.get(ValueLayout.JAVA_FLOAT, 0),
                outYSeg.get(ValueLayout.JAVA_FLOAT, 0)
            };
        } catch (Throwable t) {
            // Fallback
            float smoothX = alpha * newX + (1f - alpha) * prevX;
            float smoothY = alpha * newY + (1f - alpha) * prevY;
            return new float[] { smoothX, smoothY };
        }
    }
    
    /**
     * Smooth entire stroke in-place using bidirectional EMA.
     * 
     * @param pointsX, pointsY Point arrays (modified in-place)
     * @param alpha            Smoothing factor (0.3-0.7 recommended)
     * @return true on success
     */
    public boolean smoothStrokeEma(float[] pointsX, float[] pointsY, float alpha) {
        if (smoothStrokeEmaHandle == null || pointsX.length < 2) return false;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, pointsX);
            MemorySegment ySeg = allocateFloatArray(local, pointsY);
            
            int result = (int) smoothStrokeEmaHandle.invokeExact(xSeg, ySeg, pointsX.length, alpha);
            
            if (result != 0) {
                copyBackFloatArray(xSeg, pointsX);
                copyBackFloatArray(ySeg, pointsY);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Smooth stroke with velocity-adaptive alpha.
     * Faster movements = less smoothing, slower = more smoothing.
     * 
     * @param pointsX, pointsY  Point arrays (modified in-place)
     * @param baseAlpha         Base smoothing (0.3-0.6 recommended)
     * @param velocityScale     Velocity influence (0.01-0.05 recommended)
     * @return true on success
     */
    public boolean smoothStrokeAdaptive(float[] pointsX, float[] pointsY, 
                                         float baseAlpha, float velocityScale) {
        if (smoothStrokeAdaptiveHandle == null || pointsX.length < 2) return false;
        
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, pointsX);
            MemorySegment ySeg = allocateFloatArray(local, pointsY);
            
            int result = (int) smoothStrokeAdaptiveHandle.invokeExact(
                xSeg, ySeg, pointsX.length, baseAlpha, velocityScale);
            
            if (result != 0) {
                copyBackFloatArray(xSeg, pointsX);
                copyBackFloatArray(ySeg, pointsY);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get singleton instance if native library is available.
     */
    public static NativeDrawing getInstance() {
        SymbolLookup lookup = NativeAccess.symbolLookup();
        if (lookup == null) return null;
        
        try {
            NativeDrawing instance = new NativeDrawing(lookup);
            return instance.isAvailable() ? instance : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
