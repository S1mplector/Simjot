/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads curated quotes from resources with native-accelerated I/O when possible.
 */
public final class QuoteLibrary {
    private static final String QUOTES_RESOURCE = "quotes /quotes.json";
    private static final List<String> FALLBACK = List.of("Take a deep breath. You are enough.");
    private static volatile List<String> cachedQuotes;

    private QuoteLibrary() {}

    public static List<QuoteEntry> loadQuoteEntries() {
        List<String> cached = cachedQuotes;
        if (cached != null) {
            List<QuoteEntry> legacy = new ArrayList<>(cached.size());
            for (String q : cached) legacy.add(new QuoteEntry(q, null));
            return legacy;
        }
        synchronized (QuoteLibrary.class) {
            if (cachedQuotes != null) {
                List<QuoteEntry> legacy = new ArrayList<>(cachedQuotes.size());
                for (String q : cachedQuotes) legacy.add(new QuoteEntry(q, null));
                return legacy;
            }
        }
        return loadQuoteEntriesInternal();
    }

    public static List<String> loadQuotes() {
        List<String> cached = cachedQuotes;
        if (cached != null) return cached;
        synchronized (QuoteLibrary.class) {
            if (cachedQuotes != null) return cachedQuotes;
            List<String> loaded = loadQuotesInternal();
            if (loaded.isEmpty()) {
                loaded = new ArrayList<>(FALLBACK);
            }
            cachedQuotes = Collections.unmodifiableList(loaded);
            return cachedQuotes;
        }
    }

    private static List<QuoteEntry> loadQuoteEntriesInternal() {
        byte[] data = readResourceBytes(QUOTES_RESOURCE);
        if (data == null || data.length == 0) return Collections.emptyList();
        return parseQuoteEntriesJson(data);
    }

    private static List<String> loadQuotesInternal() {
        byte[] data = readResourceBytes(QUOTES_RESOURCE);
        if (data == null || data.length == 0) return Collections.emptyList();
        return parseQuotesJson(data);
    }

    private static byte[] readResourceBytes(String path) {
        URL url = ResourceLoader.getResource(path);
        if (url != null) {
            try {
                if ("file".equalsIgnoreCase(url.getProtocol())) {
                    return FileIO.readAllBytes(Path.of(url.toURI()));
                }
            } catch (Throwable ignored) {}
        }
        try (InputStream in = ResourceLoader.getResourceAsStream(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> parseQuotesJson(byte[] data) {
        String json = decodeJson(data);
        int len = json.length();
        int i = skipWhitespace(json, 0);
        if (i >= len || json.charAt(i) != '[') return Collections.emptyList();
        i++;
        List<String> out = new ArrayList<>(256);
        while (i < len) {
            i = skipWhitespace(json, i);
            if (i >= len) break;
            char c = json.charAt(i);
            if (c == ']') break;
            if (c != '{') {
                i++;
                continue;
            }
            int start = i;
            int end = findObjectEnd(json, i);
            if (end <= start) break;
            String obj = json.substring(start, end);
            String text = NativeJson.getString(obj, "quoteText");
            if (text != null) {
                String trimmed = text.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            i = end;
        }
        return out;
    }

    private static List<QuoteEntry> parseQuoteEntriesJson(byte[] data) {
        String json = decodeJson(data);
        int len = json.length();
        int i = skipWhitespace(json, 0);
        if (i >= len || json.charAt(i) != '[') return Collections.emptyList();
        i++;
        List<QuoteEntry> out = new ArrayList<>(256);
        while (i < len) {
            i = skipWhitespace(json, i);
            if (i >= len) break;
            char c = json.charAt(i);
            if (c == ']') break;
            if (c != '{') {
                i++;
                continue;
            }
            int start = i;
            int end = findObjectEnd(json, i);
            if (end <= start) break;
            String obj = json.substring(start, end);
            String text = NativeJson.getString(obj, "quoteText");
            if (text != null) {
                String trimmed = text.trim();
                if (!trimmed.isEmpty()) {
                    String author = NativeJson.getString(obj, "quoteAuthor");
                    if (author != null) {
                        author = author.trim();
                        if (author.isEmpty()) author = null;
                    }
                    out.add(new QuoteEntry(trimmed, author));
                }
            }
            i = end;
        }
        return out;
    }

    private static String decodeJson(byte[] data) {
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            try {
                return new String(data, java.nio.charset.Charset.forName("windows-1252"));
            } catch (Throwable ignored2) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
    }

    public static final class QuoteEntry {
        public final String text;
        public final String author;

        public QuoteEntry(String text, String author) {
            this.text = text;
            this.author = author;
        }
    }

    private static int skipWhitespace(String json, int i) {
        int len = json.length();
        while (i < len) {
            char c = json.charAt(i);
            if (c == '\uFEFF') {
                i++;
                continue;
            }
            if (c > ' ') break;
            i++;
        }
        return i;
    }

    private static int findObjectEnd(String json, int start) {
        int len = json.length();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < len; i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }
}
