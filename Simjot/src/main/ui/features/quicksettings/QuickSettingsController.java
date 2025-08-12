package main.ui.features.quicksettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Entry point for the Quick Settings feature. Encapsulates all behavior and UI.
 * Attach via install(layeredPane, host) without modifying existing UI structure.
 */
public class QuickSettingsController {

    /**
     * Minimal host API the controller needs from the main menu.
     * Implemented by the caller via a tiny adapter or the panel itself.
     */
    public interface HostApi {
        // The Swing window to anchor popups; usually the layered pane's window
        Window getWindow();
        // Show/hide the widgets panel utility
        void setWidgetsPanelVisible(boolean visible);
        // Query current visibility
        boolean isWidgetsPanelVisible();
        // List available widget names
        java.util.List<String> getWidgetNames();
        // Query if a widget is enabled
        boolean isWidgetEnabled(String name);
        // Enable/disable a widget by name
        void setWidgetEnabled(String name, boolean enabled);
    }

    private final JLayeredPane layeredPane;
    private final HostApi host;

    private QuickSettingsOverlay currentOverlay;

    private QuickSettingsController(JLayeredPane layeredPane, HostApi host) {
        this.layeredPane = layeredPane;
        this.host = host;
    }

    public static QuickSettingsController install(JLayeredPane layeredPane, HostApi host) {
        QuickSettingsController c = new QuickSettingsController(layeredPane, host);
        c.hookMouse();
        return c;
    }

    private void hookMouse() {
        // Fallback: direct listener (works when clicks hit the layeredPane itself)
        layeredPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    triggerOverlay(e.getPoint());
                }
            }
        });

        // Global listener: capture middle-clicks even when child components consume them
        final AWTEventListener global = event -> {
            if (!(event instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) event;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;
            if (me.getButton() != MouseEvent.BUTTON2) return;

            // Only react if the event occurred within our layeredPane hierarchy
            Component src = me.getComponent();
            if (src == null) return;
            if (!SwingUtilities.isDescendingFrom(src, layeredPane)) return;

            // Convert point to layeredPane coordinates
            Point p = SwingUtilities.convertPoint(src, me.getPoint(), layeredPane);
            if (p.x < 0 || p.y < 0 || p.x > layeredPane.getWidth() || p.y > layeredPane.getHeight()) return;

            triggerOverlay(p);
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(global, AWTEvent.MOUSE_EVENT_MASK);

        // Ensure we remove the global listener when this pane is removed/disposed
        layeredPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                if (!layeredPane.isDisplayable()) {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(global);
                }
            }
        });
    }

    private void triggerOverlay(Point p) {
        if (currentOverlay != null) {
            layeredPane.remove(currentOverlay);
            currentOverlay = null;
            layeredPane.revalidate();
            layeredPane.repaint();
        }

        List<QuickSettingsCategory> categories = QuickSettingsPresets.defaultCategories(host);
        QuickSettingsOverlay overlay = new QuickSettingsOverlay(categories, p, () -> {
            // Close callback uses currentOverlay to avoid capturing uninitialized local
            if (currentOverlay != null) {
                layeredPane.remove(currentOverlay);
                layeredPane.revalidate();
                layeredPane.repaint();
                currentOverlay = null;
            }
        });

        overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        currentOverlay = overlay;
        layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        layeredPane.moveToFront(overlay);
        overlay.start();
    }
}
