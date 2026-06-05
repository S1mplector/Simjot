/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

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
import main.infrastructure.io.NativeJson;

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
                StringBuilder acc = new StringBuilder(1024);
                String lastEmitted = "";
                boolean finalized = false;
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
                        // Drop everything up to and including the last </think> if present
                        String visibleSrc = decodeUnicodeish(acc.toString());
                        int lastThinkEnd = java.util.regex.Pattern.compile("(?i)</think>").matcher(visibleSrc).results()
                                .mapToInt(r -> r.end()).reduce(-1, (a,b) -> b);
                        int lastThinkOpen = java.util.regex.Pattern.compile("(?i)<think>").matcher(visibleSrc).results()
                                .mapToInt(r -> r.start()).reduce(-1, (a,b) -> b);
                        if (lastThinkEnd > 0) {
                            // If we have a closing tag, drop all content up to it
                            visibleSrc = visibleSrc.substring(lastThinkEnd);
                        } else if (lastThinkOpen >= 0) {
                            // We are inside an open <think> ... (no closing yet). Suppress everything from the open tag onward.
                            visibleSrc = visibleSrc.substring(0, lastThinkOpen);
                        }
                        String sanitized = sanitize(visibleSrc);
                        // If we have a first sentence boundary, finalize to that to prevent trailing meta
                        if (!finalized) {
                            int end = firstSentenceEnd(sanitized);
                            if (end >= 0) {
                                String candidate = sanitized.substring(0, end + 1).trim();
                                // Avoid finalizing on weak one-word greetings; wait for more content
                                if (!isWeakGreeting(candidate)) {
                                    sanitized = candidate;
                                }
                            }
                        }
                        // Delta-emit only the new visible portion after filtering meta
                        if (sanitized.length() > lastEmitted.length()) {
                            String delta = sanitized.substring(lastEmitted.length());
                            // Insert a boundary space if needed to avoid concatenation across token boundaries
                            if (!lastEmitted.isEmpty() && !delta.isEmpty()) {
                                char prev = lastEmitted.charAt(lastEmitted.length() - 1);
                                char next = delta.charAt(0);
                                boolean prevIsPunct = (prev == '.' || prev == '!' || prev == '?' || prev == ';' || prev == ':');
                                boolean nextIsWord = Character.isLetterOrDigit(next);
                                if (!Character.isWhitespace(prev) && !Character.isWhitespace(next)) {
                                    // Case 1: both sides are alnum -> add a space
                                    if (Character.isLetterOrDigit(prev) && nextIsWord) {
                                        delta = " " + delta;
                                    }
                                    // Case 2: prev is closing punctuation and next begins a word -> add a space
                                    else if (prevIsPunct && nextIsWord) {
                                        delta = " " + delta;
                                    }
                                }
                            }
                            if (!delta.isBlank()) {
                                try { onToken.accept(delta); } catch (Throwable ignored) {}
                            }
                            lastEmitted = sanitized;
                            if (!finalized) {
                                int end = firstSentenceEnd(lastEmitted);
                                if (end >= 0) {
                                    String candidate = lastEmitted.substring(0, end + 1).trim();
                                    if (!isWeakGreeting(candidate)) {
                                        finalized = true;
                                        break; // stop reading further to avoid late meta/templates
                                    }
                                }
                            }
                        }
                    }
                }
                // Final flush in case remaining sanitized text wasn't emitted
                String visibleSrc = decodeUnicodeish(acc.toString());
                int lastThinkEnd = java.util.regex.Pattern.compile("(?i)</think>").matcher(visibleSrc).results()
                        .mapToInt(r -> r.end()).reduce(-1, (a,b) -> b);
                int lastThinkOpen = java.util.regex.Pattern.compile("(?i)<think>").matcher(visibleSrc).results()
                        .mapToInt(r -> r.start()).reduce(-1, (a,b) -> b);
                if (lastThinkEnd > 0) {
                    visibleSrc = visibleSrc.substring(lastThinkEnd);
                } else if (lastThinkOpen >= 0) {
                    visibleSrc = visibleSrc.substring(0, lastThinkOpen);
                }
                String sanitized = sanitize(visibleSrc);
                if (sanitized != null && !sanitized.isEmpty()) {
                    int end = firstSentenceEnd(sanitized);
                    if (end >= 0) {
                        String candidate = sanitized.substring(0, end + 1).trim();
                        if (!isWeakGreeting(candidate)) {
                            sanitized = candidate;
                        }
                    }
                }
                if (sanitized.length() > 0 && sanitized.length() > lastEmitted.length()) {
                    String delta = sanitized.substring(lastEmitted.length());
                    if (!lastEmitted.isEmpty() && !delta.isEmpty()) {
                        char prev = lastEmitted.charAt(lastEmitted.length() - 1);
                        char next = delta.charAt(0);
                        boolean prevIsPunct = (prev == '.' || prev == '!' || prev == '?' || prev == ';' || prev == ':');
                        boolean nextIsWord = Character.isLetterOrDigit(next);
                        if (!Character.isWhitespace(prev) && !Character.isWhitespace(next)) {
                            if (Character.isLetterOrDigit(prev) && nextIsWord) {
                                delta = " " + delta;
                            } else if (prevIsPunct && nextIsWord) {
                                delta = " " + delta;
                            }
                        }
                    }
                    if (!delta.isBlank()) {
                        try { onToken.accept(delta); } catch (Throwable ignored) {}
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
        if (s == null || s.isBlank()) return s;
        String out = s;
        // Decode common unicode-escape sequences that may appear without backslashes from JSON piping
        out = decodeUnicodeish(out);
        // Strip DeepSeek/Reasoning chain-of-thought blocks entirely
        out = out.replaceAll("(?is)<think>.*?</think>", "");
        out = out.replace("<think>", "").replace("</think>", "");
        // Also drop fenced reasoning markers that some models use
        out = out.replaceAll("(?is)```(thinking|reasoning)[\n\r].*?```", "");
        // Normalize quotes and whitespace early
        out = out.replace('\r', ' ').replace('\n', ' ');
        out = out.replace('\u2019', '\''); // smart apostrophe to ASCII
        out = out.replace('\u2018', '\'');
        out = out.replace('\u201C', '"').replace('\u201D', '"');
        out = out.replaceAll("\\s+", " ").trim();
        // Remove early meta/planning preamble up to first conversational cue
        String lower = out.toLowerCase(java.util.Locale.ROOT);
        String[] cues = new String[]{
                "hi ", "hello", "hey ", "sure", "great", "i'm ", "i am ", "how can i", "how may i", "happy to", "what can i", "how can we", "thanks for"
        };
        int firstCue = -1;
        for (String c : cues) {
            int idx = lower.indexOf(c);
            if (idx >= 0) {
                if (firstCue < 0 || idx < firstCue) firstCue = idx;
            }
        }
        if (firstCue > 0) {
            out = out.substring(firstCue).trim();
            lower = out.toLowerCase(java.util.Locale.ROOT);
        }
        // Also drop leading fillers like "okay,", "alright,"
        out = out.replaceFirst("(?i)^(okay|alright|ok)\\s*,\\s*", "").trim();
        // If the model provided a suggested final in quotes, prefer the last quoted segment
        java.util.regex.Pattern qp = java.util.regex.Pattern.compile("\"([^\"]{8,})\"");
        java.util.regex.Matcher qm = qp.matcher(out);
        String quoted = null;
        while (qm.find()) { quoted = qm.group(1).trim(); }
        if (quoted != null && !quoted.toLowerCase(java.util.Locale.ROOT).contains("<think")) {
            out = quoted;
        }
        // Fix missing spaces after punctuation due to tokenization
        out = out.replaceAll("([.!?,;:])(\\S)", "$1 $2");
        // Final refinement keeps user-facing sentences only
        out = refinePlaintext(out);
        // Gentle warmth tweak for starkly generic lines
        String tl = out.trim().toLowerCase(java.util.Locale.ROOT);
        if (tl.equals("how can i assist you today?") || tl.equals("how can i help?")) {
            out = "How can I help right now?";
        }
        return out.trim();
    }

    // Decode sequences like \\u003c or u003c to '<', and common smart quotes
    private static String decodeUnicodeish(String in) {
        if (in == null || in.isEmpty()) return in;
        String t = in;
        // Handle both \\uXXXX and uXXXX variants for a few common codes first
        t = t.replaceAll("(?i)\\\\?u003c", "<"); // '<'
        t = t.replaceAll("(?i)\\\\?u003e", ">"); // '>'
        t = t.replaceAll("(?i)\\\\?u0026", "&"); // '&'
        t = t.replaceAll("(?i)\\\\?u2019", "'");
        t = t.replaceAll("(?i)\\\\?u2018", "'");
        t = t.replaceAll("(?i)\\\\?u201c", "\"");
        t = t.replaceAll("(?i)\\\\?u201d", "\"");
        // Generic \\uXXXX unescape
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        java.util.regex.Matcher m = p.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int cp = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(String.valueOf((char) cp)));
        }
        m.appendTail(sb);
        // Also handle bare uXXXX (without leading backslash)
        p = java.util.regex.Pattern.compile("(?i)u([0-9a-fA-F]{4})");
        m = p.matcher(sb.toString());
        StringBuffer sb2 = new StringBuffer();
        while (m.find()) {
            int cp = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb2, java.util.regex.Matcher.quoteReplacement(String.valueOf((char) cp)));
        }
        m.appendTail(sb2);
        return sb2.toString();
    }

    // Heuristic filter: prefer direct, second-person supportive sentences; remove meta/analysis
    private static String refinePlaintext(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text.trim();
        String lower = t.toLowerCase(java.util.Locale.ROOT);
        // Unglue some very common phrases observed from small models
        t = t
                .replaceAll("(?i)\\bhowareyou\\b", "How are you")
                .replaceAll("(?i)\\bhowareyoudoing\\b", "How are you doing")
                .replaceAll("(?i)\\bhowareyoudoingtoday\\b", "How are you doing today")
                .replaceAll("(?i)\\bhowcanIassistyoutoday\\b", "How can I assist you today")
                .replaceAll("(?i)\\byoumeanlisteningtoyou\\b", "You mean listening to you")
                .replaceAll("(?i)\\biam\\b", "I am")
                .replaceAll("(?i)\\bim\\b", "I’m")
        ;
        // If string has no spaces and is long, try a light fix for question starts
        if (!t.contains(" ") && t.length() >= 10) {
            if (lower.startsWith("howareyou")) t = "How are you?";
            else if (lower.startsWith("howcan")) t = "How can I help?";
        }
        // Add missing terminal punctuation for questions
        if (t.matches("(?i).*(how|what|why|can|could|would|do you|are you).*") && !t.endsWith("?") && t.length() <= 80) {
            t = t + "?";
        }
        // Remove trailing assistant meta markers sometimes produced by templates
        t = t.replaceAll("\\s*\\(as an ai.*$", "");
        // Sentence split on ., !, ? keeping delimiters
        String[] raw = t.split("(?<=[.!?])\\s+");
        java.util.List<String> keep = new java.util.ArrayList<>();
        java.util.Set<String> banWords = new java.util.HashSet<>(java.util.Arrays.asList(
                // meta/process and third-person analysis
                "the user", "user's", "they wrote", "journal", "entry", "query", "question", "approach this", "respond ", "respond", "analysis", "chain-of-thought", "chain of thought",
                // first-person meta planning
                "let me", "i will", "i'll", "i think", "thinking", "i should", "i need", "maybe", "let's", "okay", "alright", "something like", "so the user", "here's my response", "putting it together", "it's clear", "my tone", "the tone", "i aim to", "i strive to", "i'll make sure", "i will ensure", "the goal is", "as an ai", "i cannot", "i can't", "i'm designed to",
                // template-y meta
                "to show empathy", "that should", "cover it", "without getting", "lengthy", "i shouldn't", "avoid any"
        ));
        for (String s : raw) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            String lc = trimmed.toLowerCase(java.util.Locale.ROOT);
            boolean banned = false;
            for (String w : banWords) { if (lc.contains(w)) { banned = true; break; } }
            if (banned) continue;
            // Accept user-facing sentences
            boolean second = lc.contains(" you ") || lc.startsWith("you ") || lc.contains(" your ") || lc.endsWith("?");
            boolean greeting = lc.startsWith("hi ") || lc.startsWith("hello") || lc.startsWith("hey ") || lc.startsWith("sure") || lc.startsWith("thanks ");
            boolean firstPersonStatus = lc.startsWith("i'm ") || lc.startsWith("i am ");
            if (second || greeting || firstPersonStatus) {
                keep.add(trimmed);
            }
            if (keep.size() >= 2) break; // keep at most two sentences
        }
        if (keep.isEmpty()) return ""; // suppress meta-only content entirely
        String joined = String.join(" ", keep).trim();
        // Final punctuation spacing pass
        joined = joined.replaceAll("([.!?,;:])(\\S)", "$1 $2");
        // Remove dangling quotes
        if ((joined.startsWith("\"") && !joined.endsWith("\"")) || (joined.endsWith("\"") && !joined.startsWith("\""))) {
            joined = joined.replace("\"", "");
        }
        return joined;
    }

    // Return the index of the first sentence-ending punctuation (., !, ?) or -1 if none
    private static int firstSentenceEnd(String s) {
        if (s == null || s.isEmpty()) return -1;
        final int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return -1;
    }

    // Heuristic: consider very short, generic greetings as weak; do not finalize stream on these alone
    private static boolean isWeakGreeting(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        String lc = t.toLowerCase(java.util.Locale.ROOT);
        // strip terminal punctuation
        while (!lc.isEmpty()) {
            char last = lc.charAt(lc.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == '"' || last == '\'') {
                lc = lc.substring(0, lc.length() - 1);
            } else break;
        }
        lc = lc.trim();
        // One or two-word greetings are considered weak
        String[] words = lc.split("\\s+");
        if (words.length <= 2) {
            java.util.Set<String> greet = new java.util.HashSet<>(java.util.Arrays.asList(
                    "hi", "hello", "hey", "hiya", "howdy", "thanks", "thank you"
            ));
            if (greet.contains(lc)) return true;
            if (words.length == 2 && (greet.contains(words[0]) || greet.contains(words[1]))) return true;
        }
        // Also treat text shorter than 5 visible letters as weak
        int letters = 0;
        for (int i = 0; i < lc.length(); i++) if (Character.isLetter(lc.charAt(i))) letters++;
        return letters < 5;
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
          // Give non-streaming enough room for 1–2 short sentences by default
          .append("\"num_predict\":").append(Math.max(160, maxTokens))
          .append(',')
          .append("\"options\":{")
          .append("\"temperature\":").append(String.format(java.util.Locale.ROOT, "%.3f", temperature)).append(',')
          .append("\"top_p\":0.9,")
          .append("\"top_k\":60,")
          .append("\"repeat_penalty\":1.12,")
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
          .append("\"top_p\":0.9,")
          .append("\"top_k\":60,")
          .append("\"repeat_penalty\":1.12")
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
        String nativeValue = NativeJson.getString(json, "response");
        if (nativeValue != null) return nativeValue.trim();
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
