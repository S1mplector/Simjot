package main.ui.features.gallery;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.util.AccentColorUtil;
 

/**
 * A comprehensive wallpaper gallery panel that allows users to select wallpapers
 * from built-in options, user-created wallpapers, and gallery images.
 */
public class WallpaperGalleryPanel extends JDialog {
    
    private final DefaultListModel<WallpaperItem> model = new DefaultListModel<>();
    private final JList<WallpaperItem> list = new JList<>(model);
    private WallpaperItem selectedItem = null;
    private final boolean autoSaveSelection; // New field to control auto-saving
    private JLabel previewImageLabel;
    private JPanel accentSwatch;
    
    public WallpaperGalleryPanel(Component parent) {
        this(parent, true); // Default to auto-saving for backward compatibility
    }
    
    public WallpaperGalleryPanel(Component parent, boolean autoSaveSelection) {
        super(SwingUtilities.getWindowAncestor(parent), "Choose Wallpaper", Dialog.ModalityType.APPLICATION_MODAL);
        this.autoSaveSelection = autoSaveSelection;
        setLayout(new BorderLayout(10, 10));
        setSize(700, 600);
        setLocationRelativeTo(parent);

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        if (autoSaveSelection) {
            try {
                SettingsStore.get().setBackgroundOpacity(1.0f);
                SettingsStore.get().save();
            } catch (Throwable ignored) {}
        }
        
        // Title
        JLabel titleLabel = new JLabel("Select a wallpaper from built-in or your gallery:", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        root.add(titleLabel, BorderLayout.NORTH);
        
        // Image grid
        setupImageGrid();
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        // Apply the same modern scrollbars used in poetry/journal panels
        JScrollBar vbar = listScroll.getVerticalScrollBar();
        vbar.setUI(new ModernScrollBarUI());
        vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        vbar.setOpaque(false);
        vbar.setUnitIncrement(16);
        JScrollBar hbar = listScroll.getHorizontalScrollBar();
        hbar.setUI(new ModernScrollBarUI());
        hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
        hbar.setOpaque(false);
        hbar.setUnitIncrement(16);
        FrostedGlassPanel browserPanel = new FrostedGlassPanel(new BorderLayout(), 14);
        browserPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        browserPanel.add(listScroll, BorderLayout.CENTER);
        root.add(browserPanel, BorderLayout.CENTER);

        // Live preview + accent swatch (right side)
        FrostedGlassPanel preview = new FrostedGlassPanel(new BorderLayout(8, 8), 14);
        preview.setPreferredSize(new Dimension(280, 0));
        preview.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        previewImageLabel = new JLabel("Preview", SwingConstants.CENTER);
        previewImageLabel.setOpaque(false);
        previewImageLabel.setForeground(new Color(60, 60, 60));
        previewImageLabel.setBorder(BorderFactory.createLineBorder(new Color(200,200,200,160)));
        preview.add(previewImageLabel, BorderLayout.CENTER);

        JPanel swatchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        swatchRow.add(new JLabel("Detected Accent:"));
        accentSwatch = new JPanel();
        accentSwatch.setPreferredSize(new Dimension(42, 18));
        accentSwatch.setBorder(BorderFactory.createLineBorder(new Color(160,160,160)));
        accentSwatch.setBackground(AccentColorUtil.defaultAccent());
        swatchRow.setOpaque(false);
        swatchRow.add(accentSwatch);
        preview.add(swatchRow, BorderLayout.SOUTH);

        root.add(preview, BorderLayout.EAST);
        
        // Buttons panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        setupButtons();
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
        
        loadWallpapersAsync();
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updatePreview(); });
        if (model.getSize() > 0) {
            list.setSelectedIndex(0);
        }
    }

    private static Icon iconById(String id) {
        String path = ImageIconRenderer.mapIdToResource(id);
        return path != null ? ImageIconRenderer.icon(path, 18, false) : null;
    }
    
    private void setupImageGrid() {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(160);
        list.setFixedCellHeight(180);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setBackground(new Color(255, 255, 255, 0));

        // Reusable renderer to avoid reallocations and rescaling during scroll
        class Cell extends JPanel implements ListCellRenderer<WallpaperItem> {
            private final JLabel img = new JLabel();
            private final JLabel name = new JLabel();
            private final JLabel source = new JLabel();
            Cell(){
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                setOpaque(false);
                setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
                img.setHorizontalAlignment(SwingConstants.CENTER);
                img.setAlignmentX(Component.CENTER_ALIGNMENT);
                name.setHorizontalAlignment(SwingConstants.CENTER);
                name.setAlignmentX(Component.CENTER_ALIGNMENT);
                name.setFont(new Font("SansSerif", Font.PLAIN, 10));
                source.setHorizontalAlignment(SwingConstants.CENTER);
                source.setAlignmentX(Component.CENTER_ALIGNMENT);
                source.setFont(new Font("SansSerif", Font.ITALIC, 9));
                source.setForeground(Color.GRAY);
                add(img); add(Box.createRigidArea(new Dimension(0,5))); add(name); add(source);
            }
            @Override
            public Component getListCellRendererComponent(JList<? extends WallpaperItem> l, WallpaperItem v, int index, boolean isSelected, boolean cellHasFocus) {
                if (v == null) {
                    img.setIcon(null); img.setText("No wallpapers found");
                    name.setText(""); source.setText("");
                } else {
                    img.setText("");
                    ImageIcon thumb = v.getThumb();
                    if (thumb == null && v.getImage() != null) {
                        thumb = makeThumbIcon(v.getImage(), 120, 120);
                        v.setThumb(thumb);
                    }
                    img.setIcon(thumb);
                    name.setText(v.getName());
                    source.setText(v.getSource());
                    setToolTipText(v.getName() + " (" + v.getSource() + ")");
                }
                if (isSelected) {
                    setOpaque(true);
                    setBackground(new Color(0, 120, 215, 70));
                } else {
                    setOpaque(false);
                }
                return this;
            }
        }
        list.setCellRenderer(new Cell());
    }

    private void updatePreview() {
        WallpaperItem it = list.getSelectedValue();
        if (it == null || it.getImage() == null) {
            previewImageLabel.setIcon(null);
            previewImageLabel.setText("Preview");
            accentSwatch.setBackground(AccentColorUtil.defaultAccent());
            return;
        }
        Image img = it.getImage();
        int w = 240;
        int h = (int)(w * (img.getHeight(null)/(double)img.getWidth(null)));
        if (h <= 0) h = 160;
        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        previewImageLabel.setIcon(new ImageIcon(scaled));
        previewImageLabel.setText("");
        Color ac = AccentColorUtil.extractAccent(img);
        accentSwatch.setBackground(ac);
    }
    
private JPanel buttonPanel;
    
    private void setupButtons() {
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        buttonPanel.setOpaque(false);
        IconMenuButton selectBtn = new IconMenuButton("Select", "save");
        IconMenuButton refreshBtn = new IconMenuButton("Refresh", "rescan");
        IconMenuButton openFolderBtn = new IconMenuButton("Open", "open_folder");
        IconMenuButton cancelBtn = new IconMenuButton("Cancel", "exit");

        selectBtn.setToolTipText("Apply selected wallpaper");
        refreshBtn.setToolTipText("Refresh wallpaper list");
        openFolderBtn.setToolTipText("Open wallpapers folder");
        cancelBtn.setToolTipText("Cancel and close");
        
        selectBtn.addActionListener(e -> {
            selectedItem = list.getSelectedValue();
            if (selectedItem != null && autoSaveSelection) {
                SettingsStore settings = SettingsStore.get();
                settings.setBackgroundImage(selectedItem.getPath());
                // Persist detected accent for main menu
                try {
                    java.awt.Color ac = AccentColorUtil.extractAccent(selectedItem.getImage());
                    settings.setMainMenuAccentRGB(ac.getRGB());
                } catch (Throwable ignored) {}
                settings.save();
            }
            dispose();
        });
        
        refreshBtn.addActionListener(e -> loadWallpapersAsync());
        
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
    
    private void loadWallpapersAsync() {
        model.clear();
        SwingWorker<Void, WallpaperItem> worker = new SwingWorker<>(){
            @Override protected Void doInBackground(){
                // Built-in season-themed wallpapers
                String[] builtIn = {
                    "img/background/spring.png",
                    "img/background/summer.png",
                    "img/background/fall.jpg",
                    "img/background/winter.png",
                    "img/background/b&w.png",
                    "img/background/b&w2.png"
                };
                for (String res : builtIn) {
                    try {
                        Image img = ResourceLoader.createImage("Simjot/" + res);
                        if (img != null) {
                            String name = new java.io.File(res).getName();
                            WallpaperItem it = new WallpaperItem(name, "res:" + res, "Built-in", img);
                            it.setThumb(makeThumbIcon(img, 120, 120));
                            publish(it);
                        }
                    } catch (Throwable ignored) {}
                }

                // User wallpapers
                try {
                    java.io.File wallpapersDir = AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
                    if (wallpapersDir.exists()) {
                        java.io.File[] imageFiles = wallpapersDir.listFiles((d, f) -> {
                            String lower = f.toLowerCase();
                            return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".gif")||lower.endsWith(".bmp")||lower.endsWith(".webp");
                        });
                        if (imageFiles != null) {
                            for (java.io.File file : imageFiles) {
                                try {
                                    Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                                    WallpaperItem it = new WallpaperItem(file.getName(), file.getAbsolutePath(), "User", img);
                                    it.setThumb(makeThumbIcon(img, 120, 120));
                                    publish(it);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // Gallery images
                try {
                    java.io.File drawingsDir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
                    if (drawingsDir.exists()) {
                        java.io.File[] pngFiles = drawingsDir.listFiles((d, f) -> f.toLowerCase().endsWith(".png"));
                        if (pngFiles != null) {
                            for (java.io.File file : pngFiles) {
                                try {
                                    Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                                    WallpaperItem it = new WallpaperItem(file.getName(), file.getAbsolutePath(), "Gallery", img);
                                    it.setThumb(makeThumbIcon(img, 120, 120));
                                    publish(it);
                                } catch (Throwable ignored) {}
                            }
                        }
                        java.io.File[] imageFiles = drawingsDir.listFiles((d, f) -> {
                            String lower = f.toLowerCase();
                            return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".gif")||lower.endsWith(".bmp")||lower.endsWith(".webp");
                        });
                        if (imageFiles != null) {
                            for (java.io.File file : imageFiles) {
                                try {
                                    Image img = new ImageIcon(file.getAbsolutePath()).getImage();
                                    WallpaperItem it = new WallpaperItem(file.getName(), file.getAbsolutePath(), "Gallery", img);
                                    it.setThumb(makeThumbIcon(img, 120, 120));
                                    publish(it);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                return null;
            }
            @Override protected void process(java.util.List<WallpaperItem> chunks){
                for (WallpaperItem it : chunks) { model.addElement(it); }
            }
            @Override protected void done(){
                if (model.isEmpty()) model.addElement(null);
            }
        };
        worker.execute();
    }
    
    private void loadBuiltInWallpapers() {
        // Season-themed wallpapers
        String[] builtInWallpapers = {
            "img/background/spring.png",
            "img/background/summer.png",
            "img/background/fall.jpg",
            "img/background/winter.png",
            "img/background/b&w2.png"
        };
        for (String wallpaperPath : builtInWallpapers) {
            try {
                Image img = ResourceLoader.createImage("Simjot/" + wallpaperPath);
                if (img != null) {
                    String name = new File(wallpaperPath).getName();
                    WallpaperItem item = new WallpaperItem(name, "res:" + wallpaperPath, "Built-in", img);
                    item.setThumb(makeThumbIcon(img, 120, 120));
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
                        item.setThumb(makeThumbIcon(img, 120, 120));
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
            "img/background/spring.png",
            "img/background/summer.png", 
            "img/background/fall.jpg",
            "img/background/winter.png"
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
                        item.setThumb(makeThumbIcon(img, 120, 120));
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
        private ImageIcon thumb;
        
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
        public ImageIcon getThumb() { return thumb; }
        public void setThumb(ImageIcon t) { this.thumb = t; }
        
        @Override
        public String toString() {
            return name + " (" + source + ")";
        }
    }

    private static ImageIcon makeThumbIcon(Image img, int maxW, int maxH) {
        if (img == null) return null;
        int iw = img.getWidth(null), ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return null;
        double scale = Math.min(maxW / (double) iw, maxH / (double) ih);
        int tw = Math.max(1, (int) Math.round(iw * scale));
        int th = Math.max(1, (int) Math.round(ih * scale));
        BufferedImage bi = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img, 0, 0, tw, th, null);
        g2.dispose();
        return new ImageIcon(bi);
    }
} 
