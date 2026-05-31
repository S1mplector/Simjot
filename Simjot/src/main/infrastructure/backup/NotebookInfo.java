/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

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
    private final String backgroundImagePath; // null means use global editor background
    private final String coverImagePath; // null means use background as cover preview
    private final String editorFontFamily; // null means use global editor font
    private final String editorStylePreset; // null/default means use global editor rhythm

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
        this(name, type, folder, createdMillis, iconId, description, accentColor, clusterId,
                customIconPath, null, null, null);
    }

    /** Full constructor with lightweight writing personalization options */
    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId,
                        String description, int accentColor, String clusterId, String customIconPath,
                        String backgroundImagePath, String editorFontFamily, String editorStylePreset) {
        this(name, type, folder, createdMillis, iconId, description, accentColor, clusterId,
                customIconPath, backgroundImagePath, null, editorFontFamily, editorStylePreset);
    }

    /** Full constructor with lightweight writing personalization options */
    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId,
                        String description, int accentColor, String clusterId, String customIconPath,
                        String backgroundImagePath, String coverImagePath, String editorFontFamily, String editorStylePreset) {
        this.name = name;
        this.type = type;
        this.folder = folder;
        this.createdMillis = createdMillis;
        this.iconId = iconId == null ? "legacy" : iconId;
        this.description = description == null ? "" : description;
        this.accentColor = accentColor;
        this.clusterId = clusterId;
        this.customIconPath = customIconPath;
        this.backgroundImagePath = blankToNull(backgroundImagePath);
        this.coverImagePath = blankToNull(coverImagePath);
        this.editorFontFamily = blankToNull(editorFontFamily);
        this.editorStylePreset = normalizePreset(editorStylePreset);
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
    public String getBackgroundImagePath() { return backgroundImagePath; }
    public String getCoverImagePath() { return coverImagePath; }
    public String getEditorFontFamily() { return editorFontFamily; }
    public String getEditorStylePreset() { return editorStylePreset; }
    
    public static Color defaultAccentFor(Type type) {
        Type safeType = type == null ? Type.JOURNAL : type;
        return switch (safeType) {
            case POETRY -> new Color(147, 112, 219); // Medium purple
            case JOURNAL -> new Color(100, 149, 237); // Cornflower blue
            case NOTETAKING -> new Color(60, 179, 113); // Medium sea green
        };
    }

    /** Returns the accent color, or a default if none set */
    public Color getAccentColor() {
        if (accentColor == -1) return defaultAccentFor(type);
        return new Color(accentColor, true);
    }
    
    /** Check if notebook is part of a cluster */
    public boolean isClustered() {
        return clusterId != null && !clusterId.isEmpty();
    }
    
    /** Create a copy with a new cluster assignment */
    public NotebookInfo withCluster(String newClusterId) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, description, accentColor, newClusterId,
                customIconPath, backgroundImagePath, coverImagePath, editorFontFamily, editorStylePreset);
    }
    
    /** Create a copy with updated customization */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, newDescription, newAccentColor, clusterId,
                customIconPath, backgroundImagePath, coverImagePath, editorFontFamily, editorStylePreset);
    }
    
    /** Create a copy with updated customization including custom icon */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor, String newCustomIconPath) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, newDescription, newAccentColor, clusterId,
                newCustomIconPath, backgroundImagePath, coverImagePath, editorFontFamily, editorStylePreset);
    }

    /** Create a copy with updated lightweight writing personalization. */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor, String newCustomIconPath,
                                          String newBackgroundImagePath, String newEditorFontFamily,
                                          String newEditorStylePreset) {
        return withCustomization(newDescription, newAccentColor, newCustomIconPath, newBackgroundImagePath,
                coverImagePath, newEditorFontFamily, newEditorStylePreset);
    }

    /** Create a copy with updated lightweight writing personalization and cover. */
    public NotebookInfo withCustomization(String newDescription, int newAccentColor, String newCustomIconPath,
                                          String newBackgroundImagePath, String newCoverImagePath,
                                          String newEditorFontFamily, String newEditorStylePreset) {
        return new NotebookInfo(name, type, folder, createdMillis, iconId, newDescription, newAccentColor, clusterId,
                newCustomIconPath, newBackgroundImagePath, newCoverImagePath, newEditorFontFamily, newEditorStylePreset);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizePreset(String preset) {
        String trimmed = blankToNull(preset);
        return trimmed == null ? null : trimmed;
    }

    @Override public String toString(){ return name; }
} 
