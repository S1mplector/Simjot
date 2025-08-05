package main.dialog;

import javax.swing.*;
import java.awt.*;
import main.ui.components.ModernComboBoxUI;
import main.ui.components.ModernSpinnerUI;
import main.ui.buttons.RoundedButton;
import main.util.SettingsStore;

/**
 * Configuration dialog for the breathing widget with various breathing patterns
 * and customizable settings.
 */
public class BreathingConfigDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    
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
    
    // Store the selected configuration
    private BreathingPattern selectedPattern;
    private int inhaleTime, hold1Time, exhaleTime, hold2Time;
    private int opacity, size;
    private String color;
    
    public BreathingConfigDialog(JFrame parent) {
        super(parent, "Breathing Exercise Configuration", true);
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);
        
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        
        // Title
        JLabel titleLabel = new JLabel("Configure Your Breathing Exercise");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        mainPanel.add(titleLabel, gc);
        
        // Pattern selection
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 1;
        mainPanel.add(new JLabel("Breathing Pattern:"), gc);
        
        patternCombo = new JComboBox<>(BreathingPattern.values());
        patternCombo.setUI(new ModernComboBoxUI());
        patternCombo.addActionListener(e -> updateTimingFields());
        gc.gridx = 1;
        mainPanel.add(patternCombo, gc);
        
        // Timing controls
        gc.gridx = 0; gc.gridy = 2;
        mainPanel.add(new JLabel("Inhale (seconds):"), gc);
        inhaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 30, 1));
        inhaleSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(inhaleSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 3;
        mainPanel.add(new JLabel("Hold (seconds):"), gc);
        hold1Spinner = new JSpinner(new SpinnerNumberModel(4, 0, 30, 1));
        hold1Spinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(hold1Spinner, gc);
        
        gc.gridx = 0; gc.gridy = 4;
        mainPanel.add(new JLabel("Exhale (seconds):"), gc);
        exhaleSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 30, 1));
        exhaleSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(exhaleSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 5;
        mainPanel.add(new JLabel("Hold Empty (seconds):"), gc);
        hold2Spinner = new JSpinner(new SpinnerNumberModel(4, 0, 30, 1));
        hold2Spinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(hold2Spinner, gc);
        
        // Visual settings
        JSeparator sep = new JSeparator();
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
        gc.insets = new Insets(16, 8, 16, 8);
        mainPanel.add(sep, gc);
        gc.insets = new Insets(8, 8, 8, 8);
        
        gc.gridwidth = 1;
        gc.gridx = 0; gc.gridy = 7;
        mainPanel.add(new JLabel("Circle Opacity:"), gc);
        opacitySpinner = new JSpinner(new SpinnerNumberModel(90, 10, 100, 10));
        opacitySpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(opacitySpinner, gc);
        
        gc.gridx = 0; gc.gridy = 8;
        mainPanel.add(new JLabel("Circle Size:"), gc);
        sizeSpinner = new JSpinner(new SpinnerNumberModel(40, 20, 60, 5));
        sizeSpinner.setUI(new ModernSpinnerUI());
        gc.gridx = 1;
        mainPanel.add(sizeSpinner, gc);
        
        gc.gridx = 0; gc.gridy = 9;
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
        gc.gridx = 0; gc.gridy = 10; gc.gridwidth = 2;
        gc.insets = new Insets(16, 8, 8, 8);
        mainPanel.add(instructions, gc);
        
        // Load saved settings
        loadSettings();
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.WHITE);
        
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
        
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Listen for custom timing changes
        inhaleSpinner.addChangeListener(e -> checkCustomPattern());
        hold1Spinner.addChangeListener(e -> checkCustomPattern());
        exhaleSpinner.addChangeListener(e -> checkCustomPattern());
        hold2Spinner.addChangeListener(e -> checkCustomPattern());
        
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
}