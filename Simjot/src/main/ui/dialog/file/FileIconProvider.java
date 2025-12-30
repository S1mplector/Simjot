package main.ui.dialog.file;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;

/**
 * Provides file and folder icons for the Simjot file chooser.
 * 
 * <p>Uses a multi-tier strategy:</p>
 * <ol>
 *   <li>System icons via {@link FileSystemView} when available</li>
 *   <li>Extension-based custom icons for known file types</li>
 *   <li>Generic fallback icons for unknown types</li>
 * </ol>
 * 
 * <p>Icons are cached for performance.</p>
 * 
 * @since 1.0
 */
public final class FileIconProvider {
    
    private static final FileSystemView FILE_SYSTEM_VIEW = FileSystemView.getFileSystemView();
    
    // Icon cache for performance
    private static final Map<String, Icon> EXTENSION_ICON_CACHE = new ConcurrentHashMap<>();
    private static final Map<File, Icon> FILE_ICON_CACHE = new ConcurrentHashMap<>();
    
    // Standard icon sizes
    public static final int SMALL_ICON_SIZE = 16;
    public static final int MEDIUM_ICON_SIZE = 24;
    public static final int LARGE_ICON_SIZE = 32;
    public static final int XLARGE_ICON_SIZE = 48;
    
    // Icon colors for custom icons
    private static final Color FOLDER_COLOR = new Color(255, 196, 61);
    private static final Color DOCUMENT_COLOR = new Color(100, 149, 237);
    private static final Color IMAGE_COLOR = new Color(76, 175, 80);
    private static final Color AUDIO_COLOR = new Color(233, 30, 99);
    private static final Color VIDEO_COLOR = new Color(156, 39, 176);
    private static final Color ARCHIVE_COLOR = new Color(121, 85, 72);
    private static final Color CODE_COLOR = new Color(255, 152, 0);
    private static final Color DATA_COLOR = new Color(0, 150, 136);
    private static final Color EXECUTABLE_COLOR = new Color(244, 67, 54);
    private static final Color UNKNOWN_COLOR = new Color(158, 158, 158);
    
    // Extension to category mapping
    private static final Map<String, FileCategory> EXTENSION_CATEGORIES = new HashMap<>();
    
    static {
        // Documents
        addExtensions(FileCategory.DOCUMENT, "txt", "doc", "docx", "pdf", "rtf", "odt", "md", "tex", "pages");
        // Spreadsheets
        addExtensions(FileCategory.DOCUMENT, "xls", "xlsx", "csv", "ods", "numbers");
        // Presentations
        addExtensions(FileCategory.DOCUMENT, "ppt", "pptx", "odp", "key");
        // Images
        addExtensions(FileCategory.IMAGE, "jpg", "jpeg", "png", "gif", "bmp", "svg", "ico", "webp", "tiff", "heic", "raw");
        // Audio
        addExtensions(FileCategory.AUDIO, "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "aiff");
        // Video
        addExtensions(FileCategory.VIDEO, "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v");
        // Archives
        addExtensions(FileCategory.ARCHIVE, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "dmg", "iso");
        // Code
        addExtensions(FileCategory.CODE, "java", "py", "js", "ts", "cpp", "c", "h", "cs", "rb", "go", "rs", "swift", "kt", "scala", "php", "html", "css", "scss", "xml", "json", "yaml", "yml", "sh", "bash", "sql");
        // Data
        addExtensions(FileCategory.DATA, "db", "sqlite", "mdb", "json", "xml", "yaml", "yml", "csv", "properties", "ini", "cfg");
        // Executables
        addExtensions(FileCategory.EXECUTABLE, "exe", "app", "dmg", "msi", "deb", "rpm", "jar", "bat", "cmd", "sh");
        // Simjot specific
        addExtensions(FileCategory.SIMJOT, "sjcrypt", "sjbackup", "sjentry", "sjpoem");
    }
    
    private static void addExtensions(FileCategory category, String... extensions) {
        for (String ext : extensions) {
            EXTENSION_CATEGORIES.put(ext.toLowerCase(), category);
        }
    }
    
    private FileIconProvider() {}
    
    /**
     * File category for icon selection.
     */
    public enum FileCategory {
        FOLDER,
        DOCUMENT,
        IMAGE,
        AUDIO,
        VIDEO,
        ARCHIVE,
        CODE,
        DATA,
        EXECUTABLE,
        SIMJOT,
        UNKNOWN
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the icon for a file, attempting system icon first.
     * 
     * @param file The file
     * @return The icon for the file
     */
    public static Icon getIcon(File file) {
        return getIcon(file, SMALL_ICON_SIZE);
    }
    
    /**
     * Gets the icon for a file at specified size.
     * 
     * @param file The file
     * @param size The desired icon size
     * @return The icon for the file
     */
    public static Icon getIcon(File file, int size) {
        if (file == null) return getUnknownIcon(size);
        
        // Try system icon first
        try {
            Icon systemIcon = FILE_SYSTEM_VIEW.getSystemIcon(file);
            if (systemIcon != null) {
                if (size != SMALL_ICON_SIZE) {
                    return scaleIcon(systemIcon, size);
                }
                return systemIcon;
            }
        } catch (Exception ignored) {}
        
        // Fall back to custom icon
        return getCustomIcon(file, size);
    }
    
    /**
     * Gets a custom-drawn icon based on file type.
     */
    public static Icon getCustomIcon(File file, int size) {
        if (file.isDirectory()) {
            return getFolderIcon(size);
        }
        
        FileCategory category = getCategoryForFile(file);
        return getCategoryIcon(category, size);
    }
    
    /**
     * Gets an icon for a file extension.
     */
    public static Icon getIconForExtension(String extension, int size) {
        String key = extension.toLowerCase() + "_" + size;
        return EXTENSION_ICON_CACHE.computeIfAbsent(key, k -> {
            FileCategory category = EXTENSION_CATEGORIES.getOrDefault(
                extension.toLowerCase(), FileCategory.UNKNOWN);
            return getCategoryIcon(category, size);
        });
    }
    
    /**
     * Gets the file category for a file.
     */
    public static FileCategory getCategoryForFile(File file) {
        if (file == null) return FileCategory.UNKNOWN;
        if (file.isDirectory()) return FileCategory.FOLDER;
        
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            String ext = name.substring(dotIndex + 1).toLowerCase();
            return EXTENSION_CATEGORIES.getOrDefault(ext, FileCategory.UNKNOWN);
        }
        
        return FileCategory.UNKNOWN;
    }
    
    /**
     * Gets a folder icon.
     */
    public static Icon getFolderIcon(int size) {
        return new FolderIcon(size, FOLDER_COLOR);
    }
    
    /**
     * Gets an icon for a category.
     */
    public static Icon getCategoryIcon(FileCategory category, int size) {
        Color color = switch (category) {
            case FOLDER -> FOLDER_COLOR;
            case DOCUMENT -> DOCUMENT_COLOR;
            case IMAGE -> IMAGE_COLOR;
            case AUDIO -> AUDIO_COLOR;
            case VIDEO -> VIDEO_COLOR;
            case ARCHIVE -> ARCHIVE_COLOR;
            case CODE -> CODE_COLOR;
            case DATA -> DATA_COLOR;
            case EXECUTABLE -> EXECUTABLE_COLOR;
            case SIMJOT -> new Color(66, 133, 244); // Simjot blue
            case UNKNOWN -> UNKNOWN_COLOR;
        };
        
        if (category == FileCategory.FOLDER) {
            return new FolderIcon(size, color);
        }
        return new FileIcon(size, color, getSymbolForCategory(category));
    }
    
    /**
     * Gets a symbol character for a category.
     */
    private static String getSymbolForCategory(FileCategory category) {
        return switch (category) {
            case DOCUMENT -> "T";
            case IMAGE -> "◪";
            case AUDIO -> "♪";
            case VIDEO -> "▶";
            case ARCHIVE -> "◫";
            case CODE -> "<>";
            case DATA -> "◈";
            case EXECUTABLE -> "⚙";
            case SIMJOT -> "S";
            default -> "";
        };
    }
    
    /**
     * Gets an unknown file type icon.
     */
    public static Icon getUnknownIcon(int size) {
        return new FileIcon(size, UNKNOWN_COLOR, "?");
    }
    
    /**
     * Scales an icon to the specified size.
     */
    public static Icon scaleIcon(Icon icon, int size) {
        if (icon == null) return null;
        
        BufferedImage img = new BufferedImage(
            icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();
        
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
    
    /**
     * Clears the icon cache.
     */
    public static void clearCache() {
        EXTENSION_ICON_CACHE.clear();
        FILE_ICON_CACHE.clear();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CUSTOM ICON IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Custom folder icon.
     */
    private static class FolderIcon implements Icon {
        private final int size;
        private final Color color;
        
        FolderIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }
        
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = size;
            int h = size;
            int tabWidth = (int) (w * 0.4);
            int tabHeight = (int) (h * 0.15);
            int bodyTop = (int) (h * 0.2);
            int bodyHeight = (int) (h * 0.7);
            int arc = Math.max(2, size / 8);
            
            // Folder back
            g2.setColor(darker(color, 0.85f));
            g2.fillRoundRect(x, y + bodyTop, w, bodyHeight, arc, arc);
            
            // Tab
            g2.setColor(color);
            g2.fillRoundRect(x, y + (int)(h * 0.1), tabWidth, tabHeight + arc, arc, arc);
            
            // Folder front
            g2.fillRoundRect(x, y + bodyTop + 2, w, bodyHeight - 2, arc, arc);
            
            // Highlight
            g2.setColor(new Color(255, 255, 255, 80));
            g2.fillRoundRect(x + 2, y + bodyTop + 4, w - 4, (int)(bodyHeight * 0.3), arc, arc);
            
            g2.dispose();
        }
        
        @Override
        public int getIconWidth() { return size; }
        
        @Override
        public int getIconHeight() { return size; }
    }
    
    /**
     * Custom file icon.
     */
    private static class FileIcon implements Icon {
        private final int size;
        private final Color color;
        private final String symbol;
        
        FileIcon(int size, Color color, String symbol) {
            this.size = size;
            this.color = color;
            this.symbol = symbol;
        }
        
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int w = size;
            int h = size;
            int margin = Math.max(1, size / 8);
            int cornerFold = (int) (w * 0.25);
            int arc = Math.max(1, size / 10);
            
            // File shape with folded corner
            int[] xPoints = {x + margin, x + w - margin - cornerFold, x + w - margin, x + w - margin, x + margin};
            int[] yPoints = {y + margin, y + margin, y + margin + cornerFold, y + h - margin, y + h - margin};
            
            // Shadow
            g2.setColor(new Color(0, 0, 0, 30));
            g2.translate(1, 1);
            g2.fillPolygon(xPoints, yPoints, 5);
            g2.translate(-1, -1);
            
            // File body (white)
            g2.setColor(Color.WHITE);
            g2.fillPolygon(xPoints, yPoints, 5);
            
            // Border
            g2.setColor(new Color(200, 200, 200));
            g2.drawPolygon(xPoints, yPoints, 5);
            
            // Corner fold
            g2.setColor(new Color(230, 230, 230));
            int[] foldX = {x + w - margin - cornerFold, x + w - margin, x + w - margin - cornerFold};
            int[] foldY = {y + margin, y + margin + cornerFold, y + margin + cornerFold};
            g2.fillPolygon(foldX, foldY, 3);
            g2.setColor(new Color(180, 180, 180));
            g2.drawPolygon(foldX, foldY, 3);
            
            // Color stripe at bottom
            int stripeHeight = Math.max(3, size / 4);
            g2.setColor(color);
            g2.fillRoundRect(x + margin, y + h - margin - stripeHeight, 
                w - 2 * margin, stripeHeight, arc, arc);
            
            // Symbol
            if (symbol != null && !symbol.isEmpty() && size >= 16) {
                g2.setColor(color);
                int fontSize = Math.max(8, size / 3);
                g2.setFont(g2.getFont().deriveFont((float) fontSize));
                int textY = y + h / 2 + fontSize / 3;
                int textX = x + (w - g2.getFontMetrics().stringWidth(symbol)) / 2;
                g2.drawString(symbol, textX, textY - stripeHeight / 2);
            }
            
            g2.dispose();
        }
        
        @Override
        public int getIconWidth() { return size; }
        
        @Override
        public int getIconHeight() { return size; }
    }
    
    /**
     * Darkens a color.
     */
    private static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int) (c.getRed() * factor)),
            Math.max(0, (int) (c.getGreen() * factor)),
            Math.max(0, (int) (c.getBlue() * factor)),
            c.getAlpha()
        );
    }
}
