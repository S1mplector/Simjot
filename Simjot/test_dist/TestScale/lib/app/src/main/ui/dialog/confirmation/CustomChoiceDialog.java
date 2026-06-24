/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.dialog.confirmation;

import java.awt.*;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.theme.aero.AeroTheme;

/**
 * Modern translucent choice dialog with multiple action buttons, matching app styling.
 * Use {@link #choose(Component, String, String, String[])} to show and get the selected index.
 */
public class CustomChoiceDialog extends JDialog {
    private int selectedIndex = -1;

    private CustomChoiceDialog(Frame parent, String title, String message, String[] options) {
        super(parent, title, true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        setLayout(new BorderLayout());

        FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(14,14), 30);
        panel.setBorder(BorderFactory.createEmptyBorder(22,24,20,24));

        JLabel lbl = new JLabel("<html><body style='text-align:center;'>"+message+"</body></html>", SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
        lbl.setForeground(AeroTheme.TEXT_PRIMARY);
        panel.add(lbl, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            RoundedButton btn = new RoundedButton(options[i]);
            btn.setForeground(Color.BLACK);
            btn.setPreferredSize(new Dimension(150, 36));
            btn.addActionListener(e -> { selectedIndex = idx; setVisible(false); dispose(); });
            btnPanel.add(btn);
        }
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        pack();
        setAlwaysOnTop(true);
        setLocationRelativeTo(parent);
    }

    /**
     * Shows a choice dialog and returns the index of the selected option, or -1 if closed.
     */
    public static int choose(Component parent, String title, String message, String[] options) {
        if (options == null || options.length == 0) return -1;
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        if(frame == null) frame = new JFrame();
        CustomChoiceDialog dialog = new CustomChoiceDialog(frame, title, message, options);
        dialog.setVisible(true);
        int result = dialog.selectedIndex;
        if(frame instanceof JournalApp) {
            ((JournalApp)frame).ensureFullScreen();
        }
        return result;
    }
}
