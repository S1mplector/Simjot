/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.monitoring;

import java.util.Locale;

import main.infrastructure.ffi.NativeAccess;

/**
 * Centralized performance toggles for UI animations and effects.
 * Allows switching between normal and low-power modes and provides
 * a global target FPS for animations. Now integrates with HardwareProfile
 * for optimal performance on both Intel and Apple Silicon Macs.
 */
public final class AppPerf {
    private static volatile boolean lowPowerMode = false;
    private static volatile boolean reducedAnimations = false;
    private static volatile int baseFps = 60;
    private static volatile int targetFps = 60;
    private static volatile boolean initialized = false;

    private AppPerf() {}

    /**
     * Initialize performance settings based on hardware profile.
     * Should be called early in application startup.
     */
    public static void initialize() {
        if (initialized) return;
        
        // Initialize rendering optimizer first (sets Java2D properties)
        RenderingOptimizer.initialize();
        
        // Get hardware-recommended settings
        HardwareProfile hw = HardwareProfile.get();
        baseFps = hw.getRecommendedFps();
        targetFps = baseFps;
        reducedAnimations = hw.shouldReduceAnimations();
        
        // Apply additional system hints (battery, thermal, etc.)
        applySystemHints();
        
        initialized = true;
        System.out.println("[AppPerf] Initialized: baseFps=" + baseFps + 
            ", lowPower=" + lowPowerMode + ", reducedAnimations=" + reducedAnimations);
    }

    public static boolean isLowPowerMode() {
        return lowPowerMode;
    }
    
    public static boolean shouldReduceAnimations() {
        return reducedAnimations || lowPowerMode;
    }

    public static void setLowPowerMode(boolean lowPower) {
        lowPowerMode = lowPower;
        targetFps = lowPower ? Math.min(30, baseFps) : baseFps;
    }

    public static void setBaseFps(int fps) {
        baseFps = clampFps(fps);
        if (!lowPowerMode) {
            targetFps = baseFps;
        }
    }

    public static int getTargetFps() {
        return targetFps;
    }
    
    public static int getBaseFps() {
        return baseFps;
    }

    public static void applySystemHints() {
        String os = System.getProperty("os.name", "");
        if (os == null || !os.toLowerCase(Locale.ROOT).contains("mac")) return;

        // Use native hardware detection for optimal FPS
        int recommendedFps = NativeAccess.getRecommendedFps();
        if (recommendedFps > 0) {
            setBaseFps(recommendedFps);
        } else {
            // Fallback to display refresh rate
            float refreshRate = NativeAccess.getPrimaryDisplayRefreshRate();
            if (refreshRate > 1.0f) {
                setBaseFps(Math.round(refreshRate));
            }
        }

        // Check system power/thermal state
        if (NativeAccess.isMacLowPowerMode() || NativeAccess.isMacReduceMotionEnabled()) {
            setLowPowerMode(true);
        }

        int thermalState = NativeAccess.getMacThermalState();
        if (thermalState >= 2) {
            setLowPowerMode(true);
        }

        // Reduce FPS on battery for Intel Macs (Apple Silicon is efficient enough)
        if (NativeAccess.isMacOnBattery() && NativeAccess.isIntelMac() && baseFps > 60) {
            setBaseFps(60);
        }
    }

    public static int getAnimationDelay() {
        int fps = Math.max(1, targetFps);
        return Math.max(10, Math.round(1000f / fps));
    }
    
    /**
     * Get animation delay for reduced complexity animations.
     * Uses lower FPS on older hardware.
     */
    public static int getReducedAnimationDelay() {
        if (shouldReduceAnimations()) {
            return Math.max(33, Math.round(1000f / Math.min(30, targetFps)));
        }
        return getAnimationDelay();
    }

    private static int clampFps(int fps) {
        return Math.max(10, Math.min(240, fps));
    }
    
    /**
     * Check if high-quality rendering should be used.
     */
    public static boolean useHighQualityRendering() {
        return RenderingOptimizer.isHighQuality() && !lowPowerMode;
    }
}
