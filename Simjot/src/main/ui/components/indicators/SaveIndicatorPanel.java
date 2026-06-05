/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.indicators;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Minimal, reusable save state indicator.
 * Shows a small status glyph and timestamp after saves.
 * Text stays intentionally sparse so autosave does not compete with writing.
 */
public class SaveIndicatorPanel extends JPanel {
    private static final Color ICON_ON = new Color(28, 145, 62);
    private static final Color ICON_SAVING = new Color(128, 132, 142);
    private static final Color ICON_OFF = new Color(0, 0, 0, 0);
    private static final Color TEXT_COLOR = new Color(110, 112, 118);
    private static final Color MUTED_TEXT_COLOR = new Color(142, 144, 150);
    private static final Color ERROR_COLOR = new Color(160, 30, 30);
    private static final String TIME_TEMPLATE = "00:00";
    private static final String SAVING_TEXT = "saving";

    private final JLabel iconLabel = new JLabel("\u2713");
    private final JLabel textLabel = new JLabel(" ");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private final Dimension stableTextSize;

    public SaveIndicatorPanel() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        setToolTipText("Autosave status");
        iconLabel.setForeground(ICON_OFF); // keep width stable; toggle alpha
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        Dimension iconSize = new Dimension(12, 14);
        iconLabel.setPreferredSize(iconSize);
        iconLabel.setMinimumSize(iconSize);

        Font textFont = new Font("SansSerif", Font.PLAIN, 11);
        textLabel.setForeground(TEXT_COLOR);
        textLabel.setFont(textFont);
        FontMetrics fm = textLabel.getFontMetrics(textFont);
        int w = Math.max(fm.stringWidth(TIME_TEMPLATE), fm.stringWidth(SAVING_TEXT));
        int h = fm.getHeight();
        stableTextSize = new Dimension(w, h);
        applyStableTextSize();
        add(iconLabel);
        add(textLabel);
    }

    public void setSaving() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setText("\u2022");
            iconLabel.setForeground(ICON_SAVING);
            textLabel.setForeground(MUTED_TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(SAVING_TEXT);
            setToolTipText("Autosaving");
        });
    }

    public void setSaved(Date when) {
        if (when == null) when = new Date();
        final String msg = timeFmt.format(when);
        SwingUtilities.invokeLater(() -> {
            iconLabel.setText("\u2713");
            iconLabel.setForeground(ICON_ON);
            textLabel.setForeground(TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(msg);
            setToolTipText("Saved at " + msg);
        });
    }

    public void setSavedFromTimestamp(long lastModified) {
        setSaved(lastModified > 0 ? new Date(lastModified) : new Date());
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            iconLabel.setText("\u2713");
            iconLabel.setForeground(ICON_OFF);
            textLabel.setForeground(TEXT_COLOR);
            applyStableTextSize();
            textLabel.setText(" ");
            setToolTipText("Autosave status");
        });
    }

    public void setError(String message) {
        final String msg = (message == null || message.isBlank()) ? "Error saving" : message;
        SwingUtilities.invokeLater(() -> {
            iconLabel.setText("!");
            iconLabel.setForeground(ERROR_COLOR);
            textLabel.setForeground(ERROR_COLOR);
            clearStableTextSize();
            textLabel.setText(msg);
            setToolTipText(msg);
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
