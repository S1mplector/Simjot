/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.monitoring;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures Java2D rendering pipeline optimally based on hardware capabilities.
 * Provides different rendering profiles for Apple Silicon vs Intel Macs.
 */
public final class RenderingOptimizer {
    
    private static volatile boolean initialized = false;
    private static volatile RenderingProfile activeProfile = RenderingProfile.BALANCED;
    private static Map<RenderingHints.Key, Object> cachedHints;
    
    /** Rendering quality profiles */
    public enum RenderingProfile {
        QUALITY,    // Best quality, for high-performance hardware
        BALANCED,   // Good balance of quality and performance
        PERFORMANCE // Maximum performance, for older hardware
    }
    
    private RenderingOptimizer() {}
    
    /**
     * Initialize rendering optimizations based on hardware profile.
     * Should be called early in application lifecycle before creating UI.
     */
    public static void initialize() {
        if (initialized) return;
        
        HardwareProfile hw = HardwareProfile.get();
        configureJava2DPipeline(hw);
        activeProfile = determineProfile(hw);
        cachedHints = buildRenderingHints(activeProfile);
        initialized = true;
        
        System.out.println("[RenderingOptimizer] Initialized with profile: " + activeProfile);
    }
    
    /**
     * Configure Java2D system properties based on hardware.
     */
    private static void configureJava2DPipeline(HardwareProfile hw) {
        // Metal rendering pipeline for macOS (Apple Silicon and compatible Intel)
        if (hw.shouldUseMetal()) {
            System.setProperty("sun.java2d.metal", "true");
            System.setProperty("sun.java2d.opengl", "false");
            System.out.println("[RenderingOptimizer] Enabled Metal rendering pipeline");
        } else if (hw.shouldUseHardwareAcceleration()) {
            // OpenGL for older Intel Macs
            System.setProperty("sun.java2d.metal", "false");
            System.setProperty("sun.java2d.opengl", "true");
            System.out.println("[RenderingOptimizer] Enabled OpenGL rendering pipeline");
        } else {
            // Software rendering fallback
            System.setProperty("sun.java2d.metal", "false");
            System.setProperty("sun.java2d.opengl", "false");
            System.out.println("[RenderingOptimizer] Using software rendering pipeline");
        }
        
        // Antialiasing settings
        if (hw.isHighPerformance()) {
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
        } else if (hw.isLowPerformance()) {
            // Disable LCD antialiasing on low-end hardware (more CPU intensive)
            System.setProperty("awt.useSystemAAFontSettings", "gasp");
            System.setProperty("swing.aatext", "true");
        } else {
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
        }
        
        // Image acceleration
        if (hw.shouldUseHardwareAcceleration()) {
            System.setProperty("sun.java2d.accthreshold", "0");
            System.setProperty("sun.java2d.d3d", "false"); // Windows only
        }
        
        // Rendering quality vs speed tradeoffs
        if (hw.isLowPerformance()) {
            // Prioritize speed on older hardware
            System.setProperty("sun.java2d.renderer", "sun.java2d.marlin.MarlinRenderingEngine");
            System.setProperty("sun.java2d.renderer.useThreadLocal", "true");
            // Reduce Marlin cache sizes for lower memory footprint
            System.setProperty("sun.java2d.renderer.pixelWidth", "2048");
            System.setProperty("sun.java2d.renderer.pixelHeight", "2048");
        } else {
            // Higher quality settings for capable hardware
            System.setProperty("sun.java2d.renderer", "sun.java2d.marlin.MarlinRenderingEngine");
            System.setProperty("sun.java2d.renderer.useThreadLocal", "true");
            System.setProperty("sun.java2d.renderer.pixelWidth", "4096");
            System.setProperty("sun.java2d.renderer.pixelHeight", "4096");
        }
        
        // Double buffering
        System.setProperty("sun.java2d.noddraw", "true"); // Disable DirectDraw (Windows)
        System.setProperty("sun.awt.noerasebackground", "true"); // Reduce flicker
    }
    
    /**
     * Determine the appropriate rendering profile based on hardware.
     */
    private static RenderingProfile determineProfile(HardwareProfile hw) {
        if (hw.isHighPerformance()) {
            return RenderingProfile.QUALITY;
        } else if (hw.isLowPerformance()) {
            return RenderingProfile.PERFORMANCE;
        }
        return RenderingProfile.BALANCED;
    }
    
    /**
     * Build rendering hints map for the given profile.
     */
    private static Map<RenderingHints.Key, Object> buildRenderingHints(RenderingProfile profile) {
        Map<RenderingHints.Key, Object> hints = new HashMap<>();
        
        switch (profile) {
            case QUALITY -> {
                // High quality antialiasing
                hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                hints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                hints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            }
            case PERFORMANCE -> {
                // Speed over quality
                hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Keep AA for text
                hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
                hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
                hints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
                hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                hints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
            }
            case BALANCED -> {
                // Good balance
                hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT);
                hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_DEFAULT);
                hints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_DEFAULT);
                hints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
                hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                hints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DEFAULT);
            }
        }
        
        return hints;
    }
    
    /**
     * Apply optimal rendering hints to a Graphics2D context.
     */
    public static void applyHints(Graphics2D g2) {
        if (!initialized) initialize();
        if (cachedHints != null) {
            g2.setRenderingHints(cachedHints);
        }
    }
    
    /**
     * Apply hints for text rendering only.
     */
    public static void applyTextHints(Graphics2D g2) {
        if (!initialized) initialize();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (activeProfile == RenderingProfile.QUALITY) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, 
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }
    
    /**
     * Apply hints for image scaling operations.
     */
    public static void applyImageHints(Graphics2D g2) {
        if (!initialized) initialize();
        
        switch (activeProfile) {
            case QUALITY -> {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                    RenderingHints.VALUE_RENDER_QUALITY);
            }
            case PERFORMANCE -> {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                    RenderingHints.VALUE_RENDER_SPEED);
            }
            default -> {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                    RenderingHints.VALUE_RENDER_DEFAULT);
            }
        }
    }
    
    /**
     * Get the active rendering profile.
     */
    public static RenderingProfile getActiveProfile() {
        if (!initialized) initialize();
        return activeProfile;
    }
    
    /**
     * Check if high quality rendering is enabled.
     */
    public static boolean isHighQuality() {
        return getActiveProfile() == RenderingProfile.QUALITY;
    }
    
    /**
     * Check if performance mode is enabled.
     */
    public static boolean isPerformanceMode() {
        return getActiveProfile() == RenderingProfile.PERFORMANCE;
    }
    
    /**
     * Force a specific rendering profile (for user override).
     */
    public static void setProfile(RenderingProfile profile) {
        activeProfile = profile;
        cachedHints = buildRenderingHints(profile);
        System.out.println("[RenderingOptimizer] Profile changed to: " + profile);
    }
    
    /**
     * Reset to auto-detected profile.
     */
    public static void resetToAutoProfile() {
        activeProfile = determineProfile(HardwareProfile.get());
        cachedHints = buildRenderingHints(activeProfile);
        System.out.println("[RenderingOptimizer] Reset to auto profile: " + activeProfile);
    }
}
