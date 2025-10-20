package main.ui.features.settings;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.ui.theme.aero.AeroTheme;

public class AboutSettingsPage extends JPanel implements SettingsPage {

    public AboutSettingsPage() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Color.WHITE);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // Title with version
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("About Simjot");
        titleLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);

        JLabel versionLabel = new JLabel("v1.0.0");
        versionLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 18f));
        versionLabel.setForeground(new Color(100, 100, 100));

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createHorizontalStrut(10));
        titlePanel.add(versionLabel);

        contentPanel.add(titlePanel);

        contentPanel.add(Box.createVerticalStrut(30));

        // Version section
        JPanel versionPanel = createSectionPanel("Version Information");
        addInfoRow(versionPanel, "Application Version:", "1.0.0");
        addInfoRow(versionPanel, "Java Version:", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        addInfoRow(versionPanel, "Java Runtime:", System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version"));
        contentPanel.add(versionPanel);

        contentPanel.add(Box.createVerticalStrut(25));

        // Capabilities section
        JPanel capabilitiesPanel = createSectionPanel("Current Capabilities");
        JTextArea capabilitiesArea = new JTextArea(getCapabilitiesText());
        capabilitiesArea.setFont(AeroTheme.defaultFont().deriveFont(12f));
        capabilitiesArea.setForeground(AeroTheme.TEXT_PRIMARY);
        capabilitiesArea.setOpaque(false);
        capabilitiesArea.setEditable(false);
        capabilitiesArea.setWrapStyleWord(true);
        capabilitiesArea.setLineWrap(true);
        capabilitiesArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        capabilitiesPanel.add(capabilitiesArea, BorderLayout.CENTER);
        contentPanel.add(capabilitiesPanel);

        contentPanel.add(Box.createVerticalStrut(25));

        // Developer section
        JPanel developerPanel = createSectionPanel("Developer Information");
        addInfoRow(developerPanel, "Created by:", "Ilgaz Mehmetoglu");
        addInfoRow(developerPanel, "Development Status:", "Active");
        addInfoRow(developerPanel, "License:", "MIT License");
        contentPanel.add(developerPanel);

        contentPanel.add(Box.createVerticalStrut(25));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(10, 15, 15, 15));
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void addInfoRow(JPanel parent, String label, String value) {
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        rowPanel.setOpaque(false);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 12f));
        labelComp.setForeground(AeroTheme.TEXT_PRIMARY);
        labelComp.setPreferredSize(new Dimension(120, 20));

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(AeroTheme.defaultFont().deriveFont(12f));
        valueComp.setForeground(AeroTheme.TEXT_PRIMARY);

        rowPanel.add(labelComp);
        rowPanel.add(valueComp);

        parent.add(rowPanel);
    }

    private String getCapabilitiesText() {
        return """
            • Journal Management - Create and organize multiple journals with different types (Journal, Poetry)
            • Rich Text Editing - Full-featured editor with formatting, images, and styling options
            • Entry Management - Create, edit, and delete journal entries with automatic saving
            • Notebook Organization - Visual notebook manager with customizable covers and metadata
            • Drawing Integration - Built-in drawing canvas for sketches and artwork
            • Gallery View - Visual gallery to browse and manage journal entries
            • Mood Tracking - Mood selection and visualization for journal entries
            • Settings Management - Comprehensive settings for appearance, storage, and preferences
            • AI Companion (Sim) - Optional AI assistant for journaling assistance
            • Data Persistence - Local storage with backup and restore capabilities
            • Modern UI - Aero-styled interface with smooth animations and transitions
            • Cross-platform - Runs on Windows, macOS, and Linux systems
            • Export Options - Export journals to various formats
            • Search & Filter - Find entries across all notebooks
            • Auto-save - Automatic saving to prevent data loss
            """;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void apply() {
        // No settings to apply in About page
    }
}
