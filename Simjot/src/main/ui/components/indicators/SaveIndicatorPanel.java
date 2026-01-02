/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

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
    private static final Color ICON_ON = new Color(0, 128, 0);
    private static final Color ICON_OFF = new Color(0, 0, 0, 0);
    private static final Color TEXT_COLOR = new Color(100, 100, 100);
    private static final Color ERROR_COLOR = new Color(160, 30, 30);
    private static final String SAVED_TEMPLATE = "Saved \u2022 00:00";
    private static final String SAVING_TEXT = "Autosaving\u2026";

    private final JLabel iconLabel = new JLabel("✓");
    private final JLabel textLabel = new JLabel(" ");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private final Dimension stableTextSize;

    public SaveIndicatorPanel() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        iconLabel.setForeground(ICON_OFF); // keep width stable; toggle alpha
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD));
        Font textFont = new Font("SansSerif", Font.PLAIN, 12);
        textLabel.setForeground(TEXT_COLOR);
        textLabel.setFont(textFont);
        FontMetrics fm = textLabel.getFontMetrics(textFont);
        int w = Math.max(fm.stringWidth(SAVED_TEMPLATE), fm.stringWidth(SAVING_TEXT));
        int h = fm.getHeight();
        stableTextSize = new Dimension(w, h);
        applyStableTextSize();
        add(iconLabel);
        add(textLabel);
    }

    public void setSaving() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setForeground(ICON_OFF);
            textLabel.setForeground(TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(SAVING_TEXT);
        });
    }

    public void setSaved(Date when) {
        if (when == null) when = new Date();
        final String msg = "Saved • " + timeFmt.format(when);
        SwingUtilities.invokeLater(() -> {
            iconLabel.setForeground(ICON_ON);
            textLabel.setForeground(TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(msg);
        });
    }

    public void setSavedFromTimestamp(long lastModified) {
        setSaved(lastModified > 0 ? new Date(lastModified) : new Date());
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setForeground(ICON_OFF);
            textLabel.setForeground(TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(" ");
        });
    }

    public void setError(String message) {
        final String msg = (message == null || message.isBlank()) ? "Error saving" : message;
        SwingUtilities.invokeLater(() -> {
            iconLabel.setForeground(ICON_OFF);
            textLabel.setForeground(ERROR_COLOR);
            clearStableTextSize();
            textLabel.setText(msg);
        });
    }

    private void applyStableTextSize() {
        textLabel.setPreferredSize(stableTextSize);
        textLabel.setMinimumSize(stableTextSize);
    }

    private void clearStableTextSize() {
        textLabel.setPreferredSize(null);
        textLabel.setMinimumSize(null);
    }
}
