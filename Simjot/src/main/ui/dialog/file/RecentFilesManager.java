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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Manages recently accessed files and directories.
 * 
 * <p>Provides:</p>
 * <ul>
 *   <li>Recent files list (with configurable size limit)</li>
 *   <li>Recent directories list</li>
 *   <li>Persistence across sessions</li>
 *   <li>Automatic cleanup of non-existent files</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class RecentFilesManager {
    
    private static final String RECENT_FILES_PROP = "file.recent.";
    private static final String RECENT_DIRS_PROP = "dir.recent.";
    private static final String HISTORY_FILE = "file-history.properties";
    
    private static final int DEFAULT_MAX_FILES = 20;
    private static final int DEFAULT_MAX_DIRS = 10;
    
    private final LinkedList<File> recentFiles = new LinkedList<>();
    private final LinkedList<File> recentDirectories = new LinkedList<>();
    
    private final int maxFiles;
    private final int maxDirs;
    private final File configDir;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new manager with default limits (no persistence).
     */
    public RecentFilesManager() {
        this(null, DEFAULT_MAX_FILES, DEFAULT_MAX_DIRS);
    }
    
    /**
     * Creates a new manager with persistence.
     * 
     * @param configDir Directory to store history
     */
    public RecentFilesManager(File configDir) {
        this(configDir, DEFAULT_MAX_FILES, DEFAULT_MAX_DIRS);
    }
    
    /**
     * Creates a new manager with custom limits.
     * 
     * @param configDir Directory to store history (null for no persistence)
     * @param maxFiles Maximum number of recent files to track
     * @param maxDirs Maximum number of recent directories to track
     */
    public RecentFilesManager(File configDir, int maxFiles, int maxDirs) {
        this.configDir = configDir;
        this.maxFiles = maxFiles;
        this.maxDirs = maxDirs;
        load();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RECENT FILES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Adds a file to the recent list.
     * If the file already exists in the list, it's moved to the front.
     * 
     * @param file The file to add
     */
    public void addRecentFile(File file) {
        if (file == null || !file.exists() || file.isDirectory()) return;
        
        // Remove if already exists (will be re-added at front)
        recentFiles.remove(file);
        
        // Add to front
        recentFiles.addFirst(file);
        
        // Trim to max size
        while (recentFiles.size() > maxFiles) {
            recentFiles.removeLast();
        }
        
        save();
    }
    
    /**
     * Removes a file from the recent list.
     */
    public void removeRecentFile(File file) {
        if (recentFiles.remove(file)) {
            save();
        }
    }
    
    /**
     * Gets the list of recent files (most recent first).
     * 
     * @return Unmodifiable list of recent files
     */
    public List<File> getRecentFiles() {
        cleanupNonExistent(recentFiles);
        return Collections.unmodifiableList(new ArrayList<>(recentFiles));
    }
    
    /**
     * Gets recent files filtered by extension.
     * 
     * @param extensions File extensions to include (without dots)
     * @return Filtered list of recent files
     */
    public List<File> getRecentFiles(String... extensions) {
        if (extensions == null || extensions.length == 0) {
            return getRecentFiles();
        }
        
        List<String> extList = new ArrayList<>();
        for (String ext : extensions) {
            extList.add(ext.toLowerCase());
        }
        
        return getRecentFiles().stream()
            .filter(f -> {
                String name = f.getName().toLowerCase();
                return extList.stream().anyMatch(ext -> name.endsWith("." + ext));
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the most recently accessed file.
     */
    public File getMostRecentFile() {
        cleanupNonExistent(recentFiles);
        return recentFiles.isEmpty() ? null : recentFiles.getFirst();
    }
    
    /**
     * Clears all recent files.
     */
    public void clearRecentFiles() {
        recentFiles.clear();
        save();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RECENT DIRECTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Adds a directory to the recent list.
     * 
     * @param directory The directory to add
     */
    public void addRecentDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        
        // Remove if already exists
        recentDirectories.remove(directory);
        
        // Add to front
        recentDirectories.addFirst(directory);
        
        // Trim to max size
        while (recentDirectories.size() > maxDirs) {
            recentDirectories.removeLast();
        }
        
        save();
    }
    
    /**
     * Gets the list of recent directories.
     */
    public List<File> getRecentDirectories() {
        cleanupNonExistent(recentDirectories);
        return Collections.unmodifiableList(new ArrayList<>(recentDirectories));
    }
    
    /**
     * Gets the most recently accessed directory.
     */
    public File getMostRecentDirectory() {
        cleanupNonExistent(recentDirectories);
        return recentDirectories.isEmpty() ? null : recentDirectories.getFirst();
    }
    
    /**
     * Clears all recent directories.
     */
    public void clearRecentDirectories() {
        recentDirectories.clear();
        save();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMBINED OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Adds a file and its parent directory to recent lists.
     */
    public void addRecent(File file) {
        if (file == null) return;
        
        if (file.isDirectory()) {
            addRecentDirectory(file);
        } else {
            addRecentFile(file);
            File parent = file.getParentFile();
            if (parent != null) {
                addRecentDirectory(parent);
            }
        }
    }
    
    /**
     * Clears all history.
     */
    public void clearAll() {
        recentFiles.clear();
        recentDirectories.clear();
        save();
    }
    
    /**
     * Gets the total count of tracked items.
     */
    public int getTotalCount() {
        return recentFiles.size() + recentDirectories.size();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void load() {
        if (configDir == null) return;
        
        File file = new File(configDir, HISTORY_FILE);
        if (!file.exists()) return;
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            // Load recent files
            for (int i = 0; i < maxFiles; i++) {
                String path = props.getProperty(RECENT_FILES_PROP + i);
                if (path == null) break;
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    recentFiles.add(f);
                }
            }
            
            // Load recent directories
            for (int i = 0; i < maxDirs; i++) {
                String path = props.getProperty(RECENT_DIRS_PROP + i);
                if (path == null) break;
                File d = new File(path);
                if (d.exists() && d.isDirectory()) {
                    recentDirectories.add(d);
                }
            }
        } catch (IOException e) {
            // Silently fail - history is not critical
        }
    }
    
    private void save() {
        if (configDir == null) return;
        
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File file = new File(configDir, HISTORY_FILE);
        Properties props = new Properties();
        
        // Save recent files
        int i = 0;
        for (File f : recentFiles) {
            props.setProperty(RECENT_FILES_PROP + i++, f.getAbsolutePath());
        }
        
        // Save recent directories
        i = 0;
        for (File d : recentDirectories) {
            props.setProperty(RECENT_DIRS_PROP + i++, d.getAbsolutePath());
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Simjot File History");
        } catch (IOException e) {
            // Silently fail
        }
    }
    
    /**
     * Removes files/directories that no longer exist.
     */
    private void cleanupNonExistent(LinkedList<File> list) {
        boolean changed = false;
        Iterator<File> it = list.iterator();
        while (it.hasNext()) {
            if (!it.next().exists()) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }
    
    /**
     * Forces a cleanup and save.
     */
    public void cleanup() {
        cleanupNonExistent(recentFiles);
        cleanupNonExistent(recentDirectories);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC CONVENIENCE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static RecentFilesManager sharedInstance;
    
    /**
     * Gets or creates a shared instance.
     * 
     * @param configDir Configuration directory for persistence
     * @return The shared instance
     */
    public static synchronized RecentFilesManager getShared(File configDir) {
        if (sharedInstance == null) {
            sharedInstance = new RecentFilesManager(configDir);
        }
        return sharedInstance;
    }
    
    /**
     * Gets the shared instance (must be initialized first).
     */
    public static RecentFilesManager getShared() {
        return sharedInstance;
    }
}
