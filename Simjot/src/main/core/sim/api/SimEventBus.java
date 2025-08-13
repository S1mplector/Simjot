package main.core.sim.api;

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
        default void onSpeakStart() {}
        default void onSpeakEnd() {}
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
    public void emitTyping(String latestText){
        System.out.println("[SimBus] typing len=" + (latestText==null?0:latestText.length()) +
                " preview=\"" + preview(latestText) + "\"");
        for(var l:listeners) l.onTyping(latestText);
    }
    public void emitCardSwitched(String cardId){
        System.out.println("[SimBus] cardSwitched id=" + String.valueOf(cardId));
        for(var l:listeners) l.onCardSwitched(cardId);
    }
    public void emitMoodChanged(double value){
        System.out.println("[SimBus] moodChanged value=" + value);
        for(var l:listeners) l.onMoodChanged(value);
    }
    public void emitSpeak(String message){
        System.out.println("[SimBus] speak preview=\"" + preview(message) + "\"");
        for(var l:listeners) l.onSpeak(message);
    }
    public void emitSpeakStart(){
        System.out.println("[SimBus] speakStart");
        for (var l: listeners) l.onSpeakStart();
    }
    public void emitSpeakEnd(){
        System.out.println("[SimBus] speakEnd");
        for (var l: listeners) l.onSpeakEnd();
    }

    private static String preview(String s){
        if (s == null) return "";
        String flat = s.replace('\n',' ').trim();
        return flat.length() > 120 ? flat.substring(0,120) + "..." : flat;
    }
}
