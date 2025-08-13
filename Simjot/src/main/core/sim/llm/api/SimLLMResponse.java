package main.core.sim.llm.api;

public final class SimLLMResponse {
    public final String text;

    public SimLLMResponse(String text) {
        this.text = text == null ? "" : text.trim();
    }
}
