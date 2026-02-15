/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.llm.api;

public final class SimLLMResponse {
    public final String text;
    public final String consensus;
    public final String[] emotions;

    public SimLLMResponse(String text) {
        this(text, "", null);
    }

    public SimLLMResponse(String text, String consensus, String[] emotions) {
        this.text = text == null ? "" : text.trim();
        this.consensus = consensus == null ? "" : consensus.trim();
        this.emotions = emotions == null ? new String[0] : emotions.clone();
    }
}
