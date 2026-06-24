/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.sim.engine;

public final class NudgePrefaceBuilder {
    private NudgePrefaceBuilder() {}

    public static String buildPreface(String triggerKey, String trimmedText, String memoryHint) {
        String preface;
        if ("negativePause".equals(triggerKey)) {
            preface = "Hey, I noticed your tone might be heavy—prefer a reframe or a tiny next step?";
        } else if ("shortPause".equals(triggerKey)) {
            preface = "Hey, I noticed you’re drafting—want a 2‑minute nudge or a quick outline?";
        } else { // repeatedEdits or others
            preface = "Hey, I saw a few rapid edits—want a suggestion or a confident checkpoint?";
        }

        // Heuristic reasoning lines from recent text
        String feeling = ReasoningHeuristics.detectFeeling(trimmedText);
        if (feeling != null && !feeling.isBlank()) {
            preface += "\nYou said you were feeling " + feeling + ".";
        }
        String topic = ReasoningHeuristics.topicSnippet(trimmedText);
        if (topic != null && !topic.isBlank()) {
            preface += "\nWould you like to talk more about " + topic + "?";
        }

        // Add a brief memory hint if provided
        if (memoryHint != null && !memoryHint.isBlank()) {
            preface += " You once mentioned: " + memoryHint + ".";
        }
        return preface;
    }
}
