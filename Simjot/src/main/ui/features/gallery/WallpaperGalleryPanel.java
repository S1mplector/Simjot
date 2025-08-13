package main.ui.features.gallery;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.dialog.message.CustomMessageDialog;

/**
 * A comprehensive wallpaper gallery panel that allows users to select wallpapers
 * from built-in options, user-created wallpapers, and gallery images.
 */
public class WallpaperGalleryPanel extends JDialog {
    
    private final DefaultListModel<WallpaperItem> model = new DefaultListModel<>();
    private final JList<WallpaperItem> list = new JList<>(model);
    private WallpaperItem selectedItem = null;
    private final boolean autoSaveSelection; // New field to control auto-saving
    
    public WallpaperGalleryPanel(Component parent) {
        this(parent, true); // Default to auto-saving for backward compatibility
    }
    
    public WallpaperGalleryPanel(Component parent, boolean autoSaveSelection) {
        super(SwingUtilities.getWindowAncestor(parent), "Choose Wallpaper", Dialog.ModalityType.APPLICATION_MODAL);
        this.autoSaveSelection = autoSaveSelection;
        setLayout(new BorderLayout(10, 10));
        setSize(700, 600);
        setLocationRelativeTo(parent);
        
        // Title
        JLabel titleLabel = new JLabel("Select a wallpaper from built-in or your gallery:", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);
        
        // Image grid
        setupImageGrid();
        add(new JScrollPane(list), BorderLayout.CENTER);
        
        // Buttons panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        setupButtons();
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        loadWallpapers();
    }
    
    private void setupImageGrid() {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(160);
        list.setFixedCellHeight(180);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Color.WHITE);
        
        list.setCellRenderer((JList<? extends WallpaperItem> l, WallpaperItem value, int idx, boolean sel, boolean focus) -> {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            if (value != null) {
                // Image label
                JLabel imageLabel = new JLabel();
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
                imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                
                if (value.getImage() != null) {
                    Image scaledImage = value.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
                    imageLabel.setIcon(new ImageIcon(scaledImage));
                } else {
                    imageLabel.setText("Error loading image");
                    imageLabel.setForeground(Color.RED);
                }
                
                // Name label
                JLabel nameLabel = new JLabel(value.getName());
                nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
                nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                
                // Source label
                JLabel sourceLabel = new JLabel(value.getSource());
                sourceLabel.setHorizontalAlignment(SwingConstants.CENTER);
                sourceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                sourceLabel.setFont(new Font("SansSerif", Font.ITALIC, 9));
                sourceLabel.setForeground(Color.GRAY);
                
                panel.add(imageLabel);
                panel.add(Box.createRigidArea(new Dimension(0, 5)));
                panel.add(nameLabel);
                panel.add(sourceLabel);
                
                // Tooltip
                panel.setToolTipText(value.getName() + " (" + value.getSource() + ")");
            } else {
                // Show message when no images are available
                JLabel emptyLabel = new JLabel("No wallpapers found");
                emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
                emptyLabel.setForeground(Color.GRAY);
                panel.add(emptyLabel);
            }
            
            panel.setBackground(sel ? new Color(0, 120, 215, 60) : Color.WHITE);
            return panel;
        });
    }
    
private JPanel buttonPanel;
    
    private void setupButtons() {
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        RoundedButton selectBtn = new RoundedButton("Select");
        RoundedButton refreshBtn = new RoundedButton("Refresh");
        RoundedButton openFolderBtn = new RoundedButton("Open Wallpaper Folder");
        RoundedButton cancelBtn = new RoundedButton("Cancel");
        
        selectBtn.addActionListener(e -> {
            selectedItem = list.getSelectedValue();
            if (selectedItem != null && autoSaveSelection) {
                SettingsStore settings = SettingsStore.get();
                settings.setBackgroundImage(selectedItem.getPath());
                // Keep the existing opacity setting
                settings.save();
            }
            dispose();
        });
        
        refreshBtn.addActionListener(e -> loadWallpapers());
        
        openFolderBtn.addActionListener(e -> {
            try {
                File wallpapersDir = AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
                java.awt.Desktop.getDesktop().open(wallpapersDir);
            } catch (Exception ex) {
                CustomMessageDialog.display(this, "Error", "Could not open wallpaper folder.", true);
            }
        });
        
        cancelBtn.addActionListener(e -> dispose());
        
        buttonPanel.add(selectBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(openFolderBtn);
        buttonPanel.add(cancelBtn);
    }
    
    private void loadWallpapers() {
        model.clear();
        
        // Load built-in wallpapers
        loadBuiltInWallpapers();
        
        // Load user wallpapers
        loadUserWallpapers();
        
        // Load gallery images (from drawings)
        loadGalleryImages();
        
        // If no wallpapers found, show a message
        if (model.isEmpty()) {
            model.addElement(null);
        }
    }
    
    private void loadBuiltInWallpapers() {
        // 1) Add vector-generated gray gradient wallpapers
        try {
            java.util.List<String> ids = java.util.List.of(
                GeneratedWallpapers.ID_LINEAR,
                GeneratedWallpapers.ID_DIAGONAL,
                GeneratedWallpapers.ID_RADIAL
            );
            for (String id : ids) {
                String name = switch (id) {
                    case GeneratedWallpapers.ID_LINEAR -> "Gray Gradient (Linear)";
                    case GeneratedWallpapers.ID_DIAGONAL -> "Gray Gradient (Diagonal)";
                    case GeneratedWallpapers.ID_RADIAL -> "Gray Gradient (Radial)";
                    default -> id;
                };
                Image thumb = GeneratedWallpapers.render(id, 300, 168);
                WallpaperItem item = new WallpaperItem(name, id, "Vector", thumb);
                model.addElement(item);
            }
        } catch (Exception ignored) {}

        // 2) Include any existing bundled JPGs (if present)
        String[] builtInWallpapers = {
            "img/wallpapers/bg1.jpg",
            "img/wallpapers/bg2.jpg", 
            "img/wallpapers/bg3.jpg"
        };
        for (String wallpaperPath : builtInWallpapers) {
            try {
                Image img = ResourceLoader.createImage("Simjot/" + wallpaperPath);
                if (img != null) {
                    String name = new File(wallpaperPath).getName();
                    WallpaperItem item = new WallpaperItem(name, "res:" + wallpaperPath, "Built-in", img);
                    model.addElement(item);
                }
            } catch (Exception ex) {
                // Skip if image can't be loaded
            }
        }
    }
    
    private void loadUserWallpapers() {
        File wallpapersDir = AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
        if (wallpapersDir.exists()) {
            // Check if this is the first time (empty directory) and copy sample wallpapers
            if (wallpapersDir.listFiles() == null || wallpapersDir.listFiles().length == 0) {
                copySampleWallpapers(wallpapersDir);
            }
            
            File[] imageFiles = wallpapersDir.listFiles((d, f) -> {
                String lower = f.toLowerCase();
                return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                       lower.endsWith(".png") || lower.endsWith(".gif") || 
                       lower.endsWith(".bmp") || lower.endsWith(".webp");
            });
            
            if (imageFiles != null) {
                for (File file : imageFiles) {
                    try {
                        Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                        WallpaperItem item = new WallpaperItem(file.getName(), file.getAbsolutePath(), "User", img);
                        model.addElement(item);
                    } catch (Exception ex) {
                        // Skip if image can't be loaded
                    }
                }
            }
        }
    }
    
    private void copySampleWallpapers(File wallpapersDir) {
        String[] sampleWallpapers = {
            "img/wallpapers/bg1.jpg",
            "img/wallpapers/bg2.jpg", 
            "img/wallpapers/bg3.jpg"
        };
        
        for (String wallpaperPath : sampleWallpapers) {
            try {
                // Load the built-in wallpaper
                Image img = ResourceLoader.createImage("Simjot/" + wallpaperPath);
                if (img != null) {
                    // Convert to BufferedImage for saving
                    BufferedImage bufferedImage = new BufferedImage(
                        img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2d = bufferedImage.createGraphics();
                    g2d.drawImage(img, 0, 0, null);
                    g2d.dispose();
                    
                    // Save to user's wallpaper folder
                    String fileName = new File(wallpaperPath).getName();
                    File outputFile = new File(wallpapersDir, fileName);
                    javax.imageio.ImageIO.write(bufferedImage, "jpg", outputFile);
                }
            } catch (Exception ex) {
                // Skip if copying fails
            }
        }
    }
    
    private void loadGalleryImages() {
        File drawingsDir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
        if (drawingsDir.exists()) {
            // Look for PNG files (thumbnails from drawings)
            File[] pngFiles = drawingsDir.listFiles((d, f) -> f.toLowerCase().endsWith(".png"));
            if (pngFiles != null) {
                for (File file : pngFiles) {
                    try {
                        Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                        WallpaperItem item = new WallpaperItem(file.getName(), file.getAbsolutePath(), "Gallery", img);
                        model.addElement(item);
                    } catch (Exception ex) {
                        // Skip if image can't be loaded
                    }
                }
            }
            
            // Also look for other image formats
            File[] imageFiles = drawingsDir.listFiles((d, f) -> {
                String lower = f.toLowerCase();
                return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                       lower.endsWith(".gif") || lower.endsWith(".bmp") ||
                       lower.endsWith(".webp");
            });
            if (imageFiles != null) {
                for (File file : imageFiles) {
                    try {
                        Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                        WallpaperItem item = new WallpaperItem(file.getName(), file.getAbsolutePath(), "Gallery", img);
                        model.addElement(item);
                    } catch (Exception ex) {
                        // Skip if image can't be loaded
                    }
                }
            }
        }
    }
    
    public WallpaperItem getSelectedImage() {
        return selectedItem;
    }
    
    /**
     * Shows the wallpaper gallery dialog and returns the selected wallpaper item.
     * @param parent The parent component
     * @return The selected WallpaperItem, or null if cancelled
     */
    public static WallpaperItem showWallpaperGallery(Component parent) {
        return showWallpaperGallery(parent, true);
    }
    
    /**
     * Shows the wallpaper gallery dialog and returns the selected wallpaper item.
     * @param parent The parent component
     * @param autoSave Whether to automatically save the selection to main menu background
     * @return The selected WallpaperItem, or null if cancelled
     */
    public static WallpaperItem showWallpaperGallery(Component parent, boolean autoSave) {
        WallpaperGalleryPanel panel = new WallpaperGalleryPanel(parent, autoSave);
        panel.setVisible(true);
        return panel.getSelectedImage();
    }
    
    // --- Wallpaper Item Class ---
    public static class WallpaperItem {
        private final String name;
        private final String path;
        private final String source;
        private final Image image;
        
        public WallpaperItem(String name, String path, String source, Image image) {
            this.name = name;
            this.path = path;
            this.source = source;
            this.image = image;
        }
        
        public String getName() { return name; }
        public String getPath() { return path; }
        public String getSource() { return source; }
        public Image getImage() { return image; }
        
        @Override
        public String toString() {
            return name + " (" + source + ")";
        }
    }
} 