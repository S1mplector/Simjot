package main.core.sim.engine;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import main.core.sim.api.SimEventBus;
import main.core.sim.llm.api.SimLLMClient;
import main.core.sim.llm.api.SimLLMRequest;
import main.core.sim.llm.api.SimLLMResponse;
import main.core.sim.llm.ollama.OllamaClient;

/**
 * Minimal turn manager that supports pseudo-streaming and cancellation.
 * If the LLM is OllamaClient, it will try generateStream; otherwise falls back to generate.
 */
public final class TurnManager {
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sim-Turn");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger emptyNoTokenStreak = new AtomicInteger(0);
    private final AtomicBoolean cancelling = new AtomicBoolean(false);
    private volatile Future<?> inFlight;

    public void cancelIfUserTyping() {
        cancelling.set(true);
        Future<?> f = inFlight;
        if (f != null) {
            f.cancel(true);
        }
    }

    public void maybeSpeakProactively(SimLLMClient llm, String system, String user) {
        cancelIfUserTyping(); // cancel any ongoing stream before starting a new one
        cancelling.set(false);
        inFlight = exec.submit(() -> doStreamOrFallback(llm, system, user, null, Duration.ofSeconds(20), true));
    }

    public void maybeSpeakProactively(SimLLMClient llm, String system, String user, String preface) {
        cancelIfUserTyping();
        cancelling.set(false);
        inFlight = exec.submit(() -> doStreamOrFallback(llm, system, user, preface, Duration.ofSeconds(20), true));
    }

    // Overload allowing callers to disable generic fallback emission
    public void maybeSpeakProactively(SimLLMClient llm, String system, String user, boolean allowFallback) {
        cancelIfUserTyping();
        cancelling.set(false);
        inFlight = exec.submit(() -> doStreamOrFallback(llm, system, user, null, Duration.ofSeconds(20), allowFallback));
    }

    public void maybeSpeakProactively(SimLLMClient llm, String system, String user, String preface, boolean allowFallback) {
        cancelIfUserTyping();
        cancelling.set(false);
        inFlight = exec.submit(() -> doStreamOrFallback(llm, system, user, preface, Duration.ofSeconds(20), allowFallback));
    }

    public int getEmptyNoTokenStreak() {
        return emptyNoTokenStreak.get();
    }

    private void doStreamOrFallback(SimLLMClient llm, String system, String user, String preface, Duration timeout, boolean allowFallback) {
        SimEventBus.get().emitSpeakStart();
        // Emit contextual preface immediately so overlay reflects the trigger reason
        try {
            if (preface != null && !preface.isBlank()) {
                SimEventBus.get().emitSpeak(preface);
            }
        } catch (Throwable ignored) {}
        final boolean[] hadToken = new boolean[] { false };
        final boolean[] cancelled = new boolean[] { false };
        try {
            Consumer<String> onToken = tok -> {
                if (tok == null || tok.isBlank()) return;
                hadToken[0] = true;
                SimEventBus.get().emitSpeak(tok);
            };
            Runnable onComplete = () -> System.out.println("[TurnManager] stream complete");
            Consumer<Throwable> onError = (e) -> {
                if (e instanceof InterruptedException || (e != null && "Interrupted".equalsIgnoreCase(e.getMessage()))) {
                    cancelled[0] = true;
                    System.out.println("[TurnManager] stream cancelled by typing");
                } else {
                    System.out.println("[TurnManager] stream error: " + e);
                }
            };

            if (llm instanceof OllamaClient) {
                ((OllamaClient) llm).generateStream(
                        new SimLLMRequest(system, user, 180, 0.7),
                        timeout,
                        onToken,
                        onComplete,
                        onError,
                        () -> cancelling.get()
                );
                return;
            }
            // Fallback: non-streaming, then emit once
            SimLLMResponse resp = llm.generate(new SimLLMRequest(system, user, 180, 0.7), timeout);
            if (resp != null && resp.text != null && !resp.text.isBlank()) {
                onToken.accept(resp.text);
                onComplete.run();
            }
            // If still no content, try a very short micro-fallback with a specialized prompt
            if (!hadToken[0] && !cancelled[0] && allowFallback) {
                String microSystem = "You are a supportive assistant. Respond with one empathetic 15–25 word nudge tailored to the user's recent thoughts and situation. Be concise, friendly, and actionable.";
                String microUser = user + (preface != null && !preface.isBlank() ? ("\nTrigger: " + preface) : "");
                try {
                    SimLLMResponse micro = llm.generate(new SimLLMRequest(microSystem, microUser, 60, 0.7), Duration.ofSeconds(1));
                    if (micro != null && micro.text != null && !micro.text.isBlank()) {
                        onToken.accept(micro.text);
                        onComplete.run();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("[TurnManager] doStreamOrFallback failed: " + e);
        } finally {
            // If no tokens were produced and it wasn't cancelled, emit a brief friendly fallback
            try {
                if (!hadToken[0] && !cancelled[0] && allowFallback) {
                    // Increment empty-stream streak and emit generic fallback only if no preface was shown
                    emptyNoTokenStreak.incrementAndGet();
                    if (preface == null || preface.isBlank()) {
                        SimEventBus.get().emitSpeak("I’m here if you want a quick suggestion or summary.");
                    }
                } else {
                    // Reset streak if we had content or were cancelled
                    emptyNoTokenStreak.set(0);
                }
            } catch (Throwable ignored) {}
            try { SimEventBus.get().emitSpeakEnd(); } catch (Throwable ignored) {}
        }
    }
}
