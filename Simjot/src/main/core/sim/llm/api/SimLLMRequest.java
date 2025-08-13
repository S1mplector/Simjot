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
