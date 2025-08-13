package main.core.sim.llm.ollama;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;

/**
 * Minimal Ollama client using the /api/generate endpoint (non-streaming).
 */
public final class OllamaClient implements SimLLMClient {
    private final HttpClient http;
    private final String endpoint; // e.g., http://localhost:11434
    private final String model;    // e.g., deepseek-r1:1.5b

    public OllamaClient(String endpoint, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:11434" : endpoint;
        this.model = model == null || model.isBlank() ? "deepseek-r1:1.5b" : model;
    }

    @Override
    public SimLLMResponse generate(SimLLMRequest request, Duration timeout) throws Exception {
        String body = toGenerateJson(model, request.systemPrompt, request.userText, request.maxTokens, request.temperature);
        long t0 = System.currentTimeMillis();
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/generate"))
                .timeout(timeout == null ? Duration.ofSeconds(25) : timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        System.out.println("[OllamaClient] POST " + endpoint + "/api/generate model=" + model + " bodyLen=" + body.length());
        HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long dt = System.currentTimeMillis() - t0;
        System.out.println("[OllamaClient] status=" + resp.statusCode() + " dtMs=" + dt + " respLen=" + (resp.body()==null?0:resp.body().length()));
        if (resp.statusCode() == 404) {
            // Retry once with deepseek fallback if user model isn't found
            if (!"deepseek-r1:1.5b".equals(this.model)) {
                String altBody = toGenerateJson("deepseek-r1:1.5b", request.systemPrompt, request.userText, request.maxTokens, request.temperature);
                System.out.println("[OllamaClient] retrying with model=deepseek-r1:1.5b");
                HttpRequest altReq = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/api/generate"))
                        .timeout(timeout == null ? Duration.ofSeconds(25) : timeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(altBody, StandardCharsets.UTF_8))
                        .build();
                long t1 = System.currentTimeMillis();
                HttpResponse<String> altResp = http.send(altReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long dt2 = System.currentTimeMillis() - t1;
                System.out.println("[OllamaClient] alt status=" + altResp.statusCode() + " dtMs=" + dt2 + " respLen=" + (altResp.body()==null?0:altResp.body().length()));
                if (altResp.statusCode() / 100 != 2) {
                    throw new IOException("Ollama HTTP " + altResp.statusCode());
                }
                String text2 = extractResponseField(altResp.body());
                return new SimLLMResponse(text2);
            }
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Ollama HTTP " + resp.statusCode());
        }
        String text = extractResponseField(resp.body());
        text = sanitize(text);
        return new SimLLMResponse(text);
    }

    /**
     * Real streaming using Ollama's stream=true. Reads line-delimited JSON events and
     * emits incremental chunks via onToken. Honors cancellation supplier.
     */
    public void generateStream(
            SimLLMRequest request,
            Duration timeout,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            BooleanSupplier cancelled
    ) {
        HttpRequest httpReq = null;
        try {
            String body = toGenerateStreamJson(model, request.systemPrompt, request.userText, request.maxTokens, request.temperature);
            httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/generate"))
                    .timeout(timeout == null ? Duration.ofSeconds(25) : timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            System.out.println("[OllamaClient] POST (stream) " + endpoint + "/api/generate model=" + model);
            HttpResponse<InputStream> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Ollama HTTP " + resp.statusCode());
            }
            try (InputStream is = resp.body(); InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader br = new BufferedReader(isr)) {
                String line;
                StringBuilder acc = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        System.out.println("[OllamaClient] stream cancelled by caller");
                        break;
                    }
                    if (line.isBlank()) continue;
                    // Each line is a JSON object; extract "response" and check done flag
                    String piece = extractResponseField(line);
                    if (piece != null && !piece.isBlank()) {
                        acc.append(piece);
                        // Emit small chunks to reduce choppiness
                        if (acc.length() >= 80 || piece.endsWith("\n")) {
                            String out = sanitize(acc.toString());
                            if (!out.isBlank()) {
                                try { onToken.accept(out); } catch (Throwable ignored) {}
                            }
                            acc.setLength(0);
                        }
                    }
                    // Detect done: naive check for "\"done\":true" in line
                    if (line.contains("\"done\":true")) {
                        break;
                    }
                }
                // Flush any remainder
                if (acc.length() > 0) {
                    String out = sanitize(acc.toString());
                    if (!out.isBlank()) {
                        try { onToken.accept(out); } catch (Throwable ignored) {}
                    }
                }
            }
            if (onComplete != null) onComplete.run();
        } catch (Throwable e) {
            if (onError != null) onError.accept(e);
        }
    }

    // Convert escaped unicode sequences and remove DeepSeek reasoning tags
    private static String sanitize(String s) {
        if (s == null) return null;
        String out = s;
        // Normalize any lost backslashes (defensive when naive parsing removed them)
        out = out.replace("u003c", "\\u003c").replace("u003e", "\\u003e").replace("u0026", "\\u0026");
        // Unescape common HTML-ish escapes from JSON form
        out = out.replace("\\u003c", "<").replace("\\u003e", ">").replace("\\u0026", "&");
        // Remove DeepSeek chain-of-thought between <think> ... </think>
        out = out.replaceAll("(?is)<think>.*?</think>", "");
        // Also strip leftover tags if model emitted malformed ones
        out = out.replace("<think>", "").replace("</think>", "");
        // Trim whitespace and stray quotes
        out = out.trim();
        if (out.startsWith("\"") && out.endsWith("\"") && out.length() >= 2) {
            out = out.substring(1, out.length()-1);
        }
        out = out.trim();
        out = refinePlaintext(out);
        return out.trim();
    }

    private static String[] sentenceSplit(String text) {
        if (text == null) return new String[0];
        String t = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return new String[0];
        return t.split("(?<=[.!?])\\s+");
    }

    // Heuristic filter: prefer direct, second-person supportive sentences; remove meta/analysis
    private static String refinePlaintext(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        // Sentence split on ., !, ? keeping delimiters
        String[] raw = t.split("(?<=[.!?])\\s+");
        java.util.List<String> keep = new java.util.ArrayList<>();
        java.util.Set<String> banWords = new java.util.HashSet<>(java.util.Arrays.asList(
                "the user", "user's", "they wrote", "journal", "entry", "query", "question", "let me", "i will", "i'll", "i think", "thinking", "approach this"
        ));
        for (String s : raw) {
            String lc = s.toLowerCase(java.util.Locale.ROOT).trim();
            boolean banned = false;
            for (String w : banWords) { if (lc.contains(w)) { banned = true; break; } }
            if (banned) continue;
            // Prefer second person
            boolean second = lc.contains(" you ") || lc.startsWith("you ") || lc.contains(" your ") || lc.startsWith("it's ok") || lc.startsWith("it's okay");
            if (second || keep.isEmpty()) keep.add(s.trim());
            if (keep.size() >= 3) break;
        }
        if (keep.isEmpty()) return text.trim();
        return String.join(" ", keep).trim();
    }

    // Build minimal JSON; avoids external JSON deps
    private static String esc(String s){
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String toGenerateJson(String model, String system, String user, int maxTokens, double temperature) {
        String prompt = (system == null || system.isBlank() ? "" : (system + "\n\n")) + (user == null ? "" : user);
        // Include non-streaming and stop tokens to avoid reasoning leakage (placed under options)
        String[] stops = new String[]{"</think>", "<think>", "<think", "</think", "<|endofthink|>"};
        StringBuilder sb = new StringBuilder(512 + prompt.length());
        sb.append('{')
          .append("\"model\":\"").append(esc(model)).append('\"')
          .append(',')
          .append("\"prompt\":\"").append(esc(prompt)).append('\"')
          .append(',')
          .append("\"stream\":false,")
          .append("\"num_predict\":").append(Math.max(64, maxTokens))
          .append(',')
          .append("\"options\":{")
          .append("\"temperature\":").append(String.format(java.util.Locale.ROOT, "%.3f", temperature)).append(',')
          .append("\"stop\":[");
        for (int i = 0; i < stops.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(esc(stops[i])).append('\"');
        }
        sb.append(']')
          .append('}')
          .append('}');
        return sb.toString();
    }

    private static String toGenerateStreamJson(String model, String system, String user, int maxTokens, double temperature) {
        String prompt = (system == null || system.isBlank() ? "" : (system + "\n\n")) + (user == null ? "" : user);
        String[] stops = new String[]{"</think>", "<think>", "<think", "</think", "<|endofthink|>"};
        StringBuilder sb = new StringBuilder(512 + prompt.length());
        sb.append('{')
          .append("\"model\":\"").append(esc(model)).append('\"')
          .append(',')
          .append("\"prompt\":\"").append(esc(prompt)).append('\"')
          .append(',')
          .append("\"stream\":true,")
          .append("\"num_predict\":").append(Math.max(64, maxTokens))
          .append(',')
          .append("\"options\":{")
          .append("\"temperature\":").append(String.format(java.util.Locale.ROOT, "%.3f", temperature)).append(',')
          .append("\"stop\":[");
        for (int i = 0; i < stops.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(esc(stops[i])).append('\"');
        }
        sb.append(']')
          .append('}')
          .append('}');
        return sb.toString();
    }

    private static String buildPrompt(String system, String user){
        if (system == null || system.isBlank()) return user == null ? "" : user;
        // Simple system+user concatenation; can be extended to chat format later
        return "[System]\n" + system + "\n\n[User]\n" + (user == null ? "" : user);
    }

    private static String extractResponseField(String json) {
        // Very small, naive parser for { ..., "response":"...", ... }
        int i = json.indexOf("\"response\"");
        if (i < 0) return "";
        i = json.indexOf(':', i);
        if (i < 0) return "";
        int start = json.indexOf('"', i + 1);
        if (start < 0) return "";
        start++;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int k = start; k < json.length(); k++) {
            char c = json.charAt(k);
            if (esc) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c); break;
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
