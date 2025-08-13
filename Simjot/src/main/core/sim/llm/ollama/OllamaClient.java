package main.core.sim.llm.ollama;

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
        return new SimLLMResponse(text);
    }

    // Build minimal JSON; avoids external JSON deps
    private static String esc(String s){
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String toGenerateJson(String model, String system, String prompt, int maxTokens, double temp) {
        StringBuilder sb = new StringBuilder(256 + prompt.length());
        sb.append('{')
          .append("\"model\":\"").append(esc(model)).append("\",")
          .append("\"prompt\":\"").append(esc(buildPrompt(system, prompt))).append("\",")
          .append("\"stream\":false,")
          .append("\"options\":{")
          .append("\"temperature\":").append(String.format(java.util.Locale.ROOT, "%.3f", temp)).append(',')
          .append("\"num_predict\":").append(Math.max(64, maxTokens))
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
