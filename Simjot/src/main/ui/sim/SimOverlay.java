package main.ui.sim;

import javax.swing.*;
import java.awt.*;
import main.core.sim.SimEventBus;

/**
 * Minimal overlay for Sim. Added to the JFrame layered pane.
 */
public class SimOverlay extends JComponent implements SimEventBus.Listener {
    private String message = "Hi, I’m Sim.";

    public SimOverlay() {
        setOpaque(false);
        setVisible(true);
        // Listen for speak events
        try { SimEventBus.get().addListener(this); } catch (Throwable ignored) {}
    }

    public void showMessage(String msg) {
        this.message = msg == null ? "" : msg;
        repaint();
    }

    @Override
    public void onSpeak(String message) {
        showMessage(message);
    }

    /** Unsubscribe from event bus and hide overlay. */
    public void disposeOverlay() {
        try { SimEventBus.get().removeListener(this); } catch (Throwable ignored) {}
        setVisible(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(280, 120);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        // Bubble background
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
        g2.setColor(new Color(0, 0, 0, 60));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
        // Text
        g2.setColor(new Color(20, 20, 20));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        int x = 14, y = 24;
        for (String line : wrapText(message, w - 28, g2)) {
            g2.drawString(line, x, y);
            y += 18;
        }
        g2.dispose();
    }

    private java.util.List<String> wrapText(String text, int maxWidth, Graphics2D g2) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            String candidate = current.length() == 0 ? w : current + " " + w;
            if (g2.getFontMetrics().stringWidth(candidate) > maxWidth) {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(w);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }
}
