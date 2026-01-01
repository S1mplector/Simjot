/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;

/**
 * Modern color picker dialog with HSB sliders, preset colors, and hex input.
 * Meant to replace Java Swing's default color picker dialog, which looks outdated.
 */
public class ModernColorPickerDialog extends JDialog {
    private Color selectedColor;
    private boolean confirmed = false;
    
    private final JPanel colorPreview;
    private final JSlider hueSlider;
    private final JSlider satSlider;
    private final JSlider brightSlider;
    private final JTextField hexField;
    
    // Preset colors
    private static final Color[] PRESETS = {
        new Color(147, 112, 219), // Purple
        new Color(100, 149, 237), // Blue
        new Color(60, 179, 113),  // Green
        new Color(255, 193, 7),   // Amber
        new Color(220, 53, 69),   // Red
        new Color(255, 182, 193), // Pink
        new Color(0, 206, 209),   // Cyan
        new Color(169, 169, 169), // Gray
        new Color(139, 69, 19),   // Brown
        new Color(255, 140, 0),   // Orange
        new Color(75, 0, 130),    // Indigo
        new Color(0, 128, 128),   // Teal
    };
    
    public ModernColorPickerDialog(Component parent, Color initialColor) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Choose Color", ModalityType.APPLICATION_MODAL);
        this.selectedColor = initialColor != null ? initialColor : Color.BLUE;
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        
        FrostedGlassPanel mainPanel = new FrostedGlassPanel(new BorderLayout(12, 12), 16);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel title = new JLabel("Choose Color", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        title.setForeground(Color.DARK_GRAY);
        mainPanel.add(title, BorderLayout.NORTH);
        
        // Center content
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        
        // Color preview
        colorPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(selectedColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 12, 12));
                g2.setColor(Color.GRAY);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 12, 12));
                g2.dispose();
            }
        };
        colorPreview.setPreferredSize(new Dimension(200, 60));
        colorPreview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        colorPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(colorPreview);
        center.add(Box.createVerticalStrut(16));
        
        // Preset colors grid
        JPanel presetsPanel = new JPanel(new GridLayout(2, 6, 8, 8));
        presetsPanel.setOpaque(false);
        presetsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (Color c : PRESETS) {
            presetsPanel.add(createPresetSwatch(c));
        }
        center.add(presetsPanel);
        center.add(Box.createVerticalStrut(16));
        
        // HSB Sliders
        float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
        
        JPanel slidersPanel = new JPanel();
        slidersPanel.setOpaque(false);
        slidersPanel.setLayout(new BoxLayout(slidersPanel, BoxLayout.Y_AXIS));
        
        // Hue slider with rainbow gradient
        JPanel hueRow = createSliderRow("Hue:", 0, 360, (int)(hsb[0] * 360));
        hueSlider = (JSlider) ((JPanel)hueRow.getComponent(1)).getComponent(0);
        hueSlider.addChangeListener(e -> updateFromSliders());
        slidersPanel.add(hueRow);
        slidersPanel.add(Box.createVerticalStrut(8));
        
        // Saturation slider
        JPanel satRow = createSliderRow("Saturation:", 0, 100, (int)(hsb[1] * 100));
        satSlider = (JSlider) ((JPanel)satRow.getComponent(1)).getComponent(0);
        satSlider.addChangeListener(e -> updateFromSliders());
        slidersPanel.add(satRow);
        slidersPanel.add(Box.createVerticalStrut(8));
        
        // Brightness slider
        JPanel brightRow = createSliderRow("Brightness:", 0, 100, (int)(hsb[2] * 100));
        brightSlider = (JSlider) ((JPanel)brightRow.getComponent(1)).getComponent(0);
        brightSlider.addChangeListener(e -> updateFromSliders());
        slidersPanel.add(brightRow);
        
        center.add(slidersPanel);
        center.add(Box.createVerticalStrut(12));
        
        // Hex input
        JPanel hexRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hexRow.setOpaque(false);
        hexRow.add(new JLabel("Hex:"));
        hexField = new JTextField(String.format("#%06X", selectedColor.getRGB() & 0xFFFFFF), 8);
        hexField.setHorizontalAlignment(JTextField.CENTER);
        hexField.addActionListener(e -> updateFromHex());
        hexRow.add(hexField);
        center.add(hexRow);
        
        mainPanel.add(center, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 8));
        buttons.setOpaque(false);
        
        IconMenuButton okBtn = new IconMenuButton("OK", "save");
        okBtn.setToolTipText("Confirm color");
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        
        IconMenuButton cancelBtn = new IconMenuButton("Cancel", "close");
        cancelBtn.setToolTipText("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        mainPanel.add(buttons, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        pack();
        setSize(340, 420);
        setLocationRelativeTo(parent);
    }
    
    private JPanel createSliderRow(String label, int min, int max, int value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(80, 20));
        row.add(lbl, BorderLayout.WEST);
        
        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.setOpaque(false);
        JSlider slider = new JSlider(min, max, value);
        slider.setOpaque(false);
        sliderPanel.add(slider, BorderLayout.CENTER);
        row.add(sliderPanel, BorderLayout.CENTER);
        
        JLabel valLabel = new JLabel(String.valueOf(value));
        valLabel.setPreferredSize(new Dimension(35, 20));
        slider.addChangeListener(e -> valLabel.setText(String.valueOf(slider.getValue())));
        row.add(valLabel, BorderLayout.EAST);
        
        return row;
    }
    
    private JPanel createPresetSwatch(Color c) {
        JPanel swatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 6, 6);
                if (colorsEqual(c, selectedColor)) {
                    g2.setColor(Color.DARK_GRAY);
                    g2.setStroke(new java.awt.BasicStroke(2));
                    g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 6, 6);
                }
                g2.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(32, 32));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectedColor = c;
                updateUI();
            }
        });
        return swatch;
    }
    
    private boolean colorsEqual(Color a, Color b) {
        return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
    }
    
    private void updateFromSliders() {
        float h = hueSlider.getValue() / 360f;
        float s = satSlider.getValue() / 100f;
        float b = brightSlider.getValue() / 100f;
        selectedColor = Color.getHSBColor(h, s, b);
        hexField.setText(String.format("#%06X", selectedColor.getRGB() & 0xFFFFFF));
        colorPreview.repaint();
        repaint();
    }
    
    private void updateFromHex() {
        try {
            String hex = hexField.getText().trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            int rgb = Integer.parseInt(hex, 16);
            selectedColor = new Color(rgb);
            float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
            hueSlider.setValue((int)(hsb[0] * 360));
            satSlider.setValue((int)(hsb[1] * 100));
            brightSlider.setValue((int)(hsb[2] * 100));
            colorPreview.repaint();
            repaint();
        } catch (NumberFormatException ignored) {}
    }
    
    private void updateUI() {
        float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
        hueSlider.setValue((int)(hsb[0] * 360));
        satSlider.setValue((int)(hsb[1] * 100));
        brightSlider.setValue((int)(hsb[2] * 100));
        hexField.setText(String.format("#%06X", selectedColor.getRGB() & 0xFFFFFF));
        colorPreview.repaint();
        repaint();
    }
    
    public Color getSelectedColor() {
        return selectedColor;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Show the color picker dialog and return the selected color, or null if cancelled.
     */
    public static Color showDialog(Component parent, String title, Color initialColor) {
        ModernColorPickerDialog dialog = new ModernColorPickerDialog(parent, initialColor);
        dialog.setVisible(true);
        return dialog.isConfirmed() ? dialog.getSelectedColor() : null;
    }
}
