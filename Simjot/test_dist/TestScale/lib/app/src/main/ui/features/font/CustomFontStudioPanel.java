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
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import main.core.font.CustomFont;
import main.core.font.CustomGlyph;
import main.core.font.CustomStroke;
import main.infrastructure.font.CustomFontRegistry;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;

/**
 * Main panel for the custom font studio - combines all font editing components.
 */
public class CustomFontStudioPanel extends JPanel {
    
    private final CustomFontRegistry registry;
    private CustomFont currentFont;
    
    private FontLibraryPanel libraryPanel;
    private GlyphNavigatorPanel navigatorPanel;
    private GlyphCanvas glyphCanvas;
    private FontPreviewPanel previewPanel;
    
    private JLabel currentGlyphLabel;
    private JSlider thicknessSlider;
    private JSpinner thicknessSpinner;
    private JCheckBox smoothingCheckbox;
    private boolean updatingThickness;
    
    public CustomFontStudioPanel(Path fontsDirectory) {
        this.registry = new CustomFontRegistry(fontsDirectory);
        
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(new Color(240, 240, 245));
        
        // Left panel: Library + Navigator
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(280, 0));
        
        libraryPanel = new FontLibraryPanel(registry);
        libraryPanel.setPreferredSize(new Dimension(280, 200));
        libraryPanel.addListener(new FontLibraryPanel.FontLibraryListener() {
            @Override
            public void onFontSelected(String fontName) {
                loadFont(fontName);
            }
            @Override
            public void onFontCreated(CustomFont font) {
                setCurrentFont(font);
            }
            @Override
            public void onFontDeleted(String fontName) {
                if (currentFont != null && currentFont.getName().equals(fontName)) {
                    currentFont = null;
                    refreshUI();
                }
            }
            @Override
            public void onFontRenamed(String oldName, String newName) {
                if (currentFont != null && currentFont.getName().equals(oldName)) {
                    currentFont.setName(newName);
                }
            }
        });
        leftPanel.add(libraryPanel, BorderLayout.NORTH);
        
        navigatorPanel = new GlyphNavigatorPanel();
        navigatorPanel.addListener(codepoint -> selectGlyph(codepoint));
        leftPanel.add(navigatorPanel, BorderLayout.CENTER);
        
        add(leftPanel, BorderLayout.WEST);
        
        // Center panel: Canvas + Controls
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        
        // Canvas header
        JPanel canvasHeader = new JPanel(new BorderLayout());
        canvasHeader.setOpaque(false);
        
        currentGlyphLabel = new JLabel("Select a glyph");
        currentGlyphLabel.setFont(currentGlyphLabel.getFont().deriveFont(Font.BOLD, 16f));
        canvasHeader.add(currentGlyphLabel, BorderLayout.WEST);
        
        // Canvas controls
        JPanel canvasControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        canvasControls.setOpaque(false);
        
        RoundedButton undoButton = new RoundedButton("Undo");
        undoButton.setPreferredSize(new Dimension(90, 26));
        undoButton.addActionListener(e -> glyphCanvas.undoLastStroke());
        canvasControls.add(undoButton);
        
        RoundedButton clearButton = new RoundedButton("Clear");
        clearButton.setPreferredSize(new Dimension(90, 26));
        clearButton.addActionListener(e -> glyphCanvas.clearGlyph());
        canvasControls.add(clearButton);
        
        canvasHeader.add(canvasControls, BorderLayout.EAST);
        centerPanel.add(canvasHeader, BorderLayout.NORTH);
        
        // Canvas
        glyphCanvas = new GlyphCanvas();
        glyphCanvas.addListener(new GlyphCanvas.GlyphCanvasListener() {
            @Override
            public void onStrokeStarted() {}
            @Override
            public void onStrokeEnded(CustomStroke stroke) {}
            @Override
            public void onGlyphModified(CustomGlyph glyph) {
                saveCurrentFont();
                previewPanel.refresh();
                navigatorPanel.refresh();
            }
        });
        centerPanel.add(glyphCanvas, BorderLayout.CENTER);
        
        // Thickness and smoothing controls
        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        toolPanel.setOpaque(false);
        
        toolPanel.add(new JLabel("Thickness:"));
        thicknessSlider = new JSlider(1, 40, 3);
        thicknessSlider.setOpaque(false);
        thicknessSlider.setPreferredSize(new Dimension(160, 24));
        thicknessSlider.addChangeListener(e -> {
            if (updatingThickness) return;
            updatingThickness = true;
            float value = thicknessSlider.getValue();
            thicknessSpinner.setValue((double) value);
            applyThickness(value);
            updatingThickness = false;
        });
        toolPanel.add(thicknessSlider);

        thicknessSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.5, 40.0, 0.5));
        thicknessSpinner.setUI(new ModernSpinnerUI());
        ((JSpinner.NumberEditor) thicknessSpinner.getEditor()).getTextField().setColumns(4);
        thicknessSpinner.addChangeListener(e -> {
            if (updatingThickness) return;
            updatingThickness = true;
            double value = ((Number) thicknessSpinner.getValue()).doubleValue();
            thicknessSlider.setValue((int) Math.round(value));
            applyThickness((float) value);
            updatingThickness = false;
        });
        toolPanel.add(thicknessSpinner);
        
        smoothingCheckbox = new JCheckBox("Smoothing", true);
        smoothingCheckbox.setUI(new ModernCheckBoxUI());
        smoothingCheckbox.setOpaque(false);
        smoothingCheckbox.setBackground(new Color(0, 0, 0, 0));
        smoothingCheckbox.addActionListener(e -> {
            glyphCanvas.setSmoothing(smoothingCheckbox.isSelected());
        });
        toolPanel.add(smoothingCheckbox);
        
        JCheckBox gridCheckbox = new JCheckBox("Grid", true);
        gridCheckbox.setUI(new ModernCheckBoxUI());
        gridCheckbox.setOpaque(false);
        gridCheckbox.setBackground(new Color(0, 0, 0, 0));
        gridCheckbox.addActionListener(e -> glyphCanvas.setShowGrid(gridCheckbox.isSelected()));
        toolPanel.add(gridCheckbox);
        
        JCheckBox baselinesCheckbox = new JCheckBox("Baselines", true);
        baselinesCheckbox.setUI(new ModernCheckBoxUI());
        baselinesCheckbox.setOpaque(false);
        baselinesCheckbox.setBackground(new Color(0, 0, 0, 0));
        baselinesCheckbox.addActionListener(e -> glyphCanvas.setShowBaselines(baselinesCheckbox.isSelected()));
        toolPanel.add(baselinesCheckbox);
        
        centerPanel.add(toolPanel, BorderLayout.SOUTH);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // Right panel: Preview
        previewPanel = new FontPreviewPanel();
        previewPanel.setPreferredSize(new Dimension(320, 0));
        add(previewPanel, BorderLayout.EAST);
        
        // Initialize
        refreshUI();
    }
    
    public CustomFontRegistry getRegistry() {
        return registry;
    }
    
    public CustomFont getCurrentFont() {
        return currentFont;
    }
    
    public void setCurrentFont(CustomFont font) {
        this.currentFont = font;
        navigatorPanel.setFont(font);
        previewPanel.setCustomFont(font);
        glyphCanvas.setEmSize(font != null ? font.getEmSize() : 1000.0f);
        if (font != null) {
            syncThickness(font.getDefaultThickness());
        }
        refreshUI();
    }
    
    private void loadFont(String fontName) {
        CustomFont font = registry.loadFont(fontName);
        if (font != null) {
            setCurrentFont(font);
        }
    }
    
    private void selectGlyph(int codepoint) {
        if (currentFont == null) return;
        
        CustomGlyph glyph = currentFont.getOrCreateGlyph(codepoint);
        glyphCanvas.setGlyph(glyph);
        navigatorPanel.setSelectedCodepoint(codepoint);
        
        String display = codepoint >= 32 && codepoint < 127 ? 
            "'" + (char) codepoint + "'" : String.format("U+%04X", codepoint);
        currentGlyphLabel.setText("Editing: " + display);
    }
    
    private void saveCurrentFont() {
        if (currentFont != null) {
            registry.saveFont(currentFont);
        }
    }
    
    private void refreshUI() {
        if (currentFont == null) {
            currentGlyphLabel.setText("Select a font and glyph");
            glyphCanvas.setGlyph(null);
        }
        repaint();
    }

    private void syncThickness(float value) {
        updatingThickness = true;
        float clamped = Math.max(0.5f, Math.min(40.0f, value));
        thicknessSlider.setValue((int) Math.round(clamped));
        thicknessSpinner.setValue((double) clamped);
        applyThickness(clamped);
        updatingThickness = false;
    }

    private void applyThickness(float value) {
        glyphCanvas.setStrokeThickness(value);
        if (currentFont != null) {
            currentFont.setDefaultThickness(value);
            registry.saveFont(currentFont);
        }
    }
    
    public void refresh() {
        libraryPanel.refreshFontList();
        navigatorPanel.refresh();
        previewPanel.refresh();
    }
}
