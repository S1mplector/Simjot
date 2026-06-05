/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.dialog.utils;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.features.gallery.WallpaperGalleryPanel;

public class EntryBackgroundDialog extends JDialog {

    private final JLabel previewLabel;
    private String selectedImagePath = "";
    private BufferedImage selectedImage;
    private float currentOpacity = 0.3f;
    
    public EntryBackgroundDialog(Frame owner) {
        super(owner, "Entry Background Settings", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);
        
        // Initialize with current settings
        SettingsStore settings = SettingsStore.get();
        selectedImagePath = settings.getEntryBackgroundImage();
        currentOpacity = settings.getEntryBackgroundOpacity();

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Entry Background");
        title.setForeground(new Color(40, 40, 40));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel subtitle = new JLabel("Pick an image for the editor background");
        subtitle.setForeground(new Color(120, 120, 120));
        subtitle.setFont(subtitle.getFont().deriveFont(12f));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        panel.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel previewTitle = new JLabel("Preview");
        previewTitle.setForeground(Color.DARK_GRAY);
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD, 13f));
        previewTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(previewTitle);
        center.add(Box.createVerticalStrut(6));

        JPanel previewCard = new JPanel(new BorderLayout());
        previewCard.setBackground(new Color(250, 250, 252));
        previewCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 214, 224)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        previewLabel = new JLabel("No background selected", JLabel.CENTER);
        previewLabel.setPreferredSize(new Dimension(360, 200));
        previewLabel.setHorizontalAlignment(JLabel.CENTER);
        previewLabel.setVerticalAlignment(JLabel.CENTER);
        previewLabel.setOpaque(false);
        previewLabel.setForeground(new Color(120, 120, 120));
        previewCard.add(previewLabel, BorderLayout.CENTER);
        previewCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(previewCard);
        center.add(Box.createVerticalStrut(10));

        panel.add(center, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        btns.setOpaque(false);
        RoundedButton galleryBtn = createDialogButton("Gallery", "gallery");
        galleryBtn.setToolTipText("Choose from gallery");
        galleryBtn.addActionListener(e -> selectFromGallery());
        RoundedButton removeBtn = createDialogButton("Remove", "trash");
        removeBtn.setToolTipText("Remove background");
        removeBtn.addActionListener(e -> removeBackground());
        RoundedButton okBtn = createDialogButton("Save", "save");
        okBtn.setToolTipText("Apply changes");
        okBtn.addActionListener(e -> saveAndClose());
        RoundedButton cancelBtn = createDialogButton("Cancel", "exit");
        cancelBtn.setToolTipText("Cancel changes");
        cancelBtn.addActionListener(e -> dispose());
        btns.add(galleryBtn);
        btns.add(removeBtn);
        btns.add(okBtn);
        btns.add(cancelBtn);
        panel.add(btns, BorderLayout.SOUTH);

        add(panel);
        pack();
        setSize(520, 420);
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
        
        // Load initial preview
        if (!selectedImagePath.isEmpty()) {
            loadSelectedImage();
        } else {
            updatePreview();
        }
    }

    private RoundedButton createDialogButton(String text, String iconId) {
        RoundedButton btn = new RoundedButton(text).withIcon(iconId);
        btn.setPreferredSize(new Dimension(132, 40));
        btn.setFocusPainted(false);
        return btn;
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
