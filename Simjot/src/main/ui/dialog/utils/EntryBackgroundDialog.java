/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.utils;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;

import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.features.gallery.WallpaperGalleryPanel;

public class EntryBackgroundDialog extends JDialog {

    private final JLabel previewLabel;
    private final JSlider opacitySlider;
    private String selectedImagePath = "";
    private BufferedImage selectedImage;
    private float currentOpacity = 0.3f;
    
    public EntryBackgroundDialog(Frame owner) {
        super(owner, "Entry Background Settings", true);
        setSize(500, 400);
        
        // Initialize with current settings
        SettingsStore settings = SettingsStore.get();
        selectedImagePath = settings.getEntryBackgroundImage();
        currentOpacity = settings.getEntryBackgroundOpacity();
        
        // Main content panel with increased padding
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        // Preview panel with increased size and better spacing
        JPanel previewPanel = new JPanel(new BorderLayout(10, 10));
        previewPanel.setOpaque(false);
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        previewLabel = new JLabel("No background selected", JLabel.CENTER);
        previewLabel.setPreferredSize(new Dimension(350, 250));
        previewLabel.setHorizontalAlignment(JLabel.CENTER);
        previewLabel.setVerticalAlignment(JLabel.CENTER);
        previewLabel.setOpaque(true);
        previewLabel.setBackground(Color.WHITE);
        previewLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        previewPanel.add(previewLabel, BorderLayout.CENTER);
        
        // Opacity control with better spacing
        JPanel opacityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        opacityPanel.setOpaque(false);
        opacityPanel.setBorder(BorderFactory.createTitledBorder("Background Opacity"));
        
        JLabel opacityLabel = new JLabel("Opacity:");
        opacitySlider = new JSlider(0, 100, (int)(currentOpacity * 100));
        opacitySlider.setPreferredSize(new Dimension(250, 30));
        
        // Add a label to show the current opacity percentage
        JLabel opacityValueLabel = new JLabel(String.format("%d%%", (int)(currentOpacity * 100)));
        opacityValueLabel.setPreferredSize(new Dimension(40, 20));
        
        // Update the preview in real-time when slider changes
        opacitySlider.addChangeListener(e -> {
            currentOpacity = opacitySlider.getValue() / 100f;
            opacityValueLabel.setText(String.format("%d%%", opacitySlider.getValue()));
            updatePreview(); // Update the preview with new opacity
        });
        
        // Add components to opacity panel with proper spacing
        opacityPanel.add(opacityLabel);
        opacityPanel.add(Box.createHorizontalStrut(10));
        opacityPanel.add(opacitySlider);
        opacityPanel.add(Box.createHorizontalStrut(10));
        opacityPanel.add(opacityValueLabel);
        
        // Button panel with improved spacing
        JPanel buttonPanel = new JPanel(new BorderLayout(15, 0));
        buttonPanel.setOpaque(false);
        
        // Centered buttons (IconMenuButton style)
        JPanel centerButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 8));
        centerButtonPanel.setOpaque(false);
        
        IconMenuButton galleryBtn = new IconMenuButton("Gallery", "gallery");
        galleryBtn.setToolTipText("Choose from gallery");
        galleryBtn.addActionListener(e -> selectFromGallery());
        
        IconMenuButton removeBtn = new IconMenuButton("Remove", "trash");
        removeBtn.setToolTipText("Remove background");
        removeBtn.addActionListener(e -> removeBackground());
        
        IconMenuButton okBtn = new IconMenuButton("OK", "save");
        okBtn.setToolTipText("Apply changes");
        okBtn.addActionListener(e -> saveAndClose());
        
        IconMenuButton cancelBtn = new IconMenuButton("Cancel", "exit");
        cancelBtn.setToolTipText("Cancel changes");
        cancelBtn.addActionListener(e -> dispose());
        
        centerButtonPanel.add(galleryBtn);
        centerButtonPanel.add(removeBtn);
        centerButtonPanel.add(okBtn);
        centerButtonPanel.add(cancelBtn);
        
        // Add centered buttons
        buttonPanel.add(centerButtonPanel, BorderLayout.CENTER);
        
        // Add all sections to the content panel
        contentPanel.add(previewPanel, BorderLayout.CENTER);
        contentPanel.add(opacityPanel, BorderLayout.SOUTH);
        
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(), 16);
        root.add(contentPanel, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(root);
        
        // Ensure the dialog is properly sized
        pack();
        setMinimumSize(getSize());
        
        // Load initial preview
        if (!selectedImagePath.isEmpty()) {
            loadSelectedImage();
        } else {
            updatePreview();
        }
    }
    
    private void selectFromGallery() {
        // Use the existing WallpaperGalleryPanel to select an image, but don't auto-save to main menu background
        WallpaperGalleryPanel.WallpaperItem selected = WallpaperGalleryPanel.showWallpaperGallery(this, false);
        if (selected != null) {
            selectedImagePath = selected.getPath();
            // Convert Image to BufferedImage if needed
            Image img = selected.getImage();
            if (img != null) {
                if (img instanceof BufferedImage bufferedImage) {
                    selectedImage = bufferedImage;
                } else {
                    // Create a buffered image with transparency
                    BufferedImage bimage = new BufferedImage(
                        img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                    // Draw the image on to the buffered image
                    Graphics2D bGr = bimage.createGraphics();
                    bGr.drawImage(img, 0, 0, null);
                    bGr.dispose();
                    selectedImage = bimage;
                }
                updatePreview();
            }
        }
    }
    
    private void loadSelectedImage() {
        try {
            if (selectedImagePath.startsWith("res:")) {
                String resPath = selectedImagePath.substring(4);
                // Convert Image to BufferedImage
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                if (img != null) {
                    selectedImage = new BufferedImage(
                        img.getWidth(null), 
                        img.getHeight(null), 
                        BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = selectedImage.createGraphics();
                    g2d.drawImage(img, 0, 0, null);
                    g2d.dispose();
                }
            } else {
                selectedImage = javax.imageio.ImageIO.read(new java.io.File(selectedImagePath));
            }
            updatePreview();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading image: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void updatePreview() {
        if (selectedImage != null) {
            // Create a preview image with the current opacity
            int w = 300; // Increased preview size for better visibility
            int h = (int)(w * ((double)selectedImage.getHeight() / selectedImage.getWidth()));
            
            // Ensure minimum height for better visibility
            h = Math.max(h, 200);
            
            BufferedImage preview = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = preview.createGraphics();
            
            // Enable anti-aliasing for smoother edges
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            
            // Draw white background (default for poems and now journal)
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, w, h);
            
            // Calculate image dimensions to maintain aspect ratio
            int imgW = w;
            int imgH = (int)(w * ((double)selectedImage.getHeight() / selectedImage.getWidth()));
            
            // Center the image if it's smaller than the preview area
            int x = (w - imgW) / 2;
            int y = (h - imgH) / 2;
            
            // Draw the image with the current opacity setting
            AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentOpacity);
            g2d.setComposite(ac);
            g2d.drawImage(selectedImage, x, y, imgW, imgH, null);
            
            // Add a subtle border around the image
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(new Color(0, 0, 0, 30));
            g2d.drawRect(x, y, imgW - 1, imgH - 1);
            
            // Add a subtle drop shadow
            g2d.setPaint(new GradientPaint(0, h-5, new Color(0,0,0,20), 0, h, new Color(0,0,0,0)));
            g2d.fillRect(0, h-5, w, 5);
            
            g2d.dispose();
            
            // Update the preview label
            previewLabel.setIcon(new ImageIcon(preview));
            previewLabel.setText("");
        } else {
            previewLabel.setIcon(null);
            previewLabel.setText("White background (default)");
            previewLabel.setForeground(Color.GRAY);
        }
    }
    
    private void removeBackground() {
        selectedImage = null;
        selectedImagePath = "";
        updatePreview();
        previewLabel.repaint();
    }
    
    private void saveAndClose() {
        SettingsStore settings = SettingsStore.get();
        settings.setEntryBackgroundImage(selectedImagePath);
        settings.setEntryBackgroundOpacity(currentOpacity);
        settings.save();
        dispose();
    }
}
