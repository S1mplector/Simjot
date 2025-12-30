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

package main.core.sim.llm.api;

import java.util.function.Consumer;

/**
 * High-level service to fetch optional Sim augmentation for poetry suggestions.
 * Implementations may call remote LLMs or local models. They should be resilient
 * (timeouts, fail-silent) and invoke the callback on the Swing EDT if they mutate UI.
 */
public interface SimSuggestionService {
    /**
     * Queries Sim asynchronously. Implementations should return promptly and perform work
     * in background. When finished, they should invoke the callback with either a non-empty
     * suggestion text to append, or null to indicate no output.
     */
    void queryAsync(String word, String rhymeKey, int syllables, String fullText, Consumer<String> onComplete);
}
