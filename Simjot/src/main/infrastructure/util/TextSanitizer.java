/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.util;

import main.infrastructure.ffi.NativeAccess;

/**
 * Robust text sanitization utilities with native acceleration.
 * All methods are null-safe and return sensible defaults.
 */
public final class TextSanitizer {
    private TextSanitizer() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE SANITIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sanitize text by collapsing whitespace and trimming.
     * Uses native implementation when available.
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        
        // Try native first
        String result = NativeAccess.stringSanitize(text);
        if (result != null) return result.trim();
        
        // Java fallback
        return text.replace('\n', ' ')
                   .replace('\r', ' ')
                   .replace('\t', ' ')
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Sanitize and limit to max length, adding ellipsis if truncated.
     */
    public static String sanitize(String text, int maxLength) {
        String sanitized = sanitize(text);
        if (sanitized.length() <= maxLength) return sanitized;
        if (maxLength <= 3) return sanitized.substring(0, maxLength);
        return sanitized.substring(0, maxLength - 1) + "…";
    }

    /**
     * Collapse all whitespace to single spaces, preserving line breaks.
     */
    public static String collapseWhitespace(String text) {
        if (text == null || text.isEmpty()) return "";
        
        // Collapse spaces but keep newlines
        return text.replaceAll("[ \\t]+", " ")
                   .replaceAll(" *\\n *", "\n")
                   .trim();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NORMALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Normalize Unicode text (NFC normalization).
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
    }

    /**
     * Convert to lowercase using root locale (consistent behavior).
     */
    public static String toLower(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Convert to uppercase using root locale.
     */
    public static String toUpper(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Capitalize first letter only.
     */
    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() == 1) return text.toUpperCase(java.util.Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Title case each word.
     */
    public static String titleCase(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRIPPING & FILTERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Strip all HTML tags from text.
     */
    public static String stripHtml(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"");
    }

    /**
     * Strip all non-alphanumeric characters except spaces.
     */
    public static String stripSpecialChars(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("[^a-zA-Z0-9\\s]", "");
    }

    /**
     * Strip control characters (keeping printable ASCII and Unicode).
     */
    public static String stripControlChars(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 || c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Keep only ASCII printable characters.
     */
    public static String toAscii(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Extract first N words from text.
     */
    public static String firstWords(String text, int count) {
        if (text == null || text.isEmpty() || count <= 0) return "";
        String[] words = sanitize(text).split("\\s+");
        if (words.length <= count) return sanitize(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /**
     * Extract first line of text.
     */
    public static String firstLine(String text) {
        if (text == null || text.isEmpty()) return "";
        int idx = text.indexOf('\n');
        if (idx < 0) return text.trim();
        return text.substring(0, idx).trim();
    }

    /**
     * Count words in text.
     */
    public static int wordCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        // Try native
        Integer count = NativeAccess.stringTokenCount(text);
        if (count != null && count >= 0) return count;
        
        // Java fallback
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    /**
     * Count characters (excluding whitespace).
     */
    public static int charCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) count++;
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if text is null, empty, or only whitespace.
     */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Check if text is not blank.
     */
    public static boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Check if text contains only ASCII characters.
     */
    public static boolean isAscii(String text) {
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) return false;
        }
        return true;
    }

    /**
     * Check if text contains only alphanumeric characters.
     */
    public static boolean isAlphanumeric(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    /**
     * Return the text if not blank, otherwise return the default.
     */
    public static String orDefault(String text, String defaultValue) {
        return isNotBlank(text) ? text : defaultValue;
    }

    /**
     * Ensure text does not exceed max length.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }

    /**
     * Ensure text does not exceed max length, adding ellipsis.
     */
    public static String ellipsis(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        if (maxLength <= 3) return text.substring(0, maxLength);
        return text.substring(0, maxLength - 1) + "…";
    }
}
