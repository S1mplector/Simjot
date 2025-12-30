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

package main.infrastructure.hotkeys;

import java.util.Locale;

/**
 * Global hotkey manager stub.
 * 
 * Note: JNativeHook dependency removed for module compatibility.
 * Global hotkeys are not currently functional - this is a placeholder
 * that allows the application to compile and run normally.
 */
public final class GlobalHotkeyManager {
    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private GlobalHotkeyManager() {}

    /**
     * Attempts to register a global hotkey for quick capture.
     * Currently returns false as JNativeHook is not available.
     */
    public static boolean registerQuickCapture(Runnable action) {
        // JNativeHook dependency not available in modular build
        // Global hotkeys disabled for now
        return false;
    }

    /**
     * Unregisters any global hotkey listeners.
     */
    public static void unregister() {
        // No-op - nothing to unregister
    }

    /**
     * Returns the shortcut label for display purposes.
     */
    public static String getQuickCaptureShortcutLabel() {
        return IS_MAC ? "⌘+Shift+J" : "Ctrl+Shift+J";
    }
}
