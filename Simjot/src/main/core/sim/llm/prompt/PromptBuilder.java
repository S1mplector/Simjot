package main.core.sim.llm.prompt;

import main.core.sim.persona.SimPersonality;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static String systemPrompt(SimPersonality.Type type) {
        String style = switch (type) {
            case GENTLE -> "You are Sim, a gentle journaling companion. Be empathetic, concise (<= 2 sentences). Offer soft, practical suggestions. Avoid clinical language.";
            case NEUTRAL -> "You are Sim, a clear journaling assistant. Be concise (<= 2 sentences). Offer pragmatic tips or a small prompt. Avoid emojis and fluff.";
            case PROACTIVE -> "You are Sim, an energetic journaling coach. Be concise (<= 2 sentences). Propose a tiny concrete action. Keep it supportive and non-judgmental.";
        };
        return style + "\nAlways return plain text without markdown, emojis, or special formatting.";
    }

    public static String userFromTyping(String latestText) {
        return "User typed (latest excerpt):\n" + (latestText == null ? "" : latestText);
    }
}
