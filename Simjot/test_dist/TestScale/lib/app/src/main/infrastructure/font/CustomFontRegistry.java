/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.font;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import main.core.font.CustomFont;

/**
 * Registry for managing custom fonts in the fonts folder.
 * Provides list/add/rename/delete operations.
 */
public final class CustomFontRegistry {
    
    public static final String FONT_EXTENSION = ".sjf";
    
    private final Path fontsDirectory;
    private final Map<String, CustomFont> loadedFonts;
    private final List<RegistryListener> listeners;
    
    public interface RegistryListener {
        void onFontAdded(String fontName);
        void onFontRemoved(String fontName);
        void onFontRenamed(String oldName, String newName);
        void onFontModified(String fontName);
    }
    
    public CustomFontRegistry(Path fontsDirectory) {
        this.fontsDirectory = fontsDirectory;
        this.loadedFonts = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        
        // Ensure directory exists
        try {
            Files.createDirectories(fontsDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create fonts directory: " + e.getMessage());
        }
    }
    
    public void addListener(RegistryListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(RegistryListener listener) {
        listeners.remove(listener);
    }
    
    public Path getFontsDirectory() {
        return fontsDirectory;
    }
    
    public List<String> listFontNames() {
        List<String> names = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fontsDirectory, "*" + FONT_EXTENSION)) {
            for (Path path : stream) {
                String filename = path.getFileName().toString();
                String name = filename.substring(0, filename.length() - FONT_EXTENSION.length());
                names.add(name);
            }
        } catch (IOException e) {
            System.err.println("Failed to list fonts: " + e.getMessage());
        }
        
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    
    public int getFontCount() {
        return listFontNames().size();
    }
    
    public boolean fontExists(String name) {
        if (name == null || name.isEmpty()) return false;
        Path path = fontsDirectory.resolve(sanitizeName(name) + FONT_EXTENSION);
        return Files.exists(path);
    }
    
    public CustomFont loadFont(String name) {
        if (name == null || name.isEmpty()) return null;
        
        // Check cache
        CustomFont cached = loadedFonts.get(name);
        if (cached != null) return cached;
        
        // Load from file
        Path path = fontsDirectory.resolve(sanitizeName(name) + FONT_EXTENSION);
        if (!Files.exists(path)) return null;
        
        try {
            CustomFont font = CustomFontStorage.load(path);
            loadedFonts.put(name, font);
            return font;
        } catch (IOException e) {
            System.err.println("Failed to load font '" + name + "': " + e.getMessage());
            return null;
        }
    }
    
    public boolean saveFont(CustomFont font) {
        if (font == null || font.getName() == null || font.getName().isEmpty()) {
            return false;
        }
        font.touch();
        
        String name = font.getName();
        Path path = fontsDirectory.resolve(sanitizeName(name) + FONT_EXTENSION);
        
        try {
            CustomFontStorage.save(font, path);
            loadedFonts.put(name, font);
            
            for (RegistryListener l : listeners) {
                l.onFontModified(name);
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("Failed to save font '" + name + "': " + e.getMessage());
            return false;
        }
    }
    
    public boolean addFont(CustomFont font) {
        if (font == null || font.getName() == null || font.getName().isEmpty()) {
            return false;
        }
        
        String name = font.getName();
        if (fontExists(name)) {
            return false; // Font already exists
        }
        
        if (saveFont(font)) {
            for (RegistryListener l : listeners) {
                l.onFontAdded(name);
            }
            return true;
        }
        
        return false;
    }
    
    public CustomFont createFont(String name, String author) {
        if (name == null || name.isEmpty()) {
            name = generateUniqueName("New Font");
        } else if (fontExists(name)) {
            name = generateUniqueName(name);
        }
        
        CustomFont font = new CustomFont(name, author != null ? author : "");
        
        if (saveFont(font)) {
            for (RegistryListener l : listeners) {
                l.onFontAdded(name);
            }
            return font;
        }
        
        return null;
    }
    
    public boolean deleteFont(String name) {
        if (name == null || name.isEmpty()) return false;
        
        Path path = fontsDirectory.resolve(sanitizeName(name) + FONT_EXTENSION);
        
        try {
            if (Files.deleteIfExists(path)) {
                loadedFonts.remove(name);
                
                for (RegistryListener l : listeners) {
                    l.onFontRemoved(name);
                }
                
                return true;
            }
        } catch (IOException e) {
            System.err.println("Failed to delete font '" + name + "': " + e.getMessage());
        }
        
        return false;
    }
    
    public boolean renameFont(String oldName, String newName) {
        if (oldName == null || oldName.isEmpty() || newName == null || newName.isEmpty()) {
            return false;
        }
        
        if (oldName.equals(newName)) return true;
        if (fontExists(newName)) return false;
        
        Path oldPath = fontsDirectory.resolve(sanitizeName(oldName) + FONT_EXTENSION);
        Path newPath = fontsDirectory.resolve(sanitizeName(newName) + FONT_EXTENSION);
        
        try {
            // Load, rename, save, delete old
            CustomFont font = loadFont(oldName);
            if (font == null) return false;
            
            font.setName(newName);
            CustomFontStorage.save(font, newPath);
            Files.deleteIfExists(oldPath);
            
            loadedFonts.remove(oldName);
            loadedFonts.put(newName, font);
            
            for (RegistryListener l : listeners) {
                l.onFontRenamed(oldName, newName);
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("Failed to rename font: " + e.getMessage());
            return false;
        }
    }
    
    public CustomFont duplicateFont(String name, String newName) {
        CustomFont original = loadFont(name);
        if (original == null) return null;
        
        if (newName == null || newName.isEmpty()) {
            newName = generateUniqueName(name + " Copy");
        } else if (fontExists(newName)) {
            newName = generateUniqueName(newName);
        }
        
        CustomFont copy = original.copy();
        copy.setName(newName);
        
        if (addFont(copy)) {
            return copy;
        }
        
        return null;
    }
    
    public void unloadFont(String name) {
        loadedFonts.remove(name);
    }
    
    public void unloadAll() {
        loadedFonts.clear();
    }
    
    public void refresh() {
        // Reload all cached fonts
        for (String name : new ArrayList<>(loadedFonts.keySet())) {
            Path path = fontsDirectory.resolve(sanitizeName(name) + FONT_EXTENSION);
            if (Files.exists(path)) {
                try {
                    CustomFont font = CustomFontStorage.load(path);
                    loadedFonts.put(name, font);
                } catch (IOException e) {
                    loadedFonts.remove(name);
                }
            } else {
                loadedFonts.remove(name);
            }
        }
    }
    
    private String generateUniqueName(String baseName) {
        if (!fontExists(baseName)) return baseName;
        
        for (int i = 2; i < 1000; i++) {
            String name = baseName + " " + i;
            if (!fontExists(name)) return name;
        }
        
        return baseName + " " + System.currentTimeMillis();
    }
    
    private String sanitizeName(String name) {
        // Remove characters that are problematic for filenames
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
