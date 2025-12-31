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

package main.ui.app;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.CrashReporter;

/**
 * Manages application lifecycle: startup, shutdown, watchdogs, and process control.
 * Extracted from JournalApp for cleaner separation of concerns.
 * 
 * <p>Uses native C watchdogs when available for more reliable timeout handling,
 * with Java fallbacks for compatibility.</p>
 */
public final class AppLifecycle {
    
    /** Default exit watchdog timeout (25 seconds) */
    public static final long EXIT_WATCHDOG_TIMEOUT_MS = 25_000L;
    
    /** Minimum splash screen display time (startup) */
    public static final int STARTUP_MIN_SPLASH_MS = 6500;
    
    /** Minimum splash screen display time (exit) */
    public static final int EXIT_MIN_SPLASH_MS = 5500;
    
    /** Exit status pulse interval */
    public static final long EXIT_PULSE_MS = 1200L;
    
    /** Exit in progress flag */
    private static final AtomicBoolean exitInProgress = new AtomicBoolean(false);
    
    /** Current exit watchdog ID (-1 if none) */
    private static final AtomicInteger exitWatchdogId = new AtomicInteger(-1);
    
    /** Launch arguments for relaunch */
    private static String[] launchArgs = new String[0];
    
    /** Exit listeners */
    private static final List<Runnable> exitListeners = new ArrayList<>();
    
    private AppLifecycle() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize lifecycle management.
     * Should be called early in application startup.
     */
    public static void initialize(String[] args) {
        launchArgs = args != null ? args.clone() : new String[0];
        
        // Install shutdown hook
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                handleShutdownHook();
            }, "SimjotShutdownHook"));
        } catch (Throwable ignored) {}
    }
    
    /**
     * Register a listener to be called during graceful exit.
     */
    public static void addExitListener(Runnable listener) {
        if (listener != null) {
            synchronized (exitListeners) {
                exitListeners.add(listener);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // WATCHDOG MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Start the exit watchdog timer.
     * Will force-halt the process if exit takes too long.
     */
    public static void startExitWatchdog() {
        startExitWatchdog(EXIT_WATCHDOG_TIMEOUT_MS);
    }
    
    /**
     * Start exit watchdog with custom timeout.
     */
    public static void startExitWatchdog(long timeoutMs) {
        int id = NativeAccess.watchdogStart(timeoutMs, NativeAccess.WD_ACTION_HALT, "ExitWatchdog");
        exitWatchdogId.set(id);
        
        if (id < 0) {
            // Log warning but don't fail - Java fallback is built into NativeAccess
            System.err.println("[AppLifecycle] Warning: Could not start native watchdog");
        }
    }
    
    /**
     * Cancel the exit watchdog.
     */
    public static void cancelExitWatchdog() {
        int id = exitWatchdogId.getAndSet(-1);
        if (id >= 0) {
            NativeAccess.watchdogCancel(id);
        }
    }
    
    /**
     * Start a general-purpose watchdog.
     * 
     * @param timeoutMs Timeout in milliseconds
     * @param action Action: WD_ACTION_NONE, WD_ACTION_EXIT, WD_ACTION_HALT
     * @param name Name for logging
     * @return Watchdog ID for cancellation
     */
    public static int startWatchdog(long timeoutMs, int action, String name) {
        return NativeAccess.watchdogStart(timeoutMs, action, name);
    }
    
    /**
     * Cancel a watchdog by ID.
     */
    public static boolean cancelWatchdog(int id) {
        return NativeAccess.watchdogCancel(id);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EXIT HANDLING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if exit is in progress.
     */
    public static boolean isExitInProgress() {
        return exitInProgress.get();
    }
    
    /**
     * Begin graceful exit.
     * Returns false if exit already in progress.
     */
    public static boolean beginExit() {
        return exitInProgress.compareAndSet(false, true);
    }
    
    /**
     * Force immediate process halt.
     */
    public static void forceHalt() {
        NativeAccess.forceHalt();
    }
    
    /**
     * Report exit timeout and force halt.
     */
    public static void reportTimeoutAndHalt(String context) {
        System.err.println("[AppLifecycle] " + context + " - forcing halt");
        try {
            CrashReporter.report("exit-timeout", Thread.currentThread(), 
                new RuntimeException(context));
        } catch (Throwable ignored) {}
        forceHalt();
    }
    
    /**
     * Handle JVM shutdown hook.
     */
    private static void handleShutdownHook() {
        // Notify listeners
        List<Runnable> listeners;
        synchronized (exitListeners) {
            listeners = new ArrayList<>(exitListeners);
        }
        
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {}
        }
        
        // Final failsafe
        try {
            Thread.sleep(100);
        } catch (Throwable ignored) {}
        
        try {
            Runtime.getRuntime().halt(0);
        } catch (Throwable ignored) {}
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROCESS CONTROL
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Relaunch the current process.
     */
    public static void relaunch() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome == null ? "java"
                    : new File(new File(javaHome, "bin"), isWindows() ? "javaw" : "java").getAbsolutePath();
            String sunCmd = System.getProperty("sun.java.command", "");
            String classPath = System.getProperty("java.class.path", "");

            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);

            if (sunCmd.endsWith(".jar") || sunCmd.contains(".jar ")) {
                String[] parts = sunCmd.split(" ");
                if (parts.length > 0) {
                    cmd.add("-jar");
                    cmd.add(parts[0]);
                    for (int i = 1; i < parts.length; i++) {
                        if (!parts[i].isBlank()) cmd.add(parts[i]);
                    }
                }
            } else {
                if (!classPath.isBlank()) {
                    cmd.add("-cp");
                    cmd.add(classPath);
                }
                if (!sunCmd.isBlank()) {
                    for (String part : sunCmd.split(" ")) {
                        if (!part.isBlank()) cmd.add(part);
                    }
                } else {
                    cmd.add("main.ui.app.JournalApp");
                    cmd.addAll(Arrays.asList(launchArgs));
                }
            }
            new ProcessBuilder(cmd).start();
        } catch (Throwable t) {
            System.err.println("[AppLifecycle] Relaunch failed: " + t.getMessage());
        }
    }
    
    /**
     * Check if running on Windows.
     */
    public static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }
    
    /**
     * Check if running on macOS.
     */
    public static boolean isMacOS() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("mac");
    }
    
    /**
     * Check if running on Linux.
     */
    public static boolean isLinux() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("linux");
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TIMING UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get monotonic time in milliseconds.
     * More reliable than System.currentTimeMillis() for timing.
     */
    public static long monotonicTimeMs() {
        return NativeAccess.monotonicTimeMs();
    }
    
    /**
     * Sleep with interruption handling.
     * Returns true if slept full duration, false if interrupted.
     */
    public static boolean sleepMs(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EDT UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Run task on EDT with timeout.
     * 
     * @param task Task to run
     * @param timeoutMs Maximum time to wait
     * @return true if completed, false if timed out
     */
    public static boolean runOnEdtWithTimeout(Runnable task, long timeoutMs) {
        if (task == null) return true;
        
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
                return true;
            }
            
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
            
            SwingUtilities.invokeLater(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    latch.countDown();
                }
            });
            
            boolean ok = latch.await(Math.max(1L, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS);
            
            Throwable t = err.get();
            if (t != null) {
                try {
                    CrashReporter.report("edt-task", Thread.currentThread(), t);
                } catch (Throwable ignored) {}
            }
            
            return ok;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Run task on background thread with progress reporting.
     */
    public static void runBackground(Runnable task, Consumer<String> statusCallback, Runnable onComplete) {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                if (task != null) task.run();
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                if (statusCallback != null && !chunks.isEmpty()) {
                    statusCallback.accept(chunks.get(chunks.size() - 1));
                }
            }
            
            @Override
            protected void done() {
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Throwable ignored) {}
                }
            }
        }.execute();
    }
}
