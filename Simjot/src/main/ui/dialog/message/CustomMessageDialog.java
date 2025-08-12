package main.ui.dialog.message;

import java.awt.*;
import javax.swing.*;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.panels.RoundedPanel;
import main.ui.theme.aero.AeroTheme;

public class CustomMessageDialog extends JDialog {

    public CustomMessageDialog(Frame parent, String title, String message, boolean isError) {
        super(parent, title, true); // true for modal
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setLayout(new BorderLayout());

        RoundedPanel mainPanel = new RoundedPanel();
        mainPanel.setArc(30);
        mainPanel.setBackground(new Color(45, 45, 45, 230));
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel messageLabel = new JLabel("<html><body style='text-align: center;'>" + message + "</body></html>", SwingConstants.CENTER);
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        messageLabel.setForeground(AeroTheme.TEXT_PRIMARY);
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
        setVisible(true);
    }

    // Convenience helper for quick calls from anywhere (panel, frame, etc.)
    public static void display(Component parent, String title, String message, boolean isError) {
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