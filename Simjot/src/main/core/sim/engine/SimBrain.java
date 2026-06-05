/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.sim.engine;

import java.io.File;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import main.core.sim.api.SimEventBus;
import main.core.analytics.MoodAnalyticsEngine;
import main.core.sim.critic.CriticPromptDecorator;
import main.core.sim.data.SimDataGateway;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.magi.MagiClient;
import main.core.sim.llm.ollama.OllamaClient;
import main.core.sim.llm.openai.OpenAIClient;
import main.core.sim.llm.prompt.PromptBuilder;
import main.core.sim.memory.MemoryStore;
import main.core.sim.memory.PersistentMemoryStore;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;
import main.core.sim.proactive.ProactiveTriggerEngine;
import main.core.sim.proactive.TriggerStatsStore;
import main.core.sim.retrieval.RecencyBuffer;
import main.core.sim.retrieval.RetrievalRanker;
import main.core.sim.state.UserState;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.NativeJson;

/**
 * Core decision engine of Sim
 * Listens to user events and context changes via SimEventBus
 * Maintains proactive context and memory stores
 * Evaluates triggers and generates proactive messages via ProactiveTriggerEngine and LLM
 * Handles streaming responses and turn management via TurnManager
 * Implements simple heuristics for crisis detection and entry-start suppression
 * Designed for robustness and responsiveness; LLM failures degrade gracefully without blocking core functionality
 */
public final class SimBrain implements SimEventBus.Listener {
    private static final String CRITICAL_ADVICE_MARKER = "[critical_advice]";
    private static final String TAGI_CONSULTING_NOTICE = "One moment, I'm consulting my TAGI consensus agents...";
    private static final long TAGI_CONSULTING_NOTICE_MS = 12000L;
    private static final long TAGI_STATE_NOTICE_MS = 2800L;

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
    private final ExecutorService guidanceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sim-Brain-Guidance");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService dailyPromptExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sim-Brain-DailyPrompt");
        t.setDaemon(true);
        return t;
    });
    private final Object dailyPromptLock = new Object();
    private final java.util.Set<String> dailyPromptInFlight = new java.util.HashSet<>();
    private static final String DAILY_PROMPT_LABEL = "SIM DAILY PROMPT";
    private ScheduledFuture<?> tickFuture;

    // Track last explicit invocation source (e.g., heart, hotkey)
    private volatile main.core.sim.api.SimEventBus.InvocationSource lastInvocationSource = main.core.sim.api.SimEventBus.InvocationSource.OTHER;
    // If true, all chat turns are routed through TAGI consensus.
    private volatile boolean tagiAlwaysOn = false;

    public SimBrain(SimSettings settings, SimPersonality personality, SimDataGateway data) {
        this.settings = settings;
        this.personality = personality;
        this.data = data;
        SimEventBus.get().addListener(this);
        try { this.tagiAlwaysOn = settings.isTagiAlwaysOn(); } catch (Throwable ignored) {}
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
        try { dailyPromptExecutor.shutdownNow(); } catch (Throwable ignored) {}
        try { guidanceExecutor.shutdownNow(); } catch (Throwable ignored) {}
        try { scheduler.shutdownNow(); } catch (Throwable ignored) {}
    }

    @Override
    public void onUserInvoked(main.core.sim.api.SimEventBus.InvocationSource source) {
        if (source == null) source = main.core.sim.api.SimEventBus.InvocationSource.OTHER;
        lastInvocationSource = source;
        System.out.println("[SimBrain] onUserInvoked source=" + source);
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
                    String sysWithSource = main.core.sim.critic.CriticPromptDecorator.decorateSystem(
                            main.core.sim.llm.prompt.PromptBuilder.systemPromptWithContext(
                                    personality.getType(),
                                    null, null,
                                    in.preview,
                                    in.conversationSummary,
                                    out.triggerKey,
                                    lastInvocationSource == null ? "OTHER" : lastInvocationSource.name()
                            )
                    );
                    turns.maybeSpeakProactively(llm, sysWithSource, out.userPrompt, out.preface);
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
                    String sysWithSource = main.core.sim.critic.CriticPromptDecorator.decorateSystem(
                            main.core.sim.llm.prompt.PromptBuilder.systemPromptWithContext(
                                    personality.getType(),
                                    null, null,
                                    in.preview,
                                    in.conversationSummary,
                                    out.triggerKey,
                                    lastInvocationSource == null ? "OTHER" : lastInvocationSource.name()
                            )
                    );
                    turns.maybeSpeakProactively(llm, sysWithSource, out.userPrompt, out.preface);
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
        Boolean tagiToggle = parseTagiToggleCommand(txt);
        if (tagiToggle != null) {
            handleTagiToggleCommand(tagiToggle.booleanValue());
            return;
        }
        if (isMoodAnalysisRequest(txt)) {
            handleMoodAnalysisRequest(txt);
            return;
        }
        if (isCriticalAdviceRequest(txt)) {
            handleCriticalAdviceRequest(txt);
            return;
        }
        if (tagiAlwaysOn) {
            handleTagiConsensusChatRequest(txt);
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

    private void handleCriticalAdviceRequest(String userText) {
        String txt = userText == null ? "" : userText.strip();
        if (txt.isEmpty()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now;
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        emitProminentGuidanceEmotions(txt);
        try {
            guidanceExecutor.execute(() -> generateCriticalAdviceWithMagiAsync(txt));
        } catch (Throwable ignored) {
            emitOverlayNotice("TAGI consensus unavailable right now.", TAGI_STATE_NOTICE_MS);
            emitImmediateChatTurn("I could not consult TAGI consensus agents right now. Please try again.");
        }
    }

    private void handleTagiConsensusChatRequest(String userText) {
        String txt = userText == null ? "" : userText.strip();
        if (txt.isEmpty()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now;
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        try {
            guidanceExecutor.execute(() -> generateTagiConsensusChatAsync(txt));
        } catch (Throwable ignored) {
            emitOverlayNotice("TAGI consensus unavailable right now.", TAGI_STATE_NOTICE_MS);
            emitImmediateChatTurn("I could not consult TAGI consensus agents right now. Please try again.");
        }
    }

    private void handleMoodAnalysisRequest(String userText) {
        String txt = userText == null ? "" : userText.strip();
        if (txt.isEmpty()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now;
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        try {
            guidanceExecutor.execute(() -> generateMoodAnalysisAsync(txt));
        } catch (Throwable ignored) {
            try {
                SimEventBus.get().emitSpeakStart();
                SimEventBus.get().emitSpeak("I could not analyze your mood data right now. Please try again.");
            } catch (Throwable ignored2) {
                // no-op
            } finally {
                try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored2) {}
            }
        }
    }

    private void generateMoodAnalysisAsync(String userText) {
        try { SimEventBus.get().emitSpeakStart(); } catch (Throwable ignored) {}
        try {
            String feedback = buildMoodAnalyticsFeedback(userText);
            if (feedback == null || feedback.isBlank()) {
                feedback = "I could not analyze your mood data right now. Please try again.";
            }
            try { SimEventBus.get().emitSpeak(feedback); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            try {
                SimEventBus.get().emitSpeak("I could not analyze your mood data right now. Please try again.");
            } catch (Throwable ignored2) {
                // no-op
            }
        } finally {
            try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored) {}
        }
    }

    private void generateCriticalAdviceWithMagiAsync(String userText) {
        String txt = userText == null ? "" : userText.strip();
        if (txt.isEmpty()) return;
        emitOverlayNotice(TAGI_CONSULTING_NOTICE, TAGI_CONSULTING_NOTICE_MS);
        try { SimEventBus.get().emitGuidanceRequested(CRITICAL_ADVICE_MARKER + txt); } catch (Throwable ignored) {}
        try { SimEventBus.get().emitSpeakStart(); } catch (Throwable ignored) {}
        try {
            SimLLMClient advisory = createMagiConsensusClient();
            String sys = CriticPromptDecorator.decorateSystem(
                    PromptBuilder.systemPrompt(personality.getType())
            );
            String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 5, 5);
            String usr = String.join("\n\n",
                    (ctx != null && !ctx.isBlank()) ? ("Context: " + ctx) : "",
                    moodContextForPrompt() + " " + emotionsContextForPrompt() + " " + factsContextForPrompt(),
                    "This is a critical-advice request.",
                    "Consult the MAGI consensus agents and give advice that is safe, practical, and compassionate.",
                    "Return plain text only, 3-5 concise bullet points, and include safeguards when uncertainty is high.",
                    "User request:\n\"\"\"\n" + txt + "\n\"\"\""
            );
            main.core.sim.llm.api.SimLLMResponse resp = advisory.generate(
                    new main.core.sim.llm.api.SimLLMRequest(sys, usr, 300, 0.45),
                    Duration.ofSeconds(35)
            );
            String out = resp == null ? "" : resp.text;
            String consensus = resp == null ? "" : resp.consensus;
            String[] emotions = resp == null ? null : resp.emotions;
            String[] brainStatuses = resp == null ? null : resp.brainStatuses;
            if ((consensus != null && !consensus.isBlank())
                    || (emotions != null && emotions.length > 0)
                    || (brainStatuses != null && brainStatuses.length > 0)) {
                try { SimEventBus.get().emitGuidanceOutcome(consensus, emotions, brainStatuses); } catch (Throwable ignored) {}
            }
            emitOverlayNotice(consensusStateNotice(consensus), TAGI_STATE_NOTICE_MS);
            if (out == null || out.isBlank()) {
                out = "I could not produce a reliable critical-advice response. Please share more detail and try again.";
            }
            try { SimEventBus.get().emitSpeak(out); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            emitOverlayNotice("TAGI consensus unavailable right now.", TAGI_STATE_NOTICE_MS);
            try {
                SimEventBus.get().emitSpeak("I could not consult TAGI consensus agents right now. Check MAGI settings and try again.");
            } catch (Throwable ignored2) {}
        } finally {
            try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored) {}
        }
    }

    private void generateTagiConsensusChatAsync(String userText) {
        String txt = userText == null ? "" : userText.strip();
        if (txt.isEmpty()) return;
        emitOverlayNotice(TAGI_CONSULTING_NOTICE, TAGI_CONSULTING_NOTICE_MS);
        try { SimEventBus.get().emitSpeakStart(); } catch (Throwable ignored) {}
        try {
            SimLLMClient advisory = createMagiConsensusClient();
            String sys = CriticPromptDecorator.decorateSystem(
                    PromptBuilder.systemPrompt(personality.getType())
            );
            String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 5, 5);
            String usr = String.join("\n\n",
                    (ctx != null && !ctx.isBlank()) ? ("Context: " + ctx) : "",
                    moodContextForPrompt() + " " + emotionsContextForPrompt() + " " + factsContextForPrompt(),
                    "The user is chatting with you in a compact overlay.",
                    "Consult the TAGI consensus brains before responding.",
                    "Give one cohesive response that reflects the consensus quality.",
                    "Respond in plain text only, 1-4 concise sentences, and include at most one follow-up question.",
                    "User message:\n\"\"\"\n" + txt + "\n\"\"\""
            );
            main.core.sim.llm.api.SimLLMResponse resp = advisory.generate(
                    new main.core.sim.llm.api.SimLLMRequest(sys, usr, 260, 0.48),
                    Duration.ofSeconds(30)
            );
            String out = resp == null ? "" : resp.text;
            String consensus = resp == null ? "" : resp.consensus;
            String[] emotions = resp == null ? null : resp.emotions;
            String[] brainStatuses = resp == null ? null : resp.brainStatuses;
            if ((consensus != null && !consensus.isBlank())
                    || (emotions != null && emotions.length > 0)
                    || (brainStatuses != null && brainStatuses.length > 0)) {
                try { SimEventBus.get().emitGuidanceOutcome(consensus, emotions, brainStatuses); } catch (Throwable ignored) {}
            }
            emitOverlayNotice(consensusStateNotice(consensus), TAGI_STATE_NOTICE_MS);
            if (out == null || out.isBlank()) {
                out = "I could not produce a reliable TAGI consensus response right now. Please try again.";
            }
            try { SimEventBus.get().emitSpeak(out); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            emitOverlayNotice("TAGI consensus unavailable right now.", TAGI_STATE_NOTICE_MS);
            try {
                SimEventBus.get().emitSpeak("I could not consult TAGI consensus agents right now. Please try again.");
            } catch (Throwable ignored2) {}
        } finally {
            try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored) {}
        }
    }

    private SimLLMClient createMagiConsensusClient() {
        String python = "python3";
        String model = "gpt-5";
        String apiKey = "";
        try { python = settings.getMagiPythonCommand(); } catch (Throwable ignored) {}
        try { model = settings.getMagiModel(); } catch (Throwable ignored) {}
        try { apiKey = settings.getOpenAIApiKey(); } catch (Throwable ignored) {}
        if (python == null || python.isBlank()) python = "python3";
        if (model == null || model.isBlank()) model = "gpt-5";
        if (apiKey == null) apiKey = "";
        return new MagiClient(python, model, apiKey);
    }

    private void handleTagiToggleCommand(boolean enable) {
        boolean changed = (tagiAlwaysOn != enable);
        tagiAlwaysOn = enable;
        try { settings.setTagiAlwaysOn(enable); } catch (Throwable ignored) {}

        String notice;
        String spoken;
        if (enable) {
            notice = changed
                    ? "TAGI consensus enabled for every chat input."
                    : "TAGI consensus is already enabled.";
            spoken = changed
                    ? "TAGI consensus is now enabled. I will consult the TAGI brains on every message."
                    : "TAGI consensus is already enabled for every message.";
        } else {
            notice = changed
                    ? "TAGI consensus disabled. Using standard Sim chat."
                    : "TAGI consensus is already disabled.";
            spoken = changed
                    ? "TAGI consensus is now disabled. I will use the standard Sim chat flow."
                    : "TAGI consensus is already disabled.";
        }
        emitOverlayNotice(notice, TAGI_STATE_NOTICE_MS);
        emitImmediateChatTurn(spoken);
    }

    private Boolean parseTagiToggleCommand(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.toLowerCase(java.util.Locale.ROOT).trim();
        t = t.replaceAll("[.!?]+$", "").trim();
        boolean mentionsTagi = t.contains("tagi") || t.contains("magi");
        if (!mentionsTagi) return null;

        boolean disable = t.contains("disable") || t.contains("turn off") || t.contains("deactivate");
        boolean enable = t.contains("enable") || t.contains("turn on")
                || (t.contains("activate") && !t.contains("deactivate"));
        if (enable == disable) return null;
        return enable ? Boolean.TRUE : Boolean.FALSE;
    }

    private void emitImmediateChatTurn(String message) {
        String msg = message == null ? "" : message.strip();
        if (msg.isEmpty()) return;
        try { SimEventBus.get().emitSpeakStart(); } catch (Throwable ignored) {}
        try { SimEventBus.get().emitSpeak(msg); } catch (Throwable ignored) {}
        try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored) {}
    }

    private void emitOverlayNotice(String text, long durationMs) {
        try { SimEventBus.get().emitOverlayNotice(text, durationMs); } catch (Throwable ignored) {}
    }

    private String consensusStateNotice(String consensus) {
        String c = consensus == null ? "" : consensus.trim();
        if (c.isEmpty()) return "TAGI consensus complete.";
        c = c.replaceAll("\\s+", " ");
        if (c.length() > 56) c = c.substring(0, 56).trim();
        return "TAGI consensus: " + c;
    }

    @Override
    public void onChatEnded() {
        try { turns.cancelIfUserTyping(); } catch (Throwable ignored) {}
        // No other state needed now; cooldown prevents immediate nudges
        lastSpeakMs = System.currentTimeMillis();
    }

    @Override
    public void onTemplateGenerationRequested(String text, String notebookName) {
        if (!settings.isEnabled()) return;
        String focus = text == null ? "" : text.strip();
        if (focus.isEmpty()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now;
        String notebook = notebookName == null ? "" : notebookName.strip();
        try {
            guidanceExecutor.execute(() -> generateTemplateAsync(focus, notebook));
        } catch (Throwable ignored) {
            TemplateDraft fallback = fallbackTemplateDraft(focus, notebook);
            try {
                SimEventBus.get().emitTemplateGenerated(notebook, fallback.name, fallback.description, fallback.questions);
            } catch (Throwable ignored2) {}
        }
    }

    @Override
    public void onGuidanceRequested(String text) {
        if (!settings.isEnabled()) return;
        String txt = (text == null) ? "" : text.strip();
        if (txt.isEmpty()) return;
        long now = System.currentTimeMillis();
        lastSpeakMs = now;
        emitProminentGuidanceEmotions(txt);
        // Run guidance generation off the EDT to keep overlay animations responsive.
        try { guidanceExecutor.execute(() -> generateGuidanceAsync(txt)); } catch (Throwable ignored) {}
    }

    private void generateGuidanceAsync(String txt) {
        if (settings.isLlmEnabled()) ensureLlm();
        if (llm != null) {
            try {
                String sys = CriticPromptDecorator.decorateSystem(
                        PromptBuilder.systemPrompt(personality.getType())
                );
                // Keep prompt lean; include retrieval context
                String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 3, 3);
                String journal = txt.length() > 2000 ? txt.substring(txt.length() - 2000) : txt;
                String usr = String.join("\n\n",
                        (ctx != null && !ctx.isBlank()) ? ("Context: " + ctx) : "",
                        moodContextForPrompt() + " " + emotionsContextForPrompt() + " " + factsContextForPrompt(),
                        "You are a supportive journaling companion. Read the user's journal text and provide 2–4 concise, compassionate guidance suggestions to deepen reflection. Prefer short bullet points. Avoid platitudes. No preface, no meta; plain text only.",
                        "Journal text:\n\"\"\"\n" + journal + "\n\"\"\""
                );
                // Synchronous generation; deliver into editor, not overlay
                main.core.sim.llm.api.SimLLMResponse resp = llm.generate(
                        new main.core.sim.llm.api.SimLLMRequest(sys, usr, 220, 0.7),
                        Duration.ofSeconds(20)
                );
                String out = resp == null ? "" : resp.text;
                String consensus = resp == null ? "" : resp.consensus;
                String[] emotions = resp == null ? null : resp.emotions;
                String[] brainStatuses = resp == null ? null : resp.brainStatuses;
                if ((consensus != null && !consensus.isBlank())
                        || (emotions != null && emotions.length > 0)
                        || (brainStatuses != null && brainStatuses.length > 0)) {
                    try { SimEventBus.get().emitGuidanceOutcome(consensus, emotions, brainStatuses); } catch (Throwable ignored) {}
                }
                try { SimEventBus.get().emitGuidanceProduced(out); } catch (Throwable ignored) {}
                return;
            } catch (Throwable ignored) {
                // fall through
            }
        }
        // If LLM unavailable, do not emit a generic fallback to avoid noise
    }

    private void generateTemplateAsync(String focus, String notebookName) {
        TemplateDraft draft = null;
        if (settings.isLlmEnabled()) ensureLlm();
        if (llm != null) {
            try {
                draft = generateTemplateWithLlm(focus, notebookName);
            } catch (Throwable ignored) {
                draft = null;
            }
        }
        if (draft == null) {
            draft = fallbackTemplateDraft(focus, notebookName);
        }
        draft = sanitizeTemplateDraft(draft, focus);
        try {
            SimEventBus.get().emitTemplateGenerated(notebookName, draft.name, draft.description, draft.questions);
        } catch (Throwable ignored) {}
    }

    private TemplateDraft generateTemplateWithLlm(String focus, String notebookName) throws Exception {
        String sys = CriticPromptDecorator.decorateSystem(
                PromptBuilder.systemPrompt(personality.getType())
        );
        String ctx = RetrievalRanker.buildContext(persistent, memory, recency, 3, 3);
        String notebookLine = (notebookName == null || notebookName.isBlank())
                ? ""
                : ("Notebook context: " + notebookName + ".");
        String usr = String.join("\n",
                "Create a journal template draft based on this focus:",
                "\"" + focus + "\"",
                notebookLine,
                (ctx == null || ctx.isBlank()) ? "" : ("Relevant context: " + ctx),
                "Return ONLY JSON with this exact shape:",
                "{\"name\":\"...\",\"description\":\"...\",\"questions\":[\"...\",\"...\",\"...\"]}",
                "Rules:",
                "- name: max 60 chars, clear and specific",
                "- description: max 180 chars",
                "- questions: 3 to 6 reflective prompts, each <= 180 chars",
                "- No markdown, no extra keys, no commentary."
        );
        main.core.sim.llm.api.SimLLMResponse resp = llm.generate(
                new main.core.sim.llm.api.SimLLMRequest(sys, usr, 420, 0.35),
                Duration.ofSeconds(28)
        );
        String text = resp == null ? "" : resp.text;
        TemplateDraft draft = parseTemplateDraftFromJsonText(text);
        if (draft == null) {
            draft = parseTemplateDraftFromPlainText(text);
        }
        return draft;
    }

    private TemplateDraft parseTemplateDraftFromJsonText(String text) {
        if (text == null || text.isBlank()) return null;
        String json = extractFirstJsonObject(text);
        if (json == null || json.isBlank()) return null;

        String name = firstNonBlank(
                NativeJson.getString(json, "name"),
                NativeJson.getString(json, "template_name"),
                NativeJson.getString(json, "title")
        );
        String description = firstNonBlank(
                NativeJson.getString(json, "description"),
                NativeJson.getString(json, "summary")
        );
        java.util.List<String> questions = new java.util.ArrayList<>();
        String questionsArr = firstNonBlank(
                NativeJson.getArray(json, "questions"),
                NativeJson.getArray(json, "prompts")
        );
        if (questionsArr != null && !questionsArr.isBlank()) {
            questions.addAll(NativeJson.getStringArray(questionsArr));
        }
        if (questions.isEmpty()) {
            return null;
        }
        return new TemplateDraft(name, description, questions.toArray(new String[0]));
    }

    private TemplateDraft parseTemplateDraftFromPlainText(String text) {
        if (text == null || text.isBlank()) return null;
        String[] lines = text.split("\\R");
        String name = "";
        String description = "";
        java.util.List<String> questions = new java.util.ArrayList<>();
        boolean inQuestions = false;
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("name:")) {
                name = line.substring(5).trim();
                continue;
            }
            if (lower.startsWith("title:")) {
                name = line.substring(6).trim();
                continue;
            }
            if (lower.startsWith("description:")) {
                description = line.substring(12).trim();
                continue;
            }
            if (lower.startsWith("questions:") || lower.startsWith("prompts:")) {
                inQuestions = true;
                String tail = line.substring(line.indexOf(':') + 1).trim();
                if (!tail.isEmpty()) questions.add(cleanQuestionCandidate(tail));
                continue;
            }
            if (line.matches("^[-*]\\s+.+")) {
                questions.add(cleanQuestionCandidate(line.substring(1).trim()));
                inQuestions = true;
                continue;
            }
            if (line.matches("^\\d+[\\.)]\\s+.+")) {
                String q = line.replaceFirst("^\\d+[\\.)]\\s+", "");
                questions.add(cleanQuestionCandidate(q));
                inQuestions = true;
                continue;
            }
            if (inQuestions && line.endsWith("?")) {
                questions.add(cleanQuestionCandidate(line));
                continue;
            }
            if (name.isEmpty()) {
                name = line;
            } else if (description.isEmpty()) {
                description = line;
            }
        }
        if (questions.isEmpty()) {
            return null;
        }
        return new TemplateDraft(name, description, questions.toArray(new String[0]));
    }

    private TemplateDraft fallbackTemplateDraft(String focus, String notebookName) {
        String normalizedFocus = focus == null ? "" : focus.replaceAll("\\s+", " ").trim();
        if (normalizedFocus.isEmpty()) normalizedFocus = "Daily Reflection";
        String shortFocus = normalizedFocus.length() > 56 ? normalizedFocus.substring(0, 56).trim() : normalizedFocus;
        String title = toTitleCase(shortFocus);
        if (title.isBlank()) title = "Guided Reflection";
        if (!title.toLowerCase(java.util.Locale.ROOT).contains("template")) {
            title = title + " Template";
        }

        String description = "A guided template for reflecting on " + shortFocus.toLowerCase(java.util.Locale.ROOT) + ".";
        if (notebookName != null && !notebookName.isBlank()) {
            description = description + " Scoped to " + notebookName.trim() + ".";
        }

        String[] questions = new String[]{
                "What matters most to me about " + shortFocus + " today?",
                "What emotions or patterns do I notice around this topic?",
                "What is one perspective shift that could help me move forward?",
                "What small action can I take next to support myself?"
        };
        return new TemplateDraft(title, description, questions);
    }

    private TemplateDraft sanitizeTemplateDraft(TemplateDraft draft, String focus) {
        TemplateDraft source = draft == null ? fallbackTemplateDraft(focus, "") : draft;
        String name = source.name == null ? "" : source.name.trim();
        String description = source.description == null ? "" : source.description.trim();
        if (name.isEmpty()) {
            String fallbackFocus = focus == null ? "Daily Reflection" : focus.trim();
            if (fallbackFocus.length() > 40) fallbackFocus = fallbackFocus.substring(0, 40).trim();
            name = toTitleCase(fallbackFocus) + " Template";
        }
        if (name.length() > 60) name = name.substring(0, 60).trim();
        if (description.isEmpty()) {
            description = "A reflective template generated by Sim.";
        }
        if (description.length() > 180) description = description.substring(0, 180).trim();

        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        if (source.questions != null) {
            for (String q : source.questions) {
                String clean = cleanQuestionCandidate(q);
                if (clean.isEmpty()) continue;
                unique.add(clean);
                if (unique.size() >= 6) break;
            }
        }
        java.util.List<String> questions = new java.util.ArrayList<>(unique);
        if (questions.size() < 3) {
            String topic = (focus == null || focus.isBlank()) ? "this topic" : focus.trim();
            questions.clear();
            questions.add("What feels most important for me to explore about " + topic + "?");
            questions.add("What pattern do I notice in how I respond to this?");
            questions.add("What one small step can I take next?");
        }
        String[] qs = questions.toArray(new String[0]);
        return new TemplateDraft(name, description, qs);
    }

    private String cleanQuestionCandidate(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "";
        if (s.length() > 180) s = s.substring(0, 180).trim();
        if (!s.endsWith("?")) {
            if (s.length() < 175) s = s + "?";
        }
        return s;
    }

    private String extractFirstJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private String toTitleCase(String text) {
        if (text == null) return "";
        String[] words = text.trim().split("\\s+");
        if (words.length == 0) return "";
        StringBuilder out = new StringBuilder();
        int maxWords = Math.min(words.length, 8);
        for (int i = 0; i < maxWords; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            if (w.length() == 1) {
                out.append(w.toUpperCase(java.util.Locale.ROOT));
            } else {
                out.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out.toString().trim();
    }

    private static final class TemplateDraft {
        final String name;
        final String description;
        final String[] questions;

        TemplateDraft(String name, String description, String[] questions) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.questions = questions == null ? new String[0] : questions;
        }
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
        // Engagement mode gating: suppress proactive tick entirely in ON_CALL mode
        try {
            if (settings.getEngagementMode() == SimSettings.EngagementMode.ON_CALL) {
                return;
            }
        } catch (Throwable ignored) {}

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
                // Enrich system prompt with invocation source for context-awareness
                String sysWithSource = main.core.sim.critic.CriticPromptDecorator.decorateSystem(
                        main.core.sim.llm.prompt.PromptBuilder.systemPromptWithContext(
                                personality.getType(),
                                null, null,
                                in.preview,
                                in.conversationSummary,
                                out.triggerKey,
                                lastInvocationSource == null ? "OTHER" : lastInvocationSource.name()
                        )
                );
                turns.maybeSpeakProactively(llm, sysWithSource, out.userPrompt, out.preface);
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
            case "magi": {
                String python = "python3";
                String model = "gpt-5";
                String apiKey = "";
                try { python = settings.getMagiPythonCommand(); } catch (Throwable ignored) {}
                try { model = settings.getMagiModel(); } catch (Throwable ignored) {}
                try { apiKey = settings.getOpenAIApiKey(); } catch (Throwable ignored) {}
                if (python == null || python.isBlank()) python = "python3";
                if (model == null || model.isBlank()) model = "gpt-5";
                if (apiKey == null) apiKey = "";
                llmProviderActive = "magi";
                return new MagiClient(python, model, apiKey);
            }
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
            // Only emit a check-in if we have meaningful facts; otherwise, do not speak.
            if (facts != null && !facts.isBlank()) {
                String msg = "Hi — just checking in. How’s your day starting? I remember " + facts + ". Anything you’d like to talk about today?";
                speakOncePer(10000L, msg);
            }
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

    private boolean isMoodAnalysisRequest(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        boolean mentionsMood = t.contains("mood") || t.contains("emotion") || t.contains("feelings");
        if (!mentionsMood) return false;
        if (t.contains("analy") || t.contains("analysis") || t.contains("feedback")) return true;
        if (t.contains("trend") || t.contains("pattern") || t.contains("stats") || t.contains("data")) return true;
        if (t.contains("how am i doing") || t.contains("how is my")) return true;
        if (t.contains("volatility") || t.contains("streak")) return true;
        return t.contains("insight");
    }

    private boolean isCriticalAdviceRequest(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        boolean explicitCritical = t.contains("critical advice")
                || t.contains("urgent advice")
                || t.contains("high-stakes")
                || t.contains("high stakes")
                || t.contains("major decision")
                || t.contains("important decision")
                || t.contains("serious advice");
        boolean adviceIntent = t.contains("advice")
                || t.contains("guidance")
                || t.contains("recommend")
                || t.contains("what should i")
                || t.contains("should i");
        boolean riskyDomain = t.contains("medical")
                || t.contains("health")
                || t.contains("legal")
                || t.contains("law")
                || t.contains("financial")
                || t.contains("money")
                || t.contains("career")
                || t.contains("relationship")
                || t.contains("risk")
                || t.contains("safety");
        return explicitCritical || (adviceIntent && riskyDomain);
    }

    private String buildMoodAnalyticsFeedback(String userText) {
        int daysBack = resolveMoodRangeDays(userText);
        MoodAnalyticsEngine.AnalyticsResult result;
        try {
            result = MoodAnalyticsEngine.get().analyze(daysBack, 7);
        } catch (Throwable t) {
            return "I could not read your mood data right now. Please try again in a moment.";
        }
        if (result == null || result.totalSamples <= 0) {
            return "I don’t have enough mood data yet. Add a few mood check-ins first, then I can analyze trends.";
        }

        Double first = firstNonNullValue(result.dailyAverages);
        Double last = lastNonNullValue(result.dailyAverages);
        double latest = (last != null) ? last : result.overallAverage;
        double delta = (first != null && last != null) ? (last - first) : 0.0;
        String direction;
        if (delta >= 6.0) direction = "upward";
        else if (delta <= -6.0) direction = "downward";
        else direction = "fairly stable";

        String volatilityLabel = result.volatility < 7.5 ? "steady"
                : (result.volatility < 14.0 ? "moderately variable" : "highly variable");
        String streakLine = buildMoodStreakLine(result);
        String rangeLabel = (daysBack <= 0) ? "all recorded data" : ("the last " + daysBack + " days");
        String llmFeedback = buildMoodAnalyticsFeedbackWithLlm(
                userText, result, rangeLabel, latest, delta, direction, volatilityLabel, streakLine
        );
        if (llmFeedback != null && !llmFeedback.isBlank()) {
            return llmFeedback;
        }

        String suggestion = moodSuggestionForAverage(result.overallAverage, latest);
        String streakSentence = "none".equalsIgnoreCase(streakLine)
                ? "No active mood streak right now."
                : "Current streak: " + streakLine + ".";
        return String.format(
                java.util.Locale.ROOT,
                "I analyzed %s: average %.1f/100 (%s), latest %.1f/100 with a %s trend, volatility is %s. %s %s",
                rangeLabel,
                result.overallAverage,
                MoodAnalyticsEngine.categorize(result.overallAverage).toLowerCase(java.util.Locale.ROOT),
                latest,
                direction,
                volatilityLabel,
                streakSentence,
                suggestion
        ).trim();
    }

    private String buildMoodAnalyticsFeedbackWithLlm(
            String userText,
            MoodAnalyticsEngine.AnalyticsResult result,
            String rangeLabel,
            double latest,
            double delta,
            String direction,
            String volatilityLabel,
            String streakLine
    ) {
        try {
            if (!settings.isLlmEnabled()) return null;
            ensureLlm();
            if (llm == null) return null;

            String sys = CriticPromptDecorator.decorateSystem(
                    PromptBuilder.systemPrompt(personality.getType())
            );
            String request = userText == null ? "" : userText.trim();
            String metrics = String.join("\n",
                    "Range: " + rangeLabel,
                    "Samples: " + result.totalSamples,
                    "Overall average: " + formatMood(result.overallAverage) + "/100",
                    "Latest average: " + formatMood(latest) + "/100",
                    "Trend delta (latest-first): " + formatMood(delta),
                    "Trend direction: " + direction,
                    "Volatility: " + formatMood(result.volatility) + " (" + volatilityLabel + ")",
                    "Current streak: " + streakLine,
                    "Longest good streak: " + result.longestGoodStreak,
                    "Longest challenging streak: " + result.longestBadStreak,
                    "Recent daily series:\n" + moodSeriesSnapshot(result, 14),
                    "Recent emotion snapshot:\n" + latestEmotionSnapshot(result)
            );
            String usr = String.join("\n\n",
                    "The user asked for mood data analysis.",
                    "User request: " + request,
                    "Use ONLY the metrics below.",
                    metrics,
                    "Write a high-quality response in plain text only.",
                    "Requirements:",
                    "- Be specific and evidence-grounded from the provided data.",
                    "- Include: trend read, volatility read, streak interpretation, and 1-2 practical next steps.",
                    "- Keep a supportive but honest tone, no diagnosis, no alarmism.",
                    "- 4-7 concise sentences maximum."
            );

            main.core.sim.llm.api.SimLLMResponse resp = llm.generate(
                    new main.core.sim.llm.api.SimLLMRequest(sys, usr, 280, 0.45),
                    Duration.ofSeconds(28)
            );
            String out = resp == null ? "" : resp.text;
            if (out == null) return null;
            out = out.strip();
            if (out.isEmpty()) return null;
            if (out.length() > 1200) out = out.substring(0, 1200).trim();
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int resolveMoodRangeDays(String text) {
        if (text == null) return 30;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("all time") || t.contains("overall") || t.contains("lifetime")) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,3})\\s*(day|days|week|weeks|month|months)")
                .matcher(t);
        if (m.find()) {
            int value = 30;
            try { value = Integer.parseInt(m.group(1)); } catch (Throwable ignored) {}
            String unit = m.group(2);
            if (unit.startsWith("week")) value *= 7;
            else if (unit.startsWith("month")) value *= 30;
            return Math.max(3, Math.min(365, value));
        }
        if (t.contains("week")) return 7;
        if (t.contains("month")) return 30;
        if (t.contains("quarter")) return 90;
        return 30;
    }

    private Double firstNonNullValue(List<Double> values) {
        if (values == null) return null;
        for (Double v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private Double lastNonNullValue(List<Double> values) {
        if (values == null) return null;
        for (int i = values.size() - 1; i >= 0; i--) {
            Double v = values.get(i);
            if (v != null) return v;
        }
        return null;
    }

    private String moodSuggestionForAverage(double average, double latest) {
        if (latest < 30 || average < 35) {
            return "Try a short reset routine today: one grounding activity and one low-pressure task.";
        }
        if (latest > 70 || average > 65) {
            return "Momentum looks positive. Capture what helped so you can repeat it intentionally.";
        }
        return "A small daily reflection habit can help keep this trend moving in the right direction.";
    }

    private String buildMoodStreakLine(MoodAnalyticsEngine.AnalyticsResult result) {
        if (result == null) return "No active mood streak right now.";
        if (result.currentStreak > 0) {
            return result.currentStreak + " good day" + (result.currentStreak == 1 ? "" : "s");
        }
        if (result.currentStreak < 0) {
            int challenging = -result.currentStreak;
            return challenging + " challenging day" + (challenging == 1 ? "" : "s");
        }
        return "none";
    }

    private String moodSeriesSnapshot(MoodAnalyticsEngine.AnalyticsResult result, int maxDays) {
        if (result == null || result.dates == null || result.dates.isEmpty()) return "No series data.";
        int size = result.dates.size();
        int start = Math.max(0, size - Math.max(1, maxDays));
        StringBuilder sb = new StringBuilder(256);
        for (int i = start; i < size; i++) {
            java.time.LocalDate d = result.dates.get(i);
            MoodAnalyticsEngine.DailyStats stats = result.dailyStats == null ? null : result.dailyStats.get(d);
            if (i > start) sb.append('\n');
            if (stats == null || stats.sampleCount <= 0) {
                sb.append(d).append(": no-data");
            } else {
                sb.append(d)
                        .append(": avg=").append(formatMood(stats.average))
                        .append(", min=").append(stats.min)
                        .append(", max=").append(stats.max)
                        .append(", n=").append(stats.sampleCount);
            }
        }
        return sb.toString();
    }

    private String latestEmotionSnapshot(MoodAnalyticsEngine.AnalyticsResult result) {
        if (result == null || result.dates == null || result.dates.isEmpty() || result.dailyStats == null) {
            return "No detailed emotion components available.";
        }
        for (int i = result.dates.size() - 1; i >= 0; i--) {
            java.time.LocalDate d = result.dates.get(i);
            MoodAnalyticsEngine.DailyStats s = result.dailyStats.get(d);
            if (s == null || s.sampleCount <= 0) continue;
            boolean hasDetails = s.avgJoy >= 0 || s.avgCalm >= 0 || s.avgGratitude >= 0 || s.avgEnergy >= 0
                    || s.avgSadness >= 0 || s.avgAnger >= 0 || s.avgAnxiety >= 0 || s.avgStress >= 0;
            if (!hasDetails) continue;
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (s.avgJoy >= 0) parts.add("joy=" + formatMood(s.avgJoy));
            if (s.avgCalm >= 0) parts.add("calm=" + formatMood(s.avgCalm));
            if (s.avgGratitude >= 0) parts.add("gratitude=" + formatMood(s.avgGratitude));
            if (s.avgEnergy >= 0) parts.add("energy=" + formatMood(s.avgEnergy));
            if (s.avgSadness >= 0) parts.add("sadness=" + formatMood(s.avgSadness));
            if (s.avgAnger >= 0) parts.add("anger=" + formatMood(s.avgAnger));
            if (s.avgAnxiety >= 0) parts.add("anxiety=" + formatMood(s.avgAnxiety));
            if (s.avgStress >= 0) parts.add("stress=" + formatMood(s.avgStress));
            return d + ": " + String.join(", ", parts);
        }
        return "No detailed emotion components available.";
    }

    private String formatMood(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
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
    private static final java.util.Set<String> JOY_WORDS = java.util.Set.of(
            "happy", "happiness", "joy", "joyful", "grateful", "gratitude",
            "excited", "proud", "hopeful", "hope", "optimistic", "love", "relieved"
    );
    private static final java.util.Set<String> CALM_WORDS = java.util.Set.of(
            "calm", "peace", "peaceful", "grounded", "stable", "steady",
            "centered", "content", "balanced", "breathe", "breathing"
    );
    private static final java.util.Set<String> LOW_WORDS = java.util.Set.of(
            "sad", "down", "low", "lonely", "grief", "hurt", "empty",
            "hopeless", "depressed", "tired", "exhausted", "anxious", "anxiety",
            "worried", "worry", "afraid", "fear", "stuck"
    );
    private static final java.util.Set<String> STRESS_WORDS = java.util.Set.of(
            "angry", "anger", "mad", "frustrated", "frustrating", "annoyed", "irritated",
            "resentful", "rage", "stress", "stressed", "overwhelmed", "panic", "panicked",
            "tense", "pressure", "burnout"
    );
    private static final java.util.Set<String> NEUTRAL_WORDS = java.util.Set.of(
            "okay", "ok", "fine", "normal", "meh", "neutral"
    );

    private void emitProminentGuidanceEmotions(String text) {
        java.util.Map<String, Double> scores = detectGuidanceEmotionScores(text);
        if (scores.isEmpty()) {
            try { SimEventBus.get().emitEmotionTagged(currentEmotionEntryId, "neutral", 35.0); } catch (Throwable ignored) {}
            return;
        }
        java.util.List<java.util.Map.Entry<String, Double>> ranked = new java.util.ArrayList<>(scores.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int emitted = 0;
        for (java.util.Map.Entry<String, Double> e : ranked) {
            if (e == null || e.getKey() == null) continue;
            double score = e.getValue() == null ? 0.0 : e.getValue();
            if (score <= 0.0) continue;
            double intensity = Math.min(100.0, 30.0 + score * 18.0);
            try { SimEventBus.get().emitEmotionTagged(currentEmotionEntryId, e.getKey(), intensity); } catch (Throwable ignored) {}
            emitted++;
            if (emitted >= 3) break;
        }
        if (emitted == 0) {
            try { SimEventBus.get().emitEmotionTagged(currentEmotionEntryId, "neutral", 35.0); } catch (Throwable ignored) {}
        }
    }

    private java.util.Map<String, Double> detectGuidanceEmotionScores(String text) {
        java.util.Map<String, Double> scores = new java.util.HashMap<>();
        if (text == null || text.isBlank()) return scores;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String[] tokens = lower.split("[^a-z]+");
        for (String t : tokens) {
            if (t == null || t.isEmpty()) continue;
            if (JOY_WORDS.contains(t)) addEmotionScore(scores, "joy", 1.0);
            if (CALM_WORDS.contains(t)) addEmotionScore(scores, "calm", 1.0);
            if (LOW_WORDS.contains(t)) addEmotionScore(scores, "sad", 1.0);
            if (STRESS_WORDS.contains(t)) addEmotionScore(scores, "anger", 1.0);
            if (NEUTRAL_WORDS.contains(t)) addEmotionScore(scores, "neutral", 0.8);
        }
        // Phrase boosts catch common journaling patterns that token-only misses.
        if (lower.contains("i feel overwhelmed") || lower.contains("too much pressure")) {
            addEmotionScore(scores, "anger", 1.6);
        }
        if (lower.contains("i feel anxious") || lower.contains("can't stop worrying")) {
            addEmotionScore(scores, "sad", 1.5);
        }
        if (lower.contains("i feel grateful") || lower.contains("i am grateful")) {
            addEmotionScore(scores, "joy", 1.4);
        }
        if (lower.contains("i feel calm") || lower.contains("i feel at peace")) {
            addEmotionScore(scores, "calm", 1.4);
        }
        return scores;
    }

    private void addEmotionScore(java.util.Map<String, Double> scores, String key, double delta) {
        if (scores == null || key == null || key.isBlank() || delta <= 0.0) return;
        scores.put(key, scores.getOrDefault(key, 0.0) + delta);
    }

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
