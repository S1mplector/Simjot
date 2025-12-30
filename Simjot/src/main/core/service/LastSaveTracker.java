/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

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
