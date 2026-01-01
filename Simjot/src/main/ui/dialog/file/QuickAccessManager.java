/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Manages quick access locations (bookmarks/favorites) for the file chooser.
 * 
 * <p>Provides both system locations (Home, Desktop, Documents, Downloads)
 * and user-defined bookmarks that persist across sessions.</p>
 * 
 * @since 1.0
 */
public final class QuickAccessManager {
    
    private static final String BOOKMARKS_FILE = "file-bookmarks.properties";
    
    // System locations (always available)
    private final Map<String, File> systemLocations = new LinkedHashMap<>();
    
    // User bookmarks (persisted)
    private final Map<String, File> userBookmarks = new LinkedHashMap<>();
    
    // Config directory for persistence
    private File configDir;
    
    /**
     * Creates a new QuickAccessManager with default system locations.
     */
    public QuickAccessManager() {
        initSystemLocations();
    }
    
    /**
     * Creates a new QuickAccessManager with a config directory for persistence.
     * 
     * @param configDir Directory to store bookmark settings
     */
    public QuickAccessManager(File configDir) {
        this();
        this.configDir = configDir;
        loadBookmarks();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SYSTEM LOCATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void initSystemLocations() {
        String home = System.getProperty("user.home");
        
        addSystemLocation("Home", new File(home));
        addSystemLocation("Desktop", new File(home, "Desktop"));
        addSystemLocation("Documents", new File(home, "Documents"));
        addSystemLocation("Downloads", new File(home, "Downloads"));
        
        // Platform-specific locations
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            addSystemLocation("Applications", new File("/Applications"));
            addSystemLocation("iCloud", new File(home, "Library/Mobile Documents/com~apple~CloudDocs"));
        } else if (os.contains("win")) {
            addSystemLocation("Pictures", new File(home, "Pictures"));
            addSystemLocation("Music", new File(home, "Music"));
            addSystemLocation("Videos", new File(home, "Videos"));
        } else {
            // Linux
            addSystemLocation("Pictures", new File(home, "Pictures"));
            addSystemLocation("Music", new File(home, "Music"));
            addSystemLocation("Videos", new File(home, "Videos"));
        }
    }
    
    private void addSystemLocation(String name, File directory) {
        if (directory.exists() && directory.isDirectory()) {
            systemLocations.put(name, directory);
        }
    }
    
    /**
     * Gets all system locations.
     * 
     * @return Unmodifiable map of name to directory
     */
    public Map<String, File> getSystemLocations() {
        return Collections.unmodifiableMap(systemLocations);
    }
    
    /**
     * Gets file system root directories.
     * 
     * @return List of root directories
     */
    public List<File> getFileSystemRoots() {
        List<File> roots = new ArrayList<>();
        for (File root : File.listRoots()) {
            if (root.exists()) {
                roots.add(root);
            }
        }
        return roots;
    }
    
    /**
     * Gets the display name for a file system root.
     */
    public String getRootDisplayName(File root) {
        String path = root.getAbsolutePath();
        
        // macOS
        if (path.equals("/")) {
            return "Macintosh HD";
        }
        
        // Windows drive letters
        if (path.matches("[A-Z]:\\\\")) {
            return path.substring(0, 2);
        }
        
        return path;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // USER BOOKMARKS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Adds a user bookmark.
     * 
     * @param name Display name for the bookmark
     * @param directory The directory to bookmark
     */
    public void addBookmark(String name, File directory) {
        if (name == null || name.isBlank() || directory == null) return;
        if (!directory.exists() || !directory.isDirectory()) return;
        
        userBookmarks.put(name, directory);
        saveBookmarks();
    }
    
    /**
     * Adds a bookmark using the directory name as the display name.
     */
    public void addBookmark(File directory) {
        if (directory == null) return;
        String name = directory.getName();
        if (name.isEmpty()) name = directory.getAbsolutePath();
        addBookmark(name, directory);
    }
    
    /**
     * Removes a user bookmark.
     * 
     * @param name The bookmark name to remove
     */
    public void removeBookmark(String name) {
        if (userBookmarks.remove(name) != null) {
            saveBookmarks();
        }
    }
    
    /**
     * Checks if a directory is bookmarked.
     */
    public boolean isBookmarked(File directory) {
        if (directory == null) return false;
        return userBookmarks.containsValue(directory);
    }
    
    /**
     * Gets all user bookmarks.
     * 
     * @return Unmodifiable map of name to directory
     */
    public Map<String, File> getUserBookmarks() {
        return Collections.unmodifiableMap(userBookmarks);
    }
    
    /**
     * Gets all quick access items (system + user).
     * 
     * @return Combined map of all quick access locations
     */
    public Map<String, File> getAllQuickAccess() {
        Map<String, File> all = new LinkedHashMap<>();
        all.putAll(systemLocations);
        all.putAll(userBookmarks);
        return all;
    }
    
    /**
     * Clears all user bookmarks.
     */
    public void clearBookmarks() {
        userBookmarks.clear();
        saveBookmarks();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void loadBookmarks() {
        if (configDir == null) return;
        
        File file = new File(configDir, BOOKMARKS_FILE);
        if (!file.exists()) return;
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            for (String name : props.stringPropertyNames()) {
                String path = props.getProperty(name);
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    userBookmarks.put(name, dir);
                }
            }
        } catch (IOException e) {
            // Silently fail - bookmarks are not critical
        }
    }
    
    private void saveBookmarks() {
        if (configDir == null) return;
        
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File file = new File(configDir, BOOKMARKS_FILE);
        Properties props = new Properties();
        
        for (Map.Entry<String, File> entry : userBookmarks.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue().getAbsolutePath());
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Simjot File Chooser Bookmarks");
        } catch (IOException e) {
            // Silently fail
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the home directory.
     */
    public static File getHomeDirectory() {
        return new File(System.getProperty("user.home"));
    }
    
    /**
     * Gets the desktop directory.
     */
    public static File getDesktopDirectory() {
        return new File(System.getProperty("user.home"), "Desktop");
    }
    
    /**
     * Gets the documents directory.
     */
    public static File getDocumentsDirectory() {
        return new File(System.getProperty("user.home"), "Documents");
    }
    
    /**
     * Gets the downloads directory.
     */
    public static File getDownloadsDirectory() {
        return new File(System.getProperty("user.home"), "Downloads");
    }
    
    /**
     * Gets the temporary directory.
     */
    public static File getTempDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }
    
    /**
     * Checks if a directory is a system location.
     */
    public boolean isSystemLocation(File directory) {
        return systemLocations.containsValue(directory);
    }
    
    /**
     * Resolves a path that may contain ~ for home directory.
     */
    public static File resolvePath(String path) {
        if (path == null) return null;
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return new File(path);
    }
}
