package main.core.sim;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Periodic tasks for Sim (Phase 1 stub).
 */
public final class SimScheduler {
    private Timer timer;

    public void start() {
        // Phase 1: placeholder no-op timer (disabled by default)
        stop();
        timer = new Timer(60000, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                // Future: periodic checks
            }
        });
        timer.setRepeats(true);
        // Do not start until Phase 3; keeping constructed for wiring completeness
    }

    public void stop() {
        if (timer != null) {
            try { timer.stop(); } catch (Throwable ignored) {}
            timer = null;
        }
    }
}
