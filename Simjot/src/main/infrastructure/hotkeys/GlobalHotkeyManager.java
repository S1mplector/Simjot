package main.infrastructure.hotkeys;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.NativeInputEvent;
import org.jnativehook.keyboard.NativeKeyAdapter;
import org.jnativehook.keyboard.NativeKeyEvent;

/**
 * Registers a global hotkey for quick capture.
 */
public final class GlobalHotkeyManager {
    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    private static final int MOD_MASK = IS_MAC ? NativeInputEvent.META_MASK : NativeInputEvent.CTRL_MASK;
    private static final int SHIFT_MASK = NativeInputEvent.SHIFT_MASK;
    private static final int KEY_CODE = NativeKeyEvent.VC_J;

    private static boolean registered;
    private static NativeKeyAdapter listener;

    private GlobalHotkeyManager() {}

    public static boolean registerQuickCapture(Runnable action) {
        if (action == null) return false;
        if (registered) return true;
        suppressJNativeHookLogging();
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            return false;
        }
        listener = new NativeKeyAdapter() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                int mods = e.getModifiers();
                if ((mods & MOD_MASK) != 0 && (mods & SHIFT_MASK) != 0 && e.getKeyCode() == KEY_CODE) {
                    action.run();
                }
            }
        };
        GlobalScreen.addNativeKeyListener(listener);
        registered = true;
        return true;
    }

    public static void unregister() {
        if (!registered) return;
        try {
            if (listener != null) {
                GlobalScreen.removeNativeKeyListener(listener);
                listener = null;
            }
        } catch (Throwable ignored) {}
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (Throwable ignored) {}
        registered = false;
    }

    public static String getQuickCaptureShortcutLabel() {
        return IS_MAC ? "⌘+Shift+J" : "Ctrl+Shift+J";
    }

    private static void suppressJNativeHookLogging() {
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);
        } catch (Throwable ignored) {}
    }
}
