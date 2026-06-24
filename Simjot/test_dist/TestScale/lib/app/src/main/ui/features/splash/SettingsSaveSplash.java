/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.splash;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinner;
import main.ui.theme.aero.AeroTheme;

/**
 * Special splash screen shown when saving settings/preferences.
 * Displays a custom message and ensures robust preference saving.
 */
public class SettingsSaveSplash extends JWindow {
    private static final int MIN_VISIBLE_MS = 1000;
    private final JLabel headerLabel = new JLabel("Please wait while Simjot saves your preferences.", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
    private final ModernSpinner spinner = new ModernSpinner(28, new Color(0, 120, 215));
    private volatile long shownAtMs = -1L;

    public SettingsSaveSplash() {
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(480, 200);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBackground(new Color(0, 0, 0, 0));
        content.setDoubleBuffered(true);

        FrostedGlassPanel rounded = new FrostedGlassPanel(16);
        rounded.setLayout(new BoxLayout(rounded, BoxLayout.Y_AXIS));
        rounded.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        rounded.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Header text
        headerLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 15f));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status text
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Spinner
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        rounded.add(headerLabel);
        rounded.add(Box.createVerticalStrut(12));
        rounded.add(statusLabel);
        rounded.add(Box.createVerticalStrut(16));
        rounded.add(spinner);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        content.add(rounded, gbc);

        setContentPane(content);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            shownAtMs = System.currentTimeMillis();
        }
        super.setVisible(visible);
    }

    public void setStatus(String text) {
        if (text != null && !text.equals(statusLabel.getText())) {
            statusLabel.setText(text);
        }
    }

    public void fadeOutAndDispose() { fadeOutAndDispose(null); }

    public void fadeOutAndDispose(Runnable onComplete) {
        long elapsed = shownAtMs > 0 ? System.currentTimeMillis() - shownAtMs : MIN_VISIBLE_MS;
        long delay = MIN_VISIBLE_MS - elapsed;
        if (delay > 0) {
            Timer wait = new Timer((int) delay, null);
            wait.setRepeats(false);
            wait.addActionListener(e -> startFade(onComplete));
            wait.start();
            return;
        }
        startFade(onComplete);
    }

    private void startFade(Runnable onComplete) {
        final float[] alpha = {1f};
        Timer t = new Timer(15, null);
        t.addActionListener(e -> {
            alpha[0] -= 0.06f;
            if (alpha[0] <= 0f) {
                t.stop();
                setVisible(false);
                dispose();
                if (onComplete != null) onComplete.run();
            } else {
                float a = Math.max(0f, alpha[0]);
                try { setOpacity(a); } catch (Throwable ignore) {}
            }
        });
        t.start();
    }
}
