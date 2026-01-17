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
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile long lastWarmupMs = 0L;
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

        EXEC.execute(() -> {
            try {
                prefetchRoot(root);
            } catch (Throwable t) {
                IoLog.warn("icloud-warmup", "Warmup failed: " + t.getMessage(), t);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static void prefetchRoot(File root) {
        requestDownload(new File(root, "notebooks.json"));
        requestDownload(new File(new File(root, "settings"), "preferences.properties"));
        requestDownload(new File(new File(root, "mood"), "mood_log.txt"));
        requestDownload(new File(root, ".simjot_setup"));

        File notebooks = new File(root, "notebooks");
        if (notebooks.exists()) {
            NativeAccess.prefetchMacIcloudDir(notebooks.getAbsolutePath(), PREFETCH_MAX_ITEMS, PREFETCH_MAX_DEPTH);
        } else {
            NativeAccess.prefetchMacIcloudDir(root.getAbsolutePath(), PREFETCH_MAX_ITEMS, 3);
        }

        File wallpapers = new File(root, "wallpapers");
        if (wallpapers.exists()) {
            NativeAccess.prefetchMacIcloudDir(wallpapers.getAbsolutePath(), 512, 3);
        }

        File fonts = new File(root, "fonts");
        if (fonts.exists()) {
            NativeAccess.prefetchMacIcloudDir(fonts.getAbsolutePath(), 256, 3);
        }
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
    }
}
