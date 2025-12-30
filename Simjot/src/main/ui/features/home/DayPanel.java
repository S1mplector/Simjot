/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

@SuppressWarnings("serial")
public class DayPanel extends JPanel {
    private List<JPanel> sectionPanels;  // We keep references to each section's panel
    private JPanel sectionsContainer;    // Where new sections get added
    
    @SuppressWarnings("unused")
	public DayPanel(String dateString) {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        
        // Top label with day/date
        JLabel dateLabel = new JLabel(dateString, SwingConstants.CENTER);
        dateLabel.setForeground(Color.WHITE);
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 16f));
        add(dateLabel, BorderLayout.NORTH);

        // A vertical box to hold all sections
        sectionsContainer = new JPanel();
        sectionsContainer.setLayout(new BoxLayout(sectionsContainer, BoxLayout.Y_AXIS));
        sectionsContainer.setOpaque(false);  // transparent so background can show
        sectionPanels = new ArrayList<>();
        
        // Scroll pane for the sections
        JScrollPane scrollPane = new JScrollPane(sectionsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        // "Add Section" button at bottom
        JButton addSectionButton = new JButton("Add Section");
        addSectionButton.setBackground(Color.DARK_GRAY);
        addSectionButton.setForeground(Color.WHITE);
        addSectionButton.addActionListener(e -> addNewSection());
        add(addSectionButton, BorderLayout.SOUTH);
    }
    
    private void addNewSection() {
        // Prompt user for a section title
        String sectionTitle = JOptionPane.showInputDialog(
            this, 
            "Enter Section Title:", 
            "New Section", 
            JOptionPane.PLAIN_MESSAGE
        );
        if (sectionTitle == null || sectionTitle.trim().isEmpty()) {
            return;
        }
        
        // Create the new section panel
        JPanel sectionPanel = new JPanel(new BorderLayout());
        sectionPanel.setBackground(Color.BLACK);
        
        JLabel titleLabel = new JLabel(sectionTitle);
        titleLabel.setForeground(Color.WHITE);
        sectionPanel.add(titleLabel, BorderLayout.NORTH);
        
        // A text area where user can freely type tasks
        JTextArea taskArea = new JTextArea(3, 20);
        taskArea.setLineWrap(true);
        taskArea.setWrapStyleWord(true);
        taskArea.setBackground(Color.DARK_GRAY);
        taskArea.setForeground(Color.WHITE);
        
        sectionPanel.add(new JScrollPane(taskArea), BorderLayout.CENTER);
        
        // Some spacing after each section
        sectionPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Add the new section to the container
        sectionsContainer.add(sectionPanel);
        sectionsContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        sectionPanels.add(sectionPanel);
        
        // Re‐layout
        sectionsContainer.revalidate();
        sectionsContainer.repaint();
    }
}
