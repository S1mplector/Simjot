package main.core.service;

/**
 * Tracks the last time a user-initiated save occurred in the app.
 * Simple static utility so the status bar can show "Last save" without
 * wiring signals across panels.
 */
public final class LastSaveTracker {
    private static volatile long lastSaveMillis = 0L;

    private LastSaveTracker() {}

    /** Call after a successful save operation. */
    public static void markSaved() {
        lastSaveMillis = System.currentTimeMillis();
    }

    /** Returns epoch millis of last save, or 0 if none yet. */
    public static long getLastSaveMillis() {
        return lastSaveMillis;
    }
}
