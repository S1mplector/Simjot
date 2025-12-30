package main.ui.features.settings;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import main.core.security.EncryptionManager;
import main.core.service.SettingsStore;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.dialog.confirmation.CustomConfirmDialog;

public class SecuritySettingsPage implements SettingsPage {
    private final JPanel root = new JPanel(new GridBagLayout());

    private final JCheckBox enableLock = new JCheckBox("Enable App Lock");
    private final JCheckBox requireOnStart = new JCheckBox("Require password on startup");
    private final JSpinner timeoutMinutes = new JSpinner(new SpinnerNumberModel(0, 0, 240, 1));
    private final JCheckBox enableEncryption = new JCheckBox("Enable encryption for entries, poems, and backups");
    private final JLabel encryptionStatus = new JLabel();
    private final IconMenuButton setEncryptionPw = new IconMenuButton("Set", "set_password");

    public SecuritySettingsPage() {
        root.setOpaque(true);
        root.setBackground(Color.WHITE);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        root.add(SettingsUi.header("Security & Lock", "Protect your notes and auto-lock when idle"), gc);
        gc.gridwidth = 1;

        // Checkboxes with Aero UI
        enableLock.setUI(new ModernCheckBoxUI());
        enableLock.setBackground(new Color(0,0,0,0));
        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 2; root.add(enableLock, gc);

        requireOnStart.setUI(new ModernCheckBoxUI());
        requireOnStart.setBackground(new Color(0,0,0,0));
        gc.gridy = 2; root.add(requireOnStart, gc);

        // Spinner with Aero UI
        timeoutMinutes.setUI(new ModernSpinnerUI());
        timeoutMinutes.setPreferredSize(new Dimension(72, 26));
        try {
            JSpinner.NumberEditor ed = (JSpinner.NumberEditor) timeoutMinutes.getEditor();
            JFormattedTextField tf = ed.getTextField();
            tf.setOpaque(false);
            tf.setBackground(Color.WHITE);
            tf.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            tf.setForeground(Color.DARK_GRAY);
        } catch (Throwable ignored) {}
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 3; root.add(SettingsUi.label("Auto-lock after (minutes):"), gc);
        gc.gridx = 1; root.add(timeoutMinutes, gc);

        // Buttons row
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 2;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        IconMenuButton setPw = new IconMenuButton("Set", "set_password");
        setPw.setToolTipText("Set / Change Password…");
        setPw.addActionListener(e -> openSetPasswordDialog());
        buttons.add(setPw);
        root.add(buttons, gc);

        // Encryption section
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2;
        root.add(SettingsUi.header("Encryption", "Protect entries, poems, and backups with a separate password"), gc);
        gc.gridwidth = 1;

        enableEncryption.setUI(new ModernCheckBoxUI());
        enableEncryption.setBackground(new Color(0,0,0,0));
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2; root.add(enableEncryption, gc);

        encryptionStatus.setForeground(new Color(0,0,0,120));
        encryptionStatus.setFont(encryptionStatus.getFont().deriveFont(11f));
        gc.gridy = 7; root.add(encryptionStatus, gc);

        gc.gridy = 8; gc.gridwidth = 2;
        JPanel encButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        encButtons.setOpaque(false);
        setEncryptionPw.setToolTipText("Set / Change Encryption Password…");
        setEncryptionPw.addActionListener(e -> openSetEncryptionPasswordDialog());
        encButtons.add(setEncryptionPw);
        root.add(encButtons, gc);

        enableEncryption.addActionListener(e -> onToggleEncryption());

        load();
    }

    private void load() {
        SettingsStore s = SettingsStore.get();
        enableLock.setSelected(s.isLockEnabled());
        requireOnStart.setSelected(s.isLockRequireOnStart());
        int sec = s.getLockTimeoutSec();
        int min = Math.max(0, sec / 60);
        timeoutMinutes.setValue(min);
        enableEncryption.setSelected(s.isEncryptionEnabled());
        updateEncryptionStatus();
    }

    private void openSetPasswordDialog(){
        java.awt.Window w = SwingUtilities.getWindowAncestor(root);
        java.awt.Frame owner = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
        new main.ui.dialog.security.SetPasswordDialog(owner).setVisible(true);
    }

    private void openSetEncryptionPasswordDialog() {
        java.awt.Window w = SwingUtilities.getWindowAncestor(root);
        java.awt.Frame owner = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
        new main.ui.dialog.security.SetEncryptionPasswordDialog(owner).setVisible(true);
        updateEncryptionStatus();
    }

    private void onToggleEncryption() {
        SettingsStore s = SettingsStore.get();
        if (enableEncryption.isSelected()) {
            if (!EncryptionManager.hasPasswordSet()) {
                openSetEncryptionPasswordDialog();
                if (!EncryptionManager.hasPasswordSet()) {
                    enableEncryption.setSelected(false);
                    return;
                }
            }
        } else {
            boolean ok = CustomConfirmDialog.confirm(root, "Disable Encryption",
                    "Encrypted entries will remain encrypted and require the password to open. Continue?");
            if (!ok) {
                enableEncryption.setSelected(true);
                return;
            }
            EncryptionManager.clearSessionPassword();
        }
        s.setEncryptionEnabled(enableEncryption.isSelected());
        s.save();
        updateEncryptionStatus();
    }

    private void updateEncryptionStatus() {
        if (EncryptionManager.hasPasswordSet()) {
            encryptionStatus.setText("Encryption password set.");
        } else {
            encryptionStatus.setText("No encryption password set.");
        }
    }

    @Override
    public JComponent getComponent() { return root; }

    @Override
    public void apply() {
        SettingsStore s = SettingsStore.get();
        s.setLockEnabled(enableLock.isSelected());
        s.setLockRequireOnStart(requireOnStart.isSelected());
        int min = (Integer) timeoutMinutes.getValue();
        int sec = Math.max(0, min) * 60;
        s.setLockTimeoutSec(sec);
        // Password is managed via dialog; nothing to do here
        if (enableEncryption.isSelected() && !EncryptionManager.hasPasswordSet()) {
            enableEncryption.setSelected(false);
        }
        s.setEncryptionEnabled(enableEncryption.isSelected());
    }
}
