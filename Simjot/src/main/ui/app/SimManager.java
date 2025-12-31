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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages Sim AI assistant components: brain, overlay, and scheduler.
 * 
 * <p><strong>NOTE:</strong> Sim features are currently disabled and not initialized.
 * This class provides the infrastructure for future Sim integration without
 * actually starting any AI components.</p>
 * 
 * <p>When Sim is enabled in the future, this manager will handle:</p>
 * <ul>
 *   <li>SimBrain - Core AI processing and response generation</li>
 *   <li>SimOverlay - UI overlay for Sim interactions</li>
 *   <li>SimScheduler - Proactive scheduling for Sim activities</li>
 *   <li>SimSettings - User preferences for Sim behavior</li>
 * </ul>
 */
public class SimManager {
    
    /** Flag indicating if Sim features are enabled */
    private static final boolean SIM_ENABLED = false;
    
    /** Flag indicating if Sim is currently active */
    private final AtomicBoolean simActive = new AtomicBoolean(false);
    
    // Component references (null when disabled)
    private Object simBrain;      // main.core.sim.engine.SimBrain
    private Object simOverlay;    // main.ui.sim.overlay.SimOverlay
    private Object simScheduler;  // main.core.sim.engine.SimScheduler
    private Object simSettings;   // main.core.sim.prefs.SimSettings
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION (Currently disabled)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if Sim features are enabled.
     * Currently always returns false.
     */
    public static boolean isSimEnabled() {
        return SIM_ENABLED;
    }
    
    /**
     * Initialize Sim components.
     * Currently does nothing as Sim is disabled.
     */
    public void initialize() {
        if (!SIM_ENABLED) {
            return;
        }
        // Future: Initialize SimBrain, SimOverlay, SimScheduler
    }
    
    /**
     * Initialize Sim with app context.
     * Currently does nothing as Sim is disabled.
     */
    public void initialize(JournalApp app) {
        if (!SIM_ENABLED) {
            return;
        }
        // Future: Initialize with app reference for UI integration
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT ACCESS (Returns null when disabled)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get the SimBrain instance.
     * Returns null when Sim is disabled.
     */
    public Object getSimBrain() {
        return simBrain;
    }
    
    /**
     * Get the SimOverlay instance.
     * Returns null when Sim is disabled.
     */
    public Object getSimOverlay() {
        return simOverlay;
    }
    
    /**
     * Get the SimScheduler instance.
     * Returns null when Sim is disabled.
     */
    public Object getSimScheduler() {
        return simScheduler;
    }
    
    /**
     * Get the SimSettings instance.
     * Returns null when Sim is disabled.
     */
    public Object getSimSettings() {
        return simSettings;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Check if Sim is currently active.
     */
    public boolean isActive() {
        return simActive.get();
    }
    
    /**
     * Enable Sim features.
     * Currently does nothing as Sim is disabled at compile time.
     */
    public void enable() {
        if (!SIM_ENABLED) {
            return;
        }
        // Future: Start Sim components
        simActive.set(true);
    }
    
    /**
     * Disable Sim features.
     */
    public void disable() {
        if (!simActive.compareAndSet(true, false)) {
            return;
        }
        
        // Stop components in order
        stopScheduler();
        stopBrain();
        disposeOverlay();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT CONTROL
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Stop the SimBrain.
     */
    private void stopBrain() {
        if (simBrain != null) {
            try {
                // Future: simBrain.shutdown();
                simBrain = null;
            } catch (Throwable ignored) {}
        }
    }
    
    /**
     * Stop the SimScheduler.
     */
    private void stopScheduler() {
        if (simScheduler != null) {
            try {
                // Future: simScheduler.stop();
                simScheduler = null;
            } catch (Throwable ignored) {}
        }
    }
    
    /**
     * Dispose the SimOverlay.
     */
    private void disposeOverlay() {
        if (simOverlay != null) {
            try {
                // Future: simOverlay.disposeOverlay();
                simOverlay = null;
            } catch (Throwable ignored) {}
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SHUTDOWN
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Shutdown all Sim components gracefully.
     * Safe to call even when Sim is disabled.
     */
    public void shutdown() {
        disable();
    }
    
    /**
     * Shutdown with timeout for graceful exit.
     */
    public void shutdownWithTimeout(long timeoutMs) {
        if (!simActive.get()) {
            return;
        }
        
        AppLifecycle.runOnEdtWithTimeout(() -> {
            disable();
        }, timeoutMs);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FUTURE INTEGRATION POINTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Send a message to Sim (future).
     * Currently does nothing.
     */
    public void sendMessage(String message) {
        if (!SIM_ENABLED || !simActive.get()) {
            return;
        }
        // Future: Route message to SimBrain
    }
    
    /**
     * Notify Sim of user activity (future).
     * Used for proactive responses.
     */
    public void notifyActivity(String activityType, Object data) {
        if (!SIM_ENABLED || !simActive.get()) {
            return;
        }
        // Future: Notify SimBrain of activity for context
    }
    
    /**
     * Get Sim's current mood inference (future).
     */
    public String inferMood() {
        if (!SIM_ENABLED || !simActive.get()) {
            return null;
        }
        // Future: Return mood inference from SimBrain
        return null;
    }
}
