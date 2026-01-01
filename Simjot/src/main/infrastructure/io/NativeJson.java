/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import main.infrastructure.ffi.NativeAccess;

/**
 * Native JSON parsing utilities for fast config loading.
 * Uses native C implementation when available, falls back to regex parsing.
 */
public final class NativeJson {
    private NativeJson() {}

    /**
     * Get a string value from JSON by key.
     * @param json The JSON string
     * @param key The key to look up
     * @return The string value or null if not found
     */
    public static String getString(String json, String key) {
        if (json == null || key == null) return null;
        
        // Try native implementation first
        String nativeResult = NativeAccess.jsonGetString(json, key);
        if (nativeResult != null) return nativeResult;
        
        // Java regex fallback
        return getStringFallback(json, key);
    }

    /**
     * Get a long value from JSON by key.
     * @param json The JSON string
     * @param key The key to look up
     * @return The long value or null if not found
     */
    public static Long getLong(String json, String key) {
        if (json == null || key == null) return null;
        
        // Try native implementation first
        Long nativeResult = NativeAccess.jsonGetLong(json, key);
        if (nativeResult != null) return nativeResult;
        
        // Java fallback
        return getLongFallback(json, key);
    }

    /**
     * Get an integer value from JSON by key.
     */
    public static Integer getInt(String json, String key) {
        Long val = getLong(json, key);
        return val != null ? val.intValue() : null;
    }

    /**
     * Get a double value from JSON by key.
     */
    public static Double getDouble(String json, String key) {
        if (json == null || key == null) return null;
        String val = getString(json, key);
        if (val != null) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get a boolean value from JSON by key.
     */
    public static Boolean getBoolean(String json, String key) {
        if (json == null || key == null) return null;
        String val = getString(json, key);
        if (val != null) {
            return "true".equalsIgnoreCase(val.trim());
        }
        // Try to find raw boolean
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return "true".equals(m.group(1));
        }
        return null;
    }

    /**
     * Check if JSON has a key.
     */
    public static boolean hasKey(String json, String key) {
        if (json == null || key == null) return false;
        
        // Try native implementation first
        if (NativeAccess.jsonHasKey(json, key)) return true;
        
        // Java fallback
        return json.contains("\"" + key + "\"");
    }

    /**
     * Extract a nested JSON object by key.
     */
    public static String getObject(String json, String key) {
        if (json == null || key == null) return null;
        
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        
        int braceStart = json.indexOf('{', idx);
        if (braceStart < 0) return null;
        
        int depth = 1;
        int i = braceStart + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        
        if (depth == 0) {
            return json.substring(braceStart, i);
        }
        return null;
    }

    /**
     * Extract a JSON array by key as a string.
     */
    public static String getArray(String json, String key) {
        if (json == null || key == null) return null;
        
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        
        int bracketStart = json.indexOf('[', idx);
        if (bracketStart < 0) return null;
        
        int depth = 1;
        int i = bracketStart + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            i++;
        }
        
        if (depth == 0) {
            return json.substring(bracketStart, i);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FALLBACK IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private static String getStringFallback(String json, String key) {
        // Simple regex-based extraction
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static Long getLongFallback(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
