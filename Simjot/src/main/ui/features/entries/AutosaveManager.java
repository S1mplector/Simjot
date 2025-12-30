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

package main.ui.features.entries;

import javax.swing.SwingUtilities;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simple debounce-based autosave manager. Call markDirty() on edits; when
 * no more edits occur for the configured delay, it will invoke the save action.
 */
public class AutosaveManager {
    private final int delayMs;
    private final Runnable onSave;
    private final Runnable onStart;
    private final Runnable onEnd;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pending;
    private volatile boolean saving = false;

    public AutosaveManager(int delayMs, Runnable onSave, Runnable onStart, Runnable onEnd) {
        this.delayMs = delayMs;
        this.onSave = onSave;
        this.onStart = onStart != null ? onStart : () -> {};
        this.onEnd = onEnd != null ? onEnd : () -> {};
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutosaveWorker");
            t.setDaemon(true);
            return t;
        });
    }

    public void markDirty() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        pending = scheduler.schedule(() -> {
            if (saving) return; // avoid overlapping saves
            saving = true;
            SwingUtilities.invokeLater(onStart);
            try {
                onSave.run();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    try {
                        onEnd.run();
                    } finally {
                        saving = false;
                    }
                });
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }
}
