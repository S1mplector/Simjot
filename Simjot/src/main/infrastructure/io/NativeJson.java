/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
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
        String pattern = "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(true|false)";
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
     * Count keys in a JSON object.
     */
    public static Integer countKeys(String json) {
        if (json == null) return null;
        Integer nativeCount = NativeAccess.jsonCountKeys(json);
        if (nativeCount != null && nativeCount >= 0) return nativeCount;
        int count = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) count++;
        return count;
    }

    /**
     * Get keys from a JSON object.
     */
    public static java.util.List<String> getKeys(String json) {
        if (json == null) return null;
        java.util.List<String> nativeKeys = NativeAccess.jsonGetKeys(json);
        if (nativeKeys != null && !nativeKeys.isEmpty()) return nativeKeys;
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            String k = m.group(1);
            if (k != null && !k.isEmpty()) keys.add(k);
        }
        return keys;
    }

    /**
     * Get a nested string value via dot-path (e.g., "user.name").
     */
    public static String getPath(String json, String path) {
        if (json == null || path == null) return null;
        String nativeResult = NativeAccess.jsonGetPath(json, path);
        if (nativeResult != null) return nativeResult;
        String[] parts = path.split("\\.");
        String current = json;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            if (key.isEmpty()) return null;
            if (i == parts.length - 1) {
                return getString(current, key);
            }
            current = getObject(current, key);
            if (current == null) return null;
        }
        return null;
    }

    /**
     * Parse a JSON array of strings into a list.
     */
    public static java.util.List<String> getStringArray(String json) {
        if (json == null) return java.util.Collections.emptyList();
        java.util.List<String> nativeValues = NativeAccess.jsonParseStringArray(json);
        if (nativeValues != null && !nativeValues.isEmpty()) return nativeValues;
        String array = json.trim();
        if (!array.startsWith("[")) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(array);
        while (m.find()) {
            out.add(unescapeString(m.group(1)));
        }
        return out;
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
        String pattern = "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return unescapeString(m.group(1));
        }
        return null;
    }

    private static Long getLongFallback(String json, String key) {
        String pattern = "\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)";
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

    private static String unescapeString(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(c);
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else {
                out.append(c);
            }
        }
        if (esc) out.append('\\');
        return out.toString();
    }
}
