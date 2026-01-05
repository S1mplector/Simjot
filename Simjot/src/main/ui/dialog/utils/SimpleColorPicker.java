/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.utils;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;

/**
 * A simple, minimalist color picker dialog.
 * Provides a clean color palette with brightness variations and recent colors.
 */
public class SimpleColorPicker extends JDialog {
    
    private static final Deque<Color> recentColors = new ArrayDeque<>();
    private static final int MAX_RECENT = 8;
    
    private Color selectedColor;
    private Color initialColor;
    private boolean confirmed = false;
    
    private JPanel previewPanel;
    private JPanel recentPanel;
    
    // Base hues for the palette
    private static final Color[] BASE_COLORS = {
        new Color(244, 67, 54),   // Red
        new Color(233, 30, 99),   // Pink
        new Color(156, 39, 176),  // Purple
        new Color(103, 58, 183),  // Deep Purple
        new Color(63, 81, 181),   // Indigo
        new Color(33, 150, 243),  // Blue
        new Color(3, 169, 244),   // Light Blue
        new Color(0, 188, 212),   // Cyan
        new Color(0, 150, 136),   // Teal
        new Color(76, 175, 80),   // Green
        new Color(139, 195, 74),  // Light Green
        new Color(205, 220, 57),  // Lime
        new Color(255, 235, 59),  // Yellow
        new Color(255, 193, 7),   // Amber
        new Color(255, 152, 0),   // Orange
        new Color(255, 87, 34),   // Deep Orange
    };
    
    public SimpleColorPicker(Component parent, Color initial) {
        super(parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent),
              "Pick Color", ModalityType.APPLICATION_MODAL);
        this.initialColor = initial != null ? initial : Color.BLACK;
        this.selectedColor = this.initialColor;
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        
        initUI();
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    private void initUI() {
        FrostedGlassPanel content = new FrostedGlassPanel(new BorderLayout(12, 12), 18);
        content.setBorder(new EmptyBorder(20, 20, 16, 20));
        
        // Title
        JLabel titleLabel = new JLabel("Pick Color");
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titleLabel.setForeground(new Color(50, 55, 65));
        titleLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        content.add(titleLabel, BorderLayout.NORTH);
        
        // Main palette area
        JPanel paletteArea = new JPanel(new BorderLayout(12, 0));
        paletteArea.setOpaque(false);
        
        // Color grid
        JPanel colorGrid = createColorGrid();
        paletteArea.add(colorGrid, BorderLayout.CENTER);
        
        // Right side: preview + recent
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        
        // Preview
        JLabel previewLabel = new JLabel("Preview");
        previewLabel.setFont(previewLabel.getFont().deriveFont(11f));
        previewLabel.setForeground(new Color(100, 100, 100));
        previewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(previewLabel);
        rightPanel.add(Box.createVerticalStrut(4));
        
        previewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                // Checkerboard for transparency
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(200, 200, 200));
                int sq = 6;
                for (int y = 0; y < h; y += sq) {
                    for (int x = 0; x < w; x += sq) {
                        if ((x / sq + y / sq) % 2 == 0) {
                            g2.fillRect(x, y, sq, sq);
                        }
                    }
                }
                // Selected color
                g2.setColor(selectedColor);
                g2.fillRect(0, 0, w, h);
                // Border
                g2.setColor(new Color(180, 180, 180));
                g2.drawRect(0, 0, w - 1, h - 1);
                g2.dispose();
            }
        };
        previewPanel.setPreferredSize(new Dimension(64, 64));
        previewPanel.setMaximumSize(new Dimension(64, 64));
        previewPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(previewPanel);
        
        rightPanel.add(Box.createVerticalStrut(16));
        
        // Recent colors
        JLabel recentLabel = new JLabel("Recent");
        recentLabel.setFont(recentLabel.getFont().deriveFont(11f));
        recentLabel.setForeground(new Color(100, 100, 100));
        recentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(recentLabel);
        rightPanel.add(Box.createVerticalStrut(4));
        
        recentPanel = new JPanel(new GridLayout(2, 4, 2, 2));
        recentPanel.setOpaque(false);
        recentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentPanel.setMaximumSize(new Dimension(80, 40));
        updateRecentPanel();
        rightPanel.add(recentPanel);
        
        paletteArea.add(rightPanel, BorderLayout.EAST);
        content.add(paletteArea, BorderLayout.CENTER);
        
        // Grayscale row at bottom
        JPanel grayscaleRow = createGrayscaleRow();
        content.add(grayscaleRow, BorderLayout.SOUTH);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        
        IconMenuButton cancelBtn = new IconMenuButton("Cancel", "close");
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        IconMenuButton okBtn = new IconMenuButton("OK", "check");
        okBtn.setPreferredSize(new Dimension(80, 36));
        okBtn.addActionListener(e -> {
            confirmed = true;
            addToRecent(selectedColor);
            dispose();
        });
        
        buttonPanel.add(cancelBtn);
        buttonPanel.add(okBtn);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(16, 0, 0, 0));
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        // Combine grayscale and buttons in south area
        JPanel southArea = new JPanel(new BorderLayout(0, 8));
        southArea.setOpaque(false);
        southArea.add(grayscaleRow, BorderLayout.NORTH);
        southArea.add(bottomPanel, BorderLayout.SOUTH);
        content.add(southArea, BorderLayout.SOUTH);
        
        setContentPane(content);
        
        // ESC to cancel
        getRootPane().registerKeyboardAction(e -> { confirmed = false; dispose(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    
    private JPanel createColorGrid() {
        JPanel grid = new JPanel(new GridLayout(6, BASE_COLORS.length, 2, 2));
        grid.setOpaque(false);
        
        // Generate brightness variations for each base color
        float[] brightnessLevels = {1.0f, 0.85f, 0.7f, 0.55f, 0.4f, 0.25f};
        
        for (float brightness : brightnessLevels) {
            for (Color base : BASE_COLORS) {
                float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
                Color shade = Color.getHSBColor(hsb[0], hsb[1], hsb[2] * brightness);
                grid.add(createSwatch(shade));
            }
        }
        
        return grid;
    }
    
    private JPanel createGrayscaleRow() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));
        
        JPanel grayscale = new JPanel(new GridLayout(1, 12, 2, 0));
        grayscale.setOpaque(false);
        
        // Generate grayscale from white to black
        for (int i = 0; i < 12; i++) {
            int gray = 255 - (i * 255 / 11);
            grayscale.add(createSwatch(new Color(gray, gray, gray)));
        }
        
        panel.add(grayscale, BorderLayout.CENTER);
        return panel;
    }
    
    private JButton createSwatch(Color color) {
        JButton swatch = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                
                // Fill with color
                g2.setColor(color);
                g2.fillRect(0, 0, w, h);
                
                // Hover highlight
                if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 80));
                    g2.fillRect(0, 0, w, h);
                }
                
                // Selection indicator
                if (colorsEqual(color, selectedColor)) {
                    g2.setColor(isDark(color) ? Color.WHITE : Color.BLACK);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(2, 2, w - 5, h - 5);
                }
                
                // Border
                g2.setColor(new Color(0, 0, 0, 40));
                g2.drawRect(0, 0, w - 1, h - 1);
                
                g2.dispose();
            }
        };
        
        swatch.setPreferredSize(new Dimension(20, 20));
        swatch.setBorderPainted(false);
        swatch.setContentAreaFilled(false);
        swatch.setFocusPainted(false);
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
        
        swatch.addActionListener(e -> {
            selectedColor = color;
            previewPanel.repaint();
            // Repaint all swatches to update selection indicator
            Container parent = swatch.getParent();
            if (parent != null) parent.repaint();
        });
        
        return swatch;
    }
    
    private void updateRecentPanel() {
        recentPanel.removeAll();
        
        int count = 0;
        for (Color c : recentColors) {
            if (count >= 8) break;
            recentPanel.add(createSmallSwatch(c));
            count++;
        }
        
        // Fill remaining slots with empty swatches
        while (count < 8) {
            JPanel empty = new JPanel();
            empty.setPreferredSize(new Dimension(16, 16));
            empty.setBackground(new Color(230, 230, 230));
            empty.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            recentPanel.add(empty);
            count++;
        }
        
        recentPanel.revalidate();
        recentPanel.repaint();
    }
    
    private JButton createSmallSwatch(Color color) {
        JButton swatch = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(0, 0, 0, 60));
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        
        swatch.setPreferredSize(new Dimension(16, 16));
        swatch.setBorderPainted(false);
        swatch.setContentAreaFilled(false);
        swatch.setFocusPainted(false);
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        swatch.addActionListener(e -> {
            selectedColor = color;
            previewPanel.repaint();
        });
        
        return swatch;
    }
    
    private static void addToRecent(Color color) {
        // Remove if already exists
        recentColors.removeIf(c -> colorsEqual(c, color));
        // Add to front
        recentColors.addFirst(color);
        // Trim to max size
        while (recentColors.size() > MAX_RECENT) {
            recentColors.removeLast();
        }
    }
    
    private static boolean colorsEqual(Color a, Color b) {
        if (a == null || b == null) return false;
        return a.getRed() == b.getRed() && 
               a.getGreen() == b.getGreen() && 
               a.getBlue() == b.getBlue();
    }
    
    private static boolean isDark(Color c) {
        double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return luminance < 128;
    }
    
    public Color getSelectedColor() {
        return selectedColor;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Show the color picker dialog and return the selected color.
     * @param parent Parent component for positioning
     * @param title Dialog title (optional, uses default if null)
     * @param initialColor Initial color selection
     * @return Selected color, or null if cancelled
     */
    public static Color showDialog(Component parent, String title, Color initialColor) {
        SimpleColorPicker picker = new SimpleColorPicker(parent, initialColor);
        if (title != null && !title.isEmpty()) {
            picker.setTitle(title);
        }
        picker.setVisible(true);
        return picker.isConfirmed() ? picker.getSelectedColor() : null;
    }
}
