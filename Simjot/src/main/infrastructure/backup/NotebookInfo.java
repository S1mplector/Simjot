package main.infrastructure.backup;

import java.awt.Color;
import java.io.File;

/**
 * Value object describing a user notebook with customization options.
 * It is meant to be immutable, and is used as a value object for notebooks.
 * @author S1mplector
 */
public class NotebookInfo {
    public enum Type { JOURNAL, POETRY, NOTETAKING }

    private final String name;
    private final Type type;
    private final File folder;
    private final long createdMillis;
    private final String iconId;
    
    // Customization options
    private final String description;
    private final int accentColor; // ARGB packed int, -1 means default
    private final String clusterId; // null means unclustered
    private final String customIconPath; // null means use default icon

    /** Legacy constructor for backward compatibility */
    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId) {
        this(name, type, folder, createdMillis, iconId, "", -1, null, null);
    }
    
    /** Constructor without custom icon (backward compatibility) */
    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId,
                        String description, int accentColor, String clusterId) {
        this(name, type, folder, createdMillis, iconId, description, accentColor, clusterId, null);
    }
    
    /** Full constructor with all customization options */
    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId,
                        String description, int accentColor, String clusterId, String customIconPath) {
        this.name = name;
        this.type = type;
        this.folder = folder;
        this.createdMillis = createdMillis;
        this.iconId = iconId == null ? "legacy" : iconId;
        this.description = description == null ? "" : description;
        this.accentColor = accentColor;
        this.clusterId = clusterId;
        this.customIconPath = customIconPath;
    }

    public String getName() { return name; }
    public Type getType()   { return type; }
    public File getFolder() { return folder; }
    public long getCreatedMillis() { return createdMillis; }
    public String getIconId(){ return iconId; }
    public String getDescription() { return description; }
    public int getAccentColorRaw() { return accentColor; }
    public String getClusterId() { return clusterId; }
    public String getCustomIconPath() { return customIconPath; }
    
    /** Returns the accent color, or a default if none set */
    public Color getAccentColor() {
        if (accentColor == -1) {
            // Default accent based on type
            return switch (type) {
                case POETRY -> new Color(147, 112, 219); // Medium purple
                case JOURNAL -> new Color(100, 149, 237); // Cornflower blue
                case NOTETAKING -> new Color(60, 179, 113); // Medium sea green
            };
        }
        return new Color(accentColor, true);
    }
    
    /** Check if notebook is part of a cluster */
    public boolean isClustered() {
        return clusterId != null && !clusterId.isEmpty();
    }
    
    /** Create a copy with a new cluster assignment */
    public NotebookInfo withCluster(String newClusterId) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, description, accentColor, newClusterId, customIconPath);
    }
    
    /** Create a copy with updated customization */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, newDescription, newAccentColor, clusterId, customIconPath);
    }
    
    /** Create a copy with updated customization including custom icon */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor, String newCustomIconPath) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, newDescription, newAccentColor, clusterId, newCustomIconPath);
    }

    @Override public String toString(){ return name; }
} 