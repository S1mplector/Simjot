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
import main.core.sim.llm.ollama.OllamaClient;
import main.core.sim.llm.openai.OpenAIClient;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;

import javax.swing.*;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Default implementation that reads from SimSettings and calls the selected LLM provider.
 * Keeps UI-independent; returns plain text via callback.
 */
public class DefaultSimSuggestionService implements SimSuggestionService {

    @Override
    public void queryAsync(String word, String rhymeKey, int syllables, String fullText, Consumer<String> onComplete) {
        try {
            SimSettings ss = SimSettings.get();
            if (!ss.isEnabled() || !ss.isLlmEnabled()) {
                safeComplete(onComplete, null);
                return;
            }
            final SimLLMClient client = buildClient(ss.getLlmProvider(), ss);
            if (client == null) {
                safeComplete(onComplete, null);
                return;
            }
            final String poem = fullText == null ? "" : fullText;
            final String system = buildSystemPrompt();
            final String user = buildUserPrompt(word, rhymeKey, syllables, poem);
            final SimLLMRequest req = new SimLLMRequest(system, user, 256, 0.7);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    try {
                        SimLLMResponse resp = client.generate(req, Duration.ofSeconds(10));
                        return resp == null ? "" : resp.text;
                    } catch (Throwable t) {
                        return "";
                    }
                }
                @Override
                protected void done() {
                    String result = "";
                    try { result = get(); } catch (Throwable ignored) {}
                    final String simOut = (result == null || result.isBlank()) ? null : result.trim();
                    safeComplete(onComplete, simOut);
                }
            };
            worker.execute();
        } catch (Throwable ignored) {
            safeComplete(onComplete, null);
        }
    }

    private static void safeComplete(Consumer<String> onComplete, String text) {
        if (onComplete == null) return;
        try {
            SwingUtilities.invokeLater(() -> onComplete.accept(text));
        } catch (Throwable ignored) {
            // fall back to direct
            try { onComplete.accept(text); } catch (Throwable ignore2) {}
        }
    }

    private static SimLLMClient buildClient(String provider, SimSettings ss) {
        try {
            String p = (provider == null ? "ollama" : provider.trim().toLowerCase(Locale.ROOT));
            switch (p) {
                case "openai":
                    String key = ss.getOpenAIApiKey();
                    if (key == null || key.isBlank()) return null;
                    return new OpenAIClient(key, ss.getOpenAIModel(), ss.getOpenAIBaseUrl());
                case "ollama":
                default:
                    return new OllamaClient(ss.getOllamaEndpoint(), ss.getOllamaModel());
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private static String buildSystemPrompt() {
        String base = main.core.sim.llm.prompt.PromptBuilder.systemPrompt(SimPersonality.Type.GENTLE);
        return base + "\n\nYou are also a succinct poetry craft assistant. When asked for rhyme guidance:"
                + "\n- Consider the poem context."
                + "\n- Prefer concise lists."
                + "\n- Avoid any meta, XML/HTML, code fences, or chain-of-thought."
                + "\n- Output plain text only under 120 words.";
    }

    private static String buildUserPrompt(String word, String rhymeKey, int syllables, String poem) {
        String head = "CaretWord: '" + word + "' (syllables=" + syllables + ", rhymeKey=" + (rhymeKey==null?"-":rhymeKey) + ")";
        String ctx = poem == null ? "" : poem.trim();
        if (ctx.length() > 2000) ctx = ctx.substring(Math.max(0, ctx.length()-2000));
        return String.join("\n",
                head,
                "Poem (tail excerpt):",
                ctx,
                "\nTask: Suggest compact, high-quality options for these sections:",
                "- Rhymes (best 6):",
                "- Near rhymes (best 6):",
                "- Slant rhymes (if useful, <=4):",
                "- Poetic synonyms (<=8), avoid clichés:",
                "- Imagery/metaphor cues (<=3, short):"
        );
    }
}
