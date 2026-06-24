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
 * FFM bindings for native tablet input and pressure sensitivity.
 * Provides low-level access to drawing tablet detection and stylus state.
 */
@SuppressWarnings("preview")
public final class NativeTablet implements AutoCloseable {
    
    /* Vendor constants */
    public static final int VENDOR_UNKNOWN = 0;
    public static final int VENDOR_WACOM = 1;
    public static final int VENDOR_HUION = 2;
    public static final int VENDOR_XP_PEN = 3;
    public static final int VENDOR_APPLE = 4;
    public static final int VENDOR_GAOMON = 5;
    public static final int VENDOR_UGEE = 6;
    public static final int VENDOR_VEIKK = 7;
    public static final int VENDOR_GENERIC = 99;
    
    /* Device type constants */
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_STYLUS = 1;
    public static final int TYPE_ERASER = 2;
    public static final int TYPE_CURSOR = 3;
    public static final int TYPE_TOUCH = 4;
    public static final int TYPE_TRACKPAD = 5;
    public static final int TYPE_APPLE_PENCIL = 6;
    
    private final Arena arena;
    private final MemorySegment context;
    private boolean initialized;
    
    /* Method handles */
    private final MethodHandle createContextHandle;
    private final MethodHandle destroyContextHandle;
    private final MethodHandle initializeHandle;
    private final MethodHandle isAvailableHandle;
    private final MethodHandle getDeviceCountHandle;
    private final MethodHandle getDeviceNameHandle;
    private final MethodHandle getDeviceVendorHandle;
    private final MethodHandle getDeviceTypeHandle;
    private final MethodHandle getPressureLevelsHandle;
    private final MethodHandle hasTiltHandle;
    private final MethodHandle refreshDevicesHandle;
    private final MethodHandle getPressureHandle;
    private final MethodHandle getTiltHandle;
    private final MethodHandle getRotationHandle;
    private final MethodHandle isStylusInRangeHandle;
    private final MethodHandle isEraserActiveHandle;
    private final MethodHandle isTouchingHandle;
    private final MethodHandle getButtonsHandle;
    private final MethodHandle applyPressureCurveHandle;
    private final MethodHandle applyPressureBezierHandle;
    private final MethodHandle smoothPressureHandle;
    
    public NativeTablet(SymbolLookup lookup) {
        this.arena = Arena.ofShared();
        Linker linker = Linker.nativeLinker();
        
        /* Lookup all method handles */
        createContextHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_create_context").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
        );
        
        destroyContextHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_destroy_context").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        
        initializeHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_initialize").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        isAvailableHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_is_available").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        
        getDeviceCountHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_device_count").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        getDeviceNameHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_device_name").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        getDeviceVendorHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_device_vendor").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        getDeviceTypeHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_device_type").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        getPressureLevelsHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_pressure_levels").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        hasTiltHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_has_tilt").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        
        refreshDevicesHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_refresh_devices").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        getPressureHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_pressure").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS)
        );
        
        getTiltHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_tilt").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        
        getRotationHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_rotation").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS)
        );
        
        isStylusInRangeHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_is_stylus_in_range").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        isEraserActiveHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_is_eraser_active").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        isTouchingHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_is_touching").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        getButtonsHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_get_buttons").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );
        
        applyPressureCurveHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_apply_pressure_curve").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
        );
        
        applyPressureBezierHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_apply_pressure_bezier").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
        );
        
        smoothPressureHandle = linker.downcallHandle(
            lookup.find("simjot_tablet_smooth_pressure").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
        );
        
        /* Create context */
        try {
            context = (MemorySegment) createContextHandle.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create tablet context", t);
        }
    }
    
    /**
     * Initialize the tablet input system.
     * Must be called before using other methods.
     */
    public boolean initialize() {
        if (initialized) return true;
        try {
            int result = (int) initializeHandle.invokeExact(context);
            initialized = (result == 0);
            return initialized;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Check if tablet input is available on this system.
     */
    public static boolean isAvailable(SymbolLookup lookup) {
        try {
            Linker linker = Linker.nativeLinker();
            MethodHandle handle = linker.downcallHandle(
                lookup.find("simjot_tablet_is_available").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );
            return ((int) handle.invokeExact()) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get the number of detected tablet devices.
     */
    public int getDeviceCount() {
        try {
            return (int) getDeviceCountHandle.invokeExact(context);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Get the name of a tablet device.
     */
    public String getDeviceName(int index) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment buf = temp.allocate(128);
            int len = (int) getDeviceNameHandle.invokeExact(context, index, buf, 128);
            if (len <= 0) return "";
            byte[] bytes = new byte[len];
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, bytes, 0, len);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
    
    /**
     * Get the vendor of a tablet device.
     */
    public int getDeviceVendor(int index) {
        try {
            return (int) getDeviceVendorHandle.invokeExact(context, index);
        } catch (Throwable t) {
            return VENDOR_UNKNOWN;
        }
    }
    
    /**
     * Get the vendor name as a string.
     */
    public String getDeviceVendorName(int index) {
        int vendor = getDeviceVendor(index);
        return switch (vendor) {
            case VENDOR_WACOM -> "Wacom";
            case VENDOR_HUION -> "Huion";
            case VENDOR_XP_PEN -> "XP-Pen";
            case VENDOR_APPLE -> "Apple";
            case VENDOR_GAOMON -> "Gaomon";
            case VENDOR_UGEE -> "Ugee";
            case VENDOR_VEIKK -> "Veikk";
            case VENDOR_GENERIC -> "Generic";
            default -> "Unknown";
        };
    }
    
    /**
     * Get the type of a tablet device.
     */
    public int getDeviceType(int index) {
        try {
            return (int) getDeviceTypeHandle.invokeExact(context, index);
        } catch (Throwable t) {
            return TYPE_UNKNOWN;
        }
    }
    
    /**
     * Get the type name as a string.
     */
    public String getDeviceTypeName(int index) {
        int type = getDeviceType(index);
        return switch (type) {
            case TYPE_STYLUS -> "Stylus";
            case TYPE_ERASER -> "Eraser";
            case TYPE_CURSOR -> "Cursor";
            case TYPE_TOUCH -> "Touch";
            case TYPE_TRACKPAD -> "Trackpad";
            case TYPE_APPLE_PENCIL -> "Apple Pencil";
            default -> "Unknown";
        };
    }
    
    /**
     * Get the number of pressure levels for a device.
     */
    public int getPressureLevels(int index) {
        try {
            return (int) getPressureLevelsHandle.invokeExact(context, index);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Check if a device supports tilt detection.
     */
    public boolean hasTilt(int index) {
        try {
            return ((int) hasTiltHandle.invokeExact(context, index)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Refresh the list of detected devices.
     */
    public void refreshDevices() {
        try {
            refreshDevicesHandle.invokeExact(context);
        } catch (Throwable t) {
            // Ignore
        }
    }
    
    /**
     * Get the current stylus pressure (0.0 to 1.0).
     */
    public float getPressure() {
        try {
            return (float) getPressureHandle.invokeExact(context);
        } catch (Throwable t) {
            return 0.0f;
        }
    }
    
    /**
     * Get the current stylus tilt angles in degrees.
     * @return float array [tiltX, tiltY] or null on error
     */
    public float[] getTilt() {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment tiltX = temp.allocate(ValueLayout.JAVA_FLOAT);
            MemorySegment tiltY = temp.allocate(ValueLayout.JAVA_FLOAT);
            int result = (int) getTiltHandle.invokeExact(context, tiltX, tiltY);
            if (result != 0) return null;
            return new float[] {
                tiltX.get(ValueLayout.JAVA_FLOAT, 0),
                tiltY.get(ValueLayout.JAVA_FLOAT, 0)
            };
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Get the current stylus rotation in degrees (0-360).
     */
    public float getRotation() {
        try {
            return (float) getRotationHandle.invokeExact(context);
        } catch (Throwable t) {
            return 0.0f;
        }
    }
    
    /**
     * Check if the stylus is in detection range.
     */
    public boolean isStylusInRange() {
        try {
            return ((int) isStylusInRangeHandle.invokeExact(context)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Check if the eraser end of the stylus is active.
     */
    public boolean isEraserActive() {
        try {
            return ((int) isEraserActiveHandle.invokeExact(context)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Check if the stylus is touching the surface.
     */
    public boolean isTouching() {
        try {
            return ((int) isTouchingHandle.invokeExact(context)) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get the current button state as a bitmask.
     */
    public int getButtons() {
        try {
            return (int) getButtonsHandle.invokeExact(context);
        } catch (Throwable t) {
            return 0;
        }
    }
    
    /**
     * Apply a gamma pressure curve to a pressure value.
     * @param pressure Input pressure (0.0 to 1.0)
     * @param gamma Gamma value (1.0 = linear, <1.0 = softer start, >1.0 = harder start)
     * @return Adjusted pressure
     */
    public float applyPressureCurve(float pressure, float gamma) {
        try {
            return (float) applyPressureCurveHandle.invokeExact(pressure, gamma);
        } catch (Throwable t) {
            return pressure;
        }
    }
    
    /**
     * Apply a bezier pressure curve.
     * @param pressure Input pressure (0.0 to 1.0)
     * @param p1 First control point (typically 0.0-0.5)
     * @param p2 Second control point (typically 0.5-1.0)
     * @return Adjusted pressure
     */
    public float applyPressureBezier(float pressure, float p1, float p2) {
        try {
            return (float) applyPressureBezierHandle.invokeExact(pressure, p1, p2);
        } catch (Throwable t) {
            return pressure;
        }
    }
    
    /**
     * Smooth pressure values over time to reduce jitter.
     * @param current Current pressure value
     * @param previous Previous smoothed pressure value
     * @param smoothing Smoothing factor (0.0 = no smoothing, 1.0 = full smoothing)
     * @return Smoothed pressure value
     */
    public float smoothPressure(float current, float previous, float smoothing) {
        try {
            return (float) smoothPressureHandle.invokeExact(current, previous, smoothing);
        } catch (Throwable t) {
            return current;
        }
    }
    
    @Override
    public void close() {
        if (context != null && !context.equals(MemorySegment.NULL)) {
            try {
                destroyContextHandle.invokeExact(context);
            } catch (Throwable t) {
                // Ignore
            }
        }
        arena.close();
    }
}
