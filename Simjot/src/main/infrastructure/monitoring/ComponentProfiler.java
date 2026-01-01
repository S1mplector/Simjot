/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.monitoring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import main.infrastructure.ffi.NativeAccess;

/**
 * Component-level profiler for tracking RAM and CPU usage across Simjot.
 * Uses native C code for accurate measurement. CLI-focused output.
 */
public final class ComponentProfiler {
    
    private static final ComponentProfiler INSTANCE = new ComponentProfiler();
    
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    private Thread samplerThread;
    private int samplingIntervalMs = 500;
    
    private ComponentProfiler() {}
    
    public static ComponentProfiler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the profiler with a sampling interval.
     */
    public boolean init(int intervalMs) {
        if (!NativeAccess.isAvailable()) {
            System.err.println("[Profiler] Native library not available");
            return false;
        }
        this.samplingIntervalMs = intervalMs > 0 ? intervalMs : 500;
        Boolean result = NativeAccess.profilerInit(samplingIntervalMs);
        initialized = result != null && result;
        return initialized;
    }
    
    /**
     * Register a component for profiling.
     */
    public int registerComponent(String name) {
        if (!initialized) init(samplingIntervalMs);
        Integer result = NativeAccess.profilerRegisterComponent(name);
        return result != null ? result : -1;
    }
    
    /**
     * Register current thread as belonging to a component.
     */
    public boolean registerThread(String componentName) {
        if (!initialized) return false;
        long threadId = Thread.currentThread().threadId();
        Boolean result = NativeAccess.profilerRegisterThread(componentName, threadId);
        return result != null && result;
    }
    
    /**
     * Unregister current thread from a component.
     */
    public boolean unregisterThread(String componentName) {
        if (!initialized) return false;
        long threadId = Thread.currentThread().threadId();
        Boolean result = NativeAccess.profilerUnregisterThread(componentName, threadId);
        return result != null && result;
    }
    
    /**
     * Track memory allocation for a component.
     */
    public void trackAllocation(String componentName, long bytes) {
        if (!initialized) return;
        NativeAccess.profilerTrackAlloc(componentName, bytes);
    }
    
    /**
     * Track memory deallocation for a component.
     */
    public void trackFree(String componentName, long bytes) {
        if (!initialized) return;
        NativeAccess.profilerTrackFree(componentName, bytes);
    }
    
    /**
     * Start continuous profiling in a background thread.
     */
    public synchronized void start() {
        if (running) return;
        if (!initialized && !init(samplingIntervalMs)) return;
        
        Boolean result = NativeAccess.profilerStart();
        if (result == null || !result) return;
        
        running = true;
        samplerThread = new Thread(this::samplingLoop, "NativeProfiler-Sampler");
        samplerThread.setDaemon(true);
        samplerThread.setPriority(Thread.MIN_PRIORITY);
        samplerThread.start();
        
        System.out.println("[Profiler] Started with " + samplingIntervalMs + "ms interval");
    }
    
    /**
     * Stop profiling.
     */
    public synchronized void stop() {
        running = false;
        NativeAccess.profilerStop();
        if (samplerThread != null) {
            samplerThread.interrupt();
            samplerThread = null;
        }
        System.out.println("[Profiler] Stopped");
    }
    
    /**
     * Reset all metrics.
     */
    public void reset() {
        NativeAccess.profilerReset();
    }
    
    /**
     * Sample once (call this if not using background thread).
     */
    public boolean sample() {
        Boolean result = NativeAccess.profilerSample();
        return result != null && result;
    }
    
    private void samplingLoop() {
        while (running) {
            try {
                Thread.sleep(samplingIntervalMs);
                NativeAccess.profilerSample();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Get the number of registered components.
     */
    public int getComponentCount() {
        Integer result = NativeAccess.profilerComponentCount();
        return result != null ? result : 0;
    }
    
    /**
     * Get a snapshot of a specific component.
     */
    public ComponentSnapshot getComponentSnapshot(int index) {
        byte[] data = NativeAccess.profilerGetComponentSnapshot(index);
        if (data == null || data.length < 20) return null;
        
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
        int nameLen = buf.getInt();
        if (nameLen <= 0 || nameLen > buf.remaining()) return null;
        
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        
        long currentMemory = buf.getLong();
        long peakMemory = buf.getLong();
        buf.getLong(); // allocated bytes (reserved)
        double currentCpu = buf.getDouble();
        double avgCpu = buf.getDouble();
        double peakCpu = buf.getDouble();
        int threadCount = buf.getInt();
        buf.getInt(); // sample count (reserved)
        boolean highMem = buf.get() != 0;
        boolean highCpu = buf.get() != 0;
        
        return new ComponentSnapshot(name, currentMemory, peakMemory, 
            currentCpu, avgCpu, peakCpu, threadCount, highMem, highCpu);
    }
    
    /**
     * Get summary of all profiling data.
     */
    public ProfileSummary getSummary() {
        byte[] data = NativeAccess.profilerGetSummary();
        if (data == null || data.length < 48) return null;
        
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
        long rssBytes = buf.getLong();
        long vmemBytes = buf.getLong();
        double totalCpu = buf.getDouble();
        int componentCount = buf.getInt();
        int cpuCount = buf.getInt();
        long sampleCount = buf.getLong();
        long runtimeNs = buf.getLong();
        
        return new ProfileSummary(rssBytes, vmemBytes, totalCpu, 
            componentCount, cpuCount, sampleCount, runtimeNs);
    }
    
    /**
     * Get all component snapshots.
     */
    public List<ComponentSnapshot> getAllSnapshots() {
        List<ComponentSnapshot> list = new ArrayList<>();
        int count = getComponentCount();
        for (int i = 0; i < count; i++) {
            ComponentSnapshot snap = getComponentSnapshot(i);
            if (snap != null) list.add(snap);
        }
        return list;
    }
    
    /**
     * Print a formatted report to the console.
     */
    public String getReport() {
        String report = NativeAccess.profilerPrintReport();
        return report != null ? report : "[Profiler] No data available";
    }
    
    /**
     * Get a single-line status for continuous monitoring.
     */
    public String getStatusLine() {
        String status = NativeAccess.profilerStatusLine();
        return status != null ? status : "[PROF] Not available";
    }
    
    /**
     * Print report to stdout.
     */
    public void printReport() {
        System.out.println(getReport());
    }
    
    /**
     * Component metrics snapshot.
     */
    public record ComponentSnapshot(
        String name,
        long memoryBytes,
        long peakMemoryBytes,
        double cpuPercent,
        double avgCpuPercent,
        double peakCpuPercent,
        int threadCount,
        boolean highMemoryWarning,
        boolean highCpuWarning
    ) {
        public String getFormattedMemory() {
            return formatBytes(memoryBytes);
        }
        
        public String getFormattedCpu() {
            return String.format(Locale.ROOT, "%.1f%%", cpuPercent);
        }
        
        public static String formatBytes(long bytes) {
            if (bytes <= 0) return "0 B";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Profile summary.
     */
    public record ProfileSummary(
        long rssBytes,
        long vmemBytes,
        double totalCpuPercent,
        int componentCount,
        int cpuCount,
        long sampleCount,
        long runtimeNs
    ) {
        public String getFormattedRss() {
            return ComponentSnapshot.formatBytes(rssBytes);
        }
        
        public double getRuntimeSeconds() {
            return runtimeNs / 1_000_000_000.0;
        }
    }
}
