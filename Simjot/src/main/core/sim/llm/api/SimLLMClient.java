package main.core.sim.llm.api;

import java.time.Duration;

public interface SimLLMClient {
    SimLLMResponse generate(SimLLMRequest request, Duration timeout) throws Exception;
}
