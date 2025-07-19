package main.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

@SuppressWarnings("serial")
public class RamMonitor extends JPanel {
    private JLabel ramLabel;
    private Timer timer;

    public RamMonitor() {
        // Set a preferred size so it has room to display the text.
        setPreferredSize(new Dimension(150, 20));
        setOpaque(false);
        setBackground(new Color(0,0,0,0));
        
        ramLabel = new JLabel("RAM: 0 MB");
        ramLabel.setForeground(Color.WHITE);
        ramLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(ramLabel);
        
        // Update every second (1000 ms)
        timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long usedMB = usedMemory / (1024 * 1024);
                ramLabel.setText("RAM: " + usedMB + " MB");
            }
        });
        timer.start();
    }
}
