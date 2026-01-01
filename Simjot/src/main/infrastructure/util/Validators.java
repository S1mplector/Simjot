/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Robust validation utilities for common data types.
 * All methods are null-safe and never throw exceptions.
 */
public final class Validators {
    private Validators() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[\\w.-]+(?:/[\\w./-]*)?$"
    );
    
    private static final Pattern FILENAME_SAFE = Pattern.compile(
        "^[a-zA-Z0-9._-]+$"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // NULL & EMPTY CHECKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Require non-null value, throw if null.
     */
    public static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    /**
     * Require non-blank string, throw if null or blank.
     */
    public static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * Require non-empty collection, throw if null or empty.
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String name) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return collection;
    }

    /**
     * Check if value is null or empty.
     */
    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Check if value is null, empty, or whitespace only.
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Check if collection is null or empty.
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Check if map is null or empty.
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NUMERIC VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if integer is within range (inclusive).
     */
    public static boolean inRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Check if long is within range (inclusive).
     */
    public static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * Check if double is within range (inclusive).
     */
    public static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /**
     * Clamp integer to range.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp long to range.
     */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp double to range.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Check if value is positive (> 0).
     */
    public static boolean isPositive(int value) {
        return value > 0;
    }

    /**
     * Check if value is non-negative (>= 0).
     */
    public static boolean isNonNegative(int value) {
        return value >= 0;
    }

    /**
     * Require positive value.
     */
    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
        return value;
    }

    /**
     * Require non-negative value.
     */
    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, got: " + value);
        }
        return value;
    }

    /**
     * Require value in range.
     */
    public static int requireInRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                name + " must be between " + min + " and " + max + ", got: " + value
            );
        }
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STRING VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if string matches email pattern.
     */
    public static boolean isEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }

    /**
     * Check if string matches URL pattern.
     */
    public static boolean isUrl(String value) {
        return value != null && URL_PATTERN.matcher(value).matches();
    }

    /**
     * Check if string is safe for use as filename.
     */
    public static boolean isSafeFilename(String value) {
        if (value == null || value.isEmpty() || value.length() > 255) return false;
        if (value.equals(".") || value.equals("..")) return false;
        return FILENAME_SAFE.matcher(value).matches();
    }

    /**
     * Check if string length is within bounds.
     */
    public static boolean hasLength(String value, int min, int max) {
        if (value == null) return min <= 0;
        int len = value.length();
        return len >= min && len <= max;
    }

    /**
     * Require string length within bounds.
     */
    public static String requireLength(String value, int min, int max, String name) {
        int len = value == null ? 0 : value.length();
        if (len < min || len > max) {
            throw new IllegalArgumentException(
                name + " length must be between " + min + " and " + max + ", got: " + len
            );
        }
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATH VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if path exists.
     */
    public static boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    /**
     * Check if path is a regular file.
     */
    public static boolean isFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    /**
     * Check if path is a directory.
     */
    public static boolean isDirectory(Path path) {
        return path != null && Files.isDirectory(path);
    }

    /**
     * Check if path is readable.
     */
    public static boolean isReadable(Path path) {
        return path != null && Files.isReadable(path);
    }

    /**
     * Check if path is writable.
     */
    public static boolean isWritable(Path path) {
        return path != null && Files.isWritable(path);
    }

    /**
     * Require path to exist.
     */
    public static Path requireExists(Path path, String name) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException(name + " does not exist: " + path);
        }
        return path;
    }

    /**
     * Require path to be a file.
     */
    public static Path requireFile(Path path, String name) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(name + " is not a file: " + path);
        }
        return path;
    }

    /**
     * Require path to be a directory.
     */
    public static Path requireDirectory(Path path, String name) {
        if (path == null || !Files.isDirectory(path)) {
            throw new IllegalArgumentException(name + " is not a directory: " + path);
        }
        return path;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SAFE PARSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse integer safely, returning default on failure.
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse long safely, returning default on failure.
     */
    public static long parseLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse double safely, returning default on failure.
     */
    public static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse boolean safely (true, yes, 1, on).
     */
    public static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        String v = value.trim().toLowerCase();
        if (v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("0") || v.equals("off")) return false;
        return defaultValue;
    }
}
