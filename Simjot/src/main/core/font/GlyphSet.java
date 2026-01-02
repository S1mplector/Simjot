/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Predefined glyph sets for font creation.
 */
public final class GlyphSet {
    
    private final String name;
    private final List<Integer> codepoints;
    
    public GlyphSet(String name, List<Integer> codepoints) {
        this.name = name;
        this.codepoints = new ArrayList<>(codepoints);
    }
    
    public String getName() { return name; }
    
    public List<Integer> getCodepoints() {
        return Collections.unmodifiableList(codepoints);
    }
    
    public int size() { return codepoints.size(); }
    
    public boolean contains(int codepoint) {
        return codepoints.contains(codepoint);
    }
    
    // Predefined glyph sets
    
    public static final GlyphSet UPPERCASE;
    public static final GlyphSet LOWERCASE;
    public static final GlyphSet DIGITS;
    public static final GlyphSet BASIC_PUNCTUATION;
    public static final GlyphSet EXTENDED_PUNCTUATION;
    public static final GlyphSet BASIC_LATIN;
    public static final GlyphSet ASCII_PRINTABLE;
    public static final GlyphSet COMMON_SYMBOLS;
    
    static {
        // A-Z
        List<Integer> upper = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) upper.add((int) c);
        UPPERCASE = new GlyphSet("Uppercase Letters", upper);
        
        // a-z
        List<Integer> lower = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) lower.add((int) c);
        LOWERCASE = new GlyphSet("Lowercase Letters", lower);
        
        // 0-9
        List<Integer> digits = new ArrayList<>();
        for (char c = '0'; c <= '9'; c++) digits.add((int) c);
        DIGITS = new GlyphSet("Digits", digits);
        
        // Basic punctuation
        List<Integer> basicPunct = new ArrayList<>();
        basicPunct.add((int) '.');
        basicPunct.add((int) ',');
        basicPunct.add((int) '!');
        basicPunct.add((int) '?');
        basicPunct.add((int) '\'');
        basicPunct.add((int) '"');
        basicPunct.add((int) '-');
        basicPunct.add((int) ':');
        basicPunct.add((int) ';');
        basicPunct.add((int) ' ');
        BASIC_PUNCTUATION = new GlyphSet("Basic Punctuation", basicPunct);
        
        // Extended punctuation
        List<Integer> extPunct = new ArrayList<>(basicPunct);
        extPunct.add((int) '(');
        extPunct.add((int) ')');
        extPunct.add((int) '[');
        extPunct.add((int) ']');
        extPunct.add((int) '{');
        extPunct.add((int) '}');
        extPunct.add((int) '/');
        extPunct.add((int) '\\');
        extPunct.add((int) '@');
        extPunct.add((int) '#');
        extPunct.add((int) '$');
        extPunct.add((int) '%');
        extPunct.add((int) '&');
        extPunct.add((int) '*');
        extPunct.add((int) '+');
        extPunct.add((int) '=');
        extPunct.add((int) '<');
        extPunct.add((int) '>');
        extPunct.add((int) '_');
        extPunct.add((int) '|');
        extPunct.add((int) '~');
        extPunct.add((int) '`');
        extPunct.add((int) '^');
        EXTENDED_PUNCTUATION = new GlyphSet("Extended Punctuation", extPunct);
        
        // Basic Latin (A-Z, a-z, 0-9, basic punctuation)
        List<Integer> basicLatin = new ArrayList<>();
        basicLatin.addAll(upper);
        basicLatin.addAll(lower);
        basicLatin.addAll(digits);
        basicLatin.addAll(basicPunct);
        BASIC_LATIN = new GlyphSet("Basic Latin", basicLatin);
        
        // All printable ASCII (32-126)
        List<Integer> ascii = new ArrayList<>();
        for (int c = 32; c <= 126; c++) ascii.add(c);
        ASCII_PRINTABLE = new GlyphSet("ASCII Printable", ascii);
        
        // Common symbols
        List<Integer> symbols = new ArrayList<>();
        symbols.add(0x2190); // ←
        symbols.add(0x2191); // ↑
        symbols.add(0x2192); // →
        symbols.add(0x2193); // ↓
        symbols.add(0x2022); // •
        symbols.add(0x2026); // …
        symbols.add(0x2014); // —
        symbols.add(0x2018); // '
        symbols.add(0x2019); // '
        symbols.add(0x201C); // "
        symbols.add(0x201D); // "
        symbols.add(0x00A9); // ©
        symbols.add(0x00AE); // ®
        symbols.add(0x2122); // ™
        symbols.add(0x00B0); // °
        symbols.add(0x00B1); // ±
        symbols.add(0x00D7); // ×
        symbols.add(0x00F7); // ÷
        symbols.add(0x2264); // ≤
        symbols.add(0x2265); // ≥
        symbols.add(0x2260); // ≠
        symbols.add(0x221E); // ∞
        symbols.add(0x2665); // ♥
        symbols.add(0x2605); // ★
        COMMON_SYMBOLS = new GlyphSet("Common Symbols", symbols);
    }
    
    public static GlyphSet combine(String name, GlyphSet... sets) {
        List<Integer> combined = new ArrayList<>();
        for (GlyphSet set : sets) {
            for (int cp : set.codepoints) {
                if (!combined.contains(cp)) {
                    combined.add(cp);
                }
            }
        }
        Collections.sort(combined);
        return new GlyphSet(name, combined);
    }
    
    public static GlyphSet fromString(String name, String chars) {
        List<Integer> cps = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) {
            int cp = chars.codePointAt(i);
            if (Character.isSupplementaryCodePoint(cp)) i++;
            if (!cps.contains(cp)) {
                cps.add(cp);
            }
        }
        return new GlyphSet(name, cps);
    }
    
    public static GlyphSet range(String name, int startCodepoint, int endCodepoint) {
        List<Integer> cps = new ArrayList<>();
        for (int cp = startCodepoint; cp <= endCodepoint; cp++) {
            cps.add(cp);
        }
        return new GlyphSet(name, cps);
    }
    
    @Override
    public String toString() {
        return String.format("GlyphSet('%s', %d glyphs)", name, codepoints.size());
    }
}
