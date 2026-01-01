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

    public SimLLMResponse(String text) {
        this.text = text == null ? "" : text.trim();
    }
}
