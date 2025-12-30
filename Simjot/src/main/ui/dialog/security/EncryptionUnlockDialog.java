/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.security;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import main.ui.components.containers.FrostedGlassPanel;

/**
 * Prompt for the encryption password. Optionally remembers for the session.
 */
public class EncryptionUnlockDialog extends JDialog {
    public static final class Result {
        public final char[] password;
        public final boolean remember;

        private Result(char[] password, boolean remember) {
            this.password = password;
            this.remember = remember;
        }
    }

    private final JPasswordField passwordField = new JPasswordField(18);
    private final JCheckBox rememberCheck = new JCheckBox("Remember for this session", true);
    private boolean confirmed = false;
    private char[] password = null;

    public EncryptionUnlockDialog(Frame owner) {
        super(owner, "Unlock Encryption", true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        buildUI();
        pack();
        setSize(360, 210);
        setLocationRelativeTo(owner);
    }

    public static Result prompt(java.awt.Component parent) {
        Window w = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;
        EncryptionUnlockDialog d = new EncryptionUnlockDialog(owner);
        d.setVisible(true);
        if (!d.confirmed || d.password == null) return null;
        return new Result(d.password, d.rememberCheck.isSelected());
    }

    private void buildUI() {
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(), 16);
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));

        JLabel title = new JLabel("Enter encryption password", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        title.setForeground(new Color(30,30,30));
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new java.awt.GridBagLayout());
        center.setOpaque(false);
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0; gc.gridy = 0;
        passwordField.setEchoChar('•');
        center.add(passwordField, gc);
        gc.gridy = 1;
        rememberCheck.setOpaque(false);
        center.add(rememberCheck, gc);
        root.add(center, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton ok = new JButton("Unlock");
        ok.addActionListener(e -> confirm());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { setVisible(false); dispose(); });
        btns.add(cancel);
        btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    private void confirm() {
        this.password = passwordField.getPassword();
        this.confirmed = true;
        setVisible(false);
        dispose();
    }
}
