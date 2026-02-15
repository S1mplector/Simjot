/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.engine;

import java.io.File;
import java.time.LocalTime;
import java.time.Duration;
import java.time.LocalDate;
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
import main.core.sim.state.UserState;
import main.infrastructure.backup.NotebookInfo;
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

        // 1) Primary advice text from the configured LLM provider.
        String out = "";
        boolean primaryIsMagi = false;
        main.core.sim.llm.api.SimLLMResponse primaryResp = null;
        try {
            if (settings.isLlmEnabled()) ensureLlm();
            primaryIsMagi = "magi".equalsIgnoreCase(llmProviderActive);
            if (llm != null) {
                primaryResp = llm.generate(
                        new main.core.sim.llm.api.SimLLMRequest(sys, usr, 220, 0.7),
                        Duration.ofSeconds(20)
                );
                out = primaryResp == null ? "" : primaryResp.text;
            }
        } catch (Throwable ignored) {}

        // 2) MAGI consensus metadata (always attempted for guidance).
        main.core.sim.llm.api.SimLLMResponse magiResp = primaryIsMagi ? primaryResp : null;
        SimLLMClient guidanceLlm = null;
        if (!primaryIsMagi) {
            try { guidanceLlm = createGuidanceMagiClient(); } catch (Throwable ignored) {}
        }
        if (!primaryIsMagi && guidanceLlm != null) {
            try {
                String draft = out == null ? "" : out.strip();
                if (draft.length() > 1200) draft = draft.substring(0, 1200);
                String magiUsr = String.join("\n\n",
                        "Evaluate the journal guidance quality and produce MAGI consensus metadata.",
                        "If a draft guidance is provided, assess that draft for empathy, safety, and practicality.",
                        "Journal text:\n\"\"\"\n" + journal + "\n\"\"\"",
                        draft.isBlank() ? "" : ("Draft guidance:\n" + draft)
                );
                magiResp = guidanceLlm.generate(
                        new main.core.sim.llm.api.SimLLMRequest(sys, magiUsr, 220, 0.55),
                        Duration.ofSeconds(20)
                );
                if ((out == null || out.isBlank()) && magiResp != null) {
                    out = magiResp.text;
                }
            } catch (Throwable ignored) {}
        }

        if (out == null) out = "";
        out = out.strip();
        String consensus = normalizeGuidanceConsensus(magiResp == null ? "" : magiResp.consensus);
        String[] emotions = buildGuidanceOutcomeEmotions(txt, out, magiResp == null ? null : magiResp.emotions);
        String[] brainStatuses = magiResp == null ? null : magiResp.brainStatuses;
        emitGuidanceOutcome(consensus, emotions, brainStatuses);

        if (!out.isEmpty()) {
            try { SimEventBus.get().emitGuidanceProduced(out); } catch (Throwable ignored) {}
            return;
        }

        // If nothing could be generated, keep only metadata update.
    }

    private SimLLMClient createGuidanceMagiClient() {
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

    private void emitGuidanceOutcome(String consensus, String[] emotions, String[] brainStatuses) {
        try { SimEventBus.get().emitGuidanceOutcome(consensus, emotions, brainStatuses); } catch (Throwable ignored) {}
        if (emotions == null || emotions.length == 0) return;
        int emitted = 0;
        for (String e : emotions) {
            if (e == null || e.isBlank()) continue;
            double intensity = 88.0 - emitted * 8.0;
            try { SimEventBus.get().emitEmotionTagged(currentEmotionEntryId, e, intensity); } catch (Throwable ignored) {}
            emitted++;
            if (emitted >= 3) break;
        }
    }

    @Override
    public void onTemplateGenerationRequested(String text, String notebookName) {
        if (!settings.isEnabled()) return;
        String txt = (text == null) ? "" : text.strip();
        if (txt.isEmpty()) return;
        try { guidanceExecutor.execute(() -> generateTemplateAsync(txt, notebookName)); } catch (Throwable ignored) {}
    }

    private void generateTemplateAsync(String text, String notebookName) {
        SimTemplateDraft draft = null;
        if (settings.isLlmEnabled()) ensureLlm();
        if (llm != null) {
            try {
                String sys = CriticPromptDecorator.decorateSystem(PromptBuilder.systemPrompt(personality.getType()));
                String journal = text.length() > 2400 ? text.substring(text.length() - 2400) : text;
                String usr = String.join("\n\n",
                        moodContextForPrompt() + " " + emotionsContextForPrompt() + " " + factsContextForPrompt(),
                        "Create one practical journaling template from the text below.",
                        "Output format (strict):",
                        "NAME: <short title 2-5 words>",
                        "DESCRIPTION: <one concise sentence>",
                        "Q1: <question>",
                        "Q2: <question>",
                        "Q3: <question>",
                        "Q4: <question optional>",
                        "Rules: plain text only, no markdown, no bullets.",
                        "Journal text:\n\"\"\"\n" + journal + "\n\"\"\""
                );
                main.core.sim.llm.api.SimLLMResponse resp = llm.generate(
                        new main.core.sim.llm.api.SimLLMRequest(sys, usr, 260, 0.75),
                        Duration.ofSeconds(20)
                );
                draft = parseTemplateDraft(resp == null ? "" : resp.text);
            } catch (Throwable ignored) {
                draft = null;
            }
        }
        if (draft == null) {
            draft = fallbackTemplateDraft(text);
        }
        if (draft == null) return;
        if (draft.questions == null || draft.questions.length == 0) {
            draft.questions = new String[]{
                    "What feels most important for you to unpack right now?",
                    "What pattern do you notice in this experience?",
                    "What is one small step that would support you today?"
            };
        }
        try {
            SimEventBus.get().emitTemplateGenerated(
                    normalizeNotebookName(notebookName),
                    safeTemplateName(draft.name),
                    safeTemplateDescription(draft.description),
                    draft.questions
            );
        } catch (Throwable ignored) {}
    }

    private static String normalizeNotebookName(String notebookName) {
        if (notebookName == null) return "";
        String n = notebookName.trim();
        return n.isEmpty() ? "" : n;
    }

    private static String safeTemplateName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Sim Reflection Template";
        if (n.length() > 48) n = n.substring(0, 48).trim();
        return n;
    }

    private static String safeTemplateDescription(String description) {
        String d = description == null ? "" : description.trim();
        if (d.isEmpty()) return "Generated by Sim from your recent writing context.";
        if (d.length() > 180) d = d.substring(0, 180).trim();
        return d;
    }

    private SimTemplateDraft parseTemplateDraft(String text) {
        if (text == null || text.isBlank()) return null;
        String[] lines = text.replace('\r', '\n').split("\n");
        String name = "";
        String description = "";
        List<String> questions = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.regionMatches(true, 0, "NAME:", 0, 5)) {
                name = line.substring(5).trim();
                continue;
            }
            if (line.regionMatches(true, 0, "DESCRIPTION:", 0, 12)) {
                description = line.substring(12).trim();
                continue;
            }
            if (line.matches("(?i)^Q\\d+\\s*:.*")) {
                int idx = line.indexOf(':');
                if (idx >= 0 && idx < line.length() - 1) {
                    String q = line.substring(idx + 1).trim();
                    if (!q.isEmpty()) questions.add(q);
                }
                continue;
            }
            if (line.startsWith("-") || line.startsWith("•")) {
                String q = line.substring(1).trim();
                if (!q.isEmpty()) questions.add(q);
            }
        }
        if (name.isBlank() && description.isBlank() && questions.isEmpty()) return null;
        List<String> cleaned = new ArrayList<>();
        for (String q : questions) {
            if (q == null) continue;
            String c = q.trim();
            if (c.isEmpty()) continue;
            if (c.length() > 180) c = c.substring(0, 180).trim();
            cleaned.add(c);
            if (cleaned.size() >= 5) break;
        }
        if (cleaned.isEmpty()) cleaned = List.of(
                "What part of this situation feels most emotionally charged today?",
                "What need is underneath this feeling?",
                "What is one supportive action you can take next?"
        );
        SimTemplateDraft d = new SimTemplateDraft();
        d.name = safeTemplateName(name);
        d.description = safeTemplateDescription(description);
        d.questions = cleaned.toArray(new String[0]);
        return d;
    }

    private SimTemplateDraft fallbackTemplateDraft(String text) {
        String normalized = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        String name;
        String description;
        if (looksNegative(normalized)) {
            name = "Grounding Reset";
            description = "A template to slow down, process heavy feelings, and regain clarity.";
        } else {
            name = "Daily Reflection";
            description = "A template to reflect on your day and identify intentional next steps.";
        }
        SimTemplateDraft d = new SimTemplateDraft();
        d.name = name;
        d.description = description;
        d.questions = new String[]{
                "What stood out emotionally for you today?",
                "What thought or situation keeps returning, and why?",
                "What helped you cope or feel better, even a little?",
                "What is one small action you want to take next?"
        };
        return d;
    }

    private static final class SimTemplateDraft {
        String name;
        String description;
        String[] questions;
    }

    @Override
    public void onDailyPromptRequested(String dateKey) {
        if (!settings.isEnabled()) return;
        final String dayKey = normalizeDateKey(dateKey);
        boolean shouldGenerate;
        synchronized (dailyPromptLock) {
            shouldGenerate = dailyPromptInFlight.add(dayKey);
        }
        if (!shouldGenerate) return;
        try {
            dailyPromptExecutor.execute(() -> generateDailyPromptAsync(dayKey));
        } catch (Throwable ignored) {
            synchronized (dailyPromptLock) { dailyPromptInFlight.remove(dayKey); }
        }
    }

    private void generateDailyPromptAsync(String dateKey) {
        try {
            DailyPromptContext ctx = collectDailyPromptContext();
            String prompt = generateDailyPromptWithLlm(dateKey, ctx);
            if (prompt == null || prompt.isBlank()) {
                prompt = fallbackDailyPrompt(ctx);
            }
            try { SimEventBus.get().emitDailyPromptProduced(dateKey, DAILY_PROMPT_LABEL, prompt); } catch (Throwable ignored) {}
        } finally {
            synchronized (dailyPromptLock) { dailyPromptInFlight.remove(dateKey); }
        }
    }

    private String generateDailyPromptWithLlm(String dateKey, DailyPromptContext ctx) {
        if (!settings.isLlmEnabled()) return "";
        ensureLlm();
        if (llm == null) return "";
        try {
            String sys = CriticPromptDecorator.decorateSystem(PromptBuilder.systemPrompt(personality.getType()));
            String usr = String.join("\n",
                    "Create exactly one daily journaling prompt for " + dateKey + ".",
                    "Use the user's mood and recent entry themes as context.",
                    "Rules:",
                    "- one sentence only",
                    "- 16 to 32 words",
                    "- compassionate and practical",
                    "- no bullet, no numbering, no label, no quotation marks",
                    "- plain text only",
                    "",
                    "Context:",
                    ctx.asPromptContext(),
                    "",
                    "Daily prompt:");
            main.core.sim.llm.api.SimLLMResponse resp = llm.generate(
                    new main.core.sim.llm.api.SimLLMRequest(sys, usr, 120, 0.85),
                    Duration.ofSeconds(18)
            );
            return sanitizeDailyPrompt(resp == null ? "" : resp.text);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private DailyPromptContext collectDailyPromptContext() {
        DailyPromptContext ctx = new DailyPromptContext();
        try {
            List<SimDataGateway.MoodSample> moods = data.readMoodSamples(40);
            if (moods != null && !moods.isEmpty()) {
                ctx.moodAvg7 = averageMood(moods, 7);
                ctx.moodAvg30 = averageMood(moods, 30);
                ctx.moodTrend = moodTrend(moods);
                String avg7 = Double.isNaN(ctx.moodAvg7) ? "n/a" : String.format(java.util.Locale.ROOT, "%.0f", ctx.moodAvg7);
                String avg30 = Double.isNaN(ctx.moodAvg30) ? "n/a" : String.format(java.util.Locale.ROOT, "%.0f", ctx.moodAvg30);
                ctx.moodSummary = "Mood summary: 7-day avg " + avg7 + ", 30-day avg " + avg30 + ", trend " + ctx.moodTrend + ".";
            }
        } catch (Throwable ignored) {}

        try {
            List<File> entries = collectRecentEntries(8);
            if (!entries.isEmpty()) {
                StringBuilder snippetSummary = new StringBuilder();
                List<String> texts = new ArrayList<>();
                int taken = 0;
                for (File f : entries) {
                    if (f == null || !f.isFile()) continue;
                    String raw = data.readEntry(f, 1000);
                    String compact = compactText(raw, 220);
                    if (compact.isBlank()) continue;
                    texts.add(compact);
                    if (taken < 3) {
                        if (snippetSummary.length() > 0) snippetSummary.append(" | ");
                        snippetSummary.append(stripExtension(f.getName())).append(": ").append(compact);
                        taken++;
                    }
                }
                if (snippetSummary.length() > 0) {
                    ctx.entrySummary = "Recent entries: " + snippetSummary + ".";
                }
                String topTheme = inferTopTheme(texts);
                if (!topTheme.isBlank()) {
                    ctx.topTheme = topTheme;
                }
            }
        } catch (Throwable ignored) {}
        return ctx;
    }

    private List<File> collectRecentEntries(int limit) {
        List<File> out = new ArrayList<>();
        List<NotebookInfo> notebooks = data.listNotebooks();
        if (notebooks == null || notebooks.isEmpty()) return out;
        int perNotebook = Math.max(2, limit);
        for (NotebookInfo nb : notebooks) {
            if (nb == null) continue;
            List<File> entries = data.listEntriesByModifiedDesc(nb, perNotebook);
            if (entries == null || entries.isEmpty()) continue;
            out.addAll(entries);
        }
        out.sort(Comparator.comparingLong(File::lastModified).reversed());
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    private static double averageMood(List<SimDataGateway.MoodSample> moods, int window) {
        if (moods == null || moods.isEmpty() || window <= 0) return Double.NaN;
        int n = Math.min(window, moods.size());
        if (n <= 0) return Double.NaN;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            SimDataGateway.MoodSample sample = moods.get(i);
            if (sample == null) continue;
            sum += sample.value;
        }
        return sum / n;
    }

    private static String moodTrend(List<SimDataGateway.MoodSample> moods) {
        if (moods == null || moods.size() < 4) return "steady";
        int n = Math.min(14, moods.size());
        int half = n / 2;
        if (half < 2) return "steady";
        double recent = 0.0;
        double previous = 0.0;
        for (int i = 0; i < half; i++) recent += moods.get(i).value;
        for (int i = half; i < half * 2; i++) previous += moods.get(i).value;
        recent /= half;
        previous /= half;
        double delta = recent - previous;
        if (delta > 3.0) return "up";
        if (delta < -3.0) return "down";
        return "steady";
    }

    private static String compactText(String text, int maxChars) {
        if (text == null || text.isBlank()) return "";
        String out = text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (maxChars > 0 && out.length() > maxChars) {
            out = out.substring(out.length() - maxChars).trim();
        }
        return out;
    }

    private static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String inferTopTheme(List<String> texts) {
        if (texts == null || texts.isEmpty()) return "";
        java.util.Map<String, Integer> freq = new java.util.HashMap<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String[] parts = text.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+");
            for (String token : parts) {
                if (token == null || token.length() < 4) continue;
                if (DAILY_PROMPT_STOP_WORDS.contains(token)) continue;
                freq.put(token, freq.getOrDefault(token, 0) + 1);
            }
        }
        String best = "";
        int bestScore = 0;
        for (java.util.Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e == null || e.getKey() == null || e.getValue() == null) continue;
            int score = e.getValue();
            if (score > bestScore) {
                best = e.getKey();
                bestScore = score;
            }
        }
        return bestScore >= 2 ? best : "";
    }

    private String sanitizeDailyPrompt(String text) {
        if (text == null || text.isBlank()) return "";
        String raw = text.replace('\r', '\n').trim();
        String selected = "";
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty()) continue;
            s = s.replaceFirst("(?i)^(daily\\s*prompt|prompt|label)\\s*:\\s*", "");
            s = s.replaceFirst("^[\\-•\\d\\)\\.\\s]+", "");
            if (s.isEmpty()) continue;
            selected = s;
            break;
        }
        if (selected.isEmpty()) selected = raw;
        selected = selected.replaceAll("^\"+|\"+$", "").trim();
        if (selected.length() > 220) selected = selected.substring(0, 220).trim();
        return selected;
    }

    private String fallbackDailyPrompt(DailyPromptContext ctx) {
        String theme = ctx.topTheme;
        if (theme != null && !theme.isBlank()) {
            if (!Double.isNaN(ctx.moodAvg7) && ctx.moodAvg7 < 40.0) {
                return "When " + theme + " feels heavy today, what is one kind action you can take for yourself before tonight?";
            }
            if (!Double.isNaN(ctx.moodAvg7) && ctx.moodAvg7 > 70.0) {
                return "What part of " + theme + " lifted you today, and how can you carry that feeling into tomorrow morning?";
            }
            return "As you reflect on " + theme + " today, what pattern do you notice, and what one intentional step can you take before the day ends?";
        }
        if (!Double.isNaN(ctx.moodAvg7) && ctx.moodAvg7 < 40.0) {
            return "What felt most difficult today, and what one small, realistic act of self-care can you commit to before you sleep?";
        }
        if ("up".equals(ctx.moodTrend)) {
            return "What helped your mood rise today, and how can you repeat a small part of that support tomorrow?";
        }
        if ("down".equals(ctx.moodTrend)) {
            return "What may have pulled your mood down today, and what boundary or reset would support you for the rest of the evening?";
        }
        return "What moment from today deserves a deeper look, and what gentle next step would help you feel more grounded before tonight ends?";
    }

    private String normalizeDateKey(String dateKey) {
        if (dateKey != null && !dateKey.isBlank()) {
            try { return LocalDate.parse(dateKey.trim()).toString(); } catch (Throwable ignored) {}
        }
        return LocalDate.now().toString();
    }

    private static final class DailyPromptContext {
        double moodAvg7 = Double.NaN;
        double moodAvg30 = Double.NaN;
        String moodTrend = "steady";
        String moodSummary = "Mood summary: no recent mood data.";
        String entrySummary = "Recent entries: none.";
        String topTheme = "";

        String asPromptContext() {
            if (topTheme == null || topTheme.isBlank()) {
                return moodSummary + "\n" + entrySummary;
            }
            return moodSummary + "\n" + entrySummary + "\nDominant theme hint: " + topTheme + ".";
        }
    }

    private static final java.util.Set<String> DAILY_PROMPT_STOP_WORDS = java.util.Set.of(
            "that", "this", "with", "from", "have", "been", "were", "your", "about", "there", "their",
            "just", "then", "into", "after", "before", "because", "while", "where", "when", "what",
            "they", "them", "feel", "feels", "feeling", "today", "really", "very", "still", "also",
            "would", "could", "should", "being", "than", "over", "under", "more", "less",
            "make", "made", "doing", "done", "want", "wanted", "need", "needed", "time", "things"
    );

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
            case "magi": {
                String python = "python3";
                String model = "gpt-5";
                String apiKey = "";
                try { python = settings.getMagiPythonCommand(); } catch (Throwable ignored) {}
                try { model = settings.getMagiModel(); } catch (Throwable ignored) {}
                try { apiKey = settings.getOpenAIApiKey(); } catch (Throwable ignored) {}
                llmProviderActive = "magi";
                return new MagiClient(python, model, apiKey);
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

    private String normalizeGuidanceConsensus(String raw) {
        String c = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (c.isEmpty()) return "";
        if (c.contains("unanim")) return "Unanimous";
        if (c.contains("majority")) return "Majority";
        if (c.contains("condition")) return "Conditional";
        if (c.contains("deadlock")) return "Deadlock";
        if (c.contains("info")) return "Informational";
        if (c.length() > 24) c = c.substring(0, 24).trim();
        if (c.isEmpty()) return "";
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    private String[] buildGuidanceOutcomeEmotions(String inputText, String outputText, String[] modelEmotions) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (modelEmotions != null) {
            for (String e : modelEmotions) {
                if (e == null) continue;
                String s = e.trim().toLowerCase(java.util.Locale.ROOT);
                if (s.isEmpty()) continue;
                out.add(normalizeEmotionLabel(s));
                if (out.size() >= 3) return out.toArray(new String[0]);
            }
        }
        java.util.Map<String, Double> scores = detectGuidanceEmotionScores(
                ((inputText == null ? "" : inputText) + " " + (outputText == null ? "" : outputText)).trim()
        );
        if (!scores.isEmpty()) {
            java.util.List<java.util.Map.Entry<String, Double>> ranked = new java.util.ArrayList<>(scores.entrySet());
            ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (java.util.Map.Entry<String, Double> e : ranked) {
                if (e == null || e.getKey() == null) continue;
                String k = normalizeEmotionLabel(e.getKey());
                if (k.isEmpty()) continue;
                out.add(k);
                if (out.size() >= 3) break;
            }
        }
        return out.toArray(new String[0]);
    }

    private static String normalizeEmotionLabel(String label) {
        if (label == null) return "";
        String e = label.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.isEmpty()) return "";
        if (e.contains("joy") || e.contains("happy") || e.contains("grat") || e.contains("hope")) return "joy";
        if (e.contains("calm") || e.contains("peace") || e.contains("ground") || e.contains("content")) return "calm";
        if (e.contains("anger") || e.contains("mad") || e.contains("frustr") || e.contains("stress") || e.contains("overwhelm")) return "anger";
        if (e.contains("sad") || e.contains("anx") || e.contains("fear") || e.contains("worr") || e.contains("lonely") || e.contains("grief")) return "sad";
        if (e.contains("neutral") || e.contains("fine") || e.contains("ok")) return "neutral";
        return e;
    }

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
