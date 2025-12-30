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

package main.core.sim.llm.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;

/**
 * Minimal OpenAI Chat Completions client (non-streaming) compatible with SimLLMClient.
 * Uses https://api.openai.com/v1/chat/completions
 */
public final class OpenAIClient implements SimLLMClient {
    private final HttpClient http;
    private final String apiKey;
    private final String model; // e.g., gpt-4o-mini
    private final String baseUrl; // allow override via env or settings if needed

    public OpenAIClient(String apiKey, String model) {
        this(apiKey, model, "https://api.openai.com");
    }

    public OpenAIClient(String apiKey, String model, String baseUrl) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gpt-4o-mini" : model.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com" : baseUrl.trim();
    }

    @Override
    public SimLLMResponse generate(SimLLMRequest request, Duration timeout) throws Exception {
        if (apiKey.isEmpty()) throw new IllegalStateException("OpenAI API key is missing");
        String body = toChatCompletionsJson(model, request.systemPrompt, request.userText, request.maxTokens, request.temperature);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout == null ? Duration.ofSeconds(30) : timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long dt = System.currentTimeMillis() - t0;
        System.out.println("[OpenAIClient] status=" + resp.statusCode() + " dtMs=" + dt + " respLen=" + (resp.body()==null?0:resp.body().length()));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OpenAI HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }
        String text = extractMessageContent(resp.body());
        if (text == null) text = "";
        text = sanitize(text);
        return new SimLLMResponse(text);
    }

    private static String toChatCompletionsJson(String model, String system, String user, int maxTokens, double temperature){
        // Simple manual JSON to avoid external deps
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"model\":\"").append(escape(model)).append("\",");
        sb.append("\"max_tokens\":").append(Math.max(32, maxTokens)).append(',');
        sb.append("\"temperature\":").append(Math.max(0.0, Math.min(2.0, temperature))).append(',');
        sb.append("\"messages\":[");
        if (system != null && !system.isBlank()){
            sb.append('{')
              .append("\"role\":\"system\",\"content\":\"")
              .append(escape(system)).append("\"},");
        }
        sb.append('{')
          .append("\"role\":\"user\",\"content\":\"")
          .append(escape(user == null ? "" : user)).append("\"}");
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String extractMessageContent(String json){
        if (json == null || json.isEmpty()) return null;
        // Very naive extraction for choices[0].message.content
        // Find "message":{"role":"assistant","content":"..."}
        int idxMsg = json.indexOf("\"message\"");
        if (idxMsg < 0) return null;
        int idxContent = json.indexOf("\"content\"", idxMsg);
        if (idxContent < 0) return null;
        int colon = json.indexOf(':', idxContent);
        if (colon < 0) return null;
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) return null;
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                // handle basic escapes
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else if (c == '"') out.append('"');
                else if (c == '\\') out.append('\\');
                else out.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escape(String s){
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // Light sanitize: mirror parts of OllamaClient.sanitize without private helpers
    private static String sanitize(String s) {
        if (s == null) return "";
        String out = s.replaceAll("(?is)<think>.*?</think>", "");
        out = out.replace("<think>", "").replace("</think>", "");
        out = out.replace('\r', ' ').replace('\n', ' ');
        out = out.replace('\u2019', '\'');
        out = out.replace('\u2018', '\'');
        out = out.replace('\u201C', '"').replace('\u201D', '"');
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private static String truncate(String s, int n){
        if (s == null) return null;
        if (s.length() <= n) return s;
        return s.substring(0, n) + "…";
    }
}
