package main.ui.dialog.input;

import java.awt.*;
import javax.swing.*;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.panels.RoundedPanel;
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

        RoundedPanel panel = new RoundedPanel();
        panel.setArc(30);
        panel.setBackground(new Color(45,45,45,230));
        panel.setLayout(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel lbl = new JLabel("<html><body style='text-align:center;'>"+message+"</body></html>", SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
        lbl.setForeground(AeroTheme.TEXT_PRIMARY);
        panel.add(lbl, BorderLayout.NORTH);

        JTextField field = new JTextField(initial==null?"":initial, 20);
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
        panel.add(field, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        btnPanel.setOpaque(false);

        FadingButton okBtn = new FadingButton("OK");
        okBtn.setBackground(new Color(80,130,180));
        okBtn.setForeground(Color.WHITE);
        okBtn.setPreferredSize(new Dimension(100,40));
        okBtn.addActionListener(e -> { result = field.getText().trim(); setVisible(false); dispose(); });

        FadingButton cancelBtn = new FadingButton("Cancel");
        cancelBtn.setBackground(new Color(120,120,120));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setPreferredSize(new Dimension(100,40));
        cancelBtn.addActionListener(e -> { result = null; setVisible(false); dispose(); });

        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        pack();
        setAlwaysOnTop(true);
        setLocationRelativeTo(parent);
    }

    public static String prompt(Component parent, String title, String message, String initial) {
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        if(frame == null) frame = new JFrame();
        CustomInputDialog dialog = new CustomInputDialog(frame, title, message, initial);
        dialog.setVisible(true);
        if(frame instanceof JournalApp){ ((JournalApp)frame).ensureFullScreen(); }
        return dialog.result;
    }
} 