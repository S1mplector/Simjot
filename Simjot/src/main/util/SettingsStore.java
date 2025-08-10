package main.util;

import java.io.*;
import java.util.Properties;

/**
 * Singleton-like helper that persists user preferences under Simjot/settings/preferences.properties.
 */
public final class SettingsStore {

    private static final String FILE_NAME = "preferences.properties";

    private final Properties props = new Properties();
    private final File storeFile;

    // Keys
    private static final String KEY_JOURNAL_FONT = "journalFontSize";
    private static final String KEY_POEM_FONT    = "poemFontSize";
    private static final String KEY_ANIMATION    = "animation";
    private static final String KEY_THEME        = "theme";
    private static final String KEY_GLOW         = "glowEnabled";
    private static final String KEY_BG_IMAGE     = "backgroundImage";
    private static final String KEY_BG_OPACITY   = "backgroundOpacity";
    private static final String KEY_ENTRY_BG_IMAGE = "entryBackgroundImage";
    private static final String KEY_ENTRY_BG_OPACITY = "entryBackgroundOpacity";
    private static final String KEY_POEM_BG_IMAGE = "poemBackgroundImage";
    private static final String KEY_POEM_BG_OPACITY = "poemBackgroundOpacity";
    private static final String KEY_BRUSH_SIZE   = "defaultBrushSize";
    private static final String KEY_SMOOTHING    = "strokeSmoothing";
    private static final String KEY_THUMBNAILS   = "generateThumbnails";
    private static final String KEY_AUTOSAVE     = "autosaveMinutes";
    private static final String KEY_TUTORIAL_SEEN = "tutorialSeen";
    private static final String KEY_DISABLE_ANIMATIONS = "disableAnimations";
    private static final String KEY_BREATHING_OVERLAY = "breathingOverlayEnabled";
    private static final String KEY_SHOW_WIDGET_OPTIONS = "showWidgetOptions";
    private static final String KEY_UI_SCALE = "uiScale";
    
    // Default values
    private static final float DEF_ENTRY_BG_OPACITY = 0.7f;
    private static final float DEF_POEM_BG_OPACITY = 0.3f; // Lighter default for poems

    // Defaults
    private static final int    DEF_JOURNAL_FONT = 14;
    private static final int    DEF_POEM_FONT    = 16;
    private static final String DEF_ANIMATION    = "Snow";
    private static final String DEF_THEME        = "Light";
    private static final boolean DEF_GLOW        = false;
    private static final String  DEF_BG_IMAGE    = "";
    private static final float   DEF_BG_OPACITY  = 0.5f; // Default to 50% opacity
    private static final int     DEF_BRUSH_SIZE  = 5;
    private static final boolean DEF_SMOOTHING   = true;
    private static final boolean DEF_THUMBNAILS  = true;
    private static final int     DEF_AUTOSAVE    = 0;
    private static final boolean DEF_TUTORIAL_SEEN = false;
    private static final boolean DEF_DISABLE_ANIMATIONS = false;
    private static final boolean DEF_BREATHING_OVERLAY = true;
    private static final boolean DEF_SHOW_WIDGET_OPTIONS = true;
    private static final float DEF_UI_SCALE = 1.0f;

    // Singleton handling
    private static SettingsStore instance;
    public static synchronized SettingsStore get(){
        if(instance==null) instance = new SettingsStore();
        return instance;
    }

    private SettingsStore(){
        storeFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), FILE_NAME);
        load();
    }

    private void load(){
        if(storeFile.exists()){
            try(FileInputStream in = new FileInputStream(storeFile)){
                props.load(in);
            }catch(IOException ignored){}
        }
    }

    public void save(){
        try(FileOutputStream out = new FileOutputStream(storeFile)){
            props.store(out, "Simjot preferences");
        }catch(IOException ex){ ex.printStackTrace(); }
    }

    // ---- getters / setters ---- //
    public int getJournalFontSize(){ return Integer.parseInt(props.getProperty(KEY_JOURNAL_FONT, String.valueOf(DEF_JOURNAL_FONT))); }
    public void setJournalFontSize(int v){ props.setProperty(KEY_JOURNAL_FONT, String.valueOf(v)); }

    public int getPoemFontSize(){ return Integer.parseInt(props.getProperty(KEY_POEM_FONT, String.valueOf(DEF_POEM_FONT))); }
    public void setPoemFontSize(int v){ props.setProperty(KEY_POEM_FONT, String.valueOf(v)); }

    public String getAnimation(){ return props.getProperty(KEY_ANIMATION, DEF_ANIMATION); }
    public void setAnimation(String a){ props.setProperty(KEY_ANIMATION, a); }

    public String getTheme(){ return props.getProperty(KEY_THEME, DEF_THEME); }
    public void setTheme(String t){ props.setProperty(KEY_THEME, t); }

    public boolean isGlowEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_GLOW, String.valueOf(DEF_GLOW))); }
    public void setGlowEnabled(boolean b){ props.setProperty(KEY_GLOW, String.valueOf(b)); }

    public String getBackgroundImage(){ return props.getProperty(KEY_BG_IMAGE, DEF_BG_IMAGE); }
    
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
        return props.getProperty(KEY_ENTRY_BG_IMAGE, "");
    }
    
    public void setEntryBackgroundImage(String path) {
        if (path == null || path.trim().isEmpty()) {
            props.remove(KEY_ENTRY_BG_IMAGE);
        } else {
            props.setProperty(KEY_ENTRY_BG_IMAGE, path);
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
        return props.getProperty(KEY_POEM_BG_IMAGE, "");
    }
    
    public void setPoemBackgroundImage(String path) {
        if (path == null || path.trim().isEmpty()) {
            props.remove(KEY_POEM_BG_IMAGE);
        } else {
            props.setProperty(KEY_POEM_BG_IMAGE, path);
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
    
    public void setBackgroundImage(String path){ props.setProperty(KEY_BG_IMAGE, path==null?"":path); }

    public int getDefaultBrushSize(){ return Integer.parseInt(props.getProperty(KEY_BRUSH_SIZE, String.valueOf(DEF_BRUSH_SIZE))); }
    public void setDefaultBrushSize(int v){ props.setProperty(KEY_BRUSH_SIZE, String.valueOf(v)); }

    public boolean isSmoothingEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_SMOOTHING, String.valueOf(DEF_SMOOTHING))); }
    public void setSmoothingEnabled(boolean b){ props.setProperty(KEY_SMOOTHING, String.valueOf(b)); }

    public boolean isThumbnailGeneration(){ return Boolean.parseBoolean(props.getProperty(KEY_THUMBNAILS, String.valueOf(DEF_THUMBNAILS))); }
    public void setThumbnailGeneration(boolean b){ props.setProperty(KEY_THUMBNAILS, String.valueOf(b)); }

    public int getAutosaveMinutes(){ return Integer.parseInt(props.getProperty(KEY_AUTOSAVE, String.valueOf(DEF_AUTOSAVE))); }
    public void setAutosaveMinutes(int min){ props.setProperty(KEY_AUTOSAVE, String.valueOf(min)); }

    // Tutorial seen flag
    public boolean isTutorialSeen(){ return Boolean.parseBoolean(props.getProperty(KEY_TUTORIAL_SEEN, String.valueOf(DEF_TUTORIAL_SEEN))); }
    public void setTutorialSeen(boolean seen){ props.setProperty(KEY_TUTORIAL_SEEN, String.valueOf(seen)); }

    public boolean isAnimationsDisabled(){ return Boolean.parseBoolean(props.getProperty(KEY_DISABLE_ANIMATIONS, String.valueOf(DEF_DISABLE_ANIMATIONS))); }
    public void setAnimationsDisabled(boolean b){ props.setProperty(KEY_DISABLE_ANIMATIONS, String.valueOf(b)); }

    public boolean isBreathingOverlayEnabled(){ return Boolean.parseBoolean(props.getProperty(KEY_BREATHING_OVERLAY, String.valueOf(DEF_BREATHING_OVERLAY))); }
    public void setBreathingOverlayEnabled(boolean b){ props.setProperty(KEY_BREATHING_OVERLAY, String.valueOf(b)); }

    public boolean isShowWidgetOptions(){ return Boolean.parseBoolean(props.getProperty(KEY_SHOW_WIDGET_OPTIONS, String.valueOf(DEF_SHOW_WIDGET_OPTIONS))); }
    public void setShowWidgetOptions(boolean b){ props.setProperty(KEY_SHOW_WIDGET_OPTIONS, String.valueOf(b)); }

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
}