package main.core.sim.prefs;

import main.core.service.SettingsStore;

/**
 * Central settings for Sim. Backed by SettingsStore key-values to persist.
 */
public final class SimSettings {
    private static final String KEY_ENABLED = "sim.enabled";
    private static final String KEY_PERSONALITY = "sim.personality"; // gentle|neutral|proactive
    private static final String KEY_USE_LLM = "sim.use_llm";
    private static final String KEY_QUIET_HOURS = "sim.quiet_hours"; // e.g., "22:00-07:00"
    private static final String KEY_NUDGE_MIN = "sim.nudge_minutes"; // integer minutes between checks
    private static final String KEY_OLLAMA_ENDPOINT = "sim.ollama.endpoint"; // e.g., http://localhost:11434
    private static final String KEY_OLLAMA_MODEL = "sim.ollama.model"; // e.g., llama3:instruct

    private static SimSettings INSTANCE;

    private final SettingsStore store;

    private SimSettings(SettingsStore store) {
        this.store = store;
    }

    public static SimSettings get() {
        if (INSTANCE == null) {
            INSTANCE = new SimSettings(SettingsStore.get());
        }
        return INSTANCE;
    }

    public boolean isEnabled() {
        return store.getFlag(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        store.setFlag(KEY_ENABLED, enabled);
    }

    public String getPersonality() {
        return store.getValue(KEY_PERSONALITY, "gentle");
    }

    public void setPersonality(String value) {
        store.setValue(KEY_PERSONALITY, value == null ? "gentle" : value);
    }

    public boolean isLlmEnabled() {
        // Default to enabled so Sim is ready to use Ollama when available
        return store.getFlag(KEY_USE_LLM, true);
    }

    public void setLlmEnabled(boolean value) {
        store.setFlag(KEY_USE_LLM, value);
    }

    public String getQuietHours() {
        return store.getValue(KEY_QUIET_HOURS, "");
    }

    public void setQuietHours(String hours) {
        store.setValue(KEY_QUIET_HOURS, hours == null ? "" : hours);
    }

    // --- Nudge interval (minutes) ---
    public int getNudgeIntervalMinutes() {
        try {
            int val = Integer.parseInt(store.getValue(KEY_NUDGE_MIN, "30"));
            return Math.max(5, Math.min(120, val)); // clamp 5..120
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public void setNudgeIntervalMinutes(int minutes) {
        int v = Math.max(5, Math.min(120, minutes));
        store.setValue(KEY_NUDGE_MIN, String.valueOf(v));
    }

    // --- Ollama settings (for next step integration) ---
    public String getOllamaEndpoint() {
        return store.getValue(KEY_OLLAMA_ENDPOINT, "http://localhost:11434");
    }

    public void setOllamaEndpoint(String endpoint) {
        store.setValue(KEY_OLLAMA_ENDPOINT, endpoint == null ? "http://localhost:11434" : endpoint);
    }

    public String getOllamaModel() {
        return store.getValue(KEY_OLLAMA_MODEL, "deepseek-r1:1.5b");
    }

    public void setOllamaModel(String model) {
        store.setValue(KEY_OLLAMA_MODEL, model == null ? "deepseek-r1:1.5b" : model);
    }
}
