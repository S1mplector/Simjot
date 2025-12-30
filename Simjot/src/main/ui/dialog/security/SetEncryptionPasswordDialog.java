package main.ui.dialog.security;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import main.core.security.EncryptionManager;
import main.core.security.LockUtil;
import main.core.service.SettingsStore;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroPasswordField;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.theme.aero.AeroTheme;

public class SetEncryptionPasswordDialog extends JDialog {
    private final AeroPasswordField oldPw = new AeroPasswordField(18);
    private final AeroPasswordField newPw = new AeroPasswordField(18);
    private final AeroPasswordField confirmPw = new AeroPasswordField(18);

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
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(0, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));

        JLabel title = new JLabel("Set or change the encryption password", SwingConstants.CENTER);
        title.setFont(AeroTheme.defaultBoldFont(16f));
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.EAST;
        form.add(styledLabel("Current:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        oldPw.setPreferredSize(new Dimension(220, 30));
        form.add(oldPw, gc);

        gc.gridx = 0; gc.gridy++; gc.anchor = GridBagConstraints.EAST;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(styledLabel("New:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        newPw.setPreferredSize(new Dimension(220, 30));
        form.add(newPw, gc);

        gc.gridx = 0; gc.gridy++; gc.anchor = GridBagConstraints.EAST;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        form.add(styledLabel("Confirm:"), gc);
        gc.gridx = 1; gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        confirmPw.setPreferredSize(new Dimension(220, 30));
        form.add(confirmPw, gc);
        root.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        ToolbarMenuIconButton remove = new ToolbarMenuIconButton("", "trash");
        remove.setToolTipText("Remove Password");
        remove.addActionListener(e -> doRemove());
        ToolbarMenuIconButton ok = new ToolbarMenuIconButton("", "save");
        ok.setToolTipText("Save Password");
        ok.addActionListener(e -> doSave());
        ToolbarMenuIconButton cancel = new ToolbarMenuIconButton("", "exit");
        cancel.setToolTipText("Cancel");
        cancel.addActionListener(e -> { setVisible(false); dispose(); });
        btns.add(remove); btns.add(ok); btns.add(cancel);
        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    private JLabel styledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AeroTheme.defaultFont().deriveFont(12f));
        label.setForeground(AeroTheme.TEXT_PRIMARY);
        return label;
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
