/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.settings;

import java.awt.*;
import javax.swing.*;
import main.core.sim.prefs.SimSettings;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;

public class SimSettingsPage implements SettingsPage {
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JCheckBox enableSim = new JCheckBox("Enable Sim (AI companion)");
    private final JComboBox<String> personality = new JComboBox<>(new String[]{"gentle","neutral","proactive"});
    private final JCheckBox useLlm = new JCheckBox("Allow AI model");
    private final JComboBox<String> llmProvider = new JComboBox<>(new String[]{"ollama","openai"});
    private final JTextField quietHours = new JTextField(12);
    private final JSpinner nudgeMinutes = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
    private final JTextField ollamaEndpoint = new JTextField(18);
    private final JTextField ollamaModel = new JTextField(18);
    private final JPasswordField openaiApiKey = new JPasswordField(24);

    public SimSettingsPage(){
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,12,8,12);
        gc.anchor = GridBagConstraints.WEST;
        // Header
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; panel.add(SettingsUi.header("Sim", "Assistant behavior and triggers"), gc);
        gc.gridwidth = 1;
        // Match checkbox visuals used elsewhere in settings
        enableSim.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        enableSim.setBackground(new Color(0,0,0,0));
        useLlm.setUI(new main.ui.components.checkbox.ModernCheckBoxUI());
        useLlm.setBackground(new Color(0,0,0,0));
        // Modernize controls for consistency
        personality.setUI(new ModernComboBoxUI());
        personality.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        nudgeMinutes.setUI(new ModernSpinnerUI());
        try {
            ((JSpinner.DefaultEditor) nudgeMinutes.getEditor()).getTextField().setColumns(3);
        } catch (Throwable ignored) {}
        // Provider combobox styling
        llmProvider.setUI(new ModernComboBoxUI());
        llmProvider.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());

        gc.gridx = 0; gc.gridy = 1;
        panel.add(enableSim, gc);
        gc.gridy++;
        panel.add(new JLabel("Personality"), gc);
        gc.gridx = 1; panel.add(personality, gc);
        gc.gridx = 0; gc.gridy++;
        panel.add(useLlm, gc);
        gc.gridy++;
        panel.add(new JLabel("LLM provider"), gc);
        gc.gridx = 1;
        panel.add(llmProvider, gc);
        gc.gridy++;
        panel.add(new JLabel("Quiet hours (e.g., 22:00-07:00)"), gc);
        gc.gridx = 1;
        panel.add(quietHours, gc);

        // Nudge interval
        gc.gridx = 0; gc.gridy++;
        panel.add(new JLabel("Nudge interval (minutes)"), gc);
        gc.gridx = 1;
        panel.add(nudgeMinutes, gc);

        // Ollama settings
        gc.gridx = 0; gc.gridy++;
        panel.add(new JLabel("Ollama endpoint"), gc);
        gc.gridx = 1;
        panel.add(ollamaEndpoint, gc);
        gc.gridx = 0; gc.gridy++;
        panel.add(new JLabel("Ollama model"), gc);
        gc.gridx = 1;
        panel.add(ollamaModel, gc);

        // OpenAI settings
        gc.gridx = 0; gc.gridy++;
        panel.add(new JLabel("OpenAI API key"), gc);
        gc.gridx = 1;
        panel.add(openaiApiKey, gc);

        // Load current settings
        SimSettings s = SimSettings.get();
        enableSim.setSelected(s.isEnabled());
        personality.setSelectedItem(s.getPersonality());
        useLlm.setSelected(s.isLlmEnabled());
        llmProvider.setSelectedItem(s.getLlmProvider());
        quietHours.setText(s.getQuietHours());
        nudgeMinutes.setValue(s.getNudgeIntervalMinutes());
        ollamaEndpoint.setText(s.getOllamaEndpoint());
        ollamaModel.setText(s.getOllamaModel());
        openaiApiKey.setText(s.getOpenAIApiKey());

        // Enable/disable provider-specific fields based on checkbox and provider
        Runnable toggleLlmFields = () -> {
            boolean llmOn = useLlm.isSelected();
            llmProvider.setEnabled(llmOn);
            String provider = (String) llmProvider.getSelectedItem();
            boolean isOllama = llmOn && "ollama".equalsIgnoreCase(provider);
            boolean isOpenAI = llmOn && "openai".equalsIgnoreCase(provider);
            ollamaEndpoint.setEnabled(isOllama);
            ollamaModel.setEnabled(isOllama);
            openaiApiKey.setEnabled(isOpenAI);
        };
        useLlm.addActionListener(e -> toggleLlmFields.run());
        llmProvider.addActionListener(e -> toggleLlmFields.run());
        toggleLlmFields.run();
    }

    @Override
    public JComponent getComponent() { return panel; }

    @Override
    public void apply() {
        SimSettings s = SimSettings.get();
        s.setEnabled(enableSim.isSelected());
        s.setPersonality((String) personality.getSelectedItem());
        s.setLlmEnabled(useLlm.isSelected());
        s.setLlmProvider((String) llmProvider.getSelectedItem());
        s.setQuietHours(quietHours.getText());
        try { s.setNudgeIntervalMinutes((Integer) nudgeMinutes.getValue()); } catch (Exception ignored) {}
        s.setOllamaEndpoint(ollamaEndpoint.getText());
        s.setOllamaModel(ollamaModel.getText());
        // Only persist API key if visible/enabled and LLM is on
        if (useLlm.isSelected() && openaiApiKey.isEnabled()) {
            s.setOpenAIApiKey(new String(openaiApiKey.getPassword()));
        }
    }
}
