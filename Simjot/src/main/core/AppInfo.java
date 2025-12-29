package main.core;

/**
 * Centralized application information.
 * Single source of truth for version, name, and other app metadata.
 */
public final class AppInfo {
    private AppInfo() {}

    public static final String NAME = "Simjot";
    public static final String VERSION = "1.0.0";
    public static final String AUTHOR = "Ilgaz Mehmetoglu";
    public static final String LICENSE = "Source-Available Personal Use License";
    
    /**
     * Returns formatted version string (e.g., "v1.0.0")
     */
    public static String versionString() {
        return "v" + VERSION;
    }
    
    /**
     * Returns full application title with version (e.g., "Simjot v1.0.0")
     */
    public static String fullTitle() {
        return NAME + " " + versionString();
    }
}
