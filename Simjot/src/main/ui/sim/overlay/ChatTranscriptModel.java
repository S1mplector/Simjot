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
    // If true, an assistant turn has started but no content yet; avoid blank bubble
    private boolean assistantPending = false;

    public void addListener(Listener l) { if (l != null) listeners.add(l); }
    public void removeListener(Listener l) { if (l != null) listeners.remove(l); }

    public List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    public void clear() {
        entries.clear();
        streamingAssistantIndex = -1;
        assistantPending = false;
        notifyChanged();
    }

    public void beginAssistantTurn() {
        // Defer creating an entry until we receive first non-blank tokens
        assistantPending = true;
        streamingAssistantIndex = -1;
        // do not notify; nothing visible yet
    }

    public void appendAssistantTokens(String tokens) {
        if (tokens == null || tokens.isBlank()) return;
        if (assistantPending || streamingAssistantIndex < 0 || streamingAssistantIndex >= entries.size()) {
            // First content for this assistant turn: create entry now
            entries.add(new Entry(Role.ASSISTANT, tokens));
            streamingAssistantIndex = entries.size() - 1;
            assistantPending = false;
            notifyChanged();
            return;
        }
        Entry prev = entries.get(streamingAssistantIndex);
        Entry updated = new Entry(prev.role, prev.text + tokens);
        // replace last
        entries.set(streamingAssistantIndex, updated);
        notifyChanged();
    }

    public void endAssistantTurn() {
        // If turn ended without any tokens, just clear pending state
        assistantPending = false;
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
