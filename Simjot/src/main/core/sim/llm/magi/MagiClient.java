/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.sim.llm.magi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;
import main.infrastructure.io.NativeJson;

/**
 * Bridge client for running the local Python MAGI engine as a Sim LLM provider.
 */
public final class MagiClient implements SimLLMClient {
    private static final String DEFAULT_PYTHON = "python3";
    private static final String DEFAULT_MODEL = "gpt-5";

    private final String pythonCommand;
    private final String model;
    private final String openAiApiKey;
    private volatile String resolvedPythonCommand;

    public MagiClient(String pythonCommand, String model, String openAiApiKey) {
        String envPython = System.getenv("SIM_MAGI_PYTHON");
        String py = (pythonCommand == null || pythonCommand.isBlank()) ? envPython : pythonCommand;
        this.pythonCommand = (py == null || py.isBlank()) ? DEFAULT_PYTHON : py.trim();
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
    }

    @Override
    public SimLLMResponse generate(SimLLMRequest request, Duration timeout) throws Exception {
        if ((openAiApiKey == null || openAiApiKey.isBlank())
                && (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank())) {
            throw new IOException("MAGI requires a real OpenAI API key; mock mode is disabled.");
        }

        Path bridge = resolveBridgeScript();
        long timeoutMs = (timeout == null ? Duration.ofSeconds(25) : timeout).toMillis();
        if (timeoutMs < 1000L) timeoutMs = 1000L;

        String pythonToUse = resolvePythonCommand();
        ProcessBuilder pb = new ProcessBuilder(pythonToUse, bridge.toString());
        logInfo("Launching MAGI bridge python=" + pythonToUse + " model=" + model + " bridge=" + bridge);
        pb.redirectErrorStream(true);
        pb.directory(bridge.getParent().toFile());

        Map<String, String> env = pb.environment();
        env.putIfAbsent("PYTHONUTF8", "1");
        env.putIfAbsent("MAGI_FAST_MODE", "1");
        env.putIfAbsent("MAGI_MAX_DELIBERATION_ROUNDS", "1");
        env.putIfAbsent("MAGI_ENABLE_CROSS_EXAMINATION", "0");
        env.put("SIM_MAGI_MODEL", model);
        if (!openAiApiKey.isBlank()) {
            env.put("OPENAI_API_KEY", openAiApiKey);
        }

        Process process = pb.start();
        String payload = toBridgePayload(request, model, openAiApiKey);
        try (var stdin = process.getOutputStream()) {
            stdin.write(payload.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            logError("Magi bridge timed out after " + timeoutMs + "ms");
            throw new IOException("Magi bridge timed out after " + timeoutMs + "ms");
        }
        int exitCode = process.exitValue();

        String out;
        try (var stdout = process.getInputStream()) {
            out = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
        }

        String json = extractLastJsonLine(out);
        String bridgeLogs = extractBridgeLogs(out, json);
        if (exitCode != 0) {
            logError("Magi bridge exited with non-zero code " + exitCode + ".");
            logBridgeLogs(bridgeLogs);
        }
        if (json == null) {
            logError("Magi bridge returned non-JSON output.");
            logBridgeLogs(out);
            throw new IOException("Magi bridge returned non-JSON output: " + truncate(out, 240));
        }

        Boolean ok = NativeJson.getBoolean(json, "ok");
        if (!Boolean.TRUE.equals(ok)) {
            String error = NativeJson.getString(json, "error");
            if (error == null || error.isBlank()) error = "unknown MAGI bridge error";
            logError("Magi bridge returned error: " + error);
            logBridgeLogs(bridgeLogs);
            throw new IOException(error);
        }

        String magiMode = NativeJson.getString(json, "magi_mode");
        if (magiMode != null && magiMode.trim().equalsIgnoreCase("mock")) {
            String initError = NativeJson.getString(json, "magi_init_error");
            StringBuilder sb = new StringBuilder("MAGI initialized in mock mode; this is disabled.");
            if (initError != null && !initError.isBlank()) sb.append(" Init error: ").append(initError);
            logError(sb.toString());
            logBridgeLogs(bridgeLogs);
            throw new IOException(sb.toString());
        }

        String text = NativeJson.getString(json, "text");
        if (text == null) text = "";
        String consensus = NativeJson.getString(json, "consensus");
        String[] emotions = parseEmotions(json);
        String[] brainStatuses = parseBrainStatuses(json);
        return new SimLLMResponse(text, consensus, emotions, brainStatuses);
    }

    private String resolvePythonCommand() {
        String cached = resolvedPythonCommand;
        if (cached != null && !cached.isBlank()) return cached;

        String preferred = (pythonCommand == null || pythonCommand.isBlank()) ? DEFAULT_PYTHON : pythonCommand.trim();
        if (supportsOpenAi(preferred)) {
            resolvedPythonCommand = preferred;
            return preferred;
        }

        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, System.getenv("SIM_MAGI_PYTHON"));
        addCandidate(candidates, System.getenv("CONDA_PYTHON_EXE"));
        addCandidate(candidates, "/opt/anaconda3/bin/python3");
        addCandidate(candidates, "/opt/homebrew/bin/python3");
        addCandidate(candidates, "python3");
        addCandidate(candidates, "python");

        for (String candidate : candidates) {
            if (candidate.equals(preferred)) continue;
            if (!supportsOpenAi(candidate)) continue;
            resolvedPythonCommand = candidate;
            logWarn("Configured MAGI python '" + preferred + "' cannot import openai; using '" + candidate + "'.");
            return candidate;
        }

        // Keep the configured command so error logs still explain the missing module.
        resolvedPythonCommand = preferred;
        return preferred;
    }

    private static void addCandidate(List<String> out, String candidate) {
        if (candidate == null) return;
        String c = candidate.trim();
        if (c.isEmpty()) return;
        if (out.contains(c)) return;
        out.add(c);
    }

    private static boolean supportsOpenAi(String pythonBin) {
        if (pythonBin == null || pythonBin.isBlank()) return false;
        Process p = null;
        try {
            p = new ProcessBuilder(pythonBin, "-c", "import openai").start();
            boolean done = p.waitFor(3500L, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (p != null) {
                try (var in = p.getInputStream()) { in.readAllBytes(); } catch (Throwable ignored) {}
                try (var err = p.getErrorStream()) { err.readAllBytes(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String extractBridgeLogs(String output, String jsonLine) {
        if (output == null || output.isBlank()) return "";
        if (jsonLine == null || jsonLine.isBlank()) return output.trim();
        int idx = output.lastIndexOf(jsonLine);
        if (idx < 0) return output.trim();
        String before = output.substring(0, idx).trim();
        String after = output.substring(Math.min(output.length(), idx + jsonLine.length())).trim();
        if (before.isEmpty()) return after;
        if (after.isEmpty()) return before;
        return before + "\n" + after;
    }

    private static void logBridgeLogs(String logs) {
        if (logs == null || logs.isBlank()) return;
        System.err.println("[MagiClient][Bridge] " + truncate(logs, 4000));
    }

    private static void logError(String message) {
        System.err.println("[MagiClient][ERROR] " + (message == null ? "" : message));
    }

    private static void logInfo(String message) {
        System.err.println("[MagiClient][INFO] " + (message == null ? "" : message));
    }

    private static void logWarn(String message) {
        System.err.println("[MagiClient][WARN] " + (message == null ? "" : message));
    }

    private static Path resolveBridgeScript() throws IOException {
        String cwd = System.getProperty("user.dir", ".");
        Path[] candidates = new Path[] {
                Paths.get(cwd, "src/main/core/sim/supercomputer/python/sim_magi_bridge.py"),
                Paths.get(cwd, "Simjot/src/main/core/sim/supercomputer/python/sim_magi_bridge.py"),
                Paths.get("src/main/core/sim/supercomputer/python/sim_magi_bridge.py"),
                Paths.get("Simjot/src/main/core/sim/supercomputer/python/sim_magi_bridge.py")
        };
        for (Path candidate : candidates) {
            try {
                if (candidate != null && Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            } catch (Throwable ignored) {}
        }
        throw new IOException("sim_magi_bridge.py not found under Simjot sources");
    }

    private static String toBridgePayload(SimLLMRequest request, String model, String apiKey) {
        String sys = request == null ? "" : request.systemPrompt;
        String usr = request == null ? "" : request.userText;
        int max = request == null ? 220 : request.maxTokens;
        double temp = request == null ? 0.7 : request.temperature;

        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"system_prompt\":\"").append(escape(sys)).append("\",");
        sb.append("\"user_text\":\"").append(escape(usr)).append("\",");
        sb.append("\"max_tokens\":").append(Math.max(32, max)).append(',');
        sb.append("\"temperature\":").append(Math.max(0.0, Math.min(2.0, temp))).append(',');
        sb.append("\"model\":\"").append(escape(model)).append("\",");
        sb.append("\"openai_api_key\":\"").append(escape(apiKey == null ? "" : apiKey)).append("\"");
        sb.append('}');
        return sb.toString();
    }

    private static String extractLastJsonLine(String output) {
        if (output == null || output.isBlank()) return null;
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.startsWith("{") && line.endsWith("}")) {
                return line;
            }
        }
        int start = output.lastIndexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1).trim();
        }
        return null;
    }

    private static String[] parseEmotions(String json) {
        String arr = NativeJson.getArray(json, "emotions");
        if (arr == null || arr.isBlank()) return new String[0];
        List<String> values = NativeJson.getStringArray(arr);
        if (values == null || values.isEmpty()) return new String[0];
        List<String> cleaned = new ArrayList<>();
        for (String s : values) {
            if (s == null) continue;
            String t = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty()) continue;
            cleaned.add(t);
            if (cleaned.size() >= 3) break;
        }
        return cleaned.toArray(new String[0]);
    }

    private static String[] parseBrainStatuses(String json) {
        String obj = NativeJson.getObject(json, "brain_statuses");
        if (obj == null || obj.isBlank()) return new String[0];
        String[] out = new String[3];
        out[0] = normalizeBrainStatus(NativeJson.getString(obj, "melchior"));
        out[1] = normalizeBrainStatus(NativeJson.getString(obj, "balthasar"));
        out[2] = normalizeBrainStatus(NativeJson.getString(obj, "casper"));
        boolean any = false;
        for (String s : out) {
            if (s != null && !s.isBlank()) { any = true; break; }
        }
        return any ? out : new String[0];
    }

    private static String normalizeBrainStatus(String status) {
        if (status == null) return "";
        String s = status.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return "";
        if (s.contains("yes") || s.contains("approve") || s.contains("accept")) return "yes";
        if (s.contains("no") || s.contains("reject")) return "no";
        if (s.contains("conditional") || s.contains("condition")) return "conditional";
        if (s.contains("deadlock") || s.contains("stalemate")) return "deadlock";
        if (s.contains("inform") || s.contains("info")) return "info";
        return s;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
