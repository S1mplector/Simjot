/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import main.infrastructure.ffi.NativeAccess;

/**
 * Background iCloud warm-up for seamless cross-Mac sync.
 */
public final class IcloudSyncService {
    private static final long WARMUP_COOLDOWN_MS = 15000L;
    private static final int PREFETCH_MAX_ITEMS = 4096;
    private static final int PREFETCH_MAX_DEPTH = 6;
    private static final int PREFETCH_QUERY_TIMEOUT_MS = 1200;
    private static final long CONFLICT_SCAN_COOLDOWN_MS = 60000L;
    private static final int CONFLICT_SCAN_LIMIT = 64;
    private static final int ENSURE_DOWNLOAD_TIMEOUT_MS = 1200;
    private static final int KEYFILE_TOUCH_LIMIT = 64 * 1024;
    private static final int KEYFILE_WAIT_SLEEP_MS = 150;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile long lastWarmupMs = 0L;
    private static volatile long lastConflictScanMs = 0L;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IcloudPrefetch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private IcloudSyncService() {}

    public static void warmupRoot(File root) {
        if (root == null) return;
        if (!AppDirectories.isIcloudRoot(root)) return;
        if (!NativeAccess.isAvailable()) return;

        long now = System.currentTimeMillis();
        if (now - lastWarmupMs < WARMUP_COOLDOWN_MS) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        lastWarmupMs = now;
        boolean lowPower = isLowPowerMode();

        EXEC.execute(() -> {
            try {
                prefetchRoot(root, lowPower);
            } catch (Throwable t) {
                IoLog.warn("icloud-warmup", "Warmup failed: " + t.getMessage(), t);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static void prefetchRoot(File root, boolean lowPower) {
        requestDownload(new File(root, "notebooks.json"));
        requestDownload(new File(new File(root, "settings"), "preferences.properties"));
        requestDownload(new File(new File(root, "mood"), "mood_log.txt"));
        requestDownload(new File(root, ".simjot_setup"));

        int notebookItems = lowPower ? 512 : PREFETCH_MAX_ITEMS;
        int notebookDepth = lowPower ? 3 : PREFETCH_MAX_DEPTH;
        int smallItems = lowPower ? 128 : 512;
        int smallDepth = lowPower ? 2 : 3;
        int conflictLimit = lowPower ? 16 : CONFLICT_SCAN_LIMIT;
        int conflictTimeout = lowPower ? 600 : PREFETCH_QUERY_TIMEOUT_MS;

        File notebooks = new File(root, "notebooks");
        if (notebooks.exists()) {
            prefetchDir(notebooks, notebookItems, notebookDepth);
        } else {
            prefetchDir(root, notebookItems, smallDepth);
        }

        prefetchDir(new File(root, "wallpapers"), smallItems, smallDepth);
        prefetchDir(new File(root, "fonts"), smallItems, smallDepth);

        scanConflicts(root, conflictLimit, conflictTimeout);
    }

    private static void requestDownload(File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        int status = NativeAccess.getMacIcloudItemStatus(path);
        if ((status & NativeAccess.ICLOUD_ITEM_EXISTS) == 0) return;
        if ((status & NativeAccess.ICLOUD_ITEM_CONFLICT) != 0) {
            IoLog.warn("icloud-conflict", "Unresolved iCloud conflict: " + path, null);
        }
        if ((status & NativeAccess.ICLOUD_ITEM_DOWNLOADED) != 0) return;
        if ((status & NativeAccess.ICLOUD_ITEM_DOWNLOADING) != 0) return;
        NativeAccess.startMacIcloudDownload(path);
        NativeAccess.ensureMacIcloudDownloaded(path, ENSURE_DOWNLOAD_TIMEOUT_MS);
    }

    private static void prefetchDir(File dir, int maxItems, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (maxItems <= 0 || maxDepth <= 0) return;
        String path = dir.getAbsolutePath();
        int requested = NativeAccess.prefetchMacIcloudQuery(path, maxItems, PREFETCH_QUERY_TIMEOUT_MS);
        if (requested < 0) {
            NativeAccess.prefetchMacIcloudDir(path, maxItems, maxDepth);
        }
    }

    private static void scanConflicts(File root, int maxItems, int timeoutMs) {
        if (root == null) return;
        if (maxItems <= 0 || timeoutMs <= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastConflictScanMs < CONFLICT_SCAN_COOLDOWN_MS) return;
        lastConflictScanMs = now;
        String raw = NativeAccess.listMacIcloudConflicts(root.getAbsolutePath(), maxItems, timeoutMs);
        if (raw == null || raw.isBlank()) return;
        String[] lines = raw.split("\n");
        if (lines.length == 0) return;
        IoLog.warn("icloud-conflicts", "Unresolved iCloud conflicts detected: " + lines.length, null);
        int limit = Math.min(lines.length, 5);
        for (int i = 0; i < limit; i++) {
            String line = lines[i];
            if (line != null && !line.isBlank()) {
                IoLog.warn("icloud-conflict", "Conflict: " + line, null);
            }
        }
    }

    private static boolean isLowPowerMode() {
        return NativeAccess.isMacLowPowerMode() || NativeAccess.isMacOnBattery();
    }

    public static void ensureKeyFilesAvailable(File root, int timeoutMs) {
        if (root == null) return;
        if (!AppDirectories.isIcloudRoot(root)) return;
        int timeout = Math.max(0, timeoutMs);
        File[] keyFiles = new File[] {
            new File(root, "notebooks.json"),
            new File(new File(root, "settings"), "preferences.properties"),
            new File(new File(root, "mood"), "mood_log.txt"),
            new File(root, ".simjot_setup")
        };
        if (NativeAccess.isAvailable()) {
            for (File file : keyFiles) {
                if (file == null) continue;
                try { NativeAccess.startMacIcloudDownload(file.getAbsolutePath()); } catch (Throwable ignored) {}
                if (timeout > 0) {
                    try { NativeAccess.ensureMacIcloudDownloaded(file.getAbsolutePath(), timeout); } catch (Throwable ignored) {}
                }
            }
            return;
        }

        for (File file : keyFiles) {
            touchFile(file, KEYFILE_TOUCH_LIMIT);
        }
        if (timeout == 0) return;
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            if (allNonEmpty(keyFiles)) return;
            try { Thread.sleep(KEYFILE_WAIT_SLEEP_MS); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void touchFile(File file, int maxBytes) {
        if (file == null || maxBytes <= 0) return;
        if (!file.exists() || !file.isFile()) return;
        int limit = Math.min(maxBytes, 64 * 1024);
        byte[] buffer = new byte[limit];
        int remaining = maxBytes;
        try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(file))) {
            while (remaining > 0) {
                int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read <= 0) break;
                remaining -= read;
            }
        } catch (java.io.IOException ignored) {}
    }

    private static boolean allNonEmpty(File[] files) {
        if (files == null || files.length == 0) return true;
        for (File file : files) {
            if (file == null) continue;
            if (file.exists() && file.isFile() && file.length() > 0) continue;
            return false;
        }
        return true;
    }
}
