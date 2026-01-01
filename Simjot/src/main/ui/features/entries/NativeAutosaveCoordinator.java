/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import main.infrastructure.ffi.NativeAccess;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native-backed autosave coordinator using C++ autosave manager.
 * Provides robust multi-session tracking, debouncing, and recovery detection.
 * 
 * Falls back to Java-only behavior if native library unavailable.
 * 
 * @author S1mplector
 */
public class NativeAutosaveCoordinator {
    
    private final int sessionId;
    private final String filePath;
    private final int debounceMs;
    private final Runnable onSave;
    private final Runnable onStart;
    private final Runnable onEnd;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingCheck;
    private final AtomicBoolean saving = new AtomicBoolean(false);
    private final AtomicBoolean nativeAvailable;
    
    // Fallback Java state (when native unavailable)
    private volatile long lastDirtyTime = 0;
    private volatile boolean dirty = false;
    
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    /**
     * Create a new autosave coordinator for a document.
     * 
     * @param filePath Path to the document file (can be null for new unsaved docs)
     * @param debounceMs Debounce delay in milliseconds
     * @param onSave Callback to perform the actual save
     * @param onStart Callback when save starts (on EDT)
     * @param onEnd Callback when save ends (on EDT)
     */
    public NativeAutosaveCoordinator(String filePath, int debounceMs, 
                                      Runnable onSave, Runnable onStart, Runnable onEnd) {
        this.filePath = filePath;
        this.debounceMs = debounceMs > 0 ? debounceMs : 1500;
        this.onSave = onSave != null ? onSave : () -> {};
        this.onStart = onStart != null ? onStart : () -> {};
        this.onEnd = onEnd != null ? onEnd : () -> {};
        
        // Initialize native manager once
        if (!initialized.getAndSet(true)) {
            NativeAccess.autosaveInit();
        }
        
        // Try to create native session
        this.nativeAvailable = new AtomicBoolean(NativeAccess.hasAutosaveSupport());
        if (nativeAvailable.get() && filePath != null && !filePath.isEmpty()) {
            this.sessionId = NativeAccess.autosaveCreateSession(filePath, this.debounceMs);
            if (sessionId < 0) {
                nativeAvailable.set(false);
            }
        } else {
            this.sessionId = -1;
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NativeAutosaveCoordinator");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Mark content as dirty (changed). Schedules a save after debounce delay.
     */
    public void markDirty() {
        if (nativeAvailable.get() && sessionId > 0) {
            NativeAccess.autosaveMarkDirty(sessionId);
            scheduleNativeCheck();
        } else {
            // Java fallback
            dirty = true;
            lastDirtyTime = System.currentTimeMillis();
            scheduleJavaCheck();
        }
    }
    
    /**
     * Mark content as clean (saved successfully).
     */
    public void markClean() {
        if (nativeAvailable.get() && sessionId > 0) {
            NativeAccess.autosaveMarkClean(sessionId);
        } else {
            dirty = false;
        }
    }
    
    /**
     * Check if content is dirty.
     */
    public boolean isDirty() {
        if (nativeAvailable.get() && sessionId > 0) {
            return NativeAccess.autosaveIsDirty(sessionId);
        }
        return dirty;
    }
    
    /**
     * Update file path (e.g., after "Save As").
     */
    public void setFilePath(String newPath) {
        if (nativeAvailable.get() && sessionId > 0 && newPath != null) {
            NativeAccess.autosaveSetPath(sessionId, newPath);
        }
    }
    
    /**
     * Check if a recovery file exists for the given path.
     */
    public static boolean hasRecovery(String filePath) {
        return NativeAccess.autosaveHasRecovery(filePath);
    }
    
    /**
     * Stop the coordinator and cancel pending saves.
     */
    public void stop() {
        if (pendingCheck != null) {
            pendingCheck.cancel(false);
            pendingCheck = null;
        }
    }
    
    /**
     * Shutdown and destroy session.
     */
    public void shutdown() {
        stop();
        if (nativeAvailable.get() && sessionId > 0) {
            NativeAccess.autosaveDestroySession(sessionId);
        }
        scheduler.shutdownNow();
    }
    
    /**
     * Force immediate save (bypass debounce).
     */
    public void saveNow() {
        stop();
        if (saving.compareAndSet(false, true)) {
            performSave();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void scheduleNativeCheck() {
        if (pendingCheck != null) {
            pendingCheck.cancel(false);
        }
        
        // Check if native says we should save
        long msUntil = NativeAccess.autosaveMsUntilSave(sessionId);
        long delay = msUntil > 0 ? msUntil : debounceMs;
        
        pendingCheck = scheduler.schedule(() -> {
            if (NativeAccess.autosaveShouldSave(sessionId)) {
                if (saving.compareAndSet(false, true)) {
                    performSave();
                }
            } else {
                // Not ready yet, check again
                scheduleNativeCheck();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    private void scheduleJavaCheck() {
        if (pendingCheck != null) {
            pendingCheck.cancel(false);
        }
        
        pendingCheck = scheduler.schedule(() -> {
            if (!dirty) return;
            long elapsed = System.currentTimeMillis() - lastDirtyTime;
            if (elapsed >= debounceMs) {
                if (saving.compareAndSet(false, true)) {
                    performSave();
                }
            } else {
                // Not ready yet, check again after remaining time
                scheduleJavaCheck();
            }
        }, debounceMs, TimeUnit.MILLISECONDS);
    }
    
    private void performSave() {
        SwingUtilities.invokeLater(onStart);
        try {
            onSave.run();
            markClean();
        } finally {
            SwingUtilities.invokeLater(() -> {
                try {
                    onEnd.run();
                } finally {
                    saving.set(false);
                }
            });
        }
    }
}
