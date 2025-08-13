package main.core.sim.llm.prompt;

import main.core.sim.persona.SimPersonality;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static String systemPrompt(SimPersonality.Type type) {
        String tone;
        switch (type) {
            case GENTLE: tone = "You are Sim, a gentle, supportive journaling companion. Offer short, empathetic reflections."; break;
            case NEUTRAL: tone = "You are Sim, a balanced journaling companion. Offer brief, clear, and supportive reflections."; break;
            case PROACTIVE: tone = "You are Sim, an encouraging journaling companion. Offer brief, motivating reflections and gentle prompts."; break;
            default: tone = "You are Sim, a supportive journaling companion.";
        }
        return String.join(" ",
                tone,
                "Focus on emotional validation and small, actionable steps.",
                "Keep replies 1–3 sentences.",
                "Do NOT include chain-of-thought, reasoning tags, or any <think>...</think> content.",
                "Output plain text only—no XML/HTML/Markdown, no code fences.");
    }

    /**
     * An enhanced system prompt that adds dynamic context if available.
     * None of the parameters are required; null/blank values are omitted.
     * This preserves the existing persona tone while giving the model
     * lightweight awareness of mood, recent input, conversation summary,
     * and the reason for any proactive trigger.
     */
    public static String systemPromptWithContext(
            SimPersonality.Type type,
            String moodLabel,          // e.g., "calm", "frustrated"
            Double moodValence,        // [-1..1] if available
            String recentInputPreview, // a short sanitized snippet (<= 200 chars)
            String conversationSummary,// rolling summary kept under token budget
            String triggerReason       // why Sim is speaking now (for proactivity)
    ) {
        String base = systemPrompt(type);
        StringBuilder ctx = new StringBuilder(base.length() + 512);
        ctx.append(base);

        // Add a short, structured context footer that is safe for plain-text models
        // and keeps the instruction boundary clear.
        ctx.append("\n\nContext (for Sim only; do NOT reflect this meta to the user):");

        boolean any = false;
        if (moodLabel != null && !moodLabel.isBlank()) {
            ctx.append("\n- Mood: ").append(sanitizeInline(moodLabel));
            any = true;
        }
        if (moodValence != null) {
            double v = Math.max(-1.0, Math.min(1.0, moodValence));
            ctx.append("\n- MoodValence: ").append(String.format(java.util.Locale.ROOT, "%.2f", v));
            any = true;
        }
        if (recentInputPreview != null && !recentInputPreview.isBlank()) {
            ctx.append("\n- RecentInputPreview: ").append(trimForPreview(recentInputPreview, 200));
            any = true;
        }
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            ctx.append("\n- ConversationSummary: ").append(trimForPreview(conversationSummary, 400));
            any = true;
        }
        if (triggerReason != null && !triggerReason.isBlank()) {
            ctx.append("\n- TriggerReason: ").append(trimForPreview(triggerReason, 160));
            any = true;
        }

        if (!any) {
            // If no context provided, avoid adding the header noise
            return base;
        }
        return ctx.toString();
    }

    private static String sanitizeInline(String s) {
        if (s == null) return "";
        String out = s.replace('\n', ' ').replace('\r', ' ');
        // Collapse whitespace
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private static String trimForPreview(String s, int max) {
        String t = sanitizeInline(s);
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    public static String userFromTyping(String latestText) {
        return String.join("\n",
                "The following is the user's raw journal text.",
                "Reply as Sim directly to the user in SECOND PERSON (you).",
                "Begin immediately with the supportive message—no preface, no meta commentary, no analysis.",
                "Plain text only. 1–3 sentences.",
                "\nJournal:",
                latestText);
    }
}
