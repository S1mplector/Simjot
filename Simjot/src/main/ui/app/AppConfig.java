/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;

/**
 * Manages application configuration files including the root folder path.
 * Uses native file I/O when available for atomic writes and reliable verification.
 */
public final class AppConfig {
    
    /** Configuration filename stored in user's home directory */
    public static final String CONFIG_FILENAME = ".simjournal_config.txt";
    
    /** Configuration file reference */
    private static File configFile;
    
    /** Cached root folder */
    private static File rootFolder;
    
    private AppConfig() {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the configuration system.
     * Should be called early during application startup.
     */
    public static void initialize() {
        configFile = new File(System.getProperty("user.home"), CONFIG_FILENAME);
    }
    
    /**
     * Get the configuration file.
     */
    public static File getConfigFile() {
        if (configFile == null) {
            initialize();
        }
        return configFile;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ROOT FOLDER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Load the root folder from configuration.
     * Uses native read with verification when available.
     * 
     * @return The root folder, or null if not configured or invalid
     */
    public static File loadRootFolder() {
        if (configFile == null) {
            initialize();
        }
        
        // Try native config read first (more reliable verification)
        String nativePath = NativeAccess.readConfig(configFile.getAbsolutePath());
        if (nativePath != null) {
            File folder = new File(nativePath);
            if (NativeAccess.isSetupComplete(folder.getAbsolutePath())) {
                rootFolder = folder;
                AppDirectories.setRoot(rootFolder);
                return rootFolder;
            }
        }
        
        // Fallback: try Java config read
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String path = reader.readLine();
                if (path != null && !path.isBlank()) {
                    File folder = new File(path.trim());
                    if (folder.exists() && folder.isDirectory()) {
                        rootFolder = folder;
                        AppDirectories.setRoot(rootFolder);
                        return rootFolder;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        return null;
    }
    
    /**
     * Save the root folder to configuration.
     * Uses native atomic write when available.
     * 
     * @param folder The root folder to save
     * @return true if saved successfully
     */
    public static boolean saveRootFolder(File folder) {
        if (folder == null) return false;
        
        if (configFile == null) {
            initialize();
        }
        
        rootFolder = folder;
        
        // Use native atomic write for reliability
        boolean written = NativeAccess.writeConfig(
            configFile.getAbsolutePath(),
            folder.getAbsolutePath()
        );
        
        // Fallback to Java if native fails
        if (!written) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.println(folder.getAbsolutePath());
                return true;
            } catch (IOException ex) {
                ex.printStackTrace();
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get the current root folder.
     * Returns cached value or loads from config.
     */
    public static File getRootFolder() {
        if (rootFolder != null) {
            return rootFolder;
        }
        return loadRootFolder();
    }
    
    /**
     * Set the root folder (without saving to config).
     */
    public static void setRootFolder(File folder) {
        rootFolder = folder;
        if (folder != null) {
            AppDirectories.setRoot(folder);
        }
    }
    
    /**
     * Check if a root folder is configured and valid.
     */
    public static boolean hasValidRootFolder() {
        File root = getRootFolder();
        return root != null && root.exists() && root.isDirectory();
    }
    
    /**
     * Check if first-time setup is needed.
     */
    public static boolean needsSetup() {
        if (configFile == null) {
            initialize();
        }
        return NativeAccess.needsSetup(configFile.getAbsolutePath());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECTORY SETUP
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Ensure all required subdirectories exist.
     */
    public static void ensureDirectoriesExist() {
        if (rootFolder == null) return;
        
        AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
        AppDirectories.folder(AppDirectories.Type.MOOD_DATA);
        AppDirectories.folder(AppDirectories.Type.SETTINGS);
        AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
    }
    
    /**
     * Initialize the directory structure using native setup.
     * 
     * @param folder The root folder
     * @return 0 on success, negative error code on failure
     */
    public static int initializeDirectoryStructure(File folder) {
        if (folder == null) return -1;
        return NativeAccess.setupInit(folder.getAbsolutePath());
    }
    
    /**
     * Verify the setup is complete.
     * 
     * @return true if all directories are present and writable
     */
    public static boolean verifySetup() {
        if (rootFolder == null) return false;
        return NativeAccess.isSetupComplete(rootFolder.getAbsolutePath());
    }
    
    /**
     * Get detailed setup status.
     * 
     * @return Array: [root_exists, root_writable, notebooks_ok, mood_ok, 
     *                 settings_ok, wallpapers_ok, marker_valid, setup_complete]
     */
    public static int[] getSetupStatus() {
        if (rootFolder == null) return new int[8];
        return NativeAccess.setupStatus(rootFolder.getAbsolutePath());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPORARY FILES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Clean up old temporary files in the root folder.
     * 
     * @param extension File extension to clean (e.g., ".tmp")
     * @param maxAgeMs Maximum age in milliseconds
     */
    public static void cleanupTempFiles(String extension, long maxAgeMs) {
        if (rootFolder == null) return;
        
        try {
            main.infrastructure.io.FileIO.cleanupTempFiles(
                rootFolder.toPath(), extension, maxAgeMs
            );
        } catch (Throwable ignored) {}
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LAST OPENED FILE (stored in a separate file)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String LAST_FILE_FILENAME = ".simjot_last_opened";
    
    /**
     * Get the last opened file path.
     */
    public static String getLastOpenedFilePath() {
        try {
            File lastFile = new File(System.getProperty("user.home"), LAST_FILE_FILENAME);
            if (lastFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(lastFile))) {
                    return reader.readLine();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
    
    /**
     * Save the last opened file path.
     */
    public static void setLastOpenedFilePath(String path) {
        try {
            File lastFile = new File(System.getProperty("user.home"), LAST_FILE_FILENAME);
            if (path == null || path.isBlank()) {
                lastFile.delete();
            } else {
                try (PrintWriter writer = new PrintWriter(new FileWriter(lastFile))) {
                    writer.println(path);
                }
            }
        } catch (Throwable ignored) {}
    }
    
    /**
     * Get the last opened file if it exists and is readable.
     */
    public static File getLastOpenedFile() {
        String path = getLastOpenedFilePath();
        if (path == null || path.isBlank()) return null;
        
        File file = new File(path);
        if (file.exists() && file.isFile() && file.canRead()) {
            return file;
        }
        return null;
    }
}
