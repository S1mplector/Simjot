/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.spelling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import main.infrastructure.ffi.NativeAccess;

/**
 * Shared lexicon helper for spell-check and autocorrect features.
 * Normalizes words, provides base-form derivations, and routes
 * membership checks through the native dictionary when available,
 * falling back to the embedded lexicon otherwise.
 */
final class SpellLexicon {
    private SpellLexicon() {}

    private static final int MEMBERSHIP_CACHE_SIZE = 4096;
    private static final Map<String, Boolean> membershipCache = Collections.synchronizedMap(
        new LinkedHashMap<>(512, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                return size() > MEMBERSHIP_CACHE_SIZE;
            }
        });
    private static final Set<String> FUNCTION_WORDS = Set.of(
            "a","an","and","are","as","at","be","but","by","for","if","in","into","is","it",
            "no","not","of","on","or","such","that","the","their","then","there","these",
            "they","this","to","was","will","with"
    );

    static String normalize(String word) {
        if (word == null) return "";
        String trimmed = word.trim().toLowerCase(Locale.ROOT);
        int start = 0;
        int end = trimmed.length();
        while (start < end && !Character.isLetter(trimmed.charAt(start))) start++;
        while (end > start && !Character.isLetter(trimmed.charAt(end - 1))) end--;
        if (start >= end) return "";
        return trimmed.substring(start, end);
    }

    static boolean contains(String word) {
        if (word == null || word.isEmpty()) return false;
        String normalized = normalize(word);
        if (normalized.isEmpty()) return false;
        Boolean cached = membershipCache.get(normalized);
        if (cached != null) return cached;
        
        // Try native spell check first (most accurate)
        boolean exists;
        if (NativeAccess.spellReady()) {
            exists = NativeAccess.spellContains(normalized);
        } else if (NativeAccess.dictionaryReady()) {
            exists = NativeAccess.dictionaryContains(normalized);
        } else {
            exists = WordDatabase.getWordSet().contains(normalized);
        }
        membershipCache.put(normalized, exists);
        return exists;
    }

    static List<String> deriveBaseForms(String lower) {
        if (lower == null || lower.length() < 3) return Collections.emptyList();
        List<String> forms = new ArrayList<>();
        // Possessives/plurals
        if (lower.endsWith("'s") && lower.length() > 2) {
            forms.add(lower.substring(0, lower.length() - 2));
        }
        if (lower.endsWith("s") && lower.length() > 2) {
            forms.add(lower.substring(0, lower.length() - 1));
        }
        if (lower.endsWith("es") && lower.length() > 3) {
            forms.add(lower.substring(0, lower.length() - 2));
        }
        // Past tense
        if (lower.endsWith("ed") && lower.length() > 3) {
            forms.add(lower.substring(0, lower.length() - 2));
            forms.add(lower.substring(0, lower.length() - 1));
        }
        // Present participle
        if (lower.endsWith("ing") && lower.length() > 4) {
            String base = lower.substring(0, lower.length() - 3);
            forms.add(base);
            forms.add(base + "e");
        }
        // Adverbs
        if (lower.endsWith("ly") && lower.length() > 3) {
            forms.add(lower.substring(0, lower.length() - 2));
        }
        return forms;
    }

    static boolean isFunctionWord(String word) {
        if (word == null) return false;
        return FUNCTION_WORDS.contains(word.toLowerCase(Locale.ROOT));
    }
}
