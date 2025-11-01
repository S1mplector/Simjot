package main.ui.dialog.message;

import java.awt.*;
import javax.swing.*;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.components.containers.RoundedPanel;

public class CustomMessageDialog extends JDialog {
 
    private static volatile boolean SUPPRESS_ALL = false;
    public static void setGlobalSuppressed(boolean suppressed) { SUPPRESS_ALL = suppressed; }

    public CustomMessageDialog(Frame parent, String title, String message, boolean isError) {
        super(parent, title, true); // true for modal
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setLayout(new BorderLayout());

        // Use RoundedPanel but force a flat fill (no gradient)
        RoundedPanel mainPanel = new RoundedPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Flat, light background (no gradient)
                int arc = 30; // match setArc below
                g2.setColor(new Color(250, 250, 250));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
            }
        };
        mainPanel.setArc(30);
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout(10, 10));
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