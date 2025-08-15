package main.ui.components.indicators;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Minimal, reusable save state indicator.
 * Shows a subtle checkmark and "Saved • <time>" after saves,
 * and "Autosaving…" while autosave is in progress.
 */
public class SaveIndicatorPanel extends JPanel {
    private final JLabel iconLabel = new JLabel("✓");
    private final JLabel textLabel = new JLabel(" ");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a");

    public SaveIndicatorPanel() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        iconLabel.setForeground(new Color(0, 128, 0)); // subtle green
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD));
        iconLabel.setVisible(false);
        textLabel.setForeground(new Color(100, 100, 100));
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(iconLabel);
        add(textLabel);
    }

    public void setSaving() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setVisible(false);
            textLabel.setForeground(new Color(100, 100, 100));
            textLabel.setText("Autosaving…");
        });
    }

    public void setSaved(Date when) {
        if (when == null) when = new Date();
        final String msg = "Saved • " + timeFmt.format(when);
        SwingUtilities.invokeLater(() -> {
            iconLabel.setVisible(true);
            textLabel.setForeground(new Color(100, 100, 100));
            textLabel.setText(msg);
        });
    }

    public void setSavedFromTimestamp(long lastModified) {
        setSaved(lastModified > 0 ? new Date(lastModified) : new Date());
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setVisible(false);
            textLabel.setForeground(new Color(100, 100, 100));
            textLabel.setText(" ");
        });
    }

    public void setError(String message) {
        final String msg = (message == null || message.isBlank()) ? "Error saving" : message;
        SwingUtilities.invokeLater(() -> {
            iconLabel.setVisible(false);
            textLabel.setForeground(new Color(160, 30, 30));
            textLabel.setText(msg);
        });
    }
}
