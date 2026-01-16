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
 * a global target FPS for animations.
 */
public final class AppPerf {
    private static volatile boolean lowPowerMode = false;
    private static volatile int baseFps = 60;
    private static volatile int targetFps = 60;

    private AppPerf() {}

    public static boolean isLowPowerMode() {
        return lowPowerMode;
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

    public static void applySystemHints() {
        String os = System.getProperty("os.name", "");
        if (os == null || !os.toLowerCase(Locale.ROOT).contains("mac")) return;

        float refreshRate = NativeAccess.getPrimaryDisplayRefreshRate();
        if (refreshRate > 1.0f) {
            setBaseFps(Math.round(refreshRate));
        }

        if (NativeAccess.isMacLowPowerMode() || NativeAccess.isMacReduceMotionEnabled()) {
            setLowPowerMode(true);
        }

        int thermalState = NativeAccess.getMacThermalState();
        if (thermalState >= 2) {
            setLowPowerMode(true);
        }
    }

    public static int getAnimationDelay() {
        int fps = Math.max(1, targetFps);
        return Math.max(10, Math.round(1000f / fps));
    }

    private static int clampFps(int fps) {
        return Math.max(10, Math.min(240, fps));
    }
}
