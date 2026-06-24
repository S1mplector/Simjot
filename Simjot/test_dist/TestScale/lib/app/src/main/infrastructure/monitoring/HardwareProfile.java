/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.monitoring;

import main.infrastructure.ffi.NativeAccess;

/**
 * Detects and profiles hardware capabilities for performance optimization.
 * Provides information about CPU architecture, graphics capabilities, and
 * recommended settings for optimal performance on different Mac hardware.
 */
public final class HardwareProfile {
    
    /** CPU architecture types */
    public enum Architecture {
        APPLE_SILICON,  // M1, M2, M3, M4 series
        INTEL_64,       // Intel x86_64
        INTEL_32,       // Intel x86 (legacy)
        UNKNOWN
    }
    
    /** Performance tier based on hardware capabilities */
    public enum PerformanceTier {
        HIGH,           // M-series, recent Intel i7/i9
        MEDIUM,         // Mid-range Intel, older M1
        LOW,            // Older Intel, integrated graphics
        VERY_LOW        // Very old hardware, minimal graphics
    }
    
    /** Graphics capability level */
    public enum GraphicsCapability {
        METAL_NATIVE,   // Full Metal support (M-series)
        METAL_COMPAT,   // Metal compatibility (Intel with discrete GPU)
        OPENGL_FULL,    // OpenGL 4.x support
        OPENGL_LEGACY,  // OpenGL 2.x/3.x only
        SOFTWARE        // Software rendering only
    }
    
    private static volatile HardwareProfile instance;
    private static final Object LOCK = new Object();
    
    private final Architecture architecture;
    private final PerformanceTier performanceTier;
    private final GraphicsCapability graphicsCapability;
    private final int processorCount;
    private final long totalMemoryMB;
    private final boolean hasDiscreteGpu;
    private final boolean supportsProMotion;
    private final String cpuBrand;
    private final int simdLevel;
    
    private HardwareProfile() {
        this.architecture = detectArchitecture();
        this.processorCount = Runtime.getRuntime().availableProcessors();
        this.totalMemoryMB = detectTotalMemory();
        this.cpuBrand = detectCpuBrand();
        this.simdLevel = detectSimdLevel();
        this.hasDiscreteGpu = detectDiscreteGpu();
        this.supportsProMotion = detectProMotion();
        this.graphicsCapability = determineGraphicsCapability();
        this.performanceTier = determinePerformanceTier();
        
        logProfile();
    }
    
    public static HardwareProfile get() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new HardwareProfile();
                }
            }
        }
        return instance;
    }
    
    /** Force re-detection (useful after hardware changes like external GPU) */
    public static void refresh() {
        synchronized (LOCK) {
            instance = new HardwareProfile();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DETECTION METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static Architecture detectArchitecture() {
        int nativeArch = NativeAccess.getArchitecture();
        return switch (nativeArch) {
            case 1 -> Architecture.APPLE_SILICON;
            case 2 -> Architecture.INTEL_64;
            case 3 -> Architecture.INTEL_32;
            default -> Architecture.UNKNOWN;
        };
    }
    
    private static long detectTotalMemory() {
        return NativeAccess.getTotalSystemMemoryMB();
    }
    
    private static String detectCpuBrand() {
        String brand = NativeAccess.getCpuBrand();
        return (brand != null && !brand.isBlank()) ? brand.trim() : "Unknown CPU";
    }
    
    private static int detectSimdLevel() {
        return NativeAccess.getSimdSupportLevel();
    }
    
    private static boolean detectDiscreteGpu() {
        return NativeAccess.hasDiscreteGpu();
    }
    
    private static boolean detectProMotion() {
        float refreshRate = NativeAccess.getPrimaryDisplayRefreshRate();
        return refreshRate > 60.5f;
    }
    
    private GraphicsCapability determineGraphicsCapability() {
        if (architecture == Architecture.APPLE_SILICON) {
            return GraphicsCapability.METAL_NATIVE;
        }
        
        if (architecture == Architecture.INTEL_64) {
            // Intel Macs with discrete GPU have Metal compatibility
            if (hasDiscreteGpu) {
                return GraphicsCapability.METAL_COMPAT;
            }
            // Check for newer Intel integrated graphics (HD 4000+)
            if (cpuBrand.contains("i7") || cpuBrand.contains("i9") || 
                cpuBrand.contains("i5") && processorCount >= 4) {
                return GraphicsCapability.OPENGL_FULL;
            }
            return GraphicsCapability.OPENGL_LEGACY;
        }
        
        if (architecture == Architecture.INTEL_32) {
            return GraphicsCapability.OPENGL_LEGACY;
        }
        
        return GraphicsCapability.SOFTWARE;
    }
    
    private PerformanceTier determinePerformanceTier() {
        // Apple Silicon is always high performance
        if (architecture == Architecture.APPLE_SILICON) {
            if (processorCount >= 8 && totalMemoryMB >= 16000) {
                return PerformanceTier.HIGH;
            }
            return PerformanceTier.MEDIUM;
        }
        
        // Intel Mac performance tiers
        if (architecture == Architecture.INTEL_64) {
            // High: i7/i9 with 16GB+ RAM and discrete GPU
            if ((cpuBrand.contains("i7") || cpuBrand.contains("i9")) && 
                totalMemoryMB >= 16000 && hasDiscreteGpu) {
                return PerformanceTier.HIGH;
            }
            // Medium: i5+ with 8GB+ RAM
            if ((cpuBrand.contains("i5") || cpuBrand.contains("i7")) && 
                totalMemoryMB >= 8000 && processorCount >= 4) {
                return PerformanceTier.MEDIUM;
            }
            // Low: older i-series or low RAM
            if (totalMemoryMB >= 4000 && processorCount >= 2) {
                return PerformanceTier.LOW;
            }
            return PerformanceTier.VERY_LOW;
        }
        
        // Intel 32-bit is always very low
        if (architecture == Architecture.INTEL_32) {
            return PerformanceTier.VERY_LOW;
        }
        
        // Unknown architecture - assume low
        return PerformanceTier.LOW;
    }
    
    private void logProfile() {
        System.out.println("[HardwareProfile] Detected:");
        System.out.println("  Architecture: " + architecture);
        System.out.println("  CPU: " + cpuBrand + " (" + processorCount + " cores)");
        System.out.println("  Memory: " + totalMemoryMB + " MB");
        System.out.println("  Graphics: " + graphicsCapability + (hasDiscreteGpu ? " (discrete GPU)" : ""));
        System.out.println("  SIMD: level " + simdLevel);
        System.out.println("  Performance tier: " + performanceTier);
        if (supportsProMotion) {
            System.out.println("  ProMotion: supported");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public Architecture getArchitecture() { return architecture; }
    public PerformanceTier getPerformanceTier() { return performanceTier; }
    public GraphicsCapability getGraphicsCapability() { return graphicsCapability; }
    public int getProcessorCount() { return processorCount; }
    public long getTotalMemoryMB() { return totalMemoryMB; }
    public boolean hasDiscreteGpu() { return hasDiscreteGpu; }
    public boolean supportsProMotion() { return supportsProMotion; }
    public String getCpuBrand() { return cpuBrand; }
    public int getSimdLevel() { return simdLevel; }
    
    public boolean isAppleSilicon() { 
        return architecture == Architecture.APPLE_SILICON; 
    }
    
    public boolean isIntel() { 
        return architecture == Architecture.INTEL_64 || architecture == Architecture.INTEL_32; 
    }
    
    public boolean isHighPerformance() {
        return performanceTier == PerformanceTier.HIGH;
    }
    
    public boolean isLowPerformance() {
        return performanceTier == PerformanceTier.LOW || 
               performanceTier == PerformanceTier.VERY_LOW;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RECOMMENDED SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Get recommended target FPS for animations */
    public int getRecommendedFps() {
        return switch (performanceTier) {
            case HIGH -> supportsProMotion ? 120 : 60;
            case MEDIUM -> 60;
            case LOW -> 30;
            case VERY_LOW -> 20;
        };
    }
    
    /** Get recommended image cache size in MB */
    public int getRecommendedImageCacheMB() {
        return switch (performanceTier) {
            case HIGH -> 256;
            case MEDIUM -> 128;
            case LOW -> 64;
            case VERY_LOW -> 32;
        };
    }
    
    /** Should use hardware-accelerated rendering? */
    public boolean shouldUseHardwareAcceleration() {
        return graphicsCapability != GraphicsCapability.SOFTWARE &&
               graphicsCapability != GraphicsCapability.OPENGL_LEGACY;
    }
    
    /** Should use Metal rendering pipeline? */
    public boolean shouldUseMetal() {
        return graphicsCapability == GraphicsCapability.METAL_NATIVE ||
               graphicsCapability == GraphicsCapability.METAL_COMPAT;
    }
    
    /** Should reduce animation complexity? */
    public boolean shouldReduceAnimations() {
        return performanceTier == PerformanceTier.LOW || 
               performanceTier == PerformanceTier.VERY_LOW;
    }
    
    /** Should use simplified rendering (fewer effects)? */
    public boolean shouldSimplifyRendering() {
        return performanceTier == PerformanceTier.VERY_LOW;
    }
    
    /** Get recommended thread pool size for background tasks */
    public int getRecommendedThreadPoolSize() {
        int cores = processorCount;
        return switch (performanceTier) {
            case HIGH -> Math.max(4, cores - 2);
            case MEDIUM -> Math.max(2, cores / 2);
            case LOW -> 2;
            case VERY_LOW -> 1;
        };
    }
    
    /** Get recommended rendering quality (0.0 = fastest, 1.0 = best quality) */
    public float getRecommendedRenderQuality() {
        return switch (performanceTier) {
            case HIGH -> 1.0f;
            case MEDIUM -> 0.8f;
            case LOW -> 0.5f;
            case VERY_LOW -> 0.3f;
        };
    }
}
