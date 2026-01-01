/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.llm.api;

import java.time.Duration;

public interface SimLLMClient {
    SimLLMResponse generate(SimLLMRequest request, Duration timeout) throws Exception;
}
