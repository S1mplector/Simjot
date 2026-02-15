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
    // Optional per-brain decision statuses in fixed order:
    // [melchior, balthasar, casper]
    public final String[] brainStatuses;

    public SimLLMResponse(String text) {
        this(text, "", null, null);
    }

    public SimLLMResponse(String text, String consensus, String[] emotions) {
        this(text, consensus, emotions, null);
    }

    public SimLLMResponse(String text, String consensus, String[] emotions, String[] brainStatuses) {
        this.text = text == null ? "" : text.trim();
        this.consensus = consensus == null ? "" : consensus.trim();
        this.emotions = emotions == null ? new String[0] : emotions.clone();
        this.brainStatuses = brainStatuses == null ? new String[0] : brainStatuses.clone();
    }
}
