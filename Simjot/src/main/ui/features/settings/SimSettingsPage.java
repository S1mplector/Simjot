package main.ui.features.settings;

import java.awt.*;
import javax.swing.*;
import main.core.sim.SimSettings;

public class SimSettingsPage implements SettingsPage {
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JCheckBox enableSim = new JCheckBox("Enable Sim (AI companion)");
    private final JComboBox<String> personality = new JComboBox<>(new String[]{"gentle","neutral","proactive"});
    private final JCheckBox useLlm = new JCheckBox("Allow AI model (Ollama)");
    private final JTextField quietHours = new JTextField(12);

    public SimSettingsPage(){
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,12,8,12);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0; gc.gridy = 0;
        panel.add(enableSim, gc);
        gc.gridy++;
        panel.add(new JLabel("Personality"), gc);
        gc.gridx = 1; panel.add(personality, gc);
        gc.gridx = 0; gc.gridy++;
        panel.add(useLlm, gc);
        gc.gridy++;
        panel.add(new JLabel("Quiet hours (e.g., 22:00-07:00)"), gc);
        gc.gridx = 1;
        panel.add(quietHours, gc);

        // Load current settings
        SimSettings s = SimSettings.get();
        enableSim.setSelected(s.isEnabled());
        personality.setSelectedItem(s.getPersonality());
        useLlm.setSelected(s.isLlmEnabled());
        quietHours.setText(s.getQuietHours());
    }

    @Override
    public JComponent getComponent() { return panel; }

    @Override
    public void apply() {
        SimSettings s = SimSettings.get();
        s.setEnabled(enableSim.isSelected());
        s.setPersonality((String) personality.getSelectedItem());
        s.setLlmEnabled(useLlm.isSelected());
        s.setQuietHours(quietHours.getText());
    }
}
