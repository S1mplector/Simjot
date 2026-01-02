/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.input;

import java.awt.*;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroTextField;
import main.ui.theme.aero.AeroTheme;

/**
 * Simple modern input dialog with translucent rounded panel, OK / Cancel buttons.
 */
public class CustomInputDialog extends JDialog {
    private String result = null;

    private CustomInputDialog(Frame parent, String title, String message, String initial) {
        super(parent, title, true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        setLayout(new BorderLayout());

        FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(12, 12), 22);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AeroTheme.defaultBoldFont(16f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        JLabel subtitle = new JLabel("<html><body style='text-align:left;'>"+message+"</body></html>");
        subtitle.setFont(AeroTheme.defaultFont().deriveFont(12f));
        subtitle.setForeground(new Color(120, 130, 145));
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);
        panel.add(header, BorderLayout.NORTH);

        AeroTextField field = new AeroTextField(22);
        field.setText(initial == null ? "" : initial);
        field.setFont(AeroTheme.defaultFont().deriveFont(14f));
        field.putClientProperty("JTextField.placeholderText", "Enter a name");
        field.selectAll();
        panel.add(field, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        RoundedButton okBtn = new RoundedButton("OK");
        okBtn.setPreferredSize(new Dimension(110, 36));
        okBtn.addActionListener(e -> { result = field.getText().trim(); setVisible(false); dispose(); });

        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(110, 36));
        cancelBtn.addActionListener(e -> { result = null; setVisible(false); dispose(); });

        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        pack();
        getRootPane().setDefaultButton(okBtn);
        setAlwaysOnTop(true);
        setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
    }

    public static String prompt(Component parent, String title, String message, String initial) {
        Window win = SwingUtilities.getWindowAncestor(parent);
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (win instanceof Frame ? (Frame) win : null);
        if(frame == null) frame = new JFrame();
        CustomInputDialog dialog = new CustomInputDialog(frame, title, message, initial);
        dialog.setVisible(true);
        if(frame instanceof JournalApp){ ((JournalApp)frame).ensureFullScreen(); }
        return dialog.result;
    }
} 
