/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EntryFileFormat {
    private EntryFileFormat() {}

    static final String META_PREFIX = "SJMETA:";
    static final int META_VERSION = 1;

    static final class EntryMeta {
        String title;
        int mood = -1;
        boolean guided;
        String template;
        long savedAt;
    }

    static String buildHeader(EntryMeta meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":").append(META_VERSION);
        if (meta.title != null) sb.append(",\"title\":\"").append(escape(meta.title)).append("\"");
        if (meta.mood >= 0) sb.append(",\"mood\":").append(meta.mood);
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
        Boolean guided = extractBool(json, "guided");
        meta.guided = guided != null && guided;
        meta.template = extractString(json, "template");
        Long saved = extractLong(json, "savedAt");
        meta.savedAt = saved == null ? 0L : saved;
        if (meta.title == null && mood == null && meta.template == null && saved == null) {
            return null;
        }
        return meta;
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
}
