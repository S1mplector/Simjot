package main.ui.widgets;

import java.util.LinkedHashMap;
import java.util.Map;
import main.ui.JournalApp;

/**
 * Central registry/manager for Widgets.
 * Keeps insertion order and offers simple registration and retrieval APIs.
 */
public class WidgetManager {
    private final Map<String, Widget> widgets = new LinkedHashMap<>();

    public WidgetManager() {}

    /** Initialize the default widgets shipped with the app. */
    public void initializeDefault(JournalApp app) {
        // Simple stub for Breathing to participate in the enable/disable flow
        widgets.put("Breathing", new Widget() {
            private boolean enabled = false;
            @Override public void start() { enabled = true; }
            @Override public void stop() { enabled = false; }
            @Override public boolean isEnabled() { return enabled; }
        });

        widgets.put("Pomodoro", new PomodoroWidget(app));
        widgets.put("Idea Sticky", new IdeaStickyWidget(app));
    }

    public Map<String, Widget> getAll() { return widgets; }

    public void register(String name, Widget widget) { widgets.put(name, widget); }

    public Widget get(String name) { return widgets.get(name); }
}
