package main.core.sim.engine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import main.core.sim.api.SimEventBus;
import main.core.sim.data.SimDataGateway;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;

/**
 * Periodic tasks for Sim (Phase 1 stub).
 */
public final class SimScheduler {
    private Timer timer;
    private SimSettings settings;
    private SimPersonality personality;
    private SimDataGateway data;

    // Rate limit so we don't spam overlay from scheduler
    private long lastSpeakMs = 0L;

    /**
     * Starts periodic gentle checks. Safe to call multiple times; it restarts the timer.
     */
    public void start(SimSettings settings, SimPersonality personality, SimDataGateway data) {
        this.settings = settings;
        this.personality = personality;
        this.data = data;
        stop();
        // Check based on user-configured interval; lightweight local work
        int delayMs;
        try { delayMs = Math.max(60_000, settings.getNudgeIntervalMinutes() * 60_000); }
        catch (Throwable ignored) { delayMs = 120_000; }
        timer = new Timer(delayMs, e -> SwingUtilities.invokeLater(this::tick));
        timer.setRepeats(true);
        try { timer.start(); } catch (Throwable ignored) {}
    }

    public void stop() {
        if (timer != null) {
            try { timer.stop(); } catch (Throwable ignored) {}
            timer = null;
        }
    }

    // --- Internal ---
    private void tick() {
        if (settings == null || personality == null || data == null) return;
        if (!settings.isEnabled()) return;
        if (isQuietHoursNow(settings.getQuietHours())) return;
        // Engagement gating: in ON_CALL, do not emit autonomous nudges
        try {
            main.core.sim.prefs.SimSettings.EngagementMode mode = settings.getEngagementMode();
            if (mode == main.core.sim.prefs.SimSettings.EngagementMode.ON_CALL) return;
        } catch (Throwable ignored) {}

        // Look at the last few mood samples
        double avg = data.computeRecentMoodAverage(5);
        if (Double.isNaN(avg)) return;

        String msg = null;
        if (avg <= 3.0) {
            msg = switch (personality.getType()) {
                case GENTLE -> "Tough stretch? I’m here. Maybe a 2‑minute break helps.";
                case NEUTRAL -> "Mood’s been low—try noting one small need right now.";
                case PROACTIVE -> "Low trend detected—set one tiny action for the next 10 minutes.";
            };
            speakOncePer(25 * 60_000L, msg); // 25 minutes
        } else if (avg >= 9.0) {
            msg = switch (personality.getType()) {
                case GENTLE -> "You’ve been in great spirits. Capture a highlight?";
                case NEUTRAL -> "Strong mood lately—note what’s working.";
                case PROACTIVE -> "High momentum—pick a small stretch goal while it lasts.";
            };
            speakOncePer(30 * 60_000L, msg); // 30 minutes
        } else {
            // Neutral trend: only allow in fully PROACTIVE mode; suppress in HYBRID
            boolean allowNeutral = true;
            try {
                allowNeutral = settings.getEngagementMode() == main.core.sim.prefs.SimSettings.EngagementMode.PROACTIVE;
            } catch (Throwable ignored) {}
            if (allowNeutral) {
                msg = switch (personality.getType()) {
                    case GENTLE -> "How are you feeling right now—one sentence check‑in?";
                    case NEUTRAL -> "Quick check‑in: jot one thought to clear your mind.";
                    case PROACTIVE -> "Pulse check: list 1 task and 1 feeling, then proceed.";
                };
                speakOncePer(45 * 60_000L, msg); // 45 minutes
            }
        }
    }

    private void speakOncePer(long minIntervalMs, String msg) {
        if (msg == null || msg.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastSpeakMs < minIntervalMs) return;
        lastSpeakMs = now;
        try { SimEventBus.get().emitSpeak(msg); } catch (Throwable ignored) {}
    }

    private boolean isQuietHoursNow(String window) {
        if (window == null || window.isBlank() || !window.contains("-")) return false;
        String[] parts = window.trim().split("-");
        if (parts.length != 2) return false;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        try {
            LocalTime start = LocalTime.parse(parts[0].trim(), fmt);
            LocalTime end = LocalTime.parse(parts[1].trim(), fmt);
            LocalTime now = LocalTime.now();
            if (start.equals(end)) return true;
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
