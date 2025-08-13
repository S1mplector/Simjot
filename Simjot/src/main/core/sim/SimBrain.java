package main.core.sim;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Core decision engine: subscribes to Sim events and emits friendly overlay messages.
 * Phase 3: very lightweight local heuristics (no LLM).
 */
public final class SimBrain implements SimEventBus.Listener {
    private final SimSettings settings;
    private final SimPersonality personality;
    private final SimDataGateway data;

    // Simple cooldowns to avoid spamming overlay
    private long lastSpeakMs = 0L;
    private long lastTypingCheckMs = 0L;

    public SimBrain(SimSettings settings, SimPersonality personality, SimDataGateway data) {
        this.settings = settings;
        this.personality = personality;
        this.data = data;
        SimEventBus.get().addListener(this);
    }

    public void shutdown(){
        SimEventBus.get().removeListener(this);
    }

    @Override
    public void onTyping(String latestText) {
        if (!settings.isEnabled() || latestText == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingCheckMs < 1500L) return; // rate-limit checks
        lastTypingCheckMs = now;

        // Only analyze if there's some meaningful text
        String text = latestText.trim();
        if (text.length() < 40) return;

        if (looksNegative(text)) {
            speakOncePer(20000L, messageFor("negative"));
        }
    }

    @Override
    public void onMoodChanged(double value) {
        if (!settings.isEnabled()) return;
        // Assume mood slider scale roughly 0-10; treat <=3 as low, >=8 as high
        if (value <= 3.0) {
            speakOncePer(25000L, messageFor("lowMood"));
        } else if (value >= 9.0) {
            speakOncePer(30000L, messageFor("highMood"));
        }
    }

    @Override
    public void onCardSwitched(String cardId) {
        // Could greet on returning to main menu in proactive mode (optional)
        if (!settings.isEnabled() || cardId == null) return;
        if (personality.getType() == SimPersonality.Type.PROACTIVE && "Main Menu".equalsIgnoreCase(cardId)) {
            speakOncePer(60000L, messageFor("greet"));
        }
    }

    // --- Helpers ---

    private void speakOncePer(long minIntervalMs, String msg) {
        long now = System.currentTimeMillis();
        if (now - lastSpeakMs < minIntervalMs) return;
        if (isQuietHoursNow()) return;
        lastSpeakMs = now;
        try { SimEventBus.get().emitSpeak(msg); } catch (Throwable ignored) {}
    }

    private boolean looksNegative(String text) {
        String lt = text.toLowerCase();
        // Very small keyword set; can be expanded later or replaced by sentiment
        String[] bad = {"sad", "anxious", "tired", "lonely", "failed", "hopeless", "overwhelmed", "angry", "worthless", "depressed"};
        int hits = 0;
        for (String k : bad) { if (lt.contains(k)) hits++; }
        return hits >= 2 || (hits == 1 && lt.contains("very"));
    }

    private String messageFor(String kind) {
        SimPersonality.Type t = personality.getType();
        switch (kind) {
            case "negative":
                return switch (t) {
                    case GENTLE -> "I’m here with you. Want to try a short breathing break?";
                    case NEUTRAL -> "I noticed some heavy tone. Maybe pause and jot one small win today?";
                    case PROACTIVE -> "Let’s reset: 3 deep breaths, then list 2 things in your control now.";
                };
            case "lowMood":
                return switch (t) {
                    case GENTLE -> "Rough patch—be kind to yourself. A warm drink or a short stroll could help.";
                    case NEUTRAL -> "Mood looks low. Consider a 5‑minute break or write what you need right now.";
                    case PROACTIVE -> "Low mood detected. Start a quick plan: next 10 minutes—one simple task.";
                };
            case "highMood":
                return switch (t) {
                    case GENTLE -> "Love this energy. Capture a highlight so you can revisit it later.";
                    case NEUTRAL -> "Great mood! Maybe note what contributed—future you will thank you.";
                    case PROACTIVE -> "You’re on a roll—set a tiny stretch goal while the momentum is high.";
                };
            case "greet":
                return switch (t) {
                    case GENTLE -> "Welcome back. What would feel good to write today?";
                    case NEUTRAL -> "Hi again. Ready to continue?";
                    case PROACTIVE -> "Let’s jump in—pick a notebook and I’ll keep an eye out for insights.";
                };
            default:
                return "";
        }
    }

    private boolean isQuietHoursNow() {
        String qh = settings.getQuietHours();
        if (qh == null || qh.isBlank() || !qh.contains("-")) return false;
        String[] parts = qh.trim().split("-");
        if (parts.length != 2) return false;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        try {
            LocalTime start = LocalTime.parse(parts[0].trim(), fmt);
            LocalTime end = LocalTime.parse(parts[1].trim(), fmt);
            LocalTime now = LocalTime.now();
            if (start.equals(end)) return true; // degenerate -> always quiet
            if (start.isBefore(end)) {
                // Same-day window, e.g., 22:00-23:30
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                // Overnight window, e.g., 22:00-07:00
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
