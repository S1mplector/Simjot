/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.sim.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReasoningHeuristics {
    private ReasoningHeuristics() {}

    // Public API
    public static String detectFeeling(String text) {
        if (text == null) return "";
        String lt = text.toLowerCase();
        String[] cues = {"i feel ", "i am feeling ", "i'm feeling ", "i felt ", "feels ", "feeling ", "i am ", "i'm "};
        for (String cue : cues) {
            int i = lt.indexOf(cue);
            if (i >= 0) {
                int start = i + cue.length();
                int end = Math.min(lt.length(), start + 40);
                String span = lt.substring(start, end);
                int cut = span.indexOf('.'); if (cut < 0) cut = span.indexOf('!'); if (cut < 0) cut = span.indexOf('?');
                if (cut > 0) span = span.substring(0, cut);
                String[] parts = span.trim().split("\\s+");
                if (parts.length > 0) {
                    int take = Math.min(parts.length, 4);
                    String out = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, take)).trim();
                    String san = sanitizeFeeling(out);
                    if (!san.isBlank() && !isLowValueFeeling(san)) return san;
                }
            }
        }
        if (lt.contains("depressing") || lt.contains("depressed")) return "depressed";
        if (lt.contains("boring") || lt.contains("dull")) return "bored";
        if (lt.contains("hate my life") || (lt.contains("hate") && lt.contains("here"))) return "trapped/angry";
        String[] feelings = {"anxious","tired","drained","lonely","stuck","sad","down","overwhelmed","angry","frustrated","bored","numb","empty"};
        for (String f : feelings) if (lt.contains(f)) return f;
        return "";
    }

    public static String topicSnippet(String text) {
        if (text == null) return "";
        String lt = text.trim();
        String low = lt.toLowerCase();
        Matcher hate = Pattern.compile("\\bi hate\\s+([^.!?]{1,80})", Pattern.CASE_INSENSITIVE).matcher(lt);
        if (hate.find()) {
            String tail = hate.group(1).trim();
            String cleaned = normalizeTopic(tail);
            if (!cleaned.isBlank()) return cleaned;
        }
        Matcher rather = Pattern.compile("\\bwould rather die than\\s+([^.!?]{1,80})", Pattern.CASE_INSENSITIVE).matcher(lt);
        if (rather.find()) {
            String tail = rather.group(1).trim();
            String cleaned = normalizeTopic(tail);
            if (!cleaned.isBlank()) return cleaned;
        }
        if (low.contains("hate it here") || low.contains("hate this house")) {
            return "being here / this house";
        }
        Matcher m = Pattern.compile("\\b(about|because of|regarding)\\b(.*)", Pattern.CASE_INSENSITIVE).matcher(lt);
        if (m.find()) {
            String tail = m.group(2).trim();
            return clipWords(normalizeTopic(tail), 4, 60);
        }
        int p = Math.max(lt.lastIndexOf('.'), Math.max(lt.lastIndexOf('!'), lt.lastIndexOf('?')));
        String seg = p >= 0 ? lt.substring(p+1).trim() : lt;
        return clipWords(normalizeTopic(seg), 4, 60);
    }

    // Internal helpers
    private static String sanitizeFeeling(String s) {
        if (s == null) return "";
        s = s.replaceAll("[^a-zA-Z\u00C0-\u024F\s-]", "").trim();
        if (s.isBlank()) return "";
        s = s.replaceAll("\\b(like|about|that|really|so)\\b$", "").trim();
        return s;
    }

    private static boolean isLowValueFeeling(String s) {
        if (s == null) return true;
        String t = s.toLowerCase();
        String[] bad = {"here","there","this","that","it","house","home"};
        for (String b : bad) if (t.equals(b)) return true;
        return t.length() <= 2;
    }

    private static String normalizeTopic(String s) {
        if (s == null) return "";
        String out = s.replaceAll("[\n\r]+", " ").trim();
        out = out.replaceAll("\\b(\\w+)(?:[\t ,;:-]+\\1\\b)+", "$1");
        out = out.replaceFirst("^(it|this|that|here|there)\\s+", "");
        out = out.replaceAll("[,;:]+$", "").trim();
        return out;
    }

    private static String clipWords(String s, int minWords, int maxChars) {
        if (s == null) return "";
        String[] parts = s.split("\\s+");
        int take = Math.min(parts.length, 10);
        String joined = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, take)).trim();
        if (joined.length() > maxChars) joined = joined.substring(0, maxChars).trim() + "…";
        if (joined.split("\\s+").length < minWords) {
            if (parts.length >= minWords) {
                joined = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, Math.min(parts.length, Math.max(minWords, 8)))).trim();
                if (joined.length() > maxChars) joined = joined.substring(0, maxChars).trim() + "…";
            }
        }
        return joined;
    }
}
