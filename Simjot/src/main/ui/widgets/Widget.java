package main.ui.widgets;

/**
 * Minimal contract for pluggable "widgets" that can be overlaid on panels.
 * A widget usually has its own repaint timer – callers simply enable/disable it.
 */
public interface Widget {
    /** Starts the widget's internal animation or timers */
    void start();

    /** Stops any running animation/timers and frees resources */
    void stop();

    /**
     * Convenience toggle.  Default implementation simply delegates to
     * start()/stop(). Sub-classes can override if they need custom behaviour.
     */
    default void setEnabled(boolean enabled) {
        if (enabled) start();
        else stop();
    }

    /** Whether the widget is currently active (animating/visible) */
    boolean isEnabled();

    /** Display name for menus/buttons. Default: class simple name without trailing "Widget". */
    default String getName() {
        String n = getClass().getSimpleName();
        return n.endsWith("Widget") ? n.substring(0, n.length() - 6) : n;
    }

    /** Icon identifier to use in menus. Default generic 'lines'. */
    default String getIconId() {
        return "lines";
    }
}