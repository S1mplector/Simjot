/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.file;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Generates preview content for files in the file chooser.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>Image thumbnails (JPEG, PNG, GIF, BMP, WebP)</li>
 *   <li>Text file previews (first N lines)</li>
 *   <li>File metadata (size, dates, type)</li>
 *   <li>Icon-based previews for other file types</li>
 * </ul>
 * 
 * <p>Preview generation is asynchronous to keep the UI responsive.</p>
 * 
 * @since 1.0
 */
public final class FilePreviewProvider {
    
    private static final int DEFAULT_THUMBNAIL_SIZE = 128;
    private static final int TEXT_PREVIEW_LINES = 10;
    private static final int TEXT_PREVIEW_MAX_CHARS = 500;
    
    // LRU-bounded cache for generated thumbnails
    private static final int MAX_CACHE_SIZE = 100;
    private static final Map<String, ImageIcon> THUMBNAIL_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> e) {
                return size() > MAX_CACHE_SIZE;
            }
        });
    
    // Background executor for preview generation
    private static final ExecutorService previewExecutor = Executors.newFixedThreadPool(2);
    
    private FilePreviewProvider() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PREVIEW DATA
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Holds preview information for a file.
     */
    public static class PreviewData {
        private final File file;
        private Icon thumbnail;
        private String textPreview;
        private String metadata;
        private boolean isLoading;
        
        public PreviewData(File file) {
            this.file = file;
            this.isLoading = true;
        }
        
        public File getFile() { return file; }
        public Icon getThumbnail() { return thumbnail; }
        public String getTextPreview() { return textPreview; }
        public String getMetadata() { return metadata; }
        public boolean isLoading() { return isLoading; }
        
        public boolean hasImagePreview() { return thumbnail != null; }
        public boolean hasTextPreview() { return textPreview != null && !textPreview.isEmpty(); }
    }
    
    /**
     * Callback interface for async preview loading.
     */
    public interface PreviewCallback {
        void onPreviewReady(PreviewData preview);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates a preview for a file asynchronously.
     * 
     * @param file The file to preview
     * @param callback Callback invoked when preview is ready
     */
    public static void generatePreview(File file, PreviewCallback callback) {
        generatePreview(file, DEFAULT_THUMBNAIL_SIZE, callback);
    }
    
    /**
     * Generates a preview with custom thumbnail size.
     */
    public static void generatePreview(File file, int thumbnailSize, PreviewCallback callback) {
        if (file == null || !file.exists()) {
            callback.onPreviewReady(null);
            return;
        }
        
        previewExecutor.submit(() -> {
            PreviewData preview = new PreviewData(file);
            
            try {
                // Always generate metadata
                preview.metadata = generateMetadata(file);
                
                if (file.isDirectory()) {
                    preview.thumbnail = FileIconProvider.getFolderIcon(thumbnailSize);
                } else if (FileTypeDetector.isImage(file)) {
                    preview.thumbnail = generateImageThumbnail(file, thumbnailSize);
                } else if (FileTypeDetector.isTextFile(file)) {
                    preview.textPreview = generateTextPreview(file);
                    preview.thumbnail = FileIconProvider.getIcon(file, thumbnailSize);
                } else {
                    preview.thumbnail = FileIconProvider.getIcon(file, thumbnailSize);
                }
            } catch (Exception e) {
                preview.thumbnail = FileIconProvider.getUnknownIcon(thumbnailSize);
            }
            
            preview.isLoading = false;
            
            // Callback on EDT
            final PreviewData finalPreview = preview;
            SwingUtilities.invokeLater(() -> callback.onPreviewReady(finalPreview));
        });
    }
    
    /**
     * Generates a preview synchronously (blocking).
     */
    public static PreviewData generatePreviewSync(File file) {
        return generatePreviewSync(file, DEFAULT_THUMBNAIL_SIZE);
    }
    
    /**
     * Generates a preview synchronously with custom thumbnail size.
     */
    public static PreviewData generatePreviewSync(File file, int thumbnailSize) {
        if (file == null || !file.exists()) return null;
        
        PreviewData preview = new PreviewData(file);
        
        try {
            preview.metadata = generateMetadata(file);
            
            if (file.isDirectory()) {
                preview.thumbnail = FileIconProvider.getFolderIcon(thumbnailSize);
            } else if (FileTypeDetector.isImage(file)) {
                preview.thumbnail = generateImageThumbnail(file, thumbnailSize);
            } else if (FileTypeDetector.isTextFile(file)) {
                preview.textPreview = generateTextPreview(file);
                preview.thumbnail = FileIconProvider.getIcon(file, thumbnailSize);
            } else {
                preview.thumbnail = FileIconProvider.getIcon(file, thumbnailSize);
            }
        } catch (Exception e) {
            preview.thumbnail = FileIconProvider.getUnknownIcon(thumbnailSize);
        }
        
        preview.isLoading = false;
        return preview;
    }
    
    /**
     * Gets an image thumbnail, using cache if available.
     */
    public static ImageIcon generateImageThumbnail(File file, int size) {
        String cacheKey = file.getAbsolutePath() + "_" + size;
        
        ImageIcon cached = THUMBNAIL_CACHE.get(cacheKey);
        if (cached != null) return cached;
        
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) return null;
            
            BufferedImage thumbnail = createThumbnail(original, size);
            ImageIcon icon = new ImageIcon(thumbnail);
            
            // Add to LRU cache (automatic eviction)
            THUMBNAIL_CACHE.put(cacheKey, icon);
            
            return icon;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Generates a text preview (first N lines).
     */
    public static String generateTextPreview(File file) {
        if (!FileTypeDetector.isTextFile(file)) return null;
        
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < TEXT_PREVIEW_LINES) {
                if (sb.length() > 0) sb.append("\n");
                
                // Truncate long lines
                if (line.length() > 80) {
                    line = line.substring(0, 77) + "...";
                }
                
                sb.append(line);
                totalChars += line.length();
                lineCount++;
                
                if (totalChars > TEXT_PREVIEW_MAX_CHARS) break;
            }
        } catch (IOException e) {
            return null;
        }
        
        return sb.toString();
    }
    
    /**
     * Generates metadata string for a file.
     */
    public static String generateMetadata(File file) {
        StringBuilder sb = new StringBuilder();
        
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            sb.append(count).append(" item").append(count != 1 ? "s" : "");
        } else {
            // Size
            sb.append(formatFileSize(file.length()));
            
            // Type
            String type = FileTypeDetector.getFileTypeDescription(file);
            sb.append("\n").append(type);
        }
        
        // Dates
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US);
            
            sb.append("\n\nModified:\n").append(sdf.format(new Date(attrs.lastModifiedTime().toMillis())));
            sb.append("\n\nCreated:\n").append(sdf.format(new Date(attrs.creationTime().toMillis())));
        } catch (IOException ignored) {}
        
        return sb.toString();
    }
    
    /**
     * Creates a preview component for display.
     */
    public static JComponent createPreviewComponent(PreviewData preview, int width, int height) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setPreferredSize(new Dimension(width, height));
        
        if (preview == null) {
            label.setText("<html><center><i>No preview available</i></center></html>");
            return label;
        }
        
        StringBuilder html = new StringBuilder("<html><center>");
        
        // Icon/Thumbnail
        if (preview.thumbnail != null) {
            label.setIcon(preview.thumbnail);
        }
        
        // Filename
        html.append("<br><b>").append(escapeHtml(preview.file.getName())).append("</b><br><br>");
        
        // Metadata
        if (preview.metadata != null) {
            html.append("<font size='2'>").append(escapeHtml(preview.metadata).replace("\n", "<br>")).append("</font>");
        }
        
        html.append("</center></html>");
        label.setText(html.toString());
        
        return label;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a scaled thumbnail preserving aspect ratio.
     */
    private static BufferedImage createThumbnail(BufferedImage original, int targetSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        // Calculate scale to fit within target size
        double scale = Math.min((double) targetSize / width, (double) targetSize / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        
        BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = thumbnail.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        
        return thumbnail;
    }
    
    /**
     * Formats a file size in human-readable form.
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " bytes";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
    
    /**
     * Escapes HTML special characters.
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
    
    /**
     * Clears the thumbnail cache.
     */
    public static void clearCache() {
        THUMBNAIL_CACHE.clear();
    }
    
    /**
     * Shuts down the preview executor.
     * Call when the file chooser is closed.
     */
    public static void shutdown() {
        previewExecutor.shutdownNow();
    }
}
