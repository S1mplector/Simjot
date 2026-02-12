/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.util.Arrays;
import main.infrastructure.io.NativeJson;

final class EntryFileFormat {
    private EntryFileFormat() {}

    static final String META_PREFIX = "SJMETA:";
    static final int META_VERSION = 1;

    static final class EntryMeta {
        String title;
        int mood = -1;
        int[] moodDetails;
        boolean guided;
        String template;
        long savedAt;
    }

    static String buildHeader(EntryMeta meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":").append(META_VERSION);
        if (meta.title != null) sb.append(",\"title\":\"").append(escape(meta.title)).append("\"");
        if (meta.mood >= 0) sb.append(",\"mood\":").append(meta.mood);
        if (hasDetails(meta.moodDetails)) {
            sb.append(",\"emotions\":").append(serializeDetails(meta.moodDetails));
        }
        if (meta.guided) sb.append(",\"guided\":true");
        if (meta.template != null && !meta.template.isBlank()) {
            sb.append(",\"template\":\"").append(escape(meta.template)).append("\"");
        }
        if (meta.savedAt > 0) sb.append(",\"savedAt\":").append(meta.savedAt);
        sb.append("}");
        return META_PREFIX + sb;
    }

    static EntryMeta parseHeader(String line) {
        if (line == null || !line.startsWith(META_PREFIX)) return null;
        String json = line.substring(META_PREFIX.length()).trim();
        EntryMeta meta = new EntryMeta();
        meta.title = extractString(json, "title");
        Integer mood = extractInt(json, "mood");
        if (mood != null) meta.mood = mood;
        int[] details = extractIntArray(json, "emotions");
        if (!hasDetails(details)) {
            details = extractIntArray(json, "moodDetails");
        }
        meta.moodDetails = hasDetails(details) ? details : null;
        Boolean guided = extractBool(json, "guided");
        meta.guided = guided != null && guided;
        meta.template = extractString(json, "template");
        Long saved = extractLong(json, "savedAt");
        meta.savedAt = saved == null ? 0L : saved;
        if (meta.title == null && mood == null && meta.template == null && saved == null && meta.moodDetails == null) {
            return null;
        }
        return meta;
    }

    private static String extractString(String json, String key) {
        return NativeJson.getString(json, key);
    }

    private static Integer extractInt(String json, String key) {
        return NativeJson.getInt(json, key);
    }

    private static Long extractLong(String json, String key) {
        return NativeJson.getLong(json, key);
    }

    private static Boolean extractBool(String json, String key) {
        return NativeJson.getBoolean(json, key);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String serializeDetails(int[] details) {
        int[] normalized = normalizeDetails(details);
        StringBuilder sb = new StringBuilder(40);
        sb.append('[');
        for (int i = 0; i < normalized.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(normalized[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static int[] extractIntArray(String json, String key) {
        String arr = NativeJson.getArray(json, key);
        if (arr == null || arr.length() < 2) return null;
        int end = arr.length() - 1; // closing ']'
        int idx = 1; // after '['
        int[] out = new int[8];
        Arrays.fill(out, -1);
        boolean any = false;
        int slot = 0;
        while (idx <= end && slot < out.length) {
            int tokenStart = idx;
            while (idx < end && arr.charAt(idx) != ',') idx++;
            int tokenEnd = idx;
            Integer parsed = parseArrayTokenInt(arr, tokenStart, tokenEnd);
            if (parsed != null && parsed >= 0) {
                out[slot] = clamp(parsed);
                any = true;
            }
            slot++;
            idx++; // skip comma (or move beyond end)
        }
        return any ? out : null;
    }

    private static Integer parseArrayTokenInt(String arr, int start, int end) {
        while (start < end && Character.isWhitespace(arr.charAt(start))) start++;
        while (end > start && Character.isWhitespace(arr.charAt(end - 1))) end--;
        if (start >= end) return null;

        int sign = 1;
        char first = arr.charAt(start);
        if (first == '+' || first == '-') {
            sign = (first == '-') ? -1 : 1;
            start++;
        }
        if (start >= end) return null;

        long value = 0L;
        for (int i = start; i < end; i++) {
            char c = arr.charAt(i);
            if (c < '0' || c > '9') return null;
            value = (value * 10L) + (c - '0');
            if (value > Integer.MAX_VALUE) return null;
        }
        long signed = sign < 0 ? -value : value;
        if (signed < Integer.MIN_VALUE || signed > Integer.MAX_VALUE) return null;
        return (int) signed;
    }

    private static int[] normalizeDetails(int[] details) {
        if (details == null) return null;
        int[] out = new int[8];
        Arrays.fill(out, -1);
        int limit = Math.min(8, details.length);
        for (int i = 0; i < limit; i++) {
            int v = details[i];
            out[i] = (v < 0) ? -1 : clamp(v);
        }
        return out;
    }

    private static boolean hasDetails(int[] details) {
        if (details == null || details.length == 0) return false;
        for (int v : details) {
            if (v >= 0) return true;
        }
        return false;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

}
