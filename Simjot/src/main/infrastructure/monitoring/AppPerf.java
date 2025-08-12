package main.infrastructure.monitoring;

/**
 * Centralized performance toggles for UI animations and effects.
 * Allows switching between normal and low-power modes and provides
 * a global target FPS for animations.
 */
public final class AppPerf {
    private static volatile boolean lowPowerMode = false;
    // Defaults: normal 60 FPS, low-power 30 FPS
    private static volatile int targetFps = 60;

    private AppPerf() {}

    public static boolean isLowPowerMode() {
        return lowPowerMode;
    }

    public static void setLowPowerMode(boolean lowPower) {
        lowPowerMode = lowPower;
        targetFps = lowPower ? 30 : 60;
    }

    public static int getTargetFps() {
        return targetFps;
    }

    public static int getAnimationDelay() {
        int fps = Math.max(1, targetFps);
        return Math.max(10, Math.round(1000f / fps));
    }
}
