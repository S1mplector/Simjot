/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import main.infrastructure.io.MoodFile;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;

/**
 * A compact widget to quickly log mood to mood_log.moods without opening an editor.
 * Provides 5 preset buttons and a small slider for custom value.
 */
public class QuickMoodWidget implements Widget {
    private final JFrame owner;
    private JDialog dialog;
    private boolean enabled = false;

    private MoodSlider slider;
    private JLabel statusLabel;
    private javax.swing.Timer statusClearTimer;

    public QuickMoodWidget(JFrame owner) {
        this.owner = owner;
    }

    @Override
    public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    public void stop() {
        enabled = false;
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String getName() { return "Quick Mood Log"; }

    @Override
    public String getIconId() { return "smile"; }

    private void ensureDialog() {
        if (dialog != null) return;
        dialog = new JDialog(owner, "Quick Mood", false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.setLayout(new BorderLayout());

        FrostedGlassPanel content = new FrostedGlassPanel(new BorderLayout(8, 8), 16);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Header with close button
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Quick Mood", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        JButton close = new JButton(ImageIconRenderer.icon(ImageIconRenderer.mapIdToResource("close"), 16, true));
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setToolTipText("Close");
        close.addActionListener(e -> stop());
        header.add(close, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        content.add(header, BorderLayout.NORTH);

        // Center: mood slider + Log
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel sliderRow = new JPanel(new BorderLayout(6,0));
        sliderRow.setOpaque(false);
        slider = new MoodSlider();
        slider.setOpaque(false);
        sliderRow.add(slider, BorderLayout.CENTER);
        RoundedButton logBtn = new RoundedButton("Log");
        logBtn.addActionListener(e -> logMood(slider.getValue()));
        sliderRow.add(logBtn, BorderLayout.EAST);
        center.add(sliderRow);

        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(new Color(0,120,215));
        center.add(Box.createVerticalStrut(6));
        center.add(statusLabel);

        content.add(center, BorderLayout.CENTER);

        // Drag only by header
        MouseAdapter drag = new MouseAdapter() {
            Point offset;
            @Override public void mousePressed(MouseEvent e) { offset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                dialog.setLocation(p.x - offset.x, p.y - offset.y);
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);

        dialog.setContentPane(content);
        dialog.setSize(260, 120);
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 240);
    }

    private void logMood(int moodValue) {
        try {
            MoodFile.appendNow(moodValue);
            showStatus("Logged " + moodValue);
        } catch (Throwable ignored) {
            showStatus("Failed to log");
        }
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        if (statusClearTimer != null && statusClearTimer.isRunning()) statusClearTimer.stop();
        statusClearTimer = new javax.swing.Timer(1200, e -> statusLabel.setText(" "));
        statusClearTimer.setRepeats(false);
        statusClearTimer.start();
    }
}
