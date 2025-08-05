package main.dialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import main.ui.widgets.BreathingWidget;
import main.ui.buttons.RoundedButton;
import main.ui.panels.RoundedPanel;

/**
 * Dedicated window for the breathing exercise with controls
 */
public class BreathingExerciseWindow extends JDialog {
    private static final long serialVersionUID = 1L;
    
    private final BreathingWidget breathingWidget;
    private final JLabel instructionLabel;
    private final JLabel phaseLabel;
    private Timer phaseTimer;
    private int currentPhaseTime = 0;
    private String currentPhase = "Inhale";
    
    // Configuration
    private int inhaleTime = 4;
    private int hold1Time = 4;
    private int exhaleTime = 4;
    private int hold2Time = 4;
    
    public BreathingExerciseWindow(JFrame parent) {
        super(parent, "Breathing Exercise", false); // Non-modal so user can still interact
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(30, 30, 40));
        
        // Main breathing panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setOpaque(false);
        
        // Top instruction panel
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        
        instructionLabel = new JLabel("Follow the circle as it guides your breathing");
        instructionLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        instructionLabel.setForeground(Color.WHITE);
        instructionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        phaseLabel = new JLabel("Inhale");
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        phaseLabel.setForeground(new Color(100, 200, 255));
        phaseLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        topPanel.add(instructionLabel);
        topPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        topPanel.add(phaseLabel);
        
        // Center breathing widget
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        centerPanel.setPreferredSize(new Dimension(600, 400));
        
        breathingWidget = new BreathingWidget();
        breathingWidget.setPreferredSize(new Dimension(600, 400));
        centerPanel.add(breathingWidget);
        
        // Bottom control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setOpaque(false);
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        
        RoundedButton reconfigureBtn = new RoundedButton("Reconfigure");
        reconfigureBtn.setPreferredSize(new Dimension(120, 40));
        reconfigureBtn.addActionListener(e -> reconfigure());
        
        RoundedButton pauseBtn = new RoundedButton("Pause");
        pauseBtn.setPreferredSize(new Dimension(100, 40));
        pauseBtn.addActionListener(e -> {
            if (breathingWidget.isEnabled()) {
                breathingWidget.stop();
                pauseBtn.setText("Resume");
                stopPhaseTimer();
            } else {
                breathingWidget.start();
                pauseBtn.setText("Pause");
                startPhaseTimer();
            }
        });
        
        RoundedButton exitBtn = new RoundedButton("Exit");
        exitBtn.setPreferredSize(new Dimension(100, 40));
        exitBtn.addActionListener(e -> {
            breathingWidget.stop();
            stopPhaseTimer();
            dispose();
        });
        
        controlPanel.add(reconfigureBtn);
        controlPanel.add(pauseBtn);
        controlPanel.add(exitBtn);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Window listener to stop animation when closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                breathingWidget.stop();
                stopPhaseTimer();
            }
        });
        
        setSize(700, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    public void startExercise(int inhale, int hold1, int exhale, int hold2, 
                             int opacity, int size, String color) {
        this.inhaleTime = inhale;
        this.hold1Time = hold1;
        this.exhaleTime = exhale;
        this.hold2Time = hold2;
        
        breathingWidget.configure(inhale, hold1, exhale, hold2, opacity, size, color);
        breathingWidget.start();
        startPhaseTimer();
        setVisible(true);
    }
    
    private void startPhaseTimer() {
        stopPhaseTimer();
        
        phaseTimer = new Timer(100, e -> {
            currentPhaseTime++;
            float seconds = currentPhaseTime / 10f;
            
            // Determine current phase
            int totalCycle = inhaleTime + hold1Time + exhaleTime + hold2Time;
            float cyclePosition = seconds % totalCycle;
            
            if (cyclePosition < inhaleTime) {
                currentPhase = "Inhale";
                phaseLabel.setText(currentPhase + " (" + (int)(inhaleTime - cyclePosition) + "s)");
                phaseLabel.setForeground(new Color(100, 200, 255));
            } else if (cyclePosition < inhaleTime + hold1Time) {
                currentPhase = "Hold";
                phaseLabel.setText(currentPhase + " (" + (int)(inhaleTime + hold1Time - cyclePosition) + "s)");
                phaseLabel.setForeground(new Color(255, 200, 100));
            } else if (cyclePosition < inhaleTime + hold1Time + exhaleTime) {
                currentPhase = "Exhale";
                phaseLabel.setText(currentPhase + " (" + (int)(inhaleTime + hold1Time + exhaleTime - cyclePosition) + "s)");
                phaseLabel.setForeground(new Color(100, 255, 200));
            } else {
                currentPhase = "Hold Empty";
                phaseLabel.setText(currentPhase + " (" + (int)(totalCycle - cyclePosition) + "s)");
                phaseLabel.setForeground(new Color(200, 150, 255));
            }
        });
        phaseTimer.start();
    }
    
    private void stopPhaseTimer() {
        if (phaseTimer != null) {
            phaseTimer.stop();
            phaseTimer = null;
        }
    }
    
    private void reconfigure() {
        breathingWidget.stop();
        stopPhaseTimer();
        
        // Show configuration dialog
        BreathingConfigDialog dialog = new BreathingConfigDialog((JFrame) getOwner());
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            startExercise(
                dialog.getInhaleTime(),
                dialog.getHold1Time(),
                dialog.getExhaleTime(),
                dialog.getHold2Time(),
                dialog.getOpacityValue(),
                dialog.getSizeValue(),
                dialog.getColor()
            );
        } else {
            // Resume if not reconfigured
            breathingWidget.start();
            startPhaseTimer();
        }
    }
}