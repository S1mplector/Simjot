/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.menubar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Quick entry dialog for the menu bar service.
 * Provides a simple text entry dialog that can be triggered from the system tray.
 */
public class QuickEntryDialog extends JDialog {
    
    private final MenuBarService menuBarService;
    private final JTextArea textArea;
    private final JButton submitButton;
    private final JButton cancelButton;
    
    public QuickEntryDialog(MenuBarService service) {
        super((Frame) null, "Simjot Quick Entry", false);
        this.menuBarService = service;
        
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setAlwaysOnTop(true);
        setResizable(true);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(245, 245, 245));
        
        // Title label
        JLabel titleLabel = new JLabel("Quick Journal Entry");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Text area with scroll pane
        textArea = new JTextArea(6, 35);
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> hideDialog());
        buttonPanel.add(cancelButton);
        
        submitButton = new JButton("Save Entry");
        submitButton.setBackground(new Color(0, 122, 255));
        submitButton.setForeground(Color.WHITE);
        submitButton.addActionListener(e -> submitEntry());
        buttonPanel.add(submitButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Keyboard shortcuts
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK), "submit");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "submit");
        textArea.getActionMap().put("submit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitEntry();
            }
        });
        
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        textArea.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideDialog();
            }
        });
        
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
        
        // Position near top of screen for quick access
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - getWidth()) / 2, 100);
    }
    
    public void showDialog() {
        textArea.setText("");
        textArea.requestFocusInWindow();
        setVisible(true);
        toFront();
    }
    
    public void hideDialog() {
        setVisible(false);
    }
    
    public String getText() {
        return textArea.getText();
    }
    
    public void setText(String text) {
        textArea.setText(text);
    }
    
    public void clear() {
        textArea.setText("");
    }
    
    private void submitEntry() {
        String text = textArea.getText();
        if (text != null && !text.isBlank()) {
            menuBarService.onEntrySubmitted(text.trim(), 0);
            textArea.setText("");
            hideDialog();
        }
    }
}
