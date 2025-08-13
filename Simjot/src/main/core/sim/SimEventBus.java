package main.core.sim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal event bus for Sim signals.
 */
public final class SimEventBus {
    public interface Listener {
        default void onTyping(String latestText) {}
        default void onCardSwitched(String cardId) {}
        default void onMoodChanged(double value) {}
        default void onSpeak(String message) {}
    }

    private static SimEventBus INSTANCE;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private SimEventBus() {}

    public static SimEventBus get() {
        if (INSTANCE == null) INSTANCE = new SimEventBus();
        return INSTANCE;
    }

    public void addListener(Listener l){ if(l!=null) listeners.add(l); }
    public void removeListener(Listener l){ if(l!=null) listeners.remove(l); }

    // Emitters
    public void emitTyping(String latestText){ for(var l:listeners) l.onTyping(latestText); }
    public void emitCardSwitched(String cardId){ for(var l:listeners) l.onCardSwitched(cardId); }
    public void emitMoodChanged(double value){ for(var l:listeners) l.onMoodChanged(value); }
    public void emitSpeak(String message){ for(var l:listeners) l.onSpeak(message); }
}
