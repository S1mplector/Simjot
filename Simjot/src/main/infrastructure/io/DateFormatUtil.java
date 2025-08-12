package main.infrastructure.io;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Small reusable date/time formatting helper for settings and UI.
 */
public final class DateFormatUtil {
    private DateFormatUtil() {}

    /** Common date patterns shown in the Settings UI. */
    public static String[] getCommonPatterns() {
        return new String[] {
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "EEE, MMM d, yyyy",
            "d MMM yyyy"
        };
    }

    /** Returns true if the pattern can be used to build a formatter. */
    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return false;
        try {
            DateTimeFormatter.ofPattern(pattern);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Formats now with the given pattern; falls back to ISO if invalid. */
    public static String formatNow(String pattern) {
        DateTimeFormatter fmt;
        try {
            fmt = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException ex) {
            fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        }
        return LocalDateTime.now().format(fmt);
    }

    /** Attempts to parse a sample string using the given pattern. */
    public static boolean canParseSample(String sample, String pattern) {
        if (sample == null || pattern == null) return false;
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
            fmt.parse(sample);
            return true;
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return false;
        }
    }
}
