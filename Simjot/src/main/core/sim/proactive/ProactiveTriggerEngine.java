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

package main.core.sim.proactive;

import main.core.sim.llm.prompt.PromptBuilder;
import main.core.sim.persona.SimPersonality;
import main.core.sim.retrieval.RecencyBuffer;
import main.core.sim.retrieval.RetrievalRanker;
import main.core.sim.critic.CriticPromptDecorator;
import main.core.sim.memory.MemoryStore;
import main.core.sim.memory.PersistentMemoryStore;
import main.core.sim.engine.NudgePrefaceBuilder;

/**
 * Encapsulates proactive trigger detection and prompt construction.
 * It does NOT talk to the UI or LLM directly; it just decides if we should speak and with what prompts.
 */
public final class ProactiveTriggerEngine {

    public static final class Input {
        public long nowMs;
        public boolean enabled;
        public boolean quietHours;
        public String lastText = "";
        public long millisSinceLastType;
        public boolean negativeTone;
        public int shrinkEvents;
        public int emptyNoTokenStreak;
        public String lastTriggerKey = "";
        public long lastTriggerAtMs;
        public boolean holdOffEntryStart;
        public String preview = "";
        public String conversationSummary = "";
        public String emotionsContext = "";
        public SimPersonality.Type personalityType = SimPersonality.Type.GENTLE;
        public PersistentMemoryStore persistent;
        public MemoryStore session;
        public RecencyBuffer recency;
    }

    public static final class Output {
        public boolean fire;
        public String triggerKey;
        public String systemPrompt;
        public String userPrompt;
        public String preface;
    }

    public static Output evaluate(Input in) {
        Output out = new Output();
        if (!in.enabled) return out;
        if (in.quietHours) return out;

        String trimmed = in.lastText == null ? "" : in.lastText.trim();
        int len = trimmed.length();
        boolean shortPause = in.millisSinceLastType >= 1800L && len >= 60;
        boolean repeatedEdits = in.shrinkEvents >= 3 && len >= 30;
        boolean negativePause = in.negativeTone && in.millisSinceLastType >= 1200L && len >= 40;
        if (!(shortPause || repeatedEdits || negativePause)) return out;

        // Which trigger
        String triggerKey;
        long baseCooldownMs;
        if (negativePause) { triggerKey = "negativePause"; baseCooldownMs = 25000L; }
        else if (shortPause) { triggerKey = "shortPause"; baseCooldownMs = 45000L; }
        else { triggerKey = "repeatedEdits"; baseCooldownMs = 40000L; }

        // Same-trigger cooldown with backoff
        long sameTriggerCooldown = baseCooldownMs + Math.min(60000L, in.emptyNoTokenStreak * 5000L);
        if (triggerKey.equals(in.lastTriggerKey) && (in.nowMs - in.lastTriggerAtMs) < sameTriggerCooldown) {
            return out;
        }
        // Entry-start holdoff
        if (in.holdOffEntryStart) return out;

        // Build prompts
        String sys = CriticPromptDecorator.decorateSystem(
                PromptBuilder.systemPromptWithContext(
                        in.personalityType,
                        null,
                        null,
                        in.preview,
                        in.conversationSummary,
                        triggerKey
                )
        );
        String usr = PromptBuilder.userFromTyping(in.preview);
        if (in.emotionsContext != null && !in.emotionsContext.isBlank()) usr = usr + "\n\n" + in.emotionsContext;
        String ctx = RetrievalRanker.buildContext(in.persistent, in.session, in.recency, 4, 4);
        if (ctx != null && !ctx.isBlank()) usr = "Context: " + ctx + "\n\n" + usr;
        String preface = NudgePrefaceBuilder.buildPreface(triggerKey, trimmedPreview(in.preview), safeFactsHint(in));

        out.fire = true;
        out.triggerKey = triggerKey;
        out.systemPrompt = sys;
        out.userPrompt = usr;
        out.preface = preface;
        return out;
    }

    /** Build a mood-based proactive check-in (lowMood/highMood) with cooldowns. */
    public static Output evaluateMoodCheckIn(Input base, boolean isLow) {
        Output out = new Output();
        if (!base.enabled || base.quietHours) return out;
        String triggerKey = isLow ? "lowMood" : "highMood";
        long now = base.nowMs;
        long baseCooldown = isLow ? 25000L : 30000L;
        long backoff = Math.min(60000L, Math.max(0, base.emptyNoTokenStreak) * 5000L);
        long sameCooldown = baseCooldown + backoff;
        if (triggerKey.equals(base.lastTriggerKey) && (now - base.lastTriggerAtMs) < sameCooldown) return out;

        String preview = base.conversationSummary != null && !base.conversationSummary.isBlank() ? base.conversationSummary : trimmedPreview(base.preview);
        String sys = CriticPromptDecorator.decorateSystem(
                PromptBuilder.systemPromptWithContext(
                        base.personalityType,
                        null,
                        null,
                        preview,
                        preview,
                        triggerKey
                )
        );
        String usr = "Offer one short, compassionate check-in based on the user's current state. Plain text only.";
        String ctx = RetrievalRanker.buildContext(base.persistent, base.session, base.recency, 3, 3);
        if (ctx != null && !ctx.isBlank()) usr = "Context: " + ctx + "\n\n" + usr;
        String preface = NudgePrefaceBuilder.buildPreface(triggerKey, trimmedPreview(base.preview), safeFactsHint(base));

        out.fire = true;
        out.triggerKey = triggerKey;
        out.systemPrompt = sys;
        out.userPrompt = usr;
        out.preface = preface;
        return out;
    }

    /** Build a brief greeting for returning to main menu in proactive mode. */
    public static Output evaluateMenuGreeting(Input base) {
        Output out = new Output();
        if (!base.enabled || base.quietHours) return out;
        String triggerKey = "greet";
        long now = base.nowMs;
        long baseCooldown = 60000L;
        long backoff = Math.min(60000L, Math.max(0, base.emptyNoTokenStreak) * 5000L);
        long sameCooldown = baseCooldown + backoff;
        if (triggerKey.equals(base.lastTriggerKey) && (now - base.lastTriggerAtMs) < sameCooldown) return out;

        String sys = CriticPromptDecorator.decorateSystem(
                PromptBuilder.systemPrompt(base.personalityType)
        );
        String usr = "You just appeared as an unobtrusive overlay when the user returned to the main menu. Greet briefly and offer gentle help in one sentence. Plain text.";
        String ctx = RetrievalRanker.buildContext(base.persistent, base.session, base.recency, 2, 2);
        if (ctx != null && !ctx.isBlank()) usr = "Context: " + ctx + "\n\n" + usr;
        String preface = NudgePrefaceBuilder.buildPreface(triggerKey, trimmedPreview(base.preview), safeFactsHint(base));

        out.fire = true;
        out.triggerKey = triggerKey;
        out.systemPrompt = sys;
        out.userPrompt = usr;
        out.preface = preface;
        return out;
    }

    private static String safeFactsHint(Input in){
        try {
            String m1 = in.persistent != null ? in.persistent.getFactsSummary(1) : "";
            if (m1 == null || m1.isBlank()) m1 = in.session != null ? in.session.getFactsSummary(1) : "";
            return m1;
        } catch (Throwable ignored) { return ""; }
    }

    private static String trimmedPreview(String s){
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 200 ? t.substring(0,199) + "…" : t;
    }
}
