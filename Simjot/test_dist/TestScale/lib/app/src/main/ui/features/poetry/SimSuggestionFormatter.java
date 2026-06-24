/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.poetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes and structures Sim output for the Rhymes dock.
 * Guarantees consistent section headers and numbered lines per item.
 */
public final class SimSuggestionFormatter {
    private static final String[] ORDER = new String[]{
            "Rhymes", "Near rhymes", "Slant rhymes", "Poetic synonyms", "Imagery/metaphor cues"
    };

    // Lenient labels: allow optional hyphens and flexible whitespace
    private static final Pattern SECTION_SPLIT = Pattern.compile(
            "(?i)(Rhymes|Near[-\\s]*rhymes|Slant[-\\s]*rhymes|Poetic[-\\s]*synonyms|Imagery/?[-\\s]*metaphor[-\\s]*cues)\\s*:"
    );

    private SimSuggestionFormatter() {}

    public static String format(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();

        // Split into labeled sections while preserving order using a map
        Map<String, String> sections = new LinkedHashMap<>();
        Matcher m = SECTION_SPLIT.matcher(t);
        int lastStart = 0;
        String lastLabel = null;
        while (m.find()) {
            if (lastLabel != null) {
                sections.put(canonical(lastLabel), t.substring(lastStart, m.start()).trim());
            }
            lastLabel = m.group(1);
            lastStart = m.end();
        }
        if (lastLabel != null) {
            sections.put(canonical(lastLabel), t.substring(lastStart).trim());
        }

        StringBuilder out = new StringBuilder(256);
        boolean wroteAny = false;
        for (String label : ORDER) {
            String itemsBlob = sections.get(label);
            if (itemsBlob == null || itemsBlob.isBlank()) continue;
            List<String> items = extractItems(itemsBlob);
            if (items.isEmpty()) continue;
            wroteAny = true;
            out.append(label).append(":\n");
            for (int i = 0; i < items.size(); i++) {
                out.append(i + 1).append(". ").append(items.get(i)).append('\n');
            }
            out.append('\n');
        }

        if (!wroteAny) {
            // Fallback: treat entire text as a single list under a generic header
            List<String> items = extractItems(t);
            if (!items.isEmpty()) {
                out.append("Suggestions:").append('\n');
                for (int i = 0; i < items.size(); i++) {
                    out.append(i + 1).append(". ").append(items.get(i)).append('\n');
                }
            } else {
                // As last resort, keep the first ~200 chars to avoid empty output
                out.append("Suggestions:").append('\n');
                String snip = t.length() > 200 ? t.substring(0, 200).trim() + "…" : t;
                out.append("1. ").append(snip).append('\n');
            }
        }

        // Trim trailing blank lines
        String s = out.toString().replaceAll("\n{3,}", "\n\n").trim();
        return s;
    }

    private static String canonical(String label) {
        String l = label.toLowerCase().replaceAll("\\s+", " ").trim();
        if (l.startsWith("near rhymes")) return "Near rhymes";
        if (l.startsWith("slant rhymes")) return "Slant rhymes";
        if (l.startsWith("poetic synonyms")) return "Poetic synonyms";
        if (l.startsWith("imagery")) return "Imagery/metaphor cues";
        return "Rhymes";
    }

    // Extract numbered or comma/semicolon separated items robustly
    private static List<String> extractItems(String blob) {
        List<String> items = new ArrayList<>();
        // Prefer numbered patterns like 1. X 2. Y
        Matcher n = Pattern.compile("(?:(?:^|\n|\s))(?:[0-9]+)\\.\\s*([^0-9].*?)(?=(?:\s+[0-9]+\\.)|$)").matcher(blob);
        while (n.find()) {
            String s = clean(n.group(1));
            if (!s.isBlank()) items.add(s);
        }
        if (!items.isEmpty()) return items;
        // Fallback: split by commas/semicolons
        for (String part : blob.split("[;,]")) {
            String s = clean(part);
            if (!s.isBlank()) items.add(s);
        }
        // Cap list sizes roughly to keep UI tidy
        if (items.size() > 12) return items.subList(0, 12);
        return items;
    }

    private static String clean(String s) {
        return s.replaceAll("^[-\u2022]+\\s*", "").replaceAll("\\s+", " ").trim();
    }
}
