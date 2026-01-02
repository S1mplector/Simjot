/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import main.core.font.CustomFont;
import main.infrastructure.io.AppDirectories;

/**
 * Utility helpers for custom font discovery and naming.
 */
public final class CustomFontSupport {
    public static final String CUSTOM_PREFIX = "Custom: ";

    private static volatile CustomFontRegistry registry;

    private CustomFontSupport() {}

    public static boolean isCustomDisplayName(String name) {
        return name != null && name.startsWith(CUSTOM_PREFIX);
    }

    public static String toDisplayName(String name) {
        if (name == null || name.isBlank()) return name;
        return isCustomDisplayName(name) ? name : CUSTOM_PREFIX + name;
    }

    public static String stripDisplayName(String displayName) {
        if (displayName == null) return null;
        if (displayName.startsWith(CUSTOM_PREFIX)) {
            return displayName.substring(CUSTOM_PREFIX.length()).trim();
        }
        return displayName;
    }

    public static Path fontsDirectory() {
        try {
            return AppDirectories.folder(AppDirectories.Type.CUSTOM_FONTS).toPath();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static CustomFontRegistry registry() {
        if (registry != null) return registry;
        synchronized (CustomFontSupport.class) {
            if (registry == null) {
                Path dir = fontsDirectory();
                if (dir == null) return null;
                registry = new CustomFontRegistry(dir);
            }
        }
        return registry;
    }

    public static List<String> listDisplayNames() {
        CustomFontRegistry reg = registry();
        if (reg == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (String name : reg.listFontNames()) {
            names.add(toDisplayName(name));
        }
        return names;
    }

    public static CustomFont loadByDisplayName(String displayName) {
        CustomFontRegistry reg = registry();
        if (reg == null) return null;
        String name = stripDisplayName(displayName);
        if (name == null || name.isBlank()) return null;
        return reg.loadFont(name);
    }
}
