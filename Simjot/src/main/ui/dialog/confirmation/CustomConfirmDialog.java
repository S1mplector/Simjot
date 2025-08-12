package main.ui.dialog.confirmation;

import java.awt.*;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.panels.RoundedPanel;
import main.ui.theme.aero.AeroTheme;

/**
 * Modern translucent confirm dialog with Yes / No buttons matching style of other custom dialogs.
 */
public class CustomConfirmDialog extends JDialog {
    private boolean accepted;

    private CustomConfirmDialog(Frame parent, String title, String message) {
        super(parent, title, true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        setLayout(new BorderLayout());

        RoundedPanel panel = new RoundedPanel();
        panel.setArc(30);
        panel.setBackground(new Color(45,45,45,230));
        panel.setLayout(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel lbl = new JLabel("<html><body style='text-align:center;'>"+message+"</body></html>", SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
        lbl.setForeground(AeroTheme.TEXT_PRIMARY);
        panel.add(lbl, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        btnPanel.setOpaque(false);

        RoundedButton yesBtn = new RoundedButton("Yes");
        yesBtn.setForeground(Color.BLACK);
        yesBtn.setPreferredSize(new Dimension(110,36));
        yesBtn.addActionListener(e -> { accepted = true; setVisible(false); dispose(); });

        RoundedButton noBtn = new RoundedButton("No");
        noBtn.setForeground(Color.BLACK);
        noBtn.setPreferredSize(new Dimension(110,36));
        noBtn.addActionListener(e -> { accepted = false; setVisible(false); dispose(); });

        btnPanel.add(yesBtn);
        btnPanel.add(noBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        pack();
        setAlwaysOnTop(true);
        setLocationRelativeTo(parent);
    }

    /**
     * Shows the confirmation dialog and returns true if user pressed Yes.
     */
    public static boolean confirm(Component parent, String title, String message) {
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        if(frame == null) frame = new JFrame();
        CustomConfirmDialog dialog = new CustomConfirmDialog(frame, title, message);
        dialog.setVisible(true);
        boolean result = dialog.accepted;
        if(frame instanceof JournalApp) {
            ((JournalApp)frame).ensureFullScreen();
        }
        return result;
    }
} 