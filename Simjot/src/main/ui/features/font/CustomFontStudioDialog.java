/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.font;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;

import main.core.font.CustomFont;
import main.ui.components.buttons.RoundedButton;

/**
 * Dialog wrapper for the custom font studio.
 * Provides a modal or non-modal window for font editing.
 */
public class CustomFontStudioDialog extends JDialog {
    
    private final CustomFontStudioPanel studioPanel;
    private boolean confirmed = false;
    
    public CustomFontStudioDialog(Window owner, Path fontsDirectory) {
        super(owner, "Custom Font Studio", ModalityType.APPLICATION_MODAL);
        
        studioPanel = new CustomFontStudioPanel(fontsDirectory);
        
        initUI();
        
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(owner);
        
        // Handle window close
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClose();
            }
        });
    }
    
    private void initUI() {
        setLayout(new BorderLayout());
        
        // Main content
        add(studioPanel, BorderLayout.CENTER);
        
        // Bottom button bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 215)),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        
        RoundedButton refreshButton = new RoundedButton("Refresh");
        refreshButton.setPreferredSize(new Dimension(120, 28));
        refreshButton.addActionListener(e -> studioPanel.refresh());
        buttonBar.add(refreshButton);
        
        RoundedButton closeButton = new RoundedButton("Close");
        closeButton.setPreferredSize(new Dimension(120, 28));
        closeButton.addActionListener(e -> handleClose());
        buttonBar.add(closeButton);
        
        add(buttonBar, BorderLayout.SOUTH);
    }
    
    private void handleClose() {
        // Auto-save is handled by the studio panel on each change
        confirmed = true;
        dispose();
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public CustomFontStudioPanel getStudioPanel() {
        return studioPanel;
    }
    
    public CustomFont getSelectedFont() {
        return studioPanel.getCurrentFont();
    }
    
    /**
     * Shows the dialog and returns the selected font name, or null if cancelled.
     */
    public static String showDialog(Window owner, Path fontsDirectory) {
        CustomFontStudioDialog dialog = new CustomFontStudioDialog(owner, fontsDirectory);
        dialog.setVisible(true);
        
        CustomFont font = dialog.getSelectedFont();
        return font != null ? font.getName() : null;
    }
    
    /**
     * Shows a non-modal font studio window.
     */
    public static CustomFontStudioDialog showWindow(Window owner, Path fontsDirectory) {
        CustomFontStudioDialog dialog = new CustomFontStudioDialog(owner, fontsDirectory);
        dialog.setModalityType(ModalityType.MODELESS);
        dialog.setVisible(true);
        return dialog;
    }
}
