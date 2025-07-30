package main.dialog;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import main.ui.panels.WallpaperGalleryPanel;
import main.util.ResourceLoader;
import main.util.SettingsStore;

public class EntryBackgroundDialog extends JDialog {

    private final JLabel previewLabel;
    private final JSlider opacitySlider;
    private String selectedImagePath = "";
    private BufferedImage selectedImage;
    private float currentOpacity = 0.7f;
    
    public EntryBackgroundDialog(Frame owner) {
        super(owner, "Entry Background Settings", true);
        setSize(500, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        
        // Initialize with current settings
        SettingsStore settings = SettingsStore.get();
        selectedImagePath = settings.getEntryBackgroundImage();
        currentOpacity = settings.getEntryBackgroundOpacity();
        
        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewLabel = new JLabel("Preview");
        previewLabel.setHorizontalAlignment(JLabel.CENTER);
        previewPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        previewPanel.add(previewLabel, BorderLayout.CENTER);
        
        // Controls panel
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Opacity slider
        JPanel opacityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        opacityPanel.add(new JLabel("Opacity:"));
        opacitySlider = new JSlider(0, 100, (int)(currentOpacity * 100));
        opacitySlider.setPreferredSize(new Dimension(200, 30));
        opacitySlider.addChangeListener(e -> {
            if (!opacitySlider.getValueIsAdjusting()) {
                currentOpacity = opacitySlider.getValue() / 100f;
                previewLabel.repaint();
            }
        });
        opacityPanel.add(opacitySlider);
        
        // Gallery button
        JButton galleryBtn = new JButton("Choose from Gallery...");
        galleryBtn.addActionListener(e -> selectFromGallery());
        
        // Custom image button
        JButton customBtn = new JButton("Choose Custom Image...");
        customBtn.addActionListener(e -> selectCustomBackground());
        
        // Remove background button
        JButton removeBtn = new JButton("Remove Background");
        removeBtn.addActionListener(e -> removeBackground());
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> saveAndClose());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        
        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(previewPanel, BorderLayout.CENTER);
        topPanel.add(opacityPanel, BorderLayout.SOUTH);
        
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        
        // Button container for gallery and custom buttons
        JPanel selectButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        selectButtonsPanel.add(galleryBtn);
        selectButtonsPanel.add(customBtn);
        
        // Container for all bottom controls
        JPanel bottomControlsPanel = new JPanel(new BorderLayout(10, 10));
        bottomControlsPanel.add(selectButtonsPanel, BorderLayout.WEST);
        bottomControlsPanel.add(buttonPanel, BorderLayout.EAST);
        
        bottomPanel.add(bottomControlsPanel, BorderLayout.SOUTH);
        
        mainPanel.add(topPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Load initial preview
        if (!selectedImagePath.isEmpty()) {
            loadSelectedImage();
        } else {
            updatePreview();
        }
    }
    
    private void selectFromGallery() {
        // Use the existing WallpaperGalleryPanel to select an image
        WallpaperGalleryPanel.WallpaperItem selected = WallpaperGalleryPanel.showWallpaperGallery(this);
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
                previewLabel.repaint();
            }
        }
    }
    
    private void selectCustomBackground() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Background Image");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif"));
            
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File selectedFile = fileChooser.getSelectedFile();
                selectedImage = javax.imageio.ImageIO.read(selectedFile);
                if (selectedImage != null) {
                    selectedImagePath = selectedFile.getAbsolutePath();
                    previewLabel.repaint();
                } else {
                    throw new Exception("Failed to load image");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Error loading image: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
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
            int w = 200;
            int h = (int)(w * ((double)selectedImage.getHeight() / selectedImage.getWidth()));
            BufferedImage preview = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = preview.createGraphics();
            
            // Draw checkered background
            int size = 10;
            boolean white = true;
            for (int y = 0; y < h; y += size) {
                for (int x = 0; x < w; x += size) {
                    g2d.setColor(white ? Color.WHITE : new Color(220, 220, 220));
                    g2d.fillRect(x, y, size, size);
                    white = !white;
                }
                if ((h / size) % 2 == 0) white = !white;
            }
            
            // Draw the image with opacity
            AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentOpacity);
            g2d.setComposite(ac);
            g2d.drawImage(selectedImage, 0, 0, w, h, null);
            g2d.dispose();
            
            previewLabel.setIcon(new ImageIcon(preview));
        } else {
            previewLabel.setIcon(null);
            previewLabel.setText("No background selected");
        }
    }
    
    private void removeBackground() {
        selectedImage = null;
        selectedImagePath = "";
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
