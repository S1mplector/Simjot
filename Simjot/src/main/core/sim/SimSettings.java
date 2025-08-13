package main.core.sim;

import main.core.service.SettingsStore;

/**
 * Central settings for Sim. Backed by SettingsStore key-values to persist.
 */
public final class SimSettings {
    private static final String KEY_ENABLED = "sim.enabled";
    private static final String KEY_PERSONALITY = "sim.personality"; // gentle|neutral|proactive
    private static final String KEY_USE_LLM = "sim.use_llm";
    private static final String KEY_QUIET_HOURS = "sim.quiet_hours"; // e.g., "22:00-07:00"

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
        return store.getFlag(KEY_USE_LLM, false);
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
}
