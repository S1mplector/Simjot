/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Applies lightweight syntax coloring to code snippets inside a {@link JTextPane}.
 *
 * <p>The formatter detects fenced code blocks (```lang) as well as implicit blocks
 * that "look" like code (multiple lines ending with semicolons/braces, imports, etc.).
 * It then auto-detects the language (Java, Python, C, or C++) and colors keywords,
 * strings, comments, and numbers to improve readability without altering the underlying
 * content.</p>
 */
public final class CodeSyntaxFormatter {

    private static final int DEBOUNCE_MS = 400;
    private static final int MIN_CODE_LINES = 4;
    private static final int MIN_CODE_BLOCK_LENGTH = 48;
    private static final Color BASE_CODE_COLOR = new Color(42, 46, 55);
    private static final Color KEYWORD_COLOR = new Color(111, 66, 193);
    private static final Color TYPE_COLOR = new Color(5, 129, 168);
    private static final Color BUILTIN_COLOR = new Color(46, 116, 181);
    private static final Color COMMENT_COLOR = new Color(97, 137, 102);
    private static final Color STRING_COLOR = new Color(193, 112, 52);
    private static final Color NUMBER_COLOR = new Color(149, 85, 197);
    private static final String CODE_FONT_FAMILY = resolveCodeFont();
    private static final AttributeSet BASE_ATTR = buildBaseAttribute();
    private static final Map<Color, AttributeSet> COLOR_CACHE = new ConcurrentHashMap<>();

    private static final java.util.regex.Pattern STRING_PATTERN =
            java.util.regex.Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final java.util.regex.Pattern TRIPLE_STRING_PATTERN =
            java.util.regex.Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''");
    private static final java.util.regex.Pattern NUMBER_PATTERN =
            java.util.regex.Pattern.compile("\\b0x[0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?\\b");

    private static final SyntaxDefinition JAVA_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "class",
                    "continue", "default", "do", "double", "else", "enum", "extends", "final",
                    "finally", "float", "for", "if", "implements", "import", "instanceof",
                    "interface", "long", "native", "new", "package", "private", "protected",
                    "public", "return", "short", "static", "strictfp", "super", "switch",
                    "synchronized", "this", "throw", "throws", "transient", "try", "void",
                    "volatile", "while"
            }),
            buildWordPattern(new String[]{
                    "String", "List", "Map", "Set", "Optional", "LocalDate", "LocalDateTime",
                    "BigDecimal", "Runnable", "Comparable"
            }),
            buildWordPattern(new String[]{"System", "Objects", "Collectors"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition PYTHON_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "and", "as", "assert", "async", "await", "break", "class", "continue",
                    "def", "del", "elif", "else", "except", "False", "finally", "for",
                    "from", "global", "if", "import", "in", "is", "lambda", "None",
                    "nonlocal", "not", "or", "pass", "raise", "return", "True", "try",
                    "while", "with", "yield"
            }),
            buildWordPattern(new String[]{"list", "dict", "tuple", "set"}),
            buildWordPattern(new String[]{"print", "len", "range", "enumerate", "open"}),
            java.util.regex.Pattern.compile("#[^\\n]*"),
            java.util.regex.Pattern.compile("(?s)\"\"\".*?\"\"\"|'''[\\s\\S]*?'''")
    );

    private static final SyntaxDefinition C_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "auto", "break", "case", "char", "const", "continue", "default", "do",
                    "double", "else", "enum", "extern", "float", "for", "goto", "if",
                    "inline", "int", "long", "register", "restrict", "return", "short",
                    "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
                    "unsigned", "void", "volatile", "while"
            }),
            buildWordPattern(new String[]{"size_t", "uint32_t", "uint64_t"}),
            buildWordPattern(new String[]{"printf", "scanf", "memcpy", "malloc", "free"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition CPP_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
                    "bool", "break", "case", "catch", "char", "class", "compl", "const",
                    "constexpr", "const_cast", "continue", "decltype", "default", "delete",
                    "do", "double", "dynamic_cast", "else", "enum", "explicit", "export",
                    "extern", "false", "float", "for", "friend", "goto", "if", "inline",
                    "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
                    "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
                    "register", "reinterpret_cast", "return", "short", "signed", "sizeof",
                    "static", "static_assert", "static_cast", "struct", "switch", "template",
                    "this", "thread_local", "throw", "true", "try", "typedef", "typeid",
                    "typename", "union", "unsigned", "using", "virtual", "void", "volatile",
                    "wchar_t", "while", "xor", "xor_eq"
            }),
            buildWordPattern(new String[]{"std", "string", "vector", "map", "unique_ptr"}),
            buildWordPattern(new String[]{"cout", "cin", "endl", "printf"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition GENERIC_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{"if", "else", "for", "while", "return", "function", "var", "let"}),
            null,
            null,
            java.util.regex.Pattern.compile("//[^\\n]*|#[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final Map<Language, SyntaxDefinition> SYNTAX_DEFINITIONS = Map.of(
            Language.JAVA, JAVA_SYNTAX,
            Language.PYTHON, PYTHON_SYNTAX,
            Language.C, C_SYNTAX,
            Language.CPP, CPP_SYNTAX,
            Language.GENERIC, GENERIC_SYNTAX
    );

    private final JTextPane textPane;
    private StyledDocument document;
    private final Timer debounceTimer;
    private boolean applyingFormatting = false;

    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            handleDocumentEvent();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handleDocumentEvent();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handleDocumentEvent();
        }
    };

    private final PropertyChangeListener documentChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!"document".equals(evt.getPropertyName())) return;
            if (evt.getOldValue() instanceof StyledDocument oldDoc) {
                oldDoc.removeDocumentListener(documentListener);
            }
            if (evt.getNewValue() instanceof StyledDocument newDoc) {
                document = newDoc;
                document.addDocumentListener(documentListener);
            } else {
                document = null;
            }
            scheduleRefresh();
        }
    };

    private CodeSyntaxFormatter(JTextPane pane) {
        this.textPane = Objects.requireNonNull(pane, "textPane");
        this.document = pane.getStyledDocument();
        this.document.addDocumentListener(documentListener);
        pane.addPropertyChangeListener(documentChangeListener);
        this.debounceTimer = new Timer(DEBOUNCE_MS, e -> applyFormatting());
        this.debounceTimer.setRepeats(false);
        scheduleRefresh();
    }

    /** Install the formatter on the provided pane. */
    public static CodeSyntaxFormatter install(JTextPane pane) {
        return new CodeSyntaxFormatter(pane);
    }

    /** Remove listeners and timers. */
    public void dispose() {
        debounceTimer.stop();
        if (document != null) {
            document.removeDocumentListener(documentListener);
        }
        textPane.removePropertyChangeListener(documentChangeListener);
    }

    private void handleDocumentEvent() {
        if (applyingFormatting) return;
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::scheduleRefresh);
            return;
        }
        if (document == null) return;
        debounceTimer.restart();
    }

    private void applyFormatting() {
        if (document == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::applyFormatting);
            return;
        }
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        if (text.isBlank()) {
            return;
        }

        List<CodeRegion> regions = collectRegions(text);
        if (regions.isEmpty()) {
            return;
        }

        applyingFormatting = true;
        try {
            for (CodeRegion region : regions) {
                if (region.start() >= region.end()) continue;
                if (region.end() > text.length()) continue;
                highlightRegion(region, text);
            }
        } finally {
            applyingFormatting = false;
        }
    }

    private List<CodeRegion> collectRegions(String text) {
        List<CodeRegion> regions = new ArrayList<>();
        List<CodeRegion> fenced = findFencedRegions(text);
        regions.addAll(fenced);
        regions.addAll(findImplicitRegions(text, fenced));
        regions.sort(Comparator.comparingInt(CodeRegion::start));
        return regions;
    }

    private List<CodeRegion> findFencedRegions(String text) {
        List<CodeRegion> regions = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int fenceStart = text.indexOf("```", idx);
            if (fenceStart < 0) break;
            int fenceLineEnd = text.indexOf('\n', fenceStart + 3);
            if (fenceLineEnd < 0) break;
            String hint = text.substring(fenceStart + 3, fenceLineEnd).trim();
            int blockClose = text.indexOf("```", fenceLineEnd + 1);
            if (blockClose < 0) break;
            int contentStart = fenceLineEnd + 1;
            if (blockClose <= contentStart) {
                idx = blockClose + 3;
                continue;
            }
            Language lang = detectLanguage(text.substring(contentStart, blockClose), hint);
            regions.add(new CodeRegion(contentStart, blockClose, lang));
            idx = blockClose + 3;
        }
        return regions;
    }

    private List<CodeRegion> findImplicitRegions(String text, List<CodeRegion> reserved) {
        List<CodeRegion> regions = new ArrayList<>();
        List<CodeRegion> sortedReserved = new ArrayList<>(reserved);
        sortedReserved.sort(Comparator.comparingInt(CodeRegion::start));
        int reservedIndex = 0;
        int idx = 0;
        int blockStart = -1;
        int lastCodeLineEnd = -1;
        int codeLines = 0;

        while (idx < text.length()) {
            if (reservedIndex < sortedReserved.size()) {
                CodeRegion active = sortedReserved.get(reservedIndex);
                if (idx >= active.end()) {
                    reservedIndex++;
                    continue;
                }
                if (idx >= active.start() && idx < active.end()) {
                    if (blockStart != -1 && codeLines >= MIN_CODE_LINES && lastCodeLineEnd > blockStart) {
                        addImplicitRegion(regions, reserved, blockStart, lastCodeLineEnd, text);
                    }
                    blockStart = -1;
                    codeLines = 0;
                    idx = active.end();
                    continue;
                }
            }

            int lineEnd = text.indexOf('\n', idx);
            if (lineEnd < 0) lineEnd = text.length();
            String line = text.substring(idx, lineEnd);
            boolean blank = line.trim().isEmpty();
            boolean codeLine = isLikelyCodeLine(line);

            if (codeLine) {
                if (blockStart == -1) {
                    blockStart = idx;
                }
                codeLines++;
                lastCodeLineEnd = lineEnd;
            } else if (!blank) {
                if (blockStart != -1 && codeLines >= MIN_CODE_LINES && lastCodeLineEnd > blockStart) {
                    addImplicitRegion(regions, reserved, blockStart, lastCodeLineEnd, text);
                }
                blockStart = -1;
                codeLines = 0;
            } else {
                if (blockStart != -1) {
                    lastCodeLineEnd = lineEnd;
                }
            }
            idx = lineEnd + 1;
        }

        if (blockStart != -1 && codeLines >= MIN_CODE_LINES && lastCodeLineEnd > blockStart) {
            addImplicitRegion(regions, reserved, blockStart, lastCodeLineEnd, text);
        }
        return regions;
    }

    private void addImplicitRegion(List<CodeRegion> target, List<CodeRegion> reserved, int start, int end, String text) {
        if (end - start < MIN_CODE_BLOCK_LENGTH) return;
        if (overlapsExisting(start, end, reserved)) return;
        if (overlapsExisting(start, end, target)) return;
        String snippet = text.substring(start, end);
        Language lang = detectLanguage(snippet, null);
        target.add(new CodeRegion(start, end, lang));
    }

    private boolean overlapsExisting(int start, int end, List<CodeRegion> regions) {
        for (CodeRegion region : regions) {
            if (start < region.end() && end > region.start()) {
                return true;
            }
        }
        return false;
    }

    private void highlightRegion(CodeRegion region, String fullText) {
        SyntaxDefinition syntax = SYNTAX_DEFINITIONS.getOrDefault(region.language(), GENERIC_SYNTAX);
        int start = region.start();
        int end = region.end();
        int length = end - start;
        if (length <= 0) return;
        String text = fullText.substring(start, end);

        // Collect ranges that should not have keyword coloring (comments and strings)
        List<int[]> excludedRanges = new ArrayList<>();
        collectPatternRanges(text, syntax.blockCommentPattern(), excludedRanges);
        collectPatternRanges(text, syntax.lineCommentPattern(), excludedRanges);
        collectPatternRanges(text, STRING_PATTERN, excludedRanges);
        if (region.language() == Language.PYTHON) {
            collectPatternRanges(text, TRIPLE_STRING_PATTERN, excludedRanges);
        }

        // Apply base monospace font/color to entire region
        document.setCharacterAttributes(start, length, BASE_ATTR, false);

        // Apply keywords, types, builtins, numbers - but skip excluded ranges
        applyPatternExcluding(text, syntax.keywordPattern(), KEYWORD_COLOR, start, excludedRanges);
        applyPatternExcluding(text, syntax.typePattern(), TYPE_COLOR, start, excludedRanges);
        applyPatternExcluding(text, syntax.builtinPattern(), BUILTIN_COLOR, start, excludedRanges);
        applyPatternExcluding(text, NUMBER_PATTERN, NUMBER_COLOR, start, excludedRanges);

        // Apply comments and strings last so they override any accidental keyword matches
        applyPattern(text, syntax.blockCommentPattern(), COMMENT_COLOR, start);
        applyPattern(text, syntax.lineCommentPattern(), COMMENT_COLOR, start);
        applyPattern(text, STRING_PATTERN, STRING_COLOR, start);
        if (region.language() == Language.PYTHON) {
            applyPattern(text, TRIPLE_STRING_PATTERN, STRING_COLOR, start);
        }
    }

    private void collectPatternRanges(String text, java.util.regex.Pattern pattern, List<int[]> ranges) {
        if (pattern == null) return;
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
    }

    private boolean isInExcludedRange(int pos, int len, List<int[]> excludedRanges) {
        int end = pos + len;
        for (int[] range : excludedRanges) {
            // If any part of the match overlaps with an excluded range, skip it
            if (pos < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }

    private void applyPatternExcluding(String blockText, java.util.regex.Pattern pattern, Color color, int offset, List<int[]> excludedRanges) {
        if (pattern == null) return;
        java.util.regex.Matcher matcher = pattern.matcher(blockText);
        while (matcher.find()) {
            int matchStart = matcher.start();
            int len = matcher.end() - matchStart;
            if (len <= 0) continue;
            if (isInExcludedRange(matchStart, len, excludedRanges)) continue;
            document.setCharacterAttributes(offset + matchStart, len, colorAttribute(color), false);
        }
    }

    private void applyPattern(String blockText, java.util.regex.Pattern pattern, Color color, int offset) {
        if (pattern == null) return;
        java.util.regex.Matcher matcher = pattern.matcher(blockText);
        while (matcher.find()) {
            int len = matcher.end() - matcher.start();
            if (len <= 0) continue;
            document.setCharacterAttributes(offset + matcher.start(), len, colorAttribute(color), false);
        }
    }

    private static AttributeSet colorAttribute(Color color) {
        return COLOR_CACHE.computeIfAbsent(color, c -> {
            SimpleAttributeSet set = new SimpleAttributeSet();
            StyleConstants.setForeground(set, c);
            return set;
        });
    }

    private boolean isLikelyCodeLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.startsWith("```")) return false;
        // Skip lines that look like prose (start with capital and have few symbols)
        if (trimmed.length() > 20 && Character.isUpperCase(trimmed.charAt(0)) && !trimmed.contains(";") && !trimmed.contains("{")) {
            long symbolCount = trimmed.chars().filter(c -> "{}();=<>&|+-*/".indexOf(c) >= 0).count();
            if (symbolCount < 2) return false;
        }
        // Strong indicators of code
        if (trimmed.startsWith("//") || trimmed.startsWith("#include") || trimmed.startsWith("#define") || trimmed.startsWith("#!")) return true;
        if (trimmed.startsWith("import ") || trimmed.startsWith("package ") || trimmed.startsWith("using ")) return true;
        if (trimmed.startsWith("public ") || trimmed.startsWith("private ") || trimmed.startsWith("protected ")) return true;
        if (trimmed.startsWith("class ") || trimmed.startsWith("struct ") || trimmed.startsWith("enum ")) return true;
        if (trimmed.startsWith("def ") || trimmed.startsWith("function ") || trimmed.startsWith("const ") || trimmed.startsWith("let ") || trimmed.startsWith("var ")) return true;
        if (trimmed.startsWith("async ") || trimmed.startsWith("await ")) return true;
        if (trimmed.startsWith("@") && trimmed.length() > 1 && Character.isLetter(trimmed.charAt(1))) return true;
        if (trimmed.contains("::") || trimmed.contains("->")) return true;
        // Lines ending with code-specific patterns
        if (trimmed.endsWith("{") || trimmed.endsWith("}") || trimmed.endsWith(");") || trimmed.endsWith("};" )) return true;
        // Python-style block starters
        if (trimmed.endsWith(":") && (trimmed.startsWith("if ") || trimmed.startsWith("elif ") || trimmed.startsWith("else") || 
                trimmed.startsWith("for ") || trimmed.startsWith("while ") || trimmed.startsWith("def ") || 
                trimmed.startsWith("class ") || trimmed.startsWith("try") || trimmed.startsWith("except") || trimmed.startsWith("with "))) return true;
        // Lines with semicolon endings (statements)
        if (trimmed.endsWith(";") && trimmed.length() > 3) return true;
        // Function calls with clear syntax: word(args)
        if (trimmed.matches("^\\s*\\w+\\s*\\([^)]*\\)\\s*[;{]?\\s*$")) return true;
        // Variable assignment patterns
        if (trimmed.matches("^\\s*(\\w+\\s+)?\\w+\\s*=\\s*.+;?\\s*$") && trimmed.contains("=")) return true;
        // Require higher symbol density for uncertain lines
        int symbolCount = 0;
        for (char ch : trimmed.toCharArray()) {
            if ("{}();=<>&|+-*/".indexOf(ch) >= 0) {
                symbolCount++;
            }
        }
        return symbolCount >= 3;
    }

    private Language detectLanguage(String snippet, String explicitHint) {
        Language fromHint = fromAlias(explicitHint);
        if (fromHint != null) return fromHint;
        String lower = snippet == null ? "" : snippet.toLowerCase(Locale.ROOT);
        int javaScore = 0;
        int pythonScore = 0;
        int cScore = 0;
        int cppScore = 0;

        if (lower.contains("system.out") || lower.contains("public class") || lower.contains("implements") || lower.contains("@override")) {
            javaScore += 3;
        }
        if (lower.contains("def ") || lower.contains("self") || lower.contains("print(") || lower.contains("elif") || lower.contains("lambda")) {
            pythonScore += 3;
        }
        if (lower.contains("#include <stdio") || lower.contains("printf(") || lower.contains("scanf(") || lower.contains("->")) {
            cScore += 3;
        }
        if (lower.contains("std::") || lower.contains("#include <iostream") || lower.contains("using namespace")) {
            cppScore += 4;
        }
        if (lower.contains("template<")) {
            cppScore += 2;
        }
        if (lower.contains("#include")) {
            cScore += 1;
            cppScore += 1;
        }
        if (lower.contains("async def") || lower.contains("await ")) {
            pythonScore += 1;
        }
        if (lower.contains("new ") && lower.contains("();")) {
            javaScore += 1;
        }

        int max = Math.max(Math.max(javaScore, pythonScore), Math.max(cScore, cppScore));
        if (max == 0) {
            return Language.GENERIC;
        }
        if (javaScore == max) return Language.JAVA;
        if (pythonScore == max) return Language.PYTHON;
        if (cppScore == max) return Language.CPP;
        return Language.C;
    }

    private Language fromAlias(String hint) {
        if (hint == null || hint.isBlank()) return null;
        String normalized = hint.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "java" -> Language.JAVA;
            case "py", "python" -> Language.PYTHON;
            case "c" -> Language.C;
            case "cpp", "c++" -> Language.CPP;
            default -> null;
        };
    }

    private static java.util.regex.Pattern buildWordPattern(String[] words) {
        if (words == null || words.length == 0) return null;
        String joined = Arrays.stream(words)
                .filter(w -> w != null && !w.isBlank())
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("|"));
        if (joined.isEmpty()) return null;
        return java.util.regex.Pattern.compile("\\b(" + joined + ")\\b");
    }

    private static AttributeSet buildBaseAttribute() {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setFontFamily(set, CODE_FONT_FAMILY);
        StyleConstants.setForeground(set, BASE_CODE_COLOR);
        return set;
    }

    private static String resolveCodeFont() {
        String[] preferred = {"JetBrains Mono", "SF Mono", "Menlo", "Consolas", "Monaco", "Monospaced"};
        List<String> installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String candidate : preferred) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return "Monospaced";
    }

    private enum Language { JAVA, PYTHON, C, CPP, GENERIC }

    private record CodeRegion(int start, int end, Language language) {}

    private record SyntaxDefinition(java.util.regex.Pattern keywordPattern,
                                    java.util.regex.Pattern typePattern,
                                    java.util.regex.Pattern builtinPattern,
                                    java.util.regex.Pattern lineCommentPattern,
                                    java.util.regex.Pattern blockCommentPattern) {}
}
