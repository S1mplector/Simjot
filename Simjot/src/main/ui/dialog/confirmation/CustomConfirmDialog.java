/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.confirmation;

import java.awt.*;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.theme.aero.AeroTheme;

/**
 * Modern translucent confirm dialog with Yes / No buttons matching style of other custom dialogs.
 */
public class CustomConfirmDialog extends JDialog {
    private boolean accepted;

    private CustomConfirmDialog(Frame parent, String title, String message) {
        super(parent, title, true);
        buildUI(message, parent);
    }

    private CustomConfirmDialog(Window owner, String title, String message) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        buildUI(message, owner);
    }

    private void buildUI(String message, Window parent) {
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        setLayout(new BorderLayout());

        FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(10,10), 30);
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
        Window win = (parent != null) ? SwingUtilities.getWindowAncestor(parent) : null;
        CustomConfirmDialog dialog;
        if (win instanceof Frame f) {
            dialog = new CustomConfirmDialog(f, title, message);
        } else {
            dialog = new CustomConfirmDialog(win, title, message);
        }
        dialog.setVisible(true);
        boolean result = dialog.accepted;
        if(win instanceof JournalApp) {
            ((JournalApp)win).ensureFullScreen();
        }
        return result;
    }
}
