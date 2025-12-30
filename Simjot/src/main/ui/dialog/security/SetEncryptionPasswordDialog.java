package main.ui.dialog.security;

import main.core.security.EncryptionManager;
import main.core.security.LockUtil;
import main.core.service.SettingsStore;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.dialog.confirmation.CustomConfirmDialog;

import javax.swing.*;
import java.awt.*;

public class SetEncryptionPasswordDialog extends JDialog {
    private final JPasswordField oldPw = new JPasswordField(16);
    private final JPasswordField newPw = new JPasswordField(16);
    private final JPasswordField confirmPw = new JPasswordField(16);

    public SetEncryptionPasswordDialog(Frame owner) {
        super(owner, "Set Encryption Password", true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        buildUI();
        pack();
        setSize(420, 240);
        setLocationRelativeTo(owner);
    }

    private void buildUI(){
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(), 16);
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));

        JLabel title = new JLabel("Set or change the encryption password", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(new Color(30,30,30));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("Current:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        oldPw.setEchoChar('•');
        form.add(oldPw, gc);

        gc.gridx = 0; gc.gridy++; gc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("New:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        newPw.setEchoChar('•');
        form.add(newPw, gc);

        gc.gridx = 0; gc.gridy++; gc.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("Confirm:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        confirmPw.setEchoChar('•');
        form.add(confirmPw, gc);
        root.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton remove = new JButton("Remove Password");
        remove.addActionListener(e -> doRemove());
        JButton ok = new JButton("Save");
        ok.addActionListener(e -> doSave());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { setVisible(false); dispose(); });
        btns.add(remove); btns.add(ok); btns.add(cancel);
        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    private void doRemove(){
        boolean ok = CustomConfirmDialog.confirm(this, "Remove Encryption Password",
                "Encrypted entries will remain inaccessible without a password. Continue?");
        if (!ok) return;
        SettingsStore s = SettingsStore.get();
        s.setEncryptionPasswordSalt("");
        s.setEncryptionPasswordHash("");
        s.save();
        EncryptionManager.clearSessionPassword();
        JOptionPane.showMessageDialog(this, "Encryption password removed.");
        setVisible(false); dispose();
    }

    private void doSave(){
        SettingsStore s = SettingsStore.get();
        String existingSalt = s.getEncryptionPasswordSalt();
        String existingHash = s.getEncryptionPasswordHash();
        if (existingHash != null && !existingHash.isBlank()) {
            String cur = new String(oldPw.getPassword());
            if (!LockUtil.verify(cur, existingSalt, existingHash)) {
                JOptionPane.showMessageDialog(this, "Current password incorrect.", "Error", JOptionPane.ERROR_MESSAGE);
                oldPw.setText("");
                return;
            }
        }
        String np = new String(newPw.getPassword());
        String cp = new String(confirmPw.getPassword());
        if (np.isEmpty()) {
            JOptionPane.showMessageDialog(this, "New password cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!np.equals(cp)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String salt = LockUtil.newSalt();
        String hash = LockUtil.hashPassword(np, salt);
        s.setEncryptionPasswordSalt(salt);
        s.setEncryptionPasswordHash(hash);
        s.save();
        EncryptionManager.cacheSessionPassword(np.toCharArray());
        JOptionPane.showMessageDialog(this, "Encryption password saved.");
        setVisible(false); dispose();
    }
}
