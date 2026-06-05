/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.font;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import main.core.font.CustomFont;
import main.infrastructure.font.CustomFontRenderer;
import main.infrastructure.font.NativeFontSupport;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.scrollbar.ModernScrollBarUI;

/**
 * Panel for previewing custom font rendering with sample text.
 */
public class FontPreviewPanel extends JPanel {
    
    private static final String DEFAULT_SAMPLE = "The quick brown fox jumps over the lazy dog. 0123456789";
    private static final int[] PREVIEW_SIZES = { 12, 16, 24, 32, 48 };
    
    private CustomFont font;
    private final CustomFontRenderer renderer;
    private String sampleText = DEFAULT_SAMPLE;
    private int previewSize = 24;
    private Color textColor = Color.BLACK;
    
    private JTextArea sampleInput;
    private JComboBox<Integer> sizeCombo;
    private JPanel previewArea;
    
    public FontPreviewPanel() {
        this.renderer = new CustomFontRenderer();
        
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(248, 248, 250));
        
        // Header
        JLabel header = new JLabel("Preview");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);
        
        // Controls panel
        JPanel controlsPanel = new JPanel(new BorderLayout(8, 4));
        controlsPanel.setOpaque(false);
        
        // Sample text input
        sampleInput = new JTextArea(sampleText, 2, 30);
        sampleInput.setLineWrap(true);
        sampleInput.setWrapStyleWord(true);
        sampleInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 205)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        sampleInput.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSample(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSample(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSample(); }
        });
        JScrollPane sampleScroll = new JScrollPane(sampleInput);
        sampleScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sampleScroll.getVerticalScrollBar().setUnitIncrement(16);
        sampleScroll.setBorder(BorderFactory.createEmptyBorder());
        controlsPanel.add(sampleScroll, BorderLayout.CENTER);
        
        // Size selector
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizePanel.setOpaque(false);
        sizePanel.add(new JLabel("Size:"));
        
        sizeCombo = new JComboBox<>();
        for (int size : PREVIEW_SIZES) {
            sizeCombo.addItem(size);
        }
        sizeCombo.setSelectedItem(previewSize);
        sizeCombo.setUI(new ModernComboBoxUI());
        sizeCombo.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        sizeCombo.addActionListener(e -> {
            previewSize = (Integer) sizeCombo.getSelectedItem();
            repaintPreview();
        });
        sizePanel.add(sizeCombo);
        
        controlsPanel.add(sizePanel, BorderLayout.SOUTH);
        add(controlsPanel, BorderLayout.NORTH);
        
        // Preview area
        previewArea = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintPreview((Graphics2D) g);
            }
        };
        previewArea.setBackground(Color.WHITE);
        previewArea.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 215)));
        previewArea.setPreferredSize(new Dimension(400, 150));
        
        add(previewArea, BorderLayout.CENTER);
    }
    
    public void setCustomFont(CustomFont font) {
        this.font = font;
        renderer.clearCache();
        repaintPreview();
    }
    
    public CustomFont getCustomFont() {
        return font;
    }
    
    public void setSampleText(String text) {
        this.sampleText = text != null ? text : DEFAULT_SAMPLE;
        sampleInput.setText(this.sampleText);
        repaintPreview();
    }
    
    public void setPreviewSize(int size) {
        this.previewSize = size;
        sizeCombo.setSelectedItem(size);
        repaintPreview();
    }
    
    public void setTextColor(Color color) {
        this.textColor = color != null ? color : Color.BLACK;
        repaintPreview();
    }
    
    public void refresh() {
        renderer.clearCache();
        repaintPreview();
    }
    
    private void updateSample() {
        sampleText = sampleInput.getText();
        repaintPreview();
    }
    
    private void repaintPreview() {
        previewArea.repaint();
    }
    
    private void paintPreview(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = previewArea.getWidth();
        int h = previewArea.getHeight();
        
        if (font == null || sampleText == null || sampleText.isEmpty()) {
            g2.setColor(new Color(150, 150, 150));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 14));
            String msg = font == null ? "No font selected" : "Enter sample text";
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(msg);
            g2.drawString(msg, (w - textW) / 2, h / 2);
            return;
        }
        
        // Draw with custom font renderer
        int x = 10;
        Float nativeAscender = NativeFontSupport.getAscender(font, previewSize);
        float ascender = nativeAscender != null ? nativeAscender : font.getAscender(previewSize);
        int y = (int) ascender + 10;
        Float nativeLineHeight = NativeFontSupport.getLineHeight(font, previewSize);
        int lineHeight = (int) (nativeLineHeight != null ? nativeLineHeight : font.getLineHeight(previewSize));
        
        // Simple word wrap
        float maxWidth = w - 20;
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : sampleText.split("\\s+")) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            Float nativeWidth = NativeFontSupport.measureText(font, testLine, previewSize);
            float testWidth = nativeWidth != null ? nativeWidth : font.measureText(testLine, previewSize);
            
            if (testWidth > maxWidth && currentLine.length() > 0) {
                // Draw current line
                renderer.drawText(g2, font, currentLine.toString(), x, y, previewSize, textColor);
                y += lineHeight;
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
            
            if (y > h - 10) break;
        }
        
        // Draw remaining text
        if (currentLine.length() > 0 && y <= h - 10) {
            renderer.drawText(g2, font, currentLine.toString(), x, y, previewSize, textColor);
        }
    }
}
