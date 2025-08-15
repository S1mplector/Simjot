package main.ui.features.entries;

import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Simple debounce-based autosave manager. Call markDirty() on edits; when
 * no more edits occur for the configured delay, it will invoke the save action.
 */
public class AutosaveManager {
    private final int delayMs;
    private final Runnable onSave;
    private final Runnable onStart;
    private final Runnable onEnd;

    private final Timer timer;
    private volatile boolean saving = false;

    public AutosaveManager(int delayMs, Runnable onSave, Runnable onStart, Runnable onEnd) {
        this.delayMs = delayMs;
        this.onSave = onSave;
        this.onStart = onStart != null ? onStart : () -> {};
        this.onEnd = onEnd != null ? onEnd : () -> {};
        this.timer = new Timer(delayMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.stop();
                if (saving) return; // avoid overlapping saves
                saving = true;
                // Run save off the EDT to avoid UI stalls (especially with short delays)
                new Thread(() -> {
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
                }, "AutosaveWorker").start();
            }
        });
        this.timer.setRepeats(false);
    }

    public void markDirty() {
        if (!timer.isRunning() && !saving) {
            onStart.run();
        }
        timer.restart();
    }

    public void stop() {
        timer.stop();
    }
}
