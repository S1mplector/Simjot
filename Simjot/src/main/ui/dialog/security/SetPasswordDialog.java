package main.ui.dialog.security;

import main.core.security.LockUtil;
import main.core.service.SettingsStore;

import javax.swing.*;
import java.awt.*;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroPasswordField;
import main.ui.theme.aero.AeroTheme;

public class SetPasswordDialog extends JDialog {
    private final AeroPasswordField oldPw = new AeroPasswordField(18);
    private final AeroPasswordField newPw = new AeroPasswordField(18);
    private final AeroPasswordField confirmPw = new AeroPasswordField(18);

    public SetPasswordDialog(Frame owner) {
        super(owner, "Set Password", true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        buildUI();
        pack();
        setSize(400, 230);
        setLocationRelativeTo(owner);
    }

    private void buildUI(){
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(0, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));

        JLabel title = new JLabel("Set or change the lock password", SwingConstants.CENTER);
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
        JButton remove = new RoundedButton("Remove Password");
        remove.addActionListener(e -> doRemove());
        JButton ok = new RoundedButton("Save");
        ok.addActionListener(e -> doSave());
        JButton cancel = new RoundedButton("Cancel");
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
        SettingsStore s = SettingsStore.get();
        s.setLockPasswordSalt("");
        s.setLockPasswordHash("");
        s.save();
        JOptionPane.showMessageDialog(this, "Password removed.");
        setVisible(false); dispose();
    }

    private void doSave(){
        SettingsStore s = SettingsStore.get();
        String existingSalt = s.getLockPasswordSalt();
        String existingHash = s.getLockPasswordHash();
        if (existingHash != null && !existingHash.isBlank()) {
            // verify current
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
        s.setLockPasswordSalt(salt);
        s.setLockPasswordHash(hash);
        s.save();
        JOptionPane.showMessageDialog(this, "Password saved.");
        setVisible(false); dispose();
    }
}
