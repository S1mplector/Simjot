/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
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
    
    // Method handles - basic drawing
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
    
    // Method handles - professional stroke engine
    private final MethodHandle strokeManagerCreateHandle;
    private final MethodHandle strokeManagerDestroyHandle;
    private final MethodHandle strokeBeginHandle;
    private final MethodHandle strokeAddPointHandle;
    private final MethodHandle strokeEndHandle;
    private final MethodHandle strokeRemoveHandle;
    private final MethodHandle strokeCountHandle;
    private final MethodHandle strokeClearAllHandle;
    private final MethodHandle strokeRenderAllHandle;
    private final MethodHandle strokeRenderOneHandle;
    private final MethodHandle strokeInterpolateCatmullRomHandle;
    private final MethodHandle strokeSmoothChaikinHandle;
    private final MethodHandle strokeComputePressureHandle;
    private final MethodHandle strokeRenderVariableHandle;
    private final MethodHandle strokeDistanceSampleHandle;
    
    // Lasso selector
    private final MethodHandle lassoPointInPolygonHandle;
    private final MethodHandle lassoTestStrokeHandle;
    private final MethodHandle lassoComputeBoundsHandle;
    private final MethodHandle lassoTranslatePointsHandle;
    
    // Stroke optimizer (quadtree spatial index, dirty tracking)
    private final MethodHandle optimizerCreateHandle;
    private final MethodHandle optimizerDestroyHandle;
    private final MethodHandle optimizerAddStrokeHandle;
    private final MethodHandle optimizerRemoveStrokeHandle;
    private final MethodHandle optimizerMoveStrokeHandle;
    private final MethodHandle optimizerQueryVisibleHandle;
    private final MethodHandle optimizerQueryPointHandle;
    private final MethodHandle optimizerStrokeCountHandle;
    private final MethodHandle optimizerNeedsRepaintHandle;
    private final MethodHandle optimizerClearDirtyHandle;
    private final MethodHandle optimizerRenderVisibleHandle;
    
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
        
        // ═══════════════════════════════════════════════════════════════════════════
        // PROFESSIONAL STROKE ENGINE
        // ═══════════════════════════════════════════════════════════════════════════
        
        // simjot_draw_manager_create
        strokeManagerCreateHandle = downcall("simjot_draw_manager_create", FunctionDescriptor.of(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_manager_destroy
        strokeManagerDestroyHandle = downcall("simjot_draw_manager_destroy", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG
        ));
        
        // simjot_draw_stroke_begin
        strokeBeginHandle = downcall("simjot_draw_stroke_begin", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_stroke_add_point
        strokeAddPointHandle = downcall("simjot_draw_stroke_add_point", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_LONG
        ));
        
        // simjot_draw_stroke_end
        strokeEndHandle = downcall("simjot_draw_stroke_end", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_stroke_remove
        strokeRemoveHandle = downcall("simjot_draw_stroke_remove", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_stroke_count
        strokeCountHandle = downcall("simjot_draw_stroke_count", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG
        ));
        
        // simjot_draw_stroke_clear_all
        strokeClearAllHandle = downcall("simjot_draw_stroke_clear_all", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG
        ));
        
        // simjot_draw_render_all
        strokeRenderAllHandle = downcall("simjot_draw_render_all", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_draw_render_one
        strokeRenderOneHandle = downcall("simjot_draw_render_one", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_draw_interpolate_catmull_rom
        strokeInterpolateCatmullRomHandle = downcall("simjot_draw_interpolate_catmull_rom", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_smooth_chaikin
        strokeSmoothChaikinHandle = downcall("simjot_draw_smooth_chaikin", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_draw_compute_pressure
        strokeComputePressureHandle = downcall("simjot_draw_compute_pressure", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS
        ));
        
        // simjot_draw_render_variable
        strokeRenderVariableHandle = downcall("simjot_draw_render_variable", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // simjot_draw_distance_sample
        strokeDistanceSampleHandle = downcall("simjot_draw_distance_sample", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT
        ));
        
        // ═══════════════════════════════════════════════════════════════════════════
        // LASSO SELECTOR
        // ═══════════════════════════════════════════════════════════════════════════
        
        // simjot_lasso_point_in_polygon
        lassoPointInPolygonHandle = downcall("simjot_lasso_point_in_polygon", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
        ));
        
        // simjot_lasso_test_stroke
        lassoTestStrokeHandle = downcall("simjot_lasso_test_stroke", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT
        ));
        
        // simjot_lasso_compute_bounds
        lassoComputeBoundsHandle = downcall("simjot_lasso_compute_bounds", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
        ));
        
        // simjot_lasso_translate_points
        lassoTranslatePointsHandle = downcall("simjot_lasso_translate_points", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STROKE OPTIMIZER (quadtree, dirty tracking, batching)
        // ═══════════════════════════════════════════════════════════════════════════
        
        optimizerCreateHandle = downcall("simjot_optimizer_create", FunctionDescriptor.of(
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        optimizerDestroyHandle = downcall("simjot_optimizer_destroy", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG
        ));
        
        optimizerAddStrokeHandle = downcall("simjot_optimizer_add_stroke", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT
        ));
        
        optimizerRemoveStrokeHandle = downcall("simjot_optimizer_remove_stroke", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT
        ));
        
        optimizerMoveStrokeHandle = downcall("simjot_optimizer_move_stroke", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        optimizerQueryVisibleHandle = downcall("simjot_optimizer_query_visible", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT
        ));
        
        optimizerQueryPointHandle = downcall("simjot_optimizer_query_point", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT
        ));
        
        optimizerStrokeCountHandle = downcall("simjot_optimizer_stroke_count", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG
        ));
        
        optimizerNeedsRepaintHandle = downcall("simjot_optimizer_needs_repaint", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
        ));
        
        optimizerClearDirtyHandle = downcall("simjot_optimizer_clear_dirty", FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_LONG
        ));
        
        optimizerRenderVisibleHandle = downcall("simjot_optimizer_render_visible", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROFESSIONAL STROKE ENGINE API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Create a stroke manager for handling multiple strokes with caching.
     * @return Handle to the stroke manager, or 0 on error
     */
    public long strokeManagerCreate(int docWidth, int docHeight) {
        if (strokeManagerCreateHandle == null) return 0;
        try {
            return (long) strokeManagerCreateHandle.invokeExact(docWidth, docHeight);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Destroy stroke manager and free resources.
     */
    public void strokeManagerDestroy(long handle) {
        if (strokeManagerDestroyHandle == null || handle == 0) return;
        try {
            strokeManagerDestroyHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Begin a new stroke with given properties.
     * @return Stroke index, or -1 on error
     */
    public int strokeBegin(long handle, float x, float y, long timestamp,
                           float baseThickness, int color, boolean usePressure) {
        if (strokeBeginHandle == null || handle == 0) return -1;
        try {
            return (int) strokeBeginHandle.invokeExact(handle, x, y, timestamp,
                baseThickness, color, usePressure ? 1 : 0);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Add a point to the current stroke with real-time smoothing.
     * @return 1 if added, 0 if skipped (too close), -1 on error
     */
    public int strokeAddPoint(long handle, int strokeIdx, float x, float y, long timestamp) {
        if (strokeAddPointHandle == null || handle == 0) return -1;
        try {
            return (int) strokeAddPointHandle.invokeExact(handle, strokeIdx, x, y, timestamp);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * End the current stroke and apply final smoothing.
     */
    public int strokeEnd(long handle, int strokeIdx, boolean applySmoothing) {
        if (strokeEndHandle == null || handle == 0) return -1;
        try {
            return (int) strokeEndHandle.invokeExact(handle, strokeIdx, applySmoothing ? 1 : 0);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Remove a stroke by index.
     */
    public int strokeRemove(long handle, int strokeIdx) {
        if (strokeRemoveHandle == null || handle == 0) return -1;
        try {
            return (int) strokeRemoveHandle.invokeExact(handle, strokeIdx);
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Get stroke count.
     */
    public int strokeCount(long handle) {
        if (strokeCountHandle == null || handle == 0) return 0;
        try {
            return (int) strokeCountHandle.invokeExact(handle);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Clear all strokes.
     */
    public void strokeClearAll(long handle) {
        if (strokeClearAllHandle == null || handle == 0) return;
        try {
            strokeClearAllHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Render all strokes to a pixel buffer with variable thickness.
     */
    public boolean strokeRenderAll(long handle, int[] pixels, int width, int height,
                                    float offsetX, float offsetY) {
        if (strokeRenderAllHandle == null || handle == 0 || pixels == null) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = allocateIntArray(local, pixels);
            int result = (int) strokeRenderAllHandle.invokeExact(handle, pixelsSeg,
                width, height, offsetX, offsetY);
            if (result != 0) {
                copyBackIntArray(pixelsSeg, pixels);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Render a single stroke by index.
     */
    public boolean strokeRenderOne(long handle, int strokeIdx, int[] pixels,
                                    int width, int height, float offsetX, float offsetY) {
        if (strokeRenderOneHandle == null || handle == 0 || pixels == null) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = allocateIntArray(local, pixels);
            int result = (int) strokeRenderOneHandle.invokeExact(handle, strokeIdx,
                pixelsSeg, width, height, offsetX, offsetY);
            if (result != 0) {
                copyBackIntArray(pixelsSeg, pixels);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Check if the professional stroke engine is available.
     */
    public boolean isStrokeEngineAvailable() {
        return strokeManagerCreateHandle != null && strokeRenderAllHandle != null;
    }
    
    /**
     * Render stroke with variable thickness per point.
     */
    public boolean strokeRenderVariable(int[] pixels, int width, int height,
                                         float[] pointsX, float[] pointsY, float[] thicknesses,
                                         int argbColor, float offsetX, float offsetY) {
        if (strokeRenderVariableHandle == null || pixels == null || pointsX.length == 0) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelsSeg = allocateIntArray(local, pixels);
            MemorySegment xSeg = allocateFloatArray(local, pointsX);
            MemorySegment ySeg = allocateFloatArray(local, pointsY);
            MemorySegment tSeg = allocateFloatArray(local, thicknesses);
            
            int result = (int) strokeRenderVariableHandle.invokeExact(
                pixelsSeg, width, height,
                xSeg, ySeg, tSeg,
                pointsX.length, argbColor,
                offsetX, offsetY
            );
            
            if (result != 0) {
                copyBackIntArray(pixelsSeg, pixels);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Apply distance-based sampling to reduce jitter.
     * @return Simplified points as float[2][n] or null
     */
    public float[][] strokeDistanceSample(float[] pointsX, float[] pointsY, float minDistance) {
        if (strokeDistanceSampleHandle == null || pointsX.length < 2) return null;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment inXSeg = allocateFloatArray(local, pointsX);
            MemorySegment inYSeg = allocateFloatArray(local, pointsY);
            MemorySegment outXSeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsX.length);
            MemorySegment outYSeg = local.allocate(ValueLayout.JAVA_FLOAT, pointsY.length);
            
            int outCount = (int) strokeDistanceSampleHandle.invokeExact(
                inXSeg, inYSeg, pointsX.length,
                outXSeg, outYSeg, pointsX.length,
                minDistance
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
     * Apply Chaikin smoothing for post-stroke polish.
     * @return Smoothed points as float[2][n] or null
     */
    public float[][] strokeSmoothChaikin(float[] pointsX, float[] pointsY, int iterations) {
        if (strokeSmoothChaikinHandle == null || pointsX.length < 3) return null;
        int maxOutput = pointsX.length * (1 << iterations) + 2;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment inXSeg = allocateFloatArray(local, pointsX);
            MemorySegment inYSeg = allocateFloatArray(local, pointsY);
            MemorySegment outXSeg = local.allocate(ValueLayout.JAVA_FLOAT, maxOutput);
            MemorySegment outYSeg = local.allocate(ValueLayout.JAVA_FLOAT, maxOutput);
            
            int outCount = (int) strokeSmoothChaikinHandle.invokeExact(
                inXSeg, inYSeg, pointsX.length,
                outXSeg, outYSeg, maxOutput,
                iterations
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LASSO SELECTOR API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if lasso functions are available.
     */
    public boolean isLassoAvailable() {
        return lassoTestStrokeHandle != null && lassoComputeBoundsHandle != null;
    }
    
    /**
     * Test if a point is inside a lasso polygon.
     */
    public boolean lassoPointInPolygon(float px, float py, float[] polyX, float[] polyY) {
        if (lassoPointInPolygonHandle == null || polyX.length < 3) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment polyXSeg = allocateFloatArray(local, polyX);
            MemorySegment polyYSeg = allocateFloatArray(local, polyY);
            int result = (int) lassoPointInPolygonHandle.invokeExact(
                px, py, polyXSeg, polyYSeg, polyX.length
            );
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Test if a stroke intersects with a lasso polygon.
     * @param mode 0=any point, 1=all points, 2=centroid
     */
    public boolean lassoTestStroke(float[] strokeX, float[] strokeY,
                                    float[] lassoX, float[] lassoY, int mode) {
        if (lassoTestStrokeHandle == null || strokeX.length < 1 || lassoX.length < 3) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment strokeXSeg = allocateFloatArray(local, strokeX);
            MemorySegment strokeYSeg = allocateFloatArray(local, strokeY);
            MemorySegment lassoXSeg = allocateFloatArray(local, lassoX);
            MemorySegment lassoYSeg = allocateFloatArray(local, lassoY);
            int result = (int) lassoTestStrokeHandle.invokeExact(
                strokeXSeg, strokeYSeg, strokeX.length,
                lassoXSeg, lassoYSeg, lassoX.length,
                mode
            );
            return result != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Compute bounding box of points.
     * @return float[4] = {minX, minY, maxX, maxY} or null
     */
    public float[] lassoComputeBounds(float[] x, float[] y) {
        if (lassoComputeBoundsHandle == null || x.length < 1) return null;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, x);
            MemorySegment ySeg = allocateFloatArray(local, y);
            MemorySegment boundsSeg = local.allocate(ValueLayout.JAVA_FLOAT, 4);
            int result = (int) lassoComputeBoundsHandle.invokeExact(xSeg, ySeg, x.length, boundsSeg);
            if (result == 0) return null;
            float[] bounds = new float[4];
            for (int i = 0; i < 4; i++) {
                bounds[i] = boundsSeg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
            }
            return bounds;
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Translate points by delta. Modifies arrays in-place.
     */
    public boolean lassoTranslatePoints(float[] x, float[] y, float dx, float dy) {
        if (lassoTranslatePointsHandle == null || x.length < 1) return false;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, x);
            MemorySegment ySeg = allocateFloatArray(local, y);
            int result = (int) lassoTranslatePointsHandle.invokeExact(xSeg, ySeg, x.length, dx, dy);
            if (result == 0) return false;
            // Copy back modified values
            for (int i = 0; i < x.length; i++) {
                x[i] = xSeg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
                y[i] = ySeg.getAtIndex(ValueLayout.JAVA_FLOAT, i);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STROKE OPTIMIZER API (quadtree spatial index, dirty tracking)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if optimizer functions are available.
     */
    public boolean isOptimizerAvailable() {
        return optimizerCreateHandle != null && optimizerQueryVisibleHandle != null;
    }
    
    /**
     * Create a stroke optimizer for a document.
     * @return Handle to optimizer, or 0 on failure
     */
    public long optimizerCreate(float worldWidth, float worldHeight) {
        if (optimizerCreateHandle == null) return 0;
        try {
            return (long) optimizerCreateHandle.invokeExact(worldWidth, worldHeight);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Destroy a stroke optimizer.
     */
    public void optimizerDestroy(long handle) {
        if (optimizerDestroyHandle == null || handle == 0) return;
        try {
            optimizerDestroyHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Add a stroke to the optimizer.
     * @return Stroke ID, or -1 on failure
     */
    public int optimizerAddStroke(long handle, float[] x, float[] y, float[] thicknesses,
                                   int color, float baseThickness) {
        if (optimizerAddStrokeHandle == null || handle == 0 || x.length < 1) return -1;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment xSeg = allocateFloatArray(local, x);
            MemorySegment ySeg = allocateFloatArray(local, y);
            MemorySegment thickSeg = (thicknesses != null) ? allocateFloatArray(local, thicknesses) : MemorySegment.NULL;
            return (int) optimizerAddStrokeHandle.invokeExact(
                handle, xSeg, ySeg, x.length, thickSeg, color, baseThickness
            );
        } catch (Throwable t) {
            return -1;
        }
    }
    
    /**
     * Remove a stroke from the optimizer.
     */
    public void optimizerRemoveStroke(long handle, int strokeId) {
        if (optimizerRemoveStrokeHandle == null || handle == 0) return;
        try {
            optimizerRemoveStrokeHandle.invokeExact(handle, strokeId);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Move a stroke by delta.
     */
    public void optimizerMoveStroke(long handle, int strokeId, float dx, float dy) {
        if (optimizerMoveStrokeHandle == null || handle == 0) return;
        try {
            optimizerMoveStrokeHandle.invokeExact(handle, strokeId, dx, dy);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Query strokes visible in a viewport.
     * @return Array of visible stroke IDs, or empty array
     */
    public int[] optimizerQueryVisible(long handle, float viewX, float viewY, float viewW, float viewH) {
        if (optimizerQueryVisibleHandle == null || handle == 0) return new int[0];
        try (Arena local = Arena.ofConfined()) {
            int capacity = 4096;
            MemorySegment outSeg = local.allocate(ValueLayout.JAVA_INT, capacity);
            int count = (int) optimizerQueryVisibleHandle.invokeExact(
                handle, viewX, viewY, viewW, viewH, outSeg, capacity
            );
            if (count <= 0) return new int[0];
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = outSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return result;
        } catch (Throwable t) {
            return new int[0];
        }
    }
    
    /**
     * Query strokes near a point (for hit testing).
     * @return Array of nearby stroke IDs
     */
    public int[] optimizerQueryPoint(long handle, float x, float y, float radius) {
        if (optimizerQueryPointHandle == null || handle == 0) return new int[0];
        try (Arena local = Arena.ofConfined()) {
            int capacity = 256;
            MemorySegment outSeg = local.allocate(ValueLayout.JAVA_INT, capacity);
            int count = (int) optimizerQueryPointHandle.invokeExact(
                handle, x, y, radius, outSeg, capacity
            );
            if (count <= 0) return new int[0];
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = outSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return result;
        } catch (Throwable t) {
            return new int[0];
        }
    }
    
    /**
     * Get total stroke count in optimizer.
     */
    public int optimizerStrokeCount(long handle) {
        if (optimizerStrokeCountHandle == null || handle == 0) return 0;
        try {
            return (int) optimizerStrokeCountHandle.invokeExact(handle);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Check if viewport needs repaint.
     */
    public boolean optimizerNeedsRepaint(long handle, float viewX, float viewY, float viewW, float viewH) {
        if (optimizerNeedsRepaintHandle == null || handle == 0) return true;
        try {
            return ((int) optimizerNeedsRepaintHandle.invokeExact(handle, viewX, viewY, viewW, viewH)) != 0;
        } catch (Throwable t) {
            return true;
        }
    }
    
    /**
     * Clear dirty regions after repaint.
     */
    public void optimizerClearDirty(long handle) {
        if (optimizerClearDirtyHandle == null || handle == 0) return;
        try {
            optimizerClearDirtyHandle.invokeExact(handle);
        } catch (Throwable ignored) {}
    }
    
    /**
     * Render visible strokes to pixel buffer.
     * @return Number of strokes rendered
     */
    public int optimizerRenderVisible(long handle, int[] pixels, int width, int height,
                                       float viewX, float viewY, float viewW, float viewH) {
        if (optimizerRenderVisibleHandle == null || handle == 0 || pixels == null) return 0;
        try (Arena local = Arena.ofConfined()) {
            MemorySegment pixelSeg = local.allocate(ValueLayout.JAVA_INT, pixels.length);
            // Copy pixels to native memory
            for (int i = 0; i < pixels.length; i++) {
                pixelSeg.setAtIndex(ValueLayout.JAVA_INT, i, pixels[i]);
            }
            int count = (int) optimizerRenderVisibleHandle.invokeExact(
                handle, pixelSeg, width, height, viewX, viewY, viewW, viewH
            );
            // Copy back
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = pixelSeg.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return count;
        } catch (Throwable t) {
            return 0;
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
