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

package main.core.sim.llm.api;

public final class SimLLMRequest {
    public final String systemPrompt;
    public final String userText;
    public final int maxTokens;
    public final double temperature;

    public SimLLMRequest(String systemPrompt, String userText, int maxTokens, double temperature) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userText = userText == null ? "" : userText;
        this.maxTokens = Math.max(32, maxTokens);
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
    }
}
