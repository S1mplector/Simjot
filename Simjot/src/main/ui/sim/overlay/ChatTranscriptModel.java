package main.ui.sim.overlay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal chat transcript model decoupled from SimOverlay painting.
 * Holds immutable entries and notifies listeners on changes.
 */
public final class ChatTranscriptModel {
    public enum Role { USER, ASSISTANT, SYSTEM }

    public static final class Entry {
        public final Role role;
        public final String text;
        public final long ts;
        public Entry(Role role, String text) {
            this.role = role == null ? Role.SYSTEM : role;
            this.text = text == null ? "" : text;
            this.ts = Instant.now().toEpochMilli();
        }
    }

    public interface Listener {
        void onTranscriptChanged();
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // For streaming assistant turns
    private int streamingAssistantIndex = -1;

    public void addListener(Listener l) { if (l != null) listeners.add(l); }
    public void removeListener(Listener l) { if (l != null) listeners.remove(l); }

    public List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    public void clear() {
        entries.clear();
        streamingAssistantIndex = -1;
        notifyChanged();
    }

    public void beginAssistantTurn() {
        entries.add(new Entry(Role.ASSISTANT, ""));
        streamingAssistantIndex = entries.size() - 1;
        notifyChanged();
    }

    public void appendAssistantTokens(String tokens) {
        if (tokens == null || tokens.isBlank()) return;
        if (streamingAssistantIndex < 0 || streamingAssistantIndex >= entries.size()) {
            // If a begin was missed, start implicitly
            beginAssistantTurn();
        }
        Entry prev = entries.get(streamingAssistantIndex);
        Entry updated = new Entry(prev.role, prev.text + tokens);
        // replace last
        entries.set(streamingAssistantIndex, updated);
        notifyChanged();
    }

    public void endAssistantTurn() {
        streamingAssistantIndex = -1;
        notifyChanged();
    }

    public void appendUser(String text) {
        entries.add(new Entry(Role.USER, text));
        notifyChanged();
    }

    public void appendSystem(String text) {
        entries.add(new Entry(Role.SYSTEM, text));
        notifyChanged();
    }

    private void notifyChanged() {
        for (Listener l : listeners) {
            try { l.onTranscriptChanged(); } catch (Throwable ignored) {}
        }
    }
}
