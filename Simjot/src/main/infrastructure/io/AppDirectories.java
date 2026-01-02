/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.File;

/**
 * Centralized directory management for Simjot application file locations.
 * 
 * <p>This class provides a single source of truth for all file system locations used by Simjot.
 * On first launch, the user chooses a root "Simjot" folder, and this class automatically creates
 * and manages the subdirectory structure within that root. This eliminates path duplication
 * throughout the application and provides a clean interface for directory access.</p>
 * 
 * <p><strong>Directory Structure:</strong></p>
 * <pre>
 * Simjot/ (user-selected root)
 * ├── notebooks/          # All notebook files and subdirectories
 * ├── mood/               # Mood tracking data and analytics
 * ├── settings/           # Application preferences and configuration
 * ├── wallpapers/         # Custom background images
 * ├── fonts/              # User-created custom fonts
 * └── [legacy folders]    # Older content types (entries, poems, drawings, tasks)
 * </pre>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Initialize root directory (typically on app startup)
 * File userChosenRoot = new File("/path/to/user/Simjot");
 * AppDirectories.setRoot(userChosenRoot);
 * 
 * // Get specific directories
 * File notebooksDir = AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
 * File settingsDir = AppDirectories.folder(AppDirectories.Type.SETTINGS);
 * File moodDir = AppDirectories.folder(AppDirectories.Type.MOOD_DATA);
 * 
 * // Get root directory
 * File root = AppDirectories.getRoot();
 * }</pre>
 * 
 * @author Simjot Development Team
 * @since 1.0.0
 */
public final class AppDirectories {

    /**
     * Enumeration of directory types managed by the application.
     * 
     * <p>Each type represents a specific category of content with its own subdirectory
     * within the root Simjot folder. Legacy types are maintained for backward compatibility
     * but are no longer automatically created.</p>
     */
    public enum Type {
        /**
         * Legacy directory for journal entries.
         * No longer auto-created in favor of notebook-based organization.
         */
        ENTRIES("entries"),
        
        /**
         * Legacy directory for poetry files.
         * No longer auto-created in favor of notebook-based organization.
         */
        POEMS("poems"),
        
        /**
         * Legacy directory for drawing files.
         * No longer auto-created in favor of notebook-based organization.
         */
        DRAWINGS("drawings"),
        
        /**
         * Legacy directory for task files.
         * No longer auto-created in favor of notebook-based organization.
         */
        TASKS("tasks"),

        /**
         * Active directory for all notebook files and subdirectories.
         * This is the primary content storage location in the modern architecture.
         */
        NOTEBOOKS("notebooks"),
        
        /**
         * Directory for mood tracking data and analytics files.
         * Stores mood entries, charts, and analysis results.
         */
        MOOD_DATA("mood"),
        
        /**
         * Directory for application settings and preferences.
         * Contains configuration files and user preferences.
         */
        SETTINGS("settings"),
        
        /**
         * Directory for custom wallpaper and background images.
         * Stores user-uploaded and generated background files.
         */
        WALLPAPERS("wallpapers"),
        
        /**
         * Directory for user-created custom fonts.
         * Stores .sjf font files created in the Custom Font Studio.
         */
        CUSTOM_FONTS("fonts");

        /** The folder name for this directory type. */
        private final String folderName;
        
        /**
         * Creates a new directory type with the specified folder name.
         * 
         * @param folderName The name of the subdirectory within the root folder
         */
        Type(String folderName) { this.folderName = folderName; }
        
        /**
         * Gets the folder name for this directory type.
         * 
         * @return The folder name as a string
         */
        public String folderName() { return folderName; }
    }

    /**
     * The root directory chosen by the user on first launch.
     * This is the parent directory for all Simjot content.
     */
    private static File root; // chosen by user on first launch

    /** Private constructor to prevent instantiation of utility class. */
    private AppDirectories() {}

    /**
     * Sets the root directory for all Simjot content.
     * 
     * <p>This should be called once during application initialization, typically after
     * the user selects their preferred location for storing Simjot data.</p>
     * 
     * @param rootFolder The root directory to use for all Simjot content
     * @throws IllegalArgumentException if rootFolder is null
     */
    public static void setRoot(File rootFolder) {
        root = rootFolder;
    }

    /**
     * Gets the root directory for Simjot content.
     * 
     * <p>This method ensures the root directory has been initialized before returning it.
     * If the root has not been set, an IllegalStateException is thrown.</p>
     * 
     * @return The root directory file
     * @throws IllegalStateException if root directory has not been initialized
     */
    public static File getRoot() {
        ensureRootInitialized();
        if(root == null) throw new IllegalStateException("Root folder not initialised yet");
        return root;
    }

    /**
     * Gets the directory for a specific type of content.
     * 
     * <p>This method returns the appropriate subdirectory within the root folder
     * for the specified content type. The directory is created if it doesn't exist.</p>
     * 
     * @param t The type of directory to retrieve
     * @return The directory file for the specified type
     * @throws IllegalStateException if root directory has not been initialized
     * @throws IllegalArgumentException if type is null
     */
    public static File folder(Type t) {
        ensureRootInitialized();
        if(root == null) throw new IllegalStateException("Root folder not initialised yet");
        File f = new File(root, t.folderName());
        // Do not auto-create legacy folders we no longer use.
        switch (t) {
            case ENTRIES:
            case POEMS:
            case DRAWINGS:
            case TASKS:
                // return as-is; caller can choose to create if absolutely needed
                return f;
            default:
                if(!f.exists()) f.mkdirs();
                return f;
        }
    }

    private static void ensureRootInitialized() {
        if (root != null) return;
        try {
            File cfg = new File(System.getProperty("user.home"), ".simjournal_config.txt");
            if (!cfg.exists()) return;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cfg))) {
                String path = reader.readLine();
                if (path == null || path.isBlank()) return;
                File folder = new File(path.trim());
                if (folder.exists() && folder.isDirectory()) {
                    root = folder;
                }
            }
        } catch (Throwable ignored) {}
    }
}
