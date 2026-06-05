/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.dialog.config;

import java.awt.*;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.dialog.confirmation.CustomConfirmDialog;
/**
 * Configuration dialog for the breathing widget with various breathing patterns
 * and customizable settings.
 */
public class BreathingConfigDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String NO_PROFILES_PLACEHOLDER = "no saved profiles";
    
    // Breathing patterns
    public enum BreathingPattern {
        BOX_BREATHING("Box Breathing (4-4-4-4)", 4, 4, 4, 4),
        FOUR_SEVEN_EIGHT("4-7-8 Breathing", 4, 7, 8, 0),
        BELLY_BREATHING("Belly Breathing (5-2-7)", 5, 2, 7, 0),
        COHERENT_BREATHING("Coherent Breathing (5-5)", 5, 0, 5, 0),
        CUSTOM("Custom", 4, 4, 4, 4);
        
        private final String displayName;
        private final int inhale, hold1, exhale, hold2;
        
        BreathingPattern(String name, int in, int h1, int ex, int h2) {
            this.displayName = name;
            this.inhale = in;
            this.hold1 = h1;
            this.exhale = ex;
            this.hold2 = h2;
        }
        
        @Override
        public String toString() { return displayName; }
    }
    
    private JComboBox<BreathingPattern> patternCombo;
    private JSpinner inhaleSpinner, hold1Spinner, exhaleSpinner, hold2Spinner;
    private JSpinner opacitySpinner, sizeSpinner;
    private JComboBox<String> colorCombo;
    private boolean confirmed = false;
    // Profiles UI
    private JComboBox<String> profileCombo;
    
    // Store the selected configuration
    private BreathingPattern selectedPattern;
    private int inhaleTime, hold1Time, exhaleTime, hold2Time;
    private int opacity, size;
    private String color;
    
    public BreathingConfigDialog(JFrame parent) {
        super(parent, "Breathing Exercise Configuration", true);
        setLayout(new BorderLayout());
        
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(), 18);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setOpaque(false);
        
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        
        // Title
        JLabel titleLabel = new JLabel("Configure Your Breathing Exercise");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        mainPanel.add(titleLabel, gc);
        
        // Profile controls (row 1)
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 1;
        mainPanel.add(new JLabel("Profile:"), gc);

        JPanel profilePanel = new JPanel(new BorderLayout(6, 0));
        profilePanel.setOpaque(false);
        profileCombo = new JComboBox<>();
        profileCombo.setUI(new ModernComboBoxUI());
        // Add right padding so text doesn't intersect with the arrow icon
        profileCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 22));
                if (value != null && NO_PROFILES_PLACEHOLDER.equals(value.toString())) {
                    c.setForeground(Color.GRAY);
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                }
                return c;
            }
        });
        profilePanel.add(profileCombo, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        profileButtons.setOpaque(false);
        RoundedButton saveProfileBtn = new RoundedButton("Save");
        saveProfileBtn.putClientProperty("iconId", "save");
        saveProfileBtn.setToolTipText("Save profile");
        RoundedButton loadProfileBtn = new RoundedButton("Load");
        loadProfileBtn.putClientProperty("iconId", "load");
        loadProfileBtn.setToolTipText("Load profile");
        RoundedButton deleteProfileBtn = new RoundedButton("Delete");
        deleteProfileBtn.putClientProperty("iconId", "trash");
        deleteProfileBtn.setToolTipText("Delete profile");
        saveProfileBtn.addActionListener(e -> saveCurrentAsProfile());
        loadProfileBtn.addActionListener(e -> applySelectedProfile());
        deleteProfileBtn.addActionListener(e -> deleteSelectedProfile());
        profileButtons.add(saveProfileBtn);
        profileButtons.add(loadProfileBtn);
        profileButtons.add(deleteProfileBtn);
        profilePanel.add(profileButtons, BorderLayout.EAST);

        gc.gridx = 1;
        mainPanel.add(profilePanel, gc);

        // Pattern selection
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 2;
        mainPanel.add(new JLabel("Breathing Pattern:"), gc);
        
        patternCombo = new JComboBox<>(BreathingPattern.values());
        patternCombo.setUI(new ModernComboBoxUI());
        patternCombo.addActionListener(e -> updateTimingFields());
        gc.gridx = 1;
        mainPanel.add(patternCombo, gc);
        
        // Timing controls
        gc.gridx = 0; gc.gridy = 3;
        mainPanel.add(new JLabel("Inhale (seconds):"), gc);
        inhaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 30, 1));
        inhaleSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(inhaleSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 4;
        mainPanel.add(new JLabel("Hold (seconds):"), gc);
        hold1Spinner = new JSpinner(new SpinnerNumberModel(4, 0, 30, 1));
        hold1Spinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(hold1Spinner, gc);
        
        gc.gridx = 0; gc.gridy = 5;
        mainPanel.add(new JLabel("Exhale (seconds):"), gc);
        exhaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 30, 1));
        exhaleSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(exhaleSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 6;
        mainPanel.add(new JLabel("Hold Empty (seconds):"), gc);
        hold2Spinner = new JSpinner(new SpinnerNumberModel(4, 0, 30, 1));
        hold2Spinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(hold2Spinner, gc);
        
        // Visual settings
        JSeparator sep = new JSeparator();
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2;
        gc.insets = new Insets(16, 8, 16, 8);
        mainPanel.add(sep, gc);
        gc.insets = new Insets(8, 8, 8, 8);
        
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 8;
        mainPanel.add(new JLabel("Circle Opacity:"), gc);
        opacitySpinner = new JSpinner(new SpinnerNumberModel(90, 10, 100, 10));
        opacitySpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(opacitySpinner, gc);
        
        gc.gridx = 0; gc.gridy = 9;
        mainPanel.add(new JLabel("Circle Size:"), gc);
        sizeSpinner = new JSpinner(new SpinnerNumberModel(40, 20, 60, 5));
        sizeSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(sizeSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 10;
        mainPanel.add(new JLabel("Circle Color:"), gc);
        String[] colors = {"White", "Blue", "Green", "Purple", "Orange"};
        colorCombo = new JComboBox<>(colors);
        colorCombo.setUI(new ModernComboBoxUI());
        gc.gridx = 1;
        mainPanel.add(colorCombo, gc);
        
        // Instructions
        JTextArea instructions = new JTextArea(
            "Choose a breathing pattern or customize your own.\n" +
            "The circle will grow during inhale and shrink during exhale.\n" +
            "Hold phases will pause the animation."
        );
        instructions.setEditable(false);
        instructions.setOpaque(false);
        instructions.setFont(new Font("SansSerif", Font.ITALIC, 12));
        instructions.setForeground(Color.GRAY);
        gc.gridx = 0; gc.gridy = 11; gc.gridwidth = 2;
        gc.insets = new Insets(16, 8, 8, 8);
        mainPanel.add(instructions, gc);
        
        // Load saved settings
        loadSettings();
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        
        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        RoundedButton startBtn = new RoundedButton("Start Breathing");
        startBtn.addActionListener(e -> {
            saveSettings();
            confirmed = true;
            dispose();
        });
        
        buttonPanel.add(cancelBtn);
        buttonPanel.add(startBtn);
        
        root.add(mainPanel, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(root);
        
        // Listen for custom timing changes
        inhaleSpinner.addChangeListener(e -> checkCustomPattern());
        hold1Spinner.addChangeListener(e -> checkCustomPattern());
        exhaleSpinner.addChangeListener(e -> checkCustomPattern());
        hold2Spinner.addChangeListener(e -> checkCustomPattern());
        
        // Populate profiles after UI created
        reloadProfilesIntoCombo();

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    private void updateTimingFields() {
        BreathingPattern pattern = (BreathingPattern) patternCombo.getSelectedItem();
        if (pattern != null && pattern != BreathingPattern.CUSTOM) {
            inhaleSpinner.setValue(pattern.inhale);
            hold1Spinner.setValue(pattern.hold1);
            exhaleSpinner.setValue(pattern.exhale);
            hold2Spinner.setValue(pattern.hold2);
        }
    }
    
    private void checkCustomPattern() {
        // If user manually changes timing, switch to Custom
        BreathingPattern current = (BreathingPattern) patternCombo.getSelectedItem();
        if (current != null && current != BreathingPattern.CUSTOM) {
            int in = (Integer) inhaleSpinner.getValue();
            int h1 = (Integer) hold1Spinner.getValue();
            int ex = (Integer) exhaleSpinner.getValue();
            int h2 = (Integer) hold2Spinner.getValue();
            
            if (in != current.inhale || h1 != current.hold1 || 
                ex != current.exhale || h2 != current.hold2) {
                patternCombo.setSelectedItem(BreathingPattern.CUSTOM);
            }
        }
    }
    
    private void loadSettings() {
        SettingsStore store = SettingsStore.get();
        // Load from settings if they exist, otherwise use defaults
        inhaleSpinner.setValue(4);
        hold1Spinner.setValue(4);
        exhaleSpinner.setValue(4);
        hold2Spinner.setValue(4);
        opacitySpinner.setValue(90);
        sizeSpinner.setValue(40);
        colorCombo.setSelectedItem("White");
        patternCombo.setSelectedItem(BreathingPattern.BOX_BREATHING);
    }
    
    private void saveSettings() {
        selectedPattern = (BreathingPattern) patternCombo.getSelectedItem();
        inhaleTime = (Integer) inhaleSpinner.getValue();
        hold1Time = (Integer) hold1Spinner.getValue();
        exhaleTime = (Integer) exhaleSpinner.getValue();
        hold2Time = (Integer) hold2Spinner.getValue();
        opacity = (Integer) opacitySpinner.getValue();
        size = (Integer) sizeSpinner.getValue();
        color = (String) colorCombo.getSelectedItem();
        
        // Could save to SettingsStore here for persistence
    }
    
    public boolean isConfirmed() { return confirmed; }
    public int getInhaleTime() { return inhaleTime; }
    public int getHold1Time() { return hold1Time; }
    public int getExhaleTime() { return exhaleTime; }
    public int getHold2Time() { return hold2Time; }
    public int getOpacityValue() { return opacity; }
    public int getSizeValue() { return size; }
    public String getColor() { return color; }

    // ---------------- Profiles persistence ---------------- //
    private static final String KEY_PROFILES_LIST = "breathing.profiles"; // comma-separated names
    private static final String KEY_PROFILE_PREFIX = "breathing.profile."; // + name -> CSV payload

    private void reloadProfilesIntoCombo() {
        if (profileCombo == null) return;
        profileCombo.removeAllItems();
        SettingsStore store = SettingsStore.get();
        String csv = store.getValue(KEY_PROFILES_LIST, "");
        boolean addedAny = false;
        if (csv != null && !csv.isBlank()) {
            for (String name : csv.split(",")) {
                String n = name.trim();
                if (!n.isEmpty()) { profileCombo.addItem(n); addedAny = true; }
            }
        }
        if (!addedAny) {
            profileCombo.addItem(NO_PROFILES_PLACEHOLDER);
            profileCombo.setSelectedIndex(0);
        }
    }

    private void saveCurrentAsProfile() {
        String name = JOptionPane.showInputDialog(this, "Profile name:", "Save Profile", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return; // cancelled
        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a profile name.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Serialize current settings
        int in = (Integer) inhaleSpinner.getValue();
        int h1 = (Integer) hold1Spinner.getValue();
        int ex = (Integer) exhaleSpinner.getValue();
        int h2 = (Integer) hold2Spinner.getValue();
        int op = (Integer) opacitySpinner.getValue();
        int sz = (Integer) sizeSpinner.getValue();
        String col = (String) colorCombo.getSelectedItem();
        String payload = in+";"+h1+";"+ex+";"+h2+";"+op+";"+sz+";"+col;

        SettingsStore store = SettingsStore.get();
        String list = store.getValue(KEY_PROFILES_LIST, "");
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (list != null && !list.isBlank()) {
            for (String n : list.split(",")) { if (!n.trim().isEmpty()) names.add(n.trim()); }
        }
        names.add(name);
        store.setValue(KEY_PROFILES_LIST, String.join(",", names));
        store.setValue(KEY_PROFILE_PREFIX + name, payload);
        store.save();

        reloadProfilesIntoCombo();
        profileCombo.setSelectedItem(name);
    }

    private void applySelectedProfile() {
        String name = (String) profileCombo.getSelectedItem();
        if (name == null || name.isBlank() || NO_PROFILES_PLACEHOLDER.equals(name)) return;
        SettingsStore store = SettingsStore.get();
        String payload = store.getValue(KEY_PROFILE_PREFIX + name, null);
        if (payload == null || payload.isBlank()) return;
        String[] parts = payload.split(";");
        if (parts.length < 7) return;
        try {
            inhaleSpinner.setValue(Integer.parseInt(parts[0]));
            hold1Spinner.setValue(Integer.parseInt(parts[1]));
            exhaleSpinner.setValue(Integer.parseInt(parts[2]));
            hold2Spinner.setValue(Integer.parseInt(parts[3]));
            opacitySpinner.setValue(Integer.parseInt(parts[4]));
            sizeSpinner.setValue(Integer.parseInt(parts[5]));
            colorCombo.setSelectedItem(parts[6]);
            patternCombo.setSelectedItem(BreathingPattern.CUSTOM);
        } catch (NumberFormatException ignored) {}
    }

    private void deleteSelectedProfile() {
        String name = (String) profileCombo.getSelectedItem();
        if (name == null || name.isBlank() || NO_PROFILES_PLACEHOLDER.equals(name)) return;
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Profile", "Delete profile '"+name+"'?");
        if (!ok) return;
        SettingsStore store = SettingsStore.get();
        // Remove entry
        store.setValue(KEY_PROFILE_PREFIX + name, null);
        // Update list
        String list = store.getValue(KEY_PROFILES_LIST, "");
        java.util.ArrayList<String> updated = new java.util.ArrayList<>();
        if (list != null && !list.isBlank()) {
            for (String n : list.split(",")) {
                String t = n.trim();
                if (!t.isEmpty() && !t.equals(name)) updated.add(t);
            }
        }
        store.setValue(KEY_PROFILES_LIST, String.join(",", updated));
        store.save();
        reloadProfilesIntoCombo();
    }
}
