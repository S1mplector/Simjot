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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects file types using multiple strategies:
 * <ol>
 *   <li>Magic bytes (file signature) for accurate detection</li>
 *   <li>File extension mapping as fallback</li>
 *   <li>Content analysis for text vs binary</li>
 * </ol>
 * 
 * @since 1.0
 */
public final class FileTypeDetector {
    
    private FileTypeDetector() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MIME TYPE MAPPINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final Map<String, String> EXTENSION_TO_MIME = new HashMap<>();
    private static final Map<String, String> MIME_TO_DESCRIPTION = new HashMap<>();
    
    static {
        // Text
        addMime("txt", "text/plain", "Plain Text");
        addMime("md", "text/markdown", "Markdown");
        addMime("rtf", "application/rtf", "Rich Text");
        addMime("html", "text/html", "HTML Document");
        addMime("htm", "text/html", "HTML Document");
        addMime("css", "text/css", "CSS Stylesheet");
        addMime("js", "application/javascript", "JavaScript");
        addMime("json", "application/json", "JSON");
        addMime("xml", "application/xml", "XML Document");
        addMime("yaml", "application/x-yaml", "YAML");
        addMime("yml", "application/x-yaml", "YAML");
        addMime("csv", "text/csv", "CSV Data");
        
        // Documents
        addMime("pdf", "application/pdf", "PDF Document");
        addMime("doc", "application/msword", "Word Document");
        addMime("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document");
        addMime("xls", "application/vnd.ms-excel", "Excel Spreadsheet");
        addMime("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Spreadsheet");
        addMime("ppt", "application/vnd.ms-powerpoint", "PowerPoint");
        addMime("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint");
        addMime("odt", "application/vnd.oasis.opendocument.text", "OpenDocument Text");
        addMime("ods", "application/vnd.oasis.opendocument.spreadsheet", "OpenDocument Spreadsheet");
        
        // Images
        addMime("jpg", "image/jpeg", "JPEG Image");
        addMime("jpeg", "image/jpeg", "JPEG Image");
        addMime("png", "image/png", "PNG Image");
        addMime("gif", "image/gif", "GIF Image");
        addMime("bmp", "image/bmp", "Bitmap Image");
        addMime("svg", "image/svg+xml", "SVG Image");
        addMime("webp", "image/webp", "WebP Image");
        addMime("ico", "image/x-icon", "Icon");
        addMime("tiff", "image/tiff", "TIFF Image");
        addMime("tif", "image/tiff", "TIFF Image");
        addMime("heic", "image/heic", "HEIC Image");
        addMime("raw", "image/raw", "RAW Image");
        
        // Audio
        addMime("mp3", "audio/mpeg", "MP3 Audio");
        addMime("wav", "audio/wav", "WAV Audio");
        addMime("flac", "audio/flac", "FLAC Audio");
        addMime("aac", "audio/aac", "AAC Audio");
        addMime("ogg", "audio/ogg", "OGG Audio");
        addMime("m4a", "audio/mp4", "M4A Audio");
        addMime("wma", "audio/x-ms-wma", "WMA Audio");
        addMime("aiff", "audio/aiff", "AIFF Audio");
        
        // Video
        addMime("mp4", "video/mp4", "MP4 Video");
        addMime("avi", "video/x-msvideo", "AVI Video");
        addMime("mkv", "video/x-matroska", "MKV Video");
        addMime("mov", "video/quicktime", "QuickTime Video");
        addMime("wmv", "video/x-ms-wmv", "WMV Video");
        addMime("flv", "video/x-flv", "FLV Video");
        addMime("webm", "video/webm", "WebM Video");
        addMime("m4v", "video/x-m4v", "M4V Video");
        
        // Archives
        addMime("zip", "application/zip", "ZIP Archive");
        addMime("rar", "application/x-rar-compressed", "RAR Archive");
        addMime("7z", "application/x-7z-compressed", "7-Zip Archive");
        addMime("tar", "application/x-tar", "TAR Archive");
        addMime("gz", "application/gzip", "GZIP Archive");
        addMime("bz2", "application/x-bzip2", "BZIP2 Archive");
        addMime("xz", "application/x-xz", "XZ Archive");
        addMime("dmg", "application/x-apple-diskimage", "Disk Image");
        addMime("iso", "application/x-iso9660-image", "ISO Image");
        
        // Code
        addMime("java", "text/x-java-source", "Java Source");
        addMime("py", "text/x-python", "Python Script");
        addMime("c", "text/x-c", "C Source");
        addMime("cpp", "text/x-c++", "C++ Source");
        addMime("h", "text/x-c", "C Header");
        addMime("cs", "text/x-csharp", "C# Source");
        addMime("rb", "text/x-ruby", "Ruby Script");
        addMime("go", "text/x-go", "Go Source");
        addMime("rs", "text/x-rust", "Rust Source");
        addMime("swift", "text/x-swift", "Swift Source");
        addMime("kt", "text/x-kotlin", "Kotlin Source");
        addMime("scala", "text/x-scala", "Scala Source");
        addMime("php", "text/x-php", "PHP Script");
        addMime("sql", "application/sql", "SQL Script");
        addMime("sh", "application/x-sh", "Shell Script");
        addMime("bash", "application/x-sh", "Bash Script");
        
        // Executables
        addMime("exe", "application/x-msdownload", "Windows Executable");
        addMime("app", "application/x-mach-binary", "macOS Application");
        addMime("jar", "application/java-archive", "Java Archive");
        addMime("msi", "application/x-msi", "Windows Installer");
        addMime("deb", "application/x-deb", "Debian Package");
        addMime("rpm", "application/x-rpm", "RPM Package");
        
        // Simjot specific
        addMime("sjcrypt", "application/x-simjot-encrypted", "Simjot Encrypted File");
        addMime("sjbackup", "application/x-simjot-backup", "Simjot Backup");
        addMime("sjentry", "application/x-simjot-entry", "Simjot Journal Entry");
        addMime("sjpoem", "application/x-simjot-poem", "Simjot Poem");
    }
    
    private static void addMime(String ext, String mime, String description) {
        EXTENSION_TO_MIME.put(ext.toLowerCase(), mime);
        MIME_TO_DESCRIPTION.put(mime, description);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAGIC BYTES (FILE SIGNATURES)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final byte[] MAGIC_PDF = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47}; // .PNG
    private static final byte[] MAGIC_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_GIF = {0x47, 0x49, 0x46, 0x38}; // GIF8
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04}; // PK..
    private static final byte[] MAGIC_RAR = {0x52, 0x61, 0x72, 0x21}; // Rar!
    private static final byte[] MAGIC_7Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF};
    private static final byte[] MAGIC_GZIP = {0x1F, (byte) 0x8B};
    private static final byte[] MAGIC_MP3_ID3 = {0x49, 0x44, 0x33}; // ID3
    private static final byte[] MAGIC_MP3_SYNC = {(byte) 0xFF, (byte) 0xFB};
    private static final byte[] MAGIC_WAV = {0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] MAGIC_MP4 = {0x00, 0x00, 0x00}; // + ftyp at offset 4
    private static final byte[] MAGIC_SIMJOT = {0x53, 0x4A, 0x4F, 0x54}; // SJOT
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Detects the MIME type of a file.
     * Uses magic bytes first, then falls back to extension.
     * 
     * @param file The file to analyze
     * @return The detected MIME type, or "application/octet-stream" if unknown
     */
    public static String detectMimeType(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return "application/octet-stream";
        }
        
        // Try magic bytes first
        String magicMime = detectByMagicBytes(file);
        if (magicMime != null) {
            return magicMime;
        }
        
        // Fall back to extension
        return getMimeTypeByExtension(file);
    }
    
    /**
     * Gets the MIME type based on file extension only.
     */
    public static String getMimeTypeByExtension(File file) {
        String ext = getExtension(file);
        if (ext == null) return "application/octet-stream";
        return EXTENSION_TO_MIME.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }
    
    /**
     * Gets a human-readable description for a file type.
     */
    public static String getFileTypeDescription(File file) {
        if (file == null) return "Unknown";
        if (file.isDirectory()) return "Folder";
        
        String mime = detectMimeType(file);
        String description = MIME_TO_DESCRIPTION.get(mime);
        
        if (description != null) {
            return description;
        }
        
        // Generate description from extension
        String ext = getExtension(file);
        if (ext != null && !ext.isEmpty()) {
            return ext.toUpperCase() + " File";
        }
        
        return "File";
    }
    
    /**
     * Checks if a file is a text file (readable as text).
     */
    public static boolean isTextFile(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }
        
        String mime = detectMimeType(file);
        if (mime.startsWith("text/")) return true;
        if (mime.equals("application/json")) return true;
        if (mime.equals("application/xml")) return true;
        if (mime.equals("application/javascript")) return true;
        
        // Check content for binary bytes
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Math.min(8192, (int) file.length())];
            int read = fis.read(buffer);
            if (read <= 0) return true; // Empty file
            
            int nullCount = 0;
            for (int i = 0; i < read; i++) {
                byte b = buffer[i];
                // Count null bytes and other control characters
                if (b == 0) {
                    nullCount++;
                    if (nullCount > 1) return false; // Binary
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Checks if a file is an image.
     */
    public static boolean isImage(File file) {
        String mime = detectMimeType(file);
        return mime.startsWith("image/");
    }
    
    /**
     * Checks if a file is audio.
     */
    public static boolean isAudio(File file) {
        String mime = detectMimeType(file);
        return mime.startsWith("audio/");
    }
    
    /**
     * Checks if a file is video.
     */
    public static boolean isVideo(File file) {
        String mime = detectMimeType(file);
        return mime.startsWith("video/");
    }
    
    /**
     * Checks if a file is an archive.
     */
    public static boolean isArchive(File file) {
        String mime = detectMimeType(file);
        return mime.contains("zip") || mime.contains("rar") || 
               mime.contains("7z") || mime.contains("tar") ||
               mime.contains("gzip") || mime.contains("bzip");
    }
    
    /**
     * Checks if a file is a Simjot encrypted file.
     */
    public static boolean isSimjotEncrypted(File file) {
        if (file == null || !file.exists()) return false;
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read < 4) return false;
            return startsWith(header, MAGIC_SIMJOT);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Gets the file extension.
     */
    public static String getExtension(File file) {
        if (file == null) return null;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            return name.substring(dotIndex + 1).toLowerCase();
        }
        return null;
    }
    
    /**
     * Gets the file name without extension.
     */
    public static String getNameWithoutExtension(File file) {
        if (file == null) return null;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }
        return name;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAGIC BYTE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static String detectByMagicBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 2) return null;
            
            // Check known signatures
            if (startsWith(header, MAGIC_SIMJOT)) return "application/x-simjot-encrypted";
            if (startsWith(header, MAGIC_PDF)) return "application/pdf";
            if (startsWith(header, MAGIC_PNG)) return "image/png";
            if (startsWith(header, MAGIC_JPG)) return "image/jpeg";
            if (startsWith(header, MAGIC_GIF)) return "image/gif";
            if (startsWith(header, MAGIC_ZIP)) return "application/zip";
            if (startsWith(header, MAGIC_RAR)) return "application/x-rar-compressed";
            if (startsWith(header, MAGIC_7Z)) return "application/x-7z-compressed";
            if (startsWith(header, MAGIC_GZIP)) return "application/gzip";
            if (startsWith(header, MAGIC_MP3_ID3) || startsWith(header, MAGIC_MP3_SYNC)) return "audio/mpeg";
            if (startsWith(header, MAGIC_WAV)) return "audio/wav";
            
            // MP4 check (ftyp at offset 4)
            if (read >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
                return "video/mp4";
            }
            
        } catch (IOException e) {
            // Fall through
        }
        
        return null;
    }
    
    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
