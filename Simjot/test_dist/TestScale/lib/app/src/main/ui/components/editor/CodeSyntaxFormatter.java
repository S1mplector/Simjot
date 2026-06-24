/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
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
 * Applies syntax coloring to fenced code blocks (```lang) inside a {@link JTextPane}.
 *
 * <p>Must be explicitly enabled via toggle, no automatic detection to avoid performance cost.
 * Supports Java, Python, C, C++, JavaScript, TypeScript, Rust, Go, SQL, Shell, HTML, and CSS.</p>
 */
public final class CodeSyntaxFormatter {

    private static final int DEBOUNCE_MS = 250;
    
    // Colors - VS Code inspired dark-friendly palette
    private static final Color BASE_CODE_COLOR = new Color(42, 46, 55);
    private static final Color KEYWORD_COLOR = new Color(198, 120, 221);    // Purple
    private static final Color TYPE_COLOR = new Color(86, 182, 194);        // Cyan
    private static final Color BUILTIN_COLOR = new Color(97, 175, 239);     // Blue
    private static final Color COMMENT_COLOR = new Color(106, 153, 85);     // Green
    private static final Color STRING_COLOR = new Color(206, 145, 120);     // Orange/brown
    private static final Color NUMBER_COLOR = new Color(209, 154, 102);     // Gold
    private static final Color FUNCTION_COLOR = new Color(220, 220, 170);   // Yellow
    private static final Color OPERATOR_COLOR = new Color(150, 150, 150);   // Gray
    private static final Color TAG_COLOR = new Color(86, 156, 214);         // HTML tags
    private static final Color ATTRIBUTE_COLOR = new Color(156, 220, 254);  // HTML attributes
    
    private static final String CODE_FONT_FAMILY = resolveCodeFont();
    private static final AttributeSet BASE_ATTR = buildBaseAttribute();
    private static final Map<Color, AttributeSet> COLOR_CACHE = new ConcurrentHashMap<>();

    // Common patterns
    private static final java.util.regex.Pattern STRING_PATTERN =
            java.util.regex.Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`");
    private static final java.util.regex.Pattern TRIPLE_STRING_PATTERN =
            java.util.regex.Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''");
    private static final java.util.regex.Pattern NUMBER_PATTERN =
            java.util.regex.Pattern.compile("\\b0x[0-9a-fA-F_]+\\b|\\b0b[01_]+\\b|\\b\\d[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b");

    // ═══════════════════════════════════════════════════════════════════════════
    // LANGUAGE DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private static final SyntaxDefinition JAVA_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "class",
                    "continue", "default", "do", "double", "else", "enum", "extends", "final",
                    "finally", "float", "for", "if", "implements", "import", "instanceof",
                    "interface", "long", "native", "new", "package", "private", "protected",
                    "public", "return", "short", "static", "strictfp", "super", "switch",
                    "synchronized", "this", "throw", "throws", "transient", "try", "void",
                    "volatile", "while", "var", "yield", "record", "sealed", "permits", "when"
            }),
            buildWordPattern(new String[]{
                    "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short",
                    "List", "Map", "Set", "Queue", "Deque", "Collection", "Optional", "Stream",
                    "LocalDate", "LocalDateTime", "LocalTime", "Duration", "Instant",
                    "BigDecimal", "BigInteger", "Pattern", "Matcher",
                    "Runnable", "Callable", "Comparable", "Iterable", "Iterator",
                    "Thread", "Object", "Class", "Exception", "Error", "Throwable"
            }),
            buildWordPattern(new String[]{"System", "Objects", "Collections", "Arrays", "Math", "Files", "Paths"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition PYTHON_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "and", "as", "assert", "async", "await", "break", "class", "continue",
                    "def", "del", "elif", "else", "except", "False", "finally", "for",
                    "from", "global", "if", "import", "in", "is", "lambda", "None",
                    "nonlocal", "not", "or", "pass", "raise", "return", "True", "try",
                    "while", "with", "yield", "match", "case"
            }),
            buildWordPattern(new String[]{"int", "float", "str", "bool", "list", "dict", "tuple", "set", "bytes", "type"}),
            buildWordPattern(new String[]{
                    "print", "len", "range", "enumerate", "zip", "map", "filter", "sorted", "reversed",
                    "open", "input", "type", "isinstance", "hasattr", "getattr", "setattr",
                    "sum", "min", "max", "abs", "round", "pow", "divmod",
                    "any", "all", "iter", "next", "id", "hash", "repr", "str", "int", "float", "bool"
            }),
            java.util.regex.Pattern.compile("#[^\\n]*"),
            java.util.regex.Pattern.compile("(?s)\"\"\".*?\"\"\"|'''[\\s\\S]*?'''")
    );

    private static final SyntaxDefinition C_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "auto", "break", "case", "char", "const", "continue", "default", "do",
                    "double", "else", "enum", "extern", "float", "for", "goto", "if",
                    "inline", "int", "long", "register", "restrict", "return", "short",
                    "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
                    "unsigned", "void", "volatile", "while", "_Bool", "_Complex", "_Imaginary"
            }),
            buildWordPattern(new String[]{"size_t", "ptrdiff_t", "int8_t", "int16_t", "int32_t", "int64_t",
                    "uint8_t", "uint16_t", "uint32_t", "uint64_t", "intptr_t", "uintptr_t", "FILE"}),
            buildWordPattern(new String[]{"printf", "scanf", "fprintf", "fscanf", "sprintf", "sscanf",
                    "malloc", "calloc", "realloc", "free", "memcpy", "memmove", "memset", "memcmp",
                    "strlen", "strcpy", "strcat", "strcmp", "strncpy", "strncat", "strncmp",
                    "fopen", "fclose", "fread", "fwrite", "fgets", "fputs", "fseek", "ftell",
                    "exit", "abort", "assert", "NULL"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition CPP_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
                    "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t",
                    "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
                    "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
                    "default", "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit",
                    "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline",
                    "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
                    "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
                    "register", "reinterpret_cast", "requires", "return", "short", "signed",
                    "sizeof", "static", "static_assert", "static_cast", "struct", "switch",
                    "template", "this", "thread_local", "throw", "true", "try", "typedef",
                    "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
                    "volatile", "wchar_t", "while", "xor", "xor_eq"
            }),
            buildWordPattern(new String[]{"std", "string", "wstring", "vector", "map", "unordered_map",
                    "set", "unordered_set", "list", "deque", "array", "pair", "tuple",
                    "unique_ptr", "shared_ptr", "weak_ptr", "optional", "variant", "any",
                    "span", "string_view", "function", "thread", "mutex", "atomic"}),
            buildWordPattern(new String[]{"cout", "cin", "cerr", "clog", "endl", "printf", "scanf",
                    "std::move", "std::forward", "std::make_unique", "std::make_shared"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition JAVASCRIPT_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "async", "await", "break", "case", "catch", "class", "const", "continue",
                    "debugger", "default", "delete", "do", "else", "export", "extends", "false",
                    "finally", "for", "function", "if", "import", "in", "instanceof", "let",
                    "new", "null", "return", "static", "super", "switch", "this", "throw",
                    "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
            }),
            buildWordPattern(new String[]{"Array", "Object", "String", "Number", "Boolean", "Function",
                    "Symbol", "BigInt", "Map", "Set", "WeakMap", "WeakSet", "Promise",
                    "Date", "RegExp", "Error", "JSON", "Math", "Proxy", "Reflect"}),
            buildWordPattern(new String[]{"console", "document", "window", "fetch", "setTimeout", "setInterval",
                    "clearTimeout", "clearInterval", "parseInt", "parseFloat", "isNaN", "isFinite",
                    "encodeURI", "decodeURI", "encodeURIComponent", "decodeURIComponent",
                    "require", "module", "exports", "process"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition TYPESCRIPT_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "abstract", "any", "as", "asserts", "async", "await", "bigint", "boolean",
                    "break", "case", "catch", "class", "const", "continue", "debugger", "declare",
                    "default", "delete", "do", "else", "enum", "export", "extends", "false",
                    "finally", "for", "from", "function", "get", "if", "implements", "import",
                    "in", "infer", "instanceof", "interface", "is", "keyof", "let", "module",
                    "namespace", "never", "new", "null", "number", "object", "of", "package",
                    "private", "protected", "public", "readonly", "return", "require", "set",
                    "static", "string", "super", "switch", "symbol", "this", "throw", "true",
                    "try", "type", "typeof", "undefined", "unique", "unknown", "var", "void",
                    "while", "with", "yield"
            }),
            buildWordPattern(new String[]{"Array", "Object", "String", "Number", "Boolean", "Function",
                    "Symbol", "BigInt", "Map", "Set", "WeakMap", "WeakSet", "Promise",
                    "Partial", "Required", "Readonly", "Record", "Pick", "Omit", "Exclude", "Extract",
                    "NonNullable", "Parameters", "ReturnType", "InstanceType"}),
            buildWordPattern(new String[]{"console", "document", "window", "fetch", "setTimeout", "setInterval"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition RUST_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
                    "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
                    "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self",
                    "static", "struct", "super", "trait", "true", "type", "unsafe", "use",
                    "where", "while", "macro_rules"
            }),
            buildWordPattern(new String[]{"i8", "i16", "i32", "i64", "i128", "isize",
                    "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool", "char",
                    "str", "String", "Vec", "Box", "Rc", "Arc", "RefCell", "Cell",
                    "Option", "Result", "Some", "None", "Ok", "Err",
                    "HashMap", "HashSet", "BTreeMap", "BTreeSet"}),
            buildWordPattern(new String[]{"println", "print", "format", "panic", "assert", "assert_eq",
                    "vec", "dbg", "todo", "unimplemented", "unreachable"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition GO_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "break", "case", "chan", "const", "continue", "default", "defer", "else",
                    "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
                    "map", "package", "range", "return", "select", "struct", "switch", "type", "var"
            }),
            buildWordPattern(new String[]{"bool", "byte", "complex64", "complex128", "error", "float32", "float64",
                    "int", "int8", "int16", "int32", "int64", "rune", "string",
                    "uint", "uint8", "uint16", "uint32", "uint64", "uintptr"}),
            buildWordPattern(new String[]{"append", "cap", "close", "complex", "copy", "delete", "imag",
                    "len", "make", "new", "panic", "print", "println", "real", "recover",
                    "nil", "true", "false", "iota"}),
            java.util.regex.Pattern.compile("//[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition SQL_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN",
                    "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "ALTER",
                    "DROP", "TABLE", "INDEX", "VIEW", "DATABASE", "SCHEMA", "GRANT", "REVOKE",
                    "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "ON", "AS",
                    "ORDER", "BY", "ASC", "DESC", "GROUP", "HAVING", "LIMIT", "OFFSET",
                    "UNION", "INTERSECT", "EXCEPT", "DISTINCT", "ALL", "EXISTS", "CASE",
                    "WHEN", "THEN", "ELSE", "END", "NULL", "IS", "TRUE", "FALSE",
                    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT",
                    "CONSTRAINT", "AUTO_INCREMENT", "SERIAL", "RETURNING", "WITH", "RECURSIVE"
            }),
            buildWordPattern(new String[]{"INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "NUMERIC",
                    "FLOAT", "REAL", "DOUBLE", "VARCHAR", "CHAR", "TEXT", "BLOB", "CLOB",
                    "DATE", "TIME", "TIMESTAMP", "DATETIME", "BOOLEAN", "BOOL", "UUID", "JSON", "JSONB"}),
            buildWordPattern(new String[]{"COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF",
                    "CAST", "CONVERT", "CONCAT", "SUBSTRING", "TRIM", "UPPER", "LOWER", "LENGTH",
                    "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP"}),
            java.util.regex.Pattern.compile("--[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final SyntaxDefinition SHELL_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
                    "do", "done", "in", "function", "select", "time", "coproc",
                    "return", "exit", "break", "continue", "local", "export", "readonly",
                    "declare", "typeset", "unset", "shift", "source", "eval", "exec"
            }),
            null,
            buildWordPattern(new String[]{"echo", "printf", "read", "cd", "pwd", "ls", "cp", "mv", "rm", "mkdir",
                    "rmdir", "cat", "head", "tail", "grep", "sed", "awk", "find", "xargs",
                    "sort", "uniq", "wc", "cut", "tr", "tee", "test", "true", "false",
                    "chmod", "chown", "chgrp", "ln", "touch", "which", "whereis", "type",
                    "curl", "wget", "ssh", "scp", "rsync", "tar", "gzip", "gunzip", "zip", "unzip"}),
            java.util.regex.Pattern.compile("#[^\\n]*"),
            null
    );

    private static final SyntaxDefinition HTML_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{}), // HTML doesn't have keywords in the traditional sense
            null,
            null,
            java.util.regex.Pattern.compile("<!--.*?-->", java.util.regex.Pattern.DOTALL),
            null
    );

    private static final SyntaxDefinition CSS_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{
                    "important", "inherit", "initial", "unset", "revert", "auto", "none",
                    "block", "inline", "flex", "grid", "absolute", "relative", "fixed", "sticky",
                    "hidden", "visible", "scroll", "solid", "dashed", "dotted", "double"
            }),
            null,
            buildWordPattern(new String[]{"rgb", "rgba", "hsl", "hsla", "url", "calc", "var", "attr",
                    "linear-gradient", "radial-gradient", "rotate", "scale", "translate", "skew",
                    "min", "max", "clamp", "counter", "cubic-bezier", "steps"}),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL),
            null
    );

    private static final SyntaxDefinition GENERIC_SYNTAX = new SyntaxDefinition(
            buildWordPattern(new String[]{"if", "else", "for", "while", "return", "function", "var", "let", "const", "class"}),
            null,
            null,
            java.util.regex.Pattern.compile("//[^\\n]*|#[^\\n]*"),
            java.util.regex.Pattern.compile("/\\*.*?\\*/", java.util.regex.Pattern.DOTALL)
    );

    private static final Map<Language, SyntaxDefinition> SYNTAX_DEFINITIONS = Map.ofEntries(
            Map.entry(Language.JAVA, JAVA_SYNTAX),
            Map.entry(Language.PYTHON, PYTHON_SYNTAX),
            Map.entry(Language.C, C_SYNTAX),
            Map.entry(Language.CPP, CPP_SYNTAX),
            Map.entry(Language.JAVASCRIPT, JAVASCRIPT_SYNTAX),
            Map.entry(Language.TYPESCRIPT, TYPESCRIPT_SYNTAX),
            Map.entry(Language.RUST, RUST_SYNTAX),
            Map.entry(Language.GO, GO_SYNTAX),
            Map.entry(Language.SQL, SQL_SYNTAX),
            Map.entry(Language.SHELL, SHELL_SYNTAX),
            Map.entry(Language.HTML, HTML_SYNTAX),
            Map.entry(Language.CSS, CSS_SYNTAX),
            Map.entry(Language.GENERIC, GENERIC_SYNTAX)
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private final JTextPane textPane;
    private StyledDocument document;
    private final Timer debounceTimer;
    private boolean applyingFormatting = false;
    private volatile boolean enabled = false;

    private final DocumentListener documentListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { handleDocumentEvent(); }
        @Override public void removeUpdate(DocumentEvent e) { handleDocumentEvent(); }
        @Override public void changedUpdate(DocumentEvent e) { handleDocumentEvent(); }
    };

    private final PropertyChangeListener documentChangeListener = evt -> {
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
        if (enabled) scheduleRefresh();
    };

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    private CodeSyntaxFormatter(JTextPane pane) {
        this.textPane = Objects.requireNonNull(pane, "textPane");
        this.document = pane.getStyledDocument();
        this.document.addDocumentListener(documentListener);
        pane.addPropertyChangeListener(documentChangeListener);
        this.debounceTimer = new Timer(DEBOUNCE_MS, e -> applyFormatting());
        this.debounceTimer.setRepeats(false);
    }

    /** Install the formatter on the provided pane (starts disabled). */
    public static CodeSyntaxFormatter install(JTextPane pane) {
        return new CodeSyntaxFormatter(pane);
    }

    /** Check if syntax highlighting is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Enable or disable syntax highlighting. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            scheduleRefresh();
        }
    }

    /** Toggle syntax highlighting on/off. Returns the new state. */
    public boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    /** Force a refresh of syntax highlighting (if enabled). */
    public void refresh() {
        if (enabled) {
            scheduleRefresh();
        }
    }

    /** Remove listeners and timers. */
    public void dispose() {
        debounceTimer.stop();
        if (document != null) {
            document.removeDocumentListener(documentListener);
        }
        textPane.removePropertyChangeListener(documentChangeListener);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL LOGIC
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleDocumentEvent() {
        if (applyingFormatting || !enabled) return;
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::scheduleRefresh);
            return;
        }
        if (document == null || !enabled) return;
        debounceTimer.restart();
    }

    private void applyFormatting() {
        if (document == null || !enabled) return;
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
        if (text.isBlank()) return;

        // Only process fenced code blocks (```lang ... ```)
        List<CodeRegion> regions = findFencedRegions(text);
        if (regions.isEmpty()) return;

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

    private List<CodeRegion> findFencedRegions(String text) {
        List<CodeRegion> regions = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int fenceStart = text.indexOf("```", idx);
            if (fenceStart < 0) break;
            int fenceLineEnd = text.indexOf('\n', fenceStart + 3);
            if (fenceLineEnd < 0) break;
            String hint = text.substring(fenceStart + 3, fenceLineEnd).trim().toLowerCase(Locale.ROOT);
            int blockClose = text.indexOf("```", fenceLineEnd + 1);
            if (blockClose < 0) break;
            int contentStart = fenceLineEnd + 1;
            if (blockClose <= contentStart) {
                idx = blockClose + 3;
                continue;
            }
            Language lang = detectLanguage(hint);
            regions.add(new CodeRegion(contentStart, blockClose, lang));
            idx = blockClose + 3;
        }
        return regions;
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

        // Apply syntax colors - skip excluded ranges for keywords/types/builtins
        applyPatternExcluding(text, syntax.keywordPattern(), KEYWORD_COLOR, start, excludedRanges);
        applyPatternExcluding(text, syntax.typePattern(), TYPE_COLOR, start, excludedRanges);
        applyPatternExcluding(text, syntax.builtinPattern(), BUILTIN_COLOR, start, excludedRanges);
        applyPatternExcluding(text, NUMBER_PATTERN, NUMBER_COLOR, start, excludedRanges);

        // Apply comments and strings last so they override
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
            if (pos < range[1] && end > range[0]) return true;
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

    /** Detect language from the hint string after ``` */
    private Language detectLanguage(String hint) {
        if (hint == null || hint.isBlank()) return Language.GENERIC;
        return switch (hint) {
            case "java" -> Language.JAVA;
            case "py", "python", "python3" -> Language.PYTHON;
            case "c" -> Language.C;
            case "cpp", "c++", "cxx", "cc" -> Language.CPP;
            case "js", "javascript", "node" -> Language.JAVASCRIPT;
            case "ts", "typescript" -> Language.TYPESCRIPT;
            case "rs", "rust" -> Language.RUST;
            case "go", "golang" -> Language.GO;
            case "sql", "mysql", "postgresql", "postgres", "sqlite" -> Language.SQL;
            case "sh", "bash", "shell", "zsh", "fish" -> Language.SHELL;
            case "html", "htm", "xml", "xhtml" -> Language.HTML;
            case "css", "scss", "sass", "less" -> Language.CSS;
            default -> Language.GENERIC;
        };
    }

    private static java.util.regex.Pattern buildWordPattern(String[] words) {
        if (words == null || words.length == 0) return null;
        String joined = Arrays.stream(words)
                .filter(w -> w != null && !w.isBlank())
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("|"));
        if (joined.isEmpty()) return null;
        return java.util.regex.Pattern.compile("\\b(" + joined + ")\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
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
            if (installed.contains(candidate)) return candidate;
        }
        return "Monospaced";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    private enum Language { JAVA, PYTHON, C, CPP, JAVASCRIPT, TYPESCRIPT, RUST, GO, SQL, SHELL, HTML, CSS, GENERIC }

    private record CodeRegion(int start, int end, Language language) {}

    private record SyntaxDefinition(java.util.regex.Pattern keywordPattern,
                                    java.util.regex.Pattern typePattern,
                                    java.util.regex.Pattern builtinPattern,
                                    java.util.regex.Pattern lineCommentPattern,
                                    java.util.regex.Pattern blockCommentPattern) {}
}
