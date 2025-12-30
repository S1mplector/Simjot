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

import main.core.security.LockUtil;
import main.core.service.SettingsStore;

import javax.swing.*;
import java.awt.*;
import main.ui.components.containers.FrostedGlassPanel;

public class LockDialog extends JDialog {
    public enum Mode { UNLOCK }

    private final JPasswordField passwordField = new JPasswordField(18);
    private boolean unlocked = false;
    private final boolean modalBlock;

    public LockDialog(Frame owner, Mode mode, boolean modalBlock) {
        super(owner, "Unlock", true);
        this.modalBlock = modalBlock;
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        buildUI();
        pack();
        setSize(360, 200);
        setLocationRelativeTo(owner);
    }

    private void buildUI(){
        FrostedGlassPanel chrome = new FrostedGlassPanel(new BorderLayout(), 16);
        chrome.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));

        JLabel title = new JLabel("Enter password to unlock", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(new Color(30,30,30));
        chrome.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx=0; gc.gridy=0; gc.anchor=GridBagConstraints.CENTER; gc.insets=new Insets(6,6,6,6);
        passwordField.setEchoChar('•');
        center.add(passwordField, gc);
        chrome.add(center, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        btns.setOpaque(false);
        JButton setPw = new JButton("Set/Change Password");
        setPw.addActionListener(e -> openSetPassword());
        JButton ok = new JButton("Unlock");
        ok.addActionListener(e -> tryUnlock());
        JButton cancel = new JButton("Exit");
        cancel.addActionListener(e -> { if (!modalBlock) setVisible(false); else System.exit(0); });
        btns.add(setPw); btns.add(ok); btns.add(cancel);
        chrome.add(btns, BorderLayout.SOUTH);

        setContentPane(chrome);
        getRootPane().setDefaultButton(ok);
    }

    private void openSetPassword(){
        SetPasswordDialog d = new SetPasswordDialog((Frame) getOwner());
        d.setVisible(true);
        passwordField.setText("");
    }

    private void tryUnlock(){
        SettingsStore s = SettingsStore.get();
        String salt = s.getLockPasswordSalt();
        String hash = s.getLockPasswordHash();
        // If no password is set, treat as unlocked to avoid lockout
        if (hash == null || hash.isBlank()) {
            unlocked = true;
            setVisible(false);
            dispose();
            return;
        }
        char[] pw = passwordField.getPassword();
        boolean ok = LockUtil.verify(new String(pw), salt, hash);
        if (ok) {
            unlocked = true;
            setVisible(false);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Incorrect password.", "Unlock Failed", JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
        }
    }

    public boolean blockUntilUnlocked(){
        setVisible(true);
        return unlocked;
    }
}
