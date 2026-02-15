/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal event bus for Sim signals.
 */
public final class SimEventBus {
    /**
     * Controls whether verbose logging is enabled.
     * Disabled by default to reduce console noise and improve performance.
     */
    private static volatile boolean verboseLogging = false;
    
    /** Enable verbose logging for all SimBus events */
    public static void enableVerboseLogging() { verboseLogging = true; }
    /** Disable verbose logging (default) */
    public static void disableVerboseLogging() { verboseLogging = false; }
    /** Check if verbose logging is enabled */
    public static boolean isVerboseLogging() { return verboseLogging; }
    
    public enum InvocationSource { CALL_HEART, HOTKEY, TRAY, MENU, OTHER }
    public interface Listener {
        default void onTyping(String latestText) {}
        default void onCardSwitched(String cardId) {}
        default void onMoodChanged(double value) {}
        default void onSpeak(String message) {}
        default void onSpeakStart() {}
        default void onSpeakEnd() {}
        // User requested reflective guidance on current editor content
        default void onGuidanceRequested(String text) {}
        // Sim produced guidance text to be inserted into editor
        default void onGuidanceProduced(String text) {}
        // Request a fresh daily reflection prompt for local date key (yyyy-MM-dd)
        default void onDailyPromptRequested(String dateKey) {}
        // Daily prompt produced by Sim
        default void onDailyPromptProduced(String dateKey, String label, String prompt) {}
        // Request Sim to generate a journal template from current text and optional notebook context
        default void onTemplateGenerationRequested(String text, String notebookName) {}
        // Sim generated a template draft payload
        default void onTemplateGenerated(String notebookName, String name, String description, String[] questions) {}
        // User clicked a CTA to chat more; Phase 1: request a single follow-up turn
        default void onChatFollowupRequested() {}
        // Phase 2 chat events
        default void onChatMessage(String userText) {}
        default void onChatEnded() {}
        // App-level: request Sim overlay to quit/close gracefully
        default void onQuitRequested() {}
        // Real-time per-entry emotion tag (e.g., "sad", 0..100). entryId can be null if not applicable
        default void onEmotionTagged(String entryId, String emotion, double intensity) {}
        // User explicitly invoked Sim (e.g., heart, hotkey). Useful for tagging prompt context
        default void onUserInvoked(InvocationSource source) {}
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
        if (verboseLogging) System.out.println("[SimBus] typing len=" + (latestText==null?0:latestText.length()) +
                " preview=\"" + preview(latestText) + "\"");
        for(var l:listeners) l.onTyping(latestText);
    }
    public void emitCardSwitched(String cardId){
        if (verboseLogging) System.out.println("[SimBus] cardSwitched id=" + String.valueOf(cardId));
        for(var l:listeners) l.onCardSwitched(cardId);
    }
    public void emitMoodChanged(double value){
        if (verboseLogging) System.out.println("[SimBus] moodChanged value=" + value);
        for(var l:listeners) l.onMoodChanged(value);
    }
    public void emitSpeak(String message){
        if (verboseLogging) System.out.println("[SimBus] speak preview=\"" + preview(message) + "\"");
        for(var l:listeners) l.onSpeak(message);
    }
    public void emitSpeakStart(){
        if (verboseLogging) System.out.println("[SimBus] speakStart");
        for (var l: listeners) l.onSpeakStart();
    }
    public void emitSpeakEnd(){
        if (verboseLogging) System.out.println("[SimBus] speakEnd");
        for (var l: listeners) l.onSpeakEnd();
    }

    // Phase 1 chat: user asked to continue the conversation with one more reply
    public void emitChatFollowupRequested(){
        if (verboseLogging) System.out.println("[SimBus] chatFollowupRequested");
        for (var l: listeners) l.onChatFollowupRequested();
    }

    // Phase 2 chat: user sent a chat message
    public void emitChatMessage(String userText){
        if (verboseLogging) System.out.println("[SimBus] chatMessage len=" + (userText==null?0:userText.length()));
        for (var l: listeners) l.onChatMessage(userText);
    }

    // Phase 2 chat: user ended chat session
    public void emitChatEnded(){
        if (verboseLogging) System.out.println("[SimBus] chatEnded");
        for (var l: listeners) l.onChatEnded();
    }

    // Reflective guidance on editor content
    public void emitGuidanceRequested(String text){
        if (verboseLogging) System.out.println("[SimBus] guidanceRequested len=" + (text==null?0:text.length()));
        for (var l: listeners) l.onGuidanceRequested(text);
    }

    public void emitGuidanceProduced(String text){
        if (verboseLogging) System.out.println("[SimBus] guidanceProduced len=" + (text==null?0:text.length()));
        for (var l: listeners) l.onGuidanceProduced(text);
    }

    public void emitDailyPromptRequested(String dateKey){
        if (verboseLogging) System.out.println("[SimBus] dailyPromptRequested date=" + String.valueOf(dateKey));
        for (var l: listeners) l.onDailyPromptRequested(dateKey);
    }

    public void emitDailyPromptProduced(String dateKey, String label, String prompt){
        if (verboseLogging) {
            int len = prompt == null ? 0 : prompt.length();
            System.out.println("[SimBus] dailyPromptProduced date=" + String.valueOf(dateKey)
                    + " label=" + String.valueOf(label) + " len=" + len);
        }
        for (var l: listeners) l.onDailyPromptProduced(dateKey, label, prompt);
    }

    public void emitTemplateGenerationRequested(String text, String notebookName){
        if (verboseLogging) {
            int len = text == null ? 0 : text.length();
            System.out.println("[SimBus] templateGenerationRequested len=" + len + " notebook=" + String.valueOf(notebookName));
        }
        for (var l: listeners) l.onTemplateGenerationRequested(text, notebookName);
    }

    public void emitTemplateGenerated(String notebookName, String name, String description, String[] questions){
        if (verboseLogging) {
            int q = questions == null ? 0 : questions.length;
            System.out.println("[SimBus] templateGenerated notebook=" + String.valueOf(notebookName)
                    + " name=" + String.valueOf(name) + " q=" + q);
        }
        for (var l: listeners) l.onTemplateGenerated(notebookName, name, description, questions);
    }

    // App-level: request all listeners to quit/close gracefully
    public void emitQuitRequested(){
        if (verboseLogging) System.out.println("[SimBus] quitRequested");
        for (var l: listeners) l.onQuitRequested();
    }

    // Real-time per-entry emotion tag emitter
    public void emitEmotionTagged(String entryId, String emotion, double intensity){
        if (verboseLogging) System.out.println("[SimBus] emotionTagged entry=" + String.valueOf(entryId) +
                " emotion=" + String.valueOf(emotion) + " intensity=" + intensity);
        for (var l: listeners) l.onEmotionTagged(entryId, emotion, intensity);
    }

    // User explicit invocation emitter
    public void emitUserInvoked(InvocationSource source){
        if (verboseLogging) System.out.println("[SimBus] userInvoked source=" + String.valueOf(source));
        for (var l: listeners) l.onUserInvoked(source);
    }

    private static String preview(String s){
        if (s == null) return "";
        String flat = s.replace('\n',' ').trim();
        return flat.length() > 120 ? flat.substring(0,120) + "..." : flat;
    }
}
