/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.input;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import main.infrastructure.ffi.NativeTablet;

/**
 * High-level tablet input support with pressure sensitivity.
 * Provides easy access to stylus pressure, tilt, and device information.
 */
@SuppressWarnings("preview")
public final class TabletInputSupport {
    
    private static final Object LOCK = new Object();
    private static volatile NativeTablet tablet;
    private static volatile boolean attempted;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    /* Pressure curve settings */
    private static float pressureGamma = 1.0f;
    private static float pressureSmoothing = 0.3f;
    private static float lastSmoothedPressure = 0.0f;
    
    /* Device info cache */
    private static final List<TabletDevice> devices = new ArrayList<>();
    
    private TabletInputSupport() {}
    
    /**
     * Initialize the tablet input system.
     * @return true if tablet input is available
     */
    public static boolean initialize() {
        // DISABLED: Native tablet support causes crashes with Apple Pencil on Sidecar
        // For now, return false to use Java's built-in pressure support
        attempted = true;
        return false;
        
        /*
        if (initialized.get()) return tablet != null;
        
        synchronized (LOCK) {
            if (attempted) return tablet != null;
            attempted = true;
            
            try {
                SymbolLookup lookup = NativeAccess.symbolLookup();
                if (lookup == null) return false;
                
                tablet = new NativeTablet(lookup);
                if (!tablet.initialize()) {
                    tablet.close();
                    tablet = null;
                    return false;
                }
                
                refreshDevices();
                initialized.set(true);
                return true;
            } catch (Throwable t) {
                tablet = null;
                return false;
            }
        }
        */
    }
    
    /**
     * Check if tablet input is available.
     */
    public static boolean isAvailable() {
        // DISABLED: Native tablet causes crashes
        return false;
        /*
        if (!attempted) {
            return initialize();
        }
        return tablet != null;
        */
    }
    
    /**
     * Get the current stylus pressure (0.0 to 1.0).
     * Applies configured pressure curve and smoothing.
     */
    public static float getPressure() {
        if (tablet == null) return 1.0f; // Default to full pressure for mouse
        
        float raw = tablet.getPressure();
        
        // Apply gamma curve
        float curved = tablet.applyPressureCurve(raw, pressureGamma);
        
        // Apply smoothing
        float smoothed = tablet.smoothPressure(curved, lastSmoothedPressure, pressureSmoothing);
        lastSmoothedPressure = smoothed;
        
        return smoothed;
    }
    
    /**
     * Get the raw (unprocessed) pressure value.
     */
    public static float getRawPressure() {
        if (tablet == null) return 1.0f;
        return tablet.getPressure();
    }
    
    /**
     * Get the current stylus tilt.
     * @return float array [tiltX, tiltY] in degrees, or null if not available
     */
    public static float[] getTilt() {
        if (tablet == null) return null;
        return tablet.getTilt();
    }
    
    /**
     * Get the current stylus rotation in degrees.
     */
    public static float getRotation() {
        if (tablet == null) return 0.0f;
        return tablet.getRotation();
    }
    
    /**
     * Check if the stylus is in detection range.
     */
    public static boolean isStylusInRange() {
        if (tablet == null) return false;
        return tablet.isStylusInRange();
    }
    
    /**
     * Check if the eraser end of the stylus is active.
     */
    public static boolean isEraserActive() {
        if (tablet == null) return false;
        return tablet.isEraserActive();
    }
    
    /**
     * Check if the stylus is touching the surface.
     */
    public static boolean isTouching() {
        if (tablet == null) return false;
        return tablet.isTouching();
    }
    
    /**
     * Get the current button state as a bitmask.
     */
    public static int getButtons() {
        if (tablet == null) return 0;
        return tablet.getButtons();
    }
    
    /**
     * Set the pressure gamma curve.
     * @param gamma 1.0 = linear, <1.0 = softer start, >1.0 = harder start
     */
    public static void setPressureGamma(float gamma) {
        pressureGamma = Math.max(0.1f, Math.min(5.0f, gamma));
    }
    
    /**
     * Get the current pressure gamma.
     */
    public static float getPressureGamma() {
        return pressureGamma;
    }
    
    /**
     * Set the pressure smoothing factor.
     * @param smoothing 0.0 = no smoothing, 1.0 = full smoothing
     */
    public static void setPressureSmoothing(float smoothing) {
        pressureSmoothing = Math.max(0.0f, Math.min(1.0f, smoothing));
    }
    
    /**
     * Get the current pressure smoothing factor.
     */
    public static float getPressureSmoothing() {
        return pressureSmoothing;
    }
    
    /**
     * Reset pressure smoothing state.
     * Call this when starting a new stroke.
     */
    public static void resetPressureSmoothing() {
        lastSmoothedPressure = 0.0f;
    }
    
    /**
     * Refresh the list of detected devices.
     */
    public static void refreshDevices() {
        synchronized (devices) {
            devices.clear();
            if (tablet == null) return;
            
            tablet.refreshDevices();
            int count = tablet.getDeviceCount();
            for (int i = 0; i < count; i++) {
                TabletDevice device = new TabletDevice(
                    tablet.getDeviceName(i),
                    tablet.getDeviceVendorName(i),
                    tablet.getDeviceTypeName(i),
                    tablet.getPressureLevels(i),
                    tablet.hasTilt(i)
                );
                devices.add(device);
            }
        }
    }
    
    /**
     * Get the list of detected tablet devices.
     */
    public static List<TabletDevice> getDevices() {
        synchronized (devices) {
            return new ArrayList<>(devices);
        }
    }
    
    /**
     * Get pressure from a mouse event if available.
     * Falls back to tablet pressure or 1.0 for regular mouse.
     */
    public static float getPressureFromEvent(MouseEvent e) {
        if (tablet != null && tablet.isTouching()) {
            return getPressure();
        }
        // For regular mouse, return full pressure
        return 1.0f;
    }
    
    /**
     * Check if this event came from a tablet/stylus rather than a mouse.
     */
    public static boolean isTabletEvent(MouseEvent e) {
        if (tablet == null) return false;
        return tablet.isStylusInRange() || tablet.isTouching();
    }
    
    /**
     * Shutdown tablet support and release resources.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (tablet != null) {
                tablet.close();
                tablet = null;
            }
            initialized.set(false);
            attempted = false;
            devices.clear();
        }
    }
    
    /**
     * Represents a detected tablet device.
     */
    public static final class TabletDevice {
        private final String name;
        private final String vendor;
        private final String type;
        private final int pressureLevels;
        private final boolean hasTilt;
        
        TabletDevice(String name, String vendor, String type, int pressureLevels, boolean hasTilt) {
            this.name = name != null ? name : "Unknown";
            this.vendor = vendor != null ? vendor : "Unknown";
            this.type = type != null ? type : "Unknown";
            this.pressureLevels = pressureLevels;
            this.hasTilt = hasTilt;
        }
        
        public String getName() { return name; }
        public String getVendor() { return vendor; }
        public String getType() { return type; }
        public int getPressureLevels() { return pressureLevels; }
        public boolean hasTilt() { return hasTilt; }
        
        @Override
        public String toString() {
            return String.format("%s (%s %s, %d levels%s)",
                name, vendor, type, pressureLevels, hasTilt ? ", tilt" : "");
        }
    }
}
