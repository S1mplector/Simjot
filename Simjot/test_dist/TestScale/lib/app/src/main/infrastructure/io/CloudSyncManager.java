/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.io;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import main.infrastructure.ffi.NativeAccess;

/**
 * Comprehensive cloud sync manager using advanced native sync capabilities.
 * Orchestrates file watching, batch operations, conflict resolution, progress tracking,
 * and scheduled background syncs.
 */
public final class CloudSyncManager {
    
    // Sync states
    public static final int STATE_IDLE = 0;
    public static final int STATE_SCANNING = 1;
    public static final int STATE_SYNCING = 2;
    public static final int STATE_RESOLVING = 3;
    public static final int STATE_ERROR = 4;
    
    // Network states  
    public static final int NETWORK_UNKNOWN = 0;
    public static final int NETWORK_DISCONNECTED = 1;
    public static final int NETWORK_WIFI = 2;
    public static final int NETWORK_CELLULAR = 3;
    public static final int NETWORK_WIRED = 4;
    
    // Batch operations
    public static final int OP_DOWNLOAD = 0;
    public static final int OP_UPLOAD = 1;
    public static final int OP_DELETE = 2;
    public static final int OP_CONFLICT = 3;
    
    // Priority levels
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 5;
    public static final int PRIORITY_HIGH = 10;
    public static final int PRIORITY_CRITICAL = 15;
    
    private static final CloudSyncManager INSTANCE = new CloudSyncManager();
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean lowPowerMode = new AtomicBoolean(false);
    private final AtomicInteger currentState = new AtomicInteger(STATE_IDLE);
    private final AtomicInteger networkState = new AtomicInteger(NETWORK_UNKNOWN);
    private final AtomicLong lastSyncMs = new AtomicLong(0);
    private final AtomicLong lastDeltaScanMs = new AtomicLong(0);
    
    private volatile File rootPath;
    private volatile int watcherId = -1;
    private volatile int schedulerId = -1;
    
    private final List<SyncListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ConflictInfo> pendingConflicts = new CopyOnWriteArrayList<>();
    
    private ScheduledExecutorService scheduler;
    private ExecutorService syncExecutor;
    
    // Native FFM access
    private final Linker linker = Linker.nativeLinker();
    private SymbolLookup lookup;
    
    // Cached method handles for performance
    private MethodHandle syncInitHandle;
    private MethodHandle syncShutdownHandle;
    private MethodHandle syncGetNetworkStateHandle;
    private MethodHandle syncIsConnectedHandle;
    private MethodHandle syncSetEnabledHandle;
    private MethodHandle syncSetLowPowerHandle;
    private MethodHandle syncDeltaScanHandle;
    private MethodHandle syncDeltaCountHandle;
    private MethodHandle syncDeltaClearHandle;
    private MethodHandle syncBatchAddHandle;
    private MethodHandle syncBatchCountHandle;
    private MethodHandle syncBatchClearHandle;
    private MethodHandle syncBatchSortPriorityHandle;
    private MethodHandle syncBatchExecuteHandle;
    private MethodHandle syncBatchInProgressHandle;
    private MethodHandle syncShouldThrottleHandle;
    private MethodHandle syncGetThrottleDelayMsHandle;
    private MethodHandle conflictScanHandle;
    private MethodHandle conflictCountHandle;
    private MethodHandle conflictGetPathHandle;
    private MethodHandle conflictResolveKeepLocalHandle;
    private MethodHandle conflictResolveKeepCloudHandle;
    private MethodHandle conflictResolveKeepBothHandle;
    private MethodHandle conflictClearResolvedHandle;
    private MethodHandle syncProgressResetHandle;
    private MethodHandle syncProgressStartHandle;
    private MethodHandle syncProgressIncrementHandle;
    private MethodHandle syncProgressCompleteHandle;
    private MethodHandle syncProgressGetStateHandle;
    private MethodHandle syncProgressGetPercentHandle;
    private MethodHandle syncProgressGetElapsedMsHandle;
    private MethodHandle syncProgressEstimateRemainingMsHandle;
    private MethodHandle syncProgressGetSpeedBpsHandle;
    private MethodHandle syncMetricsGetAvgLatencyHandle;
    private MethodHandle syncMetricsGetSuccessRateHandle;
    private MethodHandle syncMetricsTotalBytesSyncedHandle;
    private MethodHandle syncGetRetryDelayMsHandle;
    private MethodHandle syncShouldRetryHandle;
    private MethodHandle watcherStartHandle;
    private MethodHandle watcherStopHandle;
    private MethodHandle watcherStopAllHandle;
    private MethodHandle watcherActiveCountHandle;
    private MethodHandle watcherEventCountHandle;
    private MethodHandle watcherPopEventHandle;
    private MethodHandle watcherClearEventsHandle;
    private MethodHandle schedulerAddHandle;
    private MethodHandle schedulerRemoveHandle;
    private MethodHandle schedulerMarkCompletedHandle;
    private MethodHandle schedulerEntryCountHandle;
    private MethodHandle schedulerClearHandle;
    private MethodHandle icloudDiscoverContainersHandle;
    private MethodHandle icloudAccountStatusHandle;
    private MethodHandle icloudGetQuotaHandle;

    private CloudSyncManager() {}
    
    public static CloudSyncManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the sync manager with the given root path.
     */
    public synchronized boolean initialize(File root) {
        if (initialized.get()) return true;
        if (root == null || !root.exists()) return false;
        
        this.rootPath = root;
        
        // Get native symbol lookup
        lookup = NativeAccess.symbolLookup();
        if (lookup == null) {
            IoLog.warn("cloud-sync", "Native library not available, sync features limited", null);
            return false;
        }
        
        // Initialize method handles
        initializeHandles();
        
        // Initialize native sync module
        if (!nativeSyncInit()) {
            IoLog.warn("cloud-sync", "Failed to initialize native sync module", null);
            return false;
        }
        
        // Create executors
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CloudSync-Scheduler");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
        
        syncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "CloudSync-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        
        // Start file watcher
        watcherId = nativeWatcherStart(root.getAbsolutePath());
        if (watcherId >= 0) {
            IoLog.info("cloud-sync", "File watcher started for: " + root.getAbsolutePath());
        }
        
        // Schedule periodic sync
        long intervalMs = lowPowerMode.get() ? 120000L : 60000L;
        schedulerId = nativeSchedulerAdd(root.getAbsolutePath(), intervalMs, PRIORITY_NORMAL);
        
        // Start background tasks
        scheduler.scheduleAtFixedRate(this::processWatcherEvents, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkScheduledSyncs, 5, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::updateNetworkState, 10, 10, TimeUnit.SECONDS);
        
        initialized.set(true);
        IoLog.info("cloud-sync", "Cloud sync manager initialized");
        return true;
    }
    
    /**
     * Shutdown the sync manager.
     */
    public synchronized void shutdown() {
        if (!initialized.get()) return;
        
        // Stop watchers
        if (watcherId >= 0) {
            nativeWatcherStop(watcherId);
            watcherId = -1;
        }
        nativeWatcherStopAll();
        
        // Clear scheduler
        nativeSchedulerClear();
        
        // Shutdown executors
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
            syncExecutor = null;
        }
        
        // Shutdown native module
        nativeSyncShutdown();
        
        initialized.set(false);
        IoLog.info("cloud-sync", "Cloud sync manager shut down");
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    public boolean isInitialized() { return initialized.get(); }
    public boolean isEnabled() { return enabled.get(); }
    public void setEnabled(boolean e) { 
        enabled.set(e);
        nativeSyncSetEnabled(e);
    }
    
    public boolean isLowPowerMode() { return lowPowerMode.get(); }
    public void setLowPowerMode(boolean lp) {
        lowPowerMode.set(lp);
        nativeSyncSetLowPower(lp);
    }
    
    public int getState() { return currentState.get(); }
    public int getNetworkState() { return networkState.get(); }
    public boolean isConnected() { return nativeSyncIsConnected(); }
    
    /**
     * Trigger an immediate sync scan.
     */
    public void triggerSync() {
        if (!initialized.get() || !enabled.get()) return;
        if (currentState.get() != STATE_IDLE) return;
        
        syncExecutor.execute(this::performFullSync);
    }
    
    /**
     * Queue a file for sync with the given operation and priority.
     */
    public boolean queueSync(String path, int operation, int priority) {
        if (!initialized.get() || path == null) return false;
        return nativeSyncBatchAdd(path, operation, priority);
    }
    
    /**
     * Queue a file for download.
     */
    public boolean queueDownload(String path) {
        return queueSync(path, OP_DOWNLOAD, PRIORITY_NORMAL);
    }
    
    /**
     * Queue a file for upload (mark as changed).
     */
    public boolean queueUpload(String path) {
        return queueSync(path, OP_UPLOAD, PRIORITY_NORMAL);
    }
    
    /**
     * Get sync progress percentage (0-100).
     */
    public float getProgressPercent() {
        return nativeSyncProgressGetPercent();
    }
    
    /**
     * Get elapsed sync time in milliseconds.
     */
    public long getElapsedMs() {
        return nativeSyncProgressGetElapsedMs();
    }
    
    /**
     * Get estimated remaining sync time in milliseconds.
     */
    public long getEstimatedRemainingMs() {
        return nativeSyncProgressEstimateRemainingMs();
    }
    
    /**
     * Get current sync speed in bytes per second.
     */
    public long getSpeedBps() {
        return nativeSyncProgressGetSpeedBps();
    }
    
    /**
     * Get average sync latency in milliseconds.
     */
    public long getAvgLatency() {
        return nativeSyncMetricsGetAvgLatency();
    }
    
    /**
     * Get sync success rate (0-100).
     */
    public float getSuccessRate() {
        return nativeSyncMetricsGetSuccessRate();
    }
    
    /**
     * Get total bytes synced.
     */
    public long getTotalBytesSynced() {
        return nativeSyncMetricsTotalBytesSynced();
    }
    
    /**
     * Get pending conflict count.
     */
    public int getConflictCount() {
        return nativeConflictCount();
    }
    
    /**
     * Get list of pending conflicts.
     */
    public List<ConflictInfo> getConflicts() {
        return new ArrayList<>(pendingConflicts);
    }
    
    /**
     * Resolve a conflict by keeping local version.
     */
    public boolean resolveConflictKeepLocal(String path) {
        boolean ok = nativeConflictResolveKeepLocal(path);
        if (ok) {
            pendingConflicts.removeIf(c -> c.path.equals(path));
            notifyConflictResolved(path);
        }
        return ok;
    }
    
    /**
     * Resolve a conflict by keeping cloud version.
     */
    public boolean resolveConflictKeepCloud(String path, int versionIndex) {
        boolean ok = nativeConflictResolveKeepCloud(path, versionIndex);
        if (ok) {
            pendingConflicts.removeIf(c -> c.path.equals(path));
            notifyConflictResolved(path);
        }
        return ok;
    }
    
    /**
     * Resolve a conflict by keeping both versions.
     */
    public boolean resolveConflictKeepBoth(String path, String suffix) {
        boolean ok = nativeConflictResolveKeepBoth(path, suffix != null ? suffix : "_conflict");
        if (ok) {
            pendingConflicts.removeIf(c -> c.path.equals(path));
            notifyConflictResolved(path);
        }
        return ok;
    }
    
    /**
     * Get iCloud account status.
     * @return 0=unavailable, 1=signed in no container, 2=readonly, 3=full access
     */
    public int getAccountStatus() {
        return nativeIcloudAccountStatus();
    }
    
    /**
     * Discover available iCloud containers.
     */
    public String discoverContainers() {
        return nativeIcloudDiscoverContainers();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LISTENERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public void addListener(SyncListener listener) {
        if (listener != null) listeners.add(listener);
    }
    
    public void removeListener(SyncListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyStateChanged(int newState) {
        for (SyncListener l : listeners) {
            try { l.onStateChanged(newState); } catch (Throwable ignored) {}
        }
    }
    
    private void notifyProgress(float percent) {
        for (SyncListener l : listeners) {
            try { l.onProgress(percent); } catch (Throwable ignored) {}
        }
    }
    
    private void notifyConflictDetected(ConflictInfo conflict) {
        for (SyncListener l : listeners) {
            try { l.onConflictDetected(conflict); } catch (Throwable ignored) {}
        }
    }
    
    private void notifyConflictResolved(String path) {
        for (SyncListener l : listeners) {
            try { l.onConflictResolved(path); } catch (Throwable ignored) {}
        }
    }
    
    private void notifySyncComplete(boolean success) {
        for (SyncListener l : listeners) {
            try { l.onSyncComplete(success); } catch (Throwable ignored) {}
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BACKGROUND TASKS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void processWatcherEvents() {
        if (!initialized.get() || !enabled.get()) return;
        
        int eventCount = nativeWatcherEventCount();
        if (eventCount == 0) return;
        
        int processed = 0;
        int maxEvents = lowPowerMode.get() ? 10 : 50;
        
        while (processed < maxEvents) {
            String path = nativeWatcherPopEvent();
            if (path == null || path.isEmpty()) break;
            
            // Queue changed files for upload
            if (rootPath != null && path.startsWith(rootPath.getAbsolutePath())) {
                queueUpload(path);
            }
            processed++;
        }
        
        // If we have queued items, trigger a sync
        if (nativeSyncBatchCount() > 0 && currentState.get() == STATE_IDLE) {
            triggerSync();
        }
    }
    
    private void checkScheduledSyncs() {
        if (!initialized.get() || !enabled.get()) return;
        if (currentState.get() != STATE_IDLE) return;
        
        // Check if any scheduled syncs are due
        int entryCount = nativeSchedulerEntryCount();
        if (entryCount > 0 && schedulerId >= 0) {
            long now = System.currentTimeMillis();
            long lastSync = lastSyncMs.get();
            long interval = lowPowerMode.get() ? 120000L : 60000L;
            
            if (now - lastSync >= interval) {
                triggerSync();
            }
        }
    }
    
    private void updateNetworkState() {
        int state = nativeSyncGetNetworkState();
        int prev = networkState.getAndSet(state);
        if (prev != state) {
            IoLog.info("cloud-sync", "Network state changed: " + networkStateToString(state));
        }
    }
    
    private String networkStateToString(int state) {
        return switch (state) {
            case NETWORK_DISCONNECTED -> "Disconnected";
            case NETWORK_WIFI -> "WiFi";
            case NETWORK_CELLULAR -> "Cellular";
            case NETWORK_WIRED -> "Wired";
            default -> "Unknown";
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC EXECUTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void performFullSync() {
        if (!enabled.get()) return;
        if (!currentState.compareAndSet(STATE_IDLE, STATE_SCANNING)) return;
        
        try (MacPowerAssertion powerAssertion = MacPowerAssertion.acquire("Simjot cloud sync")) {
            notifyStateChanged(STATE_SCANNING);
            
            // Check network
            if (!nativeSyncIsConnected()) {
                IoLog.info("cloud-sync", "Sync skipped: no network connection");
                return;
            }
            
            // Check throttling
            if (nativeSyncShouldThrottle()) {
                int delay = nativeSyncGetThrottleDelayMs();
                IoLog.info("cloud-sync", "Sync throttled, delay: " + delay + "ms");
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // Scan for delta changes
            long lastScan = lastDeltaScanMs.get();
            int changedCount = nativeSyncDeltaScan(rootPath.getAbsolutePath(), lastScan);
            lastDeltaScanMs.set(System.currentTimeMillis());
            
            if (changedCount > 0) {
                IoLog.info("cloud-sync", "Found " + changedCount + " changed files");
            }
            
            // Scan for conflicts
            currentState.set(STATE_RESOLVING);
            notifyStateChanged(STATE_RESOLVING);
            
            int conflictCount = nativeConflictScan(rootPath.getAbsolutePath(), 100);
            if (conflictCount > 0) {
                IoLog.warn("cloud-sync", "Found " + conflictCount + " conflicts", null);
                loadConflicts();
            }
            
            // Execute batch operations
            int batchCount = nativeSyncBatchCount();
            if (batchCount > 0) {
                currentState.set(STATE_SYNCING);
                notifyStateChanged(STATE_SYNCING);
                
                nativeSyncBatchSortPriority();
                nativeSyncProgressStart(batchCount, 0);
                
                int maxItems = lowPowerMode.get() ? 20 : 50;
                int timeoutMs = lowPowerMode.get() ? 5000 : 10000;
                
                int[] result = nativeSyncBatchExecute(maxItems, timeoutMs);
                int processed = result[0];
                int succeeded = result[1];
                int failed = result[2];
                
                nativeSyncProgressComplete(failed == 0);
                
                IoLog.info("cloud-sync", String.format(
                    "Sync complete: %d processed, %d succeeded, %d failed",
                    processed, succeeded, failed));
                
                notifySyncComplete(failed == 0);
            }
            
            lastSyncMs.set(System.currentTimeMillis());
            if (schedulerId >= 0) {
                nativeSchedulerMarkCompleted(schedulerId);
            }
            
        } catch (Throwable t) {
            IoLog.warn("cloud-sync", "Sync failed: " + t.getMessage(), t);
            currentState.set(STATE_ERROR);
            notifyStateChanged(STATE_ERROR);
            notifySyncComplete(false);
        } finally {
            currentState.set(STATE_IDLE);
            notifyStateChanged(STATE_IDLE);
        }
    }
    
    private void loadConflicts() {
        pendingConflicts.clear();
        int count = nativeConflictCount();
        
        for (int i = 0; i < count; i++) {
            String path = nativeConflictGetPath(i);
            if (path != null && !path.isEmpty()) {
                ConflictInfo info = new ConflictInfo(path);
                pendingConflicts.add(info);
                notifyConflictDetected(info);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE FFM CALLS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void initializeHandles() {
        if (lookup == null) return;
        
        try {
            syncInitHandle = lookup.find("simjot_sync_init")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncShutdownHandle = lookup.find("simjot_sync_shutdown")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncGetNetworkStateHandle = lookup.find("simjot_sync_get_network_state")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncIsConnectedHandle = lookup.find("simjot_sync_is_connected")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncSetEnabledHandle = lookup.find("simjot_sync_set_enabled")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncSetLowPowerHandle = lookup.find("simjot_sync_set_low_power")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncDeltaScanHandle = lookup.find("simjot_sync_delta_scan")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncDeltaCountHandle = lookup.find("simjot_sync_delta_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncDeltaClearHandle = lookup.find("simjot_sync_delta_clear")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncBatchAddHandle = lookup.find("simjot_sync_batch_add")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)))
                .orElse(null);
            syncBatchCountHandle = lookup.find("simjot_sync_batch_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncBatchClearHandle = lookup.find("simjot_sync_batch_clear")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncBatchSortPriorityHandle = lookup.find("simjot_sync_batch_sort_priority")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncBatchExecuteHandle = lookup.find("simjot_sync_batch_execute")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                .orElse(null);
            syncBatchInProgressHandle = lookup.find("simjot_sync_batch_in_progress")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncShouldThrottleHandle = lookup.find("simjot_sync_should_throttle")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncGetThrottleDelayMsHandle = lookup.find("simjot_sync_get_throttle_delay_ms")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            conflictScanHandle = lookup.find("simjot_conflict_scan")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)))
                .orElse(null);
            conflictCountHandle = lookup.find("simjot_conflict_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            conflictGetPathHandle = lookup.find("simjot_conflict_get_path")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)))
                .orElse(null);
            conflictResolveKeepLocalHandle = lookup.find("simjot_conflict_resolve_keep_local")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)))
                .orElse(null);
            conflictResolveKeepCloudHandle = lookup.find("simjot_conflict_resolve_keep_cloud")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)))
                .orElse(null);
            conflictResolveKeepBothHandle = lookup.find("simjot_conflict_resolve_keep_both")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                .orElse(null);
            conflictClearResolvedHandle = lookup.find("simjot_conflict_clear_resolved")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncProgressResetHandle = lookup.find("simjot_sync_progress_reset")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            syncProgressStartHandle = lookup.find("simjot_sync_progress_start")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncProgressIncrementHandle = lookup.find("simjot_sync_progress_increment")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncProgressCompleteHandle = lookup.find("simjot_sync_progress_complete")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncProgressGetStateHandle = lookup.find("simjot_sync_progress_get_state")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            syncProgressGetPercentHandle = lookup.find("simjot_sync_progress_get_percent")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_FLOAT)))
                .orElse(null);
            syncProgressGetElapsedMsHandle = lookup.find("simjot_sync_progress_get_elapsed_ms")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncProgressEstimateRemainingMsHandle = lookup.find("simjot_sync_progress_estimate_remaining_ms")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncProgressGetSpeedBpsHandle = lookup.find("simjot_sync_progress_get_speed_bps")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncMetricsGetAvgLatencyHandle = lookup.find("simjot_sync_metrics_get_avg_latency")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncMetricsGetSuccessRateHandle = lookup.find("simjot_sync_metrics_get_success_rate")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_FLOAT)))
                .orElse(null);
            syncMetricsTotalBytesSyncedHandle = lookup.find("simjot_sync_metrics_total_bytes_synced")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG)))
                .orElse(null);
            syncGetRetryDelayMsHandle = lookup.find("simjot_sync_get_retry_delay_ms")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)))
                .orElse(null);
            syncShouldRetryHandle = lookup.find("simjot_sync_should_retry")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)))
                .orElse(null);
            watcherStartHandle = lookup.find("simjot_watcher_start")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)))
                .orElse(null);
            watcherStopHandle = lookup.find("simjot_watcher_stop")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)))
                .orElse(null);
            watcherStopAllHandle = lookup.find("simjot_watcher_stop_all")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            watcherActiveCountHandle = lookup.find("simjot_watcher_active_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            watcherEventCountHandle = lookup.find("simjot_watcher_event_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            watcherPopEventHandle = lookup.find("simjot_watcher_pop_event")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                .orElse(null);
            watcherClearEventsHandle = lookup.find("simjot_watcher_clear_events")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            schedulerAddHandle = lookup.find("simjot_scheduler_add")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)))
                .orElse(null);
            schedulerRemoveHandle = lookup.find("simjot_scheduler_remove")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)))
                .orElse(null);
            schedulerMarkCompletedHandle = lookup.find("simjot_scheduler_mark_completed")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)))
                .orElse(null);
            schedulerEntryCountHandle = lookup.find("simjot_scheduler_entry_count")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            schedulerClearHandle = lookup.find("simjot_scheduler_clear")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            icloudDiscoverContainersHandle = lookup.find("simjot_icloud_discover_containers")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)))
                .orElse(null);
            icloudAccountStatusHandle = lookup.find("simjot_icloud_account_status")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            icloudGetQuotaHandle = lookup.find("simjot_icloud_get_quota")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                .orElse(null);
        } catch (Throwable t) {
            IoLog.warn("cloud-sync", "Failed to initialize native handles: " + t.getMessage(), t);
        }
    }
    
    private boolean nativeSyncInit() {
        if (syncInitHandle == null) return false;
        try { return ((int) syncInitHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeSyncShutdown() {
        if (syncShutdownHandle == null) return;
        try { syncShutdownHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private int nativeSyncGetNetworkState() {
        if (syncGetNetworkStateHandle == null) return NETWORK_UNKNOWN;
        try { return (int) syncGetNetworkStateHandle.invokeExact(); } catch (Throwable t) { return NETWORK_UNKNOWN; }
    }
    
    private boolean nativeSyncIsConnected() {
        if (syncIsConnectedHandle == null) return true;
        try { return ((int) syncIsConnectedHandle.invokeExact()) != 0; } catch (Throwable t) { return true; }
    }
    
    private void nativeSyncSetEnabled(boolean enabled) {
        if (syncSetEnabledHandle == null) return;
        try { syncSetEnabledHandle.invokeExact(enabled ? 1 : 0); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncSetLowPower(boolean lp) {
        if (syncSetLowPowerHandle == null) return;
        try { syncSetLowPowerHandle.invokeExact(lp ? 1 : 0); } catch (Throwable ignored) {}
    }
    
    private int nativeSyncDeltaScan(String rootPath, long sinceMs) {
        if (syncDeltaScanHandle == null || rootPath == null) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(rootPath);
            return (int) syncDeltaScanHandle.invokeExact(cPath, sinceMs);
        } catch (Throwable t) { return -1; }
    }
    
    private int nativeSyncDeltaCount() {
        if (syncDeltaCountHandle == null) return 0;
        try { return (int) syncDeltaCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private void nativeSyncDeltaClear() {
        if (syncDeltaClearHandle == null) return;
        try { syncDeltaClearHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private boolean nativeSyncBatchAdd(String path, int operation, int priority) {
        if (syncBatchAddHandle == null || path == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            return ((int) syncBatchAddHandle.invokeExact(cPath, operation, priority)) != 0;
        } catch (Throwable t) { return false; }
    }
    
    private int nativeSyncBatchCount() {
        if (syncBatchCountHandle == null) return 0;
        try { return (int) syncBatchCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private void nativeSyncBatchClear() {
        if (syncBatchClearHandle == null) return;
        try { syncBatchClearHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncBatchSortPriority() {
        if (syncBatchSortPriorityHandle == null) return;
        try { syncBatchSortPriorityHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private int[] nativeSyncBatchExecute(int maxItems, int timeoutMs) {
        if (syncBatchExecuteHandle == null) return new int[]{0, 0, 0};
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment succeeded = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment failed = arena.allocate(ValueLayout.JAVA_INT);
            int processed = (int) syncBatchExecuteHandle.invokeExact(maxItems, timeoutMs, succeeded, failed);
            return new int[]{processed, succeeded.get(ValueLayout.JAVA_INT, 0), failed.get(ValueLayout.JAVA_INT, 0)};
        } catch (Throwable t) { return new int[]{0, 0, 0}; }
    }
    
    private boolean nativeSyncBatchInProgress() {
        if (syncBatchInProgressHandle == null) return false;
        try { return ((int) syncBatchInProgressHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private boolean nativeSyncShouldThrottle() {
        if (syncShouldThrottleHandle == null) return false;
        try { return ((int) syncShouldThrottleHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private int nativeSyncGetThrottleDelayMs() {
        if (syncGetThrottleDelayMsHandle == null) return 0;
        try { return (int) syncGetThrottleDelayMsHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private int nativeConflictScan(String rootPath, int maxConflicts) {
        if (conflictScanHandle == null || rootPath == null) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(rootPath);
            return (int) conflictScanHandle.invokeExact(cPath, maxConflicts);
        } catch (Throwable t) { return -1; }
    }
    
    private int nativeConflictCount() {
        if (conflictCountHandle == null) return 0;
        try { return (int) conflictCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private String nativeConflictGetPath(int index) {
        if (conflictGetPathHandle == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(4096);
            int len = (int) conflictGetPathHandle.invokeExact(index, buffer, 4096);
            if (len <= 0) return null;
            return buffer.getString(0);
        } catch (Throwable t) { return null; }
    }
    
    private boolean nativeConflictResolveKeepLocal(String path) {
        if (conflictResolveKeepLocalHandle == null || path == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            return ((int) conflictResolveKeepLocalHandle.invokeExact(cPath)) != 0;
        } catch (Throwable t) { return false; }
    }
    
    private boolean nativeConflictResolveKeepCloud(String path, int versionIndex) {
        if (conflictResolveKeepCloudHandle == null || path == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            return ((int) conflictResolveKeepCloudHandle.invokeExact(cPath, versionIndex)) != 0;
        } catch (Throwable t) { return false; }
    }
    
    private boolean nativeConflictResolveKeepBoth(String path, String suffix) {
        if (conflictResolveKeepBothHandle == null || path == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            MemorySegment cSuffix = arena.allocateFrom(suffix != null ? suffix : "_conflict");
            return ((int) conflictResolveKeepBothHandle.invokeExact(cPath, cSuffix)) != 0;
        } catch (Throwable t) { return false; }
    }
    
    private void nativeConflictClearResolved() {
        if (conflictClearResolvedHandle == null) return;
        try { conflictClearResolvedHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncProgressReset() {
        if (syncProgressResetHandle == null) return;
        try { syncProgressResetHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncProgressStart(int totalFiles, long totalBytes) {
        if (syncProgressStartHandle == null) return;
        try { syncProgressStartHandle.invokeExact(totalFiles, totalBytes); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncProgressIncrement(long bytes) {
        if (syncProgressIncrementHandle == null) return;
        try { syncProgressIncrementHandle.invokeExact(bytes); } catch (Throwable ignored) {}
    }
    
    private void nativeSyncProgressComplete(boolean success) {
        if (syncProgressCompleteHandle == null) return;
        try { syncProgressCompleteHandle.invokeExact(success ? 1 : 0); } catch (Throwable ignored) {}
    }
    
    private int nativeSyncProgressGetState() {
        if (syncProgressGetStateHandle == null) return STATE_IDLE;
        try { return (int) syncProgressGetStateHandle.invokeExact(); } catch (Throwable t) { return STATE_IDLE; }
    }
    
    private float nativeSyncProgressGetPercent() {
        if (syncProgressGetPercentHandle == null) return 0f;
        try { return (float) syncProgressGetPercentHandle.invokeExact(); } catch (Throwable t) { return 0f; }
    }
    
    private long nativeSyncProgressGetElapsedMs() {
        if (syncProgressGetElapsedMsHandle == null) return 0;
        try { return (long) syncProgressGetElapsedMsHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private long nativeSyncProgressEstimateRemainingMs() {
        if (syncProgressEstimateRemainingMsHandle == null) return 0;
        try { return (long) syncProgressEstimateRemainingMsHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private long nativeSyncProgressGetSpeedBps() {
        if (syncProgressGetSpeedBpsHandle == null) return 0;
        try { return (long) syncProgressGetSpeedBpsHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private long nativeSyncMetricsGetAvgLatency() {
        if (syncMetricsGetAvgLatencyHandle == null) return 0;
        try { return (long) syncMetricsGetAvgLatencyHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private float nativeSyncMetricsGetSuccessRate() {
        if (syncMetricsGetSuccessRateHandle == null) return 100f;
        try { return (float) syncMetricsGetSuccessRateHandle.invokeExact(); } catch (Throwable t) { return 100f; }
    }
    
    private long nativeSyncMetricsTotalBytesSynced() {
        if (syncMetricsTotalBytesSyncedHandle == null) return 0;
        try { return (long) syncMetricsTotalBytesSyncedHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private long nativeSyncGetRetryDelayMs(int attempt) {
        if (syncGetRetryDelayMsHandle == null) return 100L * (1L << Math.min(attempt, 8));
        try { return (long) syncGetRetryDelayMsHandle.invokeExact(attempt); } 
        catch (Throwable t) { return 100L * (1L << Math.min(attempt, 8)); }
    }
    
    private boolean nativeSyncShouldRetry(int attempt, int errorCode) {
        if (syncShouldRetryHandle == null) return attempt < 5;
        try { return ((int) syncShouldRetryHandle.invokeExact(attempt, errorCode)) != 0; } 
        catch (Throwable t) { return attempt < 5; }
    }
    
    private int nativeWatcherStart(String path) {
        if (watcherStartHandle == null || path == null) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(path);
            return (int) watcherStartHandle.invokeExact(cPath);
        } catch (Throwable t) { return -1; }
    }
    
    private boolean nativeWatcherStop(int watcherId) {
        if (watcherStopHandle == null) return false;
        try { return ((int) watcherStopHandle.invokeExact(watcherId)) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeWatcherStopAll() {
        if (watcherStopAllHandle == null) return;
        try { watcherStopAllHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private int nativeWatcherActiveCount() {
        if (watcherActiveCountHandle == null) return 0;
        try { return (int) watcherActiveCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private int nativeWatcherEventCount() {
        if (watcherEventCountHandle == null) return 0;
        try { return (int) watcherEventCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private String nativeWatcherPopEvent() {
        if (watcherPopEventHandle == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(4096);
            MemorySegment timestamp = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment eventType = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) watcherPopEventHandle.invokeExact(buffer, 4096, timestamp, eventType);
            if (ok == 0) return null;
            return buffer.getString(0);
        } catch (Throwable t) { return null; }
    }
    
    private void nativeWatcherClearEvents() {
        if (watcherClearEventsHandle == null) return;
        try { watcherClearEventsHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private int nativeSchedulerAdd(String rootPath, long intervalMs, int priority) {
        if (schedulerAddHandle == null || rootPath == null) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(rootPath);
            return (int) schedulerAddHandle.invokeExact(cPath, intervalMs, priority);
        } catch (Throwable t) { return -1; }
    }
    
    private boolean nativeSchedulerRemove(int entryId) {
        if (schedulerRemoveHandle == null) return false;
        try { return ((int) schedulerRemoveHandle.invokeExact(entryId)) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeSchedulerMarkCompleted(int entryId) {
        if (schedulerMarkCompletedHandle == null) return;
        try { schedulerMarkCompletedHandle.invokeExact(entryId); } catch (Throwable ignored) {}
    }
    
    private int nativeSchedulerEntryCount() {
        if (schedulerEntryCountHandle == null) return 0;
        try { return (int) schedulerEntryCountHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    private void nativeSchedulerClear() {
        if (schedulerClearHandle == null) return;
        try { schedulerClearHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private String nativeIcloudDiscoverContainers() {
        if (icloudDiscoverContainersHandle == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(4096);
            int ok = (int) icloudDiscoverContainersHandle.invokeExact(buffer, 4096);
            if (ok == 0) return null;
            return buffer.getString(0);
        } catch (Throwable t) { return null; }
    }
    
    private int nativeIcloudAccountStatus() {
        if (icloudAccountStatusHandle == null) return 0;
        try { return (int) icloudAccountStatusHandle.invokeExact(); } catch (Throwable t) { return 0; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static class ConflictInfo {
        public final String path;
        public final long detectedTime;
        
        public ConflictInfo(String path) {
            this.path = path;
            this.detectedTime = System.currentTimeMillis();
        }
    }
    
    public interface SyncListener {
        default void onStateChanged(int newState) {}
        default void onProgress(float percent) {}
        default void onConflictDetected(ConflictInfo conflict) {}
        default void onConflictResolved(String path) {}
        default void onSyncComplete(boolean success) {}
    }
}
