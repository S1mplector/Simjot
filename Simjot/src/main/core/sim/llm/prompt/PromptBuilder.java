package main.core.sim.llm.prompt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import main.core.sim.persona.SimPersonality;

public final class PromptBuilder {
    private PromptBuilder() {}

    // Cache loaded prompts by key ("default" or persona key)
    private static final Map<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();
    private static final String DEFAULT_PROMPT_NAME = "sim_system_prompt.txt";
    private static final String PROMPT_DIR_ENV = "SIM_PROMPT_DIR"; // optional override

    public static String systemPrompt(SimPersonality.Type type) {
        // Try persona-specific file, then default file, then hardcoded fallback
        String key = type == null ? "default" : ("persona_" + type.name().toLowerCase(Locale.ROOT));
        return PROMPT_CACHE.computeIfAbsent(key, k -> {
            String fromFile = loadPromptForType(type);
            return fromFile != null ? fromFile : hardcodedFallback(type);
        });
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

    /**
     * Overload that additionally tags the invocation source (e.g., CALL_HEART, HOTKEY).
     * This preserves backward compatibility with existing call sites.
     */
    public static String systemPromptWithContext(
            SimPersonality.Type type,
            String moodLabel,
            Double moodValence,
            String recentInputPreview,
            String conversationSummary,
            String triggerReason,
            String invocationSource
    ) {
        String base = systemPromptWithContext(type, moodLabel, moodValence, recentInputPreview, conversationSummary, triggerReason);
        if (invocationSource == null || invocationSource.isBlank()) return base;
        // If base already equals the plain systemPrompt (no context), add header to ensure structure
        boolean hadContext = !base.equals(systemPrompt(type));
        StringBuilder sb = new StringBuilder(base.length() + 64);
        if (!hadContext) {
            sb.append(base).append("\n\nContext (for Sim only; do NOT reflect this meta to the user):");
        } else {
            sb.append(base);
        }
        sb.append("\n- InvocationSource: ").append(sanitizeInline(invocationSource.trim()));
        return sb.toString();
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

    // --- Prompt loading helpers ---
    private static String loadPromptForType(SimPersonality.Type type) {
        String personaSuffix = type == null ? null : type.name().toLowerCase(Locale.ROOT);
        // 1) Env override dir
        String envDir = System.getenv(PROMPT_DIR_ENV);
        if (envDir != null && !envDir.isBlank()) {
            String v = tryLoadFromDir(envDir, personaSuffix);
            if (v != null) return v;
        }
        // 2) Working dir resources/prompts
        String cwd = System.getProperty("user.dir", ".");
        String v2 = tryLoadFromDir(Paths.get(cwd, "resources", "prompts").toString(), personaSuffix);
        if (v2 != null) return v2;
        // 3) Classpath resources (e.g., src/main/resources/prompts in builds)
        String v3 = tryLoadFromClasspath("prompts/", personaSuffix);
        if (v3 != null) return v3;
        return null;
    }

    private static String tryLoadFromDir(String dir, String personaSuffix) {
        // Prefer persona-specific file first
        if (personaSuffix != null) {
            Path pf = Paths.get(dir, "sim_system_prompt_" + personaSuffix + ".txt");
            String s = readFileIfExists(pf);
            if (s != null) return s;
        }
        // Fallback to default file
        Path df = Paths.get(dir, DEFAULT_PROMPT_NAME);
        return readFileIfExists(df);
    }

    private static String tryLoadFromClasspath(String base, String personaSuffix) {
        ClassLoader cl = PromptBuilder.class.getClassLoader();
        if (personaSuffix != null) {
            String name = base + "sim_system_prompt_" + personaSuffix + ".txt";
            String s = readClasspathResource(cl, name);
            if (s != null) return s;
        }
        return readClasspathResource(cl, base + DEFAULT_PROMPT_NAME);
    }

    private static String readFileIfExists(Path p) {
        try {
            if (Files.exists(p) && Files.isRegularFile(p)) {
                byte[] bytes = Files.readAllBytes(p);
                String s = new String(bytes, StandardCharsets.UTF_8).trim();
                if (!s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String readClasspathResource(ClassLoader cl, String name) {
        try (InputStream in = cl.getResourceAsStream(name)) {
            if (in == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                String s = sb.toString().trim();
                return s.isBlank() ? null : s;
            }
        } catch (Throwable ignored) { return null; }
    }

    private static String hardcodedFallback(SimPersonality.Type type) {
        String tone;
        switch (type) {
            case GENTLE: tone = "You are Sim, a gentle, supportive journaling companion. Offer short, empathetic reflections and emotional support."; break;
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
}
