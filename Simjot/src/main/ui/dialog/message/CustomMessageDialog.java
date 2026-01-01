/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.message;

import java.awt.*;
import javax.swing.*;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.components.containers.FrostedGlassPanel;

public class CustomMessageDialog extends JDialog {
 
    private static volatile boolean SUPPRESS_ALL = false;
    public static void setGlobalSuppressed(boolean suppressed) { SUPPRESS_ALL = suppressed; }

    public CustomMessageDialog(Frame parent, String title, String message, boolean isError) {
        super(parent, title, true); // true for modal
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setLayout(new BorderLayout());

        FrostedGlassPanel mainPanel = new FrostedGlassPanel(new BorderLayout(10, 10), 30);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel messageLabel = new JLabel("<html><body style='text-align: center;'>" + message + "</body></html>", SwingConstants.CENTER);
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        messageLabel.setForeground(new Color(30, 30, 30));
        mainPanel.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        FadingButton okButton = new FadingButton("OK");
        okButton.setBackground(isError ? new Color(180, 80, 80) : new Color(80, 130, 180));
        okButton.setForeground(Color.WHITE);
        okButton.setPreferredSize(new Dimension(100, 40));
        okButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });

        buttonPanel.add(okButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setAlwaysOnTop(true);
        setLocationRelativeTo(parent);
    }

    public void showDialog() {
        if (SUPPRESS_ALL) return;
        setVisible(true);
    }

    // Convenience helper for quick calls from anywhere (panel, frame, etc.)
    public static void display(Component parent, String title, String message, boolean isError) {
        if (SUPPRESS_ALL) return;
        Frame frame;
        if (parent instanceof Frame) {
            frame = (Frame) parent;
        } else {
            frame = (Frame) SwingUtilities.getWindowAncestor(parent);
        }
        if (frame == null) {
            // As a fallback create a dummy frame (won't be shown)
            frame = new JFrame();
        }
        new CustomMessageDialog(frame, title, message, isError).showDialog();
        if(frame instanceof JournalApp){ ((JournalApp)frame).ensureFullScreen(); }
    }
} 
