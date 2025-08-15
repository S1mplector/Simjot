package main.ui.features.entries;

import javax.swing.Timer;
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

    public AutosaveManager(int delayMs, Runnable onSave, Runnable onStart, Runnable onEnd) {
        this.delayMs = delayMs;
        this.onSave = onSave;
        this.onStart = onStart != null ? onStart : () -> {};
        this.onEnd = onEnd != null ? onEnd : () -> {};
        this.timer = new Timer(delayMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.stop();
                try {
                    onSave.run();
                } finally {
                    onEnd.run();
                }
            }
        });
        this.timer.setRepeats(false);
    }

    public void markDirty() {
        if (!timer.isRunning()) {
            onStart.run();
        }
        timer.restart();
    }

    public void stop() {
        timer.stop();
    }
}
