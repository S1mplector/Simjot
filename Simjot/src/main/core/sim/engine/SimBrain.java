package main.core.sim.engine;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import main.core.sim.api.SimEventBus;
import main.core.sim.data.SimDataGateway;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;
import main.core.sim.llm.ollama.OllamaClient;
import main.core.sim.llm.prompt.PromptBuilder;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;

/**
 * Core decision engine: subscribes to Sim events and emits friendly overlay messages.
 * Phase 3: very lightweight local heuristics (no LLM).
 */
public final class SimBrain implements SimEventBus.Listener {
    private final SimSettings settings;
    private final SimPersonality personality;
    private final SimDataGateway data;

    // Optional LLM
    private final ExecutorService llmExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sim-LLM");
        t.setDaemon(true);
        return t;
    });
    private volatile SimLLMClient llm;
    private volatile long lastLlmMs = 0L;

    // Simple cooldowns to avoid spamming overlay
    private long lastSpeakMs = 0L;
    private long lastTypingCheckMs = 0L;

    public SimBrain(SimSettings settings, SimPersonality personality, SimDataGateway data) {
        this.settings = settings;
        this.personality = personality;
        this.data = data;
        SimEventBus.get().addListener(this);
        // Prepare LLM client lazily based on settings
        if (settings.isLlmEnabled()) {
            try {
                this.llm = new OllamaClient(settings.getOllamaEndpoint(), settings.getOllamaModel());
            } catch (Throwable ignored) {
                this.llm = null;
            }
        }
    }

    public void shutdown(){
        SimEventBus.get().removeListener(this);
        try { llmExec.shutdownNow(); } catch (Throwable ignored) {}
    }

    @Override
    public void onTyping(String latestText) {
        System.out.println("[SimBrain] onTyping len=" + (latestText==null?0:latestText.length()));
        if (!settings.isEnabled() || latestText == null) {
            System.out.println("[SimBrain] typing ignored: settings disabled or null text");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTypingCheckMs < 1000L) { // rate-limit checks
            System.out.println("[SimBrain] typing ignored: rate-limited");
            return;
        }
        lastTypingCheckMs = now;

        // Only analyze if there's some meaningful text
        String text = latestText.trim();
        if (text.length() < 10) {
            System.out.println("[SimBrain] typing ignored: below length threshold (" + text.length() + ")");
            return;
        }

        // Try LLM if enabled; otherwise fallback to heuristic
        if (settings.isLlmEnabled()) {
            if (llm == null) {
                try { 
                    llm = new OllamaClient(settings.getOllamaEndpoint(), settings.getOllamaModel()); 
                    System.out.println("[SimBrain] LLM client created: endpoint=" + settings.getOllamaEndpoint() + ", model=" + settings.getOllamaModel());
                } catch (Throwable ignored) {
                    System.out.println("[SimBrain] LLM client creation failed");
                }
            }
            if (llm != null && (now - lastLlmMs) >= 3_000L) { // 3s debounce, LLM-only mode
                lastLlmMs = now;
                final String sys = PromptBuilder.systemPrompt(personality.getType());
                final String usr = PromptBuilder.userFromTyping(text);
                System.out.println("[SimBrain] submitting LLM request; debounce ok");
                llmExec.submit(() -> {
                    try {
                        SimLLMRequest req = new SimLLMRequest(sys, usr, 120, 0.7);
                        long t0 = System.currentTimeMillis();
                        SimLLMResponse resp = llm.generate(req, Duration.ofSeconds(15));
                        long dt = System.currentTimeMillis() - t0;
                        String out = resp == null ? "" : resp.text;
                        System.out.println("[SimBrain] LLM response in " + dt + "ms, empty=" + (out==null || out.isBlank()));
                        if (out != null && !out.isBlank()) {
                            // Emit on EDT
                            try { 
                                System.out.println("[SimBrain] emitSpeak from LLM");
                                SimEventBus.get().emitSpeak(out); 
                            } catch (Throwable ignored) {
                                System.out.println("[SimBrain] emitSpeak failed: " + ignored.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("[SimBrain] LLM request failed: " + e);
                        // LLM-only: do nothing on failure
                    }
                });
                return;
            }
            // If LLM is enabled but not available or debounced, do nothing (LLM-only mode)
            if (llm == null) System.out.println("[SimBrain] LLM is null; skipping");
            else System.out.println("[SimBrain] LLM debounced; skipping");
            return;
        }

        // If LLM is disabled, retain heuristic behavior
        if (looksNegative(text)) {
            System.out.println("[SimBrain] heuristic negative detected; speaking");
            speakOncePer(20000L, messageFor("negative"));
        }
    }

    @Override
    public void onMoodChanged(double value) {
        System.out.println("[SimBrain] onMoodChanged value=" + value);
        if (!settings.isEnabled()) {
            System.out.println("[SimBrain] mood ignored: settings disabled");
            return;
        }
        // Assume mood slider scale roughly 0-10; treat <=3 as low, >=8 as high
        if (value <= 3.0) {
            System.out.println("[SimBrain] low mood -> speak");
            speakOncePer(25000L, messageFor("lowMood"));
        } else if (value >= 9.0) {
            System.out.println("[SimBrain] high mood -> speak");
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
        if (now - lastSpeakMs < minIntervalMs) { 
            System.out.println("[SimBrain] speak suppressed: cooldown");
            return; 
        }
        if (isQuietHoursNow()) { 
            System.out.println("[SimBrain] speak suppressed: quiet hours");
            return; 
        }
        lastSpeakMs = now;
        try { 
            System.out.println("[SimBrain] emitSpeak heuristic");
            SimEventBus.get().emitSpeak(msg); 
        } catch (Throwable ignored) {
            System.out.println("[SimBrain] emitSpeak failed: " + ignored.getMessage());
        }
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
