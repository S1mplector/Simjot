package main.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * DragController: reusable mouse-drag behavior for moving a target component.
 *
 * Features:
 * - Add one or more drag handles (components that receive mouse events).
 * - Constrain movement to the target's parent bounds.
 * - Optional cursor change while dragging.
 * - Drag-threshold tracking to allow click suppression after a drag.
 */
public class DragController {
    private final JComponent target;
    private final List<Component> handles = new ArrayList<>();

    private boolean constrainToParentBounds = true;
    private boolean setMoveCursorWhileDragging = true;
    private int dragThreshold = 3; // pixels (Manhattan distance)

    private boolean dragging = false;
    private Point pressPoint = null; // point in handle coordinates at press time
    private Point dragOffset = null; // point in target coordinates to anchor dragging
    private int movementSum = 0;
    private boolean suppressNextClick = false;

    private Cursor previousCursor = null;

    public DragController(JComponent target) {
        this.target = target;
    }

    public DragController setConstrainToParentBounds(boolean value) {
        this.constrainToParentBounds = value;
        return this;
    }

    public DragController setCursorOnDrag(boolean value) {
        this.setMoveCursorWhileDragging = value;
        return this;
    }

    public DragController setDragThreshold(int pixels) {
        this.dragThreshold = Math.max(0, pixels);
        return this;
    }

    public void addHandle(Component handle) {
        if (handle == null) return;
        MouseAdapter adapter = createHandler();
        handle.addMouseListener(adapter);
        handle.addMouseMotionListener(adapter);
        handles.add(handle);
    }

    public void clearHandles() {
        for (Component c : handles) {
            for (var l : c.getMouseListeners()) {
                if (l instanceof MouseAdapter && l.getClass().getEnclosingClass() == DragController.class) {
                    c.removeMouseListener(l);
                }
            }
            for (var l : c.getMouseMotionListeners()) {
                if (l instanceof MouseAdapter && l.getClass().getEnclosingClass() == DragController.class) {
                    c.removeMouseMotionListener(l);
                }
            }
        }
        handles.clear();
    }

    /**
     * Returns true if a drag movement above threshold happened since last click check, and resets the flag.
     */
    public boolean shouldSuppressClickAndReset() {
        boolean val = suppressNextClick;
        suppressNextClick = false;
        return val;
    }

    private MouseAdapter createHandler() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                dragging = true;
                pressPoint = e.getPoint();
                // compute offset from target origin using event source location
                Point srcInTarget = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
                dragOffset = srcInTarget;
                movementSum = 0;
                if (setMoveCursorWhileDragging) {
                    previousCursor = target.getCursor();
                    target.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging) {
                    dragging = false;
                    if (setMoveCursorWhileDragging && previousCursor != null) {
                        target.setCursor(previousCursor);
                        previousCursor = null;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging || dragOffset == null) return;

                // Track movement for click suppression
                movementSum += Math.abs(e.getX() - pressPoint.x) + Math.abs(e.getY() - pressPoint.y);
                if (movementSum > dragThreshold) {
                    suppressNextClick = true;
                }

                // Compute new location for target
                Point srcInTarget = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
                Point currentLocation = target.getLocation();
                Point newLocation = new Point(
                        currentLocation.x + srcInTarget.x - dragOffset.x,
                        currentLocation.y + srcInTarget.y - dragOffset.y
                );

                if (constrainToParentBounds) {
                    Container parent = target.getParent();
                    if (parent != null) {
                        Dimension parentSize = parent.getSize();
                        Dimension thisSize = target.getSize();
                        newLocation.x = Math.max(0, Math.min(newLocation.x, parentSize.width - thisSize.width));
                        newLocation.y = Math.max(0, Math.min(newLocation.y, parentSize.height - thisSize.height));
                    }
                }

                target.setBounds(newLocation.x, newLocation.y, target.getWidth(), target.getHeight());
            }
        };
    }
}
