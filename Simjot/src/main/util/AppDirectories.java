package main.util;

import java.io.File;

/**
 * Centralizes file system locations used by Simjot.
 *
 * On first launch the user chooses where the root "Simjot" folder lives.  
 * Within that root we automatically create (if missing) sub-directories for each
 * kind of content so the rest of the app can simply ask for the folder it
 * needs without reproducing path logic.
 */
public final class AppDirectories {

    public enum Type {
        ENTRIES("entries"),
        POEMS("poems"),
        DRAWINGS("drawings"),
        MOOD_DATA("mood"),
        SETTINGS("settings"),
        TASKS("tasks"),
        WALLPAPERS("wallpapers");

        private final String folderName;
        Type(String folderName) { this.folderName = folderName; }
        public String folderName() { return folderName; }
    }

    private static File root; // chosen by user on first launch

    private AppDirectories() {}

    public static void setRoot(File rootFolder) {
        root = rootFolder;
    }

    public static File getRoot() {
        if(root == null) throw new IllegalStateException("Root folder not initialised yet");
        return root;
    }

    public static File folder(Type t) {
        if(root == null) throw new IllegalStateException("Root folder not initialised yet");
        File f = new File(root, t.folderName());
        if(!f.exists()) f.mkdirs();
        return f;
    }
} 