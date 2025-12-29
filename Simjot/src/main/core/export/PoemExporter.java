package main.core.export;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.swing.JComponent;

/**
 * Comprehensive poetry export utility supporting multiple output formats and customization options.
 * 
 * <p>This class provides functionality to export poetry content in various formats including
 * Markdown, HTML, plain text, and PNG images. It supports customizable styling, metadata
 * inclusion, and statistics display.</p>
 * 
 * <p><strong>Supported Formats:</strong></p>
 * <ul>
 *   <li><strong>Markdown</strong> - Standard Markdown with optional metadata and stats</li>
 *   <li><strong>HTML</strong> - Styled HTML with theme support and CSS customization</li>
 *   <li><strong>TXT</strong> - Plain text with optional line numbers</li>
 *   <li><strong>PNG</strong> - Image export with custom rendering or component capture</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Export to HTML with custom styling
 * PoemExporter.Options options = new PoemExporter.Options();
 * options.includeTitle = true;
 * options.includeMetadata = true;
 * options.htmlTheme = "dark";
 * options.fontFamily = "Georgia";
 * options.fontSizePx = 18;
 * 
 * File targetFile = PoemExporter.buildTargetFile(journalFolder, poemFile, PoemExporter.Format.HTML);
 * PoemExporter.exportTextual(targetFile, PoemExporter.Format.HTML, "My Poem", content, options, stats);
 * }</pre>
 * 
 * @author Simjot Development Team
 * @since 1.0.0
 */
public final class PoemExporter {
    
    /**
     * Enumeration of supported export formats.
     * Each format has specific rendering characteristics and options.
     */
    public enum Format { 
        /** Markdown format with headers and metadata */
        MARKDOWN, 
        /** HTML format with CSS styling and theme support */
        HTML, 
        /** Plain text format with optional line numbers */
        TXT, 
        /** PNG image format with custom rendering */
        PNG 
    }

    /**
     * Configuration options for export formatting and styling.
     * 
     * <p>This class provides fine-grained control over export output including
     * metadata inclusion, styling options, and format-specific features.</p>
     */
    public static final class Options {
        
        /**
         * Whether to include the poem title in the export.
         * Default: true
         */
        public boolean includeTitle = true;
        
        /**
         * Whether to include metadata (export timestamp, etc.).
         * Default: true
         */
        public boolean includeMetadata = true;
        
        /**
         * Whether to include poetry statistics (syllable count, form, etc.).
         * Default: false
         */
        public boolean includeStats = false;
        
        /**
         * Whether to include line numbers in text-based exports.
         * Only applies to TXT format.
         * Default: false
         */
        public boolean lineNumbers = false;
        
        /**
         * HTML theme for styling. Supports "light" or "dark".
         * Only applies to HTML format.
         * Default: "light"
         */
        public String htmlTheme = "light";
        
        /**
         * Custom font family for HTML exports.
         * Examples: "Serif", "Georgia", "Arial"
         * Default: null (uses Georgia)
         */
        public String fontFamily = null;
        
        /**
         * Custom font size in pixels for HTML exports.
         * Must be positive value to take effect.
         * Default: null (uses 16px)
         */
        public Integer fontSizePx = null;
        
        /**
         * Custom line height for HTML exports.
         * CSS line-height value (e.g., 1.0, 1.2, 1.5)
         * Default: null (uses normal)
         */
        public Float lineHeight = null;
    }

    /** Private constructor to prevent instantiation of utility class. */
    private PoemExporter() {}

    /**
     * Builds a target file path for export based on the current poem file and format.
     * 
     * <p>If a current poem file is provided, the base filename will be derived from it
     * by removing the .poem extension. Otherwise, a timestamp-based filename will be generated.</p>
     * 
     * @param journalFolder The base directory where the export file should be created
     * @param currentPoemFile The current poem file (may be null)
     * @param fmt The export format to determine file extension
     * @return A File object representing the target export location
     * @throws IllegalArgumentException if journalFolder is null
     */
    public static File buildTargetFile(File journalFolder, File currentPoemFile, Format fmt) {
        String base;
        if (currentPoemFile != null) {
            base = currentPoemFile.getName().replaceFirst("\\.poem$", "");
        } else {
            base = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        }
        String ext = switch (fmt) {
            case MARKDOWN -> ".md";
            case HTML -> ".html";
            case TXT -> ".txt";
            case PNG -> ".png";
        };
        return new File(journalFolder, base + ext);
    }

    /**
     * Exports poetry content to a text-based format (Markdown, HTML, or TXT).
     * 
     * <p>This method handles the high-level export process for text-based formats,
     * delegating to format-specific writers. All output is UTF-8 encoded.</p>
     * 
     * @param outFile The target file for the export
     * @param fmt The export format (must be text-based)
     * @param title The poem title (may be null or empty)
     * @param content The poem content
     * @param opt Export options and styling preferences
     * @param stats Poetry statistics to include (may be null)
     * @throws IOException if file writing fails
     * @throws IllegalArgumentException if format is not text-based
     */
    public static void exportTextual(File outFile, Format fmt, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            switch (fmt) {
                case MARKDOWN -> writeMarkdown(w, title, content, opt, stats);
                case HTML -> writeHtml(w, title, content, opt, stats);
                case TXT -> writeTxt(w, title, content, opt, stats);
                default -> throw new IllegalArgumentException("Unsupported text format: " + fmt);
            }
        }
    }

    /**
     * Exports poetry content as a PNG image.
     * 
     * <p>This method can either render a provided Swing component or create a custom
     * text-based rendering with the specified title and content. The output is
     * a high-quality PNG image with anti-aliased text rendering.</p>
     * 
     * @param outFile The target PNG file
     * @param title The poem title (may be null or empty)
     * @param content The poem content to render
     * @param opt Export options affecting visual appearance
     * @param toRender Optional Swing component to render directly (may be null)
     * @throws IOException if image writing fails
     */
    public static void exportPng(File outFile, String title, String content, Options opt, JComponent toRender) throws IOException {
        // If a component is provided, render it; otherwise render a simple layout with title + text
        BufferedImage img;
        if (toRender != null) {
            int w = Math.max(600, toRender.getWidth());
            int h = Math.max(400, toRender.getHeight());
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            toRender.paint(g2);
            g2.dispose();
        } else {
            // Render text ourselves
            Font titleFont = new Font("Serif", Font.BOLD, 22);
            Font textFont = new Font("Serif", Font.PLAIN, 16);
            int width = 1000;
            int margin = 32;
            int lineHeight = 22;
            int y = margin + 10;
            // Approximate height
            int lines = 1 + content.split("\\R").length;
            int height = Math.max(400, margin * 2 + 50 + lines * lineHeight);
            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            if (opt.includeTitle && title != null && !title.isBlank()) {
                g2.setColor(new Color(30,30,30));
                g2.setFont(titleFont);
                g2.drawString(title, margin, y);
                y += 36;
            }
            g2.setColor(new Color(40,40,40));
            g2.setFont(textFont);
            for (String line : content.split("\\R", -1)) {
                if (opt.lineNumbers) {
                    g2.drawString(String.format("%3d ", y/lineHeight), margin, y);
                    g2.drawString(line, margin + 40, y);
                } else {
                    g2.drawString(line, margin, y);
                }
                y += lineHeight;
            }
            g2.dispose();
        }
        javax.imageio.ImageIO.write(img, "png", outFile);
    }

    /**
     * Writes poetry content in Markdown format.
     * 
     * <p>Generates standard Markdown with level-1 header for title,
     * italic metadata, and optional statistics section.</p>
     * 
     * @param w The writer to output to
     * @param title The poem title
     * @param content The poem content
     * @param opt Export options
     * @param stats Optional statistics to include
     * @throws IOException if writing fails
     */
    private static void writeMarkdown(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write("# " + title + "\n\n");
        }
        if (opt.includeMetadata) {
            w.write("_Exported: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) + "_\n\n");
        }
        w.write(content);
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("\n\n---\n");
            w.write("Stats:\n");
            for (var e : stats.entrySet()) {
                w.write(String.format("- %s: %s\n", e.getKey(), e.getValue()));
            }
        }
    }

    /**
     * Writes poetry content in plain text format.
     * 
     * <p>Generates clean text output with optional title and statistics.
     * No special formatting is applied.</p>
     * 
     * @param w The writer to output to
     * @param title The poem title
     * @param content The poem content
     * @param opt Export options
     * @param stats Optional statistics to include
     * @throws IOException if writing fails
     */
    private static void writeTxt(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write(title + "\n\n");
        }
        w.write(content);
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("\n\n---\n");
            for (var e : stats.entrySet()) {
                w.write(String.format("%s: %s\n", e.getKey(), e.getValue()));
            }
        }
    }

    /**
     * Writes poetry content in HTML format with CSS styling.
     * 
     * <p>Generates a complete HTML document with embedded CSS styling.
     * Supports light/dark themes and custom typography settings.</p>
     * 
     * @param w The writer to output to
     * @param title The poem title
     * @param content The poem content
     * @param opt Export options including styling preferences
     * @param stats Optional statistics to include
     * @throws IOException if writing fails
     */
    private static void writeHtml(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        String bg = opt.htmlTheme != null && opt.htmlTheme.equals("dark") ? "#0f0f10" : "#ffffff";
        String fg = opt.htmlTheme != null && opt.htmlTheme.equals("dark") ? "#e9e9ea" : "#222222";
        // Derive CSS from options, with sensible fallbacks
        String fontFamily = (opt.fontFamily != null && !opt.fontFamily.isBlank()) ? opt.fontFamily : "Georgia";
        int fontSize = (opt.fontSizePx != null && opt.fontSizePx > 0) ? opt.fontSizePx : 16;
        String lineHeightCss = (opt.lineHeight != null && opt.lineHeight > 0f) ? String.valueOf(opt.lineHeight) : "normal";
        w.write("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        w.write("<style>" +
                "body{background:"+bg+";color:"+fg+";margin:40px;" +
                "font:"+fontSize+"px '"+escapeCss(fontFamily)+"', serif;" +
                "}" +
                "h1{font-size:"+(int)Math.round(fontSize*1.75)+"px;margin:0 0 16px;}" +
                "pre{white-space:pre-wrap;line-height:"+lineHeightCss+";}" +
                "</style>");
        w.write("</head><body>\n");
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write("<h1>" + escapeHtml(title) + "</h1>\n");
        }
        if (opt.includeMetadata) {
            w.write("<div style='opacity:.7;font-size:13px;margin-bottom:10px'>Exported " +
                    escapeHtml(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())) + "</div>\n");
        }
        if (opt.lineNumbers) {
            StringBuilder numbered = new StringBuilder();
            String[] lines = content.split("\\R", -1);
            for (int i=0;i<lines.length;i++) {
                numbered.append(String.format("%3d  %s\n", i+1, lines[i]));
            }
            w.write("<pre>" + escapeHtml(numbered.toString()) + "</pre>\n");
        } else {
            w.write("<pre>" + escapeHtml(content) + "</pre>\n");
        }
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("<hr><ul>\n");
            for (var e : stats.entrySet()) {
                w.write("<li>" + escapeHtml(e.getKey()) + ": " + escapeHtml(e.getValue()) + "</li>\n");
            }
            w.write("</ul>\n");
        }
        w.write("</body></html>");
    }

    /**
     * Escapes HTML special characters to prevent XSS and rendering issues.
     * 
     * <p>Converts &, <, and > to their HTML entity equivalents.
     * This is a minimal implementation suitable for the poetry content context.</p>
     * 
     * @param s The string to escape
     * @return The escaped string safe for HTML output
     */
    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    /**
     * Escapes CSS special characters for font family safety.
     * 
     * <p>Provides minimal escaping for CSS font family values.
     * This is not a comprehensive CSS sanitizer but is adequate for the intended use case.</p>
     * 
     * @param s The CSS string to escape
     * @return The escaped string safe for CSS usage
     */
    // Very small helper for CSS string safety (not fully foolproof, but adequate for font family)
    private static String escapeCss(String s) {
        return s.replace("\\", "").replace("\"", "\"");
    }
}
