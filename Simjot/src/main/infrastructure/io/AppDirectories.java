/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.File;
import main.infrastructure.ffi.NativeAccess;

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
     * Suggest an iCloud Drive path for Simjot on macOS, if available.
     * Does not change the current root.
     */
    public static File suggestedIcloudRoot() {
        String os = System.getProperty("os.name", "");
        if (os == null || !os.toLowerCase().contains("mac")) return null;
        String path = NativeAccess.getMacIcloudPath();
        if (path == null || path.isBlank()) {
            if (NativeAccess.isAvailable() && !NativeAccess.isMacIcloudAvailable()) return null;
            String home = System.getProperty("user.home");
            if (home != null && !home.isBlank()) {
                File cloudDocs = new File(home, "Library/Mobile Documents/com~apple~CloudDocs");
                if (cloudDocs.exists() && cloudDocs.isDirectory()) {
                    path = new File(cloudDocs, "Simjot").getAbsolutePath();
                }
            }
        }
        if (path == null || path.isBlank()) return null;
        return new File(path);
    }

    /**
     * Attempt to set the root to iCloud Drive (creates folders if needed).
     * @return true if iCloud path was available and root was set.
     */
    public static boolean trySetIcloudRoot() {
        File icloud = suggestedIcloudRoot();
        if (icloud == null) return false;
        if (!icloud.exists() && !icloud.mkdirs()) return false;
        setRoot(icloud);
        // Ensure active subdirectories exist
        folder(Type.NOTEBOOKS);
        folder(Type.MOOD_DATA);
        folder(Type.SETTINGS);
        folder(Type.WALLPAPERS);
        folder(Type.CUSTOM_FONTS);
        return true;
    }

    /**
     * Check if a folder is inside iCloud Drive on macOS.
     */
    public static boolean isIcloudRoot(File folder) {
        if (folder == null) return false;
        String os = System.getProperty("os.name", "");
        if (os == null || !os.toLowerCase().contains("mac")) return false;
        String path = folder.getAbsolutePath();
        if (path == null || path.isBlank()) return false;
        try {
            if (NativeAccess.isMacIcloudPath(path)) return true;
        } catch (Throwable ignored) {}
        File icloud = suggestedIcloudRoot();
        if (icloud == null) return false;
        try {
            java.nio.file.Path rootPath = folder.toPath().toAbsolutePath().normalize();
            java.nio.file.Path icloudPath = icloud.toPath().toAbsolutePath().normalize();
            return rootPath.equals(icloudPath) || rootPath.startsWith(icloudPath);
        } catch (Throwable ignored) {
            String icloudPath = icloud.getAbsolutePath();
            if (icloudPath == null || icloudPath.isBlank()) return false;
            String rootPath = path;
            if (rootPath.equals(icloudPath)) return true;
            String prefix = icloudPath.endsWith(File.separator) ? icloudPath : icloudPath + File.separator;
            return rootPath.startsWith(prefix);
        }
    }

    /**
     * Heuristic check for existing Simjot data under a folder.
     */
    public static boolean looksLikeSimjotRoot(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return false;
        if (new File(folder, ".simjot_setup").exists()) return true;
        String[] candidates = new String[] {
            "notebooks", "settings", "mood", "wallpapers", "fonts",
            "entries", "poems", "drawings", "tasks"
        };
        for (String name : candidates) {
            File f = new File(folder, name);
            if (f.exists()) return true;
        }
        return new File(folder, "notebooks.json").exists();
    }

    /**
     * Find an existing Simjot root inside iCloud Drive, if available.
     */
    public static File findExistingIcloudRoot() {
        File icloud = suggestedIcloudRoot();
        if (icloud == null || !icloud.exists() || !icloud.isDirectory()) return null;
        try {
            if (NativeAccess.isSetupComplete(icloud.getAbsolutePath())) return icloud;
        } catch (Throwable ignored) {}
        return estimateDataScore(icloud) > 0 ? icloud : null;
    }

    /**
     * Default Simjot root in the user's home folder.
     */
    public static File defaultLocalRoot() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) return null;
        return new File(home, "Simjot");
    }

    /**
     * Default Simjot root in the user's Documents folder.
     */
    public static File defaultDocumentsRoot() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) return null;
        return new File(new File(home, "Documents"), "Simjot");
    }

    /**
     * Estimate whether a root contains user data (higher score = more data).
     */
    public static int estimateDataScore(File rootFolder) {
        if (rootFolder == null || !rootFolder.exists() || !rootFolder.isDirectory()) return 0;
        boolean icloudRoot = isIcloudRoot(rootFolder);
        int score = 0;
        if (new File(rootFolder, ".simjot_setup").exists()) score += 2;

        File notebooksJson = new File(rootFolder, "notebooks.json");
        if (notebooksJson.isFile()) {
            score += 50;
            long len = notebooksJson.length();
            score += (int) Math.min(100, len / 256);
        } else if (icloudRoot) {
            score += icloudItemScore(notebooksJson, 50);
        }

        File notebooks = new File(rootFolder, "notebooks");
        int notebookCount = countChildren(notebooks, 64);
        if (notebookCount > 0) {
            score += 10;
            score += Math.min(50, notebookCount);
        } else if (icloudRoot) {
            score += icloudItemScore(notebooks, 10);
        }

        File prefs = new File(new File(rootFolder, "settings"), "preferences.properties");
        if (prefs.isFile()) {
            score += 5;
        } else if (icloudRoot) {
            score += icloudItemScore(prefs, 5);
        }

        if (countChildren(new File(rootFolder, "mood"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "wallpapers"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "fonts"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "entries"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "poems"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "drawings"), 1) > 0) score += 1;
        if (countChildren(new File(rootFolder, "tasks"), 1) > 0) score += 1;

        return score;
    }

    private static int icloudItemScore(File file, int baseScore) {
        if (file == null) return 0;
        int status = NativeAccess.getMacIcloudItemStatus(file.getAbsolutePath());
        if ((status & NativeAccess.ICLOUD_ITEM_EXISTS) == 0) return 0;
        int score = baseScore;
        if ((status & NativeAccess.ICLOUD_ITEM_DOWNLOADED) != 0) score += 2;
        if ((status & NativeAccess.ICLOUD_ITEM_CONFLICT) != 0) score += 1;
        return score;
    }

    /**
     * Choose the best existing root, preferring the given root if it contains data.
     */
    public static File chooseBestRoot(File preferred, File... candidates) {
        int preferredScore = estimateDataScore(preferred);
        if (preferred != null && preferred.exists() && preferred.isDirectory() && preferredScore > 0) {
            return preferred;
        }

        File best = null;
        int bestScore = -1;
        if (preferred != null && preferred.exists() && preferred.isDirectory()) {
            best = preferred;
            bestScore = preferredScore;
        }

        if (candidates != null) {
            for (File candidate : candidates) {
                if (candidate == null) continue;
                if (!candidate.exists() || !candidate.isDirectory()) continue;
                int score = estimateDataScore(candidate);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        if (bestScore > 0) return best;
        return (preferred != null && preferred.exists() && preferred.isDirectory()) ? preferred : null;
    }

    private static int countChildren(File dir, int max) {
        if (dir == null || max <= 0 || !dir.exists() || !dir.isDirectory()) return 0;
        File[] list = dir.listFiles();
        if (list == null || list.length == 0) return 0;
        return Math.min(max, list.length);
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
