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

package main.core.sim.state;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks lightweight user signals for proactive nudging.
 * - typing cadence: last text, last typing time
 * - shrink events: times text length dropped (proxy for backspaces/edits)
 */
public final class UserState {
    private volatile String lastText = "";
    private final AtomicLong lastTypingAtMs = new AtomicLong(0L);
    private volatile int shrinkEvents = 0;
    private volatile int totalEdits = 0;

    public void onTyping(String text) {
        long now = System.currentTimeMillis();
        lastTypingAtMs.set(now);
        if (text == null) text = "";
        int newLen = text.length();
        int oldLen = lastText == null ? 0 : lastText.length();
        if (newLen < oldLen) {
            shrinkEvents++;
        }
        totalEdits++;
        lastText = text;
    }

    public long millisSinceLastTyping() {
        long t = lastTypingAtMs.get();
        if (t == 0L) return Long.MAX_VALUE;
        return Math.max(0L, System.currentTimeMillis() - t);
    }

    public String getLastText() { return lastText; }

    public int getShrinkEvents() { return shrinkEvents; }

    public int getTotalEdits() { return totalEdits; }

    public void resetShrinkCounter() { shrinkEvents = 0; }
}
