/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.security.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight metadata stored in encryption headers for quick listing/search.
 */
public final class EncryptedMetadata {
    private EncryptedMetadata() {}

    public static final String META_PREFIX = "SJMETA:";
    public static final int META_VERSION = 1;

    public static String encodeEntry(String title, int mood, boolean guided, String template, long savedAt, int words) {
        return build("entry", title, mood, guided, template, savedAt, words);
    }

    public static String encodePoem(String title, long savedAt, int words) {
        return build("poem", title, -1, false, null, savedAt, words);
    }

    public static Meta parse(String identifier) {
        if (identifier == null || !identifier.startsWith(META_PREFIX)) return null;
        String json = identifier.substring(META_PREFIX.length()).trim();
        Meta meta = new Meta();
        meta.type = extractString(json, "type");
        meta.title = extractString(json, "title");
        Integer mood = extractInt(json, "mood");
        meta.mood = mood == null ? -1 : mood;
        Boolean guided = extractBool(json, "guided");
        meta.guided = guided != null && guided;
        meta.template = extractString(json, "template");
        Long saved = extractLong(json, "savedAt");
        meta.savedAt = saved == null ? 0L : saved;
        Integer words = extractInt(json, "words");
        meta.words = words == null ? -1 : words;
        meta.contentType = parseType(meta.type);
        if (meta.title == null && meta.mood < 0 && meta.savedAt <= 0 && meta.words < 0 && meta.type == null) {
            return null;
        }
        return meta;
    }

    private static String build(String type, String title, int mood, boolean guided, String template, long savedAt, int words) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":").append(META_VERSION);
        if (type != null) sb.append(",\"type\":\"").append(escape(type)).append("\"");
        if (title != null) sb.append(",\"title\":\"").append(escape(title)).append("\"");
        if (mood >= 0) sb.append(",\"mood\":").append(mood);
        if (guided) sb.append(",\"guided\":true");
        if (template != null && !template.isBlank()) {
            sb.append(",\"template\":\"").append(escape(template)).append("\"");
        }
        if (savedAt > 0) sb.append(",\"savedAt\":").append(savedAt);
        if (words >= 0) sb.append(",\"words\":").append(words);
        sb.append("}");
        return META_PREFIX + sb;
    }

    private static ContentType parseType(String type) {
        if (type == null) return ContentType.BINARY;
        String t = type.toLowerCase();
        return switch (t) {
            case "entry", "journal" -> ContentType.ENTRY;
            case "poem", "poetry" -> ContentType.POEM;
            default -> ContentType.BINARY;
        };
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return unescape(m.group(1));
    }

    private static Integer extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private static Long extractLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        try { return Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private static Boolean extractBool(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return Boolean.parseBoolean(m.group(1));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescape(String s) {
        String out = s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
        out = out.replace("\\\"", "\"").replace("\\\\", "\\");
        return out;
    }

    public static final class Meta {
        public ContentType contentType;
        public String type;
        public String title;
        public int mood = -1;
        public boolean guided;
        public String template;
        public long savedAt;
        public int words = -1;
    }
}
