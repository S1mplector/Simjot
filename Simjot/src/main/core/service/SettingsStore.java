/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;

/**
 * Singleton helper that persists user preferences under Simjot/settings/preferences.properties.
 * 
 * <p>This class manages all user-configurable settings for the Simjot application, including:
 * <ul>
 *   <li>UI preferences (themes, fonts, colors, animations)</li>
 *   <li>Editor settings (font sizes, line spacing, autocorrect)</li>
 *   <li>Backup and security configurations</li>
 *   <li>Widget and layout preferences</li>
 *   <li>Custom quotes and accent colors</li>
 * </ul>
 * 
 * <p>Settings are stored in a Java Properties file and automatically loaded on startup.
 * The class supports both in-memory mode (during early initialization) and persistent mode
 * (once the application directory structure is established).</p>
 * 
 * <p><strong>Note:</strong> This class is thread-safe. All public methods are synchronized
 * to ensure safe concurrent access from multiple threads.</p>
 * 
 * @author S1mplector
 *  */
public final class SettingsStore {

    /**
     * The filename for the preferences properties file.
     */
    private static final String FILE_NAME = "preferences.properties";

    /**
     * Internal properties storage for all settings.
     */
    private final Properties props = new Properties();
    
    /**
     * The file where settings are persisted. Null if in memory-only mode.
     */
    private final File storeFile;

    // ====================
    // SETTINGS KEYS
    // ====================
    
    // Font Settings
    private static final String KEY_JOURNAL_FONT = "journalFontSize";
    private static final String KEY_POEM_FONT    = "poemFontSize";
    private static final String KEY_EDITOR_FONT_FAMILY = "editorFontFamily";
    private static final String KEY_EDITOR_LINE_SPACING = "editorLineSpacing";
    
    // Theme and Appearance
    private static final String KEY_ANIMATION    = "animation";
    private static final String KEY_THEME        = "theme";
    private static final String KEY_GLOW         = "glowEnabled";
    private static final String KEY_BG_IMAGE     = "backgroundImage";
    private static final String KEY_BG_OPACITY   = "backgroundOpacity";
    private static final String KEY_ENTRY_BG_IMAGE = "entryBackgroundImage";
    private static final String KEY_ENTRY_BG_OPACITY = "entryBackgroundOpacity";
    private static final String KEY_POEM_BG_IMAGE = "poemBackgroundImage";
    private static final String KEY_POEM_BG_OPACITY = "poemBackgroundOpacity";
    private static final String KEY_EDITOR_GLASS_OPACITY = "editorGlassOpacity";
    private static final String KEY_EDITOR_PAPER_FEEL = "editorPaperFeelEnabled";
    private static final String KEY_EDITOR_TYPO_POLISH = "editorTypographyPolishEnabled";
    private static final String KEY_EDITOR_HEADER_STAMP = "editorHeaderStampEnabled";
    private static final String KEY_EDITOR_HEADER_LOCATION = "editorHeaderStampLocation";
    private static final String KEY_BRUSH_SIZE   = "defaultBrushSize";
    private static final String KEY_SMOOTHING    = "strokeSmoothing";
    private static final String KEY_THUMBNAILS   = "generateThumbnails";
    private static final String KEY_AUTOSAVE     = "autosaveMinutes"; // legacy (minutes)
    private static final String KEY_AUTOSAVE_DELAY_MS = "autosaveDelayMs"; // new (milliseconds)
    private static final String KEY_TUTORIAL_SEEN = "tutorialSeen";
    private static final String KEY_DISABLE_ANIMATIONS = "disableAnimations";
    private static final String KEY_DISABLE_MAIN_MENU_ANIMATIONS = "disableMainMenuAnimations";
    private static final String KEY_BREATHING_OVERLAY = "breathingOverlayEnabled";
    private static final String KEY_SHOW_WIDGET_OPTIONS = "showWidgetOptions";
    private static final String KEY_UI_SCALE = "uiScale";
    private static final String KEY_UI_SCALING_ENABLED = "uiScalingEnabled";
    private static final String KEY_LAYOUT_DENSITY = "layoutDensity";
    private static final String KEY_LOW_POWER_MODE = "lowPowerMode";
    // Header quotes settings
    private static final String KEY_HEADER_QUOTES = "header.quotes"; // multi-line string
    private static final String KEY_HEADER_QUOTE_ROTATE_SEC = "header.quote.rotate.sec"; // integer seconds
    // Visual accents
    private static final String KEY_MAINMENU_ACCENT_RGB = "mainMenuAccentRGB";
    private static final String KEY_WIDGET_ACCENT_RGB = "widgetAccentRGB";
    // Clock and Calendar styles
    private static final String KEY_CLOCK_STYLE = "clockStyle";
    private static final String KEY_CALENDAR_STYLE = "calendarStyle";
    // Widgets
    private static final String KEY_WIDGET_PANEL_VISIBLE = "widgetPanel.visible";
    private static final String KEY_WIDGET_ENABLED_PREFIX = "widget.enabled.";
    private static final String KEY_STICKIES_PINNED = "stickies.pinned";
    // General extensions
    private static final String KEY_DATE_FORMAT = "dateFormat";
    private static final String KEY_OPEN_LAST = "openLastOnStartup";
    private static final String KEY_LAST_OPENED_FILE = "lastOpenedFile";
    private static final String KEY_SPELLCHECK = "spellCheckEnabled";
    private static final String KEY_AUTOCORRECT_JOURNAL = "autocorrect.journal";
    private static final String KEY_AUTOCORRECT_POETRY = "autocorrect.poetry";
    private static final String KEY_AUTOSAVE_ON_BLUR = "autosaveOnFocusLoss";
    public static final String KEY_BACKUP_FREQ = "backup.frequency";
    public static final String KEY_BACKUP_KEEP = "backup.keep";
    private static final String KEY_LAST_BACKUP_EPOCH = "backup.last.epoch";
    private static final String KEY_BACKUP_DEST = "backup.dest";
    private static final String KEY_BACKUP_ON_EXIT_ALWAYS = "backup.onExitAlways";
    private static final String KEY_BACKUP_INCLUDE_WALLPAPERS = "backup.include.wallpapers";
    private static final String KEY_BACKUP_INCLUDE_MOOD = "backup.include.mood";
    private static final String KEY_BACKUP_INCLUDE_SETTINGS = "backup.include.settings";
    private static final String KEY_BACKUP_VERIFY = "backup.verify";
    private static final String KEY_BACKUP_PRUNE_DAYS = "backup.prune.days";
    // Security / Lock settings
    private static final String KEY_LOCK_ENABLED = "lock.enabled";
    private static final String KEY_LOCK_TIMEOUT_SEC = "lock.timeoutSec";
    private static final String KEY_LOCK_REQUIRE_ON_START = "lock.requireOnStart";
    private static final String KEY_LOCK_PW_HASH = "lock.passwordHash";
    private static final String KEY_LOCK_PW_SALT = "lock.passwordSalt";
    private static final String KEY_LOCK_NB_PREFIX = "lock.nb.";
    private static final String KEY_LOCK_ENTRY_PREFIX = "lock.entry.";
    // Encryption settings
    private static final String KEY_ENCRYPT_ENABLED = "encrypt.enabled";
    private static final String KEY_ENCRYPT_PW_HASH = "encrypt.passwordHash";
    private static final String KEY_ENCRYPT_PW_SALT = "encrypt.passwordSalt";
    // Default values
    private static final float DEF_ENTRY_BG_OPACITY = 0.7f;
    private static final float DEF_POEM_BG_OPACITY = 0.3f; // Lighter default for poems
    private static final float DEF_EDITOR_GLASS_OPACITY = 0.9f; // Glass panel opacity (0=transparent, 1=opaque)
    private static final boolean DEF_EDITOR_PAPER_FEEL = true;
    private static final boolean DEF_EDITOR_TYPO_POLISH = true;
    private static final boolean DEF_EDITOR_HEADER_STAMP = false;

    // ====================
    // DEFAULT VALUES
    // ====================
    
    // Font defaults
    private static final int    DEF_JOURNAL_FONT = 14;
    private static final int    DEF_POEM_FONT    = 16;
    
    // Theme defaults
    private static final String DEF_ANIMATION    = "Snow";
    private static final String DEF_THEME        = "Aero";
    private static final boolean DEF_GLOW        = false;
    
    // Background defaults
    private static final String  DEF_BG_IMAGE    = "";
    private static final float   DEF_BG_OPACITY  = 0.5f; // Default to 50% opacity
    private static final int     DEF_BRUSH_SIZE  = 5;
    private static final boolean DEF_SMOOTHING   = true;
    private static final boolean DEF_THUMBNAILS  = true;
    private static final int     DEF_AUTOSAVE    = 0;          // legacy minutes (0 = off)
    private static final int     DEF_AUTOSAVE_DELAY_MS = 1500; // default debounce delay
    private static final boolean DEF_TUTORIAL_SEEN = false;
    private static final boolean DEF_DISABLE_ANIMATIONS = false;
    private static final boolean DEF_DISABLE_MAIN_MENU_ANIMATIONS = false;
    private static final boolean DEF_BREATHING_OVERLAY = true;
    private static final boolean DEF_SHOW_WIDGET_OPTIONS = true;
    private static final float DEF_UI_SCALE = 1.0f;
    private static final String DEF_LAYOUT_DENSITY = "Balanced";
    private static final boolean DEF_UI_SCALING_ENABLED = true;
    private static final boolean DEF_LOW_POWER_MODE = false;
    private static final boolean DEF_WIDGET_PANEL_VISIBLE = true;
    private static final String  DEF_DATE_FORMAT = "yyyy-MM-dd";
    private static final boolean DEF_OPEN_LAST = false;
    private static final boolean DEF_SPELLCHECK = false;
    private static final boolean DEF_AUTOCORRECT_JOURNAL = true;
    private static final boolean DEF_AUTOCORRECT_POETRY = false;
    private static final boolean DEF_AUTOSAVE_ON_BLUR = false;
    public static final String DEF_BACKUP_FREQ = "Off";
    public static final int DEF_BACKUP_KEEP = 7;
    public static final long DEF_LAST_BACKUP_EPOCH = 0L;
    private static final boolean DEF_BACKUP_ON_EXIT_ALWAYS = false;
    private static final boolean DEF_BACKUP_INCLUDE_WALLPAPERS = false;
    private static final boolean DEF_BACKUP_INCLUDE_MOOD = true;
    private static final boolean DEF_BACKUP_INCLUDE_SETTINGS = true;
    private static final boolean DEF_BACKUP_VERIFY = false;
    private static final int DEF_BACKUP_PRUNE_DAYS = 0;
    // Security defaults
    private static final boolean DEF_LOCK_ENABLED = false;
    private static final int DEF_LOCK_TIMEOUT_SEC = 0;
    private static final boolean DEF_LOCK_REQUIRE_ON_START = false;

    // ====================
    // SINGLETON MANAGEMENT
    // ====================
    
    /**
     * The singleton instance of SettingsStore.
     */
    private static SettingsStore instance;
    
    /**
     * Flag indicating if this instance operates in memory-only mode.
     * When true, settings are not persisted to disk.
     */
    private final boolean memoryOnly;
    
    /**
     * Gets the singleton instance of SettingsStore.
     * 
     * <p>If the application directory structure is not yet ready, returns a memory-only
     * instance. Once the directory structure is available, the instance will be recreated
     * with persistent storage enabled.</p>
     * 
     * @return the singleton SettingsStore instance
     */
    public static synchronized SettingsStore get(){
        boolean rootReady = false;
        try { AppDirectories.getRoot(); rootReady = true; } catch (Throwable ignored) {}
        if (instance == null || (rootReady && instance.memoryOnly)) {
            instance = new SettingsStore(!rootReady);
        }
        return instance;
    }

    /**
     * Private constructor for the SettingsStore singleton.
     * 
     * @param memoryOnly if true, settings are stored only in memory and not persisted
     */
    private SettingsStore(boolean memoryOnly){
        this.memoryOnly = memoryOnly;
        if (memoryOnly) {
            storeFile = null;
            return; // in-memory only until root is set
        }
        storeFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), FILE_NAME);
        load();
    }

    /**
     * Loads settings from the properties file.
     * If the file doesn't exist or can't be read, uses default values.
     */
    private void load(){
        if(storeFile != null && storeFile.exists()){
            try(FileInputStream in = new FileInputStream(storeFile)){
                props.load(in);
                migrateLegacyPrefs();
            }catch(IOException ignored){}
        }
    }

    /**
     * Clean up legacy/invalid settings values when loading from disk.
     */
    private void migrateLegacyPrefs() {
        // Theme: map legacy Dark/Plain to Light; unknown -> Aero
        try {
            String t = props.getProperty(KEY_THEME);
            if (t != null) {
                String v = t.trim();
                if (v.equalsIgnoreCase("Dark") || v.equalsIgnoreCase("Plain") || v.equalsIgnoreCase("Plain White") || v.equalsIgnoreCase("White")) {
                    props.setProperty(KEY_THEME, "Light");
                } else if (!(v.equalsIgnoreCase("Aero") || v.equalsIgnoreCase("Light") || v.equalsIgnoreCase("Sepia"))) {
                    props.setProperty(KEY_THEME, "Aero");
                }
            }
        } catch (Throwable ignored) {}

        // Remove deprecated UI font scale key if present
        props.remove("uiFontScale");

        // Layout density: enforce known values
        try {
            String d = props.getProperty(KEY_LAYOUT_DENSITY);
            if (d != null) {
                String dv = d.trim().toLowerCase();
                if (!(dv.startsWith("minimal") || dv.startsWith("dense") || dv.startsWith("information") || dv.startsWith("balanced"))) {
                    props.setProperty(KEY_LAYOUT_DENSITY, DEF_LAYOUT_DENSITY);
                }
            }
        } catch (Throwable ignored) {}

        // Accent values: drop invalid
        sanitizeIntProperty(KEY_MAINMENU_ACCENT_RGB);
        sanitizeIntProperty(KEY_WIDGET_ACCENT_RGB);

        // Normalize stored paths for cross-device sync
        normalizePathProperty(KEY_BG_IMAGE, true);
        normalizePathProperty(KEY_ENTRY_BG_IMAGE, true);
        normalizePathProperty(KEY_POEM_BG_IMAGE, true);
        normalizePathProperty(KEY_LAST_OPENED_FILE, true);
    }

    private void sanitizeIntProperty(String key) {
        try {
            String v = props.getProperty(key);
            if (v == null || v.isBlank()) {
                props.remove(key);
                return;
            }
            Integer.parseInt(v.trim()); // validate
        } catch (NumberFormatException e) {
            props.remove(key);
        }
    }

    private void normalizePathProperty(String key, boolean allowLegacyRebase) {
        try {
            String current = props.getProperty(key);
            if (current == null || current.isBlank()) return;
            String normalized = normalizePathForStorage(current, allowLegacyRebase);
            if (normalized == null || normalized.isBlank()) {
                props.remove(key);
                return;
            }
            if (!current.equals(normalized)) {
                props.setProperty(key, normalized);
            }
        } catch (Throwable ignored) {}
    }

    private static String resolveStoredPath(String stored) {
        if (stored == null || stored.isBlank()) return "";
        String trimmed = stored.trim();
        if (isSpecialPath(trimmed)) return trimmed;
        File root = safeRoot();
        if (root == null) return trimmed;
        File f = new File(trimmed);
        if (!f.isAbsolute()) {
            return new File(root, trimmed).getAbsolutePath();
        }
        if (isUnderRoot(f, root)) {
            return f.getAbsolutePath();
        }
        String rel = extractRelativeFromSimjotPath(trimmed);
        if (rel != null && !rel.isBlank()) {
            return new File(root, rel).getAbsolutePath();
        }
        return trimmed;
    }

    private static String normalizePathForStorage(String path, boolean allowLegacyRebase) {
        if (path == null || path.isBlank()) return "";
        String trimmed = path.trim();
        if (isSpecialPath(trimmed)) return trimmed;
        File root = safeRoot();
        if (root == null) return trimmed;
        File f = new File(trimmed);
        if (!f.isAbsolute()) return trimmed;
        if (isUnderRoot(f, root)) {
            String rel = relativizeToRoot(root, f);
            if (rel != null && !rel.isBlank()) return rel;
            return trimmed;
        }
        if (allowLegacyRebase) {
            String rel = extractRelativeFromSimjotPath(trimmed);
            if (rel != null && !rel.isBlank()) return rel;
        }
        return trimmed;
    }

    private static boolean isSpecialPath(String path) {
        if (path == null) return false;
        String p = path.trim().toLowerCase();
        return p.startsWith("res:") || p.startsWith("gen:");
    }

    private static File safeRoot() {
        try { return AppDirectories.getRoot(); } catch (Throwable ignored) { return null; }
    }

    private static boolean isUnderRoot(File folder, File root) {
        if (folder == null || root == null) return false;
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path folderPath = folder.toPath().toAbsolutePath().normalize();
            return folderPath.startsWith(rootPath);
        } catch (Throwable ignored) {
            String rootPath = root.getAbsolutePath();
            String folderPath = folder.getAbsolutePath();
            if (rootPath == null || folderPath == null) return false;
            String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
            return folderPath.startsWith(prefix);
        }
    }

    private static String relativizeToRoot(File root, File target) {
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path targetPath = target.toPath().toAbsolutePath().normalize();
            if (!targetPath.startsWith(rootPath)) return null;
            String rel = rootPath.relativize(targetPath).toString();
            return rel.isEmpty() ? null : rel;
        } catch (Throwable ignored) {
            String rootPath = root.getAbsolutePath();
            String targetPath = target.getAbsolutePath();
            if (rootPath == null || targetPath == null) return null;
            String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
            if (!targetPath.startsWith(prefix)) return null;
            return targetPath.substring(prefix.length());
        }
    }

    private static String extractRelativeFromSimjotPath(String path) {
        if (path == null) return null;
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase();
        String token = "/simjot/";
        int idx = lower.lastIndexOf(token);
        if (idx < 0) return null;
        String rel = normalized.substring(idx + token.length());
        if (rel.isEmpty()) return null;
        if (File.separatorChar != '/') {
            rel = rel.replace('/', File.separatorChar);
        }
        return rel;
    }

    /**
     * Saves the current settings to the properties file.
     * 
     * <p>This method is thread-safe and uses atomic file writing to prevent corruption.
     * If running in memory-only mode, this method does nothing.</p>
     */
    public synchronized void save(){
        if (memoryOnly || storeFile == null) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            synchronized (props) {
                props.store(baos, "Simjot preferences");
            }
            FileIO.ensureSpace(storeFile.toPath(), baos.size() + 4096L, "settings save");
            FileIO.atomicWrite(storeFile.toPath(), baos.toByteArray(), true, true);
        } catch(IOException ex){ ex.printStackTrace(); }
    }

    // ====================
    // FONT SETTINGS
    // ====================
    
    /**
     * Gets the journal font size.
     * @return the font size, or default if not set
     */
    public int getJournalFontSize(){ return safeInt(KEY_JOURNAL_FONT, DEF_JOURNAL_FONT); }
    
    /**
     * Sets the journal font size.
     * @param v the new font size
     */
    public void setJournalFontSize(int v){ props.setProperty(KEY_JOURNAL_FONT, String.valueOf(v)); }
    
    /**
     * Gets the poem font size.
     * @return the font size, or default if not set
     */
    public int getPoemFontSize(){ return safeInt(KEY_POEM_FONT, DEF_POEM_FONT); }
    
    /**
     * Sets the poem font size.
     * @param v the new font size
     */
    public void setPoemFontSize(int v){ props.setProperty(KEY_POEM_FONT, String.valueOf(v)); }

    public String getEditorFontFamily(){ return props.getProperty(KEY_EDITOR_FONT_FAMILY, "Serif"); }
    public void setEditorFontFamily(String f){ props.setProperty(KEY_EDITOR_FONT_FAMILY, f); }

    public String getEditorLineSpacing(){ return props.getProperty(KEY_EDITOR_LINE_SPACING, "1.0"); }
    public void setEditorLineSpacing(String s){ props.setProperty(KEY_EDITOR_LINE_SPACING, s); }

    public String getAnimation(){ return props.getProperty(KEY_ANIMATION, DEF_ANIMATION); }
    public void setAnimation(String a){ props.setProperty(KEY_ANIMATION, a); }

    public String getTheme(){ return props.getProperty(KEY_THEME, DEF_THEME); }
    public void setTheme(String t){ props.setProperty(KEY_THEME, t); }

    public boolean isGlowEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_GLOW, String.valueOf(DEF_GLOW))); }
    public void setGlowEnabled(boolean b){ props.setProperty(KEY_GLOW, String.valueOf(b)); }

    public String getBackgroundImage(){ return resolveStoredPath(props.getProperty(KEY_BG_IMAGE, DEF_BG_IMAGE)); }
    
    public float getBackgroundOpacity() {
        try {
            return Float.parseFloat(props.getProperty(KEY_BG_OPACITY, String.valueOf(DEF_BG_OPACITY)));
        } catch (NumberFormatException e) {
            return DEF_BG_OPACITY;
        }
    }
    
    public void setBackgroundOpacity(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        props.setProperty(KEY_BG_OPACITY, String.valueOf(clamped));
    }
    
    public String getEntryBackgroundImage() {
        return resolveStoredPath(props.getProperty(KEY_ENTRY_BG_IMAGE, ""));
    }
    
    public void setEntryBackgroundImage(String path) {
        String normalized = normalizePathForStorage(path, false);
        if (normalized == null || normalized.trim().isEmpty()) {
            props.remove(KEY_ENTRY_BG_IMAGE);
        } else {
            props.setProperty(KEY_ENTRY_BG_IMAGE, normalized);
        }
    }
    
    public float getEntryBackgroundOpacity() {
        try {
            return Float.parseFloat(props.getProperty(KEY_ENTRY_BG_OPACITY, String.valueOf(DEF_ENTRY_BG_OPACITY)));
        } catch (NumberFormatException e) {
            return DEF_ENTRY_BG_OPACITY;
        }
    }
    
    public void setEntryBackgroundOpacity(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        props.setProperty(KEY_ENTRY_BG_OPACITY, String.valueOf(clamped));
    }
    
    public String getPoemBackgroundImage() {
        return resolveStoredPath(props.getProperty(KEY_POEM_BG_IMAGE, ""));
    }
    
    public void setPoemBackgroundImage(String path) {
        String normalized = normalizePathForStorage(path, false);
        if (normalized == null || normalized.trim().isEmpty()) {
            props.remove(KEY_POEM_BG_IMAGE);
        } else {
            props.setProperty(KEY_POEM_BG_IMAGE, normalized);
        }
    }
    
    public float getPoemBackgroundOpacity() {
        try {
            return Float.parseFloat(props.getProperty(KEY_POEM_BG_OPACITY, String.valueOf(DEF_POEM_BG_OPACITY)));
        } catch (NumberFormatException e) {
            return DEF_POEM_BG_OPACITY;
        }
    }
    
    public void setPoemBackgroundOpacity(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        props.setProperty(KEY_POEM_BG_OPACITY, String.valueOf(clamped));
    }
    
    public float getEditorGlassOpacity() {
        try {
            return Float.parseFloat(props.getProperty(KEY_EDITOR_GLASS_OPACITY, String.valueOf(DEF_EDITOR_GLASS_OPACITY)));
        } catch (NumberFormatException e) {
            return DEF_EDITOR_GLASS_OPACITY;
        }
    }
    
    public void setEditorGlassOpacity(float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        props.setProperty(KEY_EDITOR_GLASS_OPACITY, String.valueOf(clamped));
    }

    public boolean isEditorPaperFeelEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_EDITOR_PAPER_FEEL, String.valueOf(DEF_EDITOR_PAPER_FEEL)));
    }

    public void setEditorPaperFeelEnabled(boolean enabled) {
        props.setProperty(KEY_EDITOR_PAPER_FEEL, String.valueOf(enabled));
    }

    public boolean isEditorTypographyPolishEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_EDITOR_TYPO_POLISH, String.valueOf(DEF_EDITOR_TYPO_POLISH)));
    }

    public void setEditorTypographyPolishEnabled(boolean enabled) {
        props.setProperty(KEY_EDITOR_TYPO_POLISH, String.valueOf(enabled));
    }

    public boolean isEditorHeaderStampEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_EDITOR_HEADER_STAMP, String.valueOf(DEF_EDITOR_HEADER_STAMP)));
    }

    public void setEditorHeaderStampEnabled(boolean enabled) {
        props.setProperty(KEY_EDITOR_HEADER_STAMP, String.valueOf(enabled));
    }

    public String getEditorHeaderStampLocation() {
        return props.getProperty(KEY_EDITOR_HEADER_LOCATION, "");
    }

    public void setEditorHeaderStampLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            props.remove(KEY_EDITOR_HEADER_LOCATION);
        } else {
            props.setProperty(KEY_EDITOR_HEADER_LOCATION, location.trim());
        }
    }
    
    public void setBackgroundImage(String path){
        String normalized = normalizePathForStorage(path, false);
        props.setProperty(KEY_BG_IMAGE, normalized == null ? "" : normalized);
        props.remove(KEY_MAINMENU_ACCENT_RGB);
    }

    public int getDefaultBrushSize(){ return safeInt(KEY_BRUSH_SIZE, DEF_BRUSH_SIZE); }
    public void setDefaultBrushSize(int v){ props.setProperty(KEY_BRUSH_SIZE, String.valueOf(v)); }

    public boolean isSmoothingEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_SMOOTHING, String.valueOf(DEF_SMOOTHING))); }
    public void setSmoothingEnabled(boolean b){ props.setProperty(KEY_SMOOTHING, String.valueOf(b)); }

    public boolean isThumbnailGeneration(){ return Boolean.parseBoolean(props.getProperty(KEY_THUMBNAILS, String.valueOf(DEF_THUMBNAILS))); }
    public void setThumbnailGeneration(boolean b){ props.setProperty(KEY_THUMBNAILS, String.valueOf(b)); }

    public String getLayoutDensity(){
        String d = props.getProperty(KEY_LAYOUT_DENSITY, DEF_LAYOUT_DENSITY);
        if (d == null) return DEF_LAYOUT_DENSITY;
        d = d.trim();
        if (d.equalsIgnoreCase("Minimal") || d.equalsIgnoreCase("Balanced") ||
            d.equalsIgnoreCase("Dense") || d.equalsIgnoreCase("Information-dense") ||
            d.equalsIgnoreCase("Information Dense")) {
            return d;
        }
        return DEF_LAYOUT_DENSITY;
    }
    public void setLayoutDensity(String density){
        if (density == null || density.trim().isEmpty()) {
            props.setProperty(KEY_LAYOUT_DENSITY, DEF_LAYOUT_DENSITY);
        } else {
            props.setProperty(KEY_LAYOUT_DENSITY, density.trim());
        }
    }

    public int getAutosaveMinutes(){ return safeInt(KEY_AUTOSAVE, DEF_AUTOSAVE); }
    public void setAutosaveMinutes(int min){ props.setProperty(KEY_AUTOSAVE, String.valueOf(min)); }

    // New debounce-based autosave delay in milliseconds. If not set, migrate from legacy minutes value.
    public int getAutosaveDelayMs(){
        try {
            String v = props.getProperty(KEY_AUTOSAVE_DELAY_MS, null);
            if (v != null) return Math.max(0, Integer.parseInt(v));
        } catch (NumberFormatException ignored) {}
        // Fallback: derive from legacy minutes if present and > 0; else use default
        int legacyMin = getAutosaveMinutes();
        if (legacyMin > 0) return legacyMin * 60 * 1000;
        return DEF_AUTOSAVE_DELAY_MS;
    }
    public void setAutosaveDelayMs(int ms){ props.setProperty(KEY_AUTOSAVE_DELAY_MS, String.valueOf(Math.max(0, ms))); }

    // Tutorial seen flag
    public boolean isTutorialSeen(){ return Boolean.parseBoolean(props.getProperty(KEY_TUTORIAL_SEEN, String.valueOf(DEF_TUTORIAL_SEEN))); }
    public void setTutorialSeen(boolean seen){ props.setProperty(KEY_TUTORIAL_SEEN, String.valueOf(seen)); }

    public boolean isAnimationsDisabled(){ return Boolean.parseBoolean(props.getProperty(KEY_DISABLE_ANIMATIONS, String.valueOf(DEF_DISABLE_ANIMATIONS))); }
    public void setAnimationsDisabled(boolean b){ props.setProperty(KEY_DISABLE_ANIMATIONS, String.valueOf(b)); }

    public boolean isMainMenuAnimationsDisabled(){ return Boolean.parseBoolean(props.getProperty(KEY_DISABLE_MAIN_MENU_ANIMATIONS, String.valueOf(DEF_DISABLE_MAIN_MENU_ANIMATIONS))); }
    public void setMainMenuAnimationsDisabled(boolean b){ props.setProperty(KEY_DISABLE_MAIN_MENU_ANIMATIONS, String.valueOf(b)); }

    public boolean isBreathingOverlayEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_BREATHING_OVERLAY, String.valueOf(DEF_BREATHING_OVERLAY))); }
    public void setBreathingOverlayEnabled(boolean b){ props.setProperty(KEY_BREATHING_OVERLAY, String.valueOf(b)); }

    public boolean isShowWidgetOptions(){ return Boolean.parseBoolean(props.getProperty(KEY_SHOW_WIDGET_OPTIONS, String.valueOf(DEF_SHOW_WIDGET_OPTIONS))); }
    public void setShowWidgetOptions(boolean b){ props.setProperty(KEY_SHOW_WIDGET_OPTIONS, String.valueOf(b)); }

    // Low Power Mode
    public boolean isLowPowerMode(){
        return Boolean.parseBoolean(props.getProperty(KEY_LOW_POWER_MODE, String.valueOf(DEF_LOW_POWER_MODE)));
    }
    public void setLowPowerMode(boolean b){ props.setProperty(KEY_LOW_POWER_MODE, String.valueOf(b)); }

    // Clock and Calendar styles
    public String getClockStyle(){ return props.getProperty(KEY_CLOCK_STYLE, "Classic"); }
    public void setClockStyle(String style){ if(style!=null && !style.isBlank()) props.setProperty(KEY_CLOCK_STYLE, style); }

    public String getCalendarStyle(){ return props.getProperty(KEY_CALENDAR_STYLE, "Classic"); }
    public void setCalendarStyle(String style){ if(style!=null && !style.isBlank()) props.setProperty(KEY_CALENDAR_STYLE, style); }

    public float getUIScale() {
        try {
            return Float.parseFloat(props.getProperty(KEY_UI_SCALE, String.valueOf(DEF_UI_SCALE)));
        } catch (NumberFormatException e) {
            return DEF_UI_SCALE;
        }
    }
    
    public void setUIScale(float scale) {
        float clamped = Math.max(0.5f, Math.min(3.0f, scale));
        props.setProperty(KEY_UI_SCALE, String.valueOf(clamped));
    }

    public boolean isUIScalingEnabled(){
        return Boolean.parseBoolean(props.getProperty(KEY_UI_SCALING_ENABLED, String.valueOf(DEF_UI_SCALING_ENABLED)));
    }
    public void setUIScalingEnabled(boolean enabled){
        props.setProperty(KEY_UI_SCALING_ENABLED, String.valueOf(enabled));
    }

    // --- New General settings ---
    public String getDateFormat(){ return props.getProperty(KEY_DATE_FORMAT, DEF_DATE_FORMAT); }
    public void setDateFormat(String fmt){ if(fmt!=null && !fmt.trim().isEmpty()) props.setProperty(KEY_DATE_FORMAT, fmt); }

    public boolean isOpenLastOnStartup(){ return Boolean.parseBoolean(props.getProperty(KEY_OPEN_LAST, String.valueOf(DEF_OPEN_LAST))); }
    public void setOpenLastOnStartup(boolean b){ props.setProperty(KEY_OPEN_LAST, String.valueOf(b)); }

    // Last opened file path (absolute). Empty if none.
    public String getLastOpenedFilePath(){ return resolveStoredPath(props.getProperty(KEY_LAST_OPENED_FILE, "")); }
    public void setLastOpenedFilePath(String path){
        String normalized = normalizePathForStorage(path, false);
        if (normalized == null || normalized.isBlank()) {
            props.remove(KEY_LAST_OPENED_FILE);
        } else {
            props.setProperty(KEY_LAST_OPENED_FILE, normalized);
        }
    }

    public boolean isSpellCheckEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_SPELLCHECK, String.valueOf(DEF_SPELLCHECK))); }
    public void setSpellCheckEnabled(boolean b){ props.setProperty(KEY_SPELLCHECK, String.valueOf(b)); }

    public boolean isJournalAutocorrectEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_AUTOCORRECT_JOURNAL, String.valueOf(DEF_AUTOCORRECT_JOURNAL))); }
    public void setJournalAutocorrectEnabled(boolean b){ props.setProperty(KEY_AUTOCORRECT_JOURNAL, String.valueOf(b)); }

    public boolean isPoetryAutocorrectEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_AUTOCORRECT_POETRY, String.valueOf(DEF_AUTOCORRECT_POETRY))); }
    public void setPoetryAutocorrectEnabled(boolean b){ props.setProperty(KEY_AUTOCORRECT_POETRY, String.valueOf(b)); }

    public boolean isAutosaveOnFocusLoss(){ return Boolean.parseBoolean(props.getProperty(KEY_AUTOSAVE_ON_BLUR, String.valueOf(DEF_AUTOSAVE_ON_BLUR))); }
    public void setAutosaveOnFocusLoss(boolean b){ props.setProperty(KEY_AUTOSAVE_ON_BLUR, String.valueOf(b)); }

    public String getBackupFrequency(){ return props.getProperty(KEY_BACKUP_FREQ, DEF_BACKUP_FREQ); }
    public void setBackupFrequency(String v){ if(v!=null) props.setProperty(KEY_BACKUP_FREQ, v); }

    public int getBackupKeepCount(){ return safeInt(KEY_BACKUP_KEEP, DEF_BACKUP_KEEP); }
    public void setBackupKeepCount(int n){ props.setProperty(KEY_BACKUP_KEEP, String.valueOf(Math.max(1, n))); }

    // Backup bookkeeping
    public long getLastBackupEpochMillis() {
        try {
            return Long.parseLong(props.getProperty(KEY_LAST_BACKUP_EPOCH, String.valueOf(DEF_LAST_BACKUP_EPOCH)));
        } catch (NumberFormatException e) {
            return DEF_LAST_BACKUP_EPOCH;
        }
    }
    public void setLastBackupEpochMillis(long epoch) {
        props.setProperty(KEY_LAST_BACKUP_EPOCH, String.valueOf(Math.max(0L, epoch)));
    }

    public String getBackupDestinationPath(){ return props.getProperty(KEY_BACKUP_DEST, ""); }
    public void setBackupDestinationPath(String p){ if(p==null || p.isBlank()) props.remove(KEY_BACKUP_DEST); else props.setProperty(KEY_BACKUP_DEST, p); }

    public boolean isBackupOnExitAlways(){ return Boolean.parseBoolean(props.getProperty(KEY_BACKUP_ON_EXIT_ALWAYS, String.valueOf(DEF_BACKUP_ON_EXIT_ALWAYS))); }
    public void setBackupOnExitAlways(boolean b){ props.setProperty(KEY_BACKUP_ON_EXIT_ALWAYS, String.valueOf(b)); }

    public boolean isBackupIncludeWallpapers(){ return Boolean.parseBoolean(props.getProperty(KEY_BACKUP_INCLUDE_WALLPAPERS, String.valueOf(DEF_BACKUP_INCLUDE_WALLPAPERS))); }
    public void setBackupIncludeWallpapers(boolean b){ props.setProperty(KEY_BACKUP_INCLUDE_WALLPAPERS, String.valueOf(b)); }

    public boolean isBackupIncludeMood(){ return Boolean.parseBoolean(props.getProperty(KEY_BACKUP_INCLUDE_MOOD, String.valueOf(DEF_BACKUP_INCLUDE_MOOD))); }
    public void setBackupIncludeMood(boolean b){ props.setProperty(KEY_BACKUP_INCLUDE_MOOD, String.valueOf(b)); }

    public boolean isBackupIncludeSettings(){ return Boolean.parseBoolean(props.getProperty(KEY_BACKUP_INCLUDE_SETTINGS, String.valueOf(DEF_BACKUP_INCLUDE_SETTINGS))); }
    public void setBackupIncludeSettings(boolean b){ props.setProperty(KEY_BACKUP_INCLUDE_SETTINGS, String.valueOf(b)); }

    public boolean isBackupVerify(){ return Boolean.parseBoolean(props.getProperty(KEY_BACKUP_VERIFY, String.valueOf(DEF_BACKUP_VERIFY))); }
    public void setBackupVerify(boolean b){ props.setProperty(KEY_BACKUP_VERIFY, String.valueOf(b)); }

    public int getBackupPruneDays(){ return safeInt(KEY_BACKUP_PRUNE_DAYS, DEF_BACKUP_PRUNE_DAYS); }
    public void setBackupPruneDays(int days){ props.setProperty(KEY_BACKUP_PRUNE_DAYS, String.valueOf(Math.max(0, days))); }

    // --- Accent color persistence ---
    public int getMainMenuAccentRGB(){
        try {
            String v = props.getProperty(KEY_MAINMENU_ACCENT_RGB, null);
            if (v == null || v.isEmpty()) return Integer.MIN_VALUE;
            return Integer.parseInt(v);
        } catch (NumberFormatException e){
            return Integer.MIN_VALUE;
        }
    }
    public void setMainMenuAccentRGB(int rgb){
        props.setProperty(KEY_MAINMENU_ACCENT_RGB, String.valueOf(rgb));
    }
    public void clearMainMenuAccent(){
        props.remove(KEY_MAINMENU_ACCENT_RGB);
    }

    public int getWidgetAccentRGB(){
        try {
            String v = props.getProperty(KEY_WIDGET_ACCENT_RGB, null);
            if (v == null || v.isEmpty()) return Integer.MIN_VALUE;
            return Integer.parseInt(v);
        } catch (NumberFormatException e){
            return Integer.MIN_VALUE;
        }
    }
    public void setWidgetAccentRGB(int rgb){
        props.setProperty(KEY_WIDGET_ACCENT_RGB, String.valueOf(rgb));
    }
    public void clearWidgetAccent(){
        props.remove(KEY_WIDGET_ACCENT_RGB);
    }

    // --- Widgets persistence ---
    public boolean isWidgetPanelVisible(){
        return Boolean.parseBoolean(props.getProperty(KEY_WIDGET_PANEL_VISIBLE, String.valueOf(DEF_WIDGET_PANEL_VISIBLE)));
    }
    public void setWidgetPanelVisible(boolean visible){
        props.setProperty(KEY_WIDGET_PANEL_VISIBLE, String.valueOf(visible));
    }

    public boolean isWidgetEnabled(String name){
        if (name == null) return false;
        return Boolean.parseBoolean(props.getProperty(KEY_WIDGET_ENABLED_PREFIX + name, String.valueOf(false)));
    }
    public void setWidgetEnabled(String name, boolean enabled){
        if (name == null) return;
        props.setProperty(KEY_WIDGET_ENABLED_PREFIX + name, String.valueOf(enabled));
    }

    // --- Sticky notes pinning ---
    public java.util.Set<String> getPinnedStickyIds(){
        String csv = props.getProperty(KEY_STICKIES_PINNED, "");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")){
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    public void setPinnedStickyIds(java.util.Set<String> ids){
        if (ids == null || ids.isEmpty()) { props.remove(KEY_STICKIES_PINNED); return; }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String id : ids){
            if (id == null || id.isBlank()) continue;
            if (!first) sb.append(',');
            sb.append(id.trim());
            first = false;
        }
        props.setProperty(KEY_STICKIES_PINNED, sb.toString());
    }
    public boolean isStickyPinned(String id){
        if (id == null || id.isBlank()) return false;
        return getPinnedStickyIds().contains(id);
    }
    public void pinSticky(String id, boolean pinned){
        if (id == null || id.isBlank()) return;
        java.util.Set<String> s = getPinnedStickyIds();
        if (pinned) s.add(id); else s.remove(id);
        setPinnedStickyIds(s);
    }

    // -------- Generic accessors (for feature modules like Sim) -------- //
    public boolean getFlag(String key, boolean def) {
        if (key == null) return def;
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)));
    }

    public void setFlag(String key, boolean value) {
        if (key == null) return;
        props.setProperty(key, String.valueOf(value));
    }

    public String getValue(String key, String def) {
        if (key == null) return def;
        return props.getProperty(key, def);
    }

    public void setValue(String key, String value) {
        if (key == null) return;
        props.setProperty(key, value == null ? "" : value);
    }

    // -------------- Security / Lock -------------- //
    public boolean isLockEnabled(){
        return Boolean.parseBoolean(props.getProperty(KEY_LOCK_ENABLED, String.valueOf(DEF_LOCK_ENABLED)));
    }
    public void setLockEnabled(boolean enabled){ props.setProperty(KEY_LOCK_ENABLED, String.valueOf(enabled)); }

    public int getLockTimeoutSec(){ return safeInt(KEY_LOCK_TIMEOUT_SEC, DEF_LOCK_TIMEOUT_SEC); }
    public void setLockTimeoutSec(int seconds){ props.setProperty(KEY_LOCK_TIMEOUT_SEC, String.valueOf(Math.max(0, seconds))); }

    public boolean isLockRequireOnStart(){
        return Boolean.parseBoolean(props.getProperty(KEY_LOCK_REQUIRE_ON_START, String.valueOf(DEF_LOCK_REQUIRE_ON_START)));
    }
    public void setLockRequireOnStart(boolean b){ props.setProperty(KEY_LOCK_REQUIRE_ON_START, String.valueOf(b)); }

    public String getLockPasswordHash(){ return props.getProperty(KEY_LOCK_PW_HASH, ""); }
    public void setLockPasswordHash(String h){ if (h==null||h.isBlank()) props.remove(KEY_LOCK_PW_HASH); else props.setProperty(KEY_LOCK_PW_HASH, h); }
    public String getLockPasswordSalt(){ return props.getProperty(KEY_LOCK_PW_SALT, ""); }
    public void setLockPasswordSalt(String s){ if (s==null||s.isBlank()) props.remove(KEY_LOCK_PW_SALT); else props.setProperty(KEY_LOCK_PW_SALT, s); }

    public boolean isNotebookLocked(String name){
        if (name == null) return false;
        return Boolean.parseBoolean(props.getProperty(KEY_LOCK_NB_PREFIX + name, String.valueOf(false)));
    }
    public void setNotebookLocked(String name, boolean locked){
        if (name == null) return;
        props.setProperty(KEY_LOCK_NB_PREFIX + name, String.valueOf(locked));
    }

    public boolean isEntryLocked(String path){
        if (path == null) return false;
        return Boolean.parseBoolean(props.getProperty(KEY_LOCK_ENTRY_PREFIX + path, String.valueOf(false)));
    }
    public void setEntryLocked(String path, boolean locked){
        if (path == null) return;
        props.setProperty(KEY_LOCK_ENTRY_PREFIX + path, String.valueOf(locked));
    }

    // -------------- Encryption -------------- //
    public boolean isEncryptionEnabled() {
        return Boolean.parseBoolean(props.getProperty(KEY_ENCRYPT_ENABLED, String.valueOf(false)));
    }
    public void setEncryptionEnabled(boolean enabled) {
        props.setProperty(KEY_ENCRYPT_ENABLED, String.valueOf(enabled));
    }

    public String getEncryptionPasswordHash() { return props.getProperty(KEY_ENCRYPT_PW_HASH, ""); }
    public void setEncryptionPasswordHash(String h) { if (h == null || h.isBlank()) props.remove(KEY_ENCRYPT_PW_HASH); else props.setProperty(KEY_ENCRYPT_PW_HASH, h); }
    public String getEncryptionPasswordSalt() { return props.getProperty(KEY_ENCRYPT_PW_SALT, ""); }
    public void setEncryptionPasswordSalt(String s) { if (s == null || s.isBlank()) props.remove(KEY_ENCRYPT_PW_SALT); else props.setProperty(KEY_ENCRYPT_PW_SALT, s); }

    // -------- Header quotes (customization) -------- //
    public String[] getHeaderCustomQuotes(){
        String multi = props.getProperty(KEY_HEADER_QUOTES, "").trim();
        if (multi.isEmpty()) return new String[0];
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String line : multi.split("\n")){
            String t = line.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list.toArray(new String[0]);
    }

    public void setHeaderCustomQuotes(String[] quotes){
        if (quotes == null || quotes.length == 0){
            props.remove(KEY_HEADER_QUOTES);
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String q : quotes){
            if (q == null) continue;
            String t = q.trim();
            if (t.isEmpty()) continue;
            if (!first) sb.append('\n');
            sb.append(t);
            first = false;
        }
        if (sb.length() == 0) props.remove(KEY_HEADER_QUOTES); else props.setProperty(KEY_HEADER_QUOTES, sb.toString());
    }

    public int getHeaderQuoteRotationSeconds(){
        int sec = safeInt(KEY_HEADER_QUOTE_ROTATE_SEC, 12);
        return Math.max(5, Math.min(120, sec));
    }

    public void setHeaderQuoteRotationSeconds(int seconds){
        int sec = Math.max(5, Math.min(120, seconds));
        props.setProperty(KEY_HEADER_QUOTE_ROTATE_SEC, String.valueOf(sec));
    }

    /**
     * Utility method to safely parse integer values from properties.
     * Returns the default value if parsing fails.
     * 
     * @param key the property key
     * @param def the default value to return if parsing fails
     * @return the parsed integer value or default
     */
    private int safeInt(String key, int def){
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e){ return def; }
    }

}
