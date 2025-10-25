package main.ui.features.settings;

import main.core.service.SettingsStore;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;

import javax.swing.*;
import java.awt.*;

public class SecuritySettingsPage implements SettingsPage {
    private final JPanel root = new JPanel(new GridBagLayout());

    private final JCheckBox enableLock = new JCheckBox("Enable App Lock");
    private final JCheckBox requireOnStart = new JCheckBox("Require password on startup");
    private final JSpinner timeoutMinutes = new JSpinner(new SpinnerNumberModel(0, 0, 240, 1));

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
        RoundedButton setPw = new RoundedButton("Set / Change Password…");
        setPw.addActionListener(e -> openSetPasswordDialog());
        buttons.add(setPw);
        root.add(buttons, gc);

        load();
    }

    private void load() {
        SettingsStore s = SettingsStore.get();
        enableLock.setSelected(s.isLockEnabled());
        requireOnStart.setSelected(s.isLockRequireOnStart());
        int sec = s.getLockTimeoutSec();
        int min = Math.max(0, sec / 60);
        timeoutMinutes.setValue(min);
    }

    private void openSetPasswordDialog(){
        java.awt.Window w = SwingUtilities.getWindowAncestor(root);
        java.awt.Frame owner = (w instanceof java.awt.Frame) ? (java.awt.Frame) w : null;
        new main.ui.dialog.security.SetPasswordDialog(owner).setVisible(true);
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
    }
}
