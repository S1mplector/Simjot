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

package main.core.sim.critic;

/**
 * Adds a lightweight self-critique instruction to the system prompt to reduce off-target replies.
 */
public final class CriticPromptDecorator {
    private static final String CRITIC = "\n\nBefore responding: briefly self-check for (1) relevance to the user's current need, (2) hallucinations, (3) privacy/tone. If uncertain, ask ONE short clarifying question instead of guessing. Plain text only.";

    private CriticPromptDecorator() {}

    public static String decorateSystem(String baseSystem){
        if (baseSystem == null || baseSystem.isBlank()) return CRITIC.trim();
        if (baseSystem.contains("Before responding: briefly self-check")) return baseSystem; // idempotent
        return baseSystem + CRITIC;
    }
}
