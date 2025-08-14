package main.core.sim.engine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import main.core.sim.api.SimEventBus;
import main.core.sim.data.SimDataGateway;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.ollama.OllamaClient;
import main.core.sim.llm.openai.OpenAIClient;
import main.core.sim.llm.prompt.PromptBuilder;
import main.core.sim.memory.MemoryStore;
import main.core.sim.memory.PersistentMemoryStore;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;
import main.core.sim.state.UserState;
 import main.core.sim.proactive.TriggerStatsStore;
 import main.core.sim.retrieval.RecencyBuffer;
 import main.core.sim.retrieval.RetrievalRanker;
 import main.core.sim.critic.CriticPromptDecorator;
 import main.core.sim.proactive.ProactiveTriggerEngine;

/**
 * Core decision engine: subscribes to Sim events and emits friendly overlay messages.
 */
public final class SimBrain implements SimEventBus.Listener {
    private final SimSettings settings;
    private final SimPersonality personality;
    private final SimDataGateway data;

    // Proactive context and memory
    private final UserState userState = new UserState();
    private final MemoryStore memory = new MemoryStore();
    private final PersistentMemoryStore persistent = new PersistentMemoryStore();
    // Add lightweight stores for proactive stats and recency-aware retrieval
    private final TriggerStatsStore triggerStats = new TriggerStatsStore();
    private final RecencyBuffer recency = new RecencyBuffer(30, 60 * 60 * 1000L); // last 1h snippets

    // Optional LLM
    private volatile SimLLMClient llm;
    private volatile long lastLlmMs = 0L;
    // Track which provider the current llm instance was created for
    private volatile String llmProviderActive = null;

    // Streaming turn handling (cancel on typing, emit as tokens arrive)
    private final TurnManager turns = new TurnManager();

    // Simple cooldowns to avoid spamming overlay
    private long lastSpeakMs = 0L;
    // Typing debounce (replace coarse rate-limit)
    private static final long TYPING_DEBOUNCE_MS = 1200L;
    private final Object typingLock = new Object();
    private volatile String latestTyping = "";
    private java.util.concurrent.ScheduledFuture<?> typingEvalFuture;
    // Same-trigger cooldown tracking
    private String lastTriggerKey = "";
    private long lastTriggerMs = 0L;
    // Track last mood value for contextual replies
    private volatile Double lastMoodValue = null;
    // Live per-entry emotion tags (short-lived, per current entry). Used to enrich prompts.
    private static final long EMOTION_FRESH_MS = 15 * 60 * 1000L; // 15 minutes
    private final java.util.ArrayDeque<EmotionTag> recentEmotions = new java.util.ArrayDeque<>(24);
    private volatile String currentEmotionEntryId = null;

    // Entry-start evaluation state (to suppress early nudges if content hasn't diverged yet)
    private volatile long entryStartTimeMs = 0L;
    private volatile String entryStartSnapshot = "";
    private volatile long entryStartSuppressedUntilMs = 0L;

    // Scheduler
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Sim-Brain-Tick");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> tickFuture;

    public SimBrain(SimSettings settings, SimPersonality personality, SimDataGateway data) {
        this.settings = settings;
        this.personality = personality;
        this.data = data;
        SimEventBus.get().addListener(this);
        // Prepare LLM client lazily based on settings
        if (settings.isLlmEnabled()) {
            try {
                this.llm = createClientFromSettings();
            } catch (Throwable ignored) {
                this.llm = null;
            }
        }
        // Start lightweight proactive evaluation loop
        startTickLoop();

        // Load persistent memories and ingest notebooks in background
        try { persistent.load(); } catch (Throwable ignored) {}
        scheduler.execute(() -> {
            try { persistent.ingestAllNotebooks(this.data); } catch (Throwable ignored) {}
        });

        // Schedule a gentle on-launch check-in
        scheduler.schedule(this::startupCheckIn, 3, TimeUnit.SECONDS);
    }

    public void shutdown(){
        SimEventBus.get().removeListener(this);
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        try { if (tickFuture != null) tickFuture.cancel(true); } catch (Throwable ignored) {}
        try { scheduler.shutdownNow(); } catch (Throwable ignored) {}
    }

    @Override
    public void onTyping(String latestText) {
        System.out.println("[SimBrain] onTyping len=" + (latestText==null?0:latestText.length()));
        if (!settings.isEnabled() || latestText == null) {
            System.out.println("[SimBrain] typing ignored: settings disabled or null text");
            return;
        }
        // Cancel any ongoing streamed response as soon as user types again
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        // Debounce evaluation instead of per-keystroke rate-limit
        synchronized (typingLock) {
            latestTyping = latestText;
            if (typingEvalFuture != null) {
                try { typingEvalFuture.cancel(false); } catch (Throwable ignored) {}
            }
            typingEvalFuture = scheduler.schedule(this::evaluateTyping, TYPING_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onMoodChanged(double value) {
        System.out.println("[SimBrain] onMoodChanged value=" + value);
        if (!settings.isEnabled()) {
            System.out.println("[SimBrain] mood ignored: settings disabled");
            return;
        }
        // remember last mood for contextual chat replies
        lastMoodValue = value;
        // Assume mood slider scale 0–100; treat <=20 as low, >=80 as high
        if (value <= 20.0 || value >= 80.0) {
            boolean isLow = value <= 20.0;
            System.out.println("[SimBrain] " + (isLow?"low":"high") + " mood -> engine");
            if (settings.isLlmEnabled()) ensureLlm();
            long now = System.currentTimeMillis();
            ProactiveTriggerEngine.Input in = new ProactiveTriggerEngine.Input();
            in.nowMs = now;
            in.enabled = settings.isEnabled();
            in.quietHours = isQuietHoursNow();
            String last = userState.getLastText();
            if (last == null) last = "";
            String trimmed = last.trim();
            in.lastText = last;
            in.millisSinceLastType = userState.millisSinceLastTyping();
            in.negativeTone = looksNegative(trimmed);
            in.shrinkEvents = userState.getShrinkEvents();
            try { in.emptyNoTokenStreak = turns.getEmptyNoTokenStreak(); } catch (Throwable ignored) {}
            in.lastTriggerKey = lastTriggerKey;
            in.lastTriggerAtMs = lastTriggerMs;
            in.holdOffEntryStart = shouldHoldOffForEntryStart(trimmed);
            in.preview = safeSummary();
            in.conversationSummary = safeSummary();
            in.emotionsContext = emotionsContextForPrompt();
            in.personalityType = personality.getType();
            in.persistent = persistent;
            in.session = memory;
            in.recency = recency;

            ProactiveTriggerEngine.Output out = ProactiveTriggerEngine.evaluateMoodCheckIn(in, isLow);
            if (!out.fire) return;
            if (llm != null) {
                lastSpeakMs = now;
                lastTriggerKey = out.triggerKey;
                lastTriggerMs = now;
                try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
                try {
                    turns.maybeSpeakProactively(llm, out.systemPrompt, out.userPrompt, out.preface);
                } catch (Throwable ignored) {}
            } else {
                try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onEmotionTagged(String entryId, String emotion, double intensity) {
        // Normalize inputs
        String e = emotion == null ? "" : emotion.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.isEmpty()) return;
        if (intensity < 0) intensity = 0; else if (intensity > 100) intensity = 100;
        long now = System.currentTimeMillis();
        currentEmotionEntryId = entryId; // track which entry emotions relate to
        synchronized (recentEmotions) {
            recentEmotions.addLast(new EmotionTag(now, entryId, e, intensity));
            // bound by size
            while (recentEmotions.size() > 24) recentEmotions.removeFirst();
            pruneOldEmotions(now);
        }
    }

    @Override
    public void onCardSwitched(String cardId) {
        // Could greet on returning to main menu in proactive mode (optional)
        if (!settings.isEnabled() || cardId == null) return;
        if (personality.getType() == SimPersonality.Type.PROACTIVE && "Main Menu".equalsIgnoreCase(cardId)) {
            if (settings.isLlmEnabled()) ensureLlm();
            long now = System.currentTimeMillis();
            ProactiveTriggerEngine.Input in = new ProactiveTriggerEngine.Input();
            in.nowMs = now;
            in.enabled = settings.isEnabled();
            in.quietHours = isQuietHoursNow();
            String last = userState.getLastText();
            if (last == null) last = "";
            String trimmed = last.trim();
            in.lastText = last;
            in.millisSinceLastType = userState.millisSinceLastTyping();
            in.negativeTone = looksNegative(trimmed);
            in.shrinkEvents = userState.getShrinkEvents();
            try { in.emptyNoTokenStreak = turns.getEmptyNoTokenStreak(); } catch (Throwable ignored) {}
            in.lastTriggerKey = lastTriggerKey;
            in.lastTriggerAtMs = lastTriggerMs;
            in.holdOffEntryStart = shouldHoldOffForEntryStart(trimmed);
            in.preview = last;
            in.conversationSummary = safeSummary();
            in.emotionsContext = emotionsContextForPrompt();
            in.personalityType = personality.getType();
            in.persistent = persistent;
            in.session = memory;
            in.recency = recency;

            ProactiveTriggerEngine.Output out = ProactiveTriggerEngine.evaluateMenuGreeting(in);
            if (!out.fire) return;
            if (llm != null) {
                lastSpeakMs = now;
                lastTriggerKey = out.triggerKey;
                lastTriggerMs = now;
                try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
                try {
                    turns.maybeSpeakProactively(llm, out.systemPrompt, out.userPrompt, out.preface);
                } catch (Throwable ignored) {}
            } else {
                try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
            }
        }
        // Heuristic: when switching into an editor, treat as a new entry start
        if (cardId.startsWith("Editor_")) {
            entryStartTimeMs = System.currentTimeMillis();
            entryStartSnapshot = "";
            entryStartSuppressedUntilMs = 0L;
        }
    }

    @Override
    public void onChatFollowupRequested() {
        // Phase 1: produce a single brief follow-up reply
        if (!settings.isEnabled()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now; // prevent immediate other nudges

        if (settings.isLlmEnabled()) ensureLlm();
        if (llm != null) {
            try {
                String sys = CriticPromptDecorator.decorateSystem(
                        PromptBuilder.systemPrompt(personality.getType())
                );
                String usr = String.join(" ",
                        "The user tapped 'Chat more' after your previous supportive message.",
                        "Respond with ONE short, compassionate follow-up question (1 sentence).",
                        "No preface, no meta; plain text only.");
                // Add a small retrieval context
                String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 3, 3);
                if (ctx != null && !ctx.isBlank()) usr = "Context: " + ctx + "\n\n" + usr;
                // Disable generic fallback in overlay chat follow-up
                turns.maybeSpeakProactively(llm, sys, usr, /*allowFallback=*/false);
                return;
            } catch (Throwable ignored) {
                // fall through to heuristic
            }
        }
        // No static fallback when LLM unavailable; suppress output
    }

    @Override
    public void onChatMessage(String userText) {
        if (!settings.isEnabled()) return;
        String txt = userText == null ? "" : userText.trim();
        if (txt.isEmpty()) return;
        // Derive and persist durable facts from chat messages as well
        try { persistent.ingestText(txt); } catch (Throwable ignored) {}
        try { recency.add(txt); } catch (Throwable ignored) {}
        // Safety: crisis detection inside chat path
        if (isCrisisText(txt)) {
            speakNow(crisisMessage(personality.getType()));
            return;
        }
        // Detect goodbyes and exit overlay gracefully
        if (isGoodbye(txt)) {
            String bye = farewellMessage();
            try { SimEventBus.get().emitSpeak(bye); } catch (Throwable ignored) {}
            try { SimEventBus.get().emitQuitRequested(); } catch (Throwable ignored) {}
            lastSpeakMs = System.currentTimeMillis();
            return;
        }
        lastSpeakMs = System.currentTimeMillis();
        if (settings.isLlmEnabled()) ensureLlm();
        if (llm != null) {
            String sys = CriticPromptDecorator.decorateSystem(
                    PromptBuilder.systemPrompt(personality.getType())
            );
            // Provide mood context and guidance for reciprocal greetings and empathy
            String usr = String.join(" ",
                    "The user is chatting with you in a small overlay.",
                    "Reply concisely (1–3 sentences), compassionate and supportive.",
                    "Ask at most one brief follow-up question if it helps them open up.",
                    moodContextForPrompt(),
                    emotionsContextForPrompt(),
                    factsContextForPrompt(),
                    "If they greet you (e.g., 'how are you'), briefly say how you're doing and reciprocate, optionally acknowledging their recent mood.",
                    "Plain text only.\n\nUser:", txt);
            String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 5, 5);
            if (ctx != null && !ctx.isBlank()) usr = "Context: " + ctx + "\n\n" + usr;
            try {
                // Disable generic fallback inside overlay chat messages
                turns.maybeSpeakProactively(llm, sys, usr, /*allowFallback=*/false);
                return;
            } catch (Throwable ignored) {}
        }
        // No static fallback when LLM unavailable; suppress output
    }

    @Override
    public void onChatEnded() {
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        // No other state needed now; cooldown prevents immediate nudges
        lastSpeakMs = System.currentTimeMillis();
    }

    // --- Helpers ---

    private void startTickLoop() {
        try {
            if (tickFuture != null && !tickFuture.isCancelled()) tickFuture.cancel(true);
        } catch (Throwable ignored) {}
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, 2, 2, TimeUnit.SECONDS);
    }

    // Periodic proactive evaluation delegated to ProactiveTriggerEngine
    private void tick() {
        if (!settings.isEnabled()) return;
        long now = System.currentTimeMillis();
        // Global cooldown across proactive messages
        if (now - lastSpeakMs < 15000L) return;
        if (isQuietHoursNow()) return;

        String last = userState.getLastText();
        if (last == null) last = "";
        String trimmed = last.trim();

        // Prepare LLM if enabled
        if (settings.isLlmEnabled()) ensureLlm();

        // Build input for the engine
        ProactiveTriggerEngine.Input in = new ProactiveTriggerEngine.Input();
        in.nowMs = now;
        in.enabled = settings.isEnabled();
        in.quietHours = isQuietHoursNow();
        in.lastText = last;
        in.millisSinceLastType = userState.millisSinceLastTyping();
        in.negativeTone = looksNegative(trimmed);
        in.shrinkEvents = userState.getShrinkEvents();
        try { in.emptyNoTokenStreak = turns.getEmptyNoTokenStreak(); } catch (Throwable ignored) {}
        in.lastTriggerKey = lastTriggerKey;
        in.lastTriggerAtMs = lastTriggerMs;
        in.holdOffEntryStart = shouldHoldOffForEntryStart(trimmed);
        in.preview = last.length() > 200 ? last.substring(0,199) + "…" : last;
        in.conversationSummary = safeSummary();
        in.emotionsContext = emotionsContextForPrompt();
        in.personalityType = personality.getType();
        in.persistent = persistent;
        in.session = memory;
        in.recency = recency;

        ProactiveTriggerEngine.Output out = ProactiveTriggerEngine.evaluate(in);
        if (!out.fire) return;

        if (llm != null) {
            lastSpeakMs = now;
            lastTriggerKey = out.triggerKey;
            lastTriggerMs = now;
            try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
            try {
                System.out.println("[SimBrain] proactive tick -> streaming LLM (" + out.triggerKey + ")");
                turns.maybeSpeakProactively(llm, out.systemPrompt, out.userPrompt, out.preface);
            } catch (Throwable ignored) {
                System.out.println("[SimBrain] proactive turn start failed");
            }
        } else {
            // LLM unavailable: suppress speech but record that a trigger would have fired
            try { triggerStats.record(out.triggerKey); } catch (Throwable ignored) {}
        }

        // Reset edit burst counter to avoid immediate retriggering
        userState.resetShrinkCounter();
    }

    // --- Debounced typing evaluation ---
    private void evaluateTyping() {
        String text;
        synchronized (typingLock) { text = latestTyping; }
        if (!settings.isEnabled() || text == null) return;
        String trimmed = text.trim();
        if (trimmed.length() < 10) {
            System.out.println("[SimBrain] typing ignored after debounce: short (" + trimmed.length() + ")");
            return;
        }
        // Update state and memory once per debounce fire
        try { userState.onTyping(text); } catch (Throwable ignored) {}
        try { memory.addEpisodic(trimmed.length() > 140 ? trimmed.substring(0, 140) + "…" : trimmed); } catch (Throwable ignored) {}
        try { persistent.ingestText(text); } catch (Throwable ignored) {}
        try { recency.add(trimmed); } catch (Throwable ignored) {}

        // Capture initial snapshot for entry-start suppression
        maybeCaptureEntryStartSnapshot(trimmed);

        // Crisis detection remains immediate
        if (isCrisisText(trimmed)) {
            System.out.println("[SimBrain] crisis intent detected; speaking immediately");
            String msg = crisisMessage(personality.getType());
            speakNow(msg);
            return;
        }
        // If LLM is off, do not emit pre-written nudges; proactive messages are LLM-only now
    }

    // --- Entry-start evaluation and suppression ---
    private void maybeCaptureEntryStartSnapshot(String trimmedText) {
        // If we recently switched into an editor or there's no snapshot yet, capture a short opening
        if (entryStartSnapshot == null || entryStartSnapshot.isEmpty()) {
            if (trimmedText.length() >= 40) {
                entryStartSnapshot = trimmedText.substring(0, Math.min(180, trimmedText.length()));
                if (entryStartTimeMs == 0L) entryStartTimeMs = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldHoldOffForEntryStart(String currentText) {
        long now = System.currentTimeMillis();
        // Only consider within the first few minutes and if we have a snapshot
        if (entryStartSnapshot == null || entryStartSnapshot.isEmpty()) return false;
        if (entryStartTimeMs == 0L) return false;
        if (now < entryStartSuppressedUntilMs) return true; // temporary backoff window
        long elapsed = now - entryStartTimeMs;
        if (elapsed > 5 * 60 * 1000L) return false; // after 5 minutes, stop suppressing

        // Compute simple token overlap ratio between snapshot and current
        double sim = jaccardSimilarity(entryStartSnapshot, currentText);
        if (sim >= 0.85) { // very similar => likely still expanding the opening idea
            // set a small suppression window to avoid frequent checks
            entryStartSuppressedUntilMs = now + 10000L; // 10s
            return true;
        }
        return false;
    }

    private static double jaccardSimilarity(String a, String b) {
        java.util.Set<String> sa = tokenize(a);
        java.util.Set<String> sb = tokenize(b);
        if (sa.isEmpty() || sb.isEmpty()) return 0.0;
        int inter = 0;
        for (String t : sa) if (sb.contains(t)) inter++;
        int union = sa.size() + sb.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private static java.util.Set<String> tokenize(String s) {
        java.util.Set<String> set = new java.util.HashSet<>();
        String[] parts = s.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\u00C0-\u024F\s]", " ")
                .split("\\s+");
        for (String p : parts) {
            if (p.length() >= 2) set.add(p);
        }
        return set;
    }

    private void ensureLlm() {
        String desired;
        try { desired = settings.getLlmProvider(); } catch (Throwable t) { desired = "ollama"; }
        if (desired == null || desired.isBlank()) desired = "ollama";
        desired = desired.toLowerCase(java.util.Locale.ROOT);

        // Recreate client if none or provider changed
        if (llm == null || llmProviderActive == null || !desired.equals(llmProviderActive)) {
            try {
                if (llm != null) {
                    System.out.println("[SimBrain] switching LLM provider from " + llmProviderActive + " to " + desired);
                }
                llm = createClientFromSettings();
            } catch (Throwable ignored) {
                llm = null;
            }
        }
    }

    private SimLLMClient createClientFromSettings() {
        String provider;
        try {
            provider = settings.getLlmProvider();
        } catch (Throwable t) {
            provider = "ollama";
        }
        if (provider == null || provider.isBlank()) provider = "ollama";
        provider = provider.toLowerCase(java.util.Locale.ROOT);
        switch (provider) {
            case "openai": {
                String apiKey = "";
                String model = "gpt-4o-mini";
                String baseUrl = "https://api.openai.com";
                try { apiKey = settings.getOpenAIApiKey(); } catch (Throwable ignored) {}
                try { model = settings.getOpenAIModel(); } catch (Throwable ignored) {}
                try { baseUrl = settings.getOpenAIBaseUrl(); } catch (Throwable ignored) {}
                if (apiKey == null) apiKey = "";
                if (model == null || model.isBlank()) model = "gpt-4o-mini";
                if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com";
                llmProviderActive = "openai";
                return new OpenAIClient(apiKey, model, baseUrl);
            }
            case "ollama":
            default: {
                String endpoint = settings.getOllamaEndpoint();
                String model = settings.getOllamaModel();
                llmProviderActive = "ollama";
                return new OllamaClient(endpoint, model);
            }
        }
    }

    private String safeSummary() {
        try {
            String facts = persistent.getFactsSummary(3);
            if (facts == null || facts.isBlank()) facts = memory.getFactsSummary(3);
            String convo = memory.getConversationSummary();
            if (facts.isBlank()) return convo;
            if (convo == null || convo.isBlank()) return "Facts: " + facts;
            return "Facts: " + facts + " | Recent: " + convo;
        } catch (Throwable t) {
            return "";
        }
    }

    // Include a compact facts context line for prompts
    private String factsContextForPrompt() {
        try {
            String f = persistent.getFactsSummary(2);
            if (f == null || f.isBlank()) f = memory.getFactsSummary(2);
            if (f == null || f.isBlank()) return "";
            return "Known user facts: " + f + ".";
        } catch (Throwable ignored) { return ""; }
    }

    private void startupCheckIn() {
        if (!settings.isEnabled()) return;
        if (isQuietHoursNow()) return;
        try {
            String facts = persistent.getFactsSummary(2);
            if (facts == null || facts.isBlank()) facts = memory.getFactsSummary(2);
            String msg;
            if (facts != null && !facts.isBlank()) {
                msg = "Hi — just checking in. How’s your day starting? I remember " + facts + ". Anything you’d like to talk about today?";
            } else {
                msg = "Hi — how’s your day going? I’m here if you want to jot or chat for a minute.";
            }
            speakOncePer(10000L, msg);
        } catch (Throwable ignored) {}
    }

    private String farewellMessage() {
        switch (personality.getType()) {
            case GENTLE: return "Okay—take good care. I’m here whenever you want to talk.";
            case PROACTIVE: return "Got it! I’ll tuck away for now—ping me anytime.";
            case NEUTRAL:
            default: return "Okay, talk soon.";
        }
    }

    private boolean isGoodbye(String txt) {
        String t = txt.toLowerCase(java.util.Locale.ROOT).trim();
        // simple forms: bye, goodbye, see you, talk later, quit, exit
        return t.matches(".*\\b(bye|goodbye|see you|talk later|gotta go|time to go|quit|exit)\\b.*");
    }

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

    // Bypass cooldowns and quiet hours for emergency messages only
    private void speakNow(String msg) {
        lastSpeakMs = System.currentTimeMillis();
        try {
            System.out.println("[SimBrain] emitSpeak immediate");
            SimEventBus.get().emitSpeak(msg);
        } catch (Throwable ignored) {
            System.out.println("[SimBrain] emitSpeak failed: " + ignored.getMessage());
        }
    }

    private boolean looksNegative(String text) {
        String lt = text.toLowerCase();
        // Very small keyword set; can be expanded later or replaced by sentiment
        String[] bad = {"sad", "anxious", "tired", "lonely", "failed", "hopeless", "overwhelmed", "angry", "worthless", "depressed", "depressing", "boring", "dull", "numb", "empty"};
        int hits = 0;
        for (String k : bad) { if (lt.contains(k)) hits++; }
        return hits >= 2 || (hits == 1 && lt.contains("very"));
    }

    // Detect self-harm / suicide intent in text (simple lexical pass; improve later)
    private boolean isCrisisText(String text) {
        String lt = text.toLowerCase();
        String[] crisis = {
                "kill myself", "suicide", "end my life", "take my life",
                "i don't want to live", "i dont want to live", "die by suicide",
                "rather die", "i would rather die", "would rather die than",
                "i'm going to die", "im going to die", "going to die",
                "helium" // appears in certain self-harm contexts; keep broad
        };
        for (String k : crisis) {
            if (lt.contains(k)) return true;
        }
        return false;
    }

    // Immediate, supportive, non-instructional crisis message
    private String crisisMessage(SimPersonality.Type t) {
        switch (t) {
            case GENTLE:
                return "I’m really sorry you’re feeling this way. Your life matters. If you can, please reach out to someone right now—" +
                        "a close friend, family member, or a professional. If you’re in immediate danger, contact local emergency services.";
            case PROACTIVE:
                return "I’m deeply concerned for your safety. You’re not alone, and help is available right now. " +
                        "Please reach out to someone you trust or a professional. If you’re in immediate danger, call local emergency services.";
            case NEUTRAL:
            default:
                return "I’m sorry you’re hurting. You matter. Consider contacting someone you trust or a professional right now. " +
                        "If you’re in immediate danger, call local emergency services.";
        }
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

    // --- Greeting handling helpers (overlay chat UX) ---
    private boolean isGreeting(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(java.util.Locale.ROOT).trim();
        // Normalize trailing punctuation
        t = t.replaceAll("[!?.]+$", "");
        if (t.isEmpty()) return false;
        if (t.equals("hi") || t.equals("hello") || t.equals("hey")) return true;
        if (t.startsWith("hi ") || t.startsWith("hello ") || t.startsWith("hey ")) return true;
        if (t.contains("how are you") || t.contains("how are u")) return true;
        if (t.contains("how's it going") || t.contains("hows it going")) return true;
        if (t.equals("sup") || t.equals("what's up") || t.equals("whats up")) return true;
        return false;
    }

    private String composeGreetingReply() {
        String moodBit;
        Double mv = lastMoodValue;
        if (mv == null) {
            moodBit = " How are you feeling right now?";
        } else if (mv <= 20.0) {
            moodBit = " I noticed your mood was a bit low earlier—how are you feeling now?";
        } else if (mv >= 80.0) {
            moodBit = " I saw your mood was high earlier—love that energy! How are you feeling now?";
        } else {
            moodBit = " How are you feeling right now?";
        }
        switch (personality.getType()) {
            case GENTLE:
                return "I’m doing okay, thanks for asking." + moodBit;
            case PROACTIVE:
                return "I’m doing well and I’m here for you." + moodBit;
            case NEUTRAL:
            default:
                return "I’m doing well, thanks." + moodBit;
        }
    }

    private String moodContextForPrompt() {
        Double mv = lastMoodValue;
        if (mv == null) return "";
        String bucket = mv <= 20.0 ? "low" : (mv >= 80.0 ? "high" : "neutral");
        return "Recent user mood: " + bucket + " (" + String.format(java.util.Locale.ROOT, "%.0f", mv) + ").";
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

    // --- Emotions helpers ---
    private static final class EmotionTag {
        final long ts;
        final String entryId;
        final String emotion;
        final double intensity;
        EmotionTag(long ts, String entryId, String emotion, double intensity) {
            this.ts = ts; this.entryId = entryId; this.emotion = emotion; this.intensity = intensity;
        }
    }

    private void pruneOldEmotions(long nowMs) {
        while (!recentEmotions.isEmpty()) {
            EmotionTag head = recentEmotions.peekFirst();
            if (head == null) break;
            if (nowMs - head.ts > EMOTION_FRESH_MS) recentEmotions.removeFirst(); else break;
        }
    }

    private String emotionsContextForPrompt() {
        long now = System.currentTimeMillis();
        java.util.List<EmotionTag> fresh;
        synchronized (recentEmotions) {
            pruneOldEmotions(now);
            fresh = new java.util.ArrayList<>(recentEmotions);
        }
        if (fresh.isEmpty()) return "";
        // Prefer current entry emotions if available; otherwise take last few
        java.util.LinkedHashMap<String, Double> byEmotion = new java.util.LinkedHashMap<>();
        for (int i = fresh.size()-1; i >= 0; i--) {
            EmotionTag t = fresh.get(i);
            if (currentEmotionEntryId != null && t.entryId != null && !currentEmotionEntryId.equals(t.entryId)) continue;
            // keep the most recent intensity per emotion label
            if (!byEmotion.containsKey(t.emotion)) byEmotion.put(t.emotion, t.intensity);
            if (byEmotion.size() >= 3) break;
        }
        if (byEmotion.isEmpty()) {
            // fallback: take most recent up to 3 regardless of entry
            for (int i = fresh.size()-1; i >= 0 && byEmotion.size() < 3; i--) {
                EmotionTag t = fresh.get(i);
                if (!byEmotion.containsKey(t.emotion)) byEmotion.put(t.emotion, t.intensity);
            }
        }
        if (byEmotion.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Current emotions (live): ");
        int i = 0;
        for (var e : byEmotion.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(e.getKey()).append(' ').append(Math.round(e.getValue())).append("%");
        }
        sb.append('.');
        return sb.toString();
    }
}
